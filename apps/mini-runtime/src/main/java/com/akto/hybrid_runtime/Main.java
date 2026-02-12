package com.akto.hybrid_runtime;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import com.akto.RuntimeMode;
import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.*;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.billing.Organization;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.hybrid_parsers.HttpCallParser;
import com.akto.hybrid_runtime.filter_updates.FilterUpdates;
import com.akto.kafka.Kafka;
import com.akto.kafka.KafkaConfig;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.metrics.AllMetrics;
import com.akto.metrics.ModuleInfoWorker;
// Import protobuf classes
import com.akto.proto.generated.threat_detection.message.http_response_param.v1.HttpResponseParam;
import com.akto.proto.generated.threat_detection.message.http_response_param.v1.StringList;
import com.akto.metrics.ConfigUpdatePoller;
import com.akto.runtime.parser.SampleParser;
import com.akto.runtime.utils.Utils;
import com.akto.testing_db_layer_client.ClientLayer;
import com.akto.usage.OrgUtils;
import com.akto.util.DashboardMode;
import com.google.gson.Gson;

import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

public class Main {

    private Consumer<String, String> consumer;
    public static final String GROUP_NAME = "group_name";
    public static final String VXLAN_ID = "vxlanId";
    public static final String VPC_CIDR = "vpc_cidr";
    public static final String ACCOUNT_ID = "account_id";
    private static final LoggerMaker loggerMaker = new LoggerMaker(Main.class, LogDb.RUNTIME);

    public static final DataActor dataActor = DataActorFactory.fetchInstance();
    private static final ClientLayer clientLayer = new ClientLayer();

    // this sync threshold time is used for deleting sample data
    public static final int sync_threshold_time = 120;
    public static final boolean isKafkaAuthenticationEnabled = KafkaConfig.isKafkaAuthenticationEnabled();
    public static final String kafkaUsername = KafkaConfig.getKafkaUsername();
    public static final String kafkaPassword = KafkaConfig.getKafkaPassword();
    public static final boolean isSendToThreatEnabled = System.getenv("SEND_TO_THREAT_ENABLED") != null && System.getenv("SEND_TO_THREAT_ENABLED").equalsIgnoreCase("true");

    private static int debugPrintCounter = 500;
    private static void printL(Object o) {
        if (debugPrintCounter > 0) {
            debugPrintCounter--;
            loggerMaker.warn(o.toString());
        }
    }   

    public static boolean isOnprem = false;
    static long lastLogSyncOffsetMRS;
    static boolean syncImmediately = false;
    static boolean fetchAllSTI = true;
    static Map<Integer, AccountInfo> accountInfoMap =  new HashMap<>();

    static boolean isDashboardInstance = false;

    public static boolean tryForCollectionName(String message) {
        boolean ret = false;
        try {
            Gson gson = new Gson();

            Map<String, Object> json = gson.fromJson(message, Map.class);

            // loggerMaker.info("Json size: " + json.size());
            boolean withoutCidrCond = json.containsKey(GROUP_NAME) && json.containsKey(VXLAN_ID);
            boolean withCidrCond = json.containsKey(GROUP_NAME) && json.containsKey(VXLAN_ID) && json.containsKey(VPC_CIDR);
            if (withCidrCond || withoutCidrCond) {
                ret = true;
                String groupName = (String) (json.get(GROUP_NAME));
                String vxlanIdStr = ((Double) json.get(VXLAN_ID)).intValue() + "";
                int vxlanId = Integer.parseInt(vxlanIdStr);
                dataActor.updateApiCollectionNameForVxlan(vxlanId, groupName);

                if (json.containsKey(VPC_CIDR)) {
                    List<String> cidrList = (List<String>) json.get(VPC_CIDR);
                    loggerMaker.info("cidrList: " + cidrList);
                    dataActor.updateCidrList(cidrList);
                }
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "error in try collection" + e);
        }

        return ret;
    }



    public static void insertRuntimeFilters() {
        RuntimeFilterDao.instance.initialiseFilters();
    }

    public static Kafka kafkaProducer = null;
    public static KafkaProducer<String, byte[]> protobufKafkaProducer = null;
    public static Kafka localKafkaProducer = null;
    
    private static void buildKafka() {
        loggerMaker.info("Building kafka...................");
        AccountSettings accountSettings = dataActor.fetchAccountSettings();
        if (accountSettings != null && accountSettings.getCentralKafkaIp()!= null) {
            String centralKafkaBrokerUrl = accountSettings.getCentralKafkaIp();
            int centralKafkaBatchSize = AccountSettings.DEFAULT_CENTRAL_KAFKA_BATCH_SIZE;
            int centralKafkaLingerMS = AccountSettings.DEFAULT_CENTRAL_KAFKA_LINGER_MS;
            if (centralKafkaBrokerUrl != null) {
                // Get Kafka authentication credentials from environment variables
                kafkaProducer = new Kafka(centralKafkaBrokerUrl, centralKafkaLingerMS, centralKafkaBatchSize, kafkaUsername, kafkaPassword, isKafkaAuthenticationEnabled);
                loggerMaker.info("Connected to central kafka @ " + Context.now());
            }
        } else {
            loggerMaker.info(String.valueOf(accountSettings));
        }
    }
    
    private static boolean isProtoKafkaEnabled() {
        if (Context.getActualAccountId() == 1752208054 || Context.getActualAccountId() == 1753806619 || Context.getActualAccountId() == 1757403870 || Context.getActualAccountId() == 1758787662 || isSendToThreatEnabled) {
            return true;
        }
        return false;
    }

    private static void buildProtobufKafkaProducer(String kafkaBrokerUrl) {
        if(!isProtoKafkaEnabled()){
            loggerMaker.info("Skipping proto kafka producer init");
            return;
        }
        loggerMaker.info("Building protobuf kafka producer...................");
        // String kafkaBrokerUrl = "kafka1:19092";
        int batchSize = AccountSettings.DEFAULT_CENTRAL_KAFKA_BATCH_SIZE;
        int lingerMS = AccountSettings.DEFAULT_CENTRAL_KAFKA_LINGER_MS;
        
        try {
            Properties kafkaProps = KafkaConfig.createProducerProperties(
                kafkaBrokerUrl, lingerMS, batchSize, 5000, 1,
                isKafkaAuthenticationEnabled, kafkaUsername, kafkaPassword
            );

            if (kafkaProps == null) {
                loggerMaker.errorAndAddToDb("Failed to create Kafka producer properties");
                return;
            }

            kafkaProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
            kafkaProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

            protobufKafkaProducer = new KafkaProducer<>(kafkaProps);
            loggerMaker.info("Connected to protobuf kafka producer @ " + Context.now());
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error building protobuf kafka producer: " + e.getMessage());
        }
    }

    private static void buildLocalKafkaProducer(String kafkaBrokerUrl) {
        loggerMaker.info("Building local kafka producer...................");
        int batchSize = AccountSettings.DEFAULT_CENTRAL_KAFKA_BATCH_SIZE;
        int lingerMS = AccountSettings.DEFAULT_LOCAL_KAFKA_LINGER_MS;
        try {
            localKafkaProducer = new Kafka(kafkaBrokerUrl, lingerMS, batchSize, kafkaUsername, kafkaPassword, isKafkaAuthenticationEnabled);
            loggerMaker.info("Connected to local kafka producer @ " + Context.now() + ", producerReady=" + localKafkaProducer.producerReady);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error building local kafka producer: " + e.getMessage());
        }
    }

    public static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public static class AccountInfo {
        long estimatedCount;
        int lastEstimatedCountTime;
        AccountSettings accountSettings;

        public AccountInfo() {
            this.estimatedCount = 0;
            this.lastEstimatedCountTime = 0;
            this.accountSettings = null;
        }

        public AccountInfo(long estimatedCount, int lastEstimatedCountTime, AccountSettings accountSettings) {
            this.estimatedCount = estimatedCount;
            this.lastEstimatedCountTime = lastEstimatedCountTime;
            this.accountSettings = accountSettings;
        }

        public AccountSettings getAccountSettings() {
            return accountSettings;
        }

        public void setAccountSettings(AccountSettings accountSettings) {
            this.accountSettings = accountSettings;
            if (accountSettings != null) {
                loggerMaker.info("Received " + accountSettings.convertApiCollectionNameMapperToRegex().size() + " apiCollectionNameMappers");
            }
        }
    }

    public static String getLogTopicName() {
        String topicName = System.getenv("AKTO_KAFKA_LOG_TOPIC_NAME");
        if (topicName == null) {
            topicName = "akto.api.producer.logs";
        }
        return topicName;
    }


    private static final String LOG_GROUP_ID = "-log";


    public static String customMiniRuntimeServiceName;
    private static final String podName = System.getenv().getOrDefault("POD_NAME", "");
    private static final String nodeName = System.getenv().getOrDefault("NODE_NAME", "");
    private static final String miniRuntimeName = System.getenv().getOrDefault("MINI_RUNTIME_NAME", "");
    static {
        if (!miniRuntimeName.isEmpty()) {
            // Highest priority: explicit MINI_RUNTIME_NAME
            customMiniRuntimeServiceName = miniRuntimeName;
        } else if (!podName.isEmpty() && !nodeName.isEmpty()) {
            // Second priority: pod and node name
            customMiniRuntimeServiceName = "akto-mr:" + podName + ":" + nodeName;
        } else {
            // Fallback: random UUID
            customMiniRuntimeServiceName = "Default_" + UUID.randomUUID().toString();
        }
    }

    static private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    /**
     * Check if fast-discovery integration is enabled via environment variable.
     */
    private static boolean isFastDiscoveryEnabled() {
        String enabled = System.getenv("ENABLE_FAST_DISCOVERY");
        return enabled != null && enabled.equalsIgnoreCase("true");
    }

    /**
     * Find the mini-runtime JAR (which contains embedded fast-discovery classes).
     * Fast-discovery runs as a separate process using the same JAR with -cp.
     *
     * @return Path to mini-runtime JAR, or null if not found
     */
    private static String findMiniRuntimeJar() {
        // Try multiple possible locations
        String[] possiblePaths = {
            // Docker deployment location
            "/app/mini-runtime-1.0-SNAPSHOT-jar-with-dependencies.jar",
            // Maven build location (development)
            "apps/mini-runtime/target/mini-runtime-1.0-SNAPSHOT-jar-with-dependencies.jar",
            // Environment variable override
            System.getenv("MINI_RUNTIME_JAR_PATH")
        };

        for (String path : possiblePaths) {
            if (path != null && new java.io.File(path).exists()) {
                return path;
            }
        }

        loggerMaker.errorAndAddToDb("Mini-runtime JAR not found. Tried locations: " +
            String.join(", ", possiblePaths));
        return null;
    }

    // REFERENCE: https://www.oreilly.com/library/view/kafka-the-definitive/9781491936153/ch04.html (But how do we Exit?)
    public static void main(String[] args) {
        //String mongoURI = System.getenv("AKTO_MONGO_CONN");;
        String configName = System.getenv("AKTO_CONFIG_NAME");
        String topicName = KafkaConfig.getTopicName();
        String kafkaBrokerUrl = System.getenv().getOrDefault("AKTO_KAFKA_BROKER_URL","kafka1:19092");
        String isKubernetes = System.getenv("IS_KUBERNETES");
        if (isKubernetes != null && isKubernetes.equalsIgnoreCase("true")) {
            loggerMaker.infoAndAddToDb("is_kubernetes: true");
            kafkaBrokerUrl = System.getenv().getOrDefault("AKTO_KAFKA_BROKER_URL", "127.0.0.1:29092");
        }
        final String brokerUrlFinal = kafkaBrokerUrl;
        String groupIdConfig =  System.getenv("AKTO_KAFKA_GROUP_ID_CONFIG") != null
                ? System.getenv("AKTO_KAFKA_GROUP_ID_CONFIG")
                : "asdf";
        boolean syncImmediately = false;
        boolean fetchAllSTI = true;
        Map<Integer, AccountInfo> accountInfoMap =  new HashMap<>();

        // Fast-Discovery integration (as child process)
        Process fastDiscoveryProcess = null;

        boolean isDashboardInstance = false;
        if (isDashboardInstance) {
            syncImmediately = true;
            fetchAllSTI = false;
        }
        int maxPollRecordsConfigTemp = Integer.parseInt(System.getenv("AKTO_KAFKA_MAX_POLL_RECORDS_CONFIG") != null
                ? System.getenv("AKTO_KAFKA_MAX_POLL_RECORDS_CONFIG")
                : "100");

        AccountSettings aSettings = dataActor.fetchAccountSettings();
        if (aSettings == null) {
            loggerMaker.errorAndAddToDb("error fetch account settings, exiting process");
            System.exit(0);
            return;
        }
        int accountId = aSettings.getId();
        Context.setActualAccountId(accountId);
        loggerMaker.infoAndAddToDb("Fetched account settings for account " + Context.getActualAccountId());

        if (Context.getActualAccountId() == 1759692400) {
            maxPollRecordsConfigTemp = 5000;
        }

        int maxPollRecordsConfig = maxPollRecordsConfigTemp;

        DataControlFetcher.init(dataActor);

        aSettings = dataActor.fetchAccountSettings();
        ModuleInfoWorker.init(ModuleInfo.ModuleType.MINI_RUNTIME, dataActor, customMiniRuntimeServiceName);
        LoggerMaker.setModuleId(customMiniRuntimeServiceName);
        //DaoInit.init(new ConnectionString(mongoURI));
        // DictionaryFilter.readDictionaryBinary();

        loggerMaker.infoAndAddToDb("Runtime starting at " + Context.now() + "....");

        dataActor.modifyHybridSaasSetting(RuntimeMode.isHybridDeployment());

        initializeRuntime();

        // Fast-Discovery Integration (optional) - Run as separate process from embedded classes
        if (isFastDiscoveryEnabled()) {
            try {
                loggerMaker.infoAndAddToDb("Fast-Discovery is ENABLED - starting as separate process...");

                // Find mini-runtime JAR (which contains embedded fast-discovery classes)
                String miniRuntimeJar = findMiniRuntimeJar();
                if (miniRuntimeJar == null) {
                    throw new Exception("Mini-runtime JAR not found. Cannot start fast-discovery.");
                }

                // Build process: use -cp to run fast-discovery main class from embedded classes
                ProcessBuilder processBuilder = new ProcessBuilder(
                    "java",
                    "-Xmx512m",  // Limit memory for fast-discovery (lightweight)
                    "-cp",
                    miniRuntimeJar,
                    "com.akto.fast_discovery.Main"  // Fast-discovery main class
                );

                // Inherit all environment variables from mini-runtime
                processBuilder.environment().putAll(System.getenv());

                // CRITICAL: Override consumer group to ensure fast-discovery uses separate group
                // Mini-runtime uses: AKTO_KAFKA_GROUP_ID_CONFIG=asdf
                // Fast-discovery must use: AKTO_KAFKA_GROUP_ID_CONFIG=fast-discovery
                processBuilder.environment().put("AKTO_KAFKA_GROUP_ID_CONFIG", "fast-discovery");

                // Output goes to stdout/stderr (captured by Docker logs)
                processBuilder.inheritIO();

                // Start the process
                fastDiscoveryProcess = processBuilder.start();

                // Verify process started successfully
                Thread.sleep(2000); // Wait 2 seconds
                if (!fastDiscoveryProcess.isAlive()) {
                    int exitCode = fastDiscoveryProcess.exitValue();
                    throw new Exception("Fast-discovery process exited immediately with code " + exitCode);
                }

            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Failed to start Fast-Discovery process: " + e.getMessage());
                e.printStackTrace();
                // Continue without fast-discovery
                fastDiscoveryProcess = null;
            }
        }

        String centralKafkaTopicName = AccountSettings.DEFAULT_CENTRAL_KAFKA_TOPIC_NAME;

        buildKafka();
        buildProtobufKafkaProducer(brokerUrlFinal);
        buildLocalKafkaProducer(kafkaBrokerUrl);

        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                if (kafkaProducer == null || !kafkaProducer.producerReady) {
                    buildKafka();
                }
                // Also check protobuf producer
                if (protobufKafkaProducer == null) {
                    buildProtobufKafkaProducer(brokerUrlFinal);
                }
                // Also check local producer
                if (localKafkaProducer == null || !localKafkaProducer.producerReady) {
                    buildLocalKafkaProducer(brokerUrlFinal);
                }
            }
        }, 5, 5, TimeUnit.MINUTES);

        final boolean checkPg = aSettings != null && aSettings.isRedactPayload();

        AllMetrics.instance.init(LogDb.RUNTIME, checkPg, dataActor, Context.getActualAccountId(), customMiniRuntimeServiceName);
        loggerMaker.infoAndAddToDb("All metrics initialized");

        dataActor.modifyHybridSaasSetting(RuntimeMode.isHybridDeployment());

        APIConfig apiConfig = dataActor.fetchApiConfig(configName);
        if (apiConfig == null) {
            apiConfig = new APIConfig(configName,"access-token", 1, 10_000_000, sync_threshold_time); // this sync threshold time is used for deleting sample data
        }

        final Main main = new Main();
        Properties properties = Main.configProperties(kafkaBrokerUrl, groupIdConfig, maxPollRecordsConfig);
        main.consumer = new KafkaConsumer<>(properties);

        final Thread mainThread = Thread.currentThread();
        final AtomicBoolean exceptionOnCommitSync = new AtomicBoolean(false);

        // Make fast-discovery process final for shutdown hook access
        final Process finalFastDiscoveryProcess = fastDiscoveryProcess;

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                // Stop Fast-Discovery process first if running
                if (finalFastDiscoveryProcess != null && finalFastDiscoveryProcess.isAlive()) {
                    try {
                        // Send SIGTERM (graceful shutdown)
                        finalFastDiscoveryProcess.destroy();

                        // Wait up to 15 seconds for graceful shutdown
                        boolean exited = finalFastDiscoveryProcess.waitFor(15, java.util.concurrent.TimeUnit.SECONDS);

                        if (!exited) {
                            // Force kill if still alive after 15 seconds
                            loggerMaker.errorAndAddToDb("Fast-Discovery did not stop gracefully within 15 seconds, force killing...");
                            finalFastDiscoveryProcess.destroyForcibly();
                            finalFastDiscoveryProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                        } else {
                            int exitCode = finalFastDiscoveryProcess.exitValue();
                            loggerMaker.infoAndAddToDb("Fast-Discovery process stopped gracefully (exit code: " + exitCode + ")");
                        }
                    } catch (InterruptedException e) {
                        loggerMaker.errorAndAddToDb("Interrupted while waiting for Fast-Discovery to stop");
                        finalFastDiscoveryProcess.destroyForcibly();
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb("Error stopping Fast-Discovery process: " + e.getMessage());
                        e.printStackTrace();
                    }
                }

                // Stop mini-runtime
                main.consumer.wakeup();
                try {
                    if (!exceptionOnCommitSync.get()) {
                        mainThread.join();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (Error e){
                    loggerMaker.errorAndAddToDb("Error in main thread: "+ e.getMessage());
                }
                
                // Close protobuf producer
                if (protobufKafkaProducer != null) {
                    protobufKafkaProducer.close();
                    loggerMaker.info("Closed protobuf kafka producer");
                }
            }
        });


        scheduler.scheduleAtFixedRate(()-> {
            try {

                Map<MetricName, ? extends Metric> metrics = main.consumer.metrics();

                for (Map.Entry<MetricName, ? extends Metric> entry : metrics.entrySet()) {
                    MetricName key = entry.getKey();
                    Metric value = entry.getValue();

                    // Only process consumer-level metrics (not per-partition metrics)
                    // Per-partition metrics have a "partition" tag, consumer-level metrics don't
                    if (key.tags().containsKey("partition")) {
                        continue;
                    }

                    // Also skip per-topic metrics - we only want consumer-level aggregated metrics
                    if (key.tags().containsKey("topic")) {
                        continue;
                    }

                    if(key.name().equals("records-lag-max")){
                        double val = value.metricValue().equals(Double.NaN) ? 0d: (double) value.metricValue();
                        AllMetrics.instance.setKafkaRecordsLagMax((float) val);
                    }
                    if(key.name().equals("records-consumed-rate")){
                        double val = value.metricValue().equals(Double.NaN) ? 0d: (double) value.metricValue();
                        AllMetrics.instance.setKafkaRecordsConsumedRate((float) val);
                    }

                    if(key.name().equals("fetch-latency-avg")){
                        double val = value.metricValue().equals(Double.NaN) ? 0d: (double) value.metricValue();
                        AllMetrics.instance.setKafkaFetchAvgLatency((float) val);
                    }

                    if(key.name().equals("bytes-consumed-rate")){
                        double val = value.metricValue().equals(Double.NaN) ? 0d: (double) value.metricValue();
                        AllMetrics.instance.setKafkaBytesConsumedRate((float) val);
                    }
                }

                if (checkPg) {
                    long dbSizeInMb = clientLayer.fetchTotalSize();
                    AllMetrics.instance.setPgDataSizeInMb(dbSizeInMb);
                    loggerMaker.infoAndAddToDb("Postgres size: " + dbSizeInMb + " MB");
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Failed to get total number of records from postgres");
            }

        }, 0, 1, TimeUnit.MINUTES);

        Map<String, HttpCallParser> httpCallParserMap = new HashMap<>();

        // sync infra metrics thread
        // ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        // KafkaHealthMetricSyncTask task = new KafkaHealthMetricSyncTask(main.consumer);
        // executor.scheduleAtFixedRate(task, 2, 60, TimeUnit.SECONDS);

        String kafkaUrl = kafkaBrokerUrl;
        executorService.schedule(new Runnable() {
            public void run() {
                try {
                    loggerMaker.infoAndAddToDb("Starting traffic log consumer");
                    String logTopicName = getLogTopicName();
                    
                    Properties logConsumerProps = Main.configProperties(kafkaUrl, groupIdConfig + LOG_GROUP_ID,maxPollRecordsConfig);
                    KafkaConsumer<String, String> logConsumer = new KafkaConsumer<>(logConsumerProps);
                    long lastLogSyncOffset = 0;
                    try {
                        logConsumer.subscribe(Collections.singletonList(logTopicName));
                        loggerMaker.infoAndAddToDb("Second consumer subscribed to " + logTopicName);
                        while (true) {
                            ConsumerRecords<String, String> records = logConsumer.poll(Duration.ofMillis(10000));
                            try {
                                logConsumer.commitSync();
                            } catch (Exception e) {
                                throw e;
                            }
                            for (ConsumerRecord<String, String> record : records) {
                                try {
                                    lastLogSyncOffset++;
                                    TrafficProducerLog trafficProducerLog = SampleParser.parseLogMessage(record.value());
                                    if (trafficProducerLog == null || trafficProducerLog.getMessage() == null) {
                                        loggerMaker.errorAndAddToDb("Traffic producer log is null");
                                        continue;
                                    }
                                    String message = String.format("[TRAFFIC_PRODUCER] [%s] %s", trafficProducerLog.getSource(), trafficProducerLog.getMessage());

                                    if (trafficProducerLog.getLogType() != null && trafficProducerLog.getLogType().equalsIgnoreCase("ERROR")) {
                                        loggerMaker.errorAndAddToDb(message);
                                    } else if (trafficProducerLog.getLogType() != null && trafficProducerLog.getLogType().equalsIgnoreCase("DEBUG")) {
                                        loggerMaker.debug(message);
                                    } else {
                                        loggerMaker.infoAndAddToDb(message);
                                    }

                                    if (lastLogSyncOffset % 100 == 0) {
                                        loggerMaker.info("Committing log offset at position: " + lastLogSyncOffset);
                                    }

                                } catch (Exception e) {
                                    loggerMaker.errorAndAddToDb(e, "Error while parsing traffic producer log kafka message " + e);
                                    continue;
                                }
                            }
                        }
                    } catch (WakeupException ignored) {
                        // Shutdown
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(e, "Error in second topic consumer");
                    } finally {
                        logConsumer.close();
                    }
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Error while starting traffic log consumer");
                }
            }
        }, 0, TimeUnit.SECONDS);

        // Pod heartbeat consumer thread
        String heartbeatTopicName = "akto.daemonset.producer.heartbeats";
        new AktoTrafficCollectorTelemetry(kafkaUrl, groupIdConfig, maxPollRecordsConfig, heartbeatTopicName, dataActor, customMiniRuntimeServiceName).run();


        String configUpdateTopicName = "akto.config.updates";
        ConfigUpdatePoller configUpdatePoller = new ConfigUpdatePoller(customMiniRuntimeServiceName, localKafkaProducer, configUpdateTopicName);
        configUpdatePoller.start();

        String runMcpJobs = System.getenv("AKTO_RUN_MCP_JOBS");
        boolean shouldRunMcpJobs = true;
        if (runMcpJobs != null && runMcpJobs.equalsIgnoreCase("false")) {
            shouldRunMcpJobs = false;
        }
        if (shouldRunMcpJobs) {
            // schedule MCP sync job for 24 hours
            loggerMaker.info("Scheduling MCP Sync Job");
            APIConfig finalApiConfig = apiConfig;
            scheduler.scheduleAtFixedRate(() -> {
                Context.accountId.set(Context.getActualAccountId());
                try {
                    loggerMaker.infoAndAddToDb("Executing MCP Tools Sync job");
                    McpToolsSyncJobExecutor.INSTANCE.runJob(finalApiConfig);
                    loggerMaker.infoAndAddToDb("Finished executing MCP Tools Sync job");
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Error while executing MCP Tools Sync Job");
                }
            }, 0, 24, TimeUnit.HOURS);

            // schedule MCP Recon Sync job for 2 mins
            loggerMaker.info("Scheduling MCP Recon Sync Job");
            APIConfig finalApiConfigRecon = apiConfig;
            scheduler.scheduleAtFixedRate(() -> {
                Context.accountId.set(Context.getActualAccountId());
                try {
                    loggerMaker.infoAndAddToDb("Executing MCP Recon Sync job");
                    McpReconSyncJobExecutor.INSTANCE.runJob(finalApiConfigRecon);
                    loggerMaker.infoAndAddToDb("Finished executing MCP Recon Sync job");
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Error while executing MCP Recon Sync Job");
                }
            }, 0, 24, TimeUnit.HOURS);
        }

        if(Context.getActualAccountId() == 1759386565 ){
            // Schedule bloom filter reset job every 30 minutes
            loggerMaker.infoAndAddToDb("Scheduling Bloom Filter Reset Job");
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    loggerMaker.infoAndAddToDb("Resetting all bloom filters");
                    FilterUpdates.resetAllFilters();
                    loggerMaker.infoAndAddToDb("Finished resetting bloom filters");
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Error while resetting bloom filters");
                }
            }, FilterUpdates.FULL_RESET_DURATION_MINUTES, FilterUpdates.FULL_RESET_DURATION_MINUTES, TimeUnit.MINUTES);
        }

        if(isDbMergingModeEnabled()){
            runDBMaintenanceJob(apiConfig);
        }else{
            kafkaSubscribeAndProcess(topicName, syncImmediately, fetchAllSTI, accountInfoMap, isDashboardInstance, centralKafkaTopicName,
                    apiConfig, main, exceptionOnCommitSync, httpCallParserMap);
        }
    }

    public static boolean isDbMergingModeEnabled(){
        return System.getenv().getOrDefault("DB_MERGING_MODE", "false").equalsIgnoreCase("true");
    }

    /**
     * Main method of mini runtime where traffic kafka topic consumer does processing.
     */
    private static void kafkaSubscribeAndProcess(String topicName, boolean syncImmediately, boolean fetchAllSTI,
            Map<Integer, AccountInfo> accountInfoMap, boolean isDashboardInstance, String centralKafkaTopicName,
            APIConfig apiConfig, final Main main, final AtomicBoolean exceptionOnCommitSync,
            Map<String, HttpCallParser> httpCallParserMap) {
        long lastSyncOffset = 0;
        try {
            main.consumer.subscribe(Arrays.asList(topicName));
            loggerMaker.infoAndAddToDb("Consumer subscribed to topic: " + topicName);
            while (true) {
                ConsumerRecords<String, String> records = main.consumer.poll(Duration.ofMillis(10000));
                try {
                    main.consumer.commitSync();
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Error while committing offset: " + e.getMessage());
                    throw e;
                }
                long start = System.currentTimeMillis();
                // TODO: what happens if exception
                Map<String, List<HttpResponseParams>> responseParamsToAccountMap = new HashMap<>();
                lastSyncOffset = bulkParseTrafficToResponseParams(lastSyncOffset, records, responseParamsToAccountMap);

                handleResponseParams(responseParamsToAccountMap,
                    accountInfoMap,
                    isDashboardInstance,
                    httpCallParserMap,
                    apiConfig,
                    fetchAllSTI,
                    syncImmediately,
                    centralKafkaTopicName);
                long deltaTime = System.currentTimeMillis() - start;
                AllMetrics.instance.setRuntimeProcessLatency(deltaTime);
                AllMetrics.instance.setRuntimeApiReceivedCount((float) records.count());
            }

        } catch (WakeupException ignored) {
          // nothing to catch. This exception is called from the shutdown hook.
          loggerMaker.error("Kafka consumer closed due to wakeup exception");
        } catch (Exception e) {
            exceptionOnCommitSync.set(true);
            printL(e);
            loggerMaker.errorAndAddToDb(e, "Error in main runtime: " + e.getMessage());
            e.printStackTrace();
            System.exit(0);
        } finally {
            loggerMaker.warn("Closing kafka consumer for topic: " + topicName);
            main.consumer.close();
        }
    }

    private static int LOG_DEBUG_RECORDS = 100; 
    private static long bulkParseTrafficToResponseParams(long lastSyncOffset, ConsumerRecords<String, String> records,
            Map<String, List<HttpResponseParams>> responseParamsToAccountMap) {
        for (ConsumerRecord<String,String> r: records) {
            HttpResponseParams httpResponseParams;
            try {
                 
                printL(r.value());
                if(LOG_DEBUG_RECORDS > 0){
                    LOG_DEBUG_RECORDS--;
                    loggerMaker.infoAndAddToDb("Kafka record received" + r.value());
                }
                AllMetrics.instance.setRuntimeKafkaRecordCount(1);
                AllMetrics.instance.setRuntimeKafkaRecordSize(r.value().length());

                lastSyncOffset++;
                if (DataControlFetcher.stopIngestionFromKafka()) {
                    continue;
                }

                if (Context.getActualAccountId() != 1759692400 && lastSyncOffset % 100 == 0) {
                    loggerMaker.infoAndAddToDb("Committing offset at position: " + lastSyncOffset);
                }

                if (Context.getActualAccountId() == 1759692400 && lastSyncOffset % 1000 == 0) {
                    loggerMaker.infoAndAddToDb("Committing offset at position: " + lastSyncOffset);
                }

                if (Context.getActualAccountId() != 1759692400 && tryForCollectionName(r.value())) {
                    continue;
                }
                
                httpResponseParams = HttpCallParser.parseKafkaMessage(r.value());
                if (httpResponseParams == null) {
                    loggerMaker.error("HttpResponse params was skipped due to invalid json requestBody");
                    continue;
                }

                if (httpResponseParams.getRequestParams().getURL().contains("api/ingestData")) {
                    continue;
                }

                if (Context.getActualAccountId() == 1759692400) {
                    String debugHeader = HttpCallParser.getHeaderValue(httpResponseParams.getRequestParams().getHeaders(), "x-debug-trace");
                    if (debugHeader != null && !debugHeader.isEmpty()) {
                        String host = HttpCallParser.getHeaderValue(httpResponseParams.getRequestParams().getHeaders(), "host");
                        loggerMaker.infoAndAddToDb("HttpResponseparam received with url: "
                                + httpResponseParams.getRequestParams().getURL() + " host: " + (host != null ? host : "null")
                                + " statusCode: " + httpResponseParams.getStatusCode());
                    }
                }
                
                HttpRequestParams requestParams = httpResponseParams.getRequestParams();
                String debugHost = Utils.printDebugHostLog(httpResponseParams);
                // if (debugHost != null) {
                //     loggerMaker.infoAndAddToDb("Found debug host: " + debugHost + " in url: " + requestParams.getMethod() + " " + requestParams.getURL());
                // }
                // if (Utils.printDebugUrlLog(requestParams.getURL())) {
                //     loggerMaker.infoAndAddToDb("Found debug url: " + requestParams.getURL());
                // }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error while parsing kafka message: " + r.value() + e);
                continue;
            }
            String accountId = httpResponseParams.getAccountId();
            if (!responseParamsToAccountMap.containsKey(accountId)) {
                responseParamsToAccountMap.put(accountId, new ArrayList<>());
            }
            responseParamsToAccountMap.get(accountId).add(httpResponseParams);
        }
    return lastSyncOffset;
    }

    /**
     * This method is used to run the postgres db sample data merging job.
     * This must run on only one special instance of mini runtime.
     * Control to enable this is done by setting the DB_MERGING_MODE environment variable to true.
     * @param apiConfig
     */
    public static void runDBMaintenanceJob(APIConfig apiConfig) {
        APICatalogSync apiCatalogSync = new APICatalogSync(apiConfig.getUserIdentifier(), apiConfig.getThreshold(), fetchAllSTI);
        while (true) {
            try {
                loggerMaker.info("Running sql merging job");

                AccountInfo accountInfo = refreshAccountInfo(accountInfoMap, Context.accountId.get());
                if (!accountInfo.accountSettings.isRedactPayload()) {
                    loggerMaker.warn("Sql merging skipped due to redaction disabled in account:"
                            + accountInfo.getAccountSettings().getId());
                    continue;
                }

                MergeLogicLocal.mergingJob(apiCatalogSync.dbState);
                loggerMaker.info("Completed sql merging job");

                apiCatalogSync.refreshDbState(fetchAllSTI);

                // Sleep 1 minutes to simulate earlier syncWithDB frequency.
                Thread.sleep(1 * 60 * 1000);

                
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error in sql merging job");
            }
        }
    }

    public static void handleResponseParams(Map<String, List<HttpResponseParams>> responseParamsToAccountMap,
        Map<Integer, AccountInfo> accountInfoMap, boolean isDashboardInstance,
        Map<String, HttpCallParser> httpCallParserMap, APIConfig apiConfig, boolean fetchAllSTI,
        boolean syncImmediately, String centralKafkaTopicName) {
        for (String accountId: responseParamsToAccountMap.keySet()) {
            int accountIdInt;
            try {
                accountIdInt = Integer.parseInt(accountId);
            } catch (Exception ignored) {
                loggerMaker.errorAndAddToDb("Account id not string");
                continue;
            }

            Context.accountId.set(accountIdInt);

            AccountInfo accountInfo = refreshAccountInfo(accountInfoMap, accountIdInt);

            if (!isDashboardInstance && accountInfo.estimatedCount> 20_000_000) {
                loggerMaker.infoAndAddToDb("STI count is greater than 20M, skipping");
                continue;
            }

            if (!httpCallParserMap.containsKey(accountId)) {
                HttpCallParser parser = new HttpCallParser(
                        apiConfig.getUserIdentifier(), apiConfig.getThreshold(), apiConfig.getSync_threshold_count(),
                        apiConfig.getSync_threshold_time(), fetchAllSTI
                );
                httpCallParserMap.put(accountId, parser);
                loggerMaker.infoAndAddToDb("New parser created for account: " + accountId);
            }

            HttpCallParser parser = httpCallParserMap.get(accountId);

            try {
                List<HttpResponseParams> accWiseResponse = responseParamsToAccountMap.get(accountId);

                // send to protobuf kafka topic (separate from central kafka)
                //loggerMaker.infoAndAddToDb("Sending " + accWiseResponse.size() +" records to protobuf kafka topic");
                for (HttpResponseParams httpResponseParams: accWiseResponse) {
                    try {
                        sendToProtobufKafka(httpResponseParams);
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(e, "Error sending to protobuf kafka: " + e.getMessage());
                    }
                }

                accWiseResponse = filterBasedOnHeaders(accWiseResponse, accountInfo.accountSettings);
                loggerMaker.infoAndAddToDb("Initiating sync function for account: " + accountId);
                parser.syncFunction(accWiseResponse, syncImmediately, fetchAllSTI, accountInfo.accountSettings);
                loggerMaker.infoAndAddToDb("Sync function completed for account: " + accountId);

                // Save raw agent traffic logs to MongoDB for future training (boolean feature flag)
                try {
                    Organization organization = OrgUtils.getOrganizationCached(Context.getActualAccountId());
                    if (organization != null && organization.getFeatureWiseAllowed() != null) {
                        FeatureAccess featureAccess = organization.getFeatureWiseAllowed().get("AGENT_TRAFFIC_LOGS");
                        if (featureAccess != null && featureAccess.getIsGranted()) {
                            saveAgentTrafficLogs(accWiseResponse);
                        }
                    }
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Error saving agent traffic logs: " + e.getMessage());
                }

                sendToCentralKafka(centralKafkaTopicName, accWiseResponse);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error in handleResponseParams: " + e.toString());
            }
        }
    }


    private static void sendToCentralKafka(String centralKafkaTopicName, List<HttpResponseParams> accWiseResponse) {
        // send to central kafka
        if (kafkaProducer != null) {
            loggerMaker.infoAndAddToDb("Sending " + accWiseResponse.size() +" records to context analyzer");
            for (HttpResponseParams httpResponseParams: accWiseResponse) {
                try {
                    loggerMaker.debugInfoAddToDb("Sending to kafka data for account: " + httpResponseParams.getAccountId());
                    kafkaProducer.send(httpResponseParams.getOrig(), centralKafkaTopicName);
                } catch (Exception e) {
                    // force close it
                    loggerMaker.errorAndAddToDb(e, "Closing kafka: " + e.getMessage());
                    kafkaProducer.close();
                    loggerMaker.infoAndAddToDb("Successfully closed kafka");
                }
            }
        } else {
            loggerMaker.error("Kafka producer is null");
        }
    }

    private static AccountInfo refreshAccountInfo(Map<Integer, AccountInfo> accountInfoMap, int accountIdInt) {
        AccountInfo accountInfo = accountInfoMap.computeIfAbsent(accountIdInt, k -> new AccountInfo());

        if ((Context.now() - accountInfo.lastEstimatedCountTime) > 60*60) {
            loggerMaker.infoAndAddToDb("current time: " + Context.now() + " lastEstimatedCountTime: " + accountInfo.lastEstimatedCountTime);
            accountInfo.lastEstimatedCountTime = Context.now();
            accountInfo.estimatedCount = dataActor.fetchEstimatedDocCount();
            accountInfo.setAccountSettings(dataActor.fetchAccountSettings());
            loggerMaker.infoAndAddToDb("STI Estimated count: " + accountInfo.estimatedCount);
        }
        return accountInfo;
    }

    public static List<HttpResponseParams> filterBasedOnHeaders(List<HttpResponseParams> accWiseResponse,
            AccountSettings accountSettings) {

        if (accountSettings != null) {
            Map<String, String> filterHeaders = accountSettings.getFilterHeaderValueMap();
            if (filterHeaders != null && !filterHeaders.isEmpty()) {
                List<HttpResponseParams> accWiseResponseFiltered = new ArrayList<HttpResponseParams>();
                for(HttpResponseParams accWiseResponseEntry : accWiseResponse) {
                    Map<String, List<String>> reqHeaders = accWiseResponseEntry.getRequestParams().getHeaders();
                    Map<String, List<String>> resHeaders = accWiseResponseEntry.getHeaders();

                    boolean shouldKeep = false;
                    for(Map.Entry<String, String> filterHeaderKV : filterHeaders.entrySet()) {
                        try {
                            List<String> reqHeaderValues = reqHeaders == null ? null : reqHeaders.get(filterHeaderKV.getKey().toLowerCase());
                            List<String> resHeaderValues = resHeaders == null ? null : resHeaders.get(filterHeaderKV.getKey().toLowerCase());


                            boolean isPresentInReq = reqHeaderValues != null && reqHeaderValues.indexOf(filterHeaderKV.getValue()) != -1;
                            boolean isPresentInRes = resHeaderValues != null && resHeaderValues.indexOf(filterHeaderKV.getValue()) != -1;

                            shouldKeep = isPresentInReq || isPresentInRes;

                            if (shouldKeep) {
                                break;
                            }

                        } catch (Exception e) {
                            // eat it
                        }
                    }

                    if (shouldKeep) {
                        accWiseResponseFiltered.add(accWiseResponseEntry);
                    }

                    if (Utils.printDebugUrlLog(accWiseResponseEntry.getRequestParams().getURL())) {
                        loggerMaker.infoAndAddToDb("Found debug url in filterBasedOnHeaders " + accWiseResponseEntry.getRequestParams().getURL() + " shouldKeep: " + shouldKeep);
                    }
                }
                accWiseResponse = accWiseResponseFiltered;
            }

            Map<String, Map<Pattern, String>> apiCollectioNameMapper = accountSettings.convertApiCollectionNameMapperToRegex();
            changeTargetCollection(apiCollectioNameMapper, accWiseResponse);
        }

        return accWiseResponse;
    }

    public static void changeTargetCollection(Map<String, Map<Pattern, String>> apiCollectionNameMapper, List<HttpResponseParams> accWiseResponse) {
        if (apiCollectionNameMapper != null && !apiCollectionNameMapper.isEmpty()) {
            for(HttpResponseParams accWiseResponseEntry : accWiseResponse) {
                Map<String, List<String>> reqHeaders = accWiseResponseEntry.getRequestParams().getHeaders();
                for(String headerName : apiCollectionNameMapper.keySet()) {
                    List<String> reqHeaderValues = reqHeaders == null ? null : reqHeaders.get(headerName);
                    if (reqHeaderValues != null && !reqHeaderValues.isEmpty()) {
                        Map<Pattern, String> apiCollectionNameForGivenHeader = apiCollectionNameMapper.get(headerName);
                        for (Map.Entry<Pattern,String> apiCollectionNameOrigXNew: apiCollectionNameForGivenHeader.entrySet()) {
                            for (int i = 0; i < reqHeaderValues.size(); i++) {
                                String reqHeaderValue = reqHeaderValues.get(i);
                                Pattern regex = apiCollectionNameOrigXNew.getKey();
                                String newValue = apiCollectionNameOrigXNew.getValue();

                                try {
                                    if (regex.matcher(reqHeaderValue).matches() &&
                                            reqHeaders != null) {
                                        reqHeaders.put("host", Collections.singletonList(newValue));

                                        if (Utils.printDebugUrlLog(accWiseResponseEntry.getRequestParams().getURL())) {
                                            loggerMaker.infoAndAddToDb("Found debug url in changeTargetCollection " + accWiseResponseEntry.getRequestParams().getURL() + " newValue: " + newValue + " reqHeaderValue: " + reqHeaderValue);
                                        }
                                    }
                                } catch (Exception e) {
                                    // eat it
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public static void initializeRuntime(){

        Account account = dataActor.fetchActiveAccount();
        int accountId = account.getId();
        Context.accountId.set(accountId);
        Context.setActualAccountId(accountId);

        Setup setup = dataActor.fetchSetup();

        String dashboardMode = "saas";
        if (setup != null) {
            dashboardMode = setup.getDashboardMode();
        }

        isOnprem = dashboardMode.equalsIgnoreCase(DashboardMode.ON_PREM.name());
        
        RuntimeVersion runtimeVersion = new RuntimeVersion();
        try {
            runtimeVersion.updateVersion(AccountSettings.API_RUNTIME_VERSION, dataActor);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "error while updating dashboard version: " + e.getMessage());
        }

        initFromRuntime(accountId);
    }

    public static void initFromRuntime(int accountId) {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                List<CustomDataType> customDataTypes = dataActor.fetchCustomDataTypes();
                loggerMaker.info("customData type " + customDataTypes.size());
                List<AktoDataType> aktoDataTypes = dataActor.fetchAktoDataTypes();
                List<CustomAuthType> customAuthTypes = dataActor.fetchCustomAuthTypes();
                SingleTypeInfo.fetchCustomDataTypes(accountId, customDataTypes, aktoDataTypes);
                SingleTypeInfo.fetchCustomAuthTypes(accountId, customAuthTypes);
            }
        }, 0, 5, TimeUnit.MINUTES);
    }
    
    /**
     * Convert HttpResponseParams to protobuf format and send to akto.api.logs2 topic
     */
    private static void sendToProtobufKafka(HttpResponseParams httpResponseParams) {
        if(!isProtoKafkaEnabled()){
            return;
        }

        try {
            if (protobufKafkaProducer == null) {
                loggerMaker.error("Protobuf Kafka producer is null");
                return;
            }
            
            // Convert HttpResponseParams to protobuf
            HttpResponseParam.Builder protobufBuilder = HttpResponseParam.newBuilder();
            
            // Set basic fields
            if (httpResponseParams.getRequestParams() != null) {
                HttpRequestParams requestParams = httpResponseParams.getRequestParams();
                protobufBuilder.setMethod(requestParams.getMethod() != null ? requestParams.getMethod() : "");
                protobufBuilder.setPath(requestParams.getURL() != null ? requestParams.getURL() : "");
                protobufBuilder.setType(requestParams.getType() != null ? requestParams.getType() : "");
                protobufBuilder.setRequestPayload(requestParams.getPayload() != null ? requestParams.getPayload() : "");
                protobufBuilder.setApiCollectionId(requestParams.getApiCollectionId());
            }
            
            protobufBuilder.setStatusCode(httpResponseParams.getStatusCode());
            protobufBuilder.setStatus(httpResponseParams.getStatus() != null ? httpResponseParams.getStatus() : "");
            protobufBuilder.setResponsePayload(httpResponseParams.getPayload() != null ? httpResponseParams.getPayload() : "");
            protobufBuilder.setTime(httpResponseParams.getTime());
            protobufBuilder.setAktoAccountId(httpResponseParams.getAccountId() != null ? httpResponseParams.getAccountId() : "");
            protobufBuilder.setIp(httpResponseParams.getSourceIP() != null ? httpResponseParams.getSourceIP() : "");
            protobufBuilder.setDestIp(httpResponseParams.getDestIP() != null ? httpResponseParams.getDestIP() : "");
            protobufBuilder.setDirection(httpResponseParams.getDirection() != null ? httpResponseParams.getDirection() : "");
            protobufBuilder.setIsPending(httpResponseParams.getIsPending());
            protobufBuilder.setSource(httpResponseParams.getSource() != null ? httpResponseParams.getSource().name() : "");
            protobufBuilder.setAktoVxlanId(httpResponseParams.getRequestParams() != null ? 
                String.valueOf(httpResponseParams.getRequestParams().getApiCollectionId()) : "");
            
            // Set request headers
            if (httpResponseParams.getRequestParams() != null && httpResponseParams.getRequestParams().getHeaders() != null) {
                for (Map.Entry<String, List<String>> entry : httpResponseParams.getRequestParams().getHeaders().entrySet()) {
                    StringList.Builder stringListBuilder = StringList.newBuilder();
                    stringListBuilder.addAllValues(entry.getValue());
                    protobufBuilder.putRequestHeaders(entry.getKey(), stringListBuilder.build());
                }
            }
            
            // Set response headers
            if (httpResponseParams.getHeaders() != null) {
                for (Map.Entry<String, List<String>> entry : httpResponseParams.getHeaders().entrySet()) {
                    StringList.Builder stringListBuilder = StringList.newBuilder();
                    stringListBuilder.addAllValues(entry.getValue());
                    protobufBuilder.putResponseHeaders(entry.getKey(), stringListBuilder.build());
                }
            }
            
            // Build the protobuf message
            HttpResponseParam protobufMessage = protobufBuilder.build();
            byte[] protobufBytes = protobufMessage.toByteArray();
            
            // Send to kafka
            ProducerRecord<String, byte[]> record = new ProducerRecord<>("akto.api.logs2", protobufBytes);
            protobufKafkaProducer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    loggerMaker.errorAndAddToDb(exception, "Error sending protobuf message to kafka: " + exception.getMessage());
                } else {
                    //loggerMaker.errorAndAddToDb("Successfully sent protobuf message to akto.api.logs2 topic with offset: " + metadata.offset());
                }
            });

            //loggerMaker.errorAndAddToDb("Finished sending data ");       
            
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error converting HttpResponseParams to protobuf: " + e.getMessage());
        }
    }

    /**
     * Save raw agent traffic logs to MongoDB for future training and analysis.
     * Stores unprocessed request/response data with collection context.
     */
    private static void saveAgentTrafficLogs(List<HttpResponseParams> responseParamsList) {
        if (responseParamsList == null || responseParamsList.isEmpty()) {
            return;
        }

        try {
            List<AgentTrafficLog> trafficLogs = new ArrayList<>();
            
            for (HttpResponseParams params : responseParamsList) {
                // Convert HttpResponseParams to AgentTrafficLog
                // Note: isBlocked and threatInfo can be enhanced later when threat detection is integrated
                AgentTrafficLog log = AgentTrafficLog.fromHttpResponseParams(params);
                trafficLogs.add(log);
            }
            
            // Use DataActor to save to MongoDB via cyborg
            if (!trafficLogs.isEmpty()) {
                List<Object> writesForAgentTrafficLogs = new ArrayList<>(trafficLogs);
                dataActor.bulkWriteAgentTrafficLogs(writesForAgentTrafficLogs);
                loggerMaker.infoAndAddToDb("Saved " + trafficLogs.size() + " agent traffic logs to MongoDB");
            }
            
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in saveAgentTrafficLogs: " + e.getMessage());
        }
    }


    public static void createIndices() {
        SingleTypeInfoDao.instance.createIndicesIfAbsent();
        SensitiveSampleDataDao.instance.createIndicesIfAbsent();
        SampleDataDao.instance.createIndicesIfAbsent();
        AgentTrafficLogDao.instance.createIndicesIfAbsent();
    }


    public static Properties configProperties(String kafkaBrokerUrl, String groupIdConfig, int maxPollRecordsConfig) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBrokerUrl);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecordsConfig);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupIdConfig);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        if(Context.getActualAccountId() == 1759692400){
            properties.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, 50 * 1024 * 1024); // 50MB per partition
            properties.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, 100 * 1024 * 1024); // 100MB total
        }
        
        if (isKafkaAuthenticationEnabled) {
            if (!KafkaConfig.addValidatedAuthenticationProperties(properties, kafkaUsername, kafkaPassword)) {
                loggerMaker.errorAndAddToDb("Kafka authentication credentials not provided");
                return null;
            }
        }

        return properties;
    }



    public static void processData(List<HttpResponseParams> data) {
        syncImmediately = true;
        fetchAllSTI = false;
        Map<String, List<HttpResponseParams>> responseParamsToAccountMap = new HashMap<>();

        for(HttpResponseParams payload : data) {
           try {
               printL(payload.toString());

               lastLogSyncOffsetMRS++;
               //TODO: test this
               if (DataControlFetcher.stopIngestionFromKafka()) {
                   continue;
               }

               if (lastLogSyncOffsetMRS % 100 == 0) {
                   loggerMaker.info("Committing offset at position: " + lastLogSyncOffsetMRS);
               }


               HttpRequestParams requestParams = payload.getRequestParams();
               String debugHost = Utils.printDebugHostLog(payload);
               if (debugHost != null) {
                   loggerMaker.infoAndAddToDb("Found debug host: " + debugHost + " in url: " + requestParams.getMethod() + " " + requestParams.getURL());
               }
               if (Utils.printDebugUrlLog(requestParams.getURL())) {
                   loggerMaker.infoAndAddToDb("Found debug url: " + requestParams.getURL());
               }
           } catch (Exception e) {
               String payloadStr = payload != null ? payload.toString() : "null";
               String payloadSnippet = payloadStr.substring(0, Math.min(50000, payloadStr.length()));
               loggerMaker.errorAndAddToDb(e, "Error while parsing kafka message | Payload length: " + payloadStr.length() +
                   " | First 50000 chars: " + payloadSnippet);
               continue;
           }

            String accountId = payload.getAccountId();
            if (!responseParamsToAccountMap.containsKey(accountId)) {
                responseParamsToAccountMap.put(accountId, new ArrayList<>());
            }
            responseParamsToAccountMap.get(accountId).add(payload);

        }
        Map<String, HttpCallParser> httpCallParserMap = new HashMap<>();
        String configName = System.getenv("AKTO_CONFIG_NAME");
        APIConfig apiConfig = dataActor.fetchApiConfig(configName);
        if (apiConfig == null) {
            apiConfig = new APIConfig(configName,"access-token", 1, 10_000_000, sync_threshold_time); // this sync threshold time is used for deleting sample data
        }

        String centralKafkaTopicName = AccountSettings.DEFAULT_CENTRAL_KAFKA_TOPIC_NAME;
        long start = System.currentTimeMillis();
        handleResponseParams(responseParamsToAccountMap,
                accountInfoMap,
                isDashboardInstance,
                httpCallParserMap,
                apiConfig,
                fetchAllSTI,
                syncImmediately,
                centralKafkaTopicName);
        AllMetrics.instance.setRuntimeProcessLatency(System.currentTimeMillis()-start);

    }

}

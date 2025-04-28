package com.akto.hybrid_runtime;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.akto.RuntimeMode;
import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dto.*;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.kafka.Kafka;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.metrics.AllMetrics;
import com.akto.hybrid_parsers.HttpCallParser;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.testing_db_layer_client.ClientLayer;
import com.akto.util.DashboardMode;
import com.google.gson.Gson;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.util.concurrent.atomic.AtomicBoolean;

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

    private static int debugPrintCounter = 500;
    private static void printL(Object o) {
        if (debugPrintCounter > 0) {
            debugPrintCounter--;
            loggerMaker.info(o.toString());
        }
    }   

    public static boolean isOnprem = false;

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
            loggerMaker.errorAndAddToDb("error in try collection" + e, LogDb.RUNTIME);
        }

        return ret;
    }



    public static void insertRuntimeFilters() {
        RuntimeFilterDao.instance.initialiseFilters();
    }

    public static Kafka kafkaProducer = null;
    private static void buildKafka() {
        loggerMaker.info("Building kafka...................");
        AccountSettings accountSettings = dataActor.fetchAccountSettings();
        if (accountSettings != null && accountSettings.getCentralKafkaIp()!= null) {
            String centralKafkaBrokerUrl = accountSettings.getCentralKafkaIp();
            int centralKafkaBatchSize = AccountSettings.DEFAULT_CENTRAL_KAFKA_BATCH_SIZE;
            int centralKafkaLingerMS = AccountSettings.DEFAULT_CENTRAL_KAFKA_LINGER_MS;
            if (centralKafkaBrokerUrl != null) {
                kafkaProducer = new Kafka(centralKafkaBrokerUrl, centralKafkaLingerMS, centralKafkaBatchSize);
                loggerMaker.info("Connected to central kafka @ " + Context.now());
            }
        } else {
            loggerMaker.info(String.valueOf(accountSettings));
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

    public static String getTopicName(){
        String topicName = System.getenv("AKTO_KAFKA_TOPIC_NAME");
        if (topicName == null) {
            topicName = "akto.api.logs";
        }
        // DictionaryFilter.readDictionaryBinary();
        return topicName;
    }

    // REFERENCE: https://www.oreilly.com/library/view/kafka-the-definitive/9781491936153/ch04.html (But how do we Exit?)
    public static void main(String[] args) {
        //String mongoURI = System.getenv("AKTO_MONGO_CONN");;
        String configName = System.getenv("AKTO_CONFIG_NAME");
        String topicName = getTopicName();
        String kafkaBrokerUrl = "kafka1:19092"; //System.getenv("AKTO_KAFKA_BROKER_URL");
        String isKubernetes = System.getenv("IS_KUBERNETES");
        if (isKubernetes != null && isKubernetes.equalsIgnoreCase("true")) {
            loggerMaker.infoAndAddToDb("is_kubernetes: true", LogDb.RUNTIME);
            kafkaBrokerUrl = "127.0.0.1:29092";
        }
        String groupIdConfig =  System.getenv("AKTO_KAFKA_GROUP_ID_CONFIG");
        String instanceType =  System.getenv("AKTO_INSTANCE_TYPE");
        boolean syncImmediately = false;
        boolean fetchAllSTI = true;
        Map<Integer, AccountInfo> accountInfoMap =  new HashMap<>();

        boolean isDashboardInstance = false;
        if (isDashboardInstance) {
            syncImmediately = true;
            fetchAllSTI = false;
        }
        int maxPollRecordsConfig = Integer.parseInt(System.getenv("AKTO_KAFKA_MAX_POLL_RECORDS_CONFIG"));

        AccountSettings aSettings = dataActor.fetchAccountSettings();
        if (aSettings == null) {
            loggerMaker.errorAndAddToDb("error fetch account settings, exiting process");
            System.exit(0);
        }

        DataControlFetcher.init(dataActor);

        aSettings = dataActor.fetchAccountSettings();

        //DaoInit.init(new ConnectionString(mongoURI));
        // DictionaryFilter.readDictionaryBinary();

        loggerMaker.infoAndAddToDb("Runtime starting at " + Context.now() + "....", LogDb.RUNTIME);

        dataActor.modifyHybridSaasSetting(RuntimeMode.isHybridDeployment());

        initializeRuntime();

        String centralKafkaTopicName = AccountSettings.DEFAULT_CENTRAL_KAFKA_TOPIC_NAME;

        buildKafka();
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                if (kafkaProducer == null || !kafkaProducer.producerReady) {
                    buildKafka();
                }
            }
        }, 5, 5, TimeUnit.MINUTES);

        final boolean checkPg = aSettings != null && aSettings.isRedactPayload();

        AllMetrics.instance.init(LogDb.RUNTIME, checkPg);
        HttpCallParser.init();
        loggerMaker.infoAndAddToDb("All metrics initialized", LogDb.RUNTIME);

        dataActor.modifyHybridSaasSetting(RuntimeMode.isHybridDeployment());

        APIConfig apiConfig = dataActor.fetchApiConfig(configName);
        if (apiConfig == null) {
            apiConfig = new APIConfig(configName,"access-token", 1, 10_000_000, sync_threshold_time); // this sync threshold time is used for deleting sample data
        }

        final Main main = new Main();
        Properties properties = main.configProperties(kafkaBrokerUrl, groupIdConfig, maxPollRecordsConfig);
        main.consumer = new KafkaConsumer<>(properties);

        final Thread mainThread = Thread.currentThread();
        final AtomicBoolean exceptionOnCommitSync = new AtomicBoolean(false);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                main.consumer.wakeup();
                try {
                    if (!exceptionOnCommitSync.get()) {
                        mainThread.join();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (Error e){
                    loggerMaker.errorAndAddToDb("Error in main thread: "+ e.getMessage(), LogDb.RUNTIME);
                }
            }
        });


        scheduler.scheduleAtFixedRate(()-> {
            try {

                Map<MetricName, ? extends Metric> metrics = main.consumer.metrics();

                for (Map.Entry<MetricName, ? extends Metric> entry : metrics.entrySet()) {
                    MetricName key = entry.getKey();
                    Metric value = entry.getValue();

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
                    int records = clientLayer.fetchTotalRecords();
                    AllMetrics.instance.setTotalSampleDataCount(records);
                    loggerMaker.infoAndAddToDb("Total number of records in postgres: " + records, LogDb.RUNTIME);
                    long dbSizeInMb = clientLayer.fetchTotalSize();
                    AllMetrics.instance.setPgDataSizeInMb(dbSizeInMb);
                    loggerMaker.infoAndAddToDb("Postgres size: " + dbSizeInMb + " MB", LogDb.RUNTIME);
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

        long lastSyncOffset = 0;

        Map<Integer, Integer> logSentMap = new HashMap<>();

        try {
            main.consumer.subscribe(Arrays.asList(topicName, "har_"+topicName));
            loggerMaker.infoAndAddToDb("Consumer subscribed", LogDb.RUNTIME);
            while (true) {
                ConsumerRecords<String, String> records = main.consumer.poll(Duration.ofMillis(10000));
                try {
                    main.consumer.commitSync();
                } catch (Exception e) {
                    throw e;
                }
                long start = System.currentTimeMillis();
                // TODO: what happens if exception
                Map<String, List<HttpResponseParams>> responseParamsToAccountMap = new HashMap<>();
                for (ConsumerRecord<String,String> r: records) {
                    HttpResponseParams httpResponseParams;
                    try {
                         
                        printL(r.value());
                        AllMetrics.instance.setRuntimeKafkaRecordCount(1);
                        AllMetrics.instance.setRuntimeKafkaRecordSize(r.value().length());

                        lastSyncOffset++;
                        if (DataControlFetcher.stopIngestionFromKafka()) {
                            continue;
                        }

                        if (lastSyncOffset % 100 == 0) {
                            loggerMaker.info("Committing offset at position: " + lastSyncOffset);
                        }

                        if (tryForCollectionName(r.value())) {
                            continue;
                        }

                        httpResponseParams = HttpCallParser.parseKafkaMessage(r.value());
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(e, "Error while parsing kafka message " + e, LogDb.RUNTIME);
                        continue;
                    }
                    String accountId = httpResponseParams.getAccountId();
                    if (!responseParamsToAccountMap.containsKey(accountId)) {
                        responseParamsToAccountMap.put(accountId, new ArrayList<>());
                    }
                    responseParamsToAccountMap.get(accountId).add(httpResponseParams);
                }

                for (String accountId: responseParamsToAccountMap.keySet()) {
                    int accountIdInt;
                    try {
                        accountIdInt = Integer.parseInt(accountId);
                    } catch (Exception ignored) {
                        loggerMaker.errorAndAddToDb("Account id not string", LogDb.RUNTIME);
                        continue;
                    }

                    Context.accountId.set(accountIdInt);

                    AccountInfo accountInfo = accountInfoMap.get(accountIdInt);
                    if (accountInfo == null) {
                        accountInfo = new AccountInfo();
                        accountInfoMap.put(accountIdInt, accountInfo);
                    }

                    if ((Context.now() - accountInfo.lastEstimatedCountTime) > 60*60) {
                        loggerMaker.infoAndAddToDb("current time: " + Context.now() + " lastEstimatedCountTime: " + accountInfo.lastEstimatedCountTime, LogDb.RUNTIME);
                        accountInfo.lastEstimatedCountTime = Context.now();
                        accountInfo.estimatedCount = dataActor.fetchEstimatedDocCount();
                        accountInfo.setAccountSettings(dataActor.fetchAccountSettings());
                        loggerMaker.infoAndAddToDb("STI Estimated count: " + accountInfo.estimatedCount, LogDb.RUNTIME);
                    }

                    if (!isDashboardInstance && accountInfo.estimatedCount> 20_000_000) {
                        loggerMaker.infoAndAddToDb("STI count is greater than 20M, skipping", LogDb.RUNTIME);
                        continue;
                    }

                    if (!httpCallParserMap.containsKey(accountId)) {
                        HttpCallParser parser = new HttpCallParser(
                                apiConfig.getUserIdentifier(), apiConfig.getThreshold(), apiConfig.getSync_threshold_count(),
                                apiConfig.getSync_threshold_time(), fetchAllSTI
                        );
                        httpCallParserMap.put(accountId, parser);
                        loggerMaker.infoAndAddToDb("New parser created for account: " + accountId, LogDb.RUNTIME);
                    }

                    HttpCallParser parser = httpCallParserMap.get(accountId);

                    try {
                        List<HttpResponseParams> accWiseResponse = responseParamsToAccountMap.get(accountId);

                        accWiseResponse = filterBasedOnHeaders(accWiseResponse, accountInfo.accountSettings);
                        loggerMaker.infoAndAddToDb("Initiating sync function for account: " + accountId, LogDb.RUNTIME);
                        parser.syncFunction(accWiseResponse, syncImmediately, fetchAllSTI, accountInfo.accountSettings);
                        loggerMaker.debugInfoAddToDb("Sync function completed for account: " + accountId, LogDb.RUNTIME);

                        // send to central kafka
                        if (kafkaProducer != null) {
                            loggerMaker.infoAndAddToDb("Sending " + accWiseResponse.size() +" records to context analyzer", LogDb.RUNTIME);
                            for (HttpResponseParams httpResponseParams: accWiseResponse) {
                                try {
                                    loggerMaker.debugInfoAddToDb("Sending to kafka data for account: " + httpResponseParams.getAccountId(), LogDb.RUNTIME);
                                    kafkaProducer.send(httpResponseParams.getOrig(), centralKafkaTopicName);
                                } catch (Exception e) {
                                    // force close it
                                    loggerMaker.errorAndAddToDb("Closing kafka: " + e.getMessage(), LogDb.RUNTIME);
                                    kafkaProducer.close();
                                    loggerMaker.infoAndAddToDb("Successfully closed kafka", LogDb.RUNTIME);
                                }
                            }
                        } else {
                            loggerMaker.errorAndAddToDb("Kafka producer is null", LogDb.RUNTIME);
                        }
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(e.toString(), LogDb.RUNTIME);
                    }
                }
                AllMetrics.instance.setRuntimeProcessLatency(System.currentTimeMillis()-start);
            }

        } catch (WakeupException ignored) {
          // nothing to catch. This exception is called from the shutdown hook.
        } catch (Exception e) {
            exceptionOnCommitSync.set(true);
            printL(e);
            loggerMaker.errorAndAddToDb("Error in main runtime: " + e.getMessage(),LogDb.RUNTIME);
            e.printStackTrace();
            System.exit(0);
        } finally {
            main.consumer.close();
        }
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
                }

                accWiseResponse = accWiseResponseFiltered;
            }

            Map<String, Map<Pattern, String>> apiCollectioNameMapper = accountSettings.convertApiCollectionNameMapperToRegex();
            changeTargetCollection(apiCollectioNameMapper, accWiseResponse);
        }

        return accWiseResponse;
    }

    public static void changeTargetCollection(Map<String, Map<Pattern, String>> apiCollectioNameMapper, List<HttpResponseParams> accWiseResponse) {
        if (apiCollectioNameMapper != null && !apiCollectioNameMapper.isEmpty()) {
            for(HttpResponseParams accWiseResponseEntry : accWiseResponse) {
                Map<String, List<String>> reqHeaders = accWiseResponseEntry.getRequestParams().getHeaders();
                for(String headerName : apiCollectioNameMapper.keySet()) {
                    List<String> reqHeaderValues = reqHeaders == null ? null : reqHeaders.get(headerName);
                    if (reqHeaderValues != null && !reqHeaderValues.isEmpty()) {
                        Map<Pattern, String> apiCollectioNameForGivenHeader = apiCollectioNameMapper.get(headerName);
                        for (Map.Entry<Pattern,String> apiCollectioNameOrigXNew: apiCollectioNameForGivenHeader.entrySet()) {
                            for (int i = 0; i < reqHeaderValues.size(); i++) {
                                String reqHeaderValue = reqHeaderValues.get(i);
                                Pattern regex = apiCollectioNameOrigXNew.getKey();
                                String newValue = apiCollectioNameOrigXNew.getValue();

                                try {
                                    if (regex.matcher(reqHeaderValue).matches()) {
                                        reqHeaders.put("host", Collections.singletonList(newValue));
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
        Context.accountId.set(account.getId());

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
            loggerMaker.errorAndAddToDb("error while updating dashboard version: " + e.getMessage(), LogDb.RUNTIME);
        }

        initFromRuntime(account.getId());
        
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

    public static void initializeRuntimeHelper() {
        SingleTypeInfo.init();
        try {
            RuntimeVersion runtimeVersion = new RuntimeVersion();
            runtimeVersion.updateVersion(AccountSettings.API_RUNTIME_VERSION, dataActor);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error while updating dashboard version: " + e.getMessage(), LogDb.RUNTIME);
        }
        createIndices();
    }

    public static void createIndices() {
        SingleTypeInfoDao.instance.createIndicesIfAbsent();
        SensitiveSampleDataDao.instance.createIndicesIfAbsent();
        SampleDataDao.instance.createIndicesIfAbsent();
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

        return properties;
    }
}

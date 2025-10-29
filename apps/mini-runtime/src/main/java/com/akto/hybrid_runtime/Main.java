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
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.kafka.Kafka;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.metrics.AllMetrics;
import com.akto.hybrid_parsers.HttpCallParser;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.metrics.ModuleInfoWorker;
import com.akto.runtime.parser.SampleParser;
import com.akto.runtime.utils.Utils;
import com.akto.testing_db_layer_client.ClientLayer;
import com.akto.util.DashboardMode;
import com.google.gson.Gson;

import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.BufferedReader;

// Import protobuf classes
import com.akto.proto.generated.threat_detection.message.http_response_param.v1.HttpResponseParam;
import com.akto.proto.generated.threat_detection.message.http_response_param.v1.StringList;

public class Main {

    public static class DataUploadServlet extends HttpServlet {
        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {

            response.setContentType("application/json");
            response.setHeader("Access-Control-Allow-Origin", "*");
            response.setHeader("Access-Control-Allow-Methods", "POST");
            response.setHeader("Access-Control-Allow-Headers", "Content-Type");

            try {
                StringBuilder jsonBuffer = new StringBuilder();
                BufferedReader reader = request.getReader();
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonBuffer.append(line);
                }

                String jsonData = jsonBuffer.toString();
                if (jsonData == null || jsonData.trim().isEmpty()) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    response.getWriter().write("{\"error\":\"Empty request body\"}");
                    return;
                }

                processUploadedData(jsonData);

                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().write("{\"status\":\"success\",\"message\":\"Data processed successfully\"}");

            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error processing uploaded data: " + e.getMessage());
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("{\"error\":\"Internal server error\"}");
            }
        }

        @Override
        protected void doOptions(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            response.setHeader("Access-Control-Allow-Origin", "*");
            response.setHeader("Access-Control-Allow-Methods", "POST");
            response.setHeader("Access-Control-Allow-Headers", "Content-Type");
            response.setStatus(HttpServletResponse.SC_OK);
        }
    }

    public static final String GROUP_NAME = "group_name";
    public static final String VXLAN_ID = "vxlanId";
    public static final String VPC_CIDR = "vpc_cidr";
    public static final String ACCOUNT_ID = "account_id";
    private static final LoggerMaker loggerMaker = new LoggerMaker(Main.class, LogDb.RUNTIME);

    public static final DataActor dataActor = DataActorFactory.fetchInstance();
    private static final ClientLayer clientLayer = new ClientLayer();

    // this sync threshold time is used for deleting sample data
    public static final int sync_threshold_time = 120;
    public static final boolean isKafkaAuthenticationEnabled = System.getenv("KAFKA_AUTH_ENABLED") != null && System.getenv("KAFKA_AUTH_ENABLED").equalsIgnoreCase("true");
    public static final String kafkaUsername = System.getenv("KAFKA_USERNAME");
    public static final String kafkaPassword = System.getenv("KAFKA_PASSWORD");
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

    private static void processUploadedData(String jsonData) {
        try {
            if (tryForCollectionName(jsonData)) {
                loggerMaker.info("Processed collection name data");
                return;
            }

            Gson gson = new Gson();
            Map<String, Object> map = gson.fromJson(jsonData, Map.class);
            List<Map<String, Object>> batchData = (List<Map<String, Object>>) map.get("batchData");

            for (Map<String, Object> batchDataItem : batchData) {
                String json = gson.toJson(batchDataItem, Map.class);

                HttpResponseParams httpResponseParams = HttpCallParser.parseKafkaMessage(json);
                if (httpResponseParams == null) {
                    loggerMaker.error("Failed to parse uploaded data - invalid JSON format");
                    return;
                }

                List<HttpResponseParams> dataList = Collections.singletonList(httpResponseParams);
                processData(dataList);
                loggerMaker.info("Successfully processed uploaded data");
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in processUploadedData: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private static void startHttpServer() {
        try {
            int port = Integer.parseInt(System.getenv().getOrDefault("HTTP_PORT", "8080"));
            Server server = new Server(port);

            ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
            context.setContextPath("/");
            server.setHandler(context);

            context.addServlet(new ServletHolder(new DataUploadServlet()), "/upload");

            server.start();
            loggerMaker.infoAndAddToDb("HTTP server started on port " + port);
            loggerMaker.infoAndAddToDb("Data upload endpoint available at: /upload");

            server.join();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Failed to start HTTP server: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public static void insertRuntimeFilters() {
        RuntimeFilterDao.instance.initialiseFilters();
    }

    public static Kafka kafkaProducer = null;
    public static KafkaProducer<String, byte[]> protobufKafkaProducer = null;
    
    private static void buildKafka() {
        loggerMaker.info("Building kafka...................");
        AccountSettings accountSettings = dataActor.fetchAccountSettings();
        if (accountSettings != null && accountSettings.getCentralKafkaIp()!= null) {
            String centralKafkaBrokerUrl = accountSettings.getCentralKafkaIp();
            int centralKafkaBatchSize = AccountSettings.DEFAULT_CENTRAL_KAFKA_BATCH_SIZE;
            int centralKafkaLingerMS = AccountSettings.DEFAULT_CENTRAL_KAFKA_LINGER_MS;
            if (centralKafkaBrokerUrl != null) {
                // Get Kafka authentication credentials from environment variables
                if(isKafkaAuthenticationEnabled){
                    if(StringUtils.isEmpty(kafkaPassword) || StringUtils.isEmpty(kafkaUsername)){
                        loggerMaker.errorAndAddToDb("Kafka authentication credentials not provided");
                        return;
                    }
                    kafkaProducer = new Kafka(centralKafkaBrokerUrl, centralKafkaLingerMS, centralKafkaBatchSize, kafkaUsername, kafkaPassword, true);
                }else{
                    kafkaProducer = new Kafka(centralKafkaBrokerUrl, centralKafkaLingerMS, centralKafkaBatchSize);
                }
                loggerMaker.info("Connected to central kafka @ " + Context.now());
            }
        } else {
            loggerMaker.info(String.valueOf(accountSettings));
        }
    }
    
    private static boolean isProtoKafkaEnabled() {
        if (DataActor.actualAccountId == 1752208054 || DataActor.actualAccountId == 1753806619 || DataActor.actualAccountId == 1757403870 || DataActor.actualAccountId == 1759692400 || isSendToThreatEnabled) {
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
            Properties kafkaProps = new Properties();
            kafkaProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBrokerUrl);
            kafkaProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
            kafkaProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            kafkaProps.put(ProducerConfig.BATCH_SIZE_CONFIG, batchSize);
            kafkaProps.put(ProducerConfig.LINGER_MS_CONFIG, lingerMS);
            kafkaProps.put(ProducerConfig.RETRIES_CONFIG, 3);
            kafkaProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
            kafkaProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, lingerMS + 5000);
            kafkaProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 100);
            
            if (isKafkaAuthenticationEnabled) {
                if(StringUtils.isEmpty(kafkaPassword) || StringUtils.isEmpty(kafkaUsername)){
                    loggerMaker.errorAndAddToDb("Kafka authentication credentials not provided");
                    return;
                }
                kafkaProps.put("security.protocol", "SASL_PLAINTEXT");
                kafkaProps.put("sasl.mechanism", "PLAIN");
                
                // Create JAAS configuration for SASL PLAIN
                String jaasConfig = String.format(
                    "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";",
                    kafkaUsername, kafkaPassword
                );
                kafkaProps.put("sasl.jaas.config", jaasConfig);
            }
            protobufKafkaProducer = new KafkaProducer<>(kafkaProps);
            loggerMaker.info("Connected to protobuf kafka producer @ " + Context.now());
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error building protobuf kafka producer: " + e.getMessage());
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

    public static String getLogTopicName() {
        String topicName = System.getenv("AKTO_KAFKA_LOG_TOPIC_NAME");
        if (topicName == null) {
            topicName = "akto.api.producer.logs";
        }
        return topicName;
    }

    private static final String LOG_GROUP_ID = "-log";

    public static final String customMiniRuntimeServiceName;
    static {
        customMiniRuntimeServiceName = System.getenv("MINI_RUNTIME_NAME") == null? "Default_" + UUID.randomUUID().toString().substring(0, 4) : System.getenv("MINI_RUNTIME_NAME");
    }

    static private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    // REFERENCE: https://www.oreilly.com/library/view/kafka-the-definitive/9781491936153/ch04.html (But how do we Exit?)
    public static void main(String[] args) {
        String configName = System.getenv("AKTO_CONFIG_NAME");
        String topicName = getTopicName();
        String kafkaBrokerUrl = System.getenv().getOrDefault("AKTO_KAFKA_BROKER_URL","kafka1:19092");
        String isKubernetes = System.getenv("IS_KUBERNETES");
        if (isKubernetes != null && isKubernetes.equalsIgnoreCase("true")) {
            loggerMaker.infoAndAddToDb("is_kubernetes: true");
            kafkaBrokerUrl = "127.0.0.1:29092";
        }
        final String brokerUrlFinal = kafkaBrokerUrl;
        String groupIdConfig =  System.getenv("AKTO_KAFKA_GROUP_ID_CONFIG") != null
                ? System.getenv("AKTO_KAFKA_GROUP_ID_CONFIG")
                : "asdf";
        boolean syncImmediately = false;
        boolean fetchAllSTI = true;
        Map<Integer, AccountInfo> accountInfoMap =  new HashMap<>();

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
        }
        DataActor.actualAccountId = aSettings.getId();
        loggerMaker.infoAndAddToDb("Fetched account settings for account ");

        if (DataActor.actualAccountId == 1759692400) {
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

        buildKafka();
        buildProtobufKafkaProducer(brokerUrlFinal);
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                if (kafkaProducer == null || !kafkaProducer.producerReady) {
                    buildKafka();
                }
                // Also check protobuf producer
                if (protobufKafkaProducer == null) {
                    buildProtobufKafkaProducer(brokerUrlFinal);
                }
            }
        }, 5, 5, TimeUnit.MINUTES);

        final boolean checkPg = aSettings != null && aSettings.isRedactPayload();

        AllMetrics.instance.init(LogDb.RUNTIME, checkPg, dataActor, DataActor.actualAccountId);
        HttpCallParser.init();
        loggerMaker.infoAndAddToDb("All metrics initialized");

        dataActor.modifyHybridSaasSetting(RuntimeMode.isHybridDeployment());

        APIConfig apiConfig = dataActor.fetchApiConfig(configName);
        if (apiConfig == null) {
            apiConfig = new APIConfig(configName,"access-token", 1, 10_000_000, sync_threshold_time); // this sync threshold time is used for deleting sample data
        }

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                // Close protobuf producer
                if (protobufKafkaProducer != null) {
                    protobufKafkaProducer.close();
                    loggerMaker.info("Closed protobuf kafka producer");
                }
            }
        });


        scheduler.scheduleAtFixedRate(()-> {
            try {
                if (checkPg) {
                    long dbSizeInMb = clientLayer.fetchTotalSize();
                    AllMetrics.instance.setPgDataSizeInMb(dbSizeInMb);
                    loggerMaker.infoAndAddToDb("Postgres size: " + dbSizeInMb + " MB");
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Failed to get total number of records from postgres");
            }

        }, 0, 1, TimeUnit.MINUTES);


        // schedule MCP sync job for 24 hours
        loggerMaker.info("Scheduling MCP Sync Job");
        APIConfig finalApiConfig = apiConfig;
        scheduler.scheduleAtFixedRate(() -> {
            Context.accountId.set(DataActor.actualAccountId);
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
            Context.accountId.set(DataActor.actualAccountId);
            try {
                loggerMaker.infoAndAddToDb("Executing MCP Recon Sync job");
                McpReconSyncJobExecutor.INSTANCE.runJob(finalApiConfigRecon);
                loggerMaker.infoAndAddToDb("Finished executing MCP Recon Sync job");
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error while executing MCP Recon Sync Job");
            }
        }, 0, 24, TimeUnit.HOURS);

        if(isDbMergingModeEnabled()){
            runDBMaintenanceJob(apiConfig);
        }else{
            loggerMaker.infoAndAddToDb("Starting HTTP server instead of Kafka consumer...");
            startHttpServer();
        }
    }

    public static boolean isDbMergingModeEnabled(){
        return System.getenv().getOrDefault("DB_MERGING_MODE", "false").equalsIgnoreCase("true");
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
            loggerMaker.errorAndAddToDb(e, "error while updating dashboard version: " + e.getMessage());
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
            loggerMaker.errorAndAddToDb(e, "error while updating dashboard version: " + e.getMessage());
        }
        createIndices();
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

        if(DataActor.actualAccountId == 1759692400){
            properties.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, 50 * 1024 * 1024); // 50MB per partition
            properties.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, 100 * 1024 * 1024); // 100MB total
        }

        if (isKafkaAuthenticationEnabled) {
            if(StringUtils.isEmpty(kafkaPassword) || StringUtils.isEmpty(kafkaUsername)){
                loggerMaker.errorAndAddToDb("Kafka authentication credentials not provided");
                return null;
            }
            properties.put("security.protocol", "SASL_PLAINTEXT");
            properties.put("sasl.mechanism", "PLAIN");
            
            // Create JAAS configuration for SASL PLAIN
            String jaasConfig = String.format(
                "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";",
                kafkaUsername, kafkaPassword
            );
            properties.put("sasl.jaas.config", jaasConfig);
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

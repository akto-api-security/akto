package com.akto.runtime;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.akto.DaoInit;
import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.TestConfigYamlParser;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dto.APIConfig;
import com.akto.dto.AccountSettings;
import com.akto.dto.ApiCollection;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.kafka.Kafka;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.parsers.HttpCallParser;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.runtime.policies.AktoPolicies;
import com.akto.util.AccountTask;
import com.google.gson.Gson;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;

import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
    private Consumer<String, String> consumer;
    public static final String GROUP_NAME = "group_name";
    public static final String VXLAN_ID = "vxlanId";
    public static final String VPC_CIDR = "vpc_cidr";
    public static final String ACCOUNT_ID = "account_id";
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final LoggerMaker loggerMaker = new LoggerMaker(Main.class);

    // this sync threshold time is used for deleting sample data
    public static final int sync_threshold_time = 120;

    private static int debugPrintCounter = 500;
    private static void printL(Object o) {
        if (debugPrintCounter > 0) {
            debugPrintCounter--;
            System.out.println(o);
        }
    }   

    public static boolean tryForCollectionName(String message) {
        boolean ret = false;
        try {
            Gson gson = new Gson();

            Map<String, Object> json = gson.fromJson(message, Map.class);

            // logger.info("Json size: " + json.size());
            boolean withoutCidrCond = json.containsKey(GROUP_NAME) && json.containsKey(VXLAN_ID);
            boolean withCidrCond = json.containsKey(GROUP_NAME) && json.containsKey(VXLAN_ID) && json.containsKey(VPC_CIDR);
            if (withCidrCond || withoutCidrCond) {
                ret = true;
                String groupName = (String) (json.get(GROUP_NAME));
                String vxlanIdStr = ((Double) json.get(VXLAN_ID)).intValue() + "";
                int vxlanId = Integer.parseInt(vxlanIdStr);
                ApiCollectionsDao.instance.getMCollection().updateMany(
                        Filters.eq(ApiCollection.VXLAN_ID, vxlanId),
                        Updates.set(ApiCollection.NAME, groupName)
                );

                if (json.containsKey(VPC_CIDR)) {
                    List<String> cidrList = (List<String>) json.get(VPC_CIDR);
                    logger.info("cidrList: " + cidrList);
                    // For old deployments, we won't receive ACCOUNT_ID. If absent, we assume 1_000_000.
                    String accountIdStr = (String) (json.get(ACCOUNT_ID));
                    int accountId = StringUtils.isNumeric(accountIdStr) ? Integer.parseInt(accountIdStr) : 1_000_000;
                    Context.accountId.set(accountId);
                    AccountSettingsDao.instance.getMCollection().updateOne(
                        AccountSettingsDao.generateFilter(), Updates.addEachToSet("privateCidrList", cidrList), new UpdateOptions().upsert(true)
                    );
                }
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in try collection" + e, LogDb.RUNTIME);
        }

        return ret;
    }


    public static void createIndices() {
        SingleTypeInfoDao.instance.createIndicesIfAbsent();
        SensitiveSampleDataDao.instance.createIndicesIfAbsent();
        SampleDataDao.instance.createIndicesIfAbsent();
    }

    public static void insertRuntimeFilters() {
        RuntimeFilterDao.instance.initialiseFilters();
    }

    public static Kafka kafkaProducer = null;
    private static void buildKafka() {
        logger.info("Building kafka...................");
        AccountTask.instance.executeTask(t -> {
            int accountId = Context.accountId.get();
            AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter(accountId));
            if (accountSettings != null && accountSettings.getCentralKafkaIp()!= null) {
                String centralKafkaBrokerUrl = accountSettings.getCentralKafkaIp();
                int centralKafkaBatchSize = AccountSettings.DEFAULT_CENTRAL_KAFKA_BATCH_SIZE;
                int centralKafkaLingerMS = AccountSettings.DEFAULT_CENTRAL_KAFKA_LINGER_MS;
                if (centralKafkaBrokerUrl != null) {
                    kafkaProducer = new Kafka(centralKafkaBrokerUrl, centralKafkaLingerMS, centralKafkaBatchSize);
                    logger.info("Connected to central kafka @ " + Context.now());
                }
        } else {
            System.out.println(accountSettings);
            }
        }, "build-kafka-task");
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
                logger.info("Received " + accountSettings.convertApiCollectionNameMapperToRegex().size() + " apiCollectionNameMappers");
            }
        }
    }

    // REFERENCE: https://www.oreilly.com/library/view/kafka-the-definitive/9781491936153/ch04.html (But how do we Exit?)
    public static void main(String[] args) {
        String mongoURI = System.getenv("AKTO_MONGO_CONN");;
        String configName = System.getenv("AKTO_CONFIG_NAME");
        String topicName = System.getenv("AKTO_KAFKA_TOPIC_NAME");
        String kafkaBrokerUrl = "kafka1:19092"; //System.getenv("AKTO_KAFKA_BROKER_URL");
        String groupIdConfig =  System.getenv("AKTO_KAFKA_GROUP_ID_CONFIG");
        String instanceType =  System.getenv("AKTO_INSTANCE_TYPE");
        boolean syncImmediately = false;
        boolean fetchAllSTI = true;
        Map<Integer, AccountInfo> accountInfoMap =  new HashMap<>();

        boolean isDashboardInstance = instanceType != null && instanceType.equals("DASHBOARD");
        if (isDashboardInstance) {
            syncImmediately = true;
            fetchAllSTI = false;
        }
        int maxPollRecordsConfig = Integer.parseInt(System.getenv("AKTO_KAFKA_MAX_POLL_RECORDS_CONFIG"));

        if (topicName == null) topicName = "akto.api.logs";

        DaoInit.init(new ConnectionString(mongoURI));
        loggerMaker.infoAndAddToDb("Runtime starting at " + Context.now() + "....", LogDb.RUNTIME);
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


        APIConfig apiConfig;
        apiConfig = APIConfigsDao.instance.findOne(Filters.eq("name", configName));
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

        Map<String, HttpCallParser> httpCallParserMap = new HashMap<>();
        Map<String, Flow> flowMap = new HashMap<>();
        Map<String, AktoPolicies> aktoPolicyMap = new HashMap<>();

        // sync infra metrics thread
        // ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        // KafkaHealthMetricSyncTask task = new KafkaHealthMetricSyncTask(main.consumer);
        // executor.scheduleAtFixedRate(task, 2, 60, TimeUnit.SECONDS);

        long lastSyncOffset = 0;

        try {
            main.consumer.subscribe(Arrays.asList(topicName, "har_"+topicName));
            loggerMaker.infoAndAddToDb("Consumer subscribed", LogDb.RUNTIME);
            while (true) {
                ConsumerRecords<String, String> records = main.consumer.poll(Duration.ofMillis(10000));
                main.consumer.commitSync();
                if (1 == 1) {
                    exceptionOnCommitSync.set(true);
                    throw new Exception("some exception here");
                }

                // TODO: what happens if exception
                Map<String, List<HttpResponseParams>> responseParamsToAccountMap = new HashMap<>();
                for (ConsumerRecord<String,String> r: records) {
                    HttpResponseParams httpResponseParams;
                    try {
                         
                        printL(r.value());
                        lastSyncOffset++;

                        if (lastSyncOffset % 100 == 0) {
                            System.out.println("Committing offset at position: " + lastSyncOffset);
                        }

                        if (tryForCollectionName(r.value())) {
                            continue;
                        }

                        httpResponseParams = HttpCallParser.parseKafkaMessage(r.value());
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb("Error while parsing kafka message " + e, LogDb.RUNTIME);
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
                        accountInfo.estimatedCount = SingleTypeInfoDao.instance.getMCollection().estimatedDocumentCount();
                        accountInfo.setAccountSettings(AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter()));
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

                    if (!flowMap.containsKey(accountId)) {
                        Flow flow= new Flow(
                                apiConfig.getThreshold(), apiConfig.getSync_threshold_count(), apiConfig.getSync_threshold_time(),
                                apiConfig.getThreshold(), apiConfig.getSync_threshold_count(), apiConfig.getSync_threshold_time(),
                                apiConfig.getUserIdentifier()
                        );

                        flowMap.put(accountId, flow);
                    }

                    if (!aktoPolicyMap.containsKey(accountId)) {
                        AktoPolicies aktoPolicy = new AktoPolicies(fetchAllSTI);
                        aktoPolicyMap.put(accountId, aktoPolicy);
                    }

                    HttpCallParser parser = httpCallParserMap.get(accountId);
                    // Flow flow = flowMap.get(accountId);
                    AktoPolicies aktoPolicy = aktoPolicyMap.get(accountId);

                    try {
                        List<HttpResponseParams> accWiseResponse = responseParamsToAccountMap.get(accountId);

                        accWiseResponse = filterBasedOnHeaders(accWiseResponse, accountInfo.accountSettings);
                        loggerMaker.infoAndAddToDb("Initiating sync function for account: " + accountId, LogDb.RUNTIME);
                        APICatalogSync apiCatalogSync = parser.syncFunction(accWiseResponse, syncImmediately, fetchAllSTI);
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

                        // flow.init(accWiseResponse);
                        loggerMaker.infoAndAddToDb("Initiating akto policy for account: " + accountId, LogDb.RUNTIME);
                        aktoPolicy.main(accWiseResponse, apiCatalogSync, fetchAllSTI);
                        loggerMaker.infoAndAddToDb("Akto policy completed for account: " + accountId, LogDb.RUNTIME);
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(e.toString(), LogDb.RUNTIME);
                    }
                }
            }

        } catch (WakeupException ignored) {
          // nothing to catch. This exception is called from the shutdown hook.
        } catch (Exception e) {
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

        return accWiseResponse;
    }

    public static void initializeRuntime(){
        AccountTask.instance.executeTask(t -> {
            initializeRuntimeHelper();
        }, "initialize-runtime-task");
        SingleTypeInfo.init();
    }

    public static void initializeRuntimeHelper() {
        SingleTypeInfoDao.instance.getMCollection().updateMany(Filters.exists("apiCollectionId", false), Updates.set("apiCollectionId", 0));
        createIndices();
        insertRuntimeFilters();
        try {
            AccountSettingsDao.instance.updateVersion(AccountSettings.API_RUNTIME_VERSION);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error while updating dashboard version: " + e.getMessage(), LogDb.RUNTIME);
        }

        ApiCollection apiCollection = ApiCollectionsDao.instance.findOne("_id", 0);
        if (apiCollection == null) {
            Set<String> urls = new HashSet<>();
            for(SingleTypeInfo singleTypeInfo: SingleTypeInfoDao.instance.fetchAll()) {
                urls.add(singleTypeInfo.getUrl());
            }
            ApiCollectionsDao.instance.insertOne(new ApiCollection(0, "Default", Context.now(), urls, null, 0));
        }
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

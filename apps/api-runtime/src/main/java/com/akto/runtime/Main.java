package com.akto.runtime;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.akto.DaoInit;
import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dto.*;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.kafka.Kafka;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.parsers.HttpCallParser;
import com.akto.util.filter.DictionaryFilter;
import com.akto.runtime.utils.Utils;
import com.akto.util.AccountTask;
import com.akto.util.DashboardMode;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;

import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.errors.WakeupException;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
    private Consumer<String, String> consumer;
    public static final String GROUP_NAME = "group_name";
    public static final String VXLAN_ID = "vxlanId";
    public static final String VPC_CIDR = "vpc_cidr";
    public static final String ACCOUNT_ID = "account_id";
    private static final LoggerMaker loggerMaker = new LoggerMaker(Main.class);

    // this sync threshold time is used for deleting sample data
    public static final int sync_threshold_time = 120;

    public static boolean isOnprem = false;

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
                    loggerMaker.info("cidrList: " + cidrList);
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



    public static void insertRuntimeFilters() {
        RuntimeFilterDao.instance.initialiseFilters();
    }

    public static Kafka kafkaProducer = null;

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

    // REFERENCE: https://www.oreilly.com/library/view/kafka-the-definitive/9781491936153/ch04.html (But how do we Exit?)
    public static void main(String[] args) {
        String mongoURI = System.getenv("AKTO_MONGO_CONN");;
        String configName = System.getenv("AKTO_CONFIG_NAME");
        String topicName = System.getenv("AKTO_KAFKA_TOPIC_NAME");
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

        boolean isDashboardInstance = instanceType != null && instanceType.equals("DASHBOARD");
        if (isDashboardInstance) {
            syncImmediately = true;
            fetchAllSTI = false;
        }
        int maxPollRecordsConfig = Integer.parseInt(System.getenv("AKTO_KAFKA_MAX_POLL_RECORDS_CONFIG"));

        DictionaryFilter.readDictionaryBinary();

        if (topicName == null) topicName = "akto.api.logs";

        DaoInit.init(new ConnectionString(mongoURI));
        loggerMaker.infoAndAddToDb("Runtime starting at " + Context.now() + "....", LogDb.RUNTIME);
        try {
            initializeRuntime();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in initializeRuntime " + e.getMessage(), LogDb.RUNTIME);
        }

        APIConfig apiConfig = null;
        try {
            apiConfig = APIConfigsDao.instance.findOne(Filters.eq("name", configName));
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error while fetching api config " + e.getMessage(), LogDb.RUNTIME);
        }
        if (apiConfig == null) {
            apiConfig = new APIConfig(configName,"access-token", 1, 10_000_000, sync_threshold_time); // this sync threshold time is used for deleting sample data
        }

        final Main main = new Main();
        Properties properties = Utils.configProperties(kafkaBrokerUrl, groupIdConfig, maxPollRecordsConfig);
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

        APIConfig finalApiConfig = apiConfig;
        scheduler.scheduleAtFixedRate(() -> {
            try {
                loggerMaker.info("Starting MCP sync job..");
                AccountTask.instance.executeTask(t -> {
                    loggerMaker.info("Starting MCP sync job");
                    new McpToolsSyncJobExecutor().runJob(finalApiConfig);
                }, "mcp-tools-sync");
                loggerMaker.info("Finished MCP sync job..");
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error in MCP tools sync job: " + e.getMessage(), LogDb.RUNTIME);
            }
        }, 0, 24, TimeUnit.HOURS);

        Map<String, HttpCallParser> httpCallParserMap = new HashMap<>();

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
                try {
                    main.consumer.commitSync();
                } catch (Exception e) {
                    throw e;
                }
                
                // TODO: what happens if exception
                Map<String, List<HttpResponseParams>> responseParamsToAccountMap = new HashMap<>();
                for (ConsumerRecord<String,String> r: records) {
                    HttpResponseParams httpResponseParams;
                    try {
                         
                        Utils.printL(r.value());
                        lastSyncOffset++;

                        if (lastSyncOffset % 100 == 0) {
                            loggerMaker.info("Committing offset at position: " + lastSyncOffset);
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

                handleResponseParams(responseParamsToAccountMap,
                    accountInfoMap,
                    isDashboardInstance,
                    httpCallParserMap,
                    apiConfig,
                    fetchAllSTI,
                    syncImmediately);
            }

        } catch (WakeupException ignored) {
          // nothing to catch. This exception is called from the shutdown hook.
        } catch (Exception e) {
            exceptionOnCommitSync.set(true);
            Utils.printL(e);
            loggerMaker.errorAndAddToDb("Error in main runtime: " + e.getMessage(),LogDb.RUNTIME);
            e.printStackTrace();
            System.exit(0);
        } finally {
            main.consumer.close();
        }
    }

    public static void handleResponseParams(Map<String, List<HttpResponseParams>> responseParamsToAccountMap,
        Map<Integer, AccountInfo> accountInfoMap, boolean isDashboardInstance,
        Map<String, HttpCallParser> httpCallParserMap, APIConfig apiConfig, boolean fetchAllSTI,
        boolean syncImmediately) {
        for (String accountId: responseParamsToAccountMap.keySet()) {
            int accountIdInt;
            try {
                accountIdInt = Integer.parseInt(accountId);
            } catch (Exception ignored) {
                loggerMaker.errorAndAddToDb("Account id not string", LogDb.RUNTIME);
                continue;
            }

            Context.accountId.set(accountIdInt);

            AccountInfo accountInfo = accountInfoMap.computeIfAbsent(accountIdInt, k -> new AccountInfo());

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

            HttpCallParser parser = httpCallParserMap.get(accountId);

            try {
                List<HttpResponseParams> accWiseResponse = responseParamsToAccountMap.get(accountId);

                accWiseResponse = filterBasedOnHeaders(accWiseResponse, accountInfo.accountSettings);
                loggerMaker.infoAndAddToDb("Initiating sync function for account: " + accountId, LogDb.RUNTIME);
                parser.syncFunction(accWiseResponse, syncImmediately, fetchAllSTI, accountInfo.accountSettings);
                loggerMaker.debugInfoAddToDb("Sync function completed for account: " + accountId, LogDb.RUNTIME);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e.toString(), LogDb.RUNTIME);
            }
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
        AccountTask.instance.executeTask(t -> {
            initializeRuntimeHelper();
        }, "initialize-runtime-task");
        SingleTypeInfo.init();
        HttpCallParser.init();
        Setup setup = SetupDao.instance.findOne(new BasicDBObject());

        String dashboardMode = "saas";
        if (setup != null) {
            dashboardMode = setup.getDashboardMode();
        }

        isOnprem = dashboardMode.equalsIgnoreCase(DashboardMode.ON_PREM.name());
    }

    public static void initializeRuntimeHelper() {
        SingleTypeInfoDao.instance.getMCollection().updateMany(Filters.exists("apiCollectionId", false), Updates.set("apiCollectionId", 0));
        // DaoInit.createIndices();
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
            ApiCollectionsDao.instance.insertOne(new ApiCollection(0, "Default", Context.now(), urls, null, 0, false, true));
        }
    }
}

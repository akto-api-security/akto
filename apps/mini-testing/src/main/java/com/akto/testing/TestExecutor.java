package com.akto.testing;

import com.akto.PayloadEncodeUtil;
import com.akto.crons.GetRunningTestsStatus;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.CustomAuthType;
import com.akto.dto.DependencyNode;
import com.akto.dto.DependencyNode.ParamInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.api_workflow.Graph;
import com.akto.dto.billing.SyncLimit;
import com.akto.dto.dependency_flow.KVPair;
import com.akto.dto.dependency_flow.ReplaceDetail;
import com.akto.dto.test_editor.*;
import com.akto.dto.testing.*;
import com.akto.dto.testing.TestResult.Confidence;
import com.akto.dto.testing.TestResult.TestError;
import com.akto.dto.testing.TestingRun.State;
import com.akto.dto.type.RequestTemplate;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLMethods.Method;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.metrics.AllMetrics;
import com.akto.store.SampleMessageStore;
import com.akto.store.TestingUtil;
import com.akto.test_editor.execution.Build;
import com.akto.test_editor.execution.Executor;
import com.akto.test_editor.execution.VariableResolver;
import com.akto.testing.yaml_tests.YamlTestTemplate;
import com.akto.testing_db_layer_client.ClientLayer;
import com.akto.testing_issues.TestingIssuesHandler;
import com.akto.util.JSONUtils;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.Severity;
import com.akto.util.enums.LoginFlowEnums;
import com.alibaba.fastjson2.JSON;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.Projections;

import static com.akto.test_editor.execution.Build.modifyRequest;
import com.akto.testing.kafka_utils.TestingConfigurations;
import com.akto.testing.kafka_utils.Producer;
import com.akto.dto.testing.info.SingleTestPayload;
import static com.akto.testing.Utils.writeJsonContentInFile;

import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.json.JSONObject;
import static com.akto.testing.Utils.generateFailedRunResultForMessage;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.interfaces.RSAPrivateKey;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.akto.testing.workflow_node_executor.Utils;

public class TestExecutor {

    private static final LoggerMaker loggerMaker = new LoggerMaker(TestExecutor.class, LogDb.TESTING);

    public static long acceptableSizeInBytes = 5_000_000;
    private static final Gson gson = new Gson();

    private static final DataActor dataActor = DataActorFactory.fetchInstance();

    public static final String REQUEST_HOUR = "requestHour";
    public static final String COUNT = "count";
    public static final int ALLOWED_REQUEST_PER_HOUR = 100;
    private static final ClientLayer clientLayer = new ClientLayer();
    private static final AtomicInteger totalTestsCount = new AtomicInteger(0);
    private static final boolean shouldCallClientLayerForSampleData = System.getenv("TESTING_DB_LAYER_SERVICE_URL") != null && !System.getenv("TESTING_DB_LAYER_SERVICE_URL").isEmpty();
    private static RSAPrivateKey privateKey = PayloadEncodeUtil.getPrivateKey();
    
    // Current execution fallback flag - used when Kafka fails during current test run
    private volatile boolean currentExecutionFallback = false;
    
    /**
     * Resets the current execution fallback flag for a new test cycle.
     * This should be called at the beginning of each test execution in Main.java
     */
    public void resetCurrentExecutionFallback() {
        currentExecutionFallback = false;
    }


    /**
     * Executes all tests using legacy (non-Kafka) approach when Kafka fails.
     * This method handles the complete test execution flow including thread management,
     * timeout handling, and test completion for fallback scenarios.
     */
    private void executeAllTestsInLegacyMode(TestingRun testingRun, ObjectId summaryId, SyncLimit syncLimit, 
            List<ApiInfo.ApiInfoKey> apiInfoKeyList, List<String> testingRunSubCategories, 
            TestingUtil testingUtil, Map<ApiInfo.ApiInfoKey, List<String>> finalApiInfoKeySubcategoryMap,
            Map<ApiInfoKey, String> apiInfoKeyToHostMap, ConcurrentHashMap<String, String> subCategoryEndpointMap, 
            Map<String, TestConfig> testConfigMap, List<TestingRunResult.TestLog> testLogs, int accountId) {
        
        loggerMaker.insertImportantTestingLog("FALLBACK METHOD CALLED: executeAllTestsInLegacyMode started with " + apiInfoKeyList.size() + " API endpoints and " + testingRunSubCategories.size() + " subcategories");
        
        int maxThreads = Math.min(100, Math.max(10, testingRun.getMaxConcurrentRequests()));
        List<Future<Void>> testingRecords = new ArrayList<>();
        ExecutorService threadPool = Executors.newFixedThreadPool(maxThreads);
        CountDownLatch latch = new CountDownLatch(apiInfoKeyList.size());
        int tempRunTime = testingRun.getTestRunTime() <= 0 ? 30*60 : testingRun.getTestRunTime();
        
        // Process all API endpoints for legacy testing
        for (ApiInfo.ApiInfoKey apiInfoKey: apiInfoKeyList) {
            List<String> messages = testingUtil.getSampleMessages().get(apiInfoKey);
            if (messages == null || messages.isEmpty()) {
                countDownLatch(latch);
                createAndSaveSampleNotFoundResultForOneTestSubcategory(apiInfoKey, testingRun.getId(), summaryId, testingRunSubCategories, null, testConfigMap);
                continue;
            }
            String sample = messages.get(messages.size() - 1);
            if(sample == null || sample.isEmpty()){
                countDownLatch(latch);
                createAndSaveSampleNotFoundResultForOneTestSubcategory(apiInfoKey, testingRun.getId(), summaryId, testingRunSubCategories, null, testConfigMap);
                continue;
            }
            if(sample.contains("originalRequestPayload")){
                // make map of original request payload if this key is present
                Map<String, Object> json = gson.fromJson(sample, Map.class);
                String originalRequestPayload = (String) json.get("originalRequestPayload");
                if(originalRequestPayload != null && !originalRequestPayload.isEmpty()){
                    String key = apiInfoKey.getMethod() + "_" + apiInfoKey.getUrl();
                    OriginalReqResPayloadInformation.getInstance().getOriginalReqPayloadMap().put(key, originalRequestPayload);
                }
            }
            RawApi rawApi = RawApi.buildFromMessage(sample, true);
            if(rawApi != null){
                TestingConfigurations.getInstance().getRawApiMap().put(apiInfoKey, rawApi);
            }
            
            // Execute legacy testing
            try {
                Future<Void> future = threadPool.submit(() -> startWithLatch(testingRunSubCategories, accountId, apiInfoKey, messages, summaryId, syncLimit, apiInfoKeyToHostMap, subCategoryEndpointMap, testConfigMap, testLogs, testingRun, latch, finalApiInfoKeySubcategoryMap));
                testingRecords.add(future);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error in starting with latch for API " + apiInfoKey + " : " + e.getMessage());
                countDownLatch(latch);
            }
        }
        
        try {
            // Wait for all tests to complete with timeout handling
            int waitTs = Context.now();
            int prevCalcTime = Context.now();
            int lastCheckedCount = 0;
            while(latch.getCount() > 0 && GetRunningTestsStatus.getRunningTests().isTestRunning(summaryId) 
                && (Context.now() - waitTs < tempRunTime)) {
                    loggerMaker.infoAndAddToDb("waiting for tests to finish, count left: " + totalTestsCount.get());

                    if(lastCheckedCount != totalTestsCount.get()){
                        lastCheckedCount = totalTestsCount.get();
                        loggerMaker.debugInfoAddToDb("Total tests left to be executed :" + totalTestsCount.get());
                        prevCalcTime = Context.now();
                    }else{
                        if((Context.now() - prevCalcTime) > 20 * 60){
                            loggerMaker.debugInfoAddToDb("No new tests are being executed in the last 20 minutes, stopping the test run");
                            break;
                        }
                    }

                    Thread.sleep(10000);
            }

            // Cancel any remaining futures
            for (Future<Void> future : testingRecords) {
                future.cancel(true);
            }
            loggerMaker.insertImportantTestingLog("Legacy mode execution completed. All tests processed.");
            
        } catch (Exception e) {
            loggerMaker.insertImportantTestingLog("Error during legacy mode execution: " + e.getMessage());
            throw new RuntimeException("Legacy mode execution failed", e);
        } finally {
            threadPool.shutdown();
        }
    }

    public void init(TestingRun testingRun, ObjectId summaryId, SyncLimit syncLimit, boolean shouldInitOnly) {
        totalTestsCount.set(0);
        PrometheusMetricsHandler.markModuleBusy();
        if (testingRun.getTestIdConfig() != 1) {
            apiWiseInit(testingRun, summaryId, false, new ArrayList<>(), syncLimit, shouldInitOnly);
        } else {
            workflowInit(testingRun, summaryId, false, new ArrayList<>());
        }
    } 

    public void workflowInit (TestingRun testingRun, ObjectId summaryId, boolean debug, List<TestingRunResult.TestLog> testLogs) {
        TestingEndpoints testingEndpoints = testingRun.getTestingEndpoints();
        if (!testingEndpoints.getType().equals(TestingEndpoints.Type.WORKFLOW)) {
            loggerMaker.errorAndAddToDb("Invalid workflow type");
            return;
        }

        WorkflowTestingEndpoints workflowTestingEndpoints = (WorkflowTestingEndpoints) testingEndpoints;
        WorkflowTest workflowTestOld = workflowTestingEndpoints.getWorkflowTest();

        WorkflowTest workflowTest = dataActor.fetchWorkflowTest(workflowTestOld.getId());

        if (workflowTest == null) {
            loggerMaker.errorAndAddToDb("Workflow test has been deleted");
            return ;
        }

        ApiWorkflowExecutor apiWorkflowExecutor = new ApiWorkflowExecutor();
        try {
            Map<String, Object> valuesMap = new HashMap<>();
            Graph graph = new Graph();
            graph.buildGraph(workflowTest);
            GraphExecutorRequest graphExecutorRequest = new GraphExecutorRequest(graph, workflowTest, testingRun.getId(), summaryId, valuesMap, false, "linear");
            GraphExecutorResult graphExecutorResult = apiWorkflowExecutor.init(graphExecutorRequest, debug, testLogs, null);
            dataActor.insertWorkflowTestResult(graphExecutorResult.getWorkflowTestResult());
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while executing workflow test " + e);
        }

        Map<String, Integer> totalCountIssues = new HashMap<>();
        totalCountIssues.put("HIGH", 0);
        totalCountIssues.put("MEDIUM", 0);
        totalCountIssues.put("LOW", 0);

        dataActor.updateIssueCountInTestSummary(summaryId.toHexString(), totalCountIssues);
    }

    public void apiWiseInit(TestingRun testingRun, ObjectId summaryId, boolean debug, List<TestingRunResult.TestLog> testLogs, SyncLimit syncLimit, boolean shouldInitOnly) {

        // Clear authStatus hashmap for new test runs (different accounts)
        authStatus.clear();

        // write producer running here as producer has been initiated now
        int accountId = Context.accountId.get();

        BasicDBObject dbObject = new BasicDBObject();
        if(!shouldInitOnly && Constants.IS_NEW_TESTING_ENABLED){
            dbObject.put("PRODUCER_RUNNING", true);
            dbObject.put("CONSUMER_RUNNING", false);
            dbObject.put("accountId", accountId);
            dbObject.put("summaryId", summaryId.toHexString());
            dbObject.put("testingRunId", testingRun.getId().toHexString()); 
            writeJsonContentInFile(Constants.TESTING_STATE_FOLDER_PATH, Constants.TESTING_STATE_FILE_NAME, dbObject);
        }

        TestingEndpoints testingEndpoints = testingRun.getTestingEndpoints();

        List<ApiInfoKey> apiInfoKeyList;
        Map<ApiInfoKey, List<String>> apiInfoKeySubcategoryMap = null;
        Set<String> testingSubCategorySet = new HashSet<>();
        if (TestingConfigurations.getInstance().getTestingRunResultList() != null) {

            Set<ApiInfoKey> apiInfoKeySet = new HashSet<>();
            apiInfoKeySubcategoryMap = new HashMap<>();

            for (TestingRunResult testingRunResult: TestingConfigurations.getInstance().getTestingRunResultList()) {
                apiInfoKeySubcategoryMap
                        .computeIfAbsent(testingRunResult.getApiInfoKey(), k -> new ArrayList<>())
                        .add(testingRunResult.getTestSubType());
                apiInfoKeySet.add(testingRunResult.getApiInfoKey());

                testingSubCategorySet.add(testingRunResult.getTestSubType());
            }

            apiInfoKeyList = new ArrayList<>(apiInfoKeySet);
        } else {
            apiInfoKeyList = testingEndpoints.returnApis();
        }

        final Map<ApiInfoKey, List<String>> finalApiInfoKeySubcategoryMap = apiInfoKeySubcategoryMap;
        List<String> testingRunSubCategories;
        if (!testingSubCategorySet.isEmpty()) {
            testingRunSubCategories = new ArrayList<>(testingSubCategorySet);
        } else {
            if(testingRun.getTestingRunConfig().getTestSuiteIds() != null && !testingRun.getTestingRunConfig().getTestSuiteIds().isEmpty()){
                testingRunSubCategories = dataActor.findTestSubCategoriesByTestSuiteId(testingRun.getTestingRunConfig().getTestSuiteIds());
            }else{
                testingRunSubCategories = testingRun.getTestingRunConfig().getTestSubCategoryList();
            }
        }

        if (testingRun.getTestingRunConfig() != null) {
            dataActor.updateTestInitiatedCountInTestSummary(summaryId.toHexString(), testingRunSubCategories.size());
        }


        if (apiInfoKeyList == null || apiInfoKeyList.isEmpty()) return;
        loggerMaker.infoAndAddToDb("APIs found: " + apiInfoKeyList.size());
        boolean collectionWise = testingEndpoints.getType().equals(TestingEndpoints.Type.COLLECTION_WISE);

        SampleMessageStore sampleMessageStore = SampleMessageStore.create();
        if(collectionWise || apiInfoKeyList.size() > 500){
            sampleMessageStore.fetchSampleMessages(Main.extractApiCollectionIds(apiInfoKeyList));
        }else{
            sampleMessageStore.fetchSampleMessages(apiInfoKeyList);
        }
        

        List<TestRoles> testRoles = sampleMessageStore.fetchTestRoles();
        TestRoles attackerTestRole = Executor.fetchOrFindAttackerRole();
        
        List<YamlTemplate> yamlTemplates = new ArrayList<>();
        final int TEST_LIMIT = 50;
        List<YamlTemplate> yamlTemplatesTemp;
        for(int i = 0; i < testingRunSubCategories.size(); i += TEST_LIMIT) {
            int end = Math.min(i + TEST_LIMIT, testingRunSubCategories.size());
            List<String> subCategories = new ArrayList<>(testingRunSubCategories.subList(i, end)); // Make a copy
            yamlTemplatesTemp = dataActor.fetchYamlTemplatesWithIds(subCategories, true);
            if (yamlTemplatesTemp != null) {
                yamlTemplates.addAll(yamlTemplatesTemp);
            }
        }

        YamlTemplate commonTemplate = dataActor.fetchCommonWordList();

        Map<String, TestConfig> testConfigMap = YamlTemplateDao.instance.fetchTestConfigMap(false, false, yamlTemplates, commonTemplate);

        List<CustomAuthType> customAuthTypes = dataActor.fetchCustomAuthTypes();
        TestingUtil testingUtil = new TestingUtil(sampleMessageStore, testRoles, testingRun.getUserEmail(), customAuthTypes);

        Map<ApiInfo.ApiInfoKey, List<String>> sampleDataMapForStatusCodeAnalyser = new HashMap<>();
        Set<ApiInfo.ApiInfoKey> apiInfoKeySet = new HashSet<>(apiInfoKeyList);
        Map<ApiInfo.ApiInfoKey, List<String>> sampleMessages = sampleMessageStore.getSampleDataMap();
        for (ApiInfo.ApiInfoKey apiInfoKey: sampleMessages.keySet()) {
            if (apiInfoKeySet.contains(apiInfoKey)) {
                sampleDataMapForStatusCodeAnalyser.put(apiInfoKey, sampleMessages.get(apiInfoKey));
            }
        }

        int currentTime = Context.now();
        Map<String, String> hostAndContentType = new HashMap<>();
        try {
            loggerMaker.infoAndAddToDb("Starting findAllHosts at: " + currentTime);
            hostAndContentType = StatusCodeAnalyser.findAllHosts(sampleMessageStore, sampleDataMapForStatusCodeAnalyser);
            loggerMaker.infoAndAddToDb("Completing findAllHosts in: " + (Context.now() -  currentTime) + " at: " + Context.now());
        } catch (Exception e){
            loggerMaker.errorAndAddToDb("Error while running findAllHosts " + e.getMessage());
        }
        currentTime = Context.now();
        loggerMaker.infoAndAddToDb("Starting status code analyser");
        try {
            StatusCodeAnalyser.run(sampleDataMapForStatusCodeAnalyser, sampleMessageStore ,  attackerTestRole.findMatchingAuthMechanism(null), testingRun.getTestingRunConfig(), hostAndContentType);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while running status code analyser " + e.getMessage());
        }

        loggerMaker.infoAndAddToDb("StatusCodeAnalyser result = " + StatusCodeAnalyser.result + " defaultPayloadMap: " + StatusCodeAnalyser.defaultPayloadsMap + " calculated in: " + (Context.now() - currentTime));

        dataActor.updateTotalApiCountInTestSummary(summaryId.toHexString(), apiInfoKeyList.size());

        // Todo: Aryan? [entire for-loop] 
        ConcurrentHashMap<String, String> subCategoryEndpointMap = new ConcurrentHashMap<>();
        Map<ApiInfoKey, String> apiInfoKeyToHostMap = new HashMap<>();

        // init the singleton class here
        TestingConfigurations.getInstance().init(testingUtil, testingRun.getTestingRunConfig(), debug, testConfigMap, testingRun.getMaxConcurrentRequests(), testingRun.getDoNotMarkIssuesAsFixed());
        //Clear the cache for sample data
        VariableResolver.clearSampleDataCache();
        totalTestsCount.set(
            testingRunSubCategories.size() * apiInfoKeyList.size()
        );

        // Pre-fetch auth token before inserting records in Kafka
        if (testingRun.getTestingRunConfig() != null && testingRun.getTestingRunConfig().getTestRoleId() != null && !testingRun.getTestingRunConfig().getTestRoleId().isEmpty()) {
            try {
                TestRoles testRole = Executor.fetchOrFindTestRole(testingRun.getTestingRunConfig().getTestRoleId(), true);
                if (testRole != null) {
                    loggerMaker.infoAndAddToDb("Pre-fetching auth token for test role: " + testRole.getName(), LogDb.TESTING);
                    // Use first API to prefetch auth
                    RawApi rawApiForAuth = null;
                    if (apiInfoKeyList != null && !apiInfoKeyList.isEmpty()) {
                        ApiInfo.ApiInfoKey firstApiKey = apiInfoKeyList.get(0);
                        List<String> messages = testingUtil.getSampleMessages().get(firstApiKey);
                        if (messages != null && !messages.isEmpty()) {
                            try {
                                rawApiForAuth = RawApi.buildFromMessage(messages.get(messages.size() - 1), true);
                            } catch (Exception e) {
                                loggerMaker.errorAndAddToDb("Error building RawApi for auth prefetch: " + e.getMessage(), LogDb.TESTING);
                            }
                        }
                    }
                    
                    if (rawApiForAuth != null && !prefetchAuthWithRetry(testRole, rawApiForAuth, 3)) {
                        String errorMessage = "Failed to fetch auth token after three retries";
                        loggerMaker.errorAndAddToDb(errorMessage, LogDb.TESTING);
                        testLogs.add(new TestingRunResult.TestLog(TestingRunResult.TestLogType.ERROR, errorMessage));

                        Map<String, String> metadata = new HashMap<>();
                        metadata.put("error", errorMessage);
                        dataActor.updateIssueCountAndStateInSummary(summaryId.toHexString(), new HashMap<>(), State.FAILED.toString());
                        dataActor.updateMetadataInSummary(summaryId.toHexString(), metadata);
                        loggerMaker.infoAndAddToDb("Test run marked as FAILED due to auth failure", LogDb.TESTING);
                        return;
                    }
                    loggerMaker.infoAndAddToDb("Successfully pre-fetched auth token", LogDb.TESTING);
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error during auth prefetch: " + e.getMessage(), LogDb.TESTING);
            }
        }

        if(!shouldInitOnly){
            int maxThreads = Math.min(yamlTemplates.size(), 1000);
            if(maxThreads == 0){
                loggerMaker.infoAndAddToDb("Subcategories list are empty");
                return;
            }

            if(!Constants.IS_NEW_TESTING_ENABLED){
                maxThreads = Math.min(100, Math.max(10, testingRun.getMaxConcurrentRequests()));
            }

            List<Future<Void>> testingRecords = new ArrayList<>();
            ExecutorService threadPool = Executors.newFixedThreadPool(maxThreads);

            // create count down latch to know when inserting kafka records are completed.
            CountDownLatch latch = new CountDownLatch(apiInfoKeyList.size());
            int tempRunTime = 10 * 60;
            if(!Constants.IS_NEW_TESTING_ENABLED){
                tempRunTime = testingRun.getTestRunTime() <= 0 ? 30*60 : testingRun.getTestRunTime();
            }else{
                try {
                    Producer.createTopicWithRetries(Constants.LOCAL_KAFKA_BROKER_URL, Constants.TEST_RESULTS_TOPIC_NAME);
                } catch (Exception e) {
                    e.printStackTrace();
                    loggerMaker.errorAndAddToDb(e, "Error in creating topic");
                }
            }

            final int maxRunTime = tempRunTime;
            AtomicInteger totalRecords = new AtomicInteger(0);
            AtomicInteger throttleNumber = new AtomicInteger(0);
            for (ApiInfo.ApiInfoKey apiInfoKey: apiInfoKeyList) {
                List<String> messages = testingUtil.getSampleMessages().get(apiInfoKey);
                if (messages == null || messages.isEmpty()) {
                    countDownLatch(latch);
                    createAndSaveSampleNotFoundResultForOneTestSubcategory(apiInfoKey, testingRun.getId(), summaryId, testingRunSubCategories, apiInfoKeySubcategoryMap, testConfigMap);
                    continue;
                }
                String sample = messages.get(messages.size() - 1);
                if(sample == null || sample.isEmpty()){
                    countDownLatch(latch);
                    createAndSaveSampleNotFoundResultForOneTestSubcategory(apiInfoKey, testingRun.getId(), summaryId, testingRunSubCategories, apiInfoKeySubcategoryMap, testConfigMap);
                    continue;
                }
                if(sample.contains("originalRequestPayload")){
                    // make map of original request payload if this key is present
                    Map<String, Object> json = gson.fromJson(sample, Map.class);
                    String originalRequestPayload = (String) json.get("originalRequestPayload");
                    if(originalRequestPayload != null && !originalRequestPayload.isEmpty()){
                        String key = apiInfoKey.getMethod() + "_" + apiInfoKey.getUrl();
                        OriginalReqResPayloadInformation.getInstance().getOriginalReqPayloadMap().put(key, originalRequestPayload);
                    }
                }
                RawApi rawApi = RawApi.buildFromMessage(sample, true);
                if(rawApi != null){
                    TestingConfigurations.getInstance().getRawApiMap().put(apiInfoKey, rawApi);
                }
                if(Constants.IS_NEW_TESTING_ENABLED){
                    for (String testSubCategory: testingRunSubCategories) {
                        if (apiInfoKeySubcategoryMap == null || apiInfoKeySubcategoryMap.get(apiInfoKey).contains(testSubCategory)) {
                            insertRecordInKafka(accountId, testSubCategory, apiInfoKey, messages, summaryId, syncLimit, apiInfoKeyToHostMap, subCategoryEndpointMap, testConfigMap, testLogs, testingRun, new AtomicBoolean(false), totalRecords, throttleNumber);
                        }
                    }
                } else {
                    try {
                        Future<Void> future = threadPool.submit(() -> startWithLatch(testingRunSubCategories, accountId,
                                apiInfoKey, messages, summaryId, syncLimit, apiInfoKeyToHostMap, subCategoryEndpointMap,
                                testConfigMap, testLogs, testingRun, latch, finalApiInfoKeySubcategoryMap));
                        testingRecords.add(future);
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(e, "Error in starting with latch for API " + apiInfoKey + " : " + e.getMessage());
                        countDownLatch(latch);
                    }
                }
            }
            try {
                if(!Constants.IS_NEW_TESTING_ENABLED){
                    int waitTs = Context.now();
                    int prevCalcTime = Context.now();
                    int lastCheckedCount = 0;
                    while(latch.getCount() > 0 && GetRunningTestsStatus.getRunningTests().isTestRunning(summaryId) 
                        && (Context.now() - waitTs < maxRunTime)) {
                            loggerMaker.infoAndAddToDb("waiting for tests to finish, count left: " + totalTestsCount.get());

                            if(lastCheckedCount != totalTestsCount.get()){
                                lastCheckedCount = totalTestsCount.get();
                                loggerMaker.debugInfoAddToDb("Total tests left to be executed :" + totalTestsCount.get());
                                prevCalcTime = Context.now();
                            }else{
                                if((Context.now() - prevCalcTime) > 20 * 60){
                                    loggerMaker.debugInfoAddToDb("No new tests are being executed in the last 20 minutes, stopping the test run");
                                    break;
                                }
                            }

                            Thread.sleep(10000);
                    }
    
                    for (Future<Void> future : testingRecords) {
                        future.cancel(!Constants.IS_NEW_TESTING_ENABLED);
                    }
                    loggerMaker.infoAndAddToDb("Canceled all running future tasks due to timeout.");
                }else{
                    // This else block only executes when IS_NEW_TESTING_ENABLED is true AND kafkaFallbackMode is false
                    // So we can directly proceed with Kafka completion logic
                    Thread.sleep(20000); // wait for 20 seconds to ensure all messages are sent

                    int unsentRecords = throttleNumber.get();
                    loggerMaker.insertImportantTestingLog("Finished inserting records in kafka, Total records: " + totalRecords.get() + " Unsent records: " + unsentRecords);

                    // Add detailed logging for unsent records analysis
                    if (unsentRecords == totalRecords.get()) {
                        // Check producer status
                        loggerMaker.infoAndAddToDb("Producer status: " + Producer.getProducerStatus());
                        loggerMaker.infoAndAddToDb("KAFKA FAILURE DETECTED: All " + totalRecords.get() + " records failed to send. Switching to legacy mode immediately to run all tests.");
                        
                        // Set fallback mode for current execution
                        currentExecutionFallback = true;
                        
                        // Execute all tests using legacy approach immediately
                        executeAllTestsInLegacyMode(testingRun, summaryId, syncLimit, apiInfoKeyList, testingRunSubCategories, 
                            testingUtil, finalApiInfoKeySubcategoryMap, apiInfoKeyToHostMap, subCategoryEndpointMap,
                            testConfigMap, testLogs, accountId);
                    } else {
                        loggerMaker.infoAndAddToDb("All records sent successfully to Kafka");
                        
                        // Normal Kafka completion - start consumer
                        dbObject.put("PRODUCER_RUNNING", false);
                        dbObject.put("CONSUMER_RUNNING", true);
                        writeJsonContentInFile(Constants.TESTING_STATE_FOLDER_PATH, Constants.TESTING_STATE_FILE_NAME, dbObject);
                    }
                }
                

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void createAndSaveSampleNotFoundResultForOneTestSubcategory(ApiInfoKey apiInfoKey, ObjectId testingRunId,
            ObjectId testingRunResultSummaryId, List<String> testingRunSubCategories,
            Map<ApiInfoKey, List<String>> apiInfoKeySubcategoryMap, Map<String, TestConfig> testConfigMap) {
        try {
            for (String testSubCategory : testingRunSubCategories) {
                if (apiInfoKeySubcategoryMap == null
                        || apiInfoKeySubcategoryMap.get(apiInfoKey).contains(testSubCategory)) {
                    TestConfig testConfig = testConfigMap.get(testSubCategory);
                    if (testConfig != null) {
                        // save for one test subcategory if no sample is found
                        // since the same result will be saved for all test subcategories
                        String testSuperType = testConfig.getInfo().getCategory().getName();
                        String testSubType = testConfig.getInfo().getSubCategory();
                        TestingRunResult runResult = generateFailedRunResultForMessage(
                                testingRunId, apiInfoKey, testSuperType, testSubType,
                                testingRunResultSummaryId, new ArrayList<>(), TestError.NO_PATH.getMessage());
                        this.insertResultsAndMakeIssues(Collections.singletonList(runResult),
                                testingRunResultSummaryId);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e,
                    "Error while creating and saving sample not found result for one test subcategory: "
                            + e.getMessage());
        }
    }

    public static void updateTestSummary(ObjectId summaryId){
        loggerMaker.infoAndAddToDb("Finished updating results count");

         TestingRunResultSummary currentSummary = dataActor.fetchTestingRunResultSummary(summaryId.toHexString());

        State updatedState;
        if (currentSummary != null && currentSummary.getState() == State.FAILED) {
            // Keep FAILED state (auth failed)
            updatedState = State.FAILED;
            loggerMaker.infoAndAddToDb("Preserving FAILED state for test run", LogDb.TESTING);
        } else {
            updatedState = GetRunningTestsStatus.getRunningTests().isTestRunning(summaryId) ? State.COMPLETED : GetRunningTestsStatus.getRunningTests().getCurrentState(summaryId);
        }
        

        int skip = 0;
        int limit = 1000;
        boolean fetchMore = false;
        do {
            fetchMore = false;
            List<TestingRunResult> testingRunResults = dataActor.fetchLatestTestingRunResultBySummaryId(summaryId.toHexString(), limit, skip);
            loggerMaker.infoAndAddToDb("Reading " + testingRunResults.size() + " vulnerable testingRunResults");
            if (testingRunResults.size() == limit) {
                skip += limit;
                fetchMore = true;
            }

        } while (fetchMore);

        TestingRunResultSummary testingRunResultSummary = dataActor.updateIssueCountAndStateInSummary(summaryId.toHexString(), new HashMap<>(), updatedState.toString());
        if (TestingConfigurations.getInstance().getRerunTestingRunResultSummary() != null) {
            dataActor.deleteTestRunResultSummary(TestingConfigurations.getInstance().getRerunTestingRunResultSummary().getId().toHexString());
            loggerMaker.infoAndAddToDb("Deleting rerun testing result summary after completion of test: TRRS_ID:" + TestingConfigurations.getInstance().getRerunTestingRunResultSummary().getHexId());
            TestingConfigurations.getInstance().setRerunTestingRunResultSummary(null);
        }
        // GithubUtils.publishGithubComments(testingRunResultSummary);
    }

    public static Severity getSeverityFromTestingRunResult(TestingRunResult testingRunResult){
        Severity severity = Severity.HIGH;
        try {
            Confidence confidence = testingRunResult.getTestResults().get(0).getConfidence();
            severity = Severity.valueOf(confidence.toString());
        } catch (Exception e){
        }
        return severity;
    }

    public static OriginalHttpRequest findOriginalHttpRequest(ApiInfo.ApiInfoKey apiInfoKey, Map<ApiInfo.ApiInfoKey, List<String>> sampleMessagesMap, SampleMessageStore sampleMessageStore){
        if (sampleMessagesMap==null || sampleMessagesMap.isEmpty()) {
            return null;
        }
        List<String> sampleMessages = sampleMessagesMap.get(apiInfoKey);
        if (sampleMessages == null || sampleMessages.isEmpty()) {
            return null;
        }
        String message = sampleMessages.get(sampleMessages.size() - 1);
        if(shouldCallClientLayerForSampleData){
            try {
                message = clientLayer.fetchLatestSample(apiInfoKey);
                if(message == null){
                    loggerMaker.infoAndAddToDb("No fetchLatestSample response found for " + apiInfoKey.toString() + " from testing db layer in findOriginalHttpRequest");
                }else if (!message.contains("requestPayload") && privateKey != null) {
                    message = PayloadEncodeUtil.decryptPacked(message, privateKey);
                }
            } catch (JWTVerificationException e) {
                loggerMaker.errorAndAddToDb(e, "Error while decoding encoded payload in findOriginalHttpRequest: " + e.getMessage());
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error while fetching sample from testing db layer in findOriginalHttpRequest: " + e.getMessage());
            }
            if (message == null) {
                return null;
            }
        }
        OriginalHttpRequest originalHttpRequest = new OriginalHttpRequest();
        originalHttpRequest.buildFromSampleMessage(message);
        return originalHttpRequest;
    }

    public static String findHostFromOriginalHttpRequest(OriginalHttpRequest originalHttpRequest)
            throws URISyntaxException {
        if(originalHttpRequest == null){
            return null;
        }
        String baseUrl = originalHttpRequest.getUrl();
        if (baseUrl.startsWith("http")) {
            URI uri = new URI(baseUrl);
            String host = uri.getScheme() + "://" + uri.getHost();
            return (uri.getPort() != -1) ? host + ":" + uri.getPort() : host;
        } else {
            return "https://" + originalHttpRequest.findHostFromHeader();
        }
    }

    public static String findContentTypeFromOriginalHttpRequest(OriginalHttpRequest originalHttpRequest) {
        Map<String, List<String>> headers = originalHttpRequest.getHeaders();
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        final String CONTENT_TYPE = "content-type";
        if (headers.containsKey(CONTENT_TYPE)) {
            List<String> headerValues = headers.get(CONTENT_TYPE);
            if (headerValues == null || headerValues.isEmpty()) {
                return null;
            }
            return headerValues.get(0);
        }
        return null;
    }


    private LoginFlowResponse triggerLoginFlow(AuthMechanism authMechanism, int retries) {
        LoginFlowResponse loginFlowResponse = null;
        for (int i=0; i<retries; i++) {
            try {
                loginFlowResponse = executeLoginFlow(authMechanism, null);
                if (loginFlowResponse.getSuccess()) {
                    loggerMaker.infoAndAddToDb("login flow success");
                    break;
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e.getMessage());
            }
        }
        return loginFlowResponse;
    }

    public static LoginFlowResponse executeLoginFlow(AuthMechanism authMechanism, LoginFlowParams loginFlowParams) throws Exception {

        if (authMechanism.getType() == null) {
            loggerMaker.infoAndAddToDb("auth type value is null");
            return new LoginFlowResponse(null, null, true);
        }

        if (!authMechanism.getType().equals(LoginFlowEnums.AuthMechanismTypes.LOGIN_REQUEST.toString())) {
            loggerMaker.infoAndAddToDb("invalid auth type for login flow execution");
            return new LoginFlowResponse(null, null, true);
        }

        loggerMaker.infoAndAddToDb("login flow execution started");

        WorkflowTest workflowObj = convertToWorkflowGraph(authMechanism.getRequestData());
        ApiWorkflowExecutor apiWorkflowExecutor = new ApiWorkflowExecutor();
        LoginFlowResponse loginFlowResp;
        loginFlowResp =  Utils.runLoginFlow(workflowObj, authMechanism, loginFlowParams);
        return loginFlowResp;
    }

    public static WorkflowTest convertToWorkflowGraph(ArrayList<RequestData> requestData) {

        String source, target;
        List<String> edges = new ArrayList<>();
        int edgeNumber = 1;
        LoginWorkflowGraphEdge edgeObj;
        Map<String,WorkflowNodeDetails> mapNodeIdToWorkflowNodeDetails = new HashMap<>();
        for (int i=0; i< requestData.size(); i++) {

            RequestData data = requestData.get(i);
            source = (i==0)? "1" : "x"+ (edgeNumber - 2);
            target = "x"+ edgeNumber;
            edgeNumber += 2;

            edgeObj = new LoginWorkflowGraphEdge(source, target, target);
            edges.add(edgeObj.toString());

            JSONObject json = new JSONObject() ;
            json.put("method", data.getMethod());
            json.put("requestPayload", data.getBody());
            json.put("path", data.getUrl());
            json.put("requestHeaders", data.getHeaders());
            json.put("type", "");

            WorkflowUpdatedSampleData sampleData = new WorkflowUpdatedSampleData(json.toString(), data.getQueryParams(),
                    data.getHeaders(), data.getBody(), data.getUrl());

            int waitTime = 0;
            WorkflowNodeDetails.Type nodeType = WorkflowNodeDetails.Type.API;
            if ((data.getType() != null
                    && data.getType().equals(LoginFlowEnums.LoginStepTypesEnums.OTP_VERIFICATION.toString()))
                    || (data.getUrl() != null && data.getUrl().contains("fetchOtpData"))) {
                nodeType = WorkflowNodeDetails.Type.OTP;
                waitTime = 60;
            }
            if (data.getType() != null
                    && data.getType().equals(LoginFlowEnums.LoginStepTypesEnums.RECORDED_FLOW.toString())) {
                nodeType = WorkflowNodeDetails.Type.RECORDED;
            }

            WorkflowNodeDetails workflowNodeDetails = new WorkflowNodeDetails(0, data.getUrl(),
                    URLMethods.Method.fromString(data.getMethod()), "", sampleData, nodeType,
                    true, waitTime, 0, 0, data.getRegex(), data.getOtpRefUuid());
            mapNodeIdToWorkflowNodeDetails.put(target, workflowNodeDetails);
        }

        edgeObj = new LoginWorkflowGraphEdge("x"+ (edgeNumber - 2), "3", "x"+ edgeNumber);
        edges.add(edgeObj.toString());

        return new WorkflowTest(0, 0, "", Context.now(), "", Context.now(),
                null, edges, mapNodeIdToWorkflowNodeDetails, WorkflowTest.State.DRAFT);
    }

    public Map<String, Object> generateResponseMap(String payloadStr, Map<String, List<String>> headers) {
        boolean isList = false;

        Map<String, Object> respMap = new HashMap<>();

        if (payloadStr == null) payloadStr = "{}";
        if (payloadStr.startsWith("[")) {
            payloadStr = "{\"json\": "+payloadStr+"}";
            isList = true;
        }

        BasicDBObject payloadObj;
        try {
            payloadObj = BasicDBObject.parse(payloadStr);
        } catch (Exception e) {
            boolean isPostFormData = payloadStr.contains("&") && payloadStr.contains("=");
            if (isPostFormData) {
                String mockUrl = "url?"+ payloadStr; // because getQueryJSON function needs complete url
                payloadObj = RequestTemplate.getQueryJSON(mockUrl);
            } else {
                payloadObj = BasicDBObject.parse("{}");
            }
        }

        Object obj;
        if (isList) {
            obj = payloadObj.get("json");
        } else {
            obj = payloadObj;
        }

        BasicDBObject flattened = JSONUtils.flattenWithDots(obj);


        for (String param: flattened.keySet()) {
            respMap.put(param, flattened.get(param));
        }

        for (String headerName: headers.keySet()) {
            for (String val: headers.get(headerName)) {
                respMap.put(headerName, val);
            }
        }
        return respMap;
    }

    public Void startWithLatch(
        List<String> testingRunSubCategories,int accountId,ApiInfo.ApiInfoKey apiInfoKey,
        List<String> messages, ObjectId summaryId, SyncLimit syncLimit, Map<ApiInfoKey, String> apiInfoKeyToHostMap,
        ConcurrentHashMap<String, String> subCategoryEndpointMap, Map<String, TestConfig> testConfigMap,
        List<TestingRunResult.TestLog> testLogs, TestingRun testingRun, CountDownLatch latch, Map<ApiInfoKey, List<String>> apiInfoKeySubcategoryMap) {

        Context.accountId.set(accountId);
        loggerMaker.warnAndAddToDb("Starting test for " + apiInfoKey);
        AtomicBoolean isApiInfoTested = new AtomicBoolean(false);
        try {
            for (String testSubCategory: testingRunSubCategories) {
                if (apiInfoKeySubcategoryMap == null || apiInfoKeySubcategoryMap.get(apiInfoKey).contains(testSubCategory)) {
                    if(GetRunningTestsStatus.getRunningTests().isTestRunning(summaryId)){
                        insertRecordInKafka(accountId, testSubCategory, apiInfoKey, messages, summaryId, syncLimit, apiInfoKeyToHostMap, subCategoryEndpointMap, testConfigMap, testLogs, testingRun, isApiInfoTested, new AtomicInteger(0), new AtomicInteger(0));
                    }else{
                        loggerMaker.warnAndAddToDb("Test stopped for id: " + testingRun.getHexId());
                        break;
                    }
                }
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "error while running tests: " + e);
        }
        if(isApiInfoTested.get()){
            loggerMaker.warnAndAddToDb("API: " + apiInfoKey.toString() + " has been successfully tested");
            dataActor.updateLastTestedField(apiInfoKey.getApiCollectionId(), apiInfoKey.getUrl(), apiInfoKey.getMethod().toString());
        }
        countDownLatch(latch);
        return null;
    }

    public void countDownLatch(CountDownLatch latch) {
        latch.countDown();
        loggerMaker.warnAndAddToDb("DONE FINAL: " + latch.getCount() + " totalTestsCount: " + totalTestsCount.get());
    }

    public static void trim(TestingRunResult testingRunResult) {
        List<GenericTestResult> testResults = testingRunResult.getTestResults();
        int endIdx = testResults.size();
        long currentSize = 0;

        for (int idx=0;idx< testResults.size();idx++) {
            GenericTestResult tr = testResults.get(idx);

            if (tr instanceof MultiExecTestResult) {
                return;
            }

            TestResult testResult = (TestResult) tr;

            String originalMessage = testResult.getOriginalMessage();
            long originalMessageSize = originalMessage == null ? 0 : originalMessage.getBytes().length;

            String message = testResult.getMessage();
            long messageSize = message == null ? 0 : message.getBytes().length;

            currentSize += originalMessageSize + messageSize;

            if (currentSize > acceptableSizeInBytes) {
                endIdx = idx;
                break;
            }
        }

        testResults = testResults.subList(0,endIdx);
        testingRunResult.setTestResults(testResults);
    }

    public void trim(List<TestingRunResult> testingRunResults) {
        for (TestingRunResult testingRunResult: testingRunResults) {
            trim(testingRunResult);
        }
    }

    public void insertResultsAndMakeIssues(List<TestingRunResult> testingRunResults, ObjectId testRunResultSummaryId) {
        int resultSize = testingRunResults.size();
        if (resultSize > 0) {
            loggerMaker.infoAndAddToDb("testingRunResults size: " + resultSize);
            trim(testingRunResults);
            TestingRunResult originalTestingRunResultForRerun = TestingConfigurations.getInstance().getTestingRunResultForApiKeyInfo(testingRunResults.get(0).getApiInfoKey(), testingRunResults.get(0).getTestSubType());
            if (originalTestingRunResultForRerun != null) {
                loggerMaker.infoAndAddToDb("Deleting original testingRunResults for rerun after replaced with run TRR_ID: " + originalTestingRunResultForRerun.getHexId());
                dataActor.deleteTestingRunResults(originalTestingRunResultForRerun.getHexId());
                /*
                 * delete from vulnerableTestResults as well.
                 * assuming if original was vulnerable, entry will be in VulnerableTestingRunResultDao
                 * for API_INFO_KEY, TEST_RUN_RESULT_SUMMARY_ID, TEST_SUB_TYPE, VulnerableTestingRunResultDao will have
                 * single entry
                 * */
            }
            TestingRunResult trr = testingRunResults.get(0);
            trr.setTestRunHexId(trr.getTestRunHexId());
            trr.setTestRunResultSummaryHexId(trr.getTestRunResultSummaryHexId());
            GenericTestResult testRes = trr.getTestResults().get(0);
            if (testRes instanceof TestResult) {
                List<TestResult> list = new ArrayList<>();
                for(GenericTestResult testResult: trr.getTestResults()){
                    list.add((TestResult) testResult);
                }
                trr.setSingleTestResults(list);
            } else {
                List<MultiExecTestResult> list = new ArrayList<>();
                for(GenericTestResult testResult: trr.getTestResults()){
                    list.add((MultiExecTestResult) testResult);
                }
                trr.setMultiExecTestResults(list);
            }
            trr.setTestResults(null);
            trr.setTestLogs(null);
            dataActor.insertTestingRunResults(trr);
            loggerMaker.infoAndAddToDb("Inserted testing results");
            dataActor.updateTestResultsCountInTestSummary(testRunResultSummaryId.toHexString(), resultSize);
            loggerMaker.infoAndAddToDb("Updated count in summary");

            TestingIssuesHandler handler = new TestingIssuesHandler();
            boolean triggeredByTestEditor = false;
            try{
                List<GenericTestResult> list = new ArrayList<>();
                list.add(testRes);
                trr.setTestResults(list);
                handler.handleIssuesCreationFromTestingRunResults(testingRunResults, triggeredByTestEditor);
            } catch (Exception e){
                loggerMaker.errorAndAddToDb(e, "Unable to create issues");
            }
        }
    }

    private Void insertRecordInKafka(int accountId, String testSubCategory, ApiInfo.ApiInfoKey apiInfoKey,
            List<String> messages, ObjectId summaryId, SyncLimit syncLimit, Map<ApiInfoKey, String> apiInfoKeyToHostMap,
            ConcurrentHashMap<String, String> subCategoryEndpointMap, Map<String, TestConfig> testConfigMap,
            List<TestingRunResult.TestLog> testLogs, TestingRun testingRun, AtomicBoolean isApiInfoTested, AtomicInteger totalRecords, AtomicInteger throttleNumber) {
        Context.accountId.set(accountId);
        TestConfig testConfig = testConfigMap.get(testSubCategory);
        if (testConfig == null) {
            totalTestsCount.decrementAndGet();
            if(Constants.KAFKA_DEBUG_MODE){
                loggerMaker.infoAndAddToDb("Found testing config null: " + apiInfoKey.toString() + " : " + testSubCategory);
            }
            return null;
        }

        if (!applyRunOnceCheck(apiInfoKey, testConfig, subCategoryEndpointMap, apiInfoKeyToHostMap, testSubCategory)) {
            totalTestsCount.decrementAndGet();
            return null;
        }

        String testSuperType = testConfig.getInfo().getCategory().getName();
        String testSubType = testConfig.getInfo().getSubCategory();

        String failMessage = null;
        TestingRunResult testingRunResult = com.akto.testing.Utils.generateFailedRunResultForMessage(testingRun.getId(), apiInfoKey, testSuperType, testSubType, summaryId, messages, failMessage); 
        if(testingRunResult != null){
            if(Constants.KAFKA_DEBUG_MODE){
                loggerMaker.infoAndAddToDb("Skipping test from producers because: " + failMessage + " apiInfo: " + apiInfoKey.toString());
            }
        }else if (Constants.IS_NEW_TESTING_ENABLED && !currentExecutionFallback){
            // push data to kafka here and inside that call run test new function
            // create an object of TestMessage
            SingleTestPayload singleTestPayload = new SingleTestPayload(
                testingRun.getId(), summaryId, apiInfoKey, testSubType, testLogs, accountId
            );
            if(Constants.KAFKA_DEBUG_MODE){
                loggerMaker.info("Inserting record for apiInfoKey: " + apiInfoKey.toString() + " subcategory: " + testSubType);
            }
            try {
                Producer.pushMessagesToKafka(Arrays.asList(singleTestPayload), totalRecords, throttleNumber);
            } catch (Exception e) {
                loggerMaker.insertImportantTestingLog("Kafka push failed. Error: " + e.getMessage());
                executeLegacyTesting(apiInfoKey, summaryId, messages, testConfig, testLogs, isApiInfoTested);
                throttleNumber.decrementAndGet();
            }
        }else{ 
            // Use legacy testing approach (either IS_NEW_TESTING_ENABLED is false OR kafkaFallbackMode is true)
            executeLegacyTesting(apiInfoKey, summaryId, messages, testConfig, testLogs, isApiInfoTested);
            throttleNumber.decrementAndGet();
        }
        totalTestsCount.decrementAndGet();
        return null;
    }

    /**
     * Executes legacy testing approach (fallback mode)
     * @return TestingRunResult if test executed, null otherwise
     */
    private TestingRunResult executeLegacyTesting(ApiInfo.ApiInfoKey apiInfoKey, ObjectId summaryId, 
            List<String> messages, TestConfig testConfig, List<TestingRunResult.TestLog> testLogs, 
            AtomicBoolean isApiInfoTested) {
        
        if(GetRunningTestsStatus.getRunningTests().isTestRunning(summaryId)){
            TestingConfigurations instance = TestingConfigurations.getInstance();
            String sampleMessage = messages.get(messages.size() - 1);
            TestingRunResult testingRunResult = runTestNew(apiInfoKey, summaryId, instance.getTestingUtil(), summaryId, testConfig, instance.getTestingRunConfig(), instance.isDebug(), testLogs, sampleMessage);
            if (testingRunResult != null) {
                List<String> errorList = testingRunResult.getErrorsList();
                if (errorList == null || !errorList.contains(TestResult.API_CALL_FAILED_ERROR_STRING)) {
                    isApiInfoTested.set(true);
                }
            }
            insertResultsAndMakeIssues(Collections.singletonList(testingRunResult), summaryId);
            return testingRunResult;
        }
        return null;
    }

    // Track auth status per test run: if auth fails, kill all remaining tests
    private static final ConcurrentHashMap<String, Boolean> authStatus = new ConcurrentHashMap<>();

    public boolean applyRunOnceCheck(ApiInfoKey apiInfoKey, TestConfig testConfig, ConcurrentHashMap<String, String> subCategoryEndpointMap, Map<ApiInfoKey, String> apiInfoKeyToHostMap, String testSubCategory) {

        if (testConfig.getStrategy() == null || testConfig.getStrategy().getRunOnce() == null) {
            return true;
        }

        String val = subCategoryEndpointMap.get(apiInfoKey.getApiCollectionId() + "_" + testSubCategory);
        if (val == null) {
            subCategoryEndpointMap.put(apiInfoKey.getApiCollectionId() + "_" + testSubCategory, "true");
            return true;
        }
        return false;
    }

    //Set<Integer> deactivatedCollections = UsageMetricCalculator.getDeactivated();

    public TestingRunResult runTestNew(ApiInfo.ApiInfoKey apiInfoKey, ObjectId testRunId, TestingUtil testingUtil,
        ObjectId testRunResultSummaryId, TestConfig testConfig, TestingRunConfig testingRunConfig, boolean debug, List<TestingRunResult.TestLog> testLogs, String message) {
            RawApi rawApi = TestingConfigurations.getInstance().getRawApiMap().get(apiInfoKey);
            if (rawApi == null) {
                rawApi = RawApi.buildFromMessage(message, true);
                TestingConfigurations.getInstance().getRawApiMap().put(apiInfoKey, rawApi);
            }
                     
            TestRoles attackerTestRole = Executor.fetchOrFindAttackerRole();
            AuthMechanism attackerAuthMechanism = null;
            if (attackerTestRole == null) {
                loggerMaker.infoAndAddToDb("ATTACKER_TOKEN_ALL test role not found");
            } else {
                attackerAuthMechanism = attackerTestRole.findMatchingAuthMechanism(rawApi);
            }
            return runTestNew(apiInfoKey, testRunId, testingUtil.getSampleMessageStore(), attackerAuthMechanism, testingUtil.getCustomAuthTypes(), testRunResultSummaryId, testConfig, testingRunConfig, debug, testLogs, rawApi);
    }

    public TestingRunResult runTestNew(ApiInfo.ApiInfoKey apiInfoKey, ObjectId testRunId, SampleMessageStore sampleMessageStore, AuthMechanism attackerAuthMechanism, List<CustomAuthType> customAuthTypes,
                                       ObjectId testRunResultSummaryId, TestConfig testConfig, TestingRunConfig testingRunConfig, boolean debug, List<TestingRunResult.TestLog> testLogs, RawApi rawApi) {
        String testSuperType = testConfig.getInfo().getCategory().getName();
        String testSubType = testConfig.getInfo().getSubCategory();
        if(shouldCallClientLayerForSampleData){
            try {
                long start = System.currentTimeMillis();
                String msg = null;
                
                try {
                    msg = clientLayer.fetchLatestSample(apiInfoKey);
                    if(msg == null){
                        loggerMaker.infoAndAddToDb("No fetchLatestSample response found for " + apiInfoKey.toString() + " from testing db layer in runTestNew");
                    }else if (!msg.contains("requestPayload") && privateKey != null) {
                            msg = PayloadEncodeUtil.decryptPacked(msg, privateKey);
                    }
                } catch (JWTVerificationException e) {
                    loggerMaker.errorAndAddToDb(e, "Error while decoding encoded payload in runTestNew: " + e.getMessage());
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Error while fetching sample from testing db layer in runTestNew: " + e.getMessage());
                }
                if (msg != null) {
                    rawApi = RawApi.buildFromMessage(msg, true);
                }
                AllMetrics.instance.setSampleDataFetchLatency(System.currentTimeMillis() - start);
            } catch (Exception e) {
                e.printStackTrace();
                loggerMaker.errorAndAddToDb(e, "Error while fetchLatestSample in runTestNew: " + e.getMessage());
            }
        }
        int startTime = Context.now();

        try {
            boolean isGraphQlPayload = filterGraphQlPayload(rawApi, apiInfoKey);
            if (isGraphQlPayload) testLogs.add(new TestingRunResult.TestLog(TestingRunResult.TestLogType.INFO, "GraphQL payload found"));
        } catch (Exception e) {
            testLogs.add(new TestingRunResult.TestLog(TestingRunResult.TestLogType.ERROR, e.getMessage()));
        }

        FilterNode filterNode = testConfig.getApiSelectionFilters().getNode();
        FilterNode validatorNode = null;
        if (testConfig.getValidation() != null) {
            validatorNode = testConfig.getValidation().getNode();
        }
        ExecutorNode executorNode = testConfig.getExecute().getNode();
        Auth auth = testConfig.getAuth();
        Map<String, List<String>> wordListsMap = testConfig.getWordlists();
        Map<String, Object> varMap = new HashMap<>();
        String severity = testConfig.getInfo().getSeverity();

        for (String key: wordListsMap.keySet()) {
            varMap.put("wordList_" + key, wordListsMap.get(key));
        }

        VariableResolver.resolveWordList(varMap, sampleMessageStore.getSampleDataMap(), apiInfoKey);

        String testExecutionLogId = UUID.randomUUID().toString();
        
        loggerMaker.infoAndAddToDb("triggering test run for apiInfoKey " + apiInfoKey + "test " + 
            testSubType + " logId " + testExecutionLogId);

        com.akto.test_editor.execution.Executor executor = new Executor();
        executor.overrideTestUrl(rawApi, testingRunConfig);
        YamlTestTemplate yamlTestTemplate = new YamlTestTemplate(apiInfoKey,filterNode, validatorNode, executorNode,
                rawApi, varMap, auth, attackerAuthMechanism, testExecutionLogId, testingRunConfig, customAuthTypes, testConfig.getStrategy());
        YamlTestResult testResults = yamlTestTemplate.run(debug, testLogs);
        if (testResults == null || testResults.getTestResults().isEmpty()) {
            List<GenericTestResult> res = new ArrayList<>();
            res.add(new TestResult(null, rawApi.getOriginalMessage(), Collections.singletonList(TestError.SOMETHING_WENT_WRONG.getMessage()), 0, false, TestResult.Confidence.HIGH, null));
            testResults.setTestResults(res);
        }
        int endTime = Context.now();

        boolean vulnerable = false;
        for (GenericTestResult testResult: testResults.getTestResults()) {
            if (testResult == null) continue;
            vulnerable = vulnerable || testResult.isVulnerable();
            try {
                testResult.setConfidence(Confidence.valueOf(severity));
            } catch (Exception e){
                testResult.setConfidence(Confidence.HIGH);
            }
            // dynamic severity for tests
            Confidence overConfidence = getConfidenceForTests(testConfig, yamlTestTemplate);
            if (overConfidence != null) {
                testResult.setConfidence(overConfidence);
            }
        }

        List<SingleTypeInfo> singleTypeInfos = new ArrayList<>();

        int confidencePercentage = 100;

        TestingRunResult ret = new TestingRunResult(
            testRunId, apiInfoKey, testSuperType, testSubType ,testResults.getTestResults(),
            vulnerable,singleTypeInfos,confidencePercentage,startTime,
            endTime, testRunResultSummaryId, testResults.getWorkflowTest(), testLogs);  

        if (testingRunConfig!=null && testingRunConfig.getCleanUp()) {
            try {
                cleanUpTestArtifacts(Collections.singletonList(ret), apiInfoKey, sampleMessageStore, testingRunConfig);
            } catch(Exception e){
                loggerMaker.errorAndAddToDb(e, "Error while cleaning up test artifacts: " + e.getMessage());
            }
        }

        return ret;
    }

    private Map<ApiInfoKey, List<ApiInfoKey>> cleanUpTestArtifacts(List<TestingRunResult> testingRunResults, ApiInfoKey apiInfoKey, SampleMessageStore sampleMessageStore, TestingRunConfig testingRunConfig) {

        Map<ApiInfoKey, List<ApiInfoKey>> cleanedUpRequests = new HashMap<>();

        for (TestingRunResult trr: testingRunResults) {

            for(GenericTestResult gtr: trr.getTestResults()) {                
                for(String message: gtr.getResponses()) {
                    if (message != null) {
                        String formattedMessage = null;
                        try {
                            formattedMessage = com.akto.runtime.utils.Utils.convertToSampleMessage(message);
                        } catch (Exception e) {
                            loggerMaker.errorAndAddToDb("cleanUpTestArtifacts: Error while formatting message: " + e.getMessage());
                        }
                        if (formattedMessage == null) {
                            continue;
                        }
                        RawApi rawApiToBeReplayed = RawApi.buildFromMessage(formattedMessage, true);
                        if (rawApiToBeReplayed.getResponse().getStatusCode() >= 300) {
                            loggerMaker.infoAndAddToDb("cleanUpTestArtifacts rawApiToBeReplayed status code invalid: " + rawApiToBeReplayed.getResponse().getStatusCode());
                            continue;
                        }
                        switch (apiInfoKey.getMethod()) {
                            case POST:
                            case PUT:
                                // TODO: Handle cases where the delete API does not have the delete method
                                List<DependencyNode> children = dataActor.findDependencyNodes(apiInfoKey.getApiCollectionId(), apiInfoKey.getUrl(), apiInfoKey.getMethod().name(), "DELETE");

                                if (children != null && !children.isEmpty()) {
                                    for(DependencyNode node: children) {
                                        Map<String, Set<Object>> valuesMap = Build.getValuesMap(rawApiToBeReplayed.getResponse());

                                        ApiInfoKey cleanUpApiInfoKey = new ApiInfoKey(Integer.valueOf(node.getApiCollectionIdReq()), node.getUrlReq(), Method.valueOf(node.getMethodReq()));
                                        List<String> samples = sampleMessageStore.getSampleDataMap().get(cleanUpApiInfoKey);
                                        if (samples == null || samples.isEmpty()) {
                                            loggerMaker.infoAndAddToDb(String.format("cleanUpTestArtifacts samples not found for: %s %s %s", node.getApiCollectionIdReq(), node.getUrlReq(), node.getMethodReq()));
                                            continue;
                                        } else {
                                            RawApi nextApi = RawApi.buildFromMessage(samples.get(0), true);

                                            List<KVPair> kvPairs = new ArrayList<>();
                                            boolean fullReplace = true;
                                            for(ParamInfo paramInfo: node.getParamInfos()) {
                                                // TODO: Handle for header
                                                if (paramInfo.isHeader()) continue;
                                                Set<Object> valuesFromResponse = valuesMap.get(paramInfo.getResponseParam());

                                                if (valuesFromResponse == null || valuesFromResponse.isEmpty()) {
                                                    fullReplace = false;
                                                    break;
                                                }

                                                Object valueFromResponse = valuesFromResponse.iterator().next();

                                                KVPair.KVType type = valueFromResponse instanceof Integer ? KVPair.KVType.INTEGER : KVPair.KVType.STRING;
                                                KVPair kvPair = new KVPair(paramInfo.getRequestParam(), valueFromResponse.toString(), false, paramInfo.isUrlParam(), type);
                                                kvPairs.add(kvPair);
                                            }

                                            if (!fullReplace) {
                                                loggerMaker.infoAndAddToDb(String.format("cleanUpTestArtifacts unable to replace all values in dependency node %s %s %s", node.getApiCollectionIdReq(), node.getUrlReq(), node.getMethodReq()));
                                                continue;
                                            }

                                            if (testingRunConfig != null && StringUtils.isNotBlank(testingRunConfig.getTestRoleId())) {
                                                TestRoles role = Executor.fetchOrFindTestRole(testingRunConfig.getTestRoleId(), true);
                                                if (role != null) {
                                                    EndpointLogicalGroup endpointLogicalGroup = role.fetchEndpointLogicalGroup();
                                                    if (endpointLogicalGroup != null && endpointLogicalGroup.getTestingEndpoints() != null  && endpointLogicalGroup.getTestingEndpoints().containsApi(apiInfoKey)) {

                                                        loggerMaker.infoAndAddToDb("attempting to override auth ");
                                                        if (Executor.modifyAuthTokenInRawApi(role, nextApi) == null) {
                                                            loggerMaker.infoAndAddToDb("Default auth mechanism absent");
                                                        }
                                                    } else {
                                                        loggerMaker.infoAndAddToDb("Endpoint didn't satisfy endpoint condition for testRole");
                                                    }
                                                } else {
                                                    String reason = "Test role has been deleted";
                                                    loggerMaker.infoAndAddToDb(reason + ", going ahead with sample auth");
                                                }
                                            }

                                            ReplaceDetail replaceDetail = new ReplaceDetail(apiInfoKey.getApiCollectionId(), apiInfoKey.getUrl(), apiInfoKey.getMethod().name(), kvPairs);
                                            modifyRequest(nextApi.getRequest(), replaceDetail);
                                            loggerMaker.infoAndAddToDb("cleanUpTestArtifacts: ====REQUEST====");
                                            loggerMaker.infoAndAddToDb("cleanUpTestArtifacts: REQUEST: " + nextApi.getRequest().getMethod() + " " + nextApi.getRequest().getUrl() + "?" + nextApi.getRequest().getQueryParams());
                                            loggerMaker.infoAndAddToDb("cleanUpTestArtifacts: REQUEST headers: " + nextApi.getRequest().getHeaders());
                                            loggerMaker.infoAndAddToDb("cleanUpTestArtifacts: REQUEST body: " + nextApi.getRequest().getBody());
                                            loggerMaker.infoAndAddToDb("cleanUpTestArtifacts: ====RESPONSE====");
                                            try {
                                                OriginalHttpResponse nextResponse = ApiExecutor.sendRequest(nextApi.getRequest(), true, testingRunConfig, false, new ArrayList<>());
                                                loggerMaker.infoAndAddToDb("cleanUpTestArtifacts: RESPONSE headers: " + nextApi.getResponse().getHeaders());
                                                loggerMaker.infoAndAddToDb("cleanUpTestArtifacts: RESPONSE body: " + nextResponse.getBody());
                                                loggerMaker.infoAndAddToDb("cleanUpTestArtifacts: RESPONSE status code: " + nextResponse.getStatusCode());

                                                if(nextResponse.getStatusCode() < 300) {
                                                    if(cleanedUpRequests.get(apiInfoKey) != null) {
                                                        cleanedUpRequests.get(apiInfoKey).add(cleanUpApiInfoKey);
                                                    } else {
                                                        cleanedUpRequests.put(apiInfoKey, Arrays.asList(cleanUpApiInfoKey));
                                                    }
                                                }

                                            } catch (Exception e) {
                                                e.printStackTrace();
                                                loggerMaker.errorAndAddToDb(e,
                                                        "exception in sending api request for cleanup"
                                                                + e.getMessage());
                                            }
                                        }
                                    }
                                } else {
                                    loggerMaker.infoAndAddToDb(String.format(
                                            "cleanUpTestArtifacts: dependency nodes not found for %s %s %s %s",
                                            String.valueOf(apiInfoKey.getApiCollectionId()), apiInfoKey.getUrl(),
                                            apiInfoKey.getMethod().name(), "POST"));
                                }

                                break;
                            // TODO: implement for other methods
                            case PATCH:

                                break;
                            case DELETE:

                                break;

                            case GET:    
                            default:
                                break;
                        }
                    }
                }
            }
        }        

        return cleanedUpRequests;
    }

    public Confidence getConfidenceForTests(TestConfig testConfig, YamlTestTemplate template) {
        Confidence someConfidence = null;
        if (testConfig.getDynamicSeverityList() != null) {
            for (SeverityParserResult temp : testConfig.getDynamicSeverityList()) {
                if (temp.getCheck() != null) {
                    FilterNode filterNode = temp.getCheck().getNode();
                    template.setFilterNode(filterNode);
                    boolean res = template.filter();
                    if (res) {
                        try {
                            return Confidence.valueOf(temp.getSeverity());
                        } catch (Exception e) {
                        }
                    }
                } else {
                    /*
                     * Default value has no check condition.
                     */
                    someConfidence = Confidence.valueOf(temp.getSeverity());
                }
            }
        }
        return someConfidence;
    }

    private boolean prefetchAuthWithRetry(TestRoles testRole, RawApi rawApi, int maxAttempts) {
        AuthMechanism authMechanism = testRole.findMatchingAuthMechanism(rawApi);
        
        if (authMechanism == null || !LoginFlowEnums.AuthMechanismTypes.LOGIN_REQUEST.toString().equalsIgnoreCase(authMechanism.getType())) {
            return true; // Not a login request type, no prefetch needed
        }
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                loggerMaker.infoAndAddToDb("Attempt " + attempt + ": Prefetching auth for role " + testRole.getName(), LogDb.TESTING);
                
                LoginFlowResponse response = executeLoginFlow(authMechanism, null);
                
                if (response.getSuccess()) {
                    loggerMaker.infoAndAddToDb("Attempt " + attempt + ": Auth prefetch succeeded", LogDb.TESTING);
                    return true;
                }
                
                loggerMaker.errorAndAddToDb("Attempt " + attempt + ": Auth prefetch failed - " + response.getError(), LogDb.TESTING);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Attempt " + attempt + ": Exception during auth prefetch - " + e.getMessage(), LogDb.TESTING);
            }
            
            // Wait 10s before retry (unless last attempt)
            if (attempt < maxAttempts) {
                try {
                    loggerMaker.infoAndAddToDb("Waiting 10 seconds before retry...", LogDb.TESTING);
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        return false;
    }

    public boolean filterGraphQlPayload(RawApi rawApi, ApiInfo.ApiInfoKey apiInfoKey) throws Exception {

        String url = apiInfoKey.getUrl();
        if (!url.toLowerCase().contains("graphql") || (!url.toLowerCase().contains("query") && !url.toLowerCase().contains("mutation"))) {
            return false;
        }

        String queryName;

        try {
            String []split;
            if(url.contains("query")) {
                split = apiInfoKey.getUrl().split("query/");
            } else{
                split = apiInfoKey.getUrl().split("mutation/");
            }
            if (split.length < 2) {
                return false;
            }
            String queryStr = split[1];

            String []querySplit = queryStr.split("/");
            if (querySplit.length < 2) {
                return false;
            }
            queryName = querySplit[0];
        } catch (Exception e) {
            throw new Exception("Error while getting queryString");
        }

        ObjectMapper m = new ObjectMapper();
        String updatedBody, updatedRespBody;
        try {
            Object obj = JSON.parse(rawApi.getRequest().getBody());
            List<Object> objList = Arrays.asList((Object[])obj);

            Object respObj = JSON.parse(rawApi.getResponse().getBody());
            List<Object> respObjList = Arrays.asList((Object[])respObj);

            if (objList.size() != respObjList.size()) {
                return false;
            }
            int index = 0;

            List<Object> updatedObjList = new ArrayList<>();
            for (int i = 0; i < objList.size(); i++) {
                Map<String,Object> mapValues = m.convertValue(objList.get(i), Map.class);
                if (mapValues.get("operationName").toString().equalsIgnoreCase(queryName)) {
                    updatedObjList.add(objList.get(i));
                    index = i;
                    break;
                }
            }
            updatedBody = gson.toJson(updatedObjList);

            List<Object> updatedRespObjList = new ArrayList<>();
            updatedRespObjList.add(respObjList.get(index));
            updatedRespBody = gson.toJson(updatedRespObjList);

            Map<String, Object> json = gson.fromJson(rawApi.getOriginalMessage(), Map.class);
            json.put("requestPayload", updatedBody);
            json.put("responsePayload", updatedRespBody);
            rawApi.setOriginalMessage(gson.toJson(json));

            rawApi.getRequest().setBody(updatedBody);
            rawApi.getResponse().setBody(updatedRespBody);
            return true;
        } catch (Exception e) {
            throw new Exception("Error while modifying graphQL payload");
        }
    }

}

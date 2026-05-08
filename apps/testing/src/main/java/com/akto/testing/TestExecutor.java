
package com.akto.testing;

import static com.akto.test_editor.execution.Build.modifyRequest;
import static com.akto.testing.Utils.writeJsonContentInFile;

import com.akto.crons.GetRunningTestsStatus;
import com.akto.dao.ActivitiesDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.CustomAuthTypeDao;
import com.akto.dao.DependencyNodeDao;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing.TestingRunResultSummariesDao;
import com.akto.dao.testing.VulnerableTestingRunResultDao;
import com.akto.dao.testing.WorkflowTestResultsDao;
import com.akto.dao.testing.WorkflowTestsDao;
import com.akto.dao.testing.config.TestSuiteDao;
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
import com.akto.dto.test_editor.Auth;
import com.akto.dto.test_editor.ExecutorNode;
import com.akto.dto.test_editor.FilterNode;
import com.akto.dto.test_editor.SeverityParserResult;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.EndpointLogicalGroup;
import com.akto.dto.testing.GenericTestResult;
import com.akto.dto.testing.GraphExecutorRequest;
import com.akto.dto.testing.GraphExecutorResult;
import com.akto.dto.testing.LoginFlowParams;
import com.akto.dto.testing.LoginFlowResponse;
import com.akto.dto.testing.LoginWorkflowGraphEdge;
import com.akto.dto.testing.MultiExecTestResult;
import com.akto.dto.testing.RequestData;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestResult.Confidence;
import com.akto.dto.testing.TestResult.TestError;
import com.akto.dto.testing.TestRoles;
import com.akto.dto.testing.TestingEndpoints;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.TestingRun.State;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.TestingRunResultSummary;
import com.akto.dto.testing.WorkflowNodeDetails;
import com.akto.dto.testing.WorkflowTest;
import com.akto.dto.testing.WorkflowTestingEndpoints;
import com.akto.dto.testing.WorkflowUpdatedSampleData;
import com.akto.dto.testing.YamlTestResult;
import com.akto.dto.testing.info.SingleTestPayload;
import com.akto.dto.type.RequestTemplate;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLMethods.Method;
import com.akto.github.GithubUtils;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.store.SampleMessageStore;
import com.akto.store.TestingUtil;
import com.akto.test_editor.TestingUtilsSingleton;
import com.akto.test_editor.execution.Build;
import com.akto.test_editor.execution.Executor;
import com.akto.test_editor.execution.VariableResolver;
import com.akto.test_editor.filter.data_operands_impl.ValidationResult;
import com.akto.testing.kafka_utils.Producer;
import com.akto.testing.kafka_utils.TestingConfigurations;
import com.akto.testing.yaml_tests.YamlTestTemplate;
import com.akto.testing_issues.TestingIssuesHandler;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.Constants;
import com.akto.util.JSONUtils;
import com.akto.util.enums.GlobalEnums.Severity;
import com.akto.util.enums.LoginFlowEnums;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.mongodb.WriteConcern;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

public class TestExecutor {

    private static final LoggerMaker loggerMaker = new LoggerMaker(TestExecutor.class, LogDb.TESTING);

    public static long acceptableSizeInBytes = 5_000_000;
    private static final Gson gson = new Gson();

    public static final String REQUEST_HOUR = "requestHour";
    public static final String COUNT = "count";
    public static final int ALLOWED_REQUEST_PER_HOUR = 100;
    private static final AtomicInteger totalTestsToBeExecuted = new AtomicInteger(0);

    public void init(TestingRun testingRun, ObjectId summaryId, SyncLimit syncLimit, boolean shouldInitOnly) {
        totalTestsToBeExecuted.set(0);
        if (testingRun.getTestIdConfig() != 1) {
            apiWiseInit(testingRun, summaryId, false, new ArrayList<>(), syncLimit, shouldInitOnly);
        } else {
            workflowInit(testingRun, summaryId, false, new ArrayList<>());
        }
    }

    public void workflowInit (TestingRun testingRun, ObjectId summaryId, boolean debug, List<TestingRunResult.TestLog> testLogs) {
        TestingEndpoints testingEndpoints = testingRun.getTestingEndpoints();
        if (!testingEndpoints.getType().equals(TestingEndpoints.Type.WORKFLOW)) {
            loggerMaker.errorAndAddToDb("Invalid workflow type", LogDb.TESTING);
            return;
        }

        WorkflowTestingEndpoints workflowTestingEndpoints = (WorkflowTestingEndpoints) testingEndpoints;
        WorkflowTest workflowTestOld = workflowTestingEndpoints.getWorkflowTest();

        WorkflowTest workflowTest = WorkflowTestsDao.instance.findOne(
                Filters.eq("_id", workflowTestOld.getId())
        );

        if (workflowTest == null) {
            loggerMaker.errorAndAddToDb("Workflow test has been deleted", LogDb.TESTING);
            return ;
        }

        ApiWorkflowExecutor apiWorkflowExecutor = new ApiWorkflowExecutor();
        try {
            Map<String, Object> valuesMap = new HashMap<>();
            Graph graph = new Graph();
            graph.buildGraph(workflowTest);
            GraphExecutorRequest graphExecutorRequest = new GraphExecutorRequest(graph, workflowTest, testingRun.getId(), summaryId, valuesMap, false, "linear");
            GraphExecutorResult graphExecutorResult = apiWorkflowExecutor.init(graphExecutorRequest, debug, testLogs, null, false);
            WorkflowTestResultsDao.instance.insertOne(graphExecutorResult.getWorkflowTestResult());
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while executing workflow test " + e, LogDb.TESTING);
        }

        Map<String, Integer> totalCountIssues = new HashMap<>();
        totalCountIssues.put("CRITICAL", 0);
        totalCountIssues.put("HIGH", 0);
        totalCountIssues.put("MEDIUM", 0);
        totalCountIssues.put("LOW", 0);

        TestingRunResultSummariesDao.instance.updateOne(
                Filters.eq("_id", summaryId),
                Updates.combine(
                        Updates.set(TestingRunResultSummary.END_TIMESTAMP, Context.now()),
                        Updates.set(TestingRunResultSummary.STATE, State.COMPLETED),
                        Updates.set(TestingRunResultSummary.COUNT_ISSUES, totalCountIssues)
                )
        );
    }

    public void apiWiseInit(TestingRun testingRun, ObjectId summaryId, boolean debug, List<TestingRunResult.TestLog> testLogs, SyncLimit syncLimit, boolean shouldInitOnly) {

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
        
        if (testingRun.getTestingRunConfig() != null) {
            TestingRunResultSummariesDao.instance.updateOneNoUpsert(Filters.eq(Constants.ID, summaryId),
                    Updates.set(TestingRunResultSummary.TESTS_INITIATED_COUNT,
                            testingRun.getTestingRunConfig().getTestSubCategoryList().size()));
        }

        if (apiInfoKeyList == null || apiInfoKeyList.isEmpty()) return;
        loggerMaker.info("APIs found: " + apiInfoKeyList.size(), LogDb.TESTING);
        boolean collectionWise = testingEndpoints.getType().equals(TestingEndpoints.Type.COLLECTION_WISE);
        SampleMessageStore sampleMessageStore = SampleMessageStore.create();

        if(collectionWise || apiInfoKeyList.size() > 500){
            // todo to fix this later. Running test on a group would fetch all samples across apiinfokeys
            sampleMessageStore.fetchSampleMessages(Main.extractApiCollectionIds(apiInfoKeyList));
        }else{
            sampleMessageStore.fetchSampleMessages(apiInfoKeyList);
        }

        TestingRunResultSummariesDao.instance.updateOne(
            Filters.eq("_id", summaryId),
            Updates.set(TestingRunResultSummary.TOTAL_APIS, apiInfoKeyList.size()));

        List<TestRoles> testRoles = sampleMessageStore.fetchTestRoles();
        TestRoles attackerTestRole = Executor.fetchOrFindAttackerRole();

        //Updating the subcategory list if its individual run
        List<String> testingRunSubCategories;
        if (!testingSubCategorySet.isEmpty()) {
            testingRunSubCategories = new ArrayList<>(testingSubCategorySet);
        } else {

            List<String> testSuiteIds = testingRun.getTestingRunConfig().getTestSuiteIds();
            if (testSuiteIds == null || testSuiteIds.isEmpty()) {
                // default testing
                testingRunSubCategories = testingRun.getTestingRunConfig().getTestSubCategoryList();
            } else {
                List<ObjectId> testSuiteObjectIds = new ArrayList<>();
                for (String testSuiteId: testSuiteIds) {
                    ObjectId testSuiteObjectId = new ObjectId(testSuiteId);
                    testSuiteObjectIds.add(testSuiteObjectId);
                }

                testingRunSubCategories = TestSuiteDao.getAllTestSuitesSubCategories(testSuiteObjectIds);
            }
        }

        
        int totalTestsToBeExecutedCount = testingRunSubCategories.size() * apiInfoKeyList.size();
        totalTestsToBeExecuted.set(totalTestsToBeExecutedCount);

        Map<String, TestConfig> testConfigMap = YamlTemplateDao.instance.fetchTestConfigMap(false, true, 0, 10_000, Filters.in("_id", testingRunSubCategories));

        List<CustomAuthType> customAuthTypes = CustomAuthTypeDao.instance.findAll(CustomAuthType.ACTIVE,true);
        TestingUtil testingUtil = new TestingUtil(sampleMessageStore, testRoles, testingRun.getUserEmail(), customAuthTypes);

        loggerMaker.debug("For account: " + accountId + " fetched test yamls and auth types");


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
            loggerMaker.info("Starting findAllHosts at: " + currentTime, LogDb.TESTING);
            hostAndContentType = StatusCodeAnalyser.findAllHosts(sampleMessageStore, sampleDataMapForStatusCodeAnalyser);
            loggerMaker.info("Completing findAllHosts in: " + (Context.now() -  currentTime) + " at: " + Context.now(), LogDb.TESTING);
        } catch (Exception e){
            loggerMaker.errorAndAddToDb("Error while running findAllHosts " + e.getMessage(), LogDb.TESTING);
        }
        try {
            currentTime = Context.now();
            loggerMaker.debugAndAddToDb("Starting HostValidator at: " + currentTime, LogDb.TESTING);
            HostValidator.compute(hostAndContentType,testingRun.getTestingRunConfig());
            loggerMaker.debugAndAddToDb("Completing HostValidator in: " + (Context.now() -  currentTime) + " at: " + Context.now(), LogDb.TESTING);
        } catch (Exception e){
            loggerMaker.errorAndAddToDb("Error while running HostValidator " + e.getMessage(), LogDb.TESTING);
        }
        try {
            currentTime = Context.now();
            loggerMaker.infoAndAddToDb("Starting StatusCodeAnalyser at: " + currentTime);
            StatusCodeAnalyser.run(sampleDataMapForStatusCodeAnalyser, sampleMessageStore , attackerTestRole.findMatchingAuthMechanism(null), testingRun.getTestingRunConfig(), hostAndContentType);
            loggerMaker.infoAndAddToDb("Completing StatusCodeAnalyser in: " + (Context.now() -  currentTime) + " at: " + Context.now());
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while running status code analyser " + e.getMessage(), LogDb.TESTING);
        }
        loggerMaker.debugAndAddToDb("StatusCodeAnalyser result = " + StatusCodeAnalyser.result +  " defaultPayloadsMap = " + StatusCodeAnalyser.defaultPayloadsMap, LogDb.TESTING);

        ConcurrentHashMap<String, String> subCategoryEndpointMap = new ConcurrentHashMap<>();
        Map<ApiInfoKey, String> apiInfoKeyToHostMap = new HashMap<>();

        TestingConfigurations.getInstance().init(testingUtil, testingRun.getTestingRunConfig(), debug, testConfigMap, testingRun.getMaxConcurrentRequests());
        TestingUtilsSingleton.init();

        if(!shouldInitOnly){
            int maxThreads = Math.min(testingRunSubCategories.size(), 500);
            AtomicInteger totalRecordsInsertedInKafka = new AtomicInteger(0);
            AtomicInteger skippedRecordsForKafka = new AtomicInteger(0);
            AtomicInteger throttleNumber = new AtomicInteger(0);
            if(maxThreads == 0){
                loggerMaker.debugAndAddToDb("Subcategories list are empty");
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
            }

            final int maxRunTime = tempRunTime;
            if(Constants.IS_NEW_TESTING_ENABLED){
                try {
                    Producer.createTopicWithRetries(Constants.LOCAL_KAFKA_BROKER_URL, Constants.TEST_RESULTS_TOPIC_NAME);
                } catch (Exception e) {
                    e.printStackTrace();
                    loggerMaker.error("Error in creating topic", e.getMessage());
                }
            }

            for (ApiInfo.ApiInfoKey apiInfoKey: apiInfoKeyList) {

                List<String> messages = testingUtil.getSampleMessages().get(apiInfoKey);
                AtomicInteger temp = new AtomicInteger(totalTestsToBeExecuted.get() - testingRunSubCategories.size());
                if (messages == null || messages.isEmpty()) {
                    totalTestsToBeExecuted.set(temp.get());
                    loggerMaker.debugAndAddToDb("No sample messages found for apiInfoKey: " + apiInfoKey.toString(), LogDb.TESTING);
                    continue;
                }
                String sample = messages.get(messages.size() - 1);
                if(sample == null || sample.isEmpty()){
                    totalTestsToBeExecuted.set(temp.get());
                    loggerMaker.debugAndAddToDb("Sample message is empty for apiInfoKey: " + apiInfoKey.toString(), LogDb.TESTING);
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

                if(TestingConfigurations.getInstance().getRawApi(apiInfoKey) == null){
                    try {
                        RawApi rawApi = RawApi.buildFromMessage(sample, true);
                        TestingConfigurations.getInstance().insertRawApi(apiInfoKey, rawApi);
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb("Error while building RawAPI for " + apiInfoKey + " : " + e, LogDb.TESTING);
                    }
                }   

                if(Constants.IS_NEW_TESTING_ENABLED){
                    for (String testSubCategory: testingRunSubCategories) {
                        if (apiInfoKeySubcategoryMap == null || apiInfoKeySubcategoryMap.get(apiInfoKey).contains(testSubCategory)) {
                            insertRecordInKafka(accountId, testSubCategory,
                                    apiInfoKey, messages, summaryId, syncLimit, apiInfoKeyToHostMap, subCategoryEndpointMap,
                                    testConfigMap, testLogs, testingRun, new AtomicBoolean(false),
                                    totalRecordsInsertedInKafka, skippedRecordsForKafka, throttleNumber);
                        }
                    }
                }
                else{
                    Future<Void> future = threadPool.submit(() -> startWithLatch(testingRunSubCategories, accountId,
                            apiInfoKey, messages, summaryId, syncLimit, apiInfoKeyToHostMap, subCategoryEndpointMap,
                            testConfigMap, testLogs, testingRun, latch, finalApiInfoKeySubcategoryMap));
                    testingRecords.add(future);
                }
            }
    
    
            try {
                if(!Constants.IS_NEW_TESTING_ENABLED){
                    int waitTs = Context.now();
                    int prevCalcTime = Context.now();
                    int lastCheckedCount = 0;
                    while(latch.getCount() > 0 && GetRunningTestsStatus.getRunningTests().isTestRunning(summaryId) 
                        && (Context.now() - waitTs < maxRunTime)) {
                            loggerMaker.infoAndAddToDb("waiting for tests to finish with count left: " + totalTestsToBeExecuted.get());

                            if(lastCheckedCount != totalTestsToBeExecuted.get()){
                                lastCheckedCount = totalTestsToBeExecuted.get();
                                prevCalcTime = Context.now();
                            }else{
                                int relaxingTime = Utils.getRelaxingTimeForTests(totalTestsToBeExecuted, totalTestsToBeExecutedCount);
                                if(relaxingTime == 0){
                                    loggerMaker.info("Successfully completed all tests.");
                                    break;
                                }
                                if(Context.now() - prevCalcTime > relaxingTime){
                                    Main.sendSlackAlertForFailedTest(accountId, "Relaxing time reached in case of old testing => " + relaxingTime + " minutes, stopping tests with count left: " + totalTestsToBeExecuted.get());
                                    loggerMaker.infoAndAddToDb("Relaxing time reached => " + relaxingTime + " minutes, stopping tests with count left: " + totalTestsToBeExecuted.get());
                                    break;
                                }                               
                            }

                            Thread.sleep(2000);
                    }
                }else{
                    Thread.sleep(20000); // wait for 20 seconds to ensure all messages are processed
                    dbObject.put("PRODUCER_RUNNING", false);
                    dbObject.put("CONSUMER_RUNNING", true);
                    writeJsonContentInFile(Constants.TESTING_STATE_FOLDER_PATH, Constants.TESTING_STATE_FILE_NAME, dbObject);
                    loggerMaker.infoAndAddToDb("Finished inserting records in kafka: " + totalRecordsInsertedInKafka.get() + " skipping records: " + skippedRecordsForKafka.get(), LogDb.TESTING);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        
    }

    private Void startWithLatch(List<String> testingRunSubCategories,int accountId,ApiInfo.ApiInfoKey apiInfoKey,
        List<String> messages, ObjectId summaryId, SyncLimit syncLimit, Map<ApiInfoKey, String> apiInfoKeyToHostMap,
        ConcurrentHashMap<String, String> subCategoryEndpointMap, Map<String, TestConfig> testConfigMap,
        List<TestingRunResult.TestLog> testLogs, TestingRun testingRun, CountDownLatch latch, Map<ApiInfoKey, List<String>> apiInfoKeySubcategoryMap){

            Context.accountId.set(accountId);
            AtomicBoolean isApiInfoTested = new AtomicBoolean(false);
            for (String testSubCategory: testingRunSubCategories) {
                if (apiInfoKeySubcategoryMap == null || apiInfoKeySubcategoryMap.get(apiInfoKey).contains(testSubCategory)) {
                    loggerMaker.debugAndAddToDb("Trying to run test for category: " + testSubCategory + " with summary state: " + GetRunningTestsStatus.getRunningTests().getCurrentState(summaryId) );
                    if(GetRunningTestsStatus.getRunningTests().isTestRunning(summaryId, true)){
                        insertRecordInKafka(accountId, testSubCategory, apiInfoKey, messages, summaryId, syncLimit,
                                apiInfoKeyToHostMap, subCategoryEndpointMap, testConfigMap, testLogs, testingRun,
                                isApiInfoTested, new AtomicInteger(), new AtomicInteger(), new AtomicInteger());
                    }else{
                        loggerMaker.info("Test stopped for id: " + testingRun.getHexId());
                        break;
                    }
                }
            }
            if(isApiInfoTested.get()){
                loggerMaker.debug("Api: " + apiInfoKey.toString() + " has been successfully tested");
                ApiInfoDao.instance.updateLastTestedField(apiInfoKey);
            }
            latch.countDown();

        return null;
    }

    private Void insertRecordInKafka(int accountId, String testSubCategory, ApiInfo.ApiInfoKey apiInfoKey,
            List<String> messages, ObjectId summaryId, SyncLimit syncLimit, Map<ApiInfoKey, String> apiInfoKeyToHostMap,
            ConcurrentHashMap<String, String> subCategoryEndpointMap, Map<String, TestConfig> testConfigMap,
            List<TestingRunResult.TestLog> testLogs, TestingRun testingRun, AtomicBoolean isApiInfoTested, 
            AtomicInteger totalRecords,  AtomicInteger skippedRecords, AtomicInteger throttleNumber) {
        Context.accountId.set(accountId);
        TestConfig testConfig = testConfigMap.get(testSubCategory);
                    
        if (testConfig == null) {
            skippedRecords.incrementAndGet();
            if(Constants.KAFKA_DEBUG_MODE){
                loggerMaker.debugAndAddToDb("Found testing config null: " + apiInfoKey.toString() + " : " + testSubCategory);
            }
            return null;
        }

        if (!applyRunOnceCheck(apiInfoKey, testConfig, subCategoryEndpointMap, apiInfoKeyToHostMap, testSubCategory)) {
            skippedRecords.incrementAndGet();
            return null;
        }

        String failMessage = null;
        if (!demoCollections.contains(apiInfoKey.getApiCollectionId()) &&
                syncLimit.updateUsageLeftAndCheckSkip()) {
            failMessage = TestError.USAGE_EXCEEDED.getMessage();
        }

        String testSuperType = testConfig.getInfo().getCategory().getName();
        String testSubType = testConfig.getInfo().getSubCategory();

        TestingRunResult testingRunResult = Utils.generateFailedRunResultForMessage(testingRun.getId(), apiInfoKey, testSuperType, testSubType, summaryId, messages, failMessage); 
        if(testingRunResult != null){
            skippedRecords.incrementAndGet();
            if(Constants.KAFKA_DEBUG_MODE){
                loggerMaker.debugAndAddToDb("Skipping test from producers because: " + failMessage + " apiinfo: " + apiInfoKey.toString(), LogDb.TESTING);
            }
        }else if (Constants.IS_NEW_TESTING_ENABLED){
            // push data to kafka here and inside that call run test new function
            // create an object of TestMessage
            SingleTestPayload singleTestPayload = new SingleTestPayload(
                testingRun.getId(), summaryId, apiInfoKey, testSubType, testLogs, accountId
            );
            if(Constants.KAFKA_DEBUG_MODE){
                loggerMaker.debug("Inserting record for apiInfoKey: " + apiInfoKey.toString() + " subcategory: " + testSubType);
            }
            
            try {
                Producer.pushMessagesToKafka(Arrays.asList(singleTestPayload), totalRecords,throttleNumber);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
            
        }else{
            if(GetRunningTestsStatus.getRunningTests().isTestRunning(summaryId, true)){
                TestingConfigurations instance = TestingConfigurations.getInstance();
                String sampleMessage = messages.get(messages.size() - 1);
                testingRunResult = runTestNew(apiInfoKey, summaryId, instance.getTestingUtil(), summaryId, testConfig, instance.getTestingRunConfig(), instance.isDebug(), testLogs, sampleMessage);
                if (testingRunResult != null) {
                    List<String> errorList = testingRunResult.getErrorsList();
                    if (errorList == null || !errorList.contains(TestResult.API_CALL_FAILED_ERROR_STRING)) {
                        isApiInfoTested.set(true);
                    }
                }
                insertResultsAndMakeIssues(Collections.singletonList(testingRunResult), summaryId);
            }
            
        }
        return null;
    }
    
    public static void updateTestSummary(ObjectId summaryId){
        loggerMaker.debugAndAddToDb("Finished updating results count", LogDb.TESTING);

        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions();
        options.returnDocument(ReturnDocument.AFTER);

        State updatedState = GetRunningTestsStatus.getRunningTests().isTestRunning(summaryId, true) ? State.COMPLETED : GetRunningTestsStatus.getRunningTests().getCurrentState(summaryId);
        Map<String,Integer> finalCountMap = Utils.finalCountIssuesMap(summaryId);
        loggerMaker.debugAndAddToDb("Final count map calculated is " + finalCountMap.toString());
        TestingRunResultSummary testingRunResultSummary = TestingRunResultSummariesDao.instance.getMCollection().withWriteConcern(WriteConcern.W1).findOneAndUpdate(
                Filters.eq(Constants.ID, summaryId),
                Updates.combine(
                        Updates.set(TestingRunResultSummary.END_TIMESTAMP, Context.now()),
                        Updates.set(TestingRunResultSummary.COUNT_ISSUES, finalCountMap),
                        Updates.set(TestingRunResultSummary.STATE, updatedState)),
                options);

        if (TestingConfigurations.getInstance().getRerunTestingRunResultSummary() != null) {
            TestingRunResultSummariesDao.instance.deleteAll(Filters.eq(TestingRunResultSummary.ID,
                    TestingConfigurations.getInstance().getRerunTestingRunResultSummary().getId()));
            loggerMaker.debugAndAddToDb("Deleting rerun testing result summary after completion of test: TRRS_ID:" + TestingConfigurations.getInstance().getRerunTestingRunResultSummary().getHexId());
            TestingConfigurations.getInstance().setRerunTestingRunResultSummary(null);
        }

        GithubUtils.publishGithubComments(testingRunResultSummary);

        Map<String , Integer> totalCountIssues = testingRunResultSummary.getCountIssues();

        loggerMaker.debugAndAddToDb("Finished updating TestingRunResultSummariesDao", LogDb.TESTING);
        if(totalCountIssues != null && totalCountIssues.getOrDefault(Severity.HIGH.toString(),0) > 0){
            ActivitiesDao.instance.insertActivity("High Vulnerability detected", totalCountIssues.get(Severity.HIGH.toString()) + " HIGH vulnerabilites detected");
        }
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
        List<String> sampleMessages = sampleMessagesMap.get(apiInfoKey);
        if (sampleMessages == null || sampleMessagesMap.isEmpty()) return null;

        loggerMaker.debugAndAddToDb("Starting to find host for apiInfoKey: " + apiInfoKey.toString());

        List<RawApi> messages = sampleMessageStore.fetchAllOriginalMessages(apiInfoKey);
        if (messages.isEmpty()) return null;

        // getting last as we run test on the latest sample
        return messages.get(messages.size() - 1).getRequest();
    }

    public static String findHostFromOriginalHttpRequest(OriginalHttpRequest originalHttpRequest)
            throws URISyntaxException {
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

    public static LoginFlowResponse executeLoginFlow(AuthMechanism authMechanism, LoginFlowParams loginFlowParams, String roleName) throws Exception {

        if (authMechanism.getType() == null) {
            loggerMaker.debugAndAddToDb("auth type value is null", LogDb.TESTING);
            return new LoginFlowResponse(null, null, true);
        }

        if (!authMechanism.getType().equals(LoginFlowEnums.AuthMechanismTypes.LOGIN_REQUEST.toString())) {
            loggerMaker.debugAndAddToDb("invalid auth type for login flow execution", LogDb.TESTING);
            return new LoginFlowResponse(null, null, true);
        }

        loggerMaker.debugAndAddToDb("login flow execution started", LogDb.TESTING);

        WorkflowTest workflowObj = convertToWorkflowGraph(authMechanism.getRequestData());
        LoginFlowResponse loginFlowResp;
        loginFlowResp =  com.akto.testing.workflow_node_executor.Utils.runLoginFlow(workflowObj, authMechanism, loginFlowParams, roleName);
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
                waitTime = 20;
                data.setOtpRefUuid(data.getUrl().substring(data.getUrl().lastIndexOf('/') + 1));
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
        try {
            int resultSize = testingRunResults.size();
            if (resultSize > 0) {
                loggerMaker.debugAndAddToDb("testingRunResults size: " + resultSize, LogDb.TESTING);
                trim(testingRunResults);
                TestingRunResult originalTestingRunResultForRerun = TestingConfigurations.getInstance().getTestingRunResultForApiKeyInfo(testingRunResults.get(0).getApiInfoKey(), testingRunResults.get(0).getTestSubType());
                if (originalTestingRunResultForRerun != null) {
                    loggerMaker.debugAndAddToDb("Deleting original testingRunResults for rerun after replaced with run TRR_ID: " + originalTestingRunResultForRerun.getHexId());
                    TestingRunResultDao.instance.deleteAll(Filters.eq(TestingRunResultDao.ID, originalTestingRunResultForRerun.getId()));
                    /*
                    * delete from vulnerableTestResults as well.
                    * assuming if original was vulnerable, entry will be in VulnerableTestingRunResultDao
                    * for API_INFO_KEY, TEST_RUN_RESULT_SUMMARY_ID, TEST_SUB_TYPE, VulnerableTestingRunResultDao will have
                    * single entry
                    * */
                    if (originalTestingRunResultForRerun.isVulnerable()) {
                        Bson filters = Filters.and(
                                Filters.eq(TestingRunResult.API_INFO_KEY, originalTestingRunResultForRerun.getApiInfoKey()),
                                Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, originalTestingRunResultForRerun.getTestRunResultSummaryId()),
                                Filters.eq(TestingRunResult.TEST_SUB_TYPE, originalTestingRunResultForRerun.getTestSubType())
                        );
                        loggerMaker.debugAndAddToDb("Deleting from vulnerableTestingRunResults if present for rerun after replaced with run TRR_ID: " + originalTestingRunResultForRerun.getHexId());
                        VulnerableTestingRunResultDao.instance.deleteAll(filters);
                    }
                }
                TestingRunResultDao.instance.insertMany(testingRunResults);
                loggerMaker.debugAndAddToDb("Inserted testing results", LogDb.TESTING);

                // insert vulnerable testing run results here
                List<TestingRunResult> vulTestResults = new ArrayList<>();
                for(TestingRunResult runResult: testingRunResults){
                    if(runResult != null && runResult.isVulnerable()){
                        vulTestResults.add(runResult);
                    }
                }

                if(!vulTestResults.isEmpty()){
                    loggerMaker.debugAndAddToDb("Inserted vul testing results.", LogDb.TESTING);
                    VulnerableTestingRunResultDao.instance.insertMany(vulTestResults);
                }

                TestingRunResultSummariesDao.instance.getMCollection().withWriteConcern(WriteConcern.W1).findOneAndUpdate(
                    Filters.eq(Constants.ID, testRunResultSummaryId),
                    Updates.inc(TestingRunResultSummary.TEST_RESULTS_COUNT, resultSize)
                );

                loggerMaker.debugAndAddToDb("Updated count in summary", LogDb.TESTING);

                TestingIssuesHandler handler = new TestingIssuesHandler();
                boolean triggeredByTestEditor = false;
                handler.handleIssuesCreationFromTestingRunResults(testingRunResults, triggeredByTestEditor);
            }
        } catch (Exception e) {
            e.printStackTrace();
            loggerMaker.errorAndAddToDb("Error while inserting testing run results: " + e.getMessage(), LogDb.TESTING);
        }
    }

    Set<Integer> deactivatedCollections = UsageMetricCalculator.getDeactivated();
    Set<Integer> demoCollections = UsageMetricCalculator.getDemos();

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
                            loggerMaker.errorAndAddToDb("Error while formatting message: " + e.getMessage(), LogDb.TESTING);
                        }
                        if (formattedMessage == null) {
                            continue;
                        }
                        RawApi rawApiToBeReplayed = RawApi.buildFromMessage(formattedMessage);
                        if (rawApiToBeReplayed.getResponse().getStatusCode() >= 300) {
                            continue;
                        }
                        switch (apiInfoKey.getMethod()) {
                            case POST:
                                Bson filterQ = DependencyNodeDao.generateChildrenFilter(apiInfoKey.getApiCollectionId(), apiInfoKey.getUrl(), apiInfoKey.getMethod());
                                // TODO: Handle cases where the delete API does not have the delete method
                                Bson delFilterQ = Filters.and(filterQ, Filters.eq(DependencyNode.METHOD_REQ, Method.DELETE.name()));
                                List<DependencyNode> children = DependencyNodeDao.instance.findAll(delFilterQ);
                                
                                if (!children.isEmpty()) {
                                    for(DependencyNode node: children) {
                                        Map<String, Set<Object>> valuesMap = Build.getValuesMap(rawApiToBeReplayed.getResponse());

                                        ApiInfoKey cleanUpApiInfoKey = new ApiInfoKey(Integer.valueOf(node.getApiCollectionIdReq()), node.getUrlReq(), Method.valueOf(node.getMethodReq()));
                                        List<String> samples = sampleMessageStore.getSampleDataMap().get(cleanUpApiInfoKey);
                                        if (samples == null || samples.isEmpty()) {
                                            continue;
                                        } else {
                                            RawApi nextApi = RawApi.buildFromMessage(samples.get(0));

                                            List<KVPair> kvPairs = new ArrayList<>();
                                            boolean fullReplace = true;
                                            for(ParamInfo paramInfo: node.getParamInfos()) {
                                                if (paramInfo.isHeader()) continue;
                                                Set<Object> valuesFromResponse = valuesMap.get(paramInfo.getResponseParam());

                                                if (valuesFromResponse == null || valuesFromResponse.isEmpty()) {
                                                    fullReplace = false;
                                                    break;
                                                }
                                                Object valueFromResponse = valuesFromResponse.iterator().next();

                                                KVPair.KVType type = valueFromResponse instanceof Integer ? KVPair.KVType.INTEGER : KVPair.KVType.STRING;
                                                KVPair kvPair = new KVPair(paramInfo.getRequestParam(), valueFromResponse.toString(), false, false, type);
                                                kvPairs.add(kvPair);
                                            }

                                            if (!fullReplace) {
                                                continue;
                                            }

                                            if (testingRunConfig != null && StringUtils.isNotBlank(testingRunConfig.getTestRoleId())) {
                                                TestRoles role = Executor.fetchOrFindTestRole(testingRunConfig.getTestRoleId(), true);
                                                if (role != null) {
                                                    EndpointLogicalGroup endpointLogicalGroup = role.fetchEndpointLogicalGroup();
                                                    if (endpointLogicalGroup != null && endpointLogicalGroup.getTestingEndpoints() != null  && endpointLogicalGroup.getTestingEndpoints().containsApi(apiInfoKey)) {
                                                        loggerMaker.debugAndAddToDb("attempting to override auth ", LogDb.TESTING);
                                                        if (Executor.modifyAuthTokenInRawApi(role, nextApi) == null) {
                                                            loggerMaker.debugAndAddToDb("Default auth mechanism absent", LogDb.TESTING);
                                                        }
                                                    } else {
                                                        loggerMaker.debugAndAddToDb("Endpoint didn't satisfy endpoint condition for testRole", LogDb.TESTING);
                                                    }
                                                } else {
                                                    String reason = "Test role has been deleted";
                                                    loggerMaker.debugAndAddToDb(reason + ", going ahead with sample auth", LogDb.TESTING);
                                                }
                                            }

                                            ReplaceDetail replaceDetail = new ReplaceDetail(apiInfoKey.getApiCollectionId(), apiInfoKey.getUrl(), apiInfoKey.getMethod().name(), kvPairs);
                                            modifyRequest(nextApi.getRequest(), replaceDetail);
                                            loggerMaker.debugAndAddToDb("cleanUpTestArtifacts: ====REQUEST====");
                                            loggerMaker.debugAndAddToDb("cleanUpTestArtifacts: REQUEST: " + nextApi.getRequest().getMethod() + " " + nextApi.getRequest().getUrl() + "?" + nextApi.getRequest().getQueryParams());
                                            loggerMaker.debugAndAddToDb("cleanUpTestArtifacts: REQUEST headers: " + nextApi.getRequest().getHeaders());
                                            loggerMaker.debugAndAddToDb("cleanUpTestArtifacts: REQUEST body: " + nextApi.getRequest().getBody());
                                            loggerMaker.debugAndAddToDb("cleanUpTestArtifacts: ====RESPONSE====");
                                            try {
                                                OriginalHttpResponse nextResponse = ApiExecutor.sendRequest(nextApi.getRequest(), true, testingRunConfig, false, new ArrayList<>());
                                                loggerMaker.debugAndAddToDb("cleanUpTestArtifacts: RESPONSE headers: " + nextApi.getResponse().getHeaders());
                                                loggerMaker.debugAndAddToDb("cleanUpTestArtifacts: RESPONSE body: " + nextResponse.getBody());
                                                loggerMaker.debugAndAddToDb("cleanUpTestArtifacts: RESPONSE status code: " + nextResponse.getStatusCode());

                                                if(nextResponse.getStatusCode() < 300) {
                                                    if(cleanedUpRequests.get(apiInfoKey) != null) {
                                                        cleanedUpRequests.get(apiInfoKey).add(cleanUpApiInfoKey);
                                                    } else {
                                                        cleanedUpRequests.put(apiInfoKey, Arrays.asList(cleanUpApiInfoKey));
                                                    }
                                                }

                                            } catch (Exception e) {
                                                e.printStackTrace();
                                                System.out.println("exception in sending api request for cleanup" + e.getMessage());
                                            }
                                        }
                                    }
                                }

                                break;
                            // TODO: implement for other methods
                            case PUT:
                            
                                break;
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

    public TestingRunResult runTestNew(ApiInfo.ApiInfoKey apiInfoKey, ObjectId testRunId, TestingUtil testingUtil,
        ObjectId testRunResultSummaryId, TestConfig testConfig, TestingRunConfig testingRunConfig, boolean debug, List<TestingRunResult.TestLog> testLogs, String message) {
            RawApi rawApi = TestingConfigurations.getInstance().getRawApi(apiInfoKey);
            if(rawApi == null){
                rawApi = RawApi.buildFromMessage(message, true);
                TestingConfigurations.getInstance().insertRawApi(apiInfoKey, rawApi);
            }
            TestRoles attackerTestRole = Executor.fetchOrFindAttackerRole();
            AuthMechanism attackerAuthMechanism = null;
            if (attackerTestRole == null) {
                loggerMaker.debugAndAddToDb("ATTACKER_TOKEN_ALL test role not found", LogDb.TESTING);
            } else {
                attackerAuthMechanism = attackerTestRole.findMatchingAuthMechanism(rawApi);
            }
            long startTime = System.currentTimeMillis();
            TestingRunResult tr =  runTestNew(apiInfoKey, testRunId, testingUtil.getSampleMessageStore(), attackerAuthMechanism, testingUtil.getCustomAuthTypes(), testRunResultSummaryId, testConfig, testingRunConfig, debug, testLogs, rawApi);
            String testSubType = testConfig.getInfo().getSubCategory();
            loggerMaker.infoAndAddToDb("Test run completed for apiInfoKey: " + apiInfoKey + " testSubType: " + testSubType + " with result: " + tr.isVulnerable() + " in " + (System.currentTimeMillis() - startTime) + "ms");
            totalTestsToBeExecuted.decrementAndGet();
            return tr;
    }

    public TestingRunResult runTestNew(ApiInfo.ApiInfoKey apiInfoKey, ObjectId testRunId, SampleMessageStore sampleMessageStore, AuthMechanism attackerAuthMechanism, List<CustomAuthType> customAuthTypes,
                                       ObjectId testRunResultSummaryId, TestConfig testConfig, TestingRunConfig testingRunConfig, boolean debug, List<TestingRunResult.TestLog> testLogs, RawApi rawApi) {


        String testSuperType = testConfig.getInfo().getCategory().getName();
        String testSubType = testConfig.getInfo().getSubCategory();

        int startTime = Context.now();

        try {
            if (filterGraphQlPayload(rawApi, apiInfoKey)) 
                testLogs.add(new TestingRunResult.TestLog(TestingRunResult.TestLogType.INFO, "GraphQL payload found"));
            else if(filterJsonRpcPayload(rawApi, apiInfoKey)) 
                testLogs.add(new TestingRunResult.TestLog(TestingRunResult.TestLogType.INFO, "JSON-RPC payload found"));
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Exception in filterGraphQlPayload or filterJsonrpcPayload: " + e.getMessage());
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
        
        loggerMaker.debugAndAddToDb("triggering test run for apiInfoKey " + apiInfoKey + "test " +
            testSubType + "logId" + testExecutionLogId, LogDb.TESTING);

        // TestingUtil -> authMechanism
        // TestingConfig -> auth
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
                loggerMaker.errorAndAddToDb("Error while cleaning up test artifacts: " + e.getMessage(), LogDb.TESTING);
            }
        }

        return ret;
    }

    public Confidence getConfidenceForTests(TestConfig testConfig, YamlTestTemplate template) {
        Confidence someConfidence = null;
        if (testConfig.getDynamicSeverityList() != null) {
            for (SeverityParserResult temp : testConfig.getDynamicSeverityList()) {
                if (temp.getCheck() != null) {
                    FilterNode filterNode = temp.getCheck().getNode();
                    template.setFilterNode(filterNode);
                    ValidationResult res = template.filter();
                    if (res.getIsValid()) {
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

    public boolean filterGraphQlPayload(RawApi rawApi, ApiInfo.ApiInfoKey apiInfoKey) throws Exception {

        String url = apiInfoKey.getUrl();
        if (!url.toLowerCase().contains("graphql") || (!url.toLowerCase().contains("query") && !url.toLowerCase().contains("mutation"))) {
            return false;
        }

        // Remove extensions from payload for specific account
        Integer currentAccountId = Context.accountId.get();
        if (currentAccountId != null && currentAccountId == 1758787662) {
            removeExtensionsFromGraphQL(rawApi);
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

            Object reqObj = null;
            for (int i = 0; i < objList.size(); i++) {
                Map<String,Object> mapValues = m.convertValue(objList.get(i), Map.class);
                if (mapValues.get("operationName").toString().equalsIgnoreCase(queryName)) {
                    reqObj = objList.get(i);
                    index = i;
                    break;
                }
            }
            updatedBody = gson.toJson(reqObj);

            Object respObject = respObjList.get(index);
            updatedRespBody = gson.toJson(respObject);

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

    private void removeExtensionsFromGraphQL(RawApi rawApi) {
        try {
            // Remove extensions from request body
            String body = rawApi.getRequest().getBody();
            if (body != null && !body.trim().isEmpty()) {
                try {
                    Object bodyObj = JSON.parse(body);
                    if (bodyObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> bodyMap = (Map<String, Object>) bodyObj;
                        if (bodyMap.containsKey("extensions")) {
                            bodyMap.remove("extensions");
                            rawApi.getRequest().setBody(gson.toJson(bodyMap));
                        }
                    } else if (bodyObj instanceof Object[]) {
                        // Handle array of GraphQL requests
                        Object[] bodyArray = (Object[]) bodyObj;
                        boolean modified = false;
                        for (Object item : bodyArray) {
                            if (item instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> itemMap = (Map<String, Object>) item;
                                if (itemMap.containsKey("extensions")) {
                                    itemMap.remove("extensions");
                                    modified = true;
                                }
                            }
                        }
                        if (modified) {
                            rawApi.getRequest().setBody(gson.toJson(bodyArray));
                        }
                    }
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Error removing extensions from GraphQL body: " + e.getMessage(), LogDb.TESTING);
                }
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in removeExtensionsFromGraphQL: " + e.getMessage(), LogDb.TESTING);
        }
    }

    public boolean filterJsonRpcPayload(RawApi rawApi, ApiInfo.ApiInfoKey apiInfoKey) throws Exception {
        String url = apiInfoKey.getUrl();
    
        ObjectMapper mapper = new ObjectMapper();
        //String updatedBody = null, updatedRespBody = null;
    
        try {

            Map<String, List<String>> headers = rawApi.getRequest().getHeaders();
            if (headers == null || headers.isEmpty()) {
                return false;
            }
            final String CONTENT_TYPE = "content-type";
            if (!headers.containsKey(CONTENT_TYPE)) {
                return false;
            }
            List<String> contentTypeValues = headers.get(CONTENT_TYPE);
            if (contentTypeValues == null || contentTypeValues.isEmpty()) {
                return false;
            }
            String contentType = contentTypeValues.get(0).toLowerCase();
            if (!contentType.contains("application/json") && !contentType.contains("application/json-rpc")) {
                return false;
            }

            // Parse request body
            Map<String, Object> requestMap = new HashMap<>();
            try {
                Object requestBodyObj = JSON.parse(rawApi.getRequest().getBody());
                requestMap = mapper.convertValue(requestBodyObj, Map.class);
            } catch (Exception e) {
                throw new Exception("Invalid JSON in request body: " + e.getMessage());
                // TODO: handle exception
            }
            
    
            // Detect JSON-RPC 2.0
            String jsonrpcVersion = String.valueOf(requestMap.get("jsonrpc"));
            if (!"2.0".equals(jsonrpcVersion)) {
                return false;
            }
    
            String methodName = (String) requestMap.get("method");
            if (methodName == null || methodName.isEmpty()) {
                return false;
            }
    
            if (url != null) {
                int methodIndex = url.indexOf("/" + methodName);
                if (methodIndex != -1) {
                    // Remove everything from method name onwards
                    String trimmedUrl = url.substring(0, methodIndex);
                    rawApi.getRequest().setUrl(trimmedUrl);
                }
            }
            return true;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while filtering JSON-RPC payload: " + e.getMessage());   
            return false; 
        }
    }
    
}

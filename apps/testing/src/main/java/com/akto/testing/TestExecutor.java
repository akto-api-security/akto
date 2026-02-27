
package com.akto.testing;

import com.akto.crons.GetRunningTestsStatus;
import com.akto.dao.ActivitiesDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.CustomAuthTypeDao;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing.TestingRunResultSummariesDao;
import com.akto.dao.testing.WorkflowTestResultsDao;
import com.akto.dao.testing.WorkflowTestsDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.billing.SyncLimit;
import com.akto.dto.CustomAuthType;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.RawApi;
import com.akto.dto.api_workflow.Graph;
import com.akto.dto.test_editor.*;
import com.akto.dto.testing.*;
import com.akto.dto.testing.TestResult.Confidence;
import com.akto.dto.testing.TestResult.TestError;
import com.akto.dto.testing.TestingRun.State;
import com.akto.dto.type.RequestTemplate;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.github.GithubUtils;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.store.SampleMessageStore;
import com.akto.store.TestingUtil;
import com.akto.test_editor.execution.Executor;
import com.akto.test_editor.execution.VariableResolver;
import com.akto.testing.yaml_tests.YamlTestTemplate;
import com.akto.testing_issues.TestingIssuesHandler;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.Constants;
import com.akto.util.JSONUtils;
import com.akto.util.enums.GlobalEnums.Severity;
import com.akto.util.enums.LoginFlowEnums;
import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.*;

import org.bson.types.ObjectId;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.*;

public class TestExecutor {

    private static final LoggerMaker loggerMaker = new LoggerMaker(TestExecutor.class, LogDb.TESTING);
    private static final Logger logger = LoggerFactory.getLogger(TestExecutor.class);

    public static long acceptableSizeInBytes = 5_000_000;
    private static final Gson gson = new Gson();

    private static Map<String, Map<String, Integer>> requestRestrictionMap = new ConcurrentHashMap<>();
    public static final String REQUEST_HOUR = "requestHour";
    public static final String COUNT = "count";
    public static final int ALLOWED_REQUEST_PER_HOUR = 100;
    public void init(TestingRun testingRun, ObjectId summaryId, SyncLimit syncLimit) {
        if (testingRun.getTestIdConfig() != 1) {
            apiWiseInit(testingRun, summaryId, false, new ArrayList<>(), syncLimit);
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
            GraphExecutorResult graphExecutorResult = apiWorkflowExecutor.init(graphExecutorRequest, debug, testLogs, null);
            WorkflowTestResultsDao.instance.insertOne(graphExecutorResult.getWorkflowTestResult());
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while executing workflow test " + e, LogDb.TESTING);
        }

        Map<String, Integer> totalCountIssues = new HashMap<>();
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

    public void apiWiseInit(TestingRun testingRun, ObjectId summaryId, boolean debug, List<TestingRunResult.TestLog> testLogs, SyncLimit syncLimit) {
        int accountId = Context.accountId.get();
        int now = Context.now();
        int maxConcurrentRequests = testingRun.getMaxConcurrentRequests() > 0 ? Math.min( testingRun.getMaxConcurrentRequests(), 100) : 10;
        TestingEndpoints testingEndpoints = testingRun.getTestingEndpoints();

        if (testingRun.getTestingRunConfig() != null) {
            TestingRunResultSummariesDao.instance.updateOneNoUpsert(Filters.eq(Constants.ID, summaryId),
                    Updates.set(TestingRunResultSummary.TESTS_INITIATED_COUNT,
                            testingRun.getTestingRunConfig().getTestSubCategoryList().size()));
        }

        SampleMessageStore sampleMessageStore = SampleMessageStore.create();
        sampleMessageStore.fetchSampleMessages(Main.extractApiCollectionIds(testingRun.getTestingEndpoints().returnApis()));

        List<ApiInfo.ApiInfoKey> apiInfoKeyList = testingEndpoints.returnApis();
        if (apiInfoKeyList == null || apiInfoKeyList.isEmpty()) return;
        loggerMaker.infoAndAddToDb("APIs found: " + apiInfoKeyList.size(), LogDb.TESTING);

        sampleMessageStore.buildSingleTypeInfoMap(testingEndpoints);
        List<TestRoles> testRoles = sampleMessageStore.fetchTestRoles();
        TestRoles attackerTestRole = Executor.fetchOrFindAttackerRole();

        Map<String, TestConfig> testConfigMap = YamlTemplateDao.instance.fetchTestConfigMap(false, false);

        List<CustomAuthType> customAuthTypes = CustomAuthTypeDao.instance.findAll(CustomAuthType.ACTIVE,true);
        TestingUtil testingUtil = new TestingUtil(sampleMessageStore, testRoles, testingRun.getUserEmail(), customAuthTypes);

        Map<ApiInfo.ApiInfoKey, List<String>> sampleDataMapForStatusCodeAnalyser = new HashMap<>();
        Set<ApiInfo.ApiInfoKey> apiInfoKeySet = new HashSet<>(apiInfoKeyList);
        Map<ApiInfo.ApiInfoKey, List<String>> sampleMessages = sampleMessageStore.getSampleDataMap();
        for (ApiInfo.ApiInfoKey apiInfoKey: sampleMessages.keySet()) {
            if (apiInfoKeySet.contains(apiInfoKey)) {
                sampleDataMapForStatusCodeAnalyser.put(apiInfoKey, sampleMessages.get(apiInfoKey));
            }
        }

        try {
            StatusCodeAnalyser.run(sampleDataMapForStatusCodeAnalyser, sampleMessageStore , attackerTestRole.findMatchingAuthMechanism(null), testingRun.getTestingRunConfig());
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error while running status code analyser " + e.getMessage());
        }

        loggerMaker.infoAndAddToDb("StatusCodeAnalyser result = " + StatusCodeAnalyser.result + " defaultPayloadsMap = " + StatusCodeAnalyser.defaultPayloadsMap);

        TestingRunResultSummariesDao.instance.updateOne(
            Filters.eq("_id", summaryId),
            Updates.set(TestingRunResultSummary.TOTAL_APIS, apiInfoKeyList.size()));

        CountDownLatch latch = new CountDownLatch(apiInfoKeyList.size());
        ExecutorService threadPool = Executors.newFixedThreadPool(maxConcurrentRequests);
        List<Future<Void>> futureTestingRunResults = new ArrayList<>();
        Map<String, Integer> hostsToApiCollectionMap = new HashMap<>();

        ConcurrentHashMap<String, String> subCategoryEndpointMap = new ConcurrentHashMap<>();
        Map<ApiInfoKey, String> apiInfoKeyToHostMap = new HashMap<>();
        String hostName;
        for (String testSubCategory: testingRun.getTestingRunConfig().getTestSubCategoryList()) {
            TestConfig testConfig = testConfigMap.get(testSubCategory);
            if (testConfig == null) {
                continue;
            }
            Map<String, Object> wordListsMap = (Map) testConfig.getWordlists();
            //VariableResolver.resolveWordList(wordListsMap, testingUtil.getSampleMessageStore().getSampleDataMap(), ap);
        }
        for (String testSubCategory: testingRun.getTestingRunConfig().getTestSubCategoryList()) {
            TestConfig testConfig = testConfigMap.get(testSubCategory);
            if (testConfig == null || testConfig.getStrategy() == null || testConfig.getStrategy().getRunOnce() == null) {
                continue;
            }
            for (ApiInfo.ApiInfoKey apiInfoKey: apiInfoKeyList) {
                try {
                    hostName = findHost(apiInfoKey, testingUtil.getSampleMessages(), testingUtil.getSampleMessageStore());
                    if (hostName == null) {
                        continue;
                    }
                    apiInfoKeyToHostMap.put(apiInfoKey, hostName);
                    subCategoryEndpointMap.put(apiInfoKey.getApiCollectionId() + "_" + testSubCategory, hostName);
                } catch (URISyntaxException e) {
                    loggerMaker.errorAndAddToDb("Error while finding host: " + e, LogDb.TESTING);
                }
            }
        }

        final int maxRunTime = testingRun.getTestRunTime() <= 0 ? 30*60 : testingRun.getTestRunTime(); // if nothing specified wait for 30 minutes

        for (ApiInfo.ApiInfoKey apiInfoKey: apiInfoKeyList) {
            try {
                hostName = findHost(apiInfoKey, testingUtil.getSampleMessages(), testingUtil.getSampleMessageStore());
                if (hostName != null && hostsToApiCollectionMap.get(hostName) == null) {
                    hostsToApiCollectionMap.put(hostName, apiInfoKey.getApiCollectionId());
                }
            } catch (URISyntaxException e) {
                loggerMaker.errorAndAddToDb("Error while finding host: " + e, LogDb.TESTING);
            }
            try {
                 Future<Void> future = threadPool.submit(
                         () -> startWithLatch(apiInfoKey,
                                 testingRun.getTestIdConfig(),
                                 testingRun.getId(), testingRun.getTestingRunConfig(), testingUtil, summaryId,
                                 accountId, latch, now, maxRunTime, testConfigMap, testingRun, subCategoryEndpointMap, apiInfoKeyToHostMap, debug, testLogs, syncLimit));
                 futureTestingRunResults.add(future);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error in API " + apiInfoKey + " : " + e.getMessage(), LogDb.TESTING);
            }
        }

        loggerMaker.infoAndAddToDb("hostsToApiCollectionMap : " + hostsToApiCollectionMap.keySet(), LogDb.TESTING);
        loggerMaker.infoAndAddToDb("Waiting...", LogDb.TESTING);

        try {
            boolean awaitResult = latch.await(maxRunTime, TimeUnit.SECONDS);
            loggerMaker.infoAndAddToDb("Await result: " + awaitResult, LogDb.TESTING);

            if (!awaitResult) { // latch countdown didn't reach 0
                for (Future<Void> future : futureTestingRunResults) {
                    future.cancel(true);
                }
                loggerMaker.infoAndAddToDb("Canceled all running future tasks due to timeout.", LogDb.TESTING);
            }

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        loggerMaker.infoAndAddToDb("Finished testing", LogDb.TESTING);

    }

    public static void updateTestSummary(ObjectId summaryId){
        loggerMaker.infoAndAddToDb("Finished updating results count", LogDb.TESTING);

        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions();
        options.returnDocument(ReturnDocument.AFTER);

        State updatedState = GetRunningTestsStatus.getRunningTests().isTestRunning(summaryId) ? State.COMPLETED : GetRunningTestsStatus.getRunningTests().getCurrentState(summaryId);

        TestingRunResultSummary testingRunResultSummary = TestingRunResultSummariesDao.instance.getMCollection().findOneAndUpdate(
                Filters.eq(Constants.ID, summaryId),
                Updates.combine(
                        Updates.set(TestingRunResultSummary.END_TIMESTAMP, Context.now()),
                        Updates.set(TestingRunResultSummary.STATE, updatedState)),
                options);
        GithubUtils.publishGithubComments(testingRunResultSummary);

        Map<String , Integer> totalCountIssues = testingRunResultSummary.getCountIssues();

        loggerMaker.infoAndAddToDb("Finished updating TestingRunResultSummariesDao", LogDb.TESTING);
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

    public static String findHost(ApiInfo.ApiInfoKey apiInfoKey, Map<ApiInfo.ApiInfoKey, List<String>> sampleMessagesMap, SampleMessageStore sampleMessageStore) throws URISyntaxException {
        List<String> sampleMessages = sampleMessagesMap.get(apiInfoKey);
        if (sampleMessages == null || sampleMessagesMap.isEmpty()) return null;

        List<RawApi> messages = sampleMessageStore.fetchAllOriginalMessages(apiInfoKey);
        if (messages.isEmpty()) return null;

        OriginalHttpRequest originalHttpRequest = messages.get(0).getRequest();

        String baseUrl = originalHttpRequest.getUrl();
        if (baseUrl.startsWith("http")) {
            URI uri = new URI(baseUrl);
            String host = uri.getScheme() + "://" + uri.getHost();
            return (uri.getPort() != -1)  ? host + ":" + uri.getPort() : host;
        } else {
            return "https://" + originalHttpRequest.findHostFromHeader();
        }
    }

    private LoginFlowResponse triggerLoginFlow(AuthMechanism authMechanism, int retries) {
        LoginFlowResponse loginFlowResponse = null;
        for (int i=0; i<retries; i++) {
            try {
                loginFlowResponse = executeLoginFlow(authMechanism, null);
                if (loginFlowResponse.getSuccess()) {
                    loggerMaker.infoAndAddToDb("login flow success", LogDb.TESTING);
                    break;
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e.getMessage(), LogDb.TESTING);
            }
        }
        return loginFlowResponse;
    }

    public static LoginFlowResponse executeLoginFlow(AuthMechanism authMechanism, LoginFlowParams loginFlowParams) throws Exception {

        if (authMechanism.getType() == null) {
            loggerMaker.infoAndAddToDb("auth type value is null", LogDb.TESTING);
            return new LoginFlowResponse(null, null, true);
        }

        if (!authMechanism.getType().equals(LoginFlowEnums.AuthMechanismTypes.LOGIN_REQUEST.toString())) {
            loggerMaker.infoAndAddToDb("invalid auth type for login flow execution", LogDb.TESTING);
            return new LoginFlowResponse(null, null, true);
        }

        loggerMaker.infoAndAddToDb("login flow execution started", LogDb.TESTING);

        WorkflowTest workflowObj = convertToWorkflowGraph(authMechanism.getRequestData());
        ApiWorkflowExecutor apiWorkflowExecutor = new ApiWorkflowExecutor();
        LoginFlowResponse loginFlowResp;
        loginFlowResp =  com.akto.testing.workflow_node_executor.Utils.runLoginFlow(workflowObj, authMechanism, loginFlowParams);
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
            if (data.getType().equals(LoginFlowEnums.LoginStepTypesEnums.OTP_VERIFICATION.toString())) {
                nodeType = WorkflowNodeDetails.Type.OTP;
                
                waitTime = 60;
                
            }
            if (data.getType().equals(LoginFlowEnums.LoginStepTypesEnums.RECORDED_FLOW.toString())) {
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
            ApiInfo.ApiInfoKey apiInfoKey, int testIdConfig, ObjectId testRunId, TestingRunConfig testingRunConfig,
            TestingUtil testingUtil, ObjectId testRunResultSummaryId, int accountId, CountDownLatch latch, int startTime,
            int maxRunTime, Map<String, TestConfig> testConfigMap, TestingRun testingRun,
            ConcurrentHashMap<String, String> subCategoryEndpointMap, Map<ApiInfoKey, String> apiInfoKeyToHostMap,
            boolean debug, List<TestingRunResult.TestLog> testLogs, SyncLimit syncLimit) {

        Context.accountId.set(accountId);
        loggerMaker.infoAndAddToDb("Starting test for " + apiInfoKey, LogDb.TESTING);   
        
        try {
            startTestNew(apiInfoKey, testRunId, testingRunConfig, testingUtil, testRunResultSummaryId, testConfigMap, subCategoryEndpointMap, apiInfoKeyToHostMap, debug, testLogs, startTime, maxRunTime, syncLimit);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "error while running tests: " + e);
        }

        latch.countDown();
        return null;
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
            loggerMaker.infoAndAddToDb("testingRunResults size: " + resultSize, LogDb.TESTING);
            trim(testingRunResults);
            TestingRunResultDao.instance.insertMany(testingRunResults);
            loggerMaker.infoAndAddToDb("Inserted testing results", LogDb.TESTING);

            TestingRunResultSummariesDao.instance.getMCollection().findOneAndUpdate(
                Filters.eq(Constants.ID, testRunResultSummaryId),
                Updates.inc(TestingRunResultSummary.TEST_RESULTS_COUNT, resultSize)
            );

            loggerMaker.infoAndAddToDb("Updated count in summary", LogDb.TESTING);

            TestingIssuesHandler handler = new TestingIssuesHandler();
            boolean triggeredByTestEditor = false;
            handler.handleIssuesCreationFromTestingRunResults(testingRunResults, triggeredByTestEditor);
            testingRunResults.clear();
        }
    }

    Set<Integer> deactivatedCollections = UsageMetricCalculator.getDeactivated();
    Set<Integer> demoCollections = UsageMetricCalculator.getDemos();

    public void startTestNew(ApiInfo.ApiInfoKey apiInfoKey, ObjectId testRunId,
                                               TestingRunConfig testingRunConfig, TestingUtil testingUtil,
                                               ObjectId testRunResultSummaryId, Map<String, TestConfig> testConfigMap,
                                               ConcurrentHashMap<String, String> subCategoryEndpointMap, Map<ApiInfoKey, String> apiInfoKeyToHostMap,
                                               boolean debug, List<TestingRunResult.TestLog> testLogs, int startTime, int timeToKill, SyncLimit syncLimit) {

        List<String> testSubCategories = testingRunConfig == null ? new ArrayList<>() : testingRunConfig.getTestSubCategoryList();

        int countSuccessfulTests = 0;
        for (String testSubCategory: testSubCategories) {
            if(GetRunningTestsStatus.getRunningTests().isTestRunning(testRunResultSummaryId)){
                if (Context.now() - startTime > timeToKill) {
                    loggerMaker.infoAndAddToDb("Timed out in " + (Context.now()-startTime) + "seconds");
                    return;
                }
                List<TestingRunResult> testingRunResults = new ArrayList<>();

                TestConfig testConfig = testConfigMap.get(testSubCategory);
                
                if (testConfig == null) continue;
                TestingRunResult testingRunResult = null;
                if (!applyRunOnceCheck(apiInfoKey, testConfig, subCategoryEndpointMap, apiInfoKeyToHostMap, testSubCategory)) {
                    continue;
                }
                String failMessage = null;
                if (deactivatedCollections.contains(apiInfoKey.getApiCollectionId())) {
                    failMessage = TestError.DEACTIVATED_ENDPOINT.getMessage();
                } else if (!demoCollections.contains(apiInfoKey.getApiCollectionId()) &&
                        syncLimit.updateUsageLeftAndCheckSkip()) {
                    failMessage = TestError.USAGE_EXCEEDED.getMessage();
                }
    
                if (failMessage != null) {
                    List<GenericTestResult> testResults = new ArrayList<>();
                    String testSuperType = testConfig.getInfo().getCategory().getName();
                    String testSubType = testConfig.getInfo().getSubCategory();
                    testResults.add(new TestResult(null, null, Collections.singletonList(failMessage), 0, false, Confidence.HIGH, null));
                    loggerMaker.infoAndAddToDb("Skipping test, " + failMessage, LogDb.TESTING);
                    testingRunResult = new TestingRunResult(
                            testRunId, apiInfoKey, testSuperType, testSubType, testResults,
                            false, new ArrayList<>(), 100, Context.now(),
                            Context.now(), testRunResultSummaryId, null, Collections.singletonList(new TestingRunResult.TestLog(TestingRunResult.TestLogType.INFO, "No samples messages found")));
                }

                try {
                    if(testingRunResult==null){
                        testingRunResult = runTestNew(apiInfoKey,testRunId,testingUtil,testRunResultSummaryId, testConfig, testingRunConfig, debug, testLogs);
                    }
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb("Error while running tests for " + testSubCategory +  ": " + e.getMessage(), LogDb.TESTING);
                    e.printStackTrace();
                }
                if (testingRunResult != null) {
                    testingRunResults.add(testingRunResult);
                    countSuccessfulTests++;
                }

                insertResultsAndMakeIssues(testingRunResults, testRunResultSummaryId);
            }else{
                logger.info("Test stopped for id: " + testRunId.toString());
                return;
            }
        }
        if(countSuccessfulTests > 0){
            ApiInfoDao.instance.updateLastTestedField(apiInfoKey);
        }

    }

    public boolean applyRunOnceCheck(ApiInfoKey apiInfoKey, TestConfig testConfig, ConcurrentHashMap<String, String> subCategoryEndpointMap, Map<ApiInfoKey, String> apiInfoKeyToHostMap, String testSubCategory) {

        if (testConfig.getStrategy() == null || testConfig.getStrategy().getRunOnce() == null) {
            return true;
        }

        String host;
        host = apiInfoKeyToHostMap.get(apiInfoKey);
        if (host != null) {
            String val = subCategoryEndpointMap.remove(apiInfoKey.getApiCollectionId() + "_" + testSubCategory);
            return val != null;
        }
        return true;
    }

    public TestingRunResult runTestNew(ApiInfo.ApiInfoKey apiInfoKey, ObjectId testRunId, TestingUtil testingUtil,
        ObjectId testRunResultSummaryId, TestConfig testConfig, TestingRunConfig testingRunConfig, boolean debug, List<TestingRunResult.TestLog> testLogs) {
            List<String> sampleMessages = testingUtil.getSampleMessages().get(apiInfoKey);
            AuthMechanism attackerAuthMechanism = null;
            if (sampleMessages != null && !sampleMessages.isEmpty()) {           

                RawApi rawApi = RawApi.buildFromMessage(sampleMessages.get(0));
                TestRoles attackerTestRole = Executor.fetchOrFindAttackerRole();
                if (attackerTestRole == null) {
                    loggerMaker.infoAndAddToDb("ATTACKER_TOKEN_ALL test role not found", LogDb.TESTING);
                } else {
                    attackerAuthMechanism = attackerTestRole.findMatchingAuthMechanism(rawApi);
                }
            }
            return runTestNew(apiInfoKey, testRunId, testingUtil.getSampleMessageStore(), attackerAuthMechanism, testingUtil.getCustomAuthTypes(), testRunResultSummaryId, testConfig, testingRunConfig, debug, testLogs);
    }

    public TestingRunResult runTestNew(ApiInfo.ApiInfoKey apiInfoKey, ObjectId testRunId, SampleMessageStore sampleMessageStore, AuthMechanism attackerAuthMechanism, List<CustomAuthType> customAuthTypes,
                                       ObjectId testRunResultSummaryId, TestConfig testConfig, TestingRunConfig testingRunConfig, boolean debug, List<TestingRunResult.TestLog> testLogs) {        
        String testSuperType = testConfig.getInfo().getCategory().getName();
        String testSubType = testConfig.getInfo().getSubCategory();

        if (deactivatedCollections.contains(apiInfoKey.getApiCollectionId())) {
            List<GenericTestResult> testResults = new ArrayList<>();
            testResults.add(new TestResult(null, null, Collections.singletonList(TestError.DEACTIVATED_ENDPOINT.getMessage()),0, false, Confidence.HIGH, null));
            return new TestingRunResult(
                testRunId, apiInfoKey, testSuperType, testSubType ,testResults,
                false,new ArrayList<>(),100,Context.now(),
                Context.now(), testRunResultSummaryId, null, Collections.singletonList(new TestingRunResult.TestLog(TestingRunResult.TestLogType.INFO, "Deactivated endpoint"))
            );
        }

        List<String> messages = sampleMessageStore.getSampleDataMap().get(apiInfoKey);
        if (messages == null || messages.isEmpty()){
            List<GenericTestResult> testResults = new ArrayList<>();
            testResults.add(new TestResult(null, null, Collections.singletonList(TestError.NO_PATH.getMessage()),0, false, Confidence.HIGH, null));
            return new TestingRunResult(
                testRunId, apiInfoKey, testSuperType, testSubType ,testResults,
                false,new ArrayList<>(),100,Context.now(),
                Context.now(), testRunResultSummaryId, null, Collections.singletonList(new TestingRunResult.TestLog(TestingRunResult.TestLogType.INFO, "No samples messages found"))
            );
        }

        String message = messages.get(messages.size() - 1);
        RawApi rawApi = RawApi.buildFromMessage(message);
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
            testSubType + "logId" + testExecutionLogId, LogDb.TESTING);


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
        }

        List<SingleTypeInfo> singleTypeInfos = new ArrayList<>();

        int confidencePercentage = 100;

        return new TestingRunResult(
                testRunId, apiInfoKey, testSuperType, testSubType ,testResults.getTestResults(),
                vulnerable,singleTypeInfos,confidencePercentage,startTime,
                endTime, testRunResultSummaryId, testResults.getWorkflowTest(), testLogs
        );
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

}

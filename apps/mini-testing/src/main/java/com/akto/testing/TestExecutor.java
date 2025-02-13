
package com.akto.testing;

import com.akto.crons.GetRunningTestsStatus;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.YamlTemplateDao;
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
import com.akto.sql.SampleDataAltDb;
import com.akto.store.AuthMechanismStore;
import com.akto.store.SampleMessageStore;
import com.akto.store.TestingUtil;
import com.akto.test_editor.execution.Build;
import com.akto.test_editor.execution.Executor;
import com.akto.test_editor.execution.VariableResolver;
import com.akto.testing.yaml_tests.YamlTestTemplate;
import com.akto.testing_issues.TestingIssuesHandler;
import com.akto.util.JSONUtils;
import com.akto.util.enums.GlobalEnums.Severity;
import com.akto.util.enums.LoginFlowEnums;
import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import static com.akto.test_editor.execution.Build.modifyRequest;

import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.*;
import com.akto.testing.workflow_node_executor.Utils;

public class TestExecutor {

    private static final LoggerMaker loggerMaker = new LoggerMaker(TestExecutor.class, LogDb.TESTING);
    private static final Logger logger = LoggerFactory.getLogger(TestExecutor.class);

    public static long acceptableSizeInBytes = 5_000_000;
    private static final Gson gson = new Gson();

    private static final DataActor dataActor = DataActorFactory.fetchInstance();

    private static Map<String, Map<String, Integer>> requestRestrictionMap = new ConcurrentHashMap<>();
    public static final String REQUEST_HOUR = "requestHour";
    public static final String COUNT = "count";
    public static final int ALLOWED_REQUEST_PER_HOUR = 100;
    public void init(TestingRun testingRun, ObjectId summaryId) {
        if (testingRun.getTestIdConfig() != 1) {
            apiWiseInit(testingRun, summaryId, false, new ArrayList<>());
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

        WorkflowTest workflowTest = dataActor.fetchWorkflowTest(workflowTestOld.getId());

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
            dataActor.insertWorkflowTestResult(graphExecutorResult.getWorkflowTestResult());
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while executing workflow test " + e, LogDb.TESTING);
        }

        Map<String, Integer> totalCountIssues = new HashMap<>();
        totalCountIssues.put("HIGH", 0);
        totalCountIssues.put("MEDIUM", 0);
        totalCountIssues.put("LOW", 0);

        dataActor.updateIssueCountInTestSummary(summaryId.toHexString(), totalCountIssues);
    }

    public void apiWiseInit(TestingRun testingRun, ObjectId summaryId, boolean debug, List<TestingRunResult.TestLog> testLogs) {
        int accountId = Context.accountId.get();
        int now = Context.now();
        int maxConcurrentRequests = testingRun.getMaxConcurrentRequests() > 0 ? Math.min( testingRun.getMaxConcurrentRequests(), 100) : 10;
        TestingEndpoints testingEndpoints = testingRun.getTestingEndpoints();

        if (testingRun.getTestingRunConfig() != null) {
            dataActor.updateTestInitiatedCountInTestSummary(summaryId.toHexString(), testingRun.getTestingRunConfig().getTestSubCategoryList().size());
        }

        SampleMessageStore sampleMessageStore = SampleMessageStore.create();
        sampleMessageStore.fetchSampleMessages(Main.extractApiCollectionIds(testingRun.getTestingEndpoints().returnApis()));
        AuthMechanismStore authMechanismStore = AuthMechanismStore.create();

        List<ApiInfo.ApiInfoKey> apiInfoKeyList = testingEndpoints.returnApis();
        if (apiInfoKeyList == null || apiInfoKeyList.isEmpty()) return;
        loggerMaker.infoAndAddToDb("APIs found: " + apiInfoKeyList.size(), LogDb.TESTING);

        List<TestRoles> testRoles = sampleMessageStore.fetchTestRoles();
        AuthMechanism authMechanism = authMechanismStore.getAuthMechanism();

        List<YamlTemplate> yamlTemplates = new ArrayList<>();
        final int TEST_LIMIT = 50;
        for (int i = 0; i < 100; i++) {
            List<YamlTemplate> temp = dataActor.fetchYamlTemplates(true, i * TEST_LIMIT);
            if (temp == null || temp.isEmpty()) {
                break;
            }
            yamlTemplates.addAll(temp);
            if (temp.size() < TEST_LIMIT) {
                break;
            }
        }

        Map<String, TestConfig> testConfigMap = YamlTemplateDao.instance.fetchTestConfigMap(false, false, yamlTemplates);

        List<CustomAuthType> customAuthTypes = dataActor.fetchCustomAuthTypes();
        TestingUtil testingUtil = new TestingUtil(authMechanism, sampleMessageStore, testRoles, testingRun.getUserEmail(), customAuthTypes);

        try {
            LoginFlowResponse loginFlowResponse = triggerLoginFlow(authMechanism, 3);
            if (!loginFlowResponse.getSuccess()) {
                loggerMaker.errorAndAddToDb("login flow failed", LogDb.TESTING);
                throw new Exception("login flow failed");
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e.getMessage(), LogDb.TESTING);
            return;
        }

        Map<ApiInfo.ApiInfoKey, List<String>> sampleDataMapForStatusCodeAnalyser = new HashMap<>();
        Set<ApiInfo.ApiInfoKey> apiInfoKeySet = new HashSet<>(apiInfoKeyList);
        Map<ApiInfo.ApiInfoKey, List<String>> sampleMessages = sampleMessageStore.getSampleDataMap();
        for (ApiInfo.ApiInfoKey apiInfoKey: sampleMessages.keySet()) {
            if (apiInfoKeySet.contains(apiInfoKey)) {
                sampleDataMapForStatusCodeAnalyser.put(apiInfoKey, sampleMessages.get(apiInfoKey));
            }
        }

        try {
            StatusCodeAnalyser.run(sampleDataMapForStatusCodeAnalyser, sampleMessageStore , authMechanismStore, testingRun.getTestingRunConfig());
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while running status code analyser " + e.getMessage(), LogDb.TESTING);
        }

        loggerMaker.infoAndAddToDb("StatusCodeAnalyser result = " + StatusCodeAnalyser.result, LogDb.TESTING);
        loggerMaker.infoAndAddToDb("StatusCodeAnalyser defaultPayloadsMap = " + StatusCodeAnalyser.defaultPayloadsMap, LogDb.TESTING);

        dataActor.updateTotalApiCountInTestSummary(summaryId.toHexString(), apiInfoKeyList.size());
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
                                 testingRun.getId(),testingRun.getTestingRunConfig(), testingUtil, summaryId,
                                 accountId, latch, now, maxRunTime, testConfigMap, testingRun, subCategoryEndpointMap, apiInfoKeyToHostMap, debug, testLogs));
                 futureTestingRunResults.add(future);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error in API " + apiInfoKey + " : " + e.getMessage(), LogDb.TESTING);
            }
        }

        loggerMaker.infoAndAddToDb("hostsToApiCollectionMap : " + hostsToApiCollectionMap.keySet(), LogDb.TESTING);
        loggerMaker.infoAndAddToDb("Waiting...", LogDb.TESTING);

        try {
            //boolean awaitResult = latch.await(maxRunTime, TimeUnit.SECONDS);
            int waitTs = Context.now();
            while(latch.getCount() > 0 && GetRunningTestsStatus.getRunningTests().isTestRunning(summaryId) 
                && (Context.now() - waitTs < maxRunTime)) {
                    loggerMaker.infoAndAddToDb("waiting for tests to finish", LogDb.TESTING);
                    Thread.sleep(10000);
            }
            loggerMaker.infoAndAddToDb("test is completed", LogDb.TESTING);
            //awaitResult = latch.getCount() > 0 && GetRunningTestsStatus.getRunningTests().isTestRunning(summaryId);
            //loggerMaker.infoAndAddToDb("Await result: " + awaitResult, LogDb.TESTING);

            for (Future<Void> future : futureTestingRunResults) {
                future.cancel(true);
            }
            loggerMaker.infoAndAddToDb("Canceled all running future tasks due to timeout.", LogDb.TESTING);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        loggerMaker.infoAndAddToDb("Finished testing", LogDb.TESTING);

    }

    public static void updateTestSummary(ObjectId summaryId){
        loggerMaker.infoAndAddToDb("Finished updating results count", LogDb.TESTING);

        State updatedState = GetRunningTestsStatus.getRunningTests().isTestRunning(summaryId) ? State.COMPLETED : GetRunningTestsStatus.getRunningTests().getCurrentState(summaryId);

        int skip = 0;
        int limit = 1000;
        boolean fetchMore = false;
        do {
            fetchMore = false;
            List<TestingRunResult> testingRunResults = dataActor.fetchLatestTestingRunResultBySummaryId(summaryId.toHexString(), limit, skip);
            loggerMaker.infoAndAddToDb("Reading " + testingRunResults.size() + " vulnerable testingRunResults",
                    LogDb.TESTING);
            if (testingRunResults.size() == limit) {
                skip += limit;
                fetchMore = true;
            }

        } while (fetchMore);

        TestingRunResultSummary testingRunResultSummary = dataActor.updateIssueCountAndStateInSummary(summaryId.toHexString(), new HashMap<>(), updatedState.toString());
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

        WorkflowTest workflowObj = convertToWorkflowGraph(authMechanism.getRequestData(), loginFlowParams);
        ApiWorkflowExecutor apiWorkflowExecutor = new ApiWorkflowExecutor();
        LoginFlowResponse loginFlowResp;
        loginFlowResp =  Utils.runLoginFlow(workflowObj, authMechanism, loginFlowParams);
        return loginFlowResp;
    }

    public static WorkflowTest convertToWorkflowGraph(ArrayList<RequestData> requestData, LoginFlowParams loginFlowParams) {

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
                if (loginFlowParams == null || !loginFlowParams.getFetchValueMap()) {
                    waitTime = 60;
                }
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
            ApiInfo.ApiInfoKey apiInfoKey, int testIdConfig, ObjectId testRunId, TestingRunConfig testingRunConfig,
            TestingUtil testingUtil, ObjectId testRunResultSummaryId, int accountId, CountDownLatch latch, int startTime,
            int maxRunTime, Map<String, TestConfig> testConfigMap, TestingRun testingRun,
            ConcurrentHashMap<String, String> subCategoryEndpointMap, Map<ApiInfoKey, String> apiInfoKeyToHostMap,
            boolean debug, List<TestingRunResult.TestLog> testLogs) {

        Context.accountId.set(accountId);
        loggerMaker.infoAndAddToDb("Starting test for " + apiInfoKey, LogDb.TESTING);   
        try {
            startTestNew(apiInfoKey, testRunId, testingRunConfig, testingUtil, testRunResultSummaryId, testConfigMap, subCategoryEndpointMap, apiInfoKeyToHostMap, debug, testLogs, startTime, maxRunTime);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "error while running tests: " + e);
        }
        latch.countDown();
        loggerMaker.infoAndAddToDb("DONE FINAL: " + latch.getCount(), LogDb.TESTING);
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
            loggerMaker.infoAndAddToDb("Inserted testing results", LogDb.TESTING);
            dataActor.updateTestResultsCountInTestSummary(testRunResultSummaryId.toHexString(), resultSize);
            loggerMaker.infoAndAddToDb("Updated count in summary", LogDb.TESTING);

            TestingIssuesHandler handler = new TestingIssuesHandler();
            boolean triggeredByTestEditor = false;
            try{
                List<GenericTestResult> list = new ArrayList<>();
                list.add(testRes);
                trr.setTestResults(list);
                handler.handleIssuesCreationFromTestingRunResults(testingRunResults, triggeredByTestEditor);
            } catch (Exception e){
                loggerMaker.errorAndAddToDb(e, "Unable to create issues", LogDb.TESTING);
            }
            testingRunResults.clear();
        }
    }

    public void startTestNew(ApiInfo.ApiInfoKey apiInfoKey, ObjectId testRunId,
                                               TestingRunConfig testingRunConfig, TestingUtil testingUtil,
                                               ObjectId testRunResultSummaryId, Map<String, TestConfig> testConfigMap,
                                               ConcurrentHashMap<String, String> subCategoryEndpointMap, Map<ApiInfoKey, String> apiInfoKeyToHostMap,
                                               boolean debug, List<TestingRunResult.TestLog> testLogs, int startTime, int timeToKill) {

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
                try {
                    testingRunResult = runTestNew(apiInfoKey,testRunId,testingUtil,testRunResultSummaryId, testConfig, testingRunConfig, debug, testLogs);
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
                break;
            }
        }
        if(countSuccessfulTests > 0){
            dataActor.updateLastTestedField(apiInfoKey.getApiCollectionId(), apiInfoKey.getUrl(), apiInfoKey.getMethod().toString());
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

    //Set<Integer> deactivatedCollections = UsageMetricCalculator.getDeactivated();

    public TestingRunResult runTestNew(ApiInfo.ApiInfoKey apiInfoKey, ObjectId testRunId, TestingUtil testingUtil,
                                       ObjectId testRunResultSummaryId, TestConfig testConfig, TestingRunConfig testingRunConfig, boolean debug, List<TestingRunResult.TestLog> testLogs) {

        String testSuperType = testConfig.getInfo().getCategory().getName();
        String testSubType = testConfig.getInfo().getSubCategory();

        // if (deactivatedCollections.contains(apiInfoKey.getApiCollectionId())) {
        //     List<GenericTestResult> testResults = new ArrayList<>();
        //     testResults.add(new TestResult(null, null, Collections.singletonList(TestError.DEACTIVATED_ENDPOINT.getMessage()),0, false, Confidence.HIGH, null));
        //     return new TestingRunResult(
        //         testRunId, apiInfoKey, testSuperType, testSubType ,testResults,
        //         false,new ArrayList<>(),100,Context.now(),
        //         Context.now(), testRunResultSummaryId, null, Collections.singletonList(new TestingRunResult.TestLog(TestingRunResult.TestLogType.INFO, "Deactivated endpoint"))
        //     );
        // }

        List<String> messages = testingUtil.getSampleMessages().get(apiInfoKey);
        if (messages == null) {
            messages = new ArrayList<String>();
        }

        try {
            messages.addAll(SampleDataAltDb.findSamplesByApiInfoKey(apiInfoKey));
        } catch (Exception e) {
            // TODO Auto-generated catch block
            //e.printStackTrace();
        }

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
        try {
            long start = System.currentTimeMillis();
            String msg = null;
            try {
                msg = SampleDataAltDb.findLatestSampleByApiInfoKey(apiInfoKey);
            } catch (Exception e) {
            }
            if (msg != null) {
                message = msg;
            }
            AllMetrics.instance.setSampleDataFetchLatency(System.currentTimeMillis() - start);
        } catch (Exception e) {
            e.printStackTrace();
        }
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

        VariableResolver.resolveWordList(varMap, testingUtil.getSampleMessages(), apiInfoKey);

        String testExecutionLogId = UUID.randomUUID().toString();
        
        loggerMaker.infoAndAddToDb("triggering test run for apiInfoKey " + apiInfoKey + "test " + 
            testSubType + " logId " + testExecutionLogId, LogDb.TESTING);

        List<CustomAuthType> customAuthTypes = testingUtil.getCustomAuthTypes();
        // TestingUtil -> authMechanism
        // TestingConfig -> auth
        com.akto.test_editor.execution.Executor executor = new Executor();
        executor.overrideTestUrl(rawApi, testingRunConfig);
        YamlTestTemplate yamlTestTemplate = new YamlTestTemplate(apiInfoKey,filterNode, validatorNode, executorNode,
                rawApi, varMap, auth, testingUtil.getAuthMechanism(), testExecutionLogId, testingRunConfig, customAuthTypes, testConfig.getStrategy());
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
                cleanUpTestArtifacts(Collections.singletonList(ret), apiInfoKey, testingUtil, testingRunConfig);
            } catch(Exception e){
                loggerMaker.errorAndAddToDb(e, "Error while cleaning up test artifacts: " + e.getMessage(), LogDb.TESTING);
            }
        }

        return ret;
    }

    private Map<ApiInfoKey, List<ApiInfoKey>> cleanUpTestArtifacts(List<TestingRunResult> testingRunResults, ApiInfoKey apiInfoKey, TestingUtil testingUtil, TestingRunConfig testingRunConfig) {

        Map<ApiInfoKey, List<ApiInfoKey>> cleanedUpRequests = new HashMap<>();

        for (TestingRunResult trr: testingRunResults) {

            for(GenericTestResult gtr: trr.getTestResults()) {                
                for(String message: gtr.getResponses()) {
                    if (message != null) {
                        String formattedMessage = null;
                        try {
                            formattedMessage = com.akto.runtime.utils.Utils.convertToSampleMessage(message);
                        } catch (Exception e) {
                            loggerMaker.errorAndAddToDb("cleanUpTestArtifacts: Error while formatting message: " + e.getMessage(), LogDb.TESTING);
                        }
                        if (formattedMessage == null) {
                            continue;
                        }
                        RawApi rawApiToBeReplayed = RawApi.buildFromMessage(formattedMessage);
                        if (rawApiToBeReplayed.getResponse().getStatusCode() >= 300) {
                            loggerMaker.infoAndAddToDb("cleanUpTestArtifacts rawApiToBeReplayed status code invalid: " + rawApiToBeReplayed.getResponse().getStatusCode(), LogDb.TESTING);
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
                                        List<String> samples = testingUtil.getSampleMessages().get(cleanUpApiInfoKey);
                                        if (samples == null || samples.isEmpty()) {
                                            loggerMaker.infoAndAddToDb(String.format("cleanUpTestArtifacts samples not found for: %s %s %s", node.getApiCollectionIdReq(), node.getUrlReq(), node.getMethodReq()));
                                            continue;
                                        } else {
                                            RawApi nextApi = RawApi.buildFromMessage(samples.get(0));

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
                                                TestRoles role = dataActor.fetchTestRolesforId(testingRunConfig.getTestRoleId());
                                                if (role != null) {
                                                    EndpointLogicalGroup endpointLogicalGroup = role.fetchEndpointLogicalGroup();
                                                    if (endpointLogicalGroup != null && endpointLogicalGroup.getTestingEndpoints() != null  && endpointLogicalGroup.getTestingEndpoints().containsApi(apiInfoKey)) {
                                                        if (role.getDefaultAuthMechanism() != null) {
                                                            loggerMaker.infoAndAddToDb("attempting to override auth ", LogDb.TESTING);
                                                            Executor.modifyAuthTokenInRawApi(role, nextApi);
                                                        } else {
                                                            loggerMaker.infoAndAddToDb("Default auth mechanism absent", LogDb.TESTING);
                                                        }
                                                    } else {
                                                        loggerMaker.infoAndAddToDb("Endpoint didn't satisfy endpoint condition for testRole", LogDb.TESTING);
                                                    }
                                                } else {
                                                    String reason = "Test role has been deleted";
                                                    loggerMaker.infoAndAddToDb(reason + ", going ahead with sample auth", LogDb.TESTING);
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

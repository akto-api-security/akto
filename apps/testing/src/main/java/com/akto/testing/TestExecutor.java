package com.akto.testing;

import com.akto.dao.AuthMechanismsDao;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing.TestingRunResultSummariesDao;
import com.akto.dao.testing.WorkflowTestsDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.Auth;
import com.akto.dto.test_editor.ExecutorNode;
import com.akto.dto.test_editor.FilterNode;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.testing.*;
import com.akto.dto.testing.TestResult.Confidence;
import com.akto.dto.testing.TestingRun.State;
import com.akto.dto.type.RequestTemplate;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.rules.TestPlugin;
import com.akto.store.AuthMechanismStore;
import com.akto.store.SampleMessageStore;
import com.akto.store.TestingUtil;
import com.akto.testing.yaml_tests.YamlTestTemplate;
import com.akto.testing_issues.TestingIssuesHandler;
import com.akto.util.JSONUtils;
import com.akto.util.enums.GlobalEnums.Severity;
import com.akto.util.enums.LoginFlowEnums;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.types.ObjectId;
import org.json.JSONObject;
import org.mortbay.util.ajax.JSON;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.*;

public class TestExecutor {

    private static final LoggerMaker loggerMaker = new LoggerMaker(TestExecutor.class);
    public static long acceptableSizeInBytes = 5_000_000;
    private static final Gson gson = new Gson();

    private static Map<String, Map<String, Integer>> requestRestrictionMap = new ConcurrentHashMap<>();
    public static final String REQUEST_HOUR = "requestHour";
    public static final String COUNT = "count";
    public static final int ALLOWED_REQUEST_PER_HOUR = 100;
    private static final Gson gson = new Gson();

    public void init(TestingRun testingRun, SampleMessageStore sampleMessageStore, AuthMechanismStore authMechanismStore, ObjectId summaryId) {
        if (testingRun.getTestIdConfig() != 1) {
            apiWiseInit(testingRun, sampleMessageStore, authMechanismStore, summaryId);
        } else {
            workflowInit(testingRun, summaryId);
        }
    }

    public void workflowInit (TestingRun testingRun, ObjectId summaryId) {
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
            apiWorkflowExecutor.init(workflowTest, testingRun.getId(), summaryId);
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


    public void apiWiseInit(TestingRun testingRun, SampleMessageStore sampleMessageStore, AuthMechanismStore authMechanismStore, ObjectId summaryId) {
        int accountId = Context.accountId.get();
        int now = Context.now();
        int maxConcurrentRequests = testingRun.getMaxConcurrentRequests() > 0 ? testingRun.getMaxConcurrentRequests() : 100;
        TestingEndpoints testingEndpoints = testingRun.getTestingEndpoints();

        List<ApiInfo.ApiInfoKey> apiInfoKeyList = testingEndpoints.returnApis();
        if (apiInfoKeyList == null || apiInfoKeyList.isEmpty()) return;
        loggerMaker.infoAndAddToDb("APIs found: " + apiInfoKeyList.size(), LogDb.TESTING);

        sampleMessageStore.buildSingleTypeInfoMap(testingEndpoints);
        List<TestRoles> testRoles = sampleMessageStore.fetchTestRoles();
        AuthMechanism authMechanism = authMechanismStore.getAuthMechanism();

        List<AuthParam> authParams = authMechanism.getAuthParams();

        Map<String, TestConfig> testConfigMap = YamlTemplateDao.instance.fetchTestConfigMap(false);


        TestingUtil testingUtil = new TestingUtil(authMechanism, sampleMessageStore, testRoles, testingRun.getUserEmail());

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

        TestingRunResultSummariesDao.instance.updateOne(
            Filters.eq("_id", summaryId),
            Updates.set(TestingRunResultSummary.TOTAL_APIS, apiInfoKeyList.size()));

        CountDownLatch latch = new CountDownLatch(apiInfoKeyList.size());
        ExecutorService threadPool = Executors.newFixedThreadPool(maxConcurrentRequests);
        List<Future<List<TestingRunResult>>> futureTestingRunResults = new ArrayList<>();
        Map<String, Integer> hostsToApiCollectionMap = new HashMap<>();

        ConcurrentHashMap<String, String> subCategoryEndpointMap = new ConcurrentHashMap<>();
        Map<ApiInfoKey, String> apiInfoKeyToHostMap = new HashMap<>();
        String hostName;
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
                 Future<List<TestingRunResult>> future = threadPool.submit(
                         () -> startWithLatch(apiInfoKey,
                                 testingRun.getTestIdConfig(),
                                 testingRun.getId(),testingRun.getTestingRunConfig(), testingUtil, summaryId,
                                 accountId, latch, now, testingRun.getTestRunTime(), testConfigMap, testingRun, subCategoryEndpointMap, apiInfoKeyToHostMap));
                 futureTestingRunResults.add(future);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error in API " + apiInfoKey + " : " + e.getMessage(), LogDb.TESTING);
            }
        }

        loggerMaker.infoAndAddToDb("hostsToApiCollectionMap : " + hostsToApiCollectionMap.keySet(), LogDb.TESTING);
        loggerMaker.infoAndAddToDb("Waiting...", LogDb.TESTING);

        try {
            int maxRunTime = testingRun.getTestRunTime();
            if (maxRunTime < 0) maxRunTime = 30*60; // if nothing specified wait for 30 minutes
            boolean awaitResult = latch.await(maxRunTime, TimeUnit.SECONDS);
            loggerMaker.infoAndAddToDb("Await result: " + awaitResult, LogDb.TESTING);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        loggerMaker.infoAndAddToDb("Finished testing", LogDb.TESTING);

        List<TestingRunResult> testingRunResults = new ArrayList<>();
        for (Future<List<TestingRunResult>> future: futureTestingRunResults) {
            if (!future.isDone()) continue;
            try {
                if (!future.get().isEmpty()) {
                    testingRunResults.addAll(future.get());
                }
            } catch (InterruptedException | ExecutionException e) {
                loggerMaker.errorAndAddToDb("Error while after running test : " + e, LogDb.TESTING);
            }
        }

        loggerMaker.infoAndAddToDb("Finished adding " + testingRunResults.size() + " testingRunResults", LogDb.TESTING);

        TestingRunResultSummariesDao.instance.updateOne(
            Filters.eq("_id", summaryId),
            Updates.set(TestingRunResultSummary.TEST_RESULTS_COUNT, testingRunResults.size())
        );

        loggerMaker.infoAndAddToDb("Finished adding issues", LogDb.TESTING);

        Map<String, Integer> totalCountIssues = new HashMap<>();
        totalCountIssues.put("HIGH", 0);
        totalCountIssues.put("MEDIUM", 0);
        totalCountIssues.put("LOW", 0);

        for (TestingRunResult testingRunResult: testingRunResults) {
            if (testingRunResult.isVulnerable()) {
                String severity = getSeverityFromTestingRunResult(testingRunResult).toString();
                int initialCount = totalCountIssues.get(severity);
                totalCountIssues.put(severity, initialCount + 1);
            }
        }

        TestingRunResultSummariesDao.instance.updateOne(
            Filters.eq("_id", summaryId),
            Updates.combine(
                    Updates.set(TestingRunResultSummary.END_TIMESTAMP, Context.now()),
                    Updates.set(TestingRunResultSummary.STATE, State.COMPLETED),
                    Updates.set(TestingRunResultSummary.COUNT_ISSUES, totalCountIssues)
            )
        );

        loggerMaker.infoAndAddToDb("Finished updating TestingRunResultSummariesDao", LogDb.TESTING);

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

    public LoginFlowResponse executeLoginFlow(AuthMechanism authMechanism, LoginFlowParams loginFlowParams) throws Exception {

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
        loginFlowResp = apiWorkflowExecutor.runLoginFlow(workflowObj, authMechanism, loginFlowParams);
        return loginFlowResp;
    }

    public WorkflowTest convertToWorkflowGraph(ArrayList<RequestData> requestData, LoginFlowParams loginFlowParams) {

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
                if (loginFlowParams == null || !loginFlowParams.getFetchValueMap()) {
                    waitTime = 60;
                }
            }
            if (data.getType().equals(LoginFlowEnums.LoginStepTypesEnums.RECORDED_FLOW.toString())) {
                nodeType = WorkflowNodeDetails.Type.RECORDED;
            }
            WorkflowNodeDetails workflowNodeDetails = new WorkflowNodeDetails(0, data.getUrl(),
                    URLMethods.Method.fromString(data.getMethod()), "", sampleData,
                    nodeType, true, waitTime, 0, 0, data.getRegex(), data.getOtpRefUuid());

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
            System.out.println(param);
            System.out.println(flattened.get(param));
            respMap.put(param, flattened.get(param));
        }

        for (String headerName: headers.keySet()) {
            for (String val: headers.get(headerName)) {
                System.out.println(headerName);
                System.out.println(val);
                respMap.put(headerName, val);
            }
        }
        return respMap;
    }

    public List<TestingRunResult> startWithLatch(
            ApiInfo.ApiInfoKey apiInfoKey, int testIdConfig, ObjectId testRunId, TestingRunConfig testingRunConfig,
            TestingUtil testingUtil, ObjectId testRunResultSummaryId, int accountId, CountDownLatch latch, int startTime,
            int timeToKill, Map<String, TestConfig> testConfigMap, TestingRun testingRun,
            ConcurrentHashMap<String, String> subCategoryEndpointMap, Map<ApiInfoKey, String> apiInfoKeyToHostMap) {

        loggerMaker.infoAndAddToDb("Starting test for " + apiInfoKey, LogDb.TESTING);

        Context.accountId.set(accountId);
        List<TestingRunResult> testingRunResults = new ArrayList<>();
        int now = Context.now();
        if ( timeToKill <= 0 || now - startTime <= timeToKill) {
            try {
                // todo: commented out older one
//                testingRunResults = start(apiInfoKey, testIdConfig, testRunId, testingRunConfig, testingUtil, testRunResultSummaryId, testConfigMap);
                testingRunResults = startTestNew(apiInfoKey, testRunId, testingRunConfig, testingUtil, testRunResultSummaryId, testConfigMap, subCategoryEndpointMap, apiInfoKeyToHostMap);
                String size = testingRunResults.size()+"";
                loggerMaker.infoAndAddToDb("testingRunResults size: " + size, LogDb.TESTING);
                if (!testingRunResults.isEmpty()) {
                    trim(testingRunResults);
                    TestingRunResultDao.instance.insertMany(testingRunResults);
                    loggerMaker.infoAndAddToDb("Inserted testing results", LogDb.TESTING);
                    //Creating issues from testingRunResults
                   TestingIssuesHandler handler = new TestingIssuesHandler();
                   boolean triggeredByTestEditor = false;
                   if (testingRun.getTriggeredBy() != null) {
                        triggeredByTestEditor = testingRun.getTriggeredBy().equals("test_editor");
                   }
                   handler.handleIssuesCreationFromTestingRunResults(testingRunResults, triggeredByTestEditor); // pass new field here
                }
            } catch (Exception e) {
                e.printStackTrace();
                loggerMaker.errorAndAddToDb("error while running tests: " + e, LogDb.TESTING);
            }
        }

        latch.countDown();
        return testingRunResults;
    }

    public static void trim(TestingRunResult testingRunResult) {
        List<TestResult> testResults = testingRunResult.getTestResults();
        int endIdx = testResults.size();
        long currentSize = 0;

        for (int idx=0;idx< testResults.size();idx++) {
            TestResult testResult = testResults.get(idx);

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

    public List<TestingRunResult> startTestNew(ApiInfo.ApiInfoKey apiInfoKey, ObjectId testRunId,
                                               TestingRunConfig testingRunConfig, TestingUtil testingUtil,
                                               ObjectId testRunResultSummaryId, Map<String, TestConfig> testConfigMap,
                                               ConcurrentHashMap<String, String> subCategoryEndpointMap, Map<ApiInfoKey, String> apiInfoKeyToHostMap) {
        List<TestingRunResult> testingRunResults = new ArrayList<>();

        List<String> testSubCategories = testingRunConfig == null ? new ArrayList<>() : testingRunConfig.getTestSubCategoryList();

        for (String testSubCategory: testSubCategories) {
            TestConfig testConfig = testConfigMap.get(testSubCategory);
            if (testConfig == null) continue;
            TestingRunResult testingRunResult = null;
            if (!applyRunOnceCheck(apiInfoKey, testConfig, subCategoryEndpointMap, apiInfoKeyToHostMap, testSubCategory)) {
                continue;
            }
            try {
                testingRunResult = runTestNew(apiInfoKey,testRunId,testingUtil,testRunResultSummaryId, testConfig, testingRunConfig);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error while running tests for " + testSubCategory +  ": " + e.getMessage(), LogDb.TESTING);
                e.printStackTrace();
            }
            if (testingRunResult != null) testingRunResults.add(testingRunResult);
        }

        return testingRunResults;
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

    public TestingRunResult runTestNew(ApiInfo.ApiInfoKey apiInfoKey, ObjectId testRunId, TestingUtil testingUtil, ObjectId testRunResultSummaryId, TestConfig testConfig, TestingRunConfig testingRunConfig) {

        List<String> messages = testingUtil.getSampleMessages().get(apiInfoKey);
        if (messages == null || messages.size() == 0) return null;

        String message = messages.get(0);

        RawApi rawApi = RawApi.buildFromMessage(message);
        int startTime = Context.now();

        filterGraphQlPayload(rawApi, apiInfoKey);

        FilterNode filterNode = testConfig.getApiSelectionFilters().getNode();
        FilterNode validatorNode = testConfig.getValidation().getNode();
        ExecutorNode executorNode = testConfig.getExecute().getNode();
        Auth auth = testConfig.getAuth();
        Map<String, List<String>> wordListsMap = testConfig.getWordlists();
        Map<String, Object> varMap = new HashMap<>();
        String severity = testConfig.getInfo().getSeverity();

        for (String key: wordListsMap.keySet()) {
            varMap.put("wordList_" + key, wordListsMap.get(key));
        }

        String testSuperType = testConfig.getInfo().getCategory().getName();
        String testSubType = testConfig.getInfo().getSubCategory();

        String testExecutionLogId = UUID.randomUUID().toString();
        
        loggerMaker.infoAndAddToDb("triggering test run for apiInfoKey " + apiInfoKey + "test " + 
            testSubType + "logId" + testExecutionLogId, LogDb.TESTING);

        YamlTestTemplate yamlTestTemplate = new YamlTestTemplate(apiInfoKey,filterNode, validatorNode, executorNode, rawApi, varMap, auth, testingUtil.getAuthMechanism(), testExecutionLogId, testingRunConfig);
        List<TestResult> testResults = yamlTestTemplate.run();
        if (testResults == null || testResults.size() == 0) {
            return null;
        }
        int endTime = Context.now();

        boolean vulnerable = false;
        for (TestResult testResult: testResults) {
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
                testRunId, apiInfoKey, testSuperType, testSubType ,testResults,
                vulnerable,singleTypeInfos,confidencePercentage,startTime,
                endTime, testRunResultSummaryId
        );
    }

    public void filterGraphQlPayload(RawApi rawApi, ApiInfo.ApiInfoKey apiInfoKey) {

        String url = apiInfoKey.getUrl();
        if (!url.toLowerCase().contains("graphql") || (!url.toLowerCase().contains("query") && !url.toLowerCase().contains("mutation"))) {
            return;
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
                return;
            }
            String queryStr = split[1];

            String []querySplit = queryStr.split("/");
            if (querySplit.length < 2) {
                return;
            }
            queryName = querySplit[0];
        } catch (Exception e) {
            return;
        }

        ObjectMapper m = new ObjectMapper();
        String updatedBody, updatedRespBody;
        try {
            Object obj = JSON.parse(rawApi.getRequest().getBody());
            List<Object> objList = Arrays.asList((Object[])obj);

            Object respObj = JSON.parse(rawApi.getResponse().getBody());
            List<Object> respObjList = Arrays.asList((Object[])respObj);

            if (objList.size() != respObjList.size()) {
                return;
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

        } catch (Exception e) {
            return;
        }
    }

}

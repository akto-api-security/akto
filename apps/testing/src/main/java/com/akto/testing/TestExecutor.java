package com.akto.testing;

import com.akto.DaoInit;
import com.akto.dao.AuthMechanismsDao;
import com.akto.dao.CustomAuthTypeDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.*;
import com.akto.dto.ApiInfo;
import com.akto.dto.CustomAuthType;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.RawApi;
import com.akto.dto.testing.*;
import com.akto.dto.testing.TestingRun.State;
import com.akto.dto.type.RequestTemplate;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.rules.*;
import com.akto.rules.SSRFOnAwsMetadataEndpoint;
import com.akto.store.SampleMessageStore;
import com.akto.store.TestingUtil;
import com.akto.testing_issues.TestingIssuesHandler;
import com.akto.util.Constants;
import com.akto.util.JSONUtils;
import com.akto.util.enums.GlobalEnums.TestSubCategory;
import com.akto.util.enums.LoginFlowEnums;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.types.ObjectId;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class TestExecutor {

    private static final LoggerMaker loggerMaker = new LoggerMaker(TestExecutor.class);
    public static long acceptableSizeInBytes = 5_000_000;



    public void init(TestingRun testingRun, ObjectId summaryId) {
        if (testingRun.getTestIdConfig() != 1) {
            apiWiseInit(testingRun, summaryId);
        } else {
            workflowInit(testingRun, summaryId);
        }
    }

    public static void main(String[] args) {
        DaoInit.init(new ConnectionString("mongodb://localhost:27017/admini"));
        Context.accountId.set(1_000_000);

        TestExecutor testExecutor = new TestExecutor();
        TestingRun testingRun = TestingRunDao.instance.findLatestOne(new BasicDBObject());
        if (testingRun.getTestIdConfig() > 1) {
            TestingRunConfig testingRunConfig = TestingRunConfigDao.instance.findOne(Constants.ID, testingRun.getTestIdConfig());
            if (testingRunConfig != null) {
                loggerMaker.infoAndAddToDb("Found testing run config with id :" + testingRunConfig.getId(), LogDb.TESTING);
                testingRun.setTestingRunConfig(testingRunConfig);
            }
        }
        testExecutor.init(testingRun, new ObjectId());
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

    public static AuthMechanism createAuthMechanism(){
        AuthMechanism authMechanism = AuthMechanismsDao.instance.findOne(new BasicDBObject());

        List<CustomAuthType> customAuthTypes = CustomAuthTypeDao.instance.findAll(CustomAuthType.ACTIVE,true);

        List<AuthParam> authParams = authMechanism.getAuthParams();

        Set<String> authParamKeys = new HashSet<>();

        for (AuthParam authParam : authParams) {
            authParamKeys.add(authParam.getKey());
        }

        for (CustomAuthType customAuthType : customAuthTypes) {
            List<String> customAuthTypeHeaderKeys = customAuthType.getHeaderKeys();
            for (String headerAuthKey: customAuthTypeHeaderKeys) {
                if (authParamKeys.contains(headerAuthKey)) {
                    continue;
                }
                authParams.add(new HardcodedAuthParam(AuthParam.Location.HEADER, headerAuthKey, null, true));
            }
            List<String> customAuthTypePayloadKeys = customAuthType.getPayloadKeys();
            for (String payloadAuthKey: customAuthTypePayloadKeys) {
                if (authParamKeys.contains(payloadAuthKey)) {
                    continue;
                }
                authParams.add(new HardcodedAuthParam(AuthParam.Location.BODY, payloadAuthKey, null, true));
            }
        }

        authMechanism.setAuthParams(authParams);
        return authMechanism;
    }

    public static TestingUtil createTestingUtil(TestingEndpoints testingEndpoints, AuthMechanism authMechanism){

        Map<String, SingleTypeInfo> singleTypeInfoMap = SampleMessageStore.buildSingleTypeInfoMap(testingEndpoints);
        Map<ApiInfo.ApiInfoKey, List<String>> sampleMessages = SampleMessageStore.fetchSampleMessages();
        List<TestRoles> testRoles = SampleMessageStore.fetchTestRoles();

        return new TestingUtil(authMechanism, sampleMessages, singleTypeInfoMap, testRoles);
    }

    public void apiWiseInit(TestingRun testingRun, ObjectId summaryId) {
        int accountId = Context.accountId.get();
        int now = Context.now();
        int maxConcurrentRequests = testingRun.getMaxConcurrentRequests() > 0 ? testingRun.getMaxConcurrentRequests() : 100;
        AuthMechanism authMechanism = createAuthMechanism();
        TestingEndpoints testingEndpoints = testingRun.getTestingEndpoints();

        TestingUtil testingUtil = createTestingUtil(testingEndpoints, authMechanism);

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

        List<ApiInfo.ApiInfoKey> apiInfoKeyList = testingEndpoints.returnApis();
        if (apiInfoKeyList == null || apiInfoKeyList.isEmpty()) return;
        loggerMaker.infoAndAddToDb("APIs found: " + apiInfoKeyList.size(), LogDb.TESTING);

        TestingRunResultSummariesDao.instance.updateOne(
            Filters.eq("_id", summaryId),
            Updates.set(TestingRunResultSummary.TOTAL_APIS, apiInfoKeyList.size()));

        CountDownLatch latch = new CountDownLatch(apiInfoKeyList.size());
        ExecutorService threadPool = Executors.newFixedThreadPool(maxConcurrentRequests);
        List<Future<List<TestingRunResult>>> futureTestingRunResults = new ArrayList<>();
        Map<String, Integer> hostsToApiCollectionMap = new HashMap<>();

        for (ApiInfo.ApiInfoKey apiInfoKey: apiInfoKeyList) {
            try {
                String host = findHost(apiInfoKey, testingUtil);
                if (host != null && hostsToApiCollectionMap.get(host) == null) {
                    hostsToApiCollectionMap.put(host, apiInfoKey.getApiCollectionId());
                }
            } catch (URISyntaxException e) {
                loggerMaker.errorAndAddToDb("Error while finding host: " + e, LogDb.TESTING);
            }
            try {
                 Future<List<TestingRunResult>> future = threadPool.submit(
                         () -> startWithLatch(apiInfoKey,
                                 testingRun.getTestIdConfig(),
                                 testingRun.getId(),testingRun.getTestingRunConfig(), testingUtil, summaryId,
                                 accountId, latch, now, testingRun.getTestRunTime()));
                 futureTestingRunResults.add(future);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error in API " + apiInfoKey + " : " + e.getMessage(), LogDb.TESTING);
            }
        }

        loggerMaker.infoAndAddToDb("hostsToApiCollectionMap : " + hostsToApiCollectionMap.keySet(), LogDb.TESTING);
        loggerMaker.infoAndAddToDb("Waiting...", LogDb.TESTING);

        try {
            latch.await();
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

        for (String host: hostsToApiCollectionMap.keySet()) {
            Integer apiCollectionId = hostsToApiCollectionMap.get(host);
            List<TestingRunResult> nucleiResults = runNucleiTests(new ApiInfo.ApiInfoKey(apiCollectionId, host, URLMethods.Method.GET), testingRun, testingUtil, summaryId);
            if (nucleiResults != null && !nucleiResults.isEmpty()) {
                testingRunResults.addAll(nucleiResults);
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
                int initialCount = totalCountIssues.get("HIGH");
                totalCountIssues.put("HIGH", initialCount + 1);
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

    public static String findHost(ApiInfo.ApiInfoKey apiInfoKey, TestingUtil testingUtil) throws URISyntaxException {
        Map<ApiInfo.ApiInfoKey, List<String>> sampleMessagesMap =  testingUtil.getSampleMessages();
        List<String> sampleMessages = sampleMessagesMap.get(apiInfoKey);
        if (sampleMessages == null || sampleMessagesMap.isEmpty()) return null;

        List<RawApi> messages = SampleMessageStore.fetchAllOriginalMessages(apiInfoKey, testingUtil.getSampleMessages());
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


    public List<TestingRunResult> runNucleiTests(ApiInfo.ApiInfoKey apiInfoKey, TestingRun testingRun, TestingUtil testingUtil, ObjectId summaryId) {
        List<TestingRunResult> testingRunResults = new ArrayList<>();
        List<String> testSubCategories = testingRun.getTestingRunConfig().getTestSubCategoryList();
        if (testSubCategories != null) {
            for (String testSubCategory: testSubCategories) {
                if (testSubCategory.startsWith("http://") || testSubCategory.startsWith("https://")) {
                    try {
                        String origTemplateURL = testSubCategory;
                        origTemplateURL = origTemplateURL.replace("https://github.com/", "https://raw.githubusercontent.com/").replace("/blob/", "/");
                        String subcategory = origTemplateURL.substring(origTemplateURL.lastIndexOf("/")+1).split("\\.")[0];

                        FuzzingTest fuzzingTest = new FuzzingTest(testingRun.getId().toHexString(), summaryId.toHexString(), origTemplateURL, subcategory, testSubCategory, null);
                        TestingRunResult fuzzResult = runTest(fuzzingTest, apiInfoKey, testingUtil, testingRun.getId(), summaryId);
                        if (fuzzResult != null) {
                            trim(fuzzResult);
                            TestingRunResultDao.instance.insertOne(fuzzResult);
                            TestingIssuesHandler handler = new TestingIssuesHandler();
                            handler.handleIssuesCreationFromTestingRunResults(Collections.singletonList(fuzzResult));
                            testingRunResults.add(fuzzResult);
                        }
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb("unable to execute fuzzing for " + testSubCategory, LogDb.TESTING);
                    }
                }
            }
        }

        return testingRunResults;
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
            respMap.put(param, flattened.get(param));
        }

        for (String headerName: headers.keySet()) {
            for (String val: headers.get(headerName)) {
                respMap.put(headerName, val);
            }
        }
        return respMap;
    }

    public List<TestingRunResult> startWithLatch(
            ApiInfo.ApiInfoKey apiInfoKey, int testIdConfig, ObjectId testRunId, TestingRunConfig testingRunConfig,
            TestingUtil testingUtil, ObjectId testRunResultSummaryId, int accountId, CountDownLatch latch, int startTime, int timeToKill) {

        loggerMaker.infoAndAddToDb("Starting test for " + apiInfoKey, LogDb.TESTING);

        Context.accountId.set(accountId);
        List<TestingRunResult> testingRunResults = new ArrayList<>();
        int now = Context.now();
        if ( timeToKill <= 0 || now - startTime <= timeToKill) {
            try {
                testingRunResults = start(apiInfoKey, testIdConfig, testRunId, testingRunConfig, testingUtil, testRunResultSummaryId);
                String size = testingRunResults == null ? "null" : testingRunResults.size()+"";
                loggerMaker.infoAndAddToDb("testingRunResults size: " + size, LogDb.TESTING);
                if (testingRunResults != null && !testingRunResults.isEmpty()) {
                    trim(testingRunResults);
                    TestingRunResultDao.instance.insertMany(testingRunResults);
                    loggerMaker.infoAndAddToDb("Inserted testing results", LogDb.TESTING);
                    //Creating issues from testingRunResults
                   TestingIssuesHandler handler = new TestingIssuesHandler();
                   handler.handleIssuesCreationFromTestingRunResults(testingRunResults);
                }
            } catch (Exception e) {
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

    public List<TestingRunResult> start(ApiInfo.ApiInfoKey apiInfoKey, int testIdConfig, ObjectId testRunId,
                                        TestingRunConfig testingRunConfig, TestingUtil testingUtil, ObjectId testRunResultSummaryId) {

        if (testIdConfig == 1) {
            loggerMaker.errorAndAddToDb("Test id config is 1", LogDb.TESTING);
            return new ArrayList<>();
        }

        List<String> testSubCategories = testingRunConfig == null ? null : testingRunConfig.getTestSubCategoryList();

        BOLATest bolaTest = new BOLATest();//REPLACE_AUTH_TOKEN
        NoAuthTest noAuthTest = new NoAuthTest();//REMOVE_TOKENS
        ChangeHttpMethodTest changeHttpMethodTest = new ChangeHttpMethodTest();//CHANGE_METHOD
        AddMethodInParameterTest addMethodInParameterTest = new AddMethodInParameterTest();//ADD_METHOD_IN_PARAMETER
        AddMethodOverrideHeadersTest addMethodOverrideHeadersTest = new AddMethodOverrideHeadersTest();//ADD_METHOD_OVERRIDE_HEADERS
        AddUserIdTest addUserIdTest = new AddUserIdTest();//ADD_USER_ID
        ParameterPollutionTest parameterPollutionTest = new ParameterPollutionTest();//PARAMETER_POLLUTION
        OldApiVersionTest oldApiVersionTest = new OldApiVersionTest();//REPLACE_AUTH_TOKEN_OLD_VERSION
        JWTNoneAlgoTest  jwtNoneAlgoTest = new JWTNoneAlgoTest();//JWT_NONE_ALGO
        JWTInvalidSignatureTest jwtInvalidSignatureTest = new JWTInvalidSignatureTest();//JWT_INVALID_SIGNATURE
        AddJkuToJwtTest addJkuToJwtTest = new AddJkuToJwtTest();//ADD_JKU_TO_JWT
        BFLATest bflaTest = new BFLATest();//BFLA
        PageSizeDosTest pageSizeDosTest = new PageSizeDosTest(testRunId.toHexString(), testRunResultSummaryId.toHexString());//PAGE_SIZE_DOS
        OpenRedirectTest openRedirectTest = new OpenRedirectTest(testRunId.toHexString(), testRunResultSummaryId.toHexString());
        SSRFOnAwsMetadataEndpoint ssrfOnAwsMetadataEndpoint = new SSRFOnAwsMetadataEndpoint(testRunId.toHexString(), testRunResultSummaryId.toHexString());
        CreateAdminUserViaMassAssignment createAdminUserViaMassAssignment = new CreateAdminUserViaMassAssignment(testRunId.toHexString(), testRunResultSummaryId.toHexString());

        List<TestingRunResult> testingRunResults = new ArrayList<>();

        TestingRunResult noAuthTestResult = runTest(noAuthTest, apiInfoKey, testingUtil, testRunId, testRunResultSummaryId);
        if (noAuthTestResult != null) {
            testingRunResults.add(noAuthTestResult);
        } else {
            loggerMaker.infoAndAddToDb("No auth result is null for " + apiInfoKey, LogDb.TESTING);
        }
        if (noAuthTestResult != null && !noAuthTestResult.isVulnerable()) {

            TestPlugin.TestRoleMatcher testRoleMatcher = new TestPlugin.TestRoleMatcher(testingUtil.getTestRoles(), apiInfoKey);
            loggerMaker.infoAndAddToDb("Starting auth required tests for " + apiInfoKey, LogDb.TESTING);
            if ((testSubCategories == null || testSubCategories.contains(TestSubCategory.BFLA.name())) && testRoleMatcher.shouldDoBFLA())  {
                TestingRunResult bflaTestResult = runTest(bflaTest, apiInfoKey, testingUtil, testRunId, testRunResultSummaryId);
                if (bflaTestResult != null) testingRunResults.add(bflaTestResult);
            } else if (testSubCategories == null || testSubCategories.contains(TestSubCategory.REPLACE_AUTH_TOKEN.name())){
                TestingRunResult bolaTestResult = runTest(bolaTest, apiInfoKey, testingUtil, testRunId, testRunResultSummaryId);
                if (bolaTestResult != null) testingRunResults.add(bolaTestResult);
            }

            if (testSubCategories == null || testSubCategories.contains(TestSubCategory.ADD_USER_ID.name())) {
                TestingRunResult addUserIdTestResult = runTest(addUserIdTest, apiInfoKey, testingUtil, testRunId, testRunResultSummaryId);
                if (addUserIdTestResult != null) testingRunResults.add(addUserIdTestResult);
            }

            if (testSubCategories == null || testSubCategories.contains(TestSubCategory.PARAMETER_POLLUTION.name())) {
                TestingRunResult parameterPollutionTestResult = runTest(parameterPollutionTest, apiInfoKey, testingUtil, testRunId, testRunResultSummaryId);
                if (parameterPollutionTestResult != null) testingRunResults.add(parameterPollutionTestResult);
            }

            if (testSubCategories == null || testSubCategories.contains(TestSubCategory.REPLACE_AUTH_TOKEN_OLD_VERSION.name())) {
                TestingRunResult oldApiVersionTestResult = runTest(oldApiVersionTest, apiInfoKey, testingUtil, testRunId, testRunResultSummaryId);
                if (oldApiVersionTestResult != null) testingRunResults.add(oldApiVersionTestResult);
            }

            if (testSubCategories == null || testSubCategories.contains(TestSubCategory.JWT_NONE_ALGO.name())) {
                TestingRunResult jwtNoneAlgoTestResult = runTest(jwtNoneAlgoTest, apiInfoKey, testingUtil, testRunId, testRunResultSummaryId);
                if (jwtNoneAlgoTestResult != null) testingRunResults.add(jwtNoneAlgoTestResult);
            }

            if (testSubCategories == null || testSubCategories.contains(TestSubCategory.JWT_INVALID_SIGNATURE.name())) {
                TestingRunResult jwtInvalidSignatureTestResult = runTest(jwtInvalidSignatureTest, apiInfoKey, testingUtil, testRunId, testRunResultSummaryId);
                if (jwtInvalidSignatureTestResult != null) testingRunResults.add(jwtInvalidSignatureTestResult);
            }

            if (testSubCategories == null || testSubCategories.contains(TestSubCategory.ADD_JKU_TO_JWT.name())) {
                TestingRunResult addJkuToJwtTestResult = runTest(addJkuToJwtTest, apiInfoKey, testingUtil, testRunId, testRunResultSummaryId);
                if (addJkuToJwtTestResult != null) testingRunResults.add(addJkuToJwtTestResult);
            }
        }

       if(testSubCategories == null || testSubCategories.contains(TestSubCategory.PAGINATION_MISCONFIGURATION.name())) {
            TestingRunResult paginationMisconfigurationTestResult = runTest(pageSizeDosTest, apiInfoKey, testingUtil, testRunId, testRunResultSummaryId);
            if (paginationMisconfigurationTestResult != null) testingRunResults.add(paginationMisconfigurationTestResult);
        }

        if (testSubCategories == null || testSubCategories.contains(TestSubCategory.ADD_METHOD_IN_PARAMETER.name())) {
            TestingRunResult addMethodInParameterTestResult = runTest(addMethodInParameterTest, apiInfoKey, testingUtil, testRunId, testRunResultSummaryId);
            if (addMethodInParameterTestResult != null) testingRunResults.add(addMethodInParameterTestResult);
        }

        if (testSubCategories == null || testSubCategories.contains(TestSubCategory.ADD_METHOD_OVERRIDE_HEADERS.name())) {
            TestingRunResult addMethodOverrideHeadersTestResult = runTest(addMethodOverrideHeadersTest, apiInfoKey, testingUtil, testRunId, testRunResultSummaryId);
            if (addMethodOverrideHeadersTestResult != null) testingRunResults.add(addMethodOverrideHeadersTestResult);
        }

        if (testSubCategories == null || testSubCategories.contains(TestSubCategory.CHANGE_METHOD.name())) {
            TestingRunResult changeHttpMethodTestResult = runTest(changeHttpMethodTest, apiInfoKey, testingUtil, testRunId, testRunResultSummaryId);
            if (changeHttpMethodTestResult != null) testingRunResults.add(changeHttpMethodTestResult);
        }

        if (testSubCategories == null || testSubCategories.contains(TestSubCategory.OPEN_REDIRECT.name())) {
            TestingRunResult openRedirectResult = runTest(openRedirectTest, apiInfoKey, testingUtil, testRunId, testRunResultSummaryId);
            if (openRedirectResult != null) testingRunResults.add(openRedirectResult);
        }

        if (testSubCategories == null || testSubCategories.contains(TestSubCategory.SSRF_AWS_METADATA_EXPOSED.name())) {
            TestingRunResult ssrfAwsMetadataExposedResult = runTest(ssrfOnAwsMetadataEndpoint, apiInfoKey, testingUtil, testRunId, testRunResultSummaryId);
            if (ssrfAwsMetadataExposedResult != null)
                testingRunResults.add(ssrfAwsMetadataExposedResult);
        }

        if(testSubCategories == null || testSubCategories.contains(TestSubCategory.MASS_ASSIGNMENT_CREATE_ADMIN_ROLE.name())) {
            TestingRunResult createAdminUserViaMassAssignmentResult = runTest(createAdminUserViaMassAssignment, apiInfoKey, testingUtil, testRunId, testRunResultSummaryId);
            if (createAdminUserViaMassAssignmentResult != null) testingRunResults.add(createAdminUserViaMassAssignmentResult);
        }

        return testingRunResults;
    }

    public TestingRunResult runTest(TestPlugin testPlugin, ApiInfo.ApiInfoKey apiInfoKey, TestingUtil testingUtil, ObjectId testRunId, ObjectId testRunResultSummaryId) {

        int startTime = Context.now();
        TestPlugin.Result result = testPlugin.start(apiInfoKey, testingUtil);
        if (result == null) return null;
        int endTime = Context.now();

        String subTestName = testPlugin.subTestName();

        if (testPlugin instanceof FuzzingTest) {
            FuzzingTest test = (FuzzingTest) testPlugin;
            subTestName = test.getTestSourceConfigCategory();
        }

        return new TestingRunResult(
                testRunId, apiInfoKey, testPlugin.superTestName(), subTestName, result.testResults,
                result.isVulnerable,result.singleTypeInfos, result.confidencePercentage,
                startTime, endTime, testRunResultSummaryId
        );
    }

}

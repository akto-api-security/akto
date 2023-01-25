package com.akto.testing;

import com.akto.DaoInit;
import com.akto.dao.AuthMechanismsDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing.TestingRunResultSummariesDao;
import com.akto.dao.testing.WorkflowTestsDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.testing.*;
import com.akto.dto.testing.TestingRun.State;
import com.akto.dto.type.RequestTemplate;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.log.LoggerMaker;
import com.akto.rules.*;
import com.akto.store.SampleMessageStore;
import com.akto.store.TestingUtil;
import com.akto.testing_issues.TestingIssuesHandler;
import com.akto.util.JSONUtils;
import com.akto.util.enums.LoginFlowEnums;
import com.akto.util.enums.GlobalEnums.TestSubCategory;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.types.ObjectId;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

public class TestExecutor {

    private static final LoggerMaker loggerMaker = new LoggerMaker(TestExecutor.class);
    private static final Logger logger = LoggerFactory.getLogger(TestExecutor.class);

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
        TestingRun testingRun = TestingRunDao.instance.findOne(new BasicDBObject());
        testExecutor.init(testingRun, new ObjectId());
    }

    public void workflowInit (TestingRun testingRun, ObjectId summaryId) {
        TestingEndpoints testingEndpoints = testingRun.getTestingEndpoints();
        if (!testingEndpoints.getType().equals(TestingEndpoints.Type.WORKFLOW)) {
            loggerMaker.errorAndAddToDb("Invalid workflow type");
            return;
        }

        WorkflowTestingEndpoints workflowTestingEndpoints = (WorkflowTestingEndpoints) testingEndpoints;
        WorkflowTest workflowTestOld = workflowTestingEndpoints.getWorkflowTest();

        WorkflowTest workflowTest = WorkflowTestsDao.instance.findOne(
                Filters.eq("_id", workflowTestOld.getId())
        );

        if (workflowTest == null) {
            loggerMaker.errorAndAddToDb("Workflow test has been deleted");
            return ;
        }

        ApiWorkflowExecutor apiWorkflowExecutor = new ApiWorkflowExecutor();
        try {
            apiWorkflowExecutor.init(workflowTest, testingRun.getId(), summaryId);
        } catch (Exception e) {
            e.printStackTrace();
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

    public void apiWiseInit(TestingRun testingRun, ObjectId summaryId) {
        int accountId = Context.accountId.get();
        int now = Context.now();
        int maxConcurrentRequests = testingRun.getMaxConcurrentRequests() > 0 ? testingRun.getMaxConcurrentRequests() : 100;
        TestingEndpoints testingEndpoints = testingRun.getTestingEndpoints();

        Map<String, SingleTypeInfo> singleTypeInfoMap = SampleMessageStore.buildSingleTypeInfoMap(testingEndpoints);
        Map<ApiInfo.ApiInfoKey, List<String>> sampleMessages = SampleMessageStore.fetchSampleMessages();
        List<TestRoles> testRoles = SampleMessageStore.fetchTestRoles();
        AuthMechanism authMechanism = AuthMechanismsDao.instance.findOne(new BasicDBObject());

        TestingUtil testingUtil = new TestingUtil(authMechanism, sampleMessages, singleTypeInfoMap, testRoles);

        try {
            LoginFlowResponse loginFlowResponse = triggerLoginFlow(authMechanism, 3);
            if (!loginFlowResponse.getSuccess()) {
                logger.error("login flow failed");
                throw new Exception("login flow failed");
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e.getMessage());
            return;
        }

        List<ApiInfo.ApiInfoKey> apiInfoKeyList = testingEndpoints.returnApis();
        if (apiInfoKeyList == null || apiInfoKeyList.isEmpty()) return;
        System.out.println("APIs: " + apiInfoKeyList.size());
        loggerMaker.infoAndAddToDb("APIs found: " + apiInfoKeyList.size());

        TestingRunResultSummariesDao.instance.updateOne(
            Filters.eq("_id", summaryId),
            Updates.set(TestingRunResultSummary.TOTAL_APIS, apiInfoKeyList.size()));

        CountDownLatch latch = new CountDownLatch(apiInfoKeyList.size());
        ExecutorService threadPool = Executors.newFixedThreadPool(maxConcurrentRequests);
        List<Future<List<TestingRunResult>>> futureTestingRunResults = new ArrayList<>();
        for (ApiInfo.ApiInfoKey apiInfoKey: apiInfoKeyList) {
            try {
                 Future<List<TestingRunResult>> future = threadPool.submit(
                         () -> startWithLatch(apiInfoKey,
                                 testingRun.getTestIdConfig(),
                                 testingRun.getId(),testingRun.getTestingRunConfig(), testingUtil, summaryId,
                                 accountId, latch, now, testingRun.getTestRunTime()));
                 futureTestingRunResults.add(future);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error in API " + apiInfoKey + " : " + e.getMessage());
            }
        }

        loggerMaker.infoAndAddToDb("Waiting...");

        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        loggerMaker.infoAndAddToDb("Finished testing");

        List<TestingRunResult> testingRunResults = new ArrayList<>();
        for (Future<List<TestingRunResult>> future: futureTestingRunResults) {
            if (!future.isDone()) continue;
            try {
                if (!future.get().isEmpty()) {
                    testingRunResults.addAll(future.get());
                }
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        loggerMaker.infoAndAddToDb("Finished adding " + testingRunResults.size() + " testingRunResults");

        TestingRunResultSummariesDao.instance.updateOne(
            Filters.eq("_id", summaryId),
            Updates.set(TestingRunResultSummary.TEST_RESULTS_COUNT, testingRunResults.size())
        );

        //Creating issues from testingRunResults
        TestingIssuesHandler handler = new TestingIssuesHandler();
        handler.handleIssuesCreationFromTestingRunResults(testingRunResults);

        loggerMaker.infoAndAddToDb("Finished adding issues");

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

        loggerMaker.infoAndAddToDb("Finished updating TestingRunResultSummariesDao");

    }

    private LoginFlowResponse triggerLoginFlow(AuthMechanism authMechanism, int retries) {
        LoginFlowResponse loginFlowResponse = null;
        for (int i=0; i<retries; i++) {
            try {
                loginFlowResponse = executeLoginFlow(authMechanism, null);
                if (loginFlowResponse.getSuccess()) {
                    logger.info("login flow success");
                    break;
                }
            } catch (Exception e) {
                logger.error("retrying login flow" + e.getMessage());
                loggerMaker.errorAndAddToDb(e.getMessage());
            }
        }
        return loginFlowResponse;
    }

    public LoginFlowResponse executeLoginFlow(AuthMechanism authMechanism, LoginFlowParams loginFlowParams) throws Exception {

        if (authMechanism.getType() == null) {
            logger.info("auth type value is null");
            return new LoginFlowResponse(null, null, true);
        }

        if (!authMechanism.getType().equals(LoginFlowEnums.AuthMechanismTypes.LOGIN_REQUEST.toString())) {
            logger.info("invalid auth type for login flow execution");
            return new LoginFlowResponse(null, null, true);
        }

        logger.info("login flow execution started");

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
            TestingUtil testingUtil, ObjectId testRunResultSummaryId, int accountId, CountDownLatch latch, int startTime, int timeToKill) {

        Context.accountId.set(accountId);
        List<TestingRunResult> testingRunResults = new ArrayList<>();
        int now = Context.now();
        if ( timeToKill <= 0 || now - startTime <= timeToKill) {
            try {
                testingRunResults = start(apiInfoKey, testIdConfig, testRunId, testingRunConfig, testingUtil, testRunResultSummaryId);
                TestingRunResultDao.instance.insertMany(testingRunResults);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        latch.countDown();
        return testingRunResults;
    }

    public List<TestingRunResult> start(ApiInfo.ApiInfoKey apiInfoKey, int testIdConfig, ObjectId testRunId,
                                        TestingRunConfig testingRunConfig, TestingUtil testingUtil, ObjectId testRunResultSummaryId) {

        if (testIdConfig == 1) {
            loggerMaker.errorAndAddToDb("Test id config is 1");
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

        List<TestingRunResult> testingRunResults = new ArrayList<>();

        TestingRunResult noAuthTestResult = runTest(noAuthTest, apiInfoKey, testingUtil, testRunId, testRunResultSummaryId);
        if (noAuthTestResult != null) testingRunResults.add(noAuthTestResult);
        if (noAuthTestResult != null && !noAuthTestResult.isVulnerable()) {

            TestPlugin.TestRoleMatcher testRoleMatcher = new TestPlugin.TestRoleMatcher(testingUtil.getTestRoles(), apiInfoKey);
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

        if (testSubCategories != null) {
            for (String testSubCategory: testSubCategories) {
                if (testSubCategory.startsWith("http://") || testSubCategory.startsWith("https://")) {
                    try {
                        String origTemplateURL = testSubCategory;

                        origTemplateURL = origTemplateURL.replace("https://github.com/", "https://raw.githubusercontent.com/").replace("/blob/", "/");

                        String subcategory = origTemplateURL.substring(origTemplateURL.lastIndexOf("/")+1).split("\\.")[0];
                        FuzzingTest fuzzingTest = new FuzzingTest(testRunId.toHexString(), testRunResultSummaryId.toHexString(), origTemplateURL, subcategory);
                        TestingRunResult fuzzResult = runTest(fuzzingTest, apiInfoKey, testingUtil, testRunId, testRunResultSummaryId);
                        if (fuzzResult != null) testingRunResults.add(fuzzResult);        
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb("unable to execute fuzzing for " + testSubCategory);
                        e.printStackTrace();
                    }
                }
            }
        }

        return testingRunResults;
    }

    public TestingRunResult runTest(TestPlugin testPlugin, ApiInfo.ApiInfoKey apiInfoKey, TestingUtil testingUtil, ObjectId testRunId, ObjectId testRunResultSummaryId) {

        int startTime = Context.now();
        TestPlugin.Result result = testPlugin.start(apiInfoKey, testingUtil);
        if (result == null) return null;
        int endTime = Context.now();

        return new TestingRunResult(
                testRunId, apiInfoKey, testPlugin.superTestName(), testPlugin.subTestName(), result.testResults,
                result.isVulnerable,result.singleTypeInfos, result.confidencePercentage,
                startTime, endTime, testRunResultSummaryId
        );
    }

}

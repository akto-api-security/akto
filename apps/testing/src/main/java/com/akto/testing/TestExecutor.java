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
import com.akto.dto.type.SingleTypeInfo;
import com.akto.rules.*;
import com.akto.store.SampleMessageStore;
import com.akto.testing_issues.TestingIssuesHandler;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class TestExecutor {

    private static final Logger logger = LoggerFactory.getLogger(TestExecutor.class);

    public void init(TestingRun testingRun, ObjectId summaryId) {
        if (testingRun.getTestIdConfig() == 0)     {
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
            logger.error("Invalid workflow type");
            return;
        }

        WorkflowTestingEndpoints workflowTestingEndpoints = (WorkflowTestingEndpoints) testingEndpoints;
        WorkflowTest workflowTestOld = workflowTestingEndpoints.getWorkflowTest();

        WorkflowTest workflowTest = WorkflowTestsDao.instance.findOne(
                Filters.eq("_id", workflowTestOld.getId())
        );

        if (workflowTest == null) {
            logger.error("Workflow test has been deleted");
            return ;
        }

        ApiWorkflowExecutor apiWorkflowExecutor = new ApiWorkflowExecutor();
        apiWorkflowExecutor.init(workflowTest, testingRun.getId());
    }

    public void  apiWiseInit(TestingRun testingRun, ObjectId summaryId) {
        int accountId = Context.accountId.get();
        TestingEndpoints testingEndpoints = testingRun.getTestingEndpoints();

        Map<String, SingleTypeInfo> singleTypeInfoMap = SampleMessageStore.buildSingleTypeInfoMap(testingEndpoints);
        Map<ApiInfo.ApiInfoKey, List<String>> sampleMessages = SampleMessageStore.fetchSampleMessages();
        AuthMechanism authMechanism = AuthMechanismsDao.instance.findOne(new BasicDBObject());

        List<ApiInfo.ApiInfoKey> apiInfoKeyList = testingEndpoints.returnApis();
        if (apiInfoKeyList == null || apiInfoKeyList.isEmpty()) return;
        System.out.println("APIs: " + apiInfoKeyList.size());

        TestingRunResultSummariesDao.instance.updateOne(
            Filters.eq("_id", summaryId),
            Updates.set(TestingRunResultSummary.TOTAL_APIS, apiInfoKeyList.size())
        );

        CountDownLatch latch = new CountDownLatch(apiInfoKeyList.size());
        ExecutorService threadPool = Executors.newFixedThreadPool(100);

        List<Future<List<TestingRunResult>>> futureTestingRunResults = new ArrayList<>();
        for (ApiInfo.ApiInfoKey apiInfoKey: apiInfoKeyList) {
            try {
                 Future<List<TestingRunResult>> future = threadPool.submit(() -> startWithLatch(apiInfoKey, testingRun.getTestIdConfig(), testingRun.getId(), singleTypeInfoMap, sampleMessages, authMechanism, summaryId, accountId, latch));
                 futureTestingRunResults.add(future);
            } catch (Exception e) {
                logger.error(e.getMessage());
            }
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        List<TestingRunResult> testingRunResults = new ArrayList<>();
        for (Future<List<TestingRunResult>> future: futureTestingRunResults) {
            if (!future.isDone()) continue;
            try {
                testingRunResults.addAll(future.get());
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        TestingRunResultDao.instance.insertMany(testingRunResults);

        TestingRunResultSummariesDao.instance.updateOne(
            Filters.eq("_id", summaryId),
            Updates.set(TestingRunResultSummary.TEST_RESULTS_COUNT, testingRunResults.size())
        );

        //Creating issues from testingRunResults
        TestingIssuesHandler handler = new TestingIssuesHandler();
        handler.handleIssuesCreationFromTestingRunResults(testingRunResults);

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
    }

    public List<TestingRunResult> startWithLatch(
            ApiInfo.ApiInfoKey apiInfoKey, int testIdConfig, ObjectId testRunId,
            Map<String, SingleTypeInfo> singleTypeInfoMap, Map<ApiInfo.ApiInfoKey, List<String>> sampleMessages,
            AuthMechanism authMechanism, ObjectId testRunResultSummaryId, int accountId,
            CountDownLatch latch) {

        Context.accountId.set(accountId);
        List<TestingRunResult> testingRunResults = new ArrayList<>();

        try {
            testingRunResults = start(apiInfoKey, testIdConfig, testRunId, singleTypeInfoMap, sampleMessages, authMechanism, testRunResultSummaryId);
        } catch (Exception e) {
            e.printStackTrace();
        }

        latch.countDown();
        return testingRunResults;
    }

    public List<TestingRunResult> start(ApiInfo.ApiInfoKey apiInfoKey, int testIdConfig, ObjectId testRunId,
                                      Map<String, SingleTypeInfo> singleTypeInfoMap, Map<ApiInfo.ApiInfoKey, List<String>> sampleMessages,
                                      AuthMechanism authMechanism, ObjectId testRunResultSummaryId) {

        if (testIdConfig != 0) {
            logger.error("Test id config is not 0 but " + testIdConfig);
            return new ArrayList<>();
        }

        BOLATest bolaTest = new BOLATest();
        NoAuthTest noAuthTest = new NoAuthTest();
        ChangeHttpMethodTest changeHttpMethodTest = new ChangeHttpMethodTest();
        AddMethodInParameterTest addMethodInParameterTest = new AddMethodInParameterTest();
        AddMethodOverrideHeadersTest addMethodOverrideHeadersTest = new AddMethodOverrideHeadersTest();
        AddUserIdTest addUserIdTest = new AddUserIdTest();
        ParameterPollutionTest parameterPollutionTest = new ParameterPollutionTest();
        OldApiVersionTest oldApiVersionTest = new OldApiVersionTest();
        JWTNoneAlgoTest  jwtNoneAlgoTest = new JWTNoneAlgoTest();

        List<TestingRunResult> testingRunResults = new ArrayList<>();
        TestingRunResult noAuthTestResult = runTest(noAuthTest, apiInfoKey, authMechanism, sampleMessages, singleTypeInfoMap, testRunId, testRunResultSummaryId);
        if (noAuthTestResult != null) testingRunResults.add(noAuthTestResult);
        if (noAuthTestResult != null && !noAuthTestResult.isVulnerable()) {
            TestingRunResult bolaTestResult = runTest(bolaTest, apiInfoKey, authMechanism, sampleMessages, singleTypeInfoMap, testRunId, testRunResultSummaryId);
            if (bolaTestResult != null) testingRunResults.add(bolaTestResult);

            TestingRunResult addUserIdTestResult = runTest(addUserIdTest, apiInfoKey, authMechanism, sampleMessages, singleTypeInfoMap, testRunId, testRunResultSummaryId);
            if (addUserIdTestResult != null) testingRunResults.add(addUserIdTestResult);

            TestingRunResult parameterPollutionTestResult = runTest(parameterPollutionTest, apiInfoKey, authMechanism, sampleMessages, singleTypeInfoMap, testRunId, testRunResultSummaryId);
            if (parameterPollutionTestResult != null) testingRunResults.add(parameterPollutionTestResult);

            TestingRunResult oldApiVersionTestResult = runTest(oldApiVersionTest, apiInfoKey, authMechanism, sampleMessages, singleTypeInfoMap, testRunId, testRunResultSummaryId);
            if (oldApiVersionTestResult != null) testingRunResults.add(oldApiVersionTestResult);

            TestingRunResult jwtNoneAlgoTestResult = runTest(jwtNoneAlgoTest, apiInfoKey, authMechanism, sampleMessages, singleTypeInfoMap, testRunId, testRunResultSummaryId);
            if (jwtNoneAlgoTestResult != null) testingRunResults.add(jwtNoneAlgoTestResult);
        }

        TestingRunResult addMethodInParameterTestResult = runTest(addMethodInParameterTest, apiInfoKey, authMechanism, sampleMessages, singleTypeInfoMap, testRunId, testRunResultSummaryId);
        if (addMethodInParameterTestResult != null) testingRunResults.add(addMethodInParameterTestResult);

        TestingRunResult addMethodOverrideHeadersTestResult = runTest(addMethodOverrideHeadersTest, apiInfoKey, authMechanism, sampleMessages, singleTypeInfoMap, testRunId, testRunResultSummaryId);
        if (addMethodOverrideHeadersTestResult != null) testingRunResults.add(addMethodOverrideHeadersTestResult);

        TestingRunResult changeHttpMethodTestResult = runTest(changeHttpMethodTest, apiInfoKey, authMechanism, sampleMessages, singleTypeInfoMap, testRunId, testRunResultSummaryId);
        if (changeHttpMethodTestResult != null) testingRunResults.add(changeHttpMethodTestResult);



        return testingRunResults;
    }

    public TestingRunResult runTest(TestPlugin testPlugin, ApiInfo.ApiInfoKey apiInfoKey, AuthMechanism authMechanism, Map<ApiInfo.ApiInfoKey, List<String>> sampleMessages,
                        Map<String, SingleTypeInfo> singleTypeInfos, ObjectId testRunId, ObjectId testRunResultSummaryId) {

        int startTime = Context.now();
        TestPlugin.Result result = testPlugin.start(apiInfoKey, authMechanism, sampleMessages, singleTypeInfos);
        if (result == null) return null;
        int endTime = Context.now();

        return new TestingRunResult(
                testRunId, apiInfoKey, testPlugin.superTestName(), testPlugin.subTestName(), result.testResults,
                result.isVulnerable,result.singleTypeInfos, result.confidencePercentage,
                startTime, endTime, testRunResultSummaryId
        );
    }

}

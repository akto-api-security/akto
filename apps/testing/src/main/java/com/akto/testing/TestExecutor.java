package com.akto.testing;

import com.akto.dao.AuthMechanismsDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing.TestingRunResultSummariesDao;
import com.akto.dao.testing.WorkflowTestsDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.*;
import com.akto.dto.testing.TestingRun.State;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.rules.*;
import com.akto.store.SampleMessageStore;
import com.akto.testing_utils.TestingUtils;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import com.mongodb.client.result.UpdateResult;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.akto.util.Constants.ID;

public class TestExecutor {

    private static final Logger logger = LoggerFactory.getLogger(TestExecutor.class);

    public void init(TestingRun testingRun, ObjectId summaryId) {
        if (testingRun.getTestIdConfig() == 0)     {
            apiWiseInit(testingRun, summaryId);
        } else {
            workflowInit(testingRun, summaryId);
        }
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

        List<TestingIssuesId> issuesIds = TestingUtils.listOfIssuesIdsFromTestingRunResults(testingRunResults);

        int now = Context.now();
        Bson query = Filters.in(ID, issuesIds);
        Bson update = Updates.combine(
                Updates.set(TestingRunIssues.LAST_SEEN,now),
                Updates.set(TestingRunIssues.TEST_RUN_ISSUES_STATUS, GlobalEnums.TestRunIssueStatus.OPEN),
                Updates.setOnInsert(TestingRunIssues.CREATION_TIME, now),
                Updates.setOnInsert(TestingRunIssues.SEVERITY, GlobalEnums.Severity.HIGH)
        );

        UpdateResult result = TestingRunIssuesDao.instance.updateMany(query, update);
        logger.info("Inserted records : {}", result.getMatchedCount());
        logger.info("upsert Ids : {}", result.getUpsertedId());


        TestingRunResultDao.instance.insertMany(testingRunResults);

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
                    Updates.set(TestingRunResultSummary.COUNT_ISSUES, totalCountIssues),
                    Updates.set(TestingRunResultSummary.TOTAL_APIS, apiInfoKeyList.size())
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

        List<TestingRunResult> testingRunResults = new ArrayList<>();
        TestingRunResult noAuthTestResult = runTest(noAuthTest, apiInfoKey, authMechanism, sampleMessages, singleTypeInfoMap, testRunId, testRunResultSummaryId);
        testingRunResults.add(noAuthTestResult);
        if (!noAuthTestResult.isVulnerable()) {
            TestingRunResult bolaTestResult = runTest(bolaTest, apiInfoKey, authMechanism, sampleMessages, singleTypeInfoMap, testRunId, testRunResultSummaryId);
            testingRunResults.add(bolaTestResult);
        }

        TestingRunResult addMethodInParameterTestResult = runTest(addMethodInParameterTest, apiInfoKey, authMechanism, sampleMessages, singleTypeInfoMap, testRunId, testRunResultSummaryId);
        testingRunResults.add(addMethodInParameterTestResult);

        TestingRunResult addMethodOverrideHeadersTestResult = runTest(addMethodOverrideHeadersTest, apiInfoKey, authMechanism, sampleMessages, singleTypeInfoMap, testRunId, testRunResultSummaryId);
        testingRunResults.add(addMethodOverrideHeadersTestResult);

        TestingRunResult changeHttpMethodTestResult = runTest(changeHttpMethodTest, apiInfoKey, authMechanism, sampleMessages, singleTypeInfoMap, testRunId, testRunResultSummaryId);
        testingRunResults.add(changeHttpMethodTestResult);

        TestingRunResult addUserIdTestResult = runTest(addUserIdTest, apiInfoKey, authMechanism, sampleMessages, singleTypeInfoMap, testRunId, testRunResultSummaryId);
        if (addUserIdTestResult != null) testingRunResults.add(addUserIdTestResult);

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

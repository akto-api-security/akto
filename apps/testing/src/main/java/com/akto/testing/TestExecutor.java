package com.akto.testing;

import com.akto.dao.AuthMechanismsDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing.WorkflowTestsDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.RawApi;
import com.akto.dto.testing.*;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.rules.*;
import com.akto.store.SampleMessageStore;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class TestExecutor {

    public static String slashHandling(String url) {
        if (!url.startsWith("/")) url = "/"+url;
        if (!url.endsWith("/")) url = url+"/";
        return url;
    }

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
        TestingEndpoints testingEndpoints = testingRun.getTestingEndpoints();

        Map<String, SingleTypeInfo> singleTypeInfoMap = SampleMessageStore.buildSingleTypeInfoMap(testingEndpoints);
        Map<ApiInfo.ApiInfoKey, List<String>> sampleMessages = SampleMessageStore.fetchSampleMessages();
        AuthMechanism authMechanism = AuthMechanismsDao.instance.findOne(new BasicDBObject());

        // todo: ???
        ObjectId testRunResultSummaryId = summaryId;

        List<ApiInfo.ApiInfoKey> apiInfoKeyList = testingEndpoints.returnApis();
        if (apiInfoKeyList == null || apiInfoKeyList.isEmpty()) return;
        System.out.println("APIs: " + apiInfoKeyList.size());

        Set<ApiInfo.ApiInfoKey> store = new HashSet<>();
        for (ApiInfo.ApiInfoKey apiInfoKey: apiInfoKeyList) {
            try {
                String url = slashHandling(apiInfoKey.url+"");
                ApiInfo.ApiInfoKey modifiedKey = new ApiInfo.ApiInfoKey(apiInfoKey.getApiCollectionId(), url, apiInfoKey.method);
                if (store.contains(modifiedKey)) continue;
                store.add(modifiedKey);
                start(apiInfoKey, testingRun.getTestIdConfig(), testingRun.getId(), singleTypeInfoMap, sampleMessages, authMechanism,  testRunResultSummaryId);
            } catch (Exception e) {
                logger.error(e.getMessage());
            }
        }
    }

    public void start(ApiInfo.ApiInfoKey apiInfoKey, int testIdConfig, ObjectId testRunId,
                      Map<String, SingleTypeInfo> singleTypeInfoMap, Map<ApiInfo.ApiInfoKey, List<String>> sampleMessages, AuthMechanism authMechanism, ObjectId testRunResultSummaryId) {
        if (testIdConfig != 0) {
            logger.error("Test id config is not 0 but " + testIdConfig);
            return;
        }

        BOLATest bolaTest = new BOLATest();
        NoAuthTest noAuthTest = new NoAuthTest();
        ChangeHttpMethodTest changeHttpMethodTest = new ChangeHttpMethodTest();
        AddMethodInParameterTest addMethodInParameterTest = new AddMethodInParameterTest();
        AddMethodOverrideHeadersTest addMethodOverrideHeadersTest = new AddMethodOverrideHeadersTest();

        List<TestingRunResult> testingRunResults = new ArrayList<>();
        TestingRunResult noAuthTestResult = runTest(noAuthTest, apiInfoKey, authMechanism, sampleMessages, singleTypeInfoMap, testRunId, testRunResultSummaryId);
        testingRunResults.add(noAuthTestResult);
        if (!noAuthTestResult.isVulnerable()) {
            TestingRunResult bolaTestResult = runTest(bolaTest, apiInfoKey, authMechanism, sampleMessages, singleTypeInfoMap, testRunId, testRunResultSummaryId);
            testingRunResults.add(bolaTestResult);
        }

        TestingRunResult changeHttpMethodTestResult = runTest(changeHttpMethodTest, apiInfoKey, authMechanism, sampleMessages, singleTypeInfoMap, testRunId, testRunResultSummaryId);
        testingRunResults.add(changeHttpMethodTestResult);

        TestingRunResult addMethodInParameterTestResult = runTest(addMethodInParameterTest, apiInfoKey, authMechanism, sampleMessages, singleTypeInfoMap, testRunId, testRunResultSummaryId);
        testingRunResults.add(addMethodInParameterTestResult);

        TestingRunResult addMethodOverrideHeadersTestResult = runTest(addMethodOverrideHeadersTest, apiInfoKey, authMechanism, sampleMessages, singleTypeInfoMap, testRunId, testRunResultSummaryId);
        testingRunResults.add(addMethodOverrideHeadersTestResult);

        TestingRunResultDao.instance.insertMany(testingRunResults);
    }

    public TestingRunResult runTest(TestPlugin testPlugin, ApiInfo.ApiInfoKey apiInfoKey, AuthMechanism authMechanism, Map<ApiInfo.ApiInfoKey, List<String>> sampleMessages,
                        Map<String, SingleTypeInfo> singleTypeInfos, ObjectId testRunId, ObjectId testRunResultSummaryId) {

        int startTime = Context.now();
        TestPlugin.Result result = testPlugin.start(apiInfoKey, authMechanism, sampleMessages, singleTypeInfos);
        int endTime = Context.now();

        return new TestingRunResult(
                testRunId, apiInfoKey, testPlugin.superTestName(), testPlugin.subTestName(), result.testResults,
                result.isVulnerable,result.singleTypeInfos, result.confidencePercentage,
                startTime, endTime, testRunResultSummaryId
        );
    }

}

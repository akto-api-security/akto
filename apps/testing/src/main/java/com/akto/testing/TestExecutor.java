package com.akto.testing;

import com.akto.dao.AuthMechanismsDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing.WorkflowTestsDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.RawApi;
import com.akto.dto.testing.*;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.rules.BOLATest;
import com.akto.rules.NoAuthTest;
import com.akto.rules.TestPlugin;
import com.akto.store.SampleMessageStore;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;
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

    public void init(TestingRun testingRun) {
        if (testingRun.getTestIdConfig() == 0)     {
            apiWiseInit(testingRun);
        } else {
            workflowInit(testingRun);
        }
    }

    public void workflowInit (TestingRun testingRun) {
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

    public void  apiWiseInit(TestingRun testingRun) {
        TestingEndpoints testingEndpoints = testingRun.getTestingEndpoints();

        Map<String, SingleTypeInfo> singleTypeInfoMap = SampleMessageStore.buildSingleTypeInfoMap(testingEndpoints);
        Map<ApiInfo.ApiInfoKey, List<String>> sampleMessages = SampleMessageStore.fetchSampleMessages();
        AuthMechanism authMechanism = AuthMechanismsDao.instance.findOne(new BasicDBObject());

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
                List<RawApi> messages = SampleMessageStore.fetchAllOriginalMessages(apiInfoKey, sampleMessages);
                start(apiInfoKey, testingRun.getTestIdConfig(), testingRun.getId(), singleTypeInfoMap, messages, authMechanism);
            } catch (Exception e) {
                logger.error(e.getMessage());
            }
        }
    }

    public void start(ApiInfo.ApiInfoKey apiInfoKey, int testIdConfig, ObjectId testRunId,
                      Map<String, SingleTypeInfo> singleTypeInfoMap, List<RawApi> messages, AuthMechanism authMechanism) {
        if (testIdConfig != 0) {
            logger.error("Test id config is not 0 but " + testIdConfig);
            return;
        }

        BOLATest bolaTest = new BOLATest();
        NoAuthTest noAuthTest = new NoAuthTest();

        List<Bson> updates = new ArrayList<>();

        // todo: test bola only if !noAuth
        runTest(noAuthTest, apiInfoKey, authMechanism, messages, updates, singleTypeInfoMap);
        runTest(bolaTest, apiInfoKey, authMechanism, messages, updates,  singleTypeInfoMap);

        Bson filter = TestingRunResultDao.generateFilter(testRunId, apiInfoKey.getApiCollectionId(), apiInfoKey.url, apiInfoKey.method.name());
        Bson update = Updates.combine(updates);

        TestingRunResultDao.instance.updateOne(filter, update);
    }

    public void runTest(TestPlugin testPlugin, ApiInfo.ApiInfoKey apiInfoKey, AuthMechanism authMechanism, List<RawApi> messages,
                        List<Bson> updates, Map<String, SingleTypeInfo> singleTypeInfos) {
        TestResult testResult = testPlugin.start(apiInfoKey, authMechanism, messages, singleTypeInfos);
        if (testResult != null) {
            updates.add(
                    Updates.set("resultMap." + testPlugin.testName(), testResult)
            );
        }
    }

}

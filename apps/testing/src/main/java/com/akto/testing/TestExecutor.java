package com.akto.testing;

import com.akto.dao.testing.WorkflowTestsDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.testing.*;
import com.akto.rules.BOLATest;
import com.akto.rules.NoAuthTest;
import com.akto.store.AuthMechanismStore;
import com.akto.store.SampleMessageStore;
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

        SampleMessageStore.buildSingleTypeInfoMap(testingEndpoints);

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
                start(apiInfoKey, testingRun.getTestIdConfig(), testingRun.getId());
            } catch (Exception e) {
                logger.error(e.getMessage());
            }
        }
    }

    private final BOLATest bolaTest = new BOLATest();
    private final NoAuthTest noAuthTest = new NoAuthTest();

    public void start(ApiInfo.ApiInfoKey apiInfoKey, int testIdConfig, ObjectId testRunId) {
        if (testIdConfig != 0) {
            logger.error("Test id config is not 0 but " + testIdConfig);
            return;
        }

        AuthMechanism authMechanism = AuthMechanismStore.getAuthMechanism();
        if (authMechanism == null) return;

        boolean noAuthResult = noAuthTest.start(apiInfoKey, testRunId, authMechanism);
        if (!noAuthResult) {
            bolaTest.start(apiInfoKey, testRunId, authMechanism);
        }

    }

}

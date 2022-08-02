package com.akto.testing;

import com.akto.dto.ApiInfo;
import com.akto.dto.testing.TestingEndpoints;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.WorkflowTest;
import com.akto.dto.testing.WorkflowTestingEndpoints;
import com.akto.dto.type.RequestTemplate;
import com.akto.rules.BOLATest;
import com.akto.rules.NoAuthTest;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TestExecutor {

    public static String slashHandling(String url) {
        if (!url.startsWith("/")) url = "/"+url;
        if (!url.endsWith("/")) url = url+"/";
        return url;
    }

    private static final Logger logger = LoggerFactory.getLogger(TestExecutor.class);

    public static void init(TestingRun testingRun) {
        if (testingRun.getTestIdConfig() == 0)     {
            apiWiseInit(testingRun);
        } else {
            workflowInit(testingRun);
        }
    }

    public static void workflowInit (TestingRun testingRun) {
        TestingEndpoints testingEndpoints = testingRun.getTestingEndpoints();
        if (!testingEndpoints.getType().equals(TestingEndpoints.Type.WORKFLOW)) {
            logger.error("Invalid workflow type");
            return;
        }

        WorkflowTestingEndpoints workflowTestingEndpoints = (WorkflowTestingEndpoints) testingEndpoints;
        WorkflowTest workflowTest = workflowTestingEndpoints.getWorkflowTest();

        ApiWorkflowExecutor apiWorkflowExecutor = new ApiWorkflowExecutor();
        apiWorkflowExecutor.init(workflowTest, testingRun.getId());
    }

    public static void  apiWiseInit(TestingRun testingRun) {
        TestingEndpoints testingEndpoints = testingRun.getTestingEndpoints();
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

    public static void start(ApiInfo.ApiInfoKey apiInfoKey, int testIdConfig, ObjectId testRunId) {
        if (testIdConfig != 0) return;

        boolean noAuthResult = new NoAuthTest().start(apiInfoKey, testRunId);
        if (!noAuthResult) {
            new BOLATest().start(apiInfoKey, testRunId);
        }

    }
}

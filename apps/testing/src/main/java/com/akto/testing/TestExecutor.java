package com.akto.testing;

import com.akto.dto.ApiInfo;
import com.akto.dto.testing.TestingEndpoints;
import com.akto.dto.testing.TestingRun;
import com.akto.rules.BOLATest;
import com.akto.rules.NoAuthTest;
import org.bson.types.ObjectId;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TestExecutor {

    public static String slashHandling(String url) {
        if (!url.startsWith("/")) url = "/"+url;
        if (!url.endsWith("/")) url = url+"/";
        return url;
    }

    public static void init(TestingRun testingRun) {
        TestingEndpoints testingEndpoints = testingRun.getTestingEndpoints();
        List<ApiInfo.ApiInfoKey> apiInfoKeyList = testingEndpoints.returnApis();
        if (apiInfoKeyList == null || apiInfoKeyList.isEmpty()) return;
        System.out.println("APIs: " + apiInfoKeyList.size());
        Set<ApiInfo.ApiInfoKey> store = new HashSet<>();
        for (ApiInfo.ApiInfoKey apiInfoKey: apiInfoKeyList) {
            String url = slashHandling(apiInfoKey.url);
            ApiInfo.ApiInfoKey modifiedKey = new ApiInfo.ApiInfoKey(apiInfoKey.getApiCollectionId(), url, apiInfoKey.method);
            if (store.contains(modifiedKey)) continue;
            store.add(modifiedKey);
            try {
                start(apiInfoKey, testingRun.getTestIdConfig(), testingRun.getId());
            } catch (Exception e) {
                // TODO:
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

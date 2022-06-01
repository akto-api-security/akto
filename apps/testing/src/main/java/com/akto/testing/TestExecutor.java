package com.akto.testing;

import com.akto.DaoInit;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.testing.TestingEndpoints;
import com.akto.dto.testing.TestingRun;
import com.akto.rules.BOLATest;
import com.akto.rules.NoAuthTest;
import com.mongodb.ConnectionString;
import io.swagger.annotations.Api;
import org.bson.types.ObjectId;

import java.util.List;

public class TestExecutor {

    public static void init(TestingRun testingRun) {
        TestingEndpoints testingEndpoints = testingRun.getTestingEndpoints();
        List<ApiInfo.ApiInfoKey> apiInfoKeyList = testingEndpoints.returnApis();
        if (apiInfoKeyList == null || apiInfoKeyList.isEmpty()) return;
        for (ApiInfo.ApiInfoKey apiInfoKey: apiInfoKeyList) {
            start(apiInfoKey, testingRun.getTestIdConfig(), testingRun.getId());
        }
    }

    public static void start(ApiInfo.ApiInfoKey apiInfoKey, int testIdConfig, ObjectId testRunId) {
        if (testIdConfig != 0) return;

        new NoAuthTest().start(apiInfoKey, testRunId);
        new BOLATest().start(apiInfoKey, testRunId);

    }
}

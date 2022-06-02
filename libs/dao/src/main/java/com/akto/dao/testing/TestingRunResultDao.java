package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.testing.TestingRunResult;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

public class TestingRunResultDao extends AccountsContextDao<TestingRunResult> {

    public static final TestingRunResultDao instance = new TestingRunResultDao();

    @Override
    public String getCollName() {
        return "testing_run_result";
    }

    @Override
    public Class<TestingRunResult> getClassT() {
        return TestingRunResult.class;
    }

    public static Bson generateFilter(ObjectId testRunId, ApiInfo.ApiInfoKey apiInfoKey) {
        return generateFilter(testRunId, apiInfoKey.getApiCollectionId(), apiInfoKey.getUrl(), apiInfoKey.getMethod().name());
    }

    public static Bson generateFilter(ObjectId testRunId, int apiCollectionId ,String url, String method) {
        return Filters.and(
                Filters.eq(TestingRunResult.TEST_RUN_ID, testRunId),
                Filters.eq(TestingRunResult.API_INFO_KEY + "." + ApiInfo.ApiInfoKey.API_COLLECTION_ID, apiCollectionId),
                Filters.eq(TestingRunResult.API_INFO_KEY + "." + ApiInfo.ApiInfoKey.URL, url),
                Filters.eq(TestingRunResult.API_INFO_KEY + "." + ApiInfo.ApiInfoKey.METHOD, method)
        );
    }
}

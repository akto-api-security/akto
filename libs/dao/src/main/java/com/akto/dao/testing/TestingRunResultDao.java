package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.testing.TestingRunResult;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.*;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;

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

    public List<TestingRunResult> fetchLatestTestingRunResult(ObjectId testRunResultSummaryId) {
        MongoCursor<TestingRunResult> cursor = instance.getMCollection().find(Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, testRunResultSummaryId))
                .projection(
                        Projections.include(
                            TestingRunResult.TEST_RUN_ID,
                            TestingRunResult.API_INFO_KEY,
                            TestingRunResult.TEST_SUPER_TYPE,
                            TestingRunResult.TEST_SUB_TYPE,
                            TestingRunResult.IS_VULNERABLE,
                            TestingRunResult.CONFIDENCE_PERCENTAGE,
                            TestingRunResult.START_TIMESTAMP,
                            TestingRunResult.END_TIMESTAMP,
                            TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID
                        )
                )
                .sort(Sorts.descending("_id"))
                .limit(10_000)
                .cursor();
        List<TestingRunResult> testingRunResults = new ArrayList<>();
        while (cursor.hasNext()) {
            TestingRunResult testingRunResult = cursor.next();
            testingRunResult.setHexId(testingRunResult.getId().toHexString());
            testingRunResults.add(testingRunResult);
        }

        return testingRunResults;
    }
}

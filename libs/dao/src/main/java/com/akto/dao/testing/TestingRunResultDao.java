package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.testing.TestingRunResult;
import com.akto.util.Constants;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.*;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.IndexOptions;
import java.util.ArrayList;
import java.util.Arrays;
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

    public List<TestingRunResult> fetchLatestTestingRunResult(Bson filters) {
        return fetchLatestTestingRunResult(filters, 10_000);
    }

    public List<TestingRunResult> fetchLatestTestingRunResult(Bson filters, int limit) {
        Bson projections = Projections.include(
                                TestingRunResult.TEST_RUN_ID,
                                TestingRunResult.API_INFO_KEY,
                                TestingRunResult.TEST_SUPER_TYPE,
                                TestingRunResult.TEST_SUB_TYPE,
                                TestingRunResult.VULNERABLE,
                                TestingRunResult.CONFIDENCE_PERCENTAGE,
                                TestingRunResult.START_TIMESTAMP,
                                TestingRunResult.END_TIMESTAMP,
                                TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID
                        );

        return fetchLatestTestingRunResult(filters, limit, 0, projections);
    }

    public List<TestingRunResult> fetchLatestTestingRunResult(Bson filters, int limit, int skip, Bson projections) {
        MongoCursor<TestingRunResult> cursor = instance.getMCollection().find(filters)
                .projection(projections)
                .sort(Sorts.descending("_id"))
                .skip(skip)
                .limit(limit)
                .cursor();
        List<TestingRunResult> testingRunResults = new ArrayList<>();
        while (cursor.hasNext()) {
            TestingRunResult testingRunResult = cursor.next();
            testingRunResult.setHexId(testingRunResult.getId().toHexString());
            testingRunResults.add(testingRunResult);
        }

        return testingRunResults;
    }

    public void createIndicesIfAbsent() {
        
        String dbName = Context.accountId.get()+"";
        createCollectionIfAbsent(dbName, getCollName(), new CreateCollectionOptions());

        Bson summaryIndex = Indexes.descending(Arrays.asList(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, Constants.ID));
        createIndexIfAbsent(dbName, getCollName(), summaryIndex, new IndexOptions().name("testRunResultSummaryId_-1__id_-1"));

    }

}

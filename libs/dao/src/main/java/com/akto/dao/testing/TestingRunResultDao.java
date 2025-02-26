package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.testing.GenericTestResult;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunResult;
import com.akto.util.Constants;
import com.akto.util.DbMode;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.*;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TestingRunResultDao extends AccountsContextDao<TestingRunResult> {

    public static final TestingRunResultDao instance = new TestingRunResultDao();
    public static final int maxDocuments = 5_000_000;
    public static final long sizeInBytes = 50_000_000_000L;
    public static final String ERRORS_KEY = TestingRunResult.TEST_RESULTS+".0."+TestResult.ERRORS+".0";

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
        Bson projections = Projections.fields(
                Projections.include(
                        TestingRunResult.TEST_RUN_ID,
                        TestingRunResult.API_INFO_KEY,
                        TestingRunResult.TEST_SUPER_TYPE,
                        TestingRunResult.TEST_SUB_TYPE,
                        TestingRunResult.VULNERABLE,
                        TestingRunResult.CONFIDENCE_PERCENTAGE,
                        TestingRunResult.START_TIMESTAMP,
                        TestingRunResult.END_TIMESTAMP,
                        TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID
                )
            );

        return fetchLatestTestingRunResult(filters, limit, 0, projections);
    }

    public List<TestingRunResult> fetchLatestTestingRunResult(Bson filters, int limit, int skip, Bson projections) {
        MongoCursor<TestingRunResult> cursor = this.getMCollection().find(filters)
                .projection(projections)
                .sort(Sorts.descending("_id"))
                .skip(skip)
                .limit(limit)
                .cursor();
        List<TestingRunResult> testingRunResults = new ArrayList<>();
        while (cursor.hasNext()) {
            TestingRunResult testingRunResult = cursor.next();

            List<String> errors = new ArrayList<>();
            List<GenericTestResult> testResults = testingRunResult.getTestResults();
            if (testResults != null && !testResults.isEmpty()) {
                GenericTestResult genericTestResult = testResults.get(0);
                if (genericTestResult instanceof TestResult) {
                    TestResult testResult = (TestResult) genericTestResult;
                    if (testResult.getErrors() != null) errors = testResult.getErrors();
                }
            }
            testingRunResult.setErrorsList(errors);

            testingRunResult.setHexId(testingRunResult.getId().toHexString());
            testingRunResults.add(testingRunResult);
        }

        return testingRunResults;
    }

    public void createIndicesIfAbsent() {
        
        String dbName = Context.accountId.get()+"";
        CreateCollectionOptions createCollectionOptions = new CreateCollectionOptions();
        if (DbMode.allowCappedCollections()) {
            createCollectionOptions = new CreateCollectionOptions().capped(true).maxDocuments(maxDocuments).sizeInBytes(sizeInBytes);
        }
        createCollectionIfAbsent(dbName, getCollName(), createCollectionOptions);

        Bson summaryIndex = Indexes.descending(Arrays.asList(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, Constants.ID));
        createIndexIfAbsent(dbName, getCollName(), summaryIndex, new IndexOptions().name("testRunResultSummaryId_-1__id_-1"));

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] { TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, TestingRunResult.VULNERABLE, Constants.ID }, false);


        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] { TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, TestingRunResult.VULNERABLE, ERRORS_KEY }, false);

        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{TestingRunResult.END_TIMESTAMP}, false);

        String[] fieldNames = new String[]{TestingRunResult.END_TIMESTAMP, TestResult.TEST_RESULTS_ERRORS};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{TestingRunResult.REQUIRES_CONFIG};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);
    }

    public void convertToCappedCollection() {
        if (DbMode.allowCappedCollections() || this.isCapped()) return;
        this.convertToCappedCollection(sizeInBytes);
        this.createIndicesIfAbsent();
    }

}

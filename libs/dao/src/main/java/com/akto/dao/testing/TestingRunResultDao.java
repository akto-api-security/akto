package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.testing.GenericTestResult;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.TestResult.Confidence;
import com.akto.dto.type.URLMethods;
import com.akto.util.Constants;
import com.akto.util.DbMode;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
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

    public static final int CLEAN_THRESHOLD = 90;
    public static final int DESIRED_THRESHOLD = 50;

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
                        TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID,
                        TestingRunResult.TEST_RESULTS + "." + GenericTestResult._CONFIDENCE,
                        TestingRunResult.TEST_RESULTS + "." + TestResult._ERRORS
                )
            );

        return fetchLatestTestingRunResult(filters, limit, 0, projections);
    }

    public List<TestingRunResult> fetchLatestTestingRunResult(Bson filters, int limit, int skip, Bson projections) {
        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(filters));
        pipeline.add(Aggregates.project(projections));
        pipeline.add(Aggregates.sort(Sorts.descending(Constants.ID)));
        pipeline.add(Aggregates.skip(skip));
        pipeline.add(Aggregates.limit(limit));
        MongoCursor<BasicDBObject> cursor = instance.getMCollection()
                .aggregate(pipeline, BasicDBObject.class).cursor();
        List<TestingRunResult> testingRunResults = new ArrayList<>();
        while (cursor.hasNext()) {
            TestingRunResult testingRunResult = new TestingRunResult();
            BasicDBObject doc = cursor.next();
            testingRunResult.setId(new ObjectId(doc.getString(Constants.ID)));
            testingRunResult.setTestRunId(new ObjectId(doc.getString(TestingRunResult.TEST_RUN_ID)));
            BasicDBObject apiInfoKeyObj = (BasicDBObject) doc.get(TestingRunResult.API_INFO_KEY);
            ApiInfoKey apiInfoKey = new ApiInfoKey(apiInfoKeyObj.getInt(ApiInfoKey.API_COLLECTION_ID),
                    apiInfoKeyObj.getString(ApiInfoKey.URL),
                    URLMethods.Method.valueOf(apiInfoKeyObj.getString(ApiInfoKey.METHOD)));
            testingRunResult.setApiInfoKey(apiInfoKey);
            testingRunResult.setTestSuperType(doc.getString(TestingRunResult.TEST_SUPER_TYPE));
            testingRunResult.setTestSubType(doc.getString(TestingRunResult.TEST_SUB_TYPE));
            testingRunResult.setVulnerable(doc.getBoolean(TestingRunResult.VULNERABLE));
            testingRunResult.setConfidencePercentage(doc.getInt(TestingRunResult.CONFIDENCE_PERCENTAGE));
            testingRunResult.setStartTimestamp(doc.getInt(TestingRunResult.START_TIMESTAMP));
            testingRunResult.setEndTimestamp(doc.getInt(TestingRunResult.END_TIMESTAMP));
            testingRunResult.setTestRunResultSummaryId(
                    new ObjectId(doc.getString(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID)));        

            BasicDBList testResultsList = (BasicDBList)doc.get(TestingRunResult.TEST_RESULTS);

            List<String> errors = new ArrayList<>();
            List<GenericTestResult> testResults = new ArrayList<>();
            if (testResultsList != null && !testResultsList.isEmpty()) {
                BasicDBObject genericTestResult = (BasicDBObject)testResultsList.get(0);
                String confidence = "";
                if (genericTestResult.get(GenericTestResult._CONFIDENCE)!=null) {
                    TestResult testResult = new TestResult();
                    confidence = genericTestResult.getString(GenericTestResult._CONFIDENCE);
                    try {
                        testResult.setConfidence(Confidence.valueOf(confidence));
                        testResults.add(testResult);
                    } catch(Exception e){
                    }
                }
                if (genericTestResult.get(TestResult._ERRORS)!=null) {
                    try {
                        errors = (List)genericTestResult.get(TestResult._ERRORS);
                    } catch(Exception e){
                    }
                }
            }
            testingRunResult.setErrorsList(errors);
            testingRunResult.setTestResults(testResults);
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

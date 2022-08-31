package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunResult;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.*;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public List<TestingRunResult> fetchLatestTestingRunResult() {
        MongoCursor<TestingRunResult> cursor = instance.getMCollection().find().sort(Sorts.descending("_id")).limit(1000).cursor();
        List<TestingRunResult> testingRunResults = new ArrayList<>();
        while (cursor.hasNext()) {
            TestingRunResult testingRunResult = cursor.next();
            for (TestResult testResult: testingRunResult.getResultMap().values()) {
                testResult.setMessage("");
            }
            testingRunResult.setHexId(testingRunResult.getId().toHexString());
            testingRunResults.add(testingRunResult);
        }

        return testingRunResults;
    }

    // this is not optimised please don't use
    public List<TestingRunResult> fetchLatestTestingRunResultWithAggregation() {
        List<TestingRunResult> testingRunResults = new ArrayList<>();

        List<Bson> pipeline = new ArrayList<>();

        pipeline.add(Aggregates.sort(Sorts.descending("_id")));

        Bson projections = Projections.fields(
                Projections.include("apiInfoKey", "testRunId", "_id", "resultMap")
        );
        pipeline.add(Aggregates.project(projections));

        BasicDBObject groupedId = new BasicDBObject("apiCollectionId", "$apiInfoKey.apiCollectionId")
                .append("url", "$apiInfoKey.url")
                .append("method", "$apiInfoKey.method");

//        Accumulators.last("resultMap", "$resultMap")
        pipeline.add(
                Aggregates.group(
                        groupedId,
                        Accumulators.first("resultId", "$_id"),
                        Accumulators.first("testRunId", "$testRunId"),
                        Accumulators.first("resultMap", "$resultMap")
                )
        );

        MongoCursor<BasicDBObject> cursor = TestingRunResultDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();

        Gson gson = new Gson();
        while (cursor.hasNext()) {
            BasicDBObject r = cursor.next();

            BasicDBObject apiInfoKeyObj = (BasicDBObject)  r.get("_id");
            ApiInfo.ApiInfoKey apiInfoKey = gson.fromJson(apiInfoKeyObj.toJson(), ApiInfo.ApiInfoKey.class);

            ObjectId testRunId = (ObjectId) r.get("testRunId");
            ObjectId resultId = (ObjectId) r.get("resultId");

            BasicDBObject resultMapObj = (BasicDBObject) r.get("resultMap");
            Map<String, TestResult> m = new HashMap<>();
            for (String k: resultMapObj.keySet()) {
                BasicDBObject b = (BasicDBObject) resultMapObj.get(k);
                TestResult testResult = new TestResult("", (boolean)b.get("vulnerable"), null,null);
                m.put(k, testResult);
            }

            TestingRunResult testingRunResult = new TestingRunResult(testRunId, apiInfoKey, m);
            testingRunResult.setId(resultId);
            testingRunResult.setHexId(resultId.toHexString());
            testingRunResults.add(testingRunResult);
        }

        return testingRunResults;
    }
}

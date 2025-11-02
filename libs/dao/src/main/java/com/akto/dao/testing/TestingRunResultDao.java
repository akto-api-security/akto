package com.akto.dao.testing;

import com.akto.dao.AccountsContextDaoWithRbac;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.testing.GenericTestResult;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.TestResult.Confidence;
import com.akto.dto.type.URLMethods;
import com.akto.util.Constants;
import com.akto.util.DbMode;
import com.akto.util.enums.GlobalEnums;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.*;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class TestingRunResultDao extends AccountsContextDaoWithRbac<TestingRunResult> {

    public static final TestingRunResultDao instance = new TestingRunResultDao();
    public static final int maxDocuments = 5_000_000;
    public static final long sizeInBytes = 50_000_000_000L;

    public static final int CLEAN_THRESHOLD = 80;
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

    @Override
    public String getFilterKeyString() {
        return TestingRunResult.API_INFO_KEY + "." + ApiInfoKey.API_COLLECTION_ID;
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

    private Bson getLatestTestingRunResultProjections() {
        return Projections.include(
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
                TestingRunResult.TEST_RESULTS + "." + TestResult._ERRORS,
                TestingRunResult.TEST_RESULTS + "." + TestResult._MESSAGE
        );
    }

    public List<TestingRunResult> fetchLatestTestingRunResult(Bson filters, int limit) {
        Bson projections = Projections.fields(
                getLatestTestingRunResultProjections()
            );

        return fetchLatestTestingRunResult(filters, limit, 0, Arrays.asList(Aggregates.project(projections)), false);
    }

    public List<TestingRunResult> fetchLatestTestingRunResultWithCustomAggregations(Bson filters, int limit, int skip, Bson customSort) {
        if(customSort == null) {
            customSort = new BasicDBObject();
        }

        Bson projections = Projections.fields(
                Projections.computed("confidence", Projections.computed("$first", "$testResults.confidence")),
                getLatestTestingRunResultProjections()
        );

        Bson addSeverityValueStage = Aggregates.addFields(
                new Field<>("severityValue", new BasicDBObject("$switch",
                        new BasicDBObject("branches", Arrays.asList(
                                new BasicDBObject("case", new BasicDBObject("$eq", Arrays.asList("$confidence", GlobalEnums.Severity.CRITICAL.name()))).append("then", 4),
                                new BasicDBObject("case", new BasicDBObject("$eq", Arrays.asList("$confidence", GlobalEnums.Severity.HIGH.name()))).append("then", 3),
                                new BasicDBObject("case", new BasicDBObject("$eq", Arrays.asList("$confidence", GlobalEnums.Severity.MEDIUM.name()))).append("then", 2),
                                new BasicDBObject("case", new BasicDBObject("$eq", Arrays.asList("$confidence", GlobalEnums.Severity.LOW.name()))).append("then", 1)
                        )).append("default", 0)
                ))
        );

        return fetchLatestTestingRunResult(filters, limit, skip, Arrays.asList(Aggregates.project(projections), addSeverityValueStage, customSort), true);
    }

    public List<TestingRunResult> fetchLatestTestingRunResult(Bson filters, int limit, int skip, List<Bson> customAggregation, boolean skipCollectionFilter) {
        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(filters));
        if (!skipCollectionFilter) {
            try {
                List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
                if (collectionIds != null) {
                    pipeline.add(Aggregates.match(Filters.in(getFilterKeyString(), collectionIds)));
                }
            } catch (Exception e) {
            }
        }
        pipeline.add(Aggregates.sort(Sorts.descending(Constants.ID)));
        pipeline.addAll(customAggregation);
        pipeline.add(Aggregates.skip(skip));
        pipeline.add(Aggregates.limit(limit));
        MongoCursor<BasicDBObject> cursor = this.getMCollection()
                .aggregate(pipeline, BasicDBObject.class).cursor();
        List<TestingRunResult> testingRunResults = new ArrayList<>();
        while (cursor.hasNext()) {
            TestingRunResult testingRunResult = new TestingRunResult();
            BasicDBObject doc = cursor.next();
            try {
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
                    String message;
                    if (genericTestResult.get(GenericTestResult._CONFIDENCE)!=null) {
                        TestResult testResult = new TestResult();
                        confidence = genericTestResult.getString(GenericTestResult._CONFIDENCE);

                        try {
                            testResult.setConfidence(Confidence.valueOf(confidence));
                            message = genericTestResult.getString(TestResult._MESSAGE, null);
                            testResult.setMessage(message);
                        } catch(Exception e){
                        }
                        testResults.add(testResult);
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
            } catch (Exception e) {
                e.printStackTrace();;
                continue;
            }
           
        }

        return testingRunResults;
    }

    public Map<ObjectId,String> mapSummaryIdToTestingResultHexId(Set<String> testingRunResultHexIds){
        Map<ObjectId,String> finalMap = new HashMap<>();
        if(testingRunResultHexIds == null || testingRunResultHexIds.isEmpty()){
            return finalMap;
        }

        List<ObjectId> objectIdList = testingRunResultHexIds.stream()
                                                .map(ObjectId::new)
                                                .collect(Collectors.toList());

        List<TestingRunResult> runResults = this.findAll(Filters.in(Constants.ID, objectIdList), Projections.include(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID));
        for(TestingRunResult runResult: runResults){
            finalMap.put(runResult.getTestRunResultSummaryId(), runResult.getHexId());
        }

        return finalMap;
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

        // Add partial index for testRunResultSummaryId, vulnerable, endTimestamp with partialFilterExpression on testResults.message
        Bson partialIndex = Indexes.compoundIndex(
        Indexes.ascending("testRunResultSummaryId"),
        Indexes.ascending("vulnerable"), 
        Indexes.descending("endTimestamp")
        );

        IndexOptions partialIndexOptions = new IndexOptions()
            .name("testRunResultSummaryId_1_vulnerable_1_endTimestamp_-1_partial_message_exists")
            .partialFilterExpression(Filters.exists("testResults.message", true));
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), partialIndex, partialIndexOptions);


        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] { TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, TestingRunResult.VULNERABLE, ERRORS_KEY }, false);

        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{TestingRunResult.END_TIMESTAMP}, false);

        String[] fieldNames = new String[]{TestingRunResult.END_TIMESTAMP, TestResult.TEST_RESULTS_ERRORS};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{TestingRunResult.REQUIRES_CONFIG};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{getFilterKeyString()};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, TestingRunResult.API_INFO_KEY+"."+ApiInfoKey.API_COLLECTION_ID};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, TestingRunResult.TEST_RESULTS+"."+GenericTestResult._CONFIDENCE};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, TestingRunResult.API_INFO_KEY+"."+ApiInfoKey.METHOD};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, TestingRunResult.TEST_SUPER_TYPE};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, TestingRunResult.VULNERABLE, TestingRunResult.API_INFO_KEY, TestingRunResult.TEST_SUB_TYPE};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);
    }

    public MongoCollection<Document> getRawCollection() {
        return clients[0].getDatabase(getDBName()).getCollection(getCollName(), Document.class);
    }

    public void convertToCappedCollection() {
        if (DbMode.allowCappedCollections() || this.isCapped()) return;
        this.convertToCappedCollection(sizeInBytes);
        this.createIndicesIfAbsent();
    }

    /**
     * Aggregates testing results by category (testSuperType) with pass/fail/skip counts
     * OPTIMIZED VERSION: Simplified skip detection and better performance
     * @param startTimestamp Start time filter (0 to ignore)
     * @param endTimestamp End time filter (0 to ignore)
     * @param relevantCategories List of categories to filter by (null/empty for all)
     * @return List of category-wise statistics
     */
    public List<Map<String, Object>> getCategoryWiseScores(int startTimestamp, int endTimestamp, List<String> relevantCategories) {
        List<Bson> pipeline = new ArrayList<>();
        
        // Stage 1: Match filters for time range and category filtering (INDEXED)
        List<Bson> matchFilters = new ArrayList<>();
        
        // Time range filter
        if (startTimestamp > 0 && endTimestamp > 0) {
            matchFilters.add(Filters.gte(TestingRunResult.END_TIMESTAMP, startTimestamp));
            matchFilters.add(Filters.lte(TestingRunResult.END_TIMESTAMP, endTimestamp));
        }
        
        // Filter by relevant categories if provided (INDEXED)
        if (relevantCategories != null && !relevantCategories.isEmpty()) {
            matchFilters.add(Filters.in(TestingRunResult.TEST_SUPER_TYPE, relevantCategories));
        }
        
        if (!matchFilters.isEmpty()) {
            pipeline.add(Aggregates.match(Filters.and(matchFilters)));
        }
        
        // Stage 2: Group by testSuperType - calculate all counts in one stage
        pipeline.add(Aggregates.group(
            "$testSuperType",
            // Pass count: not vulnerable
            Accumulators.sum("passCount", new BasicDBObject("$cond", Arrays.asList(
                new BasicDBObject("$eq", Arrays.asList("$vulnerable", false)), 1, 0))),
            // Fail count: vulnerable  
            Accumulators.sum("failCount", new BasicDBObject("$cond", Arrays.asList(
                new BasicDBObject("$eq", Arrays.asList("$vulnerable", true)), 1, 0))),
            // Skip detection: check if testResults.errors exists and has elements
            Accumulators.sum("skipCount", new BasicDBObject("$cond", Arrays.asList(
                new BasicDBObject("$and", Arrays.asList(
                    new BasicDBObject("$isArray", "$testResults.errors"),
                    new BasicDBObject("$gt", Arrays.asList(new BasicDBObject("$size", "$testResults.errors"), 0))
                )), 1, 0)))
        ));
        
        // Stage 3: Project the results in the desired format
        pipeline.add(Aggregates.project(
            Projections.fields(
                Projections.computed("categoryName", "$_id"),
                Projections.include("passCount"),
                Projections.include("failCount"), 
                Projections.include("skipCount"),
                Projections.exclude("_id")
            )
        ));
        
        // Stage 4: Sort by category name
        pipeline.add(Aggregates.sort(Sorts.ascending("categoryName")));
        
        // Execute aggregation with optimizations
        List<Map<String, Object>> results = new ArrayList<>();
        try (MongoCursor<BasicDBObject> cursor = this.getMCollection()
                .aggregate(pipeline, BasicDBObject.class)
                .allowDiskUse(true) // Allow disk usage for large datasets
                .cursor()) {
            
            while (cursor.hasNext()) {
                BasicDBObject result = cursor.next();
                String categoryName = result.getString("categoryName");
                Map<String, Object> categoryScore = new HashMap<>();
                categoryScore.put("categoryName", categoryName);
                categoryScore.put("pass", result.getInt("passCount", 0));
                categoryScore.put("fail", result.getInt("failCount", 0));
                categoryScore.put("skip", result.getInt("skipCount", 0));
                results.add(categoryScore);
            }
        }
        
        return results;
    }


}
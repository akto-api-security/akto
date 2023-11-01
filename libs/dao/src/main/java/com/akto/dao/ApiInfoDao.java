package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.util.Constants;
import com.mongodb.client.model.CreateCollectionOptions;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunResult;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;

import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApiInfoDao extends AccountsContextDao<ApiInfo>{

    public static ApiInfoDao instance = new ApiInfoDao();

    public static final List<Bson> legacyIndices = Arrays.asList(
                Indexes.ascending(new String[] {Constants.ID + Constants.DOT + ApiInfo.ApiInfoKey.API_COLLECTION_ID}),
                Indexes.ascending(new String[] {Constants.ID + Constants.DOT + ApiInfo.ApiInfoKey.API_COLLECTION_ID, Constants.ID + Constants.DOT + ApiInfo.ApiInfoKey.URL}));

    public void createIndicesIfAbsent(boolean createLegacyIndices) {

        String dbName = Context.accountId.get()+"";
        createCollectionIfAbsent(dbName, getCollName(), new CreateCollectionOptions());

        List<Bson> indices = new ArrayList<>(Arrays.asList(
                Indexes.ascending(new String[]{SingleTypeInfo._COLLECTION_IDS}),
                Indexes.ascending(new String[]{Constants.ID + Constants.DOT + ApiInfo.ApiInfoKey.URL}),
                Indexes.ascending(new String[]{SingleTypeInfo._COLLECTION_IDS, Constants.ID + Constants.DOT + ApiInfo.ApiInfoKey.URL}),
                Indexes.descending(new String[]{ApiInfo.LAST_SEEN}),
                Indexes.descending(new String[]{ApiInfo.LAST_TESTED})
        ));

        if (createLegacyIndices) {
            indices.addAll(legacyIndices);
        }
        createIndices(indices);
    }

    private boolean hasTestRunSuccessfullyOnApi(List<TestingRunResult> testingRunResults){
        for(TestingRunResult result : testingRunResults){
            List<TestResult> testResults = result.getTestResults() ;
            for(TestResult singleTestResult : testResults){
                List<String> errors = singleTestResult.getErrors();
                if(errors.size() == 0 && singleTestResult.getMessage().length() > 0){
                    return true ;
                }
            }
        }
        return false;
    }

    public void updateLastTestedField(List<TestingRunResult> testingRunResults, ApiInfo.ApiInfoKey apiInfoKey){
        if(hasTestRunSuccessfullyOnApi(testingRunResults)){
            UpdateOptions updateOptions = new UpdateOptions();
            updateOptions.upsert(true);
            instance.getMCollection().updateOne(
                getFilter(apiInfoKey), 
                Updates.combine(
                    Updates.set("lastTested", Context.now()),
                    Updates.setOnInsert("allAuthTypesFound", new ArrayList<>()),
                    Updates.setOnInsert("lastSeen", 0),
                    Updates.setOnInsert("apiAccessTypes",  new ArrayList<>()),
                    Updates.setOnInsert("violations", new HashMap<>())
                ),
                updateOptions
            ) ;
        }
    }

    public Map<Integer,Integer> getCoverageCount(){
        Map<Integer,Integer> result = new HashMap<>();
        List<Bson> pipeline = new ArrayList<>();
        int oneMonthAgo = Context.now() - (30 * 24 * 60 * 60) ;
        pipeline.add(Aggregates.match(Filters.gte("lastTested", oneMonthAgo)));

        BasicDBObject groupedId2 = new BasicDBObject("apiCollectionId", "$_id.apiCollectionId");
        pipeline.add(Aggregates.group(groupedId2, Accumulators.sum("count",1)));

        MongoCursor<BasicDBObject> collectionsCursor = ApiInfoDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
        while(collectionsCursor.hasNext()){
            try {
                BasicDBObject basicDBObject = collectionsCursor.next();
                Integer apiCollectionId = ((BasicDBObject) basicDBObject.get("_id")).getInt("apiCollectionId");
                int count = basicDBObject.getInt("count");
                result.put(apiCollectionId, count);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    public Map<Integer,Integer> getLastTrafficSeen(){
        Map<Integer,Integer> result = new HashMap<>();
        List<Bson> pipeline = new ArrayList<>();
        BasicDBObject groupedId = new BasicDBObject("apiCollectionId", "$_id.apiCollectionId");
        pipeline.add(Aggregates.sort(Sorts.descending(ApiInfo.LAST_SEEN)));
        pipeline.add(Aggregates.group(groupedId, Accumulators.first(ApiInfo.LAST_SEEN, "$lastSeen")));

        MongoCursor<BasicDBObject> collectionsCursor = ApiInfoDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
        while(collectionsCursor.hasNext()){
            try {
                BasicDBObject basicDBObject = collectionsCursor.next();
                Integer apiCollectionId = ((BasicDBObject) basicDBObject.get("_id")).getInt("apiCollectionId");
                int lastTrafficSeen = basicDBObject.getInt("lastSeen");
                result.put(apiCollectionId, lastTrafficSeen);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    public List<Bson> buildRiskScorePipeline(){
        int oneMonthBefore = Context.now() - Constants.ONE_MONTH_TIMESTAMP;
        String computedSeverityScore = "{'$cond':[{'$gte':['$severityScore',100]},2,{'$cond':[{'$gte':['$severityScore',10]},1,{'$cond':[{'$gt':['$severityScore',0]},0.5,0]}]}]}";
        String computedAccessTypeScore = "{ '$cond': { 'if': { '$and': [ { '$gt': [ { '$size': '$apiAccessTypes' }, 0 ] }, { '$in': ['PUBLIC', '$apiAccessTypes'] } ] }, 'then': 1, 'else': 0 } }";
        String computedLastSeenScore = "{ '$cond': [ { '$gte': ['$lastSeen', " +  oneMonthBefore + " ] }, 1, 0 ] }";
        String computedIsSensitiveScore = "{ '$cond': [ { '$eq': ['$isSensitive', true] }, 1, 0 ] }";

        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.project(
            Projections.fields(
                Projections.include("_id"),
                Projections.computed("sensitiveScore",Document.parse(computedIsSensitiveScore)),
                Projections.computed("isNewScore",Document.parse(computedLastSeenScore)),
                Projections.computed("accessTypeScore",Document.parse(computedAccessTypeScore)),
                Projections.computed("severityScore",Document.parse(computedSeverityScore))
            )
        ));

        String computedRiskScore = "{ '$add': ['$sensitiveScore', '$isNewScore', '$accessTypeScore', '$severityScore']}";

        pipeline.add(
            Aggregates.project(
                Projections.fields(
                    Projections.include("_id"),
                    Projections.computed("riskScore", Document.parse(computedRiskScore))
                )
            )
        );
        return pipeline;
    }

    @Override
    public String getCollName() {
        return "api_info";
    }

    @Override
    public Class<ApiInfo> getClassT() {
        return ApiInfo.class;
    }

    public static Bson getFilter(ApiInfo.ApiInfoKey apiInfoKey) {
        return getFilter(apiInfoKey.getUrl(), apiInfoKey.getMethod().name(), apiInfoKey.getApiCollectionId());
    }

    public static Bson getFilter(String url, String method, int apiCollectionId) {
        return Filters.and(
                Filters.eq("_id.url", url),
                Filters.eq("_id.method", method),
                Filters.eq("_id.apiCollectionId", apiCollectionId)
        );
    }

}

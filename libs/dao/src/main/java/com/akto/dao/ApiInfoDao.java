package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;

import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApiInfoDao extends AccountsContextDao<ApiInfo>{

    public static ApiInfoDao instance = new ApiInfoDao();

    public static final String ID = "_id.";

    public void createIndicesIfAbsent() {

        boolean exists = false;
        for (String col: clients[0].getDatabase(Context.accountId.get()+"").listCollectionNames()){
            if (getCollName().equalsIgnoreCase(col)){
                exists = true;
                break;
            }
        };

        if (!exists) {
            clients[0].getDatabase(Context.accountId.get()+"").createCollection(getCollName());
        }

        String[] fieldNames = {"_id." + ApiInfo.ApiInfoKey.API_COLLECTION_ID};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);

        fieldNames = new String[]{"_id." + ApiInfo.ApiInfoKey.URL};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] { SingleTypeInfo._COLLECTION_IDS, ApiInfo.ID_URL }, true);
        
        fieldNames = new String[]{"_id." + ApiInfo.ApiInfoKey.API_COLLECTION_ID, "_id." + ApiInfo.ApiInfoKey.URL};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);

        fieldNames = new String[]{ApiInfo.ID_API_COLLECTION_ID, ApiInfo.LAST_SEEN};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{ApiInfo.LAST_TESTED};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] { SingleTypeInfo._COLLECTION_IDS, ApiInfo.ID_URL }, true);

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] {ApiInfo.SEVERITY_SCORE }, false);
    }
    

    public void updateLastTestedField(ApiInfoKey apiInfoKey){
        instance.getMCollection().updateOne(
            getFilter(apiInfoKey), 
            Updates.set(ApiInfo.LAST_TESTED, Context.now())
        );
    }

    public Map<Integer,Integer> getCoverageCount(){
        Map<Integer,Integer> result = new HashMap<>();
        List<Bson> pipeline = new ArrayList<>();
        int oneMonthAgo = Context.now() - Constants.ONE_MONTH_TIMESTAMP ;
        pipeline.add(Aggregates.match(Filters.gte("lastTested", oneMonthAgo)));

        BasicDBObject groupedId2 = new BasicDBObject("apiCollectionId", "$_id.apiCollectionId");
        pipeline.add(Aggregates.group(groupedId2, Accumulators.sum("count",1)));
        pipeline.add(Aggregates.project(
            Projections.fields(
                Projections.include("count"),
                Projections.computed("apiCollectionId", "$_id.apiCollectionId")
            )
        ));

        MongoCursor<BasicDBObject> collectionsCursor = ApiInfoDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
        while(collectionsCursor.hasNext()){
            try {
                BasicDBObject basicDBObject = collectionsCursor.next();
                result.put(basicDBObject.getInt("apiCollectionId"), basicDBObject.getInt("count"));
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
        pipeline.add(Aggregates.sort(Sorts.orderBy(Sorts.descending(ApiInfo.ID_API_COLLECTION_ID), Sorts.descending(ApiInfo.LAST_SEEN))));
        pipeline.add(Aggregates.group(groupedId, Accumulators.first(ApiInfo.LAST_SEEN, "$lastSeen")));
        
        MongoCursor<ApiInfo> cursor = ApiInfoDao.instance.getMCollection().aggregate(pipeline, ApiInfo.class).cursor();
        while(cursor.hasNext()){
            try {
               ApiInfo apiInfo = cursor.next();
               result.put(apiInfo.getId().getApiCollectionId(), apiInfo.getLastSeen());
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

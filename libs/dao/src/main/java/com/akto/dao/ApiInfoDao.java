package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunResult;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;

import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApiInfoDao extends AccountsContextDao<ApiInfo>{

    public static ApiInfoDao instance = new ApiInfoDao();

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
        
        MongoCursor<Document> cursor = instance.getMCollection().listIndexes().cursor();
        int counter = 0;
        while (cursor.hasNext()) {
            counter++;
            cursor.next();
        }

        if (counter == 1) {
            String[] fieldNames = {"_id.apiCollectionId"};
            ApiInfoDao.instance.getMCollection().createIndex(Indexes.ascending(fieldNames));    
            counter++;
        }

        if (counter == 2) {
            String[] fieldNames = {"_id.url"};
            ApiInfoDao.instance.getMCollection().createIndex(Indexes.ascending(fieldNames));    
            counter++;
        }

        if (counter == 3) {
            String[] fieldNames = {"_id.apiCollectionId", "_id.url"};
            ApiInfoDao.instance.getMCollection().createIndex(Indexes.ascending(fieldNames));    
            counter++;
        }

        if (counter == 4) {
            String[] fieldNames = {"lastTested"};
            ApiInfoDao.instance.getMCollection().createIndex(Indexes.ascending(fieldNames));    
            counter++;
        }
    }

    private boolean hasApiHitTest(List<TestingRunResult> testingRunResults){
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
        if(hasApiHitTest(testingRunResults)){
            UpdateOptions updateOptions = new UpdateOptions();

            System.out.println("should be atleast one");
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

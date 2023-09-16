package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunResult;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;

import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

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
                    Updates.setOnInsert("allAuthTypesFound", new ArrayList<>())
                ),
                updateOptions
            ) ;
        }
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

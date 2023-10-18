package com.akto.dao.testing_run_findings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.crypto.dsig.spec.XPathType.Filter;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.util.enums.MongoDBEnums;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;

public class TestingRunIssuesDao extends AccountsContextDao<TestingRunIssues> {

    public static final TestingRunIssuesDao instance = new TestingRunIssuesDao();

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
            String[] fieldNames = {"_id.apiInfoKey.apiCollectionId", TestingRunIssues.TEST_RUN_ISSUES_STATUS};
            TestingRunIssuesDao.instance.getMCollection().createIndex(Indexes.ascending(fieldNames));    
            counter++;
        }

        if(counter == 2){
            String[] fieldNames = {TestingRunIssues.TEST_RUN_ISSUES_STATUS};
            TestingRunIssuesDao.instance.getMCollection().createIndex(Indexes.ascending(fieldNames));    
            counter++;
        }
        if(counter == 3){
            String[] fieldNames = {TestingRunIssues.LAST_SEEN};
            TestingRunIssuesDao.instance.getMCollection().createIndex(Indexes.descending(fieldNames));    
            counter++;
        }
    }

    public Map<Integer,Map<String,Integer>> getSeveritiesMapForCollections(){
        Map<Integer,Map<String,Integer>> resultMap = new HashMap<>() ;
        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, "OPEN")));

        BasicDBObject groupedId = new BasicDBObject("apiCollectionId", "$_id.apiInfoKey.apiCollectionId")
                                                .append("severity", "$severity") ;

        pipeline.add(Aggregates.group(groupedId, Accumulators.sum("count", 1)));

        MongoCursor<BasicDBObject> severitiesCursor = TestingRunIssuesDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
        while(severitiesCursor.hasNext()) {
            try {
                BasicDBObject basicDBObject = severitiesCursor.next();
                String severity = ((BasicDBObject) basicDBObject.get("_id")).getString("severity");
                int apiCollectionId = ((BasicDBObject) basicDBObject.get("_id")).getInt("apiCollectionId");
                int count = basicDBObject.getInt("count");
                if(resultMap.containsKey(apiCollectionId)){
                    Map<String,Integer> severityMap = resultMap.get(apiCollectionId);
                    severityMap.put(severity, count);
                }else{
                    Map<String,Integer> severityMap = new HashMap<>();
                    severityMap.put(severity, count);
                    resultMap.put(apiCollectionId, severityMap);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return resultMap;
    }
  
    public Map<String, Integer> getTotalSubcategoriesCountMap(int startTimeStamp, int endTimeStamp){
        List<Bson> pipeline = new ArrayList<>();

        pipeline.add(Aggregates.match(Filters.and(
                Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, "OPEN"),
                Filters.lte(TestingRunIssues.LAST_SEEN, endTimeStamp),
                Filters.gte(TestingRunIssues.LAST_SEEN, startTimeStamp)
            )
        ));
        BasicDBObject groupedId = new BasicDBObject("subCategory", "$_id.testSubCategory");
        pipeline.add(Aggregates.group(groupedId, Accumulators.sum("count", 1)));

        Map<String,Integer> result = new HashMap<>();
        MongoCursor<BasicDBObject> severitiesCursor = TestingRunIssuesDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
        while(severitiesCursor.hasNext()) {
            try {
                BasicDBObject basicDBObject = severitiesCursor.next();
                String subCategory = ((BasicDBObject) basicDBObject.get("_id")).getString("subCategory");
                Integer count = basicDBObject.getInt("count");
                result.put(subCategory, count);
            }catch(Exception e){
                e.printStackTrace();
            }
        }

        return result;
    }

    public Map<String, Integer> getTotalSeveritiesCountMap(int startTimeStamp, int endTimeStamp){
        List<Bson> pipeline = new ArrayList<>();

        pipeline.add(Aggregates.match(Filters.and(
                Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, "OPEN"),
                Filters.lte(TestingRunIssues.LAST_SEEN, endTimeStamp),
                Filters.gte(TestingRunIssues.LAST_SEEN, startTimeStamp)
            )
        ));
        BasicDBObject groupedId = new BasicDBObject("severity", "$severity");
        pipeline.add(Aggregates.group(groupedId, Accumulators.sum("count", 1)));

        Map<String,Integer> result = new HashMap<>();
        MongoCursor<BasicDBObject> severitiesCursor = TestingRunIssuesDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
        while(severitiesCursor.hasNext()) {
            try {
                BasicDBObject basicDBObject = severitiesCursor.next();
                String severity = ((BasicDBObject) basicDBObject.get("_id")).getString("severity");
                Integer count = basicDBObject.getInt("count");
                result.put(severity, count);
            }catch(Exception e){
                e.printStackTrace();
            }
        }

        return result;
    }

    private TestingRunIssuesDao() {}
    @Override
    public String getCollName() {
        return MongoDBEnums.Collection.TESTING_RUN_ISSUES.getCollectionName();
    }

    @Override
    public Class<TestingRunIssues> getClassT() {
        return TestingRunIssues.class;
    }
}

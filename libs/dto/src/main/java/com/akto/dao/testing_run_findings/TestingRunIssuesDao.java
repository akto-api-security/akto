package com.akto.dao.testing_run_findings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.dao.AccountsContextDao;
import org.bson.conversions.Bson;

import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.util.enums.MongoDBEnums;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

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

        String[] fieldNames = {TestingRunIssues.TEST_RUN_ISSUES_STATUS, TestingRunIssues.CREATION_TIME};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);
        
        fieldNames = new String[]{TestingRunIssues.TEST_RUN_ISSUES_STATUS, "_id.apiInfoKey.apiCollectionId"};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);    
        
        fieldNames = new String[]{TestingRunIssues.TEST_RUN_ISSUES_STATUS, TestingRunIssues.LAST_SEEN};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[] {TestingRunIssues.TEST_RUN_ISSUES_STATUS};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);
        fieldNames = new String[] {TestingRunIssues.LATEST_TESTING_RUN_SUMMARY_ID};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);
    
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
                int count = basicDBObject.getInt("count");
                result.put(subCategory, count);
            }catch(Exception e){
                e.printStackTrace();
            }
        }

        return result;
    }

    public List<Bson> buildPipelineForCalculatingTrend(int startTimestamp, int endTimestamp){
        // this functions make a pipeline for calculating a list map to the epoch value in day.
        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(Filters.gte(TestingRunIssues.LAST_SEEN, startTimestamp)));
        pipeline.add(Aggregates.match(Filters.lte(TestingRunIssues.LAST_SEEN, endTimestamp)));
        pipeline.add(Aggregates.project(Projections.computed("dayOfYearFloat", new BasicDBObject("$divide", new Object[]{"$lastSeen", 86400}))));
        pipeline.add(Aggregates.project(Projections.computed("dayOfYear", new BasicDBObject("$floor", new Object[]{"$dayOfYearFloat"}))));

        BasicDBObject groupedId = new BasicDBObject("dayOfYear", "$dayOfYear").append("subCategory", "$_id.testSubCategory");
        pipeline.add(Aggregates.group(groupedId, Accumulators.sum("count", 1)));

        BasicDBObject bd = new BasicDBObject("subCategory", "$_id.subCategory").append("count", "$count");
        pipeline.add(Aggregates.group("$_id.dayOfYear", Accumulators.addToSet("issuesTrend", bd)));

        return pipeline;
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

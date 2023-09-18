package com.akto.dao.testing_run_findings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.crypto.dsig.spec.XPathType.Filter;

import org.bson.conversions.Bson;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.util.enums.MongoDBEnums;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;

public class TestingRunIssuesDao extends AccountsContextDao<TestingRunIssues> {

    public static final TestingRunIssuesDao instance = new TestingRunIssuesDao();

    public Map<Integer,Map<String,Integer>> getSeveritiesMapForCollections(){
        Map<Integer,Map<String,Integer>> resultMap = new HashMap<>() ;
        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(Filters.eq("testRunIssueStatus", "OPEN")));

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

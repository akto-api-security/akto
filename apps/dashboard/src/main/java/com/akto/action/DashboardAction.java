package com.akto.action;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.conversions.Bson;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.opensymphony.xwork2.Action;

public class DashboardAction extends UserAction {

    private Map<Integer,Integer> riskScoreCountMap = new HashMap<>();
    private int startTimeStamp;
    private int endTimeStamp;
    private Map<Integer,List<Object>> issuesTrendMap = new HashMap<>() ;


    private static final LoggerMaker loggerMaker = new LoggerMaker(DashboardAction.class);

    private static boolean isBetween(int low, int high, double score){
        return (score >= low && score < high) ;
    }
    
    public String fetchRiskScoreCountMap(){
        List<Bson> pipeline = ApiInfoDao.instance.buildRiskScorePipeline();
        Map<Integer, Integer> riskScoreCounts = new HashMap<>();
        MongoCursor<BasicDBObject> apiCursor = ApiInfoDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
        while(apiCursor.hasNext()){
            try {
                BasicDBObject basicDBObject = apiCursor.next();
                double riskScore = basicDBObject.getDouble("riskScore");
                if (isBetween(0, 2, riskScore)) {
                    riskScoreCounts.put(2, riskScoreCounts.getOrDefault(2,0) + 1);
                } else if (isBetween(2, 3, riskScore)) {
                    riskScoreCounts.put(3, riskScoreCounts.getOrDefault(3,0) + 1);
                } else if (isBetween(3, 4, riskScore)) {
                    riskScoreCounts.put(4, riskScoreCounts.getOrDefault(4,0) + 1);
                } else {
                    riskScoreCounts.put(5, riskScoreCounts.getOrDefault(5,0) + 1);
                }
            }catch (Exception e) {
                loggerMaker.errorAndAddToDb("error in calculating risk score count " + e.toString(), LogDb.DASHBOARD);
            }
        }

        this.riskScoreCountMap = riskScoreCounts;

        return Action.SUCCESS.toUpperCase();
    }

    public String fetchIssuesTrend(){
        if(endTimeStamp == 0){
            endTimeStamp = Context.now() ;
        }

        Map<Integer,List<Object>> trendMap = new HashMap<>();

        List<Bson> pipeline = TestingRunIssuesDao.instance.buildPipelineForCalculatingTrend(startTimeStamp, endTimeStamp);
        MongoCursor<BasicDBObject> issuesCursor = TestingRunIssuesDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
        
        while(issuesCursor.hasNext()){
            try {
                BasicDBObject basicDBObject = issuesCursor.next();
                int dayEpoch = basicDBObject.getInt("_id");
                BasicDBList categoryList = ((BasicDBList) basicDBObject.get("issuesTrend"));
                trendMap.put(dayEpoch, categoryList);

            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("error in getting issues trend " + e.toString(), LogDb.DASHBOARD);
            }
        }
        this.issuesTrendMap = trendMap;
        
        return Action.SUCCESS.toUpperCase();
    }

    public Map<Integer, Integer> getRiskScoreCountMap() {
        return riskScoreCountMap;
    }

    public int getStartTimeStamp() {
        return startTimeStamp;
    }

    public void setStartTimeStamp(int startTimeStamp) {
        this.startTimeStamp = startTimeStamp;
    }

    public int getEndTimeStamp() {
        return endTimeStamp;
    }

    public void setEndTimeStamp(int endTimeStamp) {
        this.endTimeStamp = endTimeStamp;
    }

    public Map<Integer, List<Object>> getIssuesTrendMap() {
        return issuesTrendMap;
    }
    
}

package com.akto.action;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.conversions.Bson;

import com.akto.dao.AccountSettingsDao;
import com.akto.dao.ActivitiesDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.AccountSettings;
import com.akto.dto.Activity;
import com.akto.dto.ApiInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.ConnectionInfo;
import com.akto.util.IssueTrendType;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;

public class DashboardAction extends UserAction {

    private Map<Integer,Integer> riskScoreCountMap = new HashMap<>();
    private int startTimeStamp;
    private int endTimeStamp;
    private Map<Integer,List<IssueTrendType>> issuesTrendMap = new HashMap<>() ;
    private int skip;
    private List<Activity> recentActivities = new ArrayList<>();
    private int totalActivities;
    private Map<String,ConnectionInfo> integratedConnectionsInfo = new HashMap<>();
    private String connectionSkipped;

    private static final LoggerMaker loggerMaker = new LoggerMaker(DashboardAction.class);

    private static boolean isBetween(int low, int high, double score){
        return (score >= low && score < high) ;
    }
    
    // function for getting number of api in between multiple ranges to show trend on dashboard pagecalculateRiskValueForSeverity
    public String fetchRiskScoreCountMap(){
        Map<Integer, Integer> riskScoreCounts = new HashMap<>();
        MongoCursor<ApiInfo> apiCursor = ApiInfoDao.instance.getMCollection().find().projection(Projections.include("_id", ApiInfo.RISK_SCORE)).cursor();
        while(apiCursor.hasNext()){
            try {
                ApiInfo apiInfo = apiCursor.next();
                float riskScore = apiInfo.getRiskScore();
                if (isBetween(0, 3, riskScore)) {
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

        Map<Integer,List<IssueTrendType>> trendMap = new HashMap<>();

        List<Bson> pipeline = TestingRunIssuesDao.instance.buildPipelineForCalculatingTrend(startTimeStamp, endTimeStamp);
        MongoCursor<BasicDBObject> issuesCursor = TestingRunIssuesDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
        
        while(issuesCursor.hasNext()){
            try {
                BasicDBObject basicDBObject = issuesCursor.next();
                int dayEpoch = basicDBObject.getInt("_id");
                BasicDBList categoryList = ((BasicDBList) basicDBObject.get("issuesTrend"));
                List<IssueTrendType> trendList = new ArrayList<>();
                for(Object obj: categoryList){
                    BasicDBObject dbObject = (BasicDBObject) obj;
                    IssueTrendType trendObj = new IssueTrendType(dbObject.getInt("count"), dbObject.getString("subCategory"));
                    trendList.add(trendObj);
                }

                trendMap.put(dayEpoch, trendList);

            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("error in getting issues trend " + e.toString(), LogDb.DASHBOARD);
            }
        }
        this.issuesTrendMap = trendMap;
        
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchRecentActivities(){
        List<Activity> activities = ActivitiesDao.instance.fetchRecentActivitiesFeed((skip * 5), 5);
        this.recentActivities = activities;
        this.totalActivities = (int) ActivitiesDao.instance.getMCollection().countDocuments();
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchIntegratedConnections(){
        Map<String,ConnectionInfo> infoMap = AccountSettingsDao.instance.getIntegratedConnectionsInfo();
        Map<String,ConnectionInfo> finalMap = new HashMap<>();
        finalMap.put(ConnectionInfo.AUTOMATED_TRAFFIC,infoMap.getOrDefault(ConnectionInfo.AUTOMATED_TRAFFIC, new ConnectionInfo(0, false)));
        finalMap.put(ConnectionInfo.GITHUB_SSO,infoMap.getOrDefault(ConnectionInfo.GITHUB_SSO, new ConnectionInfo(0, false)));
        finalMap.put(ConnectionInfo.SLACK_ALERTS,infoMap.getOrDefault(ConnectionInfo.SLACK_ALERTS, new ConnectionInfo(0, false)));
        finalMap.put(ConnectionInfo.CI_CD_INTEGRATIONS,infoMap.getOrDefault(ConnectionInfo.CI_CD_INTEGRATIONS, new ConnectionInfo(0, false)));
        finalMap.put(ConnectionInfo.INVITE_MEMBERS,infoMap.getOrDefault(ConnectionInfo.INVITE_MEMBERS, new ConnectionInfo(0, false)));

        this.integratedConnectionsInfo = finalMap;

        return Action.SUCCESS.toUpperCase();
    }

    public String markConnectionAsSkipped(){
        if(connectionSkipped != null){
            AccountSettingsDao.instance.updateOne(AccountSettingsDao.generateFilter(), Updates.set(AccountSettings.CONNECTION_INTEGRATIONS_INFO + "." + connectionSkipped + "." + "lastSkipped", Context.now()));
            return Action.SUCCESS.toUpperCase();
        }else{
            return Action.ERROR.toUpperCase();
        }
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

    public Map<Integer, List<IssueTrendType>> getIssuesTrendMap() {
        return issuesTrendMap;
    }

    public int getSkip() {
        return skip;
    }

    public void setSkip(int skip) {
        this.skip = skip;
    }

    public List<Activity> getRecentActivities() {
        return recentActivities;
    }

    public int getTotalActivities() {
        return totalActivities;
    }

    public void setTotalActivities(int totalActivities) {
        this.totalActivities = totalActivities;
    }

    public Map<String, ConnectionInfo> getIntegratedConnectionsInfo() {
        return integratedConnectionsInfo;
    }

    public void setIntegratedConnectionsInfo(Map<String, ConnectionInfo> integratedConnectionsInfo) {
        this.integratedConnectionsInfo = integratedConnectionsInfo;
    }

    public String getConnectionSkipped() {
        return connectionSkipped;
    }

    public void setConnectionSkipped(String connectionSkipped) {
        this.connectionSkipped = connectionSkipped;
    }
    
}

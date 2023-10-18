package com.akto.action;

import java.util.*;

import javax.swing.text.html.FormSubmitEvent.MethodType;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.mortbay.util.ajax.JSON;

import com.akto.DaoInit;
import com.akto.dao.APISpecDao;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.SensitiveParamInfoDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.SensitiveInfoInApiCollections;
import com.akto.dto.AccountSettings.CronTimers;
import com.akto.util.Constants;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Projections;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.opensymphony.xwork2.Action;

public class ApiCollectionsAction extends UserAction {

    List<ApiCollection> apiCollections = new ArrayList<>();
    List<SensitiveInfoInApiCollections> sensitiveInfoInApiCollections = new ArrayList<>() ;
    Map<Integer,Integer> testedEndpointsMaps = new HashMap<>();
    Map<Integer,Integer> lastTrafficSeenMap = new HashMap<>();
    Map<Integer,Double> riskScoreOfCollectionsMap = new HashMap<>();
    int criticalEndpointsCount;
    CronTimers timerInfo;

    Map<Integer,Map<String,Integer>> severityInfo = new HashMap<>();

    int apiCollectionId;

    public String fetchAllCollections() {
        this.apiCollections = ApiCollectionsDao.instance.findAll(new BasicDBObject());

        Map<Integer, Integer> countMap = ApiCollectionsDao.instance.buildEndpointsCountToApiCollectionMap();

        for (ApiCollection apiCollection: apiCollections) {
            int apiCollectionId = apiCollection.getId();
            Integer count = countMap.get(apiCollectionId);
            if (count != null && apiCollection.getHostName() != null) {
                apiCollection.setUrlsCount(count);
            } else {
                apiCollection.setUrlsCount(apiCollection.getUrls().size());
            }
            apiCollection.setUrls(new HashSet<>());
        }

        return Action.SUCCESS.toUpperCase();
    }

    public String fetchCollection() {
        this.apiCollections = new ArrayList<>();
        this.apiCollections.add(ApiCollectionsDao.instance.findOne(Filters.eq(Constants.ID, apiCollectionId)));
        return Action.SUCCESS.toUpperCase();
    }

    static int maxCollectionNameLength = 25;
    private String collectionName;
    public String createCollection() {
        if (this.collectionName == null) {
            addActionError("Invalid collection name");
            return ERROR.toUpperCase();
        }

        if (this.collectionName.length() > maxCollectionNameLength) {
            addActionError("Custom collections max length: " + maxCollectionNameLength);
            return ERROR.toUpperCase();
        }

        for (char c: this.collectionName.toCharArray()) {
            boolean alphabets = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
            boolean numbers = c >= '0' && c <= '9';
            boolean specialChars = c == '-' || c == '.' || c == '_';
            boolean spaces = c == ' ';

            if (!(alphabets || numbers || specialChars || spaces)) {
                addActionError("Collection names can only be alphanumeric and contain '-','.' and '_'");
                return ERROR.toUpperCase();
            }
        }

        // unique names
        ApiCollection sameNameCollection = ApiCollectionsDao.instance.findByName(collectionName);
        if (sameNameCollection != null){
            addActionError("Collection names must be unique");
            return ERROR.toUpperCase();
        }

        // do not change hostName or vxlanId here
        ApiCollection apiCollection = new ApiCollection(Context.now(), collectionName,Context.now(),new HashSet<>(), null, 0);
        ApiCollectionsDao.instance.insertOne(apiCollection);
        this.apiCollections = new ArrayList<>();
        this.apiCollections.add(apiCollection);
        return Action.SUCCESS.toUpperCase();
    }

    public String deleteCollection() {
        
        this.apiCollections = new ArrayList<>();
        this.apiCollections.add(new ApiCollection(apiCollectionId, null, 0, null, null, 0));
        return this.deleteMultipleCollections();
    } 

    public String deleteMultipleCollections() {
        List<Integer> apiCollectionIds = new ArrayList<>();
        for(ApiCollection apiCollection: this.apiCollections) {
            if(apiCollection.getId() == 0) {
                continue;
            }
            apiCollectionIds.add(apiCollection.getId());
        }

        ApiCollectionsDao.instance.deleteAll(Filters.in("_id", apiCollectionIds));
        SingleTypeInfoDao.instance.deleteAll(Filters.in("apiCollectionId", apiCollectionIds));
        APISpecDao.instance.deleteAll(Filters.in("apiCollectionId", apiCollectionIds));
        SensitiveParamInfoDao.instance.deleteAll(Filters.in("apiCollectionId", apiCollectionIds));                    

        return SUCCESS.toUpperCase();
    }

    public String fetchSensitiveInfoInCollections(){
        this.sensitiveInfoInApiCollections = SingleTypeInfoDao.instance.getSensitiveInfoForCollections() ;
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchCoverageInfoInCollections(){
        this.testedEndpointsMaps = ApiInfoDao.instance.getCoverageCount();
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchSeverityInfoInCollections(){
        this.severityInfo = TestingRunIssuesDao.instance.getSeveritiesMapForCollections();
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchLastSeenInfoInCollections(){
        this.lastTrafficSeenMap = ApiInfoDao.instance.getLastTrafficSeen();
        return Action.SUCCESS.toUpperCase();
    }

    public String riskScoreInfo(){
        int oneMonthBefore = Context.now() - (60 * 60 * 24 * 30) ;
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

        int criticalCount = 0 ;
        Map<Integer, Double> riskScoreMap = new HashMap<>();

        MongoCursor<BasicDBObject> apiCursor = ApiInfoDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
        while(apiCursor.hasNext()){
            try {
                BasicDBObject basicDBObject = apiCursor.next();
                double riskScore = basicDBObject.getDouble("riskScore");
                BasicDBObject bd = (BasicDBObject) basicDBObject.get("_id");
                Integer collectionId = bd.getInt("apiCollectionId");

                if(riskScore >= 4){
                    criticalCount++;
                }

                if(riskScoreMap.isEmpty() || !riskScoreMap.containsKey(collectionId)){
                    riskScoreMap.put(collectionId, riskScore);
                }else{
                    double prev = riskScoreMap.get(collectionId);
                    riskScore = Math.max(riskScore, prev);
                    riskScoreMap.put(collectionId, riskScore);
                }

            } catch (Exception e) {
                // TODO: handle exception
                e.printStackTrace();
            }
        }

        this.criticalEndpointsCount = criticalCount;
        this.riskScoreOfCollectionsMap = riskScoreMap;
        return Action.SUCCESS.toUpperCase();
    }
    
    public String fetchTimersInfo(){
        CronTimers timer = AccountSettingsDao.instance.getTimersInfo();
        this.timerInfo = timer;
        return Action.SUCCESS.toUpperCase();
    }

    public List<ApiCollection> getApiCollections() {
        return this.apiCollections;
    }

    public void setApiCollections(List<ApiCollection> apiCollections) {
        this.apiCollections = apiCollections;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public int getApiCollectionId() {
        return this.apiCollectionId;
    }
  
    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }
    
    public List<SensitiveInfoInApiCollections> getSensitiveInfoInApiCollections() {
        return sensitiveInfoInApiCollections;
    }

    public Map<Integer, Integer> getTestedEndpointsMaps() {
        return testedEndpointsMaps;
    }

    public Map<Integer, Map<String, Integer>> getSeverityInfo() {
        return severityInfo;
    }

    public Map<Integer, Integer> getLastTrafficSeenMap() {
        return lastTrafficSeenMap;
    }

    public int getCriticalEndpointsCount() {
        return criticalEndpointsCount;
    }

    public Map<Integer, Double> getRiskScoreOfCollectionsMap() {
        return riskScoreOfCollectionsMap;
    }

    public CronTimers getTimerInfo() {
        return timerInfo;
    }

}

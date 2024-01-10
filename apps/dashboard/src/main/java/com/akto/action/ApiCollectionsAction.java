package com.akto.action;

import java.util.*;
import java.util.stream.Collectors;

import org.bson.conversions.Bson;
import com.akto.dao.APISpecDao;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.ActivitiesDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.SensitiveParamInfoDao;
import com.akto.dao.SingleTypeInfoDao;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import com.akto.dao.*;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dao.usage.UsageMetricInfoDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.billing.Organization;
import com.akto.dto.usage.MetricTypes;
import com.akto.dto.usage.UsageMetricInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.akto.util.LastCronRunInfo;
import com.akto.utils.usage.UsageMetricCalculator;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.BasicDBObject;
import com.opensymphony.xwork2.Action;

public class ApiCollectionsAction extends UserAction {

    List<ApiCollection> apiCollections = new ArrayList<>();
    Map<Integer,Integer> testedEndpointsMaps = new HashMap<>();
    Map<Integer,Integer> lastTrafficSeenMap = new HashMap<>();
    Map<Integer,Double> riskScoreOfCollectionsMap = new HashMap<>();
    int criticalEndpointsCount;
    int sensitiveUrlsInResponse;
    Map<Integer, List<String>> sensitiveSubtypesInCollection = new HashMap<>();
    LastCronRunInfo timerInfo;

    Map<Integer,Map<String,Integer>> severityInfo = new HashMap<>();
    private static final LoggerMaker loggerMaker = new LoggerMaker(ApiCollectionsAction.class);
    int apiCollectionId;

    private static List<ApiCollection> fillApiCollectionsUrlCount(List<ApiCollection> apiCollections) {
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
        return apiCollections;
    }

    public String fetchAllCollections() {

        
        this.apiCollections = ApiCollectionsDao.instance.findAll(new BasicDBObject());
        this.apiCollections = fillApiCollectionsUrlCount(this.apiCollections);

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

        ActivitiesDao.instance.insertActivity("Collection created", "Collection named " + this.collectionName + " was created.");

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
        deleteApiEndpointData(apiCollectionIds);
        return SUCCESS.toUpperCase();
    }

    private static void deleteApiEndpointData(List<Integer> apiCollectionIds){
        SingleTypeInfoDao.instance.deleteAll(Filters.in("apiCollectionId", apiCollectionIds));
        APISpecDao.instance.deleteAll(Filters.in("apiCollectionId", apiCollectionIds));
        SensitiveParamInfoDao.instance.deleteAll(Filters.in("apiCollectionId", apiCollectionIds));
        SampleDataDao.instance.deleteAll(Filters.in("_id.apiCollectionId", apiCollectionIds));
        TrafficInfoDao.instance.deleteAll(Filters.in("_id.apiCollectionId", apiCollectionIds));
        ApiInfoDao.instance.deleteAll(Filters.in("_id.apiCollectionId", apiCollectionIds));

    }

    // required for icons and total sensitive endpoints in collections
    public String fetchSensitiveInfoInCollections(){
        List<String> sensitiveSubtypes = SingleTypeInfoDao.instance.sensitiveSubTypeInResponseNames();
        sensitiveSubtypes.addAll(SingleTypeInfoDao.instance.sensitiveSubTypeNames());

        List<String> sensitiveSubtypesInRequest = SingleTypeInfoDao.instance.sensitiveSubTypeInRequestNames();
        this.sensitiveUrlsInResponse = SingleTypeInfoDao.instance.getSensitiveApisCount(sensitiveSubtypes);

        sensitiveSubtypes.addAll(sensitiveSubtypesInRequest);
        this.sensitiveSubtypesInCollection = SingleTypeInfoDao.instance.getSensitiveSubtypesDetectedForCollection(sensitiveSubtypes);
        return Action.SUCCESS.toUpperCase();
    }

    // required to measure the count of total tested endpoints per collection.
    public String fetchCoverageInfoInCollections(){
        this.testedEndpointsMaps = ApiInfoDao.instance.getCoverageCount();
        return Action.SUCCESS.toUpperCase();
    }

    // required to measure the count of total issues per collection.
    public String fetchSeverityInfoInCollections(){
        this.severityInfo = TestingRunIssuesDao.instance.getSeveritiesMapForCollections();
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchLastSeenInfoInCollections(){
        this.lastTrafficSeenMap = ApiInfoDao.instance.getLastTrafficSeen();
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchRiskScoreInfo(){
        int criticalCount = 0 ;
        Map<Integer, Double> riskScoreMap = new HashMap<>();
        List<Bson> pipeline = ApiInfoDao.instance.buildRiskScorePipeline();
        BasicDBObject groupId = new BasicDBObject("apiCollectionId", "$_id.apiCollectionId");
        pipeline.add(Aggregates.group(groupId, 
            Accumulators.max("riskScore", "$riskScore"), 
            Accumulators.sum("criticalCounts", new BasicDBObject("$cond", Arrays.asList(new BasicDBObject("$gte", Arrays.asList("$riskScore", 4)), 1, 0)))
        ));

        MongoCursor<BasicDBObject> cursor = ApiInfoDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
        while(cursor.hasNext()){
            try {
                BasicDBObject basicDBObject = cursor.next();
                criticalCount += basicDBObject.getInt("criticalCounts");
                BasicDBObject id = (BasicDBObject) basicDBObject.get("_id");
                riskScoreMap.put(id.getInt("apiCollectionId"), basicDBObject.getDouble("riskScore"));
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("error in calculating risk score for collections " + e.toString(), LogDb.DASHBOARD);
                e.printStackTrace();
            }
        }

        this.criticalEndpointsCount = criticalCount;
        this.riskScoreOfCollectionsMap = riskScoreMap;
        return Action.SUCCESS.toUpperCase();
    }
    
    public String fetchTimersInfo(){
        try {
            LastCronRunInfo timeInfo = AccountSettingsDao.instance.getLastCronRunInfo();
            this.timerInfo = timeInfo;
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Action.ERROR.toUpperCase();
    }

    public String deactivateCollections() {

        if (apiCollections == null || apiCollections.isEmpty()) {
            addActionError("No collections selected");
            return Action.ERROR.toUpperCase();
        }
        List<Integer> apiCollectionIds = apiCollections.stream().map(apiCollection -> apiCollection.getId()).collect(Collectors.toList());
        ApiCollectionsDao.instance.updateMany(Filters.in(Constants.ID, apiCollectionIds),
                Updates.set(ApiCollection._DEACTIVATED, true));

        return Action.SUCCESS.toUpperCase();
    }

    public String activateCollections() {

        if (apiCollections == null) {
            addActionMessage("No collections selected");
            return Action.SUCCESS.toUpperCase();
        }
        apiCollections = apiCollections.stream().filter(apiCollection -> apiCollection.isDeactivated()).collect(Collectors.toList());
        if (apiCollections.isEmpty()) {
            addActionMessage("No deactivated collections selected");
            return Action.SUCCESS.toUpperCase();
        }
        apiCollections = fillApiCollectionsUrlCount(this.apiCollections);
        int accountId = Context.accountId.get();
        Organization organization = OrganizationsDao.instance.findOneByAccountId(accountId);
        if (organization == null) {
            addActionError("Organization not found");
            return Action.ERROR.toUpperCase();
        }
        String organizationId = organization.getId();
        HashMap<String, FeatureAccess> featureWiseAllowed = organization.getFeatureWiseAllowed();
        if (featureWiseAllowed == null || featureWiseAllowed.isEmpty()) {
            featureWiseAllowed = new HashMap<>();
            featureWiseAllowed.put(MetricTypes.ACTIVE_ENDPOINTS.toString(), FeatureAccess.fullAccess);
        }

        UsageMetricInfo usageMetricInfo = UsageMetricInfoDao.instance.findOneOrInsert(organizationId, accountId, MetricTypes.ACTIVE_ENDPOINTS);
        int measureEpoch = usageMetricInfo.getMeasureEpoch();

        int usageBefore = UsageMetricCalculator.calculateActiveEndpoints(measureEpoch);
        int count = apiCollections.stream().mapToInt(apiCollection -> apiCollection.getUrlsCount()).sum();

        FeatureAccess featureAccess = featureWiseAllowed.getOrDefault(MetricTypes.ACTIVE_ENDPOINTS.toString(), FeatureAccess.noAccess);
        int usageLimit = featureAccess.getUsageLimit();

        if (featureAccess.checkUnlimited() || (usageBefore + count <= usageLimit)) {
            List<Integer> apiCollectionIds = apiCollections.stream().map(apiCollection -> apiCollection.getId()).collect(Collectors.toList());
            ApiCollectionsDao.instance.updateMany(Filters.in(Constants.ID, apiCollectionIds), Updates.unset(ApiCollection._DEACTIVATED));
        } else {
            addActionError("API endpoints in collections exceeded usage limit. Unable to activate collections. Please upgrade your plan.");
            return Action.ERROR.toUpperCase();
        }

        return Action.SUCCESS.toUpperCase();
    }

    public String forceActivateCollections() {
        if (apiCollections == null) {
            addActionMessage("No collections selected");
            return Action.SUCCESS.toUpperCase();
        }
        apiCollections = apiCollections.stream().filter(apiCollection -> apiCollection.isDeactivated()).collect(Collectors.toList());
        if (apiCollections.isEmpty()) {
            addActionMessage("No deactivated collections selected");
            return Action.SUCCESS.toUpperCase();
        }

        List<Integer> apiCollectionIds = apiCollections.stream().map(apiCollection -> apiCollection.getId()).collect(Collectors.toList());
        ApiCollectionsDao.instance.updateMany(
                Filters.and(
                        Filters.in(Constants.ID, apiCollectionIds),
                        Filters.eq(ApiCollection._DEACTIVATED, true)),
                Updates.unset(ApiCollection._DEACTIVATED));
        deleteApiEndpointData(apiCollectionIds);

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
    
    public int getSensitiveUrlsInResponse() {
        return sensitiveUrlsInResponse;
    }

    public Map<Integer, List<String>> getSensitiveSubtypesInCollection() {
        return sensitiveSubtypesInCollection;
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

    public LastCronRunInfo getTimerInfo() {
        return timerInfo;
    }

}

package com.akto.action;

import java.util.*;

import org.bson.conversions.Bson;

import com.akto.action.observe.Utils;
import com.akto.dao.*;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiCollectionTestStatus;
import com.akto.dto.testing.TestingEndpoints;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Aggregates;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.ApiCollectionUsers;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.testing.CustomTestingEndpoints;
import com.akto.dto.CollectionConditions.ConditionUtils;
import com.akto.dto.billing.Organization;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.usage.MetricTypes;
import com.akto.dto.usage.UsageMetric;
import com.akto.listener.RuntimeListener;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.akto.util.LastCronRunInfo;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Sorts;
import com.opensymphony.xwork2.Action;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.Document;

public class ApiCollectionsAction extends UserAction {

    List<ApiCollection> apiCollections = new ArrayList<>();
    List<ApiCollectionTestStatus> apiCollectionTestStatus = new ArrayList<>();
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
    List<ApiInfoKey> apiList;

    private boolean hasUsageEndpoints;

    public List<ApiInfoKey> getApiList() {
        return apiList;
    }

    public void setApiList(List<ApiInfoKey> apiList) {
        this.apiList = apiList;
    }

    public String fetchAllCollections() {
        this.apiCollections = ApiCollectionsDao.instance.findAll(new BasicDBObject());
        this.apiCollectionTestStatus = new ArrayList<>();

        Map<Integer, Integer> countMap = ApiCollectionsDao.instance.buildEndpointsCountToApiCollectionMap();
        Map<Integer, Pair<String,Integer>> map = new HashMap<>();
        try (MongoCursor<BasicDBObject> cursor = TestingRunDao.instance.getMCollection().aggregate(
                Arrays.asList(
                        Aggregates.match(Filters.eq("testingEndpoints.type", TestingEndpoints.Type.COLLECTION_WISE.name())),
                        Aggregates.sort(Sorts.descending(Arrays.asList("testingEndpoints.apiCollectionId", "endTimestamp"))),
                        new Document("$group", new Document("_id", "$testingEndpoints.apiCollectionId")
                                .append("latestRun", new Document("$first", "$$ROOT")))
                ), BasicDBObject.class
        ).cursor()) {
            while (cursor.hasNext()) {
                BasicDBObject basicDBObject = cursor.next();
                BasicDBObject latestRun = (BasicDBObject) basicDBObject.get("latestRun");
                String state = latestRun.getString("state");
                int endTimestamp = latestRun.getInt("endTimestamp", -1);
                int collectionId = ((BasicDBObject)latestRun.get("testingEndpoints")).getInt("apiCollectionId");
                map.put(collectionId, Pair.of(state, endTimestamp));
            }
        }

        for (ApiCollection apiCollection: apiCollections) {
            int apiCollectionId = apiCollection.getId();
            Integer count = countMap.get(apiCollectionId);
            int fallbackCount = apiCollection.getUrls()!=null ? apiCollection.getUrls().size() : 0;
            if (count != null && (apiCollection.getHostName() != null)) {
                apiCollection.setUrlsCount(count);
            } else if(ApiCollection.Type.API_GROUP.equals(apiCollection.getType())){
                count = Utils.countEndpoints(Filters.in(SingleTypeInfo._COLLECTION_IDS, apiCollectionId));
                apiCollection.setUrlsCount(count);
            } else {
                apiCollection.setUrlsCount(fallbackCount);
            }
            if(map.containsKey(apiCollectionId)) {
                Pair<String, Integer> pair = map.get(apiCollectionId);
                apiCollectionTestStatus.add(new ApiCollectionTestStatus(apiCollection.getId(), pair.getRight(), pair.getLeft()));
            } else {
                apiCollectionTestStatus.add(new ApiCollectionTestStatus(apiCollection.getId(), -1, null));
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

    private boolean isValidApiCollectionName(){
        if (this.collectionName == null) {
            addActionError("Invalid collection name");
            return false;
        }

        if (this.collectionName.length() > maxCollectionNameLength) {
            addActionError("Custom collections max length: " + maxCollectionNameLength);
            return false;
        }

        for (char c: this.collectionName.toCharArray()) {
            boolean alphabets = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
            boolean numbers = c >= '0' && c <= '9';
            boolean specialChars = c == '-' || c == '.' || c == '_';
            boolean spaces = c == ' ';

            if (!(alphabets || numbers || specialChars || spaces)) {
                addActionError("Collection names can only be alphanumeric and contain '-','.' and '_'");
                return false;
            }
        }

        // unique names
        ApiCollection sameNameCollection = ApiCollectionsDao.instance.findByName(collectionName);
        if (sameNameCollection != null){
            addActionError("Collection names must be unique");
            return false;
        }

        return true;
    }

    public String createCollection() {
        
        if(!isValidApiCollectionName()){
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

        Bson filter = Filters.in(SingleTypeInfo._COLLECTION_IDS, apiCollectionIds);
        Bson update = Updates.pullAll(SingleTypeInfo._COLLECTION_IDS, apiCollectionIds);

        SingleTypeInfoDao.instance.deleteAll(Filters.in("apiCollectionId", apiCollectionIds));
        SingleTypeInfoDao.instance.updateMany(filter, update);
        APISpecDao.instance.deleteAll(Filters.in("apiCollectionId", apiCollectionIds));
        SensitiveParamInfoDao.instance.deleteAll(Filters.in("apiCollectionId", apiCollectionIds));
        SampleDataDao.instance.deleteAll(Filters.in("_id.apiCollectionId", apiCollectionIds));
        SensitiveSampleDataDao.instance.deleteAll(Filters.in("_id.apiCollectionId", apiCollectionIds));
        TrafficInfoDao.instance.deleteAll(Filters.in("_id.apiCollectionId", apiCollectionIds));
        ApiInfoDao.instance.deleteAll(Filters.in("_id.apiCollectionId", apiCollectionIds));
        SensitiveParamInfoDao.instance.updateMany(filter, update);

        List<ApiCollection> apiGroups = ApiCollectionsDao.instance.findAll(Filters.eq(ApiCollection._TYPE, ApiCollection.Type.API_GROUP.toString()));
        for(ApiCollection collection: apiGroups){
            List<TestingEndpoints> conditions = collection.getConditions();
            for (TestingEndpoints it : conditions) {
                switch (it.getType()) {
                    case CUSTOM:
                        Set<ApiInfoKey> tmp = new HashSet<>(it.returnApis());
                        tmp.removeIf((ApiInfoKey key) -> apiCollectionIds.contains(key.getApiCollectionId()));
                        ((CustomTestingEndpoints) it).setApisList(new ArrayList<>(tmp));
                        break;
                    default:
                        break;
                }
            }
            ApiCollectionUsers.updateApiCollection(collection.getConditions(), collection.getId());
        }
        return SUCCESS.toUpperCase();
    }

    public String addApisToCustomCollection(){

        if(apiList.isEmpty()){
            addActionError("No APIs selected");
            return ERROR.toUpperCase();
        }

        ApiCollection apiCollection = ApiCollectionsDao.instance.findByName(collectionName);
        if(apiCollection == null){
            
            if(!isValidApiCollectionName()){
                return ERROR.toUpperCase();
            }

            apiCollection = new ApiCollection(Context.now(), collectionName, new ArrayList<>() );
            ApiCollectionsDao.instance.insertOne(apiCollection);

        } else if(!ApiCollection.Type.API_GROUP.equals(apiCollection.getType())){
            addActionError("Invalid api collection group.");
            return ERROR.toUpperCase();
        }

        CustomTestingEndpoints condition = new CustomTestingEndpoints(apiList, CustomTestingEndpoints.Operator.OR);
        apiCollection.addToConditions(condition);
        ApiCollectionUsers.updateApiCollection(apiCollection.getConditions(), apiCollection.getId());
        ApiCollectionUsers.addToCollectionsForCollectionId(apiCollection.getConditions(), apiCollection.getId());

        fetchAllCollections();

        return SUCCESS.toUpperCase();
    }

    public String removeApisFromCustomCollection(){

        if(apiList.isEmpty()){
            addActionError("No APIs selected");
            return ERROR.toUpperCase();
        }

        ApiCollection apiCollection = ApiCollectionsDao.instance.findByName(collectionName);
        if(apiCollection == null || !ApiCollection.Type.API_GROUP.equals(apiCollection.getType())){
            addActionError("Invalid api collection group");
            return ERROR.toUpperCase();
        }

        CustomTestingEndpoints condition = new CustomTestingEndpoints(apiList, CustomTestingEndpoints.Operator.OR);
        apiCollection.removeFromConditions(condition);
        ApiCollectionUsers.updateApiCollection(apiCollection.getConditions(), apiCollection.getId());
        ApiCollectionUsers.removeFromCollectionsForCollectionId(apiCollection.getConditions(), apiCollection.getId());

        fetchAllCollections();
    
        return SUCCESS.toUpperCase();
    }

    List<ConditionUtils> conditions;

    private static List<TestingEndpoints> generateConditions(List<ConditionUtils> conditions){
        List<TestingEndpoints> ret = new ArrayList<>();

        if (conditions != null) {
            for (ConditionUtils conditionUtils : conditions) {
                TestingEndpoints condition = TestingEndpoints.generateCondition(conditionUtils.getType(),
                        conditionUtils.getOperator(), conditionUtils.getData());
                if (condition != null) {
                    ret.add(condition);
                }
            }
        }
        return ret;
    }

    public String createCustomCollection() {
        if (!isValidApiCollectionName()) {
            return ERROR.toUpperCase();
        }

        List<TestingEndpoints> conditions = generateConditions(this.conditions);

        ApiCollection apiCollection = new ApiCollection(Context.now(), collectionName, conditions);
        ApiCollectionsDao.instance.insertOne(apiCollection);
        
        ApiCollectionUsers.computeCollectionsForCollectionId(apiCollection.getConditions(), apiCollection.getId());
        
        this.apiCollections = new ArrayList<>();
        this.apiCollections.add(apiCollection);

        return SUCCESS.toUpperCase();
    }

    int apiCount;

    public String getEndpointsFromConditions(){
        List<TestingEndpoints> conditions = generateConditions(this.conditions);

        apiCount = ApiCollectionUsers.getApisCountFromConditions(conditions);
    
        return SUCCESS.toUpperCase();
    }

    public String computeCustomCollections(){
        
        ApiCollection apiCollection = ApiCollectionsDao.instance.findByName(collectionName);
        if(apiCollection == null || !ApiCollection.Type.API_GROUP.equals(apiCollection.getType())){
            addActionError("Invalid api collection group");
            return ERROR.toUpperCase();
        }

        ApiCollectionUsers.computeCollectionsForCollectionId(apiCollection.getConditions(), apiCollection.getId());

        return SUCCESS.toUpperCase();
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

    public String fetchCustomerEndpoints(){
        try {
            ApiCollection juiceShop = ApiCollectionsDao.instance.findByName("juice_shop_demo");
            ArrayList<Integer> demos = new ArrayList<>();
            demos.add(RuntimeListener.VULNERABLE_API_COLLECTION_ID);
            demos.add(RuntimeListener.LLM_API_COLLECTION_ID);
            if (juiceShop != null) {
                demos.add(juiceShop.getId());
            }

            Bson filter = Filters.nin(SingleTypeInfo._API_COLLECTION_ID, demos);
            this.hasUsageEndpoints = SingleTypeInfoDao.instance.findOne(filter) != null;

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Action.ERROR.toUpperCase();
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

    public List<ApiCollectionTestStatus> getApiCollectionTestStatus() {
        return apiCollectionTestStatus;
    }

    public void setApiCollectionTestStatus(List<ApiCollectionTestStatus> apiCollectionTestStatus) {
        this.apiCollectionTestStatus = apiCollectionTestStatus;
    }

    public List<ConditionUtils> getConditions() {
        return conditions;
    }

    public void setConditions(List<ConditionUtils> conditions) {
        this.conditions = conditions;
    }

    public int getApiCount() {
        return apiCount;
    }

    public void setApiCount(int apiCount) {
        this.apiCount = apiCount;
    }

    public boolean getHasUsageEndpoints() {
        return hasUsageEndpoints;
    }

}

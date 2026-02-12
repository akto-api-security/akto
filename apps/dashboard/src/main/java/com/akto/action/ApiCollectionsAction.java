package com.akto.action;

import java.util.*;
import java.util.stream.Collectors;

import org.bson.conversions.Bson;

import com.akto.dao.*;
import com.akto.billing.UsageMetricUtils;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.usage.MetricTypes;
import com.akto.dto.ApiCollectionTestStatus;
import com.akto.dto.testing.TestingEndpoints;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Aggregates;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.ApiCollectionUsers;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.testing.CustomTestingEndpoints;
import com.akto.dto.CollectionConditions.ConditionUtils;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.listener.RuntimeListener;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.usage.UsageMetricHandler;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiCollection.ENV_TYPE;
import com.akto.util.Constants;
import com.akto.util.LastCronRunInfo;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.mongodb.BasicDBObject;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UnwindOptions;
import com.opensymphony.xwork2.Action;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.Document;

public class ApiCollectionsAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ApiCollectionsAction.class);

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
    int apiCollectionId;
    List<ApiInfoKey> apiList;

    private boolean hasUsageEndpoints;

    public List<ApiInfoKey> getApiList() {
        return apiList;
    }

    public void setApiList(List<ApiInfoKey> apiList) {
        this.apiList = apiList;
    }

    boolean redacted;
    
    public List<ApiCollection> fillApiCollectionsUrlCount(List<ApiCollection> apiCollections) {
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
                count = SingleTypeInfoDao.instance.countEndpoints(Filters.in(SingleTypeInfo._COLLECTION_IDS, apiCollectionId));
                apiCollection.setUrlsCount(count);
            } else {
                /*
                 * In case the default collection is filled by traffic-collector traffic, 
                 * the count will not be null, but the fallbackCount would be zero
                 */
                if (apiCollectionId == 0 && count != null) {
                    fallbackCount = count;
                }
                if (fallbackCount == 0 && count != null) {
                    fallbackCount = count;
                }
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
        return apiCollections;
    }

    public String fetchAllCollections() {

        this.apiCollections = ApiCollectionsDao.instance.findAll(new BasicDBObject());
        this.apiCollections = fillApiCollectionsUrlCount(this.apiCollections);

        return Action.SUCCESS.toUpperCase();
    }

    public String fetchAllCollectionsBasic(){
        this.apiCollections = ApiCollectionsDao.instance.findAll(
            new BasicDBObject(), 
            Projections.include(ApiCollection.ID, ApiCollection.NAME, ApiCollection.HOST_NAME)
        );
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
        ApiCollection apiCollection = new ApiCollection(Context.now(), collectionName,Context.now(),new HashSet<>(), null, 0, false, true);
        ApiCollectionsDao.instance.insertOne(apiCollection);
        this.apiCollections = new ArrayList<>();
        this.apiCollections.add(apiCollection);

        ActivitiesDao.instance.insertActivity("Collection created", "new Collection " + this.collectionName + " created");

        return Action.SUCCESS.toUpperCase();
    }

    public String deleteCollection() {
        
        this.apiCollections = new ArrayList<>();
        this.apiCollections.add(new ApiCollection(apiCollectionId, null, 0, null, null, 0, false, true));
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
        DeleteResult apiInfoDeleteResult = ApiInfoDao.instance.deleteAll(Filters.in("_id.apiCollectionId", apiCollectionIds));
        SensitiveParamInfoDao.instance.updateMany(filter, update);

        /*
         * This delta might not be accurate, since it may also include deletions from
         * deactivated/demo collections or old collections, which were not being used
         * for usage calculation
         * Any inaccuracies here, would be fixed in the next calcUsage cycle (4 hrs)
         */
        int deltaUsage = -1 * (int) apiInfoDeleteResult.getDeletedCount();
        UsageMetricHandler.calcAndFetchFeatureAccessUsingDeltaUsage(MetricTypes.ACTIVE_ENDPOINTS, Context.accountId.get(), deltaUsage);

        List<ApiCollection> apiGroups = ApiCollectionsDao.instance.fetchApiGroups();
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

    public static void dropSampleDataForApiCollection() {
        List<ApiCollection> apiCollections = ApiCollectionsDao.instance.findAll(Filters.eq(ApiCollection.SAMPLE_COLLECTIONS_DROPPED, false));
        if(apiCollections.isEmpty()) {
            loggerMaker.infoAndAddToDb("No api collections to fix sample data for", LoggerMaker.LogDb.DASHBOARD);
            return;
        }
        loggerMaker.infoAndAddToDb(String.format("Fixing sample data for %d api collections", apiCollections.size()), LoggerMaker.LogDb.DASHBOARD);
        for (ApiCollection apiCollection: apiCollections) {
            int apiCollectionId = apiCollection.getId();
            UpdateResult updateResult = SampleDataDao.instance.updateManyNoUpsert(Filters.eq("_id.apiCollectionId", apiCollectionId), Updates.set("samples", Collections.emptyList()));
            loggerMaker.infoAndAddToDb(String.format("Fixed %d sample data for api collection %d", updateResult.getModifiedCount(), apiCollectionId), LoggerMaker.LogDb.DASHBOARD);
            updateResult = SensitiveSampleDataDao.instance.updateManyNoUpsert(Filters.eq("_id.apiCollectionId", apiCollectionId), Updates.set("sampleData", Collections.emptyList()));
            loggerMaker.infoAndAddToDb(String.format("Fixed %d sensitive sample data for api collection %d", updateResult.getModifiedCount(), apiCollectionId), LoggerMaker.LogDb.DASHBOARD);
            updateResult = SingleTypeInfoDao.instance.updateManyNoUpsert(Filters.and(Filters.eq("apiCollectionId", apiCollectionId), Filters.exists("values", true)), Updates.set("values.elements", Collections.emptyList()));
            loggerMaker.infoAndAddToDb(String.format("Fixed %d sti for api collection %d", updateResult.getModifiedCount(), apiCollectionId), LoggerMaker.LogDb.DASHBOARD);
            ApiCollectionsDao.instance.updateOneNoUpsert(Filters.eq("_id", apiCollectionId), Updates.set(ApiCollection.SAMPLE_COLLECTIONS_DROPPED, true));
        }
        loggerMaker.infoAndAddToDb(String.format("Fixed sample data for %d api collections", apiCollections.size()), LoggerMaker.LogDb.DASHBOARD);
    }

    public String redactCollection() {
        List<Bson> updates = Arrays.asList(
            Updates.set(ApiCollection.REDACT, redacted),
            Updates.set(ApiCollection.SAMPLE_COLLECTIONS_DROPPED, !redacted)
        );
        ApiCollectionsDao.instance.updateOneNoUpsert(Filters.eq("_id", apiCollectionId), Updates.combine(updates));
        if(redacted){
            int accountId = Context.accountId.get();
            Runnable r = () -> {
                Context.accountId.set(accountId);
                loggerMaker.infoAndAddToDb("Triggered job to delete sample data", LoggerMaker.LogDb.DASHBOARD);
                dropSampleDataForApiCollection();
            };
            new Thread(r).start();
        }
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
        Map<Integer, Double> riskScoreMap = new HashMap<>();
        List<Bson> pipeline = new ArrayList<>();

        /*
         * Use Unwind to unwind the collectionIds field resulting in a document for each collectionId in the collectionIds array
         */
        UnwindOptions unwindOptions = new UnwindOptions();
        unwindOptions.preserveNullAndEmptyArrays(false);  
        pipeline.add(Aggregates.unwind("$collectionIds", unwindOptions));

        BasicDBObject groupId = new BasicDBObject("apiCollectionId", "$collectionIds");
        pipeline.add(Aggregates.sort(
            Sorts.descending(ApiInfo.RISK_SCORE)
        ));
        pipeline.add(Aggregates.group(groupId,
            Accumulators.max(ApiInfo.RISK_SCORE, "$riskScore")
        ));

        MongoCursor<BasicDBObject> cursor = ApiInfoDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
        while(cursor.hasNext()){
            try {
                BasicDBObject basicDBObject = cursor.next();
                BasicDBObject id = (BasicDBObject) basicDBObject.get("_id");
                double riskScore = 0;
                if(basicDBObject.get(ApiInfo.RISK_SCORE) != null){
                    riskScore = basicDBObject.getDouble(ApiInfo.RISK_SCORE);
                }
                riskScoreMap.put(id.getInt("apiCollectionId"), riskScore);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e,"error in calculating risk score for collections " + e.toString(), LogDb.DASHBOARD);
                e.printStackTrace();
            }
        }

        this.criticalEndpointsCount = (int) ApiInfoDao.instance.count(Filters.gte(ApiInfo.RISK_SCORE, 4));
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

    private List<Integer> reduceApiCollectionToId(List<ApiCollection> apiCollections) {
        if (apiCollections == null) {
            return new ArrayList<>();
        }
        return apiCollections.stream().map(apiCollection -> apiCollection.getId()).collect(Collectors.toList());
    }

    private List<ApiCollection> filterCollections(List<ApiCollection> apiCollections, boolean deactivated) {
        if (apiCollections == null) {
            return new ArrayList<>();
        }
        List<Integer> apiCollectionIds = reduceApiCollectionToId(this.apiCollections);
        Bson deactivatedFilter = Filters.eq(ApiCollection._DEACTIVATED, true);
        if(!deactivated){
            deactivatedFilter = Filters.or(
                Filters.exists(ApiCollection._DEACTIVATED, false),
                Filters.eq(ApiCollection._DEACTIVATED, false)
            );
        }

        /*
         * The apiCollections from request contain only the IDs,
         * thus we need to fetch the active status from the db.
         */
        return ApiCollectionsDao.instance.findAll(Filters.and(
                Filters.in(Constants.ID, apiCollectionIds),
                deactivatedFilter));
    }

    public String deactivateCollections() {
        this.apiCollections = filterCollections(this.apiCollections, false);
        this.apiCollections = fillApiCollectionsUrlCount(this.apiCollections);
        int deltaUsage = (-1) * this.apiCollections.stream().mapToInt(apiCollection -> apiCollection.getUrlsCount()).sum();
        List<Integer> apiCollectionIds = reduceApiCollectionToId(this.apiCollections);
        ApiCollectionsDao.instance.updateMany(Filters.in(Constants.ID, apiCollectionIds),
                Updates.set(ApiCollection._DEACTIVATED, true));
        UsageMetricHandler.calcAndFetchFeatureAccessUsingDeltaUsage(MetricTypes.ACTIVE_ENDPOINTS, Context.accountId.get(), deltaUsage);
        return Action.SUCCESS.toUpperCase();
    }

    public String activateCollections() {
        this.apiCollections = filterCollections(this.apiCollections, true);
        if (this.apiCollections.isEmpty()) {
            return Action.SUCCESS.toUpperCase();
        }
        this.apiCollections = fillApiCollectionsUrlCount(this.apiCollections);

        int accountId = Context.accountId.get();
        FeatureAccess featureAccess = UsageMetricUtils.getFeatureAccess(accountId, MetricTypes.ACTIVE_ENDPOINTS);
        int usageBefore = featureAccess.getUsage();
        int count = this.apiCollections.stream().mapToInt(apiCollection -> apiCollection.getUrlsCount()).sum();
        featureAccess.setUsage(usageBefore + count);

        if (!featureAccess.checkInvalidAccess()) {
            List<Integer> apiCollectionIds = reduceApiCollectionToId(this.apiCollections);
            ApiCollectionsDao.instance.updateMany(Filters.in(Constants.ID, apiCollectionIds),
                    Updates.unset(ApiCollection._DEACTIVATED));
        } else {
            String errorMessage = "API endpoints in collections exceeded usage limit. Unable to activate collections. Please upgrade your plan.";
            addActionError(errorMessage);
            return Action.ERROR.toUpperCase();
        }
        UsageMetricHandler.calcAndFetchFeatureAccessUsingDeltaUsage(MetricTypes.ACTIVE_ENDPOINTS, Context.accountId.get(), count);
        return Action.SUCCESS.toUpperCase();
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

    List<Integer> apiCollectionIds;

    private ENV_TYPE envType;

	public String updateEnvType(){
        try {
            Bson filter =  Filters.in("_id", apiCollectionIds);
            FindOneAndUpdateOptions updateOptions = new FindOneAndUpdateOptions();
            updateOptions.upsert(false);

            UpdateResult result = ApiCollectionsDao.instance.getMCollection().updateMany(filter,
                                            Updates.set(ApiCollection.USER_ENV_TYPE,envType)
                                    );;
            if(result == null){
                return Action.ERROR.toUpperCase();
            }
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

    public boolean isRedacted() {
        return redacted;
    }

    public void setRedacted(boolean redacted) {
        this.redacted = redacted;
    }

    public void setEnvType(ENV_TYPE envType) {
		this.envType = envType;
	}

    public void setApiCollectionIds(List<Integer> apiCollectionIds) {
        this.apiCollectionIds = apiCollectionIds;
    }
}

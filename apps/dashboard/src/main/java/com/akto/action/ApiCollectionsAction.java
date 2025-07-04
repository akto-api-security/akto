package com.akto.action;

import java.util.*;
import java.util.stream.Collectors;

import com.akto.action.observe.InventoryAction;
import com.akto.dto.*;
import com.akto.util.Pair;
import lombok.Getter;
import org.bson.conversions.Bson;

import com.akto.action.observe.Utils;
import com.akto.dao.*;
import com.akto.billing.UsageMetricUtils;
import com.akto.dao.context.Context;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.usage.MetricTypes;
import com.akto.dto.testing.TestingEndpoints;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.CollectionTags.TagSource;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Aggregates;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.testing.CustomTestingEndpoints;
import com.akto.dto.CollectionConditions.ConditionUtils;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.listener.RuntimeListener;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.usage.UsageMetricCalculator;
import com.akto.usage.UsageMetricHandler;
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
import com.mongodb.client.model.UpdateOptions;
import com.opensymphony.xwork2.Action;

import static com.akto.util.Constants.AKTO_DISCOVERED_APIS_COLLECTION;

public class ApiCollectionsAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ApiCollectionsAction.class, LogDb.DASHBOARD);

    List<ApiCollection> apiCollections = new ArrayList<>();
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
    private BasicDBObject response;
    private boolean hasUsageEndpoints;
    @Getter
    int sensitiveUnauthenticatedEndpointsCount;
    @Getter
    int highRiskThirdPartyEndpointsCount;
    @Getter
    int shadowApisCount;

    public List<ApiInfoKey> getApiList() {
        return apiList;
    }

    public void setApiList(List<ApiInfoKey> apiList) {
        this.apiList = apiList;
    }

    boolean redacted;
    
    public List<ApiCollection> fillApiCollectionsUrlCount(List<ApiCollection> apiCollections, Bson filter) {
	int tsRandom = Context.now();
	loggerMaker.debugAndAddToDb("fillApiCollectionsUrlCount started: " + tsRandom, LoggerMaker.LogDb.DASHBOARD);
        Map<Integer, Integer> countMap = ApiCollectionsDao.instance.buildEndpointsCountToApiCollectionMap(filter);
	loggerMaker.debugAndAddToDb("fillApiCollectionsUrlCount buildEndpointsCountToApiCollectionMap done: " + tsRandom, LoggerMaker.LogDb.DASHBOARD);

        for (ApiCollection apiCollection: apiCollections) {
            int apiCollectionId = apiCollection.getId();
            Integer count = countMap.get(apiCollectionId);
            int fallbackCount = apiCollection.getUrls()!=null ? apiCollection.getUrls().size() : apiCollection.getUrlsCount();
            if (count != null && (apiCollection.getHostName() != null)) {
                apiCollection.setUrlsCount(count);
            } else if(ApiCollection.Type.API_GROUP.equals(apiCollection.getType())){
                if (count == null) {
                    count = fallbackCount;
                }
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

            apiCollection.setUrls(new HashSet<>());
        }
        return apiCollections;
    }

    private Map<Integer, Integer> deactivatedHostnameCountMap;

    public String getCountForHostnameDeactivatedCollections(){
        this.deactivatedHostnameCountMap = new HashMap<>();
        if(deactivatedCollections == null || deactivatedCollections.isEmpty()){
            return SUCCESS.toUpperCase();
        }
        Bson filter = Filters.and(Filters.exists(ApiCollection.HOST_NAME), Filters.in(Constants.ID, deactivatedCollections));
        List<ApiCollection> hCollections = ApiCollectionsDao.instance.findAll(filter, Projections.include(Constants.ID));
        List<Integer> deactivatedIds = new ArrayList<>();
        for(ApiCollection collection : hCollections){
            if(deactivatedCollections.contains(collection.getId())){
                deactivatedIds.add(collection.getId());
            }
        }

        if(deactivatedIds.isEmpty()){
            return SUCCESS.toUpperCase();
        }

        this.deactivatedHostnameCountMap = ApiCollectionsDao.instance.buildEndpointsCountToApiCollectionMap(
            Filters.in(SingleTypeInfo._COLLECTION_IDS, deactivatedIds)
        );
        return SUCCESS.toUpperCase();
    }

    public String fetchAllCollections() {
        this.apiCollections = ApiCollectionsDao.instance.findAll(new BasicDBObject());
        this.apiCollections = fillApiCollectionsUrlCount(this.apiCollections, Filters.empty());
        return Action.SUCCESS.toUpperCase();
    }

    Set<Integer> deactivatedCollections = UsageMetricCalculator.getDeactivated();

    private ApiStats apiStatsStart;
    private ApiStats apiStatsEnd;
    private int startTimestamp;
    private int endTimestamp;
    public String fetchApiStats() {
        Bson filter = UsageMetricCalculator.excludeDemosAndDeactivated(ApiInfo.ID_API_COLLECTION_ID);
        Pair<ApiStats, ApiStats> result = ApiInfoDao.instance.fetchApiInfoStats(filter, startTimestamp, endTimestamp);
        apiStatsStart = result.getFirst();
        apiStatsEnd = result.getSecond();
        return SUCCESS.toUpperCase();
    }

    public String fetchAllCollectionsBasic() {
        List<Bson> pipeLine = new ArrayList<>();
        pipeLine.add(Aggregates.project(Projections.fields(
            Projections.computed(ApiCollection.URLS_COUNT, new BasicDBObject("$size", new BasicDBObject("$ifNull", Arrays.asList("$urls", Collections.emptyList())))),
            Projections.include(ApiCollection.ID, ApiCollection.NAME, ApiCollection.HOST_NAME, ApiCollection._TYPE, ApiCollection.TAGS_STRING, ApiCollection._DEACTIVATED,ApiCollection.START_TS, ApiCollection.AUTOMATED, ApiCollection.DESCRIPTION, ApiCollection.USER_ENV_TYPE)
        )));

        try {
            List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
            if(collectionIds != null) {
                pipeLine.add(Aggregates.match(Filters.in(Constants.ID, collectionIds)));
            }
        } catch(Exception e){
        }
        MongoCursor<ApiCollection> cursor = ApiCollectionsDao.instance.getMCollection().aggregate(pipeLine, ApiCollection.class).cursor();
        while(cursor.hasNext()){
            try {
                ApiCollection apiCollection = cursor.next();
                this.apiCollections.add(apiCollection);
            } catch (Exception e) {
                e.printStackTrace();
            }   
        }

        this.apiCollections = fillApiCollectionsUrlCount(this.apiCollections, Filters.nin(SingleTypeInfo._API_COLLECTION_ID, deactivatedCollections));

        return Action.SUCCESS.toUpperCase();
    }

    public String fetchCollection() {
        this.apiCollections = new ArrayList<>();
        this.apiCollections.add(ApiCollectionsDao.instance.findOne(Filters.eq(Constants.ID, apiCollectionId)));
        return Action.SUCCESS.toUpperCase();
    }

    static int maxCollectionNameLength = 60;
    private String collectionName;

    private boolean isValidApiCollectionName(){
        if (this.collectionName == null || this.collectionName.length() == 0) {
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

        try {
            int userId = Context.userId.get();
            int accountId = Context.accountId.get();
    
            /*
             * Since admin has all access, we don't update any collections for them.
             */
            RBACDao.instance.getMCollection().updateOne(
                    Filters.and(
                            Filters.eq(RBAC.USER_ID, userId),
                            Filters.eq(RBAC.ACCOUNT_ID, accountId),
                            Filters.ne(RBAC.ROLE, RBAC.Role.ADMIN.getName())
                    ),
                    Updates.addToSet(RBAC.API_COLLECTIONS_ID, apiCollection.getId()),
                    new UpdateOptions().upsert(false)
            );
    
            UsersCollectionsList.deleteCollectionIdsFromCache(userId, accountId);
        } catch(Exception e){
        }
        
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

        try {
            int userId = Context.userId.get();
            int accountId = Context.accountId.get();
            UsersCollectionsList.deleteCollectionIdsFromCache(userId, accountId);
        } catch (Exception e) {
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

        loggerMaker.debugAndAddToDb("Started adding " + this.apiList.size() + " apis into custom collection.", LogDb.DASHBOARD);

        CustomTestingEndpoints condition = new CustomTestingEndpoints(apiList, CustomTestingEndpoints.Operator.OR);
        apiCollection.addToConditions(condition);
        loggerMaker.debugAndAddToDb("Final conditions for collection: " +  apiCollection.getName() + " are: " + apiCollection.getConditions().toString());
        ApiCollectionUsers.updateApiCollection(apiCollection.getConditions(), apiCollection.getId());
        ApiCollectionUsers.addToCollectionsForCollectionId(apiCollection.getConditions(), apiCollection.getId());

        fetchAllCollections();

        return SUCCESS.toUpperCase();
    }

    public String deleteApis(){

        if(apiList.isEmpty()){
            addActionError("No APIs selected");
            return ERROR.toUpperCase();
        }

        List<Key> keys = new ArrayList<>();
        for (ApiInfoKey apiInfoKey: apiList) {
            keys.add(new Key(apiInfoKey.getApiCollectionId(), apiInfoKey.getUrl(), apiInfoKey.getMethod(), -1, 0, 0));
        }

        try {
            com.akto.utils.Utils.deleteApis(keys);
        } catch (Exception e) {
            e.printStackTrace();
            addActionError("Error deleting APIs");
            return ERROR.toUpperCase();
        }
        
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

    public String updateCustomCollection(){
        Bson filter = Filters.eq(Constants.ID, this.apiCollectionId);
        ApiCollection collection = ApiCollectionsDao.instance.findOne(filter);
        if(collection == null){
            addActionError("No collection with id exists.");
            return ERROR.toUpperCase();
        }
        List<TestingEndpoints> conditions = generateConditions(this.conditions);
        ApiCollectionsDao.instance.updateOneNoUpsert(filter, Updates.set(ApiCollection.CONDITIONS_STRING, conditions));
        ApiCollectionUsers.computeCollectionsForCollectionId(conditions, collection.getId());
        return SUCCESS.toUpperCase();
    }

    int apiCount;

    public String getEndpointsListFromConditions() {
        List<TestingEndpoints> conditions = generateConditions(this.conditions);
        List<BasicDBObject> list = ApiCollectionUsers.getSingleTypeInfoListFromConditions(conditions, 0, 200, Utils.DELTA_PERIOD_VALUE,  new ArrayList<>(deactivatedCollections));
        InventoryAction inventoryAction = new InventoryAction();
        inventoryAction.attachAPIInfoListInResponse(list,-1);
        this.setResponse(inventoryAction.getResponse());
        response.put("apiCount", ApiCollectionUsers.getApisCountFromConditionsWithStis(conditions, new ArrayList<>(deactivatedCollections)));
        return SUCCESS.toUpperCase();
    }
    public String getEndpointsFromConditions(){
        List<TestingEndpoints> conditions = generateConditions(this.conditions);

        apiCount = ApiCollectionUsers.getApisCountFromConditions(conditions, new ArrayList<>(deactivatedCollections));

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
            loggerMaker.debugAndAddToDb("No api collections to fix sample data for", LoggerMaker.LogDb.DASHBOARD);
            return;
        }
        loggerMaker.debugAndAddToDb(String.format("Fixing sample data for %d api collections", apiCollections.size()), LoggerMaker.LogDb.DASHBOARD);
        for (ApiCollection apiCollection: apiCollections) {
            int apiCollectionId = apiCollection.getId();
            UpdateResult updateResult = SampleDataDao.instance.updateManyNoUpsert(Filters.eq("_id.apiCollectionId", apiCollectionId), Updates.set("samples", Collections.emptyList()));
            loggerMaker.debugAndAddToDb(String.format("Fixed %d sample data for api collection %d", updateResult.getModifiedCount(), apiCollectionId), LoggerMaker.LogDb.DASHBOARD);
            updateResult = SensitiveSampleDataDao.instance.updateManyNoUpsert(Filters.eq("_id.apiCollectionId", apiCollectionId), Updates.set("sampleData", Collections.emptyList()));
            loggerMaker.debugAndAddToDb(String.format("Fixed %d sensitive sample data for api collection %d", updateResult.getModifiedCount(), apiCollectionId), LoggerMaker.LogDb.DASHBOARD);
            updateResult = SingleTypeInfoDao.instance.updateManyNoUpsert(Filters.and(Filters.eq("apiCollectionId", apiCollectionId), Filters.exists("values", true)), Updates.set("values.elements", Collections.emptyList()));
            loggerMaker.debugAndAddToDb(String.format("Fixed %d sti for api collection %d", updateResult.getModifiedCount(), apiCollectionId), LoggerMaker.LogDb.DASHBOARD);
            ApiCollectionsDao.instance.updateOneNoUpsert(Filters.eq("_id", apiCollectionId), Updates.set(ApiCollection.SAMPLE_COLLECTIONS_DROPPED, true));
        }
        loggerMaker.debugAndAddToDb(String.format("Fixed sample data for %d api collections", apiCollections.size()), LoggerMaker.LogDb.DASHBOARD);
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
                loggerMaker.debugAndAddToDb("Triggered job to delete sample data", LoggerMaker.LogDb.DASHBOARD);
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
        this.sensitiveUrlsInResponse = SingleTypeInfoDao.instance.getSensitiveApisCount(sensitiveSubtypes, true, Filters.nin(SingleTypeInfo._API_COLLECTION_ID, deactivatedCollections));

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

        try {
            List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
            if(collectionIds != null) {
                pipeline.add(Aggregates.match(Filters.in(SingleTypeInfo._COLLECTION_IDS, collectionIds)));
            }
        } catch(Exception e){
        }

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
        this.apiCollections = fillApiCollectionsUrlCount(this.apiCollections,Filters.empty());
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
        this.apiCollections = fillApiCollectionsUrlCount(this.apiCollections,Filters.empty());

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

    private List<CollectionTags> envType;
    private boolean resetEnvTypes;

	public String updateEnvType(){
        if(!resetEnvTypes && (envType == null || envType.isEmpty())) {
            addActionError("Please enter a valid ENV type.");
            return Action.ERROR.toUpperCase();
        }
        try {
            Bson filter = Filters.in("_id", apiCollectionIds);
            FindOneAndUpdateOptions updateOptions = new FindOneAndUpdateOptions();
            updateOptions.upsert(false);

            /*
            * User can only update collections which they have access to.
            * so we remove entries which are not in the collections access list.
            */
            try {
                List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
                if(collectionIds != null) {
                    apiCollectionIds.removeIf(apiCollectionId -> !collectionIds.contains(apiCollectionId));
                    filter =  Filters.in(Constants.ID, apiCollectionIds);
                }
            } catch(Exception e){
            }

            if(resetEnvTypes) {
                UpdateResult updateResult = ApiCollectionsDao.instance.getMCollection().updateMany(filter, Updates.combine(Updates.unset(ApiCollection.TAGS_STRING), Updates.unset(ApiCollection.USER_ENV_TYPE)));
                if(updateResult == null) {
                    return Action.ERROR.toUpperCase();
                }
                return Action.SUCCESS.toUpperCase();
            }

            List<ApiCollection> apiCollectionList = ApiCollectionsDao.instance.findAll(filter, Projections.include(ApiCollection.TAGS_STRING, ApiCollection.USER_ENV_TYPE));
            for(ApiCollection apiCollection : apiCollectionList) {
                filter =  Filters.in(Constants.ID, apiCollection.getId());
                List<CollectionTags> tagsList = apiCollection.getTagsList();
                String userSetEnvType = apiCollection.getUserSetEnvType();
                List<String> userSetEnvTypeList = new ArrayList<>();
                if (userSetEnvType != null && !userSetEnvType.isEmpty()) {
                    userSetEnvTypeList = new ArrayList<>(Arrays.asList(userSetEnvType.split(",")));
                }

                List<CollectionTags> toPull = new ArrayList<>();
                List<CollectionTags> toAdd = new ArrayList<>();
                if (tagsList == null || tagsList.isEmpty()) {
                    envType.stream().forEach((item) -> {
                        item.setSource(TagSource.USER);
                    });

                    toAdd.addAll(envType);
                } else {
                    for (CollectionTags env : envType) {
                        Optional<CollectionTags> matchingTag = tagsList.stream()
                                .filter(tag ->
                                        Objects.equals(tag.getKeyName(), env.getKeyName()) &&
                                                Objects.equals(tag.getValue(), env.getValue())
                                )
                                .findFirst();

                        if (env.getKeyName().equalsIgnoreCase(ApiCollection.DEFAULT_TAG_KEY) && userSetEnvTypeList.contains(env.getValue())) {
                            if (userSetEnvTypeList.size() == 1) {
                                ApiCollectionsDao.instance.updateOne(filter, Updates.unset(ApiCollection.USER_ENV_TYPE));
                            } else {
                                userSetEnvTypeList.remove(env.getValue());
                                String userEnvType = String.join(",", userSetEnvTypeList);
                                ApiCollectionsDao.instance.updateOne(filter, Updates.set(ApiCollection.USER_ENV_TYPE, userEnvType));
                            }

                            continue;
                        } else if (matchingTag.isPresent()) {
                            toPull.add(matchingTag.get());
                        } else {
                            env.setSource(TagSource.USER);
                            toAdd.add(env);
                        }
                    }

                    boolean isAddingStaging = toAdd.stream().anyMatch(tag ->
                            "envType".equalsIgnoreCase(tag.getKeyName()) &&
                                    "staging".equalsIgnoreCase(tag.getValue())
                    );

                    boolean isAddingProduction = toAdd.stream().anyMatch(tag ->
                            "envType".equalsIgnoreCase(tag.getKeyName()) &&
                                    "production".equalsIgnoreCase(tag.getValue())
                    );

                    if (isAddingStaging) {
                        tagsList.stream()
                                .filter(tag ->
                                        "envType".equalsIgnoreCase(tag.getKeyName()) &&
                                                "production".equalsIgnoreCase(tag.getValue())
                                )
                                .findFirst()
                                .ifPresent(toPull::add);
                    }

                    if (isAddingProduction) {
                        tagsList.stream()
                                .filter(tag ->
                                        "envType".equalsIgnoreCase(tag.getKeyName()) &&
                                                "staging".equalsIgnoreCase(tag.getValue())
                                )
                                .findFirst()
                                .ifPresent(toPull::add);
                    }
                }

                if (!toPull.isEmpty()) {
                    ApiCollectionsDao.instance.getMCollection().updateOne(filter,
                            Updates.pullAll(ApiCollection.TAGS_STRING, toPull)
                    );
                }

                if (!toAdd.isEmpty()) {
                    ApiCollectionsDao.instance.getMCollection().updateOne(filter,
                            Updates.addEachToSet(ApiCollection.TAGS_STRING, toAdd)
                    );
                }
            }
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Action.ERROR.toUpperCase();
    }

    public Map<String, List<Integer>> userCollectionMap = new HashMap<>();

    public String updateUserCollections() {
        int accountId = Context.accountId.get();

        for(Map.Entry<String, List<Integer>> entry : userCollectionMap.entrySet()) {
            int userId = Integer.parseInt(entry.getKey());
            Set<Integer> apiCollections = new HashSet<>(entry.getValue());

            /*
             * Need actual role, not base role, 
             * thus using direct Rbac query, not cached map.
             */
            RBAC rbac = RBACDao.instance.findOne(Filters.and(
                    Filters.eq(RBAC.USER_ID, userId),
                    Filters.eq(RBAC.ACCOUNT_ID, accountId)));
            String role = rbac.getRole();
            CustomRole customRole = CustomRoleDao.instance.findRoleByName(role);
            /*
             * If the role is custom role, only update the user with the delta.
             */
            if (customRole != null && customRole.getApiCollectionsId() != null
                    && !customRole.getApiCollectionsId().isEmpty()) {
                apiCollections.removeAll(customRole.getApiCollectionsId());
            }

            RBACDao.updateApiCollectionAccess(userId, accountId, apiCollections);
            UsersCollectionsList.deleteCollectionIdsFromCache(userId, accountId);
        }

        return SUCCESS.toUpperCase();
    }


    HashMap<Integer, List<Integer>> usersCollectionList;
    public String getAllUsersCollections() {
        int accountId = Context.accountId.get();
        this.usersCollectionList = RBACDao.instance.getAllUsersCollections(accountId);

        return SUCCESS.toUpperCase();
    }

    public void setUserCollectionMap(Map<String, List<Integer>> userCollectionMap) {
        this.userCollectionMap = userCollectionMap;
    }

    public HashMap<Integer, List<Integer>> getUsersCollectionList() {
        return this.usersCollectionList;
    }
    public String editCollectionName() {
        if(!isValidApiCollectionName()){
            return ERROR.toUpperCase();
        }

        ApiCollection apiCollection = ApiCollectionsDao.instance.getMeta(apiCollectionId);
        if (apiCollection == null) {
            String errorMessage = "API collection not found";
            addActionError(errorMessage);
            return Action.ERROR.toUpperCase();
        }

        if (apiCollection.getHostName() != null) {
            String errorMessage = "Unable to modify the Traffic API collection";
            addActionError(errorMessage);
            return Action.ERROR.toUpperCase();
        }

        ApiCollectionsDao.instance.updateOne(
            Filters.eq(ApiCollection.ID, apiCollectionId),
            Updates.combine(
                Updates.set(ApiCollection.NAME, collectionName),
                Updates.set("displayName", collectionName)
            )
        );

        return Action.SUCCESS.toUpperCase();
    }

    private String description;
    public String saveCollectionDescription() {
        if(description == null) {
            addActionError("No description provided");
            return Action.ERROR.toUpperCase();
        }

        ApiCollectionsDao.instance.updateOneNoUpsert(
                Filters.eq(ApiCollection.ID, apiCollectionId),
                Updates.set(ApiCollection.DESCRIPTION, description)
        );

        return SUCCESS.toUpperCase();
    }

    public String fetchSensitiveAndUnauthenticatedValue(){

        List<ApiInfo> sensitiveEndpoints = ApiInfoDao.instance.findAll(Filters.eq(ApiInfo.IS_SENSITIVE, true));

        for(ApiInfo apiInfo: sensitiveEndpoints) {
            if(apiInfo.getAllAuthTypesFound() != null && !apiInfo.getAllAuthTypesFound().isEmpty()) {
                for(Set<ApiInfo.AuthType> authType: apiInfo.getAllAuthTypesFound()) {
                    if(authType.contains(ApiInfo.AuthType.UNAUTHENTICATED)) {
                        this.sensitiveUnauthenticatedEndpointsCount++;
                    }
                }
            }
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchHighRiskThirdPartyValue(){

        List<ApiInfo> highRiskEndpoints = ApiInfoDao.instance.findAll(
                Filters.and(
                        Filters.gte(ApiInfo.RISK_SCORE, 4),
                        Filters.in(ApiInfo.API_ACCESS_TYPES, ApiInfo.ApiAccessType.THIRD_PARTY)
                )
        );
        this.highRiskThirdPartyEndpointsCount = highRiskEndpoints.size();
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchShadowApisValue(){

        ApiCollection shadowApisCollection;
        shadowApisCollection = ApiCollectionsDao.instance.findByName(AKTO_DISCOVERED_APIS_COLLECTION);

        if(shadowApisCollection != null) {
            this.shadowApisCount = shadowApisCollection.getUrls().size();
        } else {
            this.shadowApisCount = 0;
        }
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

    public void setEnvType(List<CollectionTags> envType) {
		this.envType = envType;
	}

    public void setApiCollectionIds(List<Integer> apiCollectionIds) {
        this.apiCollectionIds = apiCollectionIds;
    }

    public BasicDBObject getResponse() {
        return response;
    }

    public void setResponse(BasicDBObject response) {
        this.response = response;
    }

    public ApiStats getApiStatsEnd() {
        return apiStatsEnd;
    }

    public ApiStats getApiStatsStart() {
        return apiStatsStart;
    }

    public void setStartTimestamp(int startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public void setEndTimestamp(int endTimestamp) {
        this.endTimestamp = endTimestamp;
    }

    public Map<Integer, Integer> getDeactivatedHostnameCountMap() {
        return deactivatedHostnameCountMap;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setResetEnvTypes(boolean resetEnvTypes) {
        this.resetEnvTypes = resetEnvTypes;
    }

}

package com.akto.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.bson.conversions.Bson;
import java.util.Collections;

import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.FilterSampleDataDao;
import com.akto.dao.MCollection;
import com.akto.dao.SampleDataDao;
import com.akto.dao.SensitiveParamInfoDao;
import com.akto.dao.SensitiveSampleDataDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.TrafficInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.demo.VulnerableRequestForTemplateDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.AccessTypeTestingEndpoints;
import com.akto.dto.testing.AuthTypeTestingEndpoints;
import com.akto.dto.testing.CustomTestingEndpoints;
import com.akto.dto.testing.SensitiveDataEndpoints;
import com.akto.dto.testing.TestingEndpoints;
import com.akto.dto.testing.custom_groups.AllAPIsGroup;
import com.akto.dto.testing.custom_groups.UnauthenticatedEndpoint;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;

public class ApiCollectionUsers {

    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private static final Logger logger = LoggerFactory.getLogger(ApiCollectionUsers.class);

    private static final int MAX_ALLOWED_API_COUNT = 10000;  

    public enum CollectionType {
        ApiCollectionId, Id_ApiCollectionId, Id_ApiInfoKey_ApiCollectionId
    }

    public static final Map<CollectionType, MCollection<?>[]> COLLECTIONS_WITH_API_COLLECTION_ID = 
        Collections.unmodifiableMap(new HashMap<CollectionType, MCollection<?>[]>() {{
            put(CollectionType.ApiCollectionId, new MCollection[] {
                    SingleTypeInfoDao.instance,
                    SensitiveParamInfoDao.instance
            });
            put(CollectionType.Id_ApiCollectionId, new MCollection[] {
                    ApiInfoDao.instance,
                    TrafficInfoDao.instance,
                    SampleDataDao.instance,
                    SensitiveSampleDataDao.instance,
                    VulnerableRequestForTemplateDao.instance,
                    FilterSampleDataDao.instance
            });
            put(CollectionType.Id_ApiInfoKey_ApiCollectionId, new MCollection[] {
                    TestingRunIssuesDao.instance
            });
        }});

    public static List<BasicDBObject> getSingleTypeInfoListFromConditions(List<TestingEndpoints> conditions, int skip, int limit, int deltaPeriodValue, List<Integer> deactivatedCollections) {
        if(conditions == null || conditions.isEmpty()){
            return new ArrayList<>();
        }
        List<Bson> filterList = SingleTypeInfoDao.filterForHostHostHeaderRaw();
        boolean hasAuthFilter = checkAuthTypeFilter(conditions);
        boolean hasApiAccessType = checkApiAccessTypeFilter(conditions);
        if (hasAuthFilter || hasApiAccessType) {
            int apiInfoCount = getApisCountFromConditions(conditions, deactivatedCollections);
            if (apiInfoCount >= MAX_ALLOWED_API_COUNT) {
                Bson apiInfoFilter = getFilters(conditions,CollectionType.Id_ApiCollectionId);
                return findAllAsBasicDBObject(apiInfoFilter, skip, limit, Projections.include("_id"));
            }
        }
        Bson singleTypeInfoFilters = getFilters(conditions, CollectionType.ApiCollectionId);
        Bson filters = Filters.and(filterList);
        singleTypeInfoFilters = Filters.and(filters, singleTypeInfoFilters);
        if (deactivatedCollections != null && !deactivatedCollections.isEmpty()) {
            singleTypeInfoFilters = Filters.and(singleTypeInfoFilters, Filters.nin(SingleTypeInfo._API_COLLECTION_ID, deactivatedCollections));
        }
        return ApiCollectionsDao.fetchEndpointsInCollection(singleTypeInfoFilters, skip, limit, deltaPeriodValue);
    }
    public static int getApisCountFromConditions(List<TestingEndpoints> conditions, List<Integer> deactivatedCollections) {

        if(conditions == null || conditions.isEmpty()){
            return 0;
        }

        Bson apiInfoFilters = getFilters(conditions, CollectionType.Id_ApiCollectionId);

        if ( deactivatedCollections != null && !deactivatedCollections.isEmpty()) {
            apiInfoFilters = Filters.and(apiInfoFilters, Filters.nin("_id.apiCollectionId", deactivatedCollections));
        }

        return (int) ApiInfoDao.instance.count(apiInfoFilters);
    }

    public static int getApisCountFromConditionsWithStis(List<TestingEndpoints> conditions, List<Integer> deactivatedCollections){
        if(conditions == null || conditions.isEmpty()){
            return 0;
        }

        boolean hasAuthFilter = checkAuthTypeFilter(conditions);
        if (hasAuthFilter) {
            int apiInfoCount = getApisCountFromConditions(conditions, deactivatedCollections);
            if (apiInfoCount >= MAX_ALLOWED_API_COUNT) {
                return apiInfoCount;
            }
        }

        List<Bson> filterList = SingleTypeInfoDao.filterForHostHostHeaderRaw();
        Bson filters = Filters.and(filterList);
        Bson stiFiltes = getFilters(conditions, CollectionType.ApiCollectionId);
        stiFiltes = Filters.and(filters, stiFiltes);
        List<Bson> pipeLine = new ArrayList<>();
        pipeLine.add(Aggregates.match(stiFiltes));

        try {
            List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
            if(collectionIds != null) {
                pipeLine.add(Aggregates.match(Filters.in(SingleTypeInfo._COLLECTION_IDS, collectionIds)));
            }
        } catch(Exception e){
        }

        pipeLine.add(Aggregates.match(Filters.and(
            Filters.eq(SingleTypeInfo._RESPONSE_CODE, -1),
            Filters.eq(SingleTypeInfo._IS_HEADER, true)
        )));
        BasicDBObject groupedId = SingleTypeInfoDao.getApiInfoGroupedId();
        pipeLine.add(Aggregates.group(groupedId, Accumulators.sum("count", 1)));
        pipeLine.add(Aggregates.count("finalCount"));

        int ansCount = 0;

        MongoCursor<BasicDBObject> countCursor = SingleTypeInfoDao.instance.getMCollection().aggregate(pipeLine, BasicDBObject.class).cursor();
        while(countCursor.hasNext()){
            BasicDBObject dbObject = countCursor.next();
            ansCount = dbObject.getInt("finalCount");
        }

        return ansCount;
    }

    public static void updateApiCollection(List<TestingEndpoints> conditions, int id) {

        ApiCollectionsDao.instance.updateOne(
                Filters.eq(Constants.ID, id),
                Updates.set(ApiCollection.CONDITIONS_STRING, conditions));
    }

    private static Bson getFilters(List<TestingEndpoints> conditions, CollectionType type){
        List<Bson> filters = new ArrayList<>();
        List<Bson> orFilters = new ArrayList<>();
        conditions.forEach((condition) -> {
            Bson conditionFilter = condition.returnFiltersMap().get(type);
            if(condition.getOperator().equals(TestingEndpoints.Operator.OR)){
                orFilters.add(conditionFilter);
            } else {
                filters.add(conditionFilter);
            }
        });
        if(!orFilters.isEmpty()){
            filters.add(Filters.or(orFilters));
        }
        return Filters.and(filters);
    }

    private static void operationForCollectionId(List<TestingEndpoints> conditions, int apiCollectionId, Bson update, Bson matchFilter, boolean remove, Set<Integer> excludedOwners) {
        Map<CollectionType, Bson> filtersMap = new HashMap<>();
        for (CollectionType type : CollectionType.values()) {
            Bson filter = getFilters(conditions, type);
            if(remove){
                filter = Filters.nor(filter);
            }
            Bson finalFilter = Filters.and(matchFilter, filter);
            if (!excludedOwners.isEmpty()) {
                finalFilter = Filters.and(finalFilter, Filters.nin(ownerField(type), excludedOwners));
            }
            filtersMap.put(type, finalFilter);
        }
        updateCollectionsForCollectionId(filtersMap, update);
    }

    private static String ownerField(CollectionType type) {
        switch (type) {
            case ApiCollectionId:
                return SingleTypeInfo._API_COLLECTION_ID;
            case Id_ApiCollectionId:
                return ApiInfo.ID_API_COLLECTION_ID;
            default:
                return TestingRunIssues.ID_API_COLLECTION_ID;
        }
    }

    /*
     * Collections that own APIs but are not part of API Security (Argus, DAST, Endpoint).
     * API Security groups must never contain their APIs.
     */
    private static Set<Integer> nonApiSecurityOwners(Set<Integer> apiSecurityIds) {
        Set<Integer> owners = new HashSet<>();
        try (MongoCursor<ApiCollection> cursor = ApiCollectionsDao.instance.getMCollection()
                .find(Filters.ne(ApiCollection._TYPE, ApiCollection.Type.API_GROUP.name()))
                .projection(Projections.include(Constants.ID)).iterator()) {
            while (cursor.hasNext()) {
                int id = cursor.next().getId();
                if (!apiSecurityIds.contains(id)) {
                    owners.add(id);
                }
            }
        }
        return owners;
    }

    /*
     * Groups that belong only to API Security. A group shared with another product, like the
     * Unauthenticated MCP Tools group, is left alone, and so is the All APIs group, which spans
     * every product and is scoped when read (AllAPIsGroup.visibleOwnerFilter).
     */
    private static boolean isApiSecurityOnlyGroup(int apiCollectionId, Set<Integer> apiSecurityIds, Set<Integer> otherProductIds) {
        return apiCollectionId != AllAPIsGroup.ALL_APIS_GROUP_ID && apiSecurityIds.contains(apiCollectionId)
                && !otherProductIds.contains(apiCollectionId);
    }

    private static Set<Integer> otherProductIds() {
        Set<Integer> ids = new HashSet<>();
        for (CONTEXT_SOURCE source : CONTEXT_SOURCE.values()) {
            if (source != CONTEXT_SOURCE.API) {
                ids.addAll(UsersCollectionsList.getContextCollections(source));
            }
        }
        return ids;
    }

    // Owners to skip when adding to this group: non API Security owners if it is an API Security group, else none.
    private static Set<Integer> ownersExcludedFromGroup(int apiCollectionId) {
        Set<Integer> apiSecurityIds = UsersCollectionsList.getContextCollections(CONTEXT_SOURCE.API);
        if (!isApiSecurityOnlyGroup(apiCollectionId, apiSecurityIds, otherProductIds())) {
            return Collections.emptySet();
        }
        return nonApiSecurityOwners(apiSecurityIds);
    }

    /*
     * Removes API Security group ids from APIs owned outside API Security, e.g. Argus APIs that were
     * added to "Admin APIs" or the risk score groups before group membership was scoped to API Security.
     */
    public static void removeNonApiSecurityApisFromApiSecurityGroups() {
        Set<Integer> apiSecurityIds = UsersCollectionsList.getContextCollections(CONTEXT_SOURCE.API);
        Set<Integer> otherProductIds = otherProductIds();
        List<Integer> apiSecurityGroupIds = new ArrayList<>();
        try (MongoCursor<ApiCollection> cursor = ApiCollectionsDao.instance.getMCollection()
                .find(Filters.eq(ApiCollection._TYPE, ApiCollection.Type.API_GROUP.name()))
                .projection(Projections.include(Constants.ID)).iterator()) {
            while (cursor.hasNext()) {
                int id = cursor.next().getId();
                if (isApiSecurityOnlyGroup(id, apiSecurityIds, otherProductIds)) {
                    apiSecurityGroupIds.add(id);
                }
            }
        }
        Set<Integer> owners = nonApiSecurityOwners(apiSecurityIds);
        if (apiSecurityGroupIds.isEmpty() || owners.isEmpty()) {
            return;
        }

        Bson update = Updates.pullAll(SingleTypeInfo._COLLECTION_IDS, apiSecurityGroupIds);
        for (Map.Entry<CollectionType, MCollection<?>[]> entry : COLLECTIONS_WITH_API_COLLECTION_ID.entrySet()) {
            Bson filter = Filters.and(
                Filters.in(ownerField(entry.getKey()), owners),
                Filters.in(SingleTypeInfo._COLLECTION_IDS, apiSecurityGroupIds)
            );
            for (MCollection<?> collection : entry.getValue()) {
                UpdateResult res = collection.getMCollection().updateMany(filter, update);
                if (res.getModifiedCount() > 0) {
                    logger.info("Removed API Security groups from " + res.getModifiedCount() + " non API Security docs in " + collection.getCollName());
                }
            }
        }
    }

    public static void addToCollectionsForCollectionId(List<TestingEndpoints> conditions, int apiCollectionId) {
        Bson update = Updates.addToSet(SingleTypeInfo._COLLECTION_IDS, apiCollectionId);
        Bson matchFilter = Filters.nin(SingleTypeInfo._COLLECTION_IDS, apiCollectionId);
        operationForCollectionId(conditions, apiCollectionId, update, matchFilter, false, ownersExcludedFromGroup(apiCollectionId));
    }

    public static void removeFromCollectionsForCollectionId(List<TestingEndpoints> conditions, int apiCollectionId) {
        Bson update = Updates.pull(SingleTypeInfo._COLLECTION_IDS, apiCollectionId);
        Bson matchFilter = Filters.in(SingleTypeInfo._COLLECTION_IDS, apiCollectionId);

        // Check if any of the conditions is delta update based
        boolean isDeltaUpdateBasedApiGroup = false;
        for (TestingEndpoints testingEndpoints : conditions) {
            if (TestingEndpoints.checkDeltaUpdateBased(testingEndpoints.getType())) {
                isDeltaUpdateBasedApiGroup = true;
                break;
            }
        }

        if (isDeltaUpdateBasedApiGroup) {
            operationForCollectionId(conditions, apiCollectionId, update, matchFilter, false, Collections.emptySet());
        } else {
            operationForCollectionId(conditions, apiCollectionId, update, matchFilter, true, Collections.emptySet());
        }

    }

    public static void computeCollectionsForCollectionId(List<TestingEndpoints> conditions, int apiCollectionId) {

        ApiCollection collection = ApiCollectionsDao.instance.findOne(Filters.eq(Constants.ID, apiCollectionId));
        if(collection == null){
            return;
        }

        if(UnauthenticatedEndpoint.UNAUTHENTICATED_GROUP_ID == apiCollectionId){
            UnauthenticatedEndpoint.updateCollections();
            return;
        }

        if(SensitiveDataEndpoints.API_GROUP_ID == apiCollectionId){
            SensitiveDataEndpoints.updateCollections();
            return;
        }

        addToCollectionsForCollectionId(conditions, apiCollectionId);
        removeFromCollectionsForCollectionId(conditions, apiCollectionId);
        updateApiCollection(conditions, apiCollectionId);
    }

    private static void updateCollectionsForCollectionId(Map<CollectionType, Bson> filtersMap, Bson update) {
        int accountId = Context.accountId.get();
        executorService.schedule(new Runnable() {
            public void run() {
                Context.accountId.set(accountId);

                Map<CollectionType, MCollection<?>[]> collectionsMap = COLLECTIONS_WITH_API_COLLECTION_ID;

                for (Map.Entry<CollectionType, MCollection<?>[]> collectionsEntry : collectionsMap.entrySet()) {

                    CollectionType type = collectionsEntry.getKey();
                    MCollection<?>[] collections = collectionsEntry.getValue();
                    Bson filter = filtersMap.get(type);

                    updateCollections(collections, filter, update);
                }

            }
        }, 0, TimeUnit.SECONDS);
    }

    private static void updateCollections(MCollection<?>[] collections, Bson filter, Bson update) {
        for (MCollection<?> collection : collections) {
            UpdateResult res = collection.getMCollection().updateMany(filter, update);
        }
    }

    public static void updateCollections(MCollection<?>[] collections, Bson filter, List<Bson> update) {
        for (MCollection<?> collection : collections) {
            collection.getMCollection().updateMany(filter, update);
        }
    }

    public static void updateCollectionsInBatches(MCollection<?>[] collections, Bson filter, List<Bson> update) {
        for (MCollection<?> collection : collections) {
            updateCollectionInBatches(collection, filter, update);
        }
    }

    static final int UPDATE_LIMIT = 50_000;

    // NOTE: This update only works with collections which have ObjectId as id.
    private static void updateCollectionInBatches(MCollection<?> collection, Bson filter, List<Bson> update) {
        boolean doUpdate = true;
        int c = 0;
        int time = Context.now();
        int accountId = Context.accountId.get();
        while (doUpdate) {

            List<Bson> pipeline = new ArrayList<>();
            pipeline.add(Aggregates.match(filter));
            pipeline.add(Aggregates.project(Projections.include(Constants.ID)));
            pipeline.add(Aggregates.limit(UPDATE_LIMIT));

            MongoCursor<BasicDBObject> cursor = collection.getMCollection()
                    .aggregate(pipeline, BasicDBObject.class).cursor();

            ArrayList<ObjectId> ret = new ArrayList<>();

            while (cursor.hasNext()) {
                BasicDBObject elem = cursor.next();
                ret.add((ObjectId) elem.get(Constants.ID));
            }

            UpdateResult res = collection.getMCollection().updateMany(
                    Filters.in(Constants.ID, ret), update);

            if (res.getMatchedCount() == 0) {
                doUpdate = false;
            }
            c += Math.min(UPDATE_LIMIT , res.getModifiedCount());
            logger.info("updated " + c + " " + collection.getCollName() + " in account id: " + accountId);
        }
        logger.info("Total time taken : " + (Context.now() - time) + " for " + collection.getCollName() + " in account id: " + accountId);
    }

    public static void reset(int apiCollectionId) {
        CustomTestingEndpoints ep = new CustomTestingEndpoints(new ArrayList<>());
        removeFromCollectionsForCollectionId(Collections.singletonList(ep), apiCollectionId);
    }

    private static boolean checkAuthTypeFilter(List<TestingEndpoints> conditions) {
        for (TestingEndpoints condition : conditions) {
            if (condition instanceof AuthTypeTestingEndpoints) {
                return true;
            }
        }
        return false;
    }

    private static boolean checkApiAccessTypeFilter(List<TestingEndpoints> conditions) {
        for (TestingEndpoints condition : conditions) {
            if (condition instanceof AccessTypeTestingEndpoints) {
                return true;
            }
        }
        return false;
    }

    private static List<BasicDBObject> findAllAsBasicDBObject(Bson filter,int skip,int limit,Bson projection) {
        List<BasicDBObject> results = new ArrayList<>();
        MongoCursor<BasicDBObject> cursor = ApiInfoDao.instance.getMCollection().find(filter, BasicDBObject.class).projection(projection).skip(skip).limit(limit).iterator();
        while (cursor.hasNext()) {
            results.add(cursor.next());
        }

        return results;
    }
}

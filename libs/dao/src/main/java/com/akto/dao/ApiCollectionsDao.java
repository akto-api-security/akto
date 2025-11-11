package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.util.Constants;
import com.akto.util.Pair;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ApiCollectionsDao extends AccountsContextDaoWithRbac<ApiCollection> {

    public static final ApiCollectionsDao instance = new ApiCollectionsDao();
    private static Bson projectionForApis = Projections.include(Constants.TIMESTAMP, ApiInfoKey.API_COLLECTION_ID, ApiInfoKey.URL, ApiInfoKey.METHOD);

    private ApiCollectionsDao() {}

    @Override
    public String getCollName() {
        return "api_collections";
    }

    @Override
    public Class<ApiCollection> getClassT() {
        return ApiCollection.class;
    }

    @Override
    public String getFilterKeyString(){
        return ApiCollection.ID;
    }

    public void createIndicesIfAbsent() {
        boolean exists = false;
        String dbName = Context.accountId.get()+"";
        MongoDatabase db = clients[0].getDatabase(dbName);
        for (String col: db.listCollectionNames()){
            if (getCollName().equalsIgnoreCase(col)){
                exists = true;
                break;
            }
        };

        if (!exists) {
            db.createCollection(getCollName());
        }

        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[] { ApiCollection.START_TS }, false);

        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[] { ApiCollection.NAME }, true);
    }

    public ApiCollection getMeta(int apiCollectionId) {
        List<ApiCollection> ret = ApiCollectionsDao.instance.findAll(Filters.eq("_id", apiCollectionId), Projections.exclude("urls"));

        return (ret != null && ret.size() > 0) ? ret.get(0) : null;
    }

    public void updateTransportType(ApiCollection apiCollection, String transportType) {
        try {
            Bson filter = Filters.eq(ApiCollection.ID, apiCollection.getId());
            Bson update = Updates.set(ApiCollection.MCP_TRANSPORT_TYPE, transportType);
            ApiCollectionsDao.instance.updateOne(filter, update);
            apiCollection.setMcpTransportType(transportType);
        } catch (Exception e) {
        }
    }


    public List<ApiCollection> getMetaForIds(List<Integer> apiCollectionIds) {
        return ApiCollectionsDao.instance.findAll(Filters.in("_id", apiCollectionIds), Projections.exclude("urls"));
    }

    public ApiCollection getMetaForId(int apiCollectionId) {
        return ApiCollectionsDao.instance.findOne(Filters.eq(Constants.ID, apiCollectionId),
                Projections.exclude("urls"));
    }

    public Map<Integer, ApiCollection> getApiCollectionsMetaMap() {
        Map<Integer, ApiCollection> apiCollectionsMap = new HashMap<>();
        List<ApiCollection> metaAll = getMetaAll();
        for (ApiCollection apiCollection: metaAll) {
            apiCollectionsMap.put(apiCollection.getId(), apiCollection);
        }

        return apiCollectionsMap;
    }

    public List<ApiCollection> getMetaAll() {
        return ApiCollectionsDao.instance.findAll(new BasicDBObject(), Projections.exclude("urls"));
    }

    public Map<Integer, ApiCollection> generateApiCollectionMap() {
        Map<Integer, ApiCollection> apiCollectionMap = new HashMap<>();
        List<ApiCollection> apiCollections = getMetaAll();
        for (ApiCollection apiCollection: apiCollections) {
            apiCollectionMap.put(apiCollection.getId(), apiCollection);
        }

        return apiCollectionMap;
    }

    public List<ApiCollection> fetchApiGroups() {
        return ApiCollectionsDao.instance.findAll(Filters.eq(ApiCollection._TYPE, ApiCollection.Type.API_GROUP.toString()));
    }

    public List<ApiCollection> fetchNonApiGroupsIds() {
        return ApiCollectionsDao.instance.findAll(
                Filters.or(
                        Filters.exists(ApiCollection._TYPE, false),
                        Filters.ne(ApiCollection._TYPE, ApiCollection.Type.API_GROUP.toString())),
                Projections.include(ApiCollection.ID));
    }

    public ApiCollection findByName(String name) {
        List<ApiCollection> apiCollections = ApiCollectionsDao.instance.findAll(new BasicDBObject());
        for (ApiCollection apiCollection: apiCollections) {
            if (apiCollection.getDisplayName() == null) continue;
            if (apiCollection.getDisplayName().equalsIgnoreCase(name)) {
                return apiCollection;
            }
        }
        return null;
    }

    public ApiCollection findByHost(String host) {
        return instance.findOne(ApiCollection.HOST_NAME, host);
    }

    // this is flawed. Because we were in a hurry we allowed this
    // traffic collection with internal api... do not have hosts and will also be included
    public List<ApiCollection> fetchNonTrafficApiCollections() {
        return instance.findAll(
            Filters.or(
                Filters.eq(ApiCollection.HOST_NAME, null),
                Filters.exists(ApiCollection.HOST_NAME, false)
            )       
        );
    }

    public List<Integer> fetchNonTrafficApiCollectionsIds() {
        List<ApiCollection> nonTrafficApiCollections = ApiCollectionsDao.instance.fetchNonTrafficApiCollections();
        List<Integer> apiCollectionIds = new ArrayList<>();
        for (ApiCollection apiCollection: nonTrafficApiCollections) {
            apiCollectionIds.add(apiCollection.getId());
        }

        return apiCollectionIds;
    }

    public Map<Integer, Integer> buildEndpointsCountToApiCollectionMap(Bson filter) {
        Map<Integer, Integer> countMap = new HashMap<>();
        List<Bson> pipeline = new ArrayList<>();

        pipeline.add(Aggregates.match(Filters.and(
                SingleTypeInfoDao.filterForHostHeader(0, false),
                filter
            )
        ));
        try {
            List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
            if(collectionIds != null) {
                pipeline.add(Aggregates.match(Filters.in(SingleTypeInfo._COLLECTION_IDS, collectionIds)));
            }
        } catch(Exception e){
        }

        // segregate above ids into group, non group
        // make 2 separate queries

        BasicDBObject groupedId = new BasicDBObject(SingleTypeInfo._COLLECTION_IDS, "$" + SingleTypeInfo._COLLECTION_IDS);
        pipeline.add(Aggregates.unwind("$" + SingleTypeInfo._COLLECTION_IDS));
        pipeline.add(Aggregates.group(groupedId, Accumulators.sum("count",1)));

        MongoCursor<BasicDBObject> endpointsCursor = SingleTypeInfoDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
        while(endpointsCursor.hasNext()) {
            try {
                BasicDBObject basicDBObject = endpointsCursor.next();
                int apiCollectionId = ((BasicDBObject) basicDBObject.get("_id")).getInt(SingleTypeInfo._COLLECTION_IDS);
                int count = basicDBObject.getInt("count");
                countMap.put(apiCollectionId, count);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Map<String, Integer> codeAnalysisUrlsCountMap = CodeAnalysisApiInfoDao.instance.getUrlsCount();
        if (codeAnalysisUrlsCountMap.isEmpty()) return countMap;

        Map<String, Integer> idToCollectionNameMap = CodeAnalysisCollectionDao.instance.findIdToCollectionNameMap();
        for (String codeAnalysisId: codeAnalysisUrlsCountMap.keySet()) {
            int count = codeAnalysisUrlsCountMap.getOrDefault(codeAnalysisId, 0);
            Integer apiCollectionId = idToCollectionNameMap.get(codeAnalysisId);
            if (apiCollectionId == null) continue;

            int currentCount = countMap.getOrDefault(apiCollectionId, 0);
            currentCount += count;

            countMap.put(apiCollectionId, currentCount);
        }


        return countMap;
    }

    public Map<Integer, Integer> buildEndpointsCountToApiCollectionMapOptimized(Bson filter, List<ApiCollection> apiCollections) {
        Map<Integer, Integer> countMap = new HashMap<>();
        int ts = Context.nowInMillis();
        // Get user collection IDs for RBAC filtering
        List<Integer> userCollectionIds = null;
        Set<Integer> userCollectionIdsSet = new HashSet<>();
        try {
            userCollectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
            if (userCollectionIds != null) {
                userCollectionIdsSet.addAll(userCollectionIds);
            }
        } catch(Exception e){
            // If no user collections, proceed without filtering
        }

        
        // Step 1: Query api_collections to separate group and non-group collections
        List<Integer> groupCollectionIds = new ArrayList<>();
        List<Integer> nonGroupCollectionIds = new ArrayList<>();
                
        for(ApiCollection collection : apiCollections) {
            if (userCollectionIds != null && !userCollectionIdsSet.contains(collection.getId())) {
                continue;
            }
            if(collection.getType() == ApiCollection.Type.API_GROUP) {
                groupCollectionIds.add(collection.getId());
            } else {
                nonGroupCollectionIds.add(collection.getId());
            }
        }
        
        // Capture current context before async operations
        final Integer currentAccountId = Context.accountId.get();
        final Integer currentUserId = Context.userId.get();
        final CONTEXT_SOURCE currentContextSource = Context.contextSource.get();
        
        // Create a custom executor for better control
        ExecutorService executor = Executors.newFixedThreadPool(2);
        
        // Step 2: Execute group and non-group queries in parallel
        CompletableFuture<Map<Integer, Integer>> nonGroupFuture = CompletableFuture.supplyAsync(() -> {
            // Restore context in async thread
            Context.accountId.set(currentAccountId);
            Context.userId.set(currentUserId);
            Context.contextSource.set(currentContextSource);
            
            Map<Integer, Integer> nonGroupCountMap = new HashMap<>();
            try {
                if(!nonGroupCollectionIds.isEmpty()) {
                    Bson hostHeaderFilter = SingleTypeInfoDao.filterForHostHeader(0, false);
                    List<Bson> pipeline = new ArrayList<>();
                    pipeline.add(Aggregates.match(Filters.and(
                        hostHeaderFilter,
                        Filters.in(SingleTypeInfo._API_COLLECTION_ID, nonGroupCollectionIds),
                        filter
                    )));
                    pipeline.add(Aggregates.group(
                        "$" + SingleTypeInfo._API_COLLECTION_ID,
                        Accumulators.sum("count", 1)
                    ));
                    
                    MongoCursor<BasicDBObject> cursor = SingleTypeInfoDao.instance.getMCollection()
                        .aggregate(pipeline, BasicDBObject.class).cursor();
                    
                    try {
                        while(cursor.hasNext()) {
                            BasicDBObject doc = cursor.next();
                            int apiCollectionId = doc.getInt("_id");
                            int count = doc.getInt("count");
                            nonGroupCountMap.put(apiCollectionId, count);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        cursor.close(); // Always close cursor to free resources
                    }

                }
            } finally {
                // Clean up ThreadLocal
                Context.accountId.remove();
                Context.userId.remove();
            }
            return nonGroupCountMap;
        }, executor);

        CompletableFuture<Map<Integer, Integer>> groupFuture = CompletableFuture.supplyAsync(() -> {
            // Restore context in async thread
            Context.accountId.set(currentAccountId);
            Context.userId.set(currentUserId);
            Context.contextSource.set(currentContextSource);
            
            Map<Integer, Integer> groupCountMap = new HashMap<>();
            try {
                if(!groupCollectionIds.isEmpty()) {
                    Bson hostHeaderFilter = SingleTypeInfoDao.filterForHostHeader(0, false);
                    
                    // For small number of groups, individual queries are faster than $unwind
                    if (groupCollectionIds.size() <= 50) {
                        
                        // Run individual count queries in parallel using CompletableFutures
                        List<CompletableFuture<Pair<Integer, Integer>>> futures = new ArrayList<>();
                        ExecutorService queryExecutor = Executors.newFixedThreadPool(Math.min(groupCollectionIds.size(), 10));
                        
                        for (Integer collectionId : groupCollectionIds) {
                            CompletableFuture<Pair<Integer, Integer>> future = CompletableFuture.supplyAsync(() -> {
                                // Restore context in async thread
                                Context.accountId.set(currentAccountId);
                                Context.userId.set(currentUserId);
                                Context.contextSource.set(currentContextSource);
                                
                                try {
                                    // Use Filters.in for array field - checks if array contains the value
                                    Bson matchFilter = Filters.and(
                                        hostHeaderFilter,
                                        Filters.in(SingleTypeInfo._COLLECTION_IDS, collectionId),
                                        filter
                                    );
                                    long count = SingleTypeInfoDao.instance.getMCollection().countDocuments(matchFilter);
                                    return new Pair<>(collectionId, (int)count);
                                } finally {
                                    // Clean up ThreadLocal
                                    Context.accountId.remove();
                                    Context.userId.remove();
                                    Context.contextSource.remove();
                                }
                            }, queryExecutor);
                            futures.add(future);
                        }
                        
                        // Collect results
                        try {
                            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                            for (CompletableFuture<Pair<Integer, Integer>> future : futures) {
                                Pair<Integer, Integer> result = future.get();
                                groupCountMap.put(result.getFirst(), result.getSecond());
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            queryExecutor.shutdown();
                        }
                        
                    } else {
                        // For large number of groups, use optimized aggregation
                        
                        // Remove redundant second match after unwind
                        List<Bson> pipeline = new ArrayList<>();
                        pipeline.add(Aggregates.match(Filters.and(
                            hostHeaderFilter,
                            Filters.in(SingleTypeInfo._COLLECTION_IDS, groupCollectionIds),
                            filter
                        )));
                        pipeline.add(Aggregates.unwind("$" + SingleTypeInfo._COLLECTION_IDS));
                        // REMOVED redundant match - unwind already filtered the documents
                        pipeline.add(Aggregates.group(
                            "$" + SingleTypeInfo._COLLECTION_IDS,
                            Accumulators.sum("count", 1)
                        ));
                        
                        MongoCursor<BasicDBObject> cursor = SingleTypeInfoDao.instance.getMCollection()
                            .aggregate(pipeline, BasicDBObject.class)
                            .allowDiskUse(true)
                            .batchSize(1000)
                            .cursor();
                        
                        try {
                            while(cursor.hasNext()) {
                                BasicDBObject doc = cursor.next();
                                int apiCollectionId = doc.getInt("_id");
                                int count = doc.getInt("count");
                                groupCountMap.put(apiCollectionId, count);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            cursor.close();
                        }
                    }
                    
                }
            } finally {
                // Clean up ThreadLocal
                Context.accountId.remove();
                Context.userId.remove();
                Context.contextSource.remove();
            }
            return groupCountMap;
        }, executor);
        
        // Wait for both futures to complete and merge results
        try {
            Map<Integer, Integer> nonGroupCounts = nonGroupFuture.get();
            Map<Integer, Integer> groupCounts = groupFuture.get();
            
            // Merge non-group counts
            countMap.putAll(nonGroupCounts);
            
            // Iterate through groupCounts and only add if key doesn't exist in nonGroupCounts
            for (Map.Entry<Integer, Integer> entry : groupCounts.entrySet()) {
                if (!nonGroupCounts.containsKey(entry.getKey())) {
                    countMap.put(entry.getKey(), entry.getValue());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Shutdown executor
            executor.shutdown();
        }
        
        // Step 3: Add code analysis counts (if any)
        Map<String, Integer> codeAnalysisUrlsCountMap = CodeAnalysisApiInfoDao.instance.getUrlsCount();
        if (!codeAnalysisUrlsCountMap.isEmpty()) {
            Map<String, Integer> idToCollectionNameMap = CodeAnalysisCollectionDao.instance.findIdToCollectionNameMap();
            for (String codeAnalysisId: codeAnalysisUrlsCountMap.keySet()) {
                int count = codeAnalysisUrlsCountMap.getOrDefault(codeAnalysisId, 0);
                Integer apiCollectionId = idToCollectionNameMap.get(codeAnalysisId);
                if (apiCollectionId == null) continue;
                
                int currentCount = countMap.getOrDefault(apiCollectionId, 0);
                currentCount += count;
                
                countMap.put(apiCollectionId, currentCount);
            }
        }
        
        return countMap;
    }

    public static List<BasicDBObject> fetchEndpointsInCollection(Bson filter, int skip, int limit, int deltaPeriodValue) {
        List<Bson> pipeline = new ArrayList<>();
        BasicDBObject groupedId =
                new BasicDBObject(ApiInfoKey.API_COLLECTION_ID, "$apiCollectionId")
                        .append(ApiInfoKey.URL, "$url")
                        .append(ApiInfoKey.METHOD, "$method");

        pipeline.add(Aggregates.match(filter));

        try {
            List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
            if(collectionIds != null) {
                pipeline.add(Aggregates.match(Filters.in(SingleTypeInfo._COLLECTION_IDS, collectionIds)));
            }
        } catch(Exception e){
        }

        int recentEpoch = Context.now() - deltaPeriodValue;

        Bson projections = Projections.fields(
                Projections.include(Constants.TIMESTAMP, ApiInfoKey.API_COLLECTION_ID, ApiInfoKey.URL, ApiInfoKey.METHOD),
                Projections.computed("dayOfYearFloat", new BasicDBObject("$divide", new Object[]{"$timestamp", recentEpoch}))
        );

        pipeline.add(Aggregates.project(projections));
        pipeline.add(Aggregates.group(groupedId, Accumulators.min("startTs", "$timestamp"), Accumulators.sum("changesCount", 1)));
        if(limit != -1){
            pipeline.add(Aggregates.skip(skip));
            pipeline.add(Aggregates.limit(limit));
        }

        pipeline.add(Aggregates.sort(Sorts.descending("startTs")));
        MongoCursor<BasicDBObject> endpointsCursor = SingleTypeInfoDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
        List<BasicDBObject> endpoints = new ArrayList<>();
        while(endpointsCursor.hasNext()) {
            endpoints.add(endpointsCursor.next());
        }

        return endpoints;
    }
    public static List<BasicDBObject> fetchEndpointsInCollection(int apiCollectionId, int skip, int limit, int deltaPeriodValue) {
        Bson filter = Filters.in(SingleTypeInfo._COLLECTION_IDS, apiCollectionId);
        return fetchEndpointsInCollection(filter, skip, limit, deltaPeriodValue);
    }

    public static final int STIS_LIMIT = 10_000;

    public static List<SingleTypeInfo> fetchHostSTIsForGroups(int apiCollectionId,int skip, ObjectId lastScannedId, Bson projection){
        Bson filterQ = SingleTypeInfoDao.filterForHostHeader(apiCollectionId, false);
        if(lastScannedId != null){
            filterQ = Filters.and(filterQ, Filters.in(SingleTypeInfo._COLLECTION_IDS, apiCollectionId), Filters.gte(Constants.ID, lastScannedId));
        }
        return SingleTypeInfoDao.instance.findAll(filterQ, skip, STIS_LIMIT, Sorts.ascending(Constants.ID), projection);
    }

    public static List<SingleTypeInfo> fetchHostSTIWithinTsRange(int apiCollectionId, int skip, ObjectId lastScannedId, Bson projection, int startTs, int endTs) {
        Bson filterQ = SingleTypeInfoDao.filterForHostHeader(apiCollectionId, true);
        if(lastScannedId != null){
            filterQ = Filters.and(filterQ, Filters.gte(Constants.ID, lastScannedId));
            if(endTs > 0) {
                filterQ = Filters.and(filterQ, Filters.and(Filters.lte(SingleTypeInfo._TIMESTAMP, endTs), Filters.gte(SingleTypeInfo._TIMESTAMP, startTs)));
            }
        }
        return SingleTypeInfoDao.instance.findAll(filterQ, skip, STIS_LIMIT, Sorts.ascending(Constants.ID), projection);
    }

    public static List<SingleTypeInfo> fetchHostSTI(int apiCollectionId, int skip, ObjectId lastScannedId, Bson projection) {
        Bson filterQ = SingleTypeInfoDao.filterForHostHeader(apiCollectionId, true);
        if(lastScannedId != null){
            filterQ = Filters.and(filterQ, Filters.gte(Constants.ID, lastScannedId));
        }
        return SingleTypeInfoDao.instance.findAll(filterQ, skip, STIS_LIMIT, Sorts.ascending(Constants.ID), projection);
    }

    public static List<BasicDBObject> fetchEndpointsInCollectionUsingHost(int apiCollectionId, int skip, boolean isApiGroup) {
        List<SingleTypeInfo> allUrlsInCollection = new ArrayList<>();
        ObjectId lastScannedId = null;
        
        while(true){
            List<SingleTypeInfo> stis = new ArrayList<>();
            if(isApiGroup) {
                stis = fetchHostSTIsForGroups(apiCollectionId, 0, lastScannedId, projectionForApis);
            } else {
                stis = fetchHostSTI(apiCollectionId, 0, lastScannedId, projectionForApis);
            }
            lastScannedId = stis.size() != 0 ? stis.get(stis.size() - 1).getId() : null;
            allUrlsInCollection.addAll(stis);
            if(stis.size() < STIS_LIMIT){
                break;
            }
        }

        List<BasicDBObject> endpoints = new ArrayList<>();
        for(SingleTypeInfo singleTypeInfo: allUrlsInCollection) {
            BasicDBObject groupId = new BasicDBObject(ApiInfoKey.API_COLLECTION_ID, singleTypeInfo.getApiCollectionId())
                .append(ApiInfoKey.URL, singleTypeInfo.getUrl())
                .append(ApiInfoKey.METHOD, singleTypeInfo.getMethod());
            endpoints.add(new BasicDBObject("startTs", singleTypeInfo.getTimestamp()).append(Constants.ID, groupId));
        }

        return endpoints;
        
    }

    public static List<BasicDBObject> fetchEndpointsInCollectionUsingHostWithTsRange(int apiCollectionId, int startTs, int endTs) {
        List<SingleTypeInfo> allUrlsInCollection = new ArrayList<>();
        int localSkip = 0;
        ObjectId lastScannedId = null;
        
        while(true){
            List<SingleTypeInfo> stis = fetchHostSTIWithinTsRange(apiCollectionId, localSkip, lastScannedId, projectionForApis, startTs, endTs);
            lastScannedId = stis.size() != 0 ? stis.get(stis.size() - 1).getId() : null;
            allUrlsInCollection.addAll(stis);
            if(stis.size() < STIS_LIMIT){
                break;
            }

            localSkip += STIS_LIMIT;
        }

        List<BasicDBObject> endpoints = new ArrayList<>();
        for(SingleTypeInfo singleTypeInfo: allUrlsInCollection) {
            BasicDBObject groupId = new BasicDBObject(ApiInfoKey.API_COLLECTION_ID, singleTypeInfo.getApiCollectionId())
                .append(ApiInfoKey.URL, singleTypeInfo.getUrl())
                .append(ApiInfoKey.METHOD, singleTypeInfo.getMethod());
            endpoints.add(new BasicDBObject("startTs", singleTypeInfo.getTimestamp()).append(Constants.ID, groupId));
        }

        return endpoints;
    }

    public static List<ApiCollection> fetchAllHosts() {
        Bson filters = Filters.exists("hostName", true);
        return ApiCollectionsDao.instance.findAll(filters, Projections.include("hostName", "_id"));
    }

    public static List<ApiCollection> fetchAllActiveHosts() {
        Bson filters = Filters.and(Filters.exists("hostName", true), Filters.ne(ApiCollection._DEACTIVATED, true));
        return ApiCollectionsDao.instance.findAll(filters, Projections.include("hostName", "_id"));
    }

}

package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApiCollectionsDao extends AccountsContextDaoWithRbac<ApiCollection> {

    public static final ApiCollectionsDao instance = new ApiCollectionsDao();

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

    public List<ApiCollection> getMetaForIds(List<Integer> apiCollectionIds) {
        return ApiCollectionsDao.instance.findAll(Filters.in("_id", apiCollectionIds), Projections.exclude("urls"));
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

    public static List<SingleTypeInfo> fetchHostSTI(int apiCollectionId, int skip, ObjectId lastScannedId, Bson projection) {
        Bson filterQ = SingleTypeInfoDao.filterForHostHeader(apiCollectionId, true);
        if(lastScannedId != null){
            filterQ = Filters.and(filterQ, Filters.gte(Constants.ID, lastScannedId));
        }
        return SingleTypeInfoDao.instance.findAll(filterQ, skip, STIS_LIMIT, Sorts.ascending(Constants.ID), projection);
    }

    public static List<BasicDBObject> fetchEndpointsInCollectionUsingHost(int apiCollectionId, int skip, boolean isApiGroup) {
        List<SingleTypeInfo> allUrlsInCollection = new ArrayList<>();
        int localSkip = skip;
        ObjectId lastScannedId = null;
        Bson projection = Projections.include(Constants.TIMESTAMP, ApiInfoKey.API_COLLECTION_ID, ApiInfoKey.URL, ApiInfoKey.METHOD);
        while(true){
            List<SingleTypeInfo> stis = new ArrayList<>();
            if(isApiGroup) {
                stis = fetchHostSTIsForGroups(apiCollectionId, localSkip, lastScannedId, projection);
            } else {
                stis = fetchHostSTI(apiCollectionId, localSkip, lastScannedId, projection);
            }
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

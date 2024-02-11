package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.util.Constants;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApiCollectionsDao extends AccountsContextDao<ApiCollection> {

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

        String[] fieldNames = {"startTs"};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames,false);
    }

    public ApiCollection getMeta(int apiCollectionId) {
        List<ApiCollection> ret = ApiCollectionsDao.instance.findAll(Filters.eq("_id", apiCollectionId), Projections.exclude("urls"));

        return (ret != null && ret.size() > 0) ? ret.get(0) : null;
    }

    public List<ApiCollection> getMetaForIds(List<Integer> apiCollectionIds) {
        return ApiCollectionsDao.instance.findAll(Filters.in("_id", apiCollectionIds), Projections.exclude("urls"));
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

    public Map<Integer, Integer> buildEndpointsCountToApiCollectionMap() {
        Map<Integer, Integer> countMap = new HashMap<>();
        List<Bson> pipeline = new ArrayList<>();

        pipeline.add(Aggregates.match(SingleTypeInfoDao.filterForHostHeader(0, false)));

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

        return countMap;
    }

    public static List<BasicDBObject> fetchEndpointsInCollection(int apiCollectionId, int skip, int limit, int deltaPeriodValue) {
        List<Bson> pipeline = new ArrayList<>();
        BasicDBObject groupedId = 
            new BasicDBObject(ApiInfoKey.API_COLLECTION_ID, "$apiCollectionId")
            .append(ApiInfoKey.URL, "$url")
            .append(ApiInfoKey.METHOD, "$method");

        pipeline.add(Aggregates.match(Filters.in(SingleTypeInfo._COLLECTION_IDS, apiCollectionId)));

        int recentEpoch = Context.now() - deltaPeriodValue;

        Bson projections = Projections.fields(
            Projections.include(Constants.TIMESTAMP, ApiInfoKey.API_COLLECTION_ID, ApiInfoKey.URL, ApiInfoKey.METHOD),
            Projections.computed("dayOfYearFloat", new BasicDBObject("$divide", new Object[]{"$timestamp", recentEpoch}))
        );

        pipeline.add(Aggregates.project(projections));
        pipeline.add(Aggregates.group(groupedId, Accumulators.min("startTs", "$timestamp"), Accumulators.sum("changesCount", 1)));
        pipeline.add(Aggregates.skip(skip));
        pipeline.add(Aggregates.limit(limit));
        pipeline.add(Aggregates.sort(Sorts.descending("startTs")));

        MongoCursor<BasicDBObject> endpointsCursor = SingleTypeInfoDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();

        List<BasicDBObject> endpoints = new ArrayList<>();
        while(endpointsCursor.hasNext()) {
            endpoints.add(endpointsCursor.next());
        }

        return endpoints;
    }

    public static List<SingleTypeInfo> fetchHostSTI(int apiCollectionId, int skip) {
        Bson filterQ = SingleTypeInfoDao.filterForHostHeader(apiCollectionId, true);
        return SingleTypeInfoDao.instance.findAll(filterQ, skip,10_000, null);
    }

    public static List<BasicDBObject> fetchEndpointsInCollectionUsingHost(int apiCollectionId, int skip, int limit, int deltaPeriodValue) {

        ApiCollection apiCollection = ApiCollectionsDao.instance.getMeta(apiCollectionId);

        if(apiCollection == null){
            return new ArrayList<>();
        }

        if (apiCollection.getHostName() == null || apiCollection.getHostName().length() == 0 ) {
            return fetchEndpointsInCollection(apiCollectionId, skip, limit, deltaPeriodValue);
        } else {
            List<SingleTypeInfo> allUrlsInCollection = fetchHostSTI(apiCollectionId, skip);

            List<BasicDBObject> endpoints = new ArrayList<>();
            for(SingleTypeInfo singleTypeInfo: allUrlsInCollection) {
                BasicDBObject groupId = new BasicDBObject(ApiInfoKey.API_COLLECTION_ID, singleTypeInfo.getApiCollectionId())
                    .append(ApiInfoKey.URL, singleTypeInfo.getUrl())
                    .append(ApiInfoKey.METHOD, singleTypeInfo.getMethod());
                endpoints.add(new BasicDBObject("startTs", singleTypeInfo.getTimestamp()).append(Constants.ID, groupId));
            }

            return endpoints;
        }
    }    
}

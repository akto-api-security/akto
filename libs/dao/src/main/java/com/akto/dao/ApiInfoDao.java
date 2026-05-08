package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiCollectionUsers;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiAccessType;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.testing.TestingEndpoints;
import com.akto.dto.ApiStats;
import com.akto.util.Constants;
import com.akto.util.Pair;
import com.mongodb.BasicDBObject;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods.Method;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UnwindOptions;
import com.mongodb.client.model.Updates;

import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ApiInfoDao extends AccountsContextDaoWithRbac<ApiInfo>{

    public static final ApiInfoDao instance = new ApiInfoDao();

    public static final String ID = "_id.";
    public static final int AKTO_DISCOVERED_APIS_COLLECTION_ID = 1333333333;

    public void createIndicesIfAbsent() {

        boolean exists = false;
        for (String col: clients[0].getDatabase(Context.accountId.get()+"").listCollectionNames()){
            if (getCollName().equalsIgnoreCase(col)){
                exists = true;
                break;
            }
        };

        if (!exists) {
            clients[0].getDatabase(Context.accountId.get()+"").createCollection(getCollName());
        }

        String[] fieldNames = {"_id." + ApiInfo.ApiInfoKey.API_COLLECTION_ID};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);

        fieldNames = new String[]{"_id." + ApiInfo.ApiInfoKey.URL};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);
        
        fieldNames = new String[]{"_id." + ApiInfo.ApiInfoKey.API_COLLECTION_ID, "_id." + ApiInfo.ApiInfoKey.URL};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);

        fieldNames = new String[]{ApiInfo.ID_API_COLLECTION_ID, ApiInfo.LAST_SEEN};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{ApiInfo.LAST_TESTED};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{ApiInfo.LAST_CALCULATED_TIME};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] { SingleTypeInfo._COLLECTION_IDS, ApiInfo.ID_URL }, true);

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] {ApiInfo.SEVERITY_SCORE }, false);
                
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] {ApiInfo.RISK_SCORE }, false);

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] { ApiInfo.RISK_SCORE, ApiInfo.ID_API_COLLECTION_ID }, false);

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            new String[] {ApiInfo.DISCOVERED_TIMESTAMP }, false);

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            new String[] {ApiInfo.PARENT_MCP_TOOL_NAMES }, false);
    }
    

    public void updateLastTestedField(ApiInfoKey apiInfoKey){
        instance.getMCollection().updateOne(
                getFilter(apiInfoKey),
                Updates.combine(
                        Updates.set(ApiInfo.LAST_TESTED, Context.now()),
                        Updates.inc(ApiInfo.TOTAL_TESTED_COUNT, 1)
                ));
    }

    public Map<Integer,Integer> getCoverageCount(){
        Map<Integer,Integer> result = new HashMap<>();
        List<Bson> pipeline = new ArrayList<>();
        int oneMonthAgo = Context.now() - Constants.ONE_MONTH_TIMESTAMP ;
        pipeline.add(Aggregates.match(Filters.gte("lastTested", oneMonthAgo)));

        try {
            List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
            if(collectionIds != null) {
                pipeline.add(Aggregates.match(Filters.in(SingleTypeInfo._COLLECTION_IDS, collectionIds)));
            }
        } catch(Exception e){
        }

        UnwindOptions unwindOptions = new UnwindOptions();
        unwindOptions.preserveNullAndEmptyArrays(false);  
        pipeline.add(Aggregates.unwind("$collectionIds", unwindOptions));

        BasicDBObject groupedId2 = new BasicDBObject("apiCollectionId", "$collectionIds");
        pipeline.add(Aggregates.group(groupedId2, Accumulators.sum("count",1)));
        pipeline.add(Aggregates.project(
            Projections.fields(
                Projections.include("count"),
                Projections.computed("apiCollectionId", "$_id.apiCollectionId")
            )
        ));

        MongoCursor<BasicDBObject> collectionsCursor = ApiInfoDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
        while(collectionsCursor.hasNext()){
            try {
                BasicDBObject basicDBObject = collectionsCursor.next();
                result.put(basicDBObject.getInt("apiCollectionId"), basicDBObject.getInt("count"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    public Map<Integer,Integer> getLastTrafficSeen(){
        Map<Integer,Integer> result = new HashMap<>();
        List<Bson> pipeline = new ArrayList<>();

        try {
            List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
            if(collectionIds != null) {
                pipeline.add(Aggregates.match(Filters.in(SingleTypeInfo._COLLECTION_IDS, collectionIds)));
            }
        } catch(Exception e){
        }

        UnwindOptions unwindOptions = new UnwindOptions();
        unwindOptions.preserveNullAndEmptyArrays(false);  
        pipeline.add(Aggregates.unwind("$collectionIds", unwindOptions));

        BasicDBObject groupedId = new BasicDBObject("apiCollectionId", "$collectionIds");
        pipeline.add(Aggregates.sort(Sorts.orderBy(Sorts.descending(ApiInfo.ID_API_COLLECTION_ID), Sorts.descending(ApiInfo.LAST_SEEN))));
        pipeline.add(Aggregates.group(groupedId, Accumulators.first(ApiInfo.LAST_SEEN, "$lastSeen")));
        
        MongoCursor<ApiInfo> cursor = ApiInfoDao.instance.getMCollection().aggregate(pipeline, ApiInfo.class).cursor();
        while(cursor.hasNext()){
            try {
               ApiInfo apiInfo = cursor.next();
               result.put(apiInfo.getId().getApiCollectionId(), apiInfo.getLastSeen());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    public static Float getRiskScore(ApiInfo apiInfo, boolean isSensitive, float riskScoreFromSeverityScore){
        float riskScore = 0;
        if(apiInfo != null){
            if(Context.now() - apiInfo.getLastSeen() <= Constants.ONE_MONTH_TIMESTAMP){
                riskScore += 1;
            }
            if(apiInfo.getApiAccessTypes().contains(ApiAccessType.PUBLIC)){
                riskScore += 1;
            }
        }
        if(isSensitive){
            riskScore += 1;
        }
        riskScore += riskScoreFromSeverityScore;
        return riskScore;
    }

    public static List<ApiInfo> getApiInfosFromList(List<BasicDBObject> list, int apiCollectionId){
        List<ApiInfo> apiInfoList = new ArrayList<>();

        Set<ApiInfoKey> apiInfoKeys = new HashSet<ApiInfoKey>();
        for (BasicDBObject singleTypeInfo: list) {
            singleTypeInfo = (BasicDBObject) (singleTypeInfo.getOrDefault("_id", new BasicDBObject()));
            apiInfoKeys.add(new ApiInfoKey(singleTypeInfo.getInt("apiCollectionId"),singleTypeInfo.getString("url"), Method.fromString(singleTypeInfo.getString("method"))));
        }

        BasicDBObject query = new BasicDBObject();
        if (apiCollectionId > -1) {
            query.append(SingleTypeInfo._COLLECTION_IDS, new BasicDBObject("$in", Arrays.asList(apiCollectionId)));
        }

        int counter = 0;
        int batchSize = 100;

        List<String> urlsToSearch = new ArrayList<>();
        
        for(ApiInfoKey apiInfoKey: apiInfoKeys) {
            urlsToSearch.add(apiInfoKey.getUrl());
            counter++;
            if (counter % batchSize == 0 || counter == apiInfoKeys.size()) {
                query.append("_id.url", new BasicDBObject("$in", urlsToSearch));
                List<ApiInfo> fromDb = ApiInfoDao.instance.findAll(query);
                for (ApiInfo a: fromDb) {
                    if (apiInfoKeys.contains(a.getId())) {
                        a.calculateActualAuth();
                        apiInfoList.add(a);
                    }
                }
                urlsToSearch.clear();
            } 
        }
        return apiInfoList;
    }

    public Pair<ApiStats,ApiStats> fetchApiInfoStats(Bson collectionFilter, Bson apiFilter, int startTimestamp, int endTimestamp) {
        ApiStats apiStatsStart = new ApiStats(startTimestamp);
        ApiStats apiStatsEnd = new ApiStats(endTimestamp);

        int totalApis = 0;
        int apisTestedInLookBackPeriod = 0;
        float totalRiskScore = 0;
        int apisInScopeForTesting = 0;

        Map<Integer,Boolean> collectionsMap = ApiCollectionsDao.instance.findAll(collectionFilter, Projections.include(ApiCollection.ID, ApiCollection.IS_OUT_OF_TESTING_SCOPE))
            .stream()
            .collect(Collectors.toMap(ApiCollection::getId, ApiCollection::getIsOutOfTestingScope));

        // we need only end timestamp filter because data needs to be till end timestamp while start timestamp is for calculating 
        Bson filter = Filters.and(apiFilter,
            Filters.or(
                Filters.lte(ApiInfo.DISCOVERED_TIMESTAMP, endTimestamp),
                Filters.and(
                    Filters.exists(ApiInfo.DISCOVERED_TIMESTAMP, false),
                    Filters.lte(ApiInfo.LAST_SEEN, endTimestamp) // in case discovered timestamp is not set
                )// in case discovered timestamp is not set
            )
        );
        try {
            List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(),Context.accountId.get());
            if (collectionIds != null) {
                filter = Filters.and(filter, Filters.in("collectionIds", collectionIds));
            }
        } catch (Exception e){
        }
        MongoCursor<ApiInfo> cursor = instance.getMCollection().find(filter).cursor();
        boolean isOutOfTestingScope = false;
        while(cursor.hasNext()) {
            ApiInfo apiInfo = cursor.next();
            isOutOfTestingScope = collectionsMap.getOrDefault(apiInfo.getId().getApiCollectionId(), false);
            if (apiInfo.getDiscoveredTimestamp() <= startTimestamp) {
                apiInfo.addStats(apiStatsStart);
                apiStatsStart.setTotalAPIs(apiStatsStart.getTotalAPIs()+1);
                apiStatsStart.setTotalRiskScore(apiStatsStart.getTotalRiskScore() + apiInfo.getRiskScore());
            }

            apiInfo.addStats(apiStatsEnd);
            totalApis += 1;
            totalRiskScore += apiInfo.getRiskScore();
            if(!isOutOfTestingScope){
                if (apiInfo.getLastTested() > (Context.now() - 30 * 24 * 60 * 60)) apisTestedInLookBackPeriod += 1;
                apisInScopeForTesting += 1;
            }
            String severity = apiInfo.findSeverity();
            apiStatsEnd.addSeverityCount(severity);
        }
        cursor.close();

        apiStatsEnd.setTotalAPIs(totalApis);
        apiStatsEnd.setApisTestedInLookBackPeriod(apisTestedInLookBackPeriod);
        apiStatsEnd.setTotalRiskScore(totalRiskScore);
        apiStatsEnd.setTotalInScopeForTestingApis(apisInScopeForTesting);

        return new Pair<>(apiStatsStart, apiStatsEnd);
    }

    public Map<Integer, BasicDBObject> getApisListMissingInApiInfoDao(Bson customFilterForCollection, int startTimestamp, int endTimestamp){
        Map<Integer, BasicDBObject> result = new HashMap<>();
        ExecutorService executor = Executors.newFixedThreadPool(20);
        Set<Integer> apiCollectionIds = ApiCollectionsDao.instance.findAll(customFilterForCollection, Projections.include(ApiCollection.ID)).stream()
                .map(ApiCollection::getId)
                .collect(Collectors.toSet());

        List<Future<Void>> futures = new ArrayList<>();
        int accountId = Context.accountId.get();
        
        for (Integer apiCollectionId : apiCollectionIds) {
            futures.add(executor.submit(() -> {
                Context.accountId.set(accountId);
                Set<ApiInfoKey> missingApiInfoKeysInSti = new HashSet<>();
                List<ApiInfoKey> missingApiInfoKeysInSamples = new ArrayList<>();
                List<ApiInfoKey> missingApiInfoKeysForAuth = new ArrayList<>();
                List<ApiInfoKey> missingApiInfoKeysForAccessType = new ArrayList<>();
                List<ApiInfoKey> redundantApiInfoKeys = new ArrayList<>();
                BasicDBObject missingInfos = new BasicDBObject();

                List<BasicDBObject> endpoints = ApiCollectionsDao.fetchEndpointsInCollectionUsingHostWithTsRange(apiCollectionId, startTimestamp, endTimestamp);

                Bson apiInfoFilter = Filters.eq(ApiInfo.ID_API_COLLECTION_ID, apiCollectionId);
                if(endTimestamp > 0) {
                    apiInfoFilter = Filters.and(apiInfoFilter, Filters.or(
                            Filters.lte(ApiInfo.DISCOVERED_TIMESTAMP, endTimestamp),
                            Filters.and(
                                    Filters.exists(ApiInfo.DISCOVERED_TIMESTAMP, false),
                                    Filters.lte(ApiInfo.LAST_SEEN, endTimestamp) // in case discovered timestamp is not set
                            )// in case discovered timestamp is not set
                    ));
                }

                List<ApiInfo> actualApiInfosInColl = ApiInfoDao.instance.findAll(
                    apiInfoFilter,
                    Projections.include(Constants.ID, ApiInfo.ALL_AUTH_TYPES_FOUND, ApiInfo.API_ACCESS_TYPES, ApiInfo.API_TYPE)
                );
                Map<ApiInfoKey, ApiInfo> apiInfos = actualApiInfosInColl.stream()
                        .collect(Collectors.toMap(ApiInfo::getId, Function.identity()));

                Set<ApiInfoKey> apiInfoKeysFromStis = new HashSet<>();
                for (BasicDBObject singleTypeInfo : endpoints) {
                    // apiinfos which are not present in the sti
                    singleTypeInfo = (BasicDBObject) (singleTypeInfo.getOrDefault("_id", new BasicDBObject()));
                    int apiCollectionIdFromDb = singleTypeInfo.getInt("apiCollectionId");
                    String url = singleTypeInfo.getString("url");
                    String method = singleTypeInfo.getString("method");
                    ApiInfoKey apiInfoKey = new ApiInfoKey(apiCollectionIdFromDb, url, Method.fromString(method));
                    apiInfoKeysFromStis.add(apiInfoKey);
                    if (apiInfos.get(apiInfoKey) == null) {
                        missingApiInfoKeysInSti.add(apiInfoKey);
                    }
                }

                Set<ApiInfoKey> sampleApisKeys = SampleDataDao.instance.findAll(Filters.eq(ApiInfo.ID_API_COLLECTION_ID, apiCollectionId), Projections.include(Constants.ID)).stream().map((data) -> {
                    return new ApiInfoKey(apiCollectionId, data.getId().getUrl(), data.getId().getMethod());
                }).collect(Collectors.toSet());
                for (ApiInfoKey apiInfoKey : sampleApisKeys) {
                    if (apiInfos.get(apiInfoKey) == null) {
                        missingApiInfoKeysInSamples.add(apiInfoKey);
                    }
                }

                for (ApiInfo apiInfo : actualApiInfosInColl) {
                    ApiInfoKey apiInfoKey = apiInfo.getId();
                    if (!apiInfoKeysFromStis.contains(apiInfoKey)) {
                        redundantApiInfoKeys.add(apiInfoKey);
                    }
                    if (apiInfo.getAllAuthTypesFound() == null || apiInfo.getAllAuthTypesFound().isEmpty()) {
                        missingApiInfoKeysForAuth.add(apiInfoKey);
                    }
                    if (apiInfo.getApiAccessTypes() == null || apiInfo.getApiAccessTypes().isEmpty()) {
                        missingApiInfoKeysForAccessType.add(apiInfoKey);
                    }
                    
                }

                if(missingApiInfoKeysInSti.isEmpty() && missingApiInfoKeysInSamples.isEmpty() && missingApiInfoKeysForAuth.isEmpty() && missingApiInfoKeysForAccessType.isEmpty() && redundantApiInfoKeys.isEmpty()) {
                    return null;
                }

                missingInfos.append("missingApiInfoKeysInSti", missingApiInfoKeysInSti);
                missingInfos.append("missingApiInfoKeysInSamples", missingApiInfoKeysInSamples);
                missingInfos.append("missingApiInfoKeysForAuth", missingApiInfoKeysForAuth);
                missingInfos.append("missingApiInfoKeysForAccessType", missingApiInfoKeysForAccessType);
                missingInfos.append("redundantApiInfoKeys", redundantApiInfoKeys);
                result.put(apiCollectionId, missingInfos);
                return null;
            }));
        }
     
        executor.shutdown();
        try {
            if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return result;
    }

    @Override
    public String getCollName() {
        return "api_info";
    }

    @Override
    public Class<ApiInfo> getClassT() {
        return ApiInfo.class;
    }

    public static Bson getFilter(ApiInfo.ApiInfoKey apiInfoKey) {
        return getFilter(apiInfoKey.getUrl(), apiInfoKey.getMethod().name(), apiInfoKey.getApiCollectionId());
    }

    public static Bson getFilter(String url, String method, int apiCollectionId) {
        return Filters.and(
                Filters.eq("_id.url", url),
                Filters.eq("_id.method", method),
                Filters.eq("_id.apiCollectionId", apiCollectionId)
        );
    }

    @Override
    public String getFilterKeyString() {
        return TestingEndpoints.getFilterPrefix(ApiCollectionUsers.CollectionType.Id_ApiCollectionId) + ApiInfo.ApiInfoKey.API_COLLECTION_ID;
    }
}

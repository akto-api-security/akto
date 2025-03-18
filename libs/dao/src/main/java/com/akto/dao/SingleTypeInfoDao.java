package com.akto.dao;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.CollectionConditions.MethodCondition;
import com.akto.dto.CustomDataType;
import com.akto.dto.SensitiveParamInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.util.Util;
import com.akto.dto.type.URLMethods.Method;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.*;

import org.bson.conversions.Bson;

public class SingleTypeInfoDao extends AccountsContextDao<SingleTypeInfo> {

    public static final SingleTypeInfoDao instance = new SingleTypeInfoDao();

    private SingleTypeInfoDao() {}

    @Override
    public String getCollName() {
        return "single_type_info";
    }

    @Override
    public Class<SingleTypeInfo> getClassT() {
        return SingleTypeInfo.class;
    }

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

        String[] fieldNames = {SingleTypeInfo._URL, SingleTypeInfo._METHOD, SingleTypeInfo._RESPONSE_CODE, SingleTypeInfo._IS_HEADER, SingleTypeInfo._PARAM, SingleTypeInfo.SUB_TYPE, SingleTypeInfo._API_COLLECTION_ID};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);

        fieldNames = new String[] { SingleTypeInfo._API_COLLECTION_ID, SingleTypeInfo._TIMESTAMP };
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{SingleTypeInfo._PARAM, SingleTypeInfo._API_COLLECTION_ID};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);

        fieldNames = new String[]{SingleTypeInfo._RESPONSE_CODE, SingleTypeInfo._IS_HEADER, SingleTypeInfo._PARAM, SingleTypeInfo.SUB_TYPE, SingleTypeInfo._API_COLLECTION_ID};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);

        fieldNames = new String[]{SingleTypeInfo.SUB_TYPE, SingleTypeInfo._RESPONSE_CODE};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            new String[] { SingleTypeInfo.LAST_SEEN, SingleTypeInfo._API_COLLECTION_ID }, false);

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            new String[] { SingleTypeInfo._COLLECTION_IDS }, true);
            
        fieldNames =  new String[]{SingleTypeInfo._RESPONSE_CODE, SingleTypeInfo.SUB_TYPE, SingleTypeInfo._TIMESTAMP};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);

        // needed for usage metric calculation
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            new String[] { SingleTypeInfo.LAST_SEEN }, true);
        
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            new String[] { SingleTypeInfo._TIMESTAMP }, true);
    }

    public static Bson filterForSTIUsingURL(int apiCollectionId, String url, URLMethods.Method method) {
        return Filters.and(
                Filters.eq("apiCollectionId", apiCollectionId),
                Filters.eq("url", url),
                Filters.eq("method", method.name())
        );
    }

    public static Bson filterForHostHeader(int apiCollectionId, boolean useApiCollectionId) {
        List<Bson> filters = new ArrayList<>();
        filters.add(Filters.eq(SingleTypeInfo._RESPONSE_CODE, -1));
        filters.add(Filters.eq(SingleTypeInfo._IS_HEADER, true));
        filters.add(Filters.eq(SingleTypeInfo._PARAM, "host"));
        filters.add(Filters.eq(SingleTypeInfo.SUB_TYPE, SingleTypeInfo.GENERIC.getName()));

        if (useApiCollectionId) filters.add(Filters.eq(SingleTypeInfo._API_COLLECTION_ID, apiCollectionId));

        return Filters.and(filters);
    }

    public static List<ApiInfo.ApiInfoKey> fetchLatestEndpointsForTesting(int startTimestamp, int endTimestamp, int apiCollectionId) {
        List<ApiInfo.ApiInfoKey> endpoints = new ArrayList<>();
        Bson hostFilterQ = SingleTypeInfoDao.filterForHostHeader(apiCollectionId, true);
        Bson filterQWithTs = Filters.and(
                Filters.gte(SingleTypeInfo._TIMESTAMP, startTimestamp),
                Filters.lte(SingleTypeInfo._TIMESTAMP, endTimestamp),
                Filters.eq(SingleTypeInfo._API_COLLECTION_ID, apiCollectionId),
                hostFilterQ
        );

        List<SingleTypeInfo> latestHosts = SingleTypeInfoDao.instance.findAll(filterQWithTs, 0, 10000, Sorts.descending("timestamp"), Projections.exclude("values"));
        if (latestHosts.size() == 0) {
            List<Bson> pipeline = new ArrayList<>();

            BasicDBObject groupedId = 
                new BasicDBObject("apiCollectionId", "$apiCollectionId")
                .append("url", "$url")
                .append("method", "$method");
            pipeline.add(Aggregates.match(Filters.eq("apiCollectionId", apiCollectionId)));
            pipeline.add(Aggregates.group(groupedId, Accumulators.min("startTs", "$timestamp"),Accumulators.sum("countTs",1)));
            pipeline.add(Aggregates.match(Filters.gte("startTs", startTimestamp)));
            pipeline.add(Aggregates.match(Filters.lte("startTs", endTimestamp)));
            MongoCursor<BasicDBObject> endpointsCursor = SingleTypeInfoDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
            while(endpointsCursor.hasNext()) {
                BasicDBObject basicDBObject = endpointsCursor.next();
                BasicDBObject id = (BasicDBObject) basicDBObject.get("_id");
                int collectionId = id.getInt("apiCollectionId");
                String url = id.getString("url");
                String method = id.getString("method");
                endpoints.add(new ApiInfoKey(collectionId, url, URLMethods.Method.valueOf(method)));
            }
        } else {
            for (SingleTypeInfo sti: latestHosts) {
                endpoints.add(new ApiInfoKey(sti.getApiCollectionId(), sti.getUrl(), URLMethods.Method.valueOf(sti.getMethod())));
            }
        }

        return endpoints;
    }

    public List<SingleTypeInfo> fetchAll() {
        return this.findAll(new BasicDBObject());
    }

    public static Bson createFiltersWithoutSubType(SingleTypeInfo info) {
        List<Bson> filters = createFiltersBasic(info);
        return Filters.and(filters);
    }

    public static Map<String, Object> createFiltersMap(SingleTypeInfo info) {
        Map<String, Object> filterMap = new HashMap<>();
        filterMap.put("url", info.getUrl());
        filterMap.put("method", info.getMethod());
        filterMap.put("responseCode", info.getResponseCode());
        filterMap.put("isHeader", info.getIsHeader());
        filterMap.put("param", info.getParam());
        filterMap.put("apiCollectionId", info.getApiCollectionId());
        filterMap.put("subType", info.getSubType().getName());
        filterMap.put("isUrlParam", info.getIsUrlParam());
        return filterMap;
    }

    public static List<Bson> createFiltersBasic(SingleTypeInfo info) {
        List<Bson> filters = new ArrayList<>();
        filters.add(Filters.eq("url", info.getUrl()));
        filters.add(Filters.eq("method", info.getMethod()));
        filters.add(Filters.eq("responseCode", info.getResponseCode()));
        filters.add(Filters.eq("isHeader", info.getIsHeader()));
        filters.add(Filters.eq("param", info.getParam()));
        filters.add(Filters.eq("apiCollectionId", info.getApiCollectionId()));

        List<Boolean> urlParamQuery;
        if (info.getIsUrlParam()) {
            urlParamQuery = Collections.singletonList(true);
        } else {
            urlParamQuery = Arrays.asList(false, null);
        }

        filters.add(Filters.in("isUrlParam", urlParamQuery));
        return filters;
    }

    public static Bson createFilters(SingleTypeInfo info) {
        List<Bson> filters = createFiltersBasic(info);
        filters.add(Filters.eq("subType", info.getSubType().getName()));
        return Filters.and(filters);
    }

    public Set<String> getUniqueEndpoints(int apiCollectionId) {
        Bson filter = Filters.eq("apiCollectionId", apiCollectionId);
        return instance.findDistinctFields("url", String.class, filter);
    }

    public List<String> sensitiveSubTypeNames() {
        List<String> sensitiveSubTypes = new ArrayList<>();
        // AKTO sensitive
        for (SingleTypeInfo.SubType subType: SingleTypeInfo.subTypeMap.values()) {
            if (subType.isSensitiveAlways()) {
                sensitiveSubTypes.add(subType.getName());
            }
        }

        // Custom data type sensitive
        for (CustomDataType customDataType: SingleTypeInfo.getCustomDataTypeMap(Context.accountId.get()).values()) {
            if (customDataType.isSensitiveAlways()) {
                sensitiveSubTypes.add(customDataType.getName());
            }
        }

        return sensitiveSubTypes;
    }

    public List<String> sensitiveSubTypeInRequestNames() {
        List<String> sensitiveInRequest = new ArrayList<>();
        for (SingleTypeInfo.SubType subType: SingleTypeInfo.subTypeMap.values()) {
            if (subType.getSensitivePosition().contains(SingleTypeInfo.Position.REQUEST_HEADER) || subType.getSensitivePosition().contains(SingleTypeInfo.Position.REQUEST_PAYLOAD)) {
                sensitiveInRequest.add(subType.getName());
            }
        }

        for (CustomDataType customDataType: SingleTypeInfo.getCustomDataTypeMap(Context.accountId.get()).values()) {
            if (customDataType.getSensitivePosition().contains(SingleTypeInfo.Position.REQUEST_HEADER) || customDataType.getSensitivePosition().contains(SingleTypeInfo.Position.REQUEST_PAYLOAD)) {
                sensitiveInRequest.add(customDataType.getName());
            }
        }
        return sensitiveInRequest;
    }

    public List<String> sensitiveSubTypeInResponseNames() {
        List<String> sensitiveInResponse = new ArrayList<>();
        for (SingleTypeInfo.SubType subType: SingleTypeInfo.subTypeMap.values()) {
            if (subType.getSensitivePosition().contains(SingleTypeInfo.Position.RESPONSE_HEADER) || subType.getSensitivePosition().contains(SingleTypeInfo.Position.RESPONSE_PAYLOAD)) {
                sensitiveInResponse.add(subType.getName());
            }
        }
        for (CustomDataType customDataType: SingleTypeInfo.getCustomDataTypeMap(Context.accountId.get()).values()) {
            if (customDataType.getSensitivePosition().contains(SingleTypeInfo.Position.RESPONSE_HEADER) || customDataType.getSensitivePosition().contains(SingleTypeInfo.Position.RESPONSE_PAYLOAD)) {
                sensitiveInResponse.add(customDataType.getName());
            }
        }
        return sensitiveInResponse;
    }

    public Bson filterForSensitiveParamsExcludingUserMarkedSensitive(Integer apiCollectionId, String url, String method, String subType) {
        // apiCollectionId null then no filter for apiCollectionId
        List<String> sensitiveSubTypes = sensitiveSubTypeNames();

        Bson alwaysSensitiveFilter = Filters.in("subType", sensitiveSubTypes);

        List<String> sensitiveInResponse;
        List<String> sensitiveInRequest;
        if (subType != null) {
            sensitiveInRequest = Collections.singletonList(subType);
            sensitiveInResponse = Collections.singletonList(subType);
        } else {
            sensitiveInResponse = sensitiveSubTypeInResponseNames();
            sensitiveInRequest = sensitiveSubTypeInRequestNames();
        }

        Bson sensitiveInResponseFilter = Filters.and(
                Filters.in("subType",sensitiveInResponse ),
                Filters.gt("responseCode", -1)
        );
        Bson sensitiveInRequestFilter = Filters.and(
                Filters.in("subType",sensitiveInRequest ),
                Filters.eq("responseCode", -1)
        );

        List<Bson> filters = new ArrayList<>();

        List<Bson> subTypeFilters =  new ArrayList<>();
        subTypeFilters.add(sensitiveInRequestFilter);
        subTypeFilters.add(sensitiveInResponseFilter);
        if (subType == null) subTypeFilters.add(alwaysSensitiveFilter);

        filters.add(Filters.or(subTypeFilters));

        if (apiCollectionId != null && apiCollectionId != -1) {
            filters.add(Filters.in(SingleTypeInfo._COLLECTION_IDS, apiCollectionId));
        }

        if (url != null) {
            filters.add(Filters.eq("url", url));
        }

        if (method != null) {
            filters.add(Filters.eq("method",method));
        }

        return Filters.and(filters);
    }

    public Bson filterForAllNewParams(int startTimestamp,int endTimestamp){

        List<Bson> filters = new ArrayList<>();

        filters.add(Filters.gte("timestamp",startTimestamp));
        filters.add(Filters.lte("timestamp",endTimestamp));

        return Filters.and(filters);
    }

    public Set<String> getSensitiveEndpoints(int apiCollectionId, String url, String method) {
        Set<String> urls = new HashSet<>();

        // User manually set sensitive
        List<SensitiveParamInfo> customSensitiveList = SensitiveParamInfoDao.instance.findAll(
                Filters.and(
                        Filters.eq("sensitive", true),
                        Filters.eq("apiCollectionId", apiCollectionId)
                )
        );
        for (SensitiveParamInfo sensitiveParamInfo: customSensitiveList) {
            urls.add(sensitiveParamInfo.getUrl());
        }

        Bson filter = filterForSensitiveParamsExcludingUserMarkedSensitive(apiCollectionId, url, method, null);

        urls.addAll(instance.findDistinctFields("url", String.class, filter));

        return urls;
    }
    
    public void resetCount() {
        instance.getMCollection().updateMany(
                Filters.gt("count", 0),
                Updates.set("count", 0)
        );
    }

    // to get results irrespective of collections use negative value for apiCollectionId
    public List<ApiInfo.ApiInfoKey> fetchEndpointsInCollection(int apiCollectionId) {
        Bson filter = null;
        if (apiCollectionId != -1) {
            filter = Filters.in(SingleTypeInfo._COLLECTION_IDS, apiCollectionId);
        }
        List<Bson> pipeline = getPipelineForEndpoints(filter);
        return processPipelineForEndpoint(pipeline);
    }

    public List<ApiInfo.ApiInfoKey> fetchEndpointsInCollection(Method method) {
        Bson filter = null;
        if (method == null) {
            return new ArrayList<>();
        }
        // the filter obtained uses index.
        filter = MethodCondition.createMethodFilter(method);
        List<Bson> pipeline = getPipelineForEndpoints(filter);
        pipeline.add(Aggregates.limit(50));
        return processPipelineForEndpoint(pipeline);
    }

    public List<ApiInfo.ApiInfoKey> fetchEndpointsBySubType(SingleTypeInfo.SubType subType, int skip, int limit) {
        Bson filter = Filters.eq("subType", subType.getName());
        List<Bson> pipeline = getPipelineForEndpoints(filter);
        pipeline.add(Aggregates.limit(limit));
        pipeline.add(Aggregates.skip(skip));
        return processPipelineForEndpoint(pipeline);
    }

    public List<ApiInfo.ApiInfoKey> fetchSensitiveEndpoints(int apiCollectionId, int skip, int limit) {
        Bson filter = filterForSensitiveParamsExcludingUserMarkedSensitive(apiCollectionId,
                null, null, null);
        List<Bson> pipeline = getPipelineForEndpoints(filter);
        pipeline.add(Aggregates.limit(limit));
        pipeline.add(Aggregates.skip(skip));
        return processPipelineForEndpoint(pipeline);
    }

    private List<Bson> getPipelineForEndpoints(Bson matchCriteria) {
        List<Bson> pipeline = new ArrayList<>();
        BasicDBObject groupedId =
                new BasicDBObject("apiCollectionId", "$apiCollectionId")
                        .append("url", "$url")
                        .append("method", "$method");

        if(matchCriteria != null) {
            pipeline.add(Aggregates.match(matchCriteria));
        }

        Bson projections = Projections.fields(
                Projections.include("timestamp", "lastSeen", "apiCollectionId", "url", "method", SingleTypeInfo._COLLECTION_IDS)
        );

        pipeline.add(Aggregates.project(projections));
        pipeline.add(Aggregates.group(groupedId));
        pipeline.add(Aggregates.sort(Sorts.descending(SingleTypeInfo._TIMESTAMP)));
        return pipeline;
    }

    private List<ApiInfoKey> processPipelineForEndpoint(List<Bson> pipeline){
        MongoCursor<BasicDBObject> endpointsCursor = instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();

        List<ApiInfo.ApiInfoKey> endpoints = new ArrayList<>();
        while(endpointsCursor.hasNext()) {
            BasicDBObject v = endpointsCursor.next();
            try {
                BasicDBObject vv = (BasicDBObject) v.get("_id");
                ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(
                        (int) vv.get("apiCollectionId"),
                        (String) vv.get("url"),
                        URLMethods.Method.fromString((String) vv.get("method"))
                );
                endpoints.add(apiInfoKey);
            } catch (Exception e) {
                e.printStackTrace();

            }
        }

        return endpoints;
    }

    public List<SingleTypeInfo> fetchStiOfCollections(List<Integer> apiCollectionIds) {
        Bson filters = Filters.in(SingleTypeInfo._API_COLLECTION_ID, apiCollectionIds);
        return instance.findAll(filters);
    }

    public void deleteValues() {
        instance.getMCollection().updateMany(
                Filters.exists(SingleTypeInfo._VALUES),
                Updates.unset(SingleTypeInfo._VALUES)
        );
    }

    public long getEstimatedCount(){
        return instance.getMCollection().estimatedDocumentCount();
    }

    public Map<String,Map<String, Integer>> buildSubTypeCountMap(int startTimestamp, int endTimestamp) {

        ArrayList<Bson> filterList = new ArrayList<>();
        filterList.add(Filters.gt("timestamp", startTimestamp));
        filterList.add(Filters.lt("timestamp", endTimestamp));

        List<String> sensitiveInRequest = SingleTypeInfoDao.instance.sensitiveSubTypeInRequestNames();
        sensitiveInRequest.addAll(SingleTypeInfoDao.instance.sensitiveSubTypeNames());
        Bson sensitveSubTypeFilterRequest = Filters.in("subType",sensitiveInRequest);
        List<Bson> requestFilterList = new ArrayList<>();
        requestFilterList.add(sensitveSubTypeFilterRequest);
        requestFilterList.addAll(filterList);
        requestFilterList.add(Filters.eq("responseCode", -1));

        List<String> sensitiveInResponse = SingleTypeInfoDao.instance.sensitiveSubTypeInResponseNames();
        sensitiveInResponse.addAll(SingleTypeInfoDao.instance.sensitiveSubTypeNames());
        Bson sensitveSubTypeFilterResponse = Filters.in("subType",sensitiveInResponse);
        List<Bson> responseFilterList = new ArrayList<>();
        responseFilterList.add(sensitveSubTypeFilterResponse);
        responseFilterList.addAll(filterList);
        responseFilterList.add(Filters.gt("responseCode", -1));

        Map<String, Integer> requestResult = execute(requestFilterList);
        Map<String, Integer> responseResult = execute(responseFilterList);

        Map<String, Map<String, Integer>> resultMap = new HashMap<>();
        resultMap.put("REQUEST", requestResult);
        resultMap.put("RESPONSE", responseResult);
        
        return resultMap;
    }

    public Map<String, Integer> execute(List<Bson> filterList) {
        Map<String, Integer> countMap = new HashMap<>();
        List<Bson> pipeline = new ArrayList<>();

        pipeline.add(Aggregates.match(Filters.and(filterList)));

        BasicDBObject groupedId = new BasicDBObject("subType", "$subType");
        pipeline.add(Aggregates.group(groupedId, Accumulators.sum("count",1)));

        MongoCursor<BasicDBObject> endpointsCursor = SingleTypeInfoDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
        while(endpointsCursor.hasNext()) {
            try {
                BasicDBObject basicDBObject = endpointsCursor.next();
                String subType = ((BasicDBObject) basicDBObject.get("_id")).getString("subType");
                int count = basicDBObject.getInt("count");
                countMap.put(subType, count);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return countMap;
    }

    private List<Bson> generateFilterForSubtypes(List<String> sensitiveParameters, BasicDBObject groupedId, Boolean inResponseOnly){
        int codeValue = inResponseOnly ? 0 : -1 ;
        List<Bson> pipeline = new ArrayList<>();
        Bson filterOnResponse = Filters.gte(SingleTypeInfo._RESPONSE_CODE, codeValue);
        Bson sensitiveSubTypeFilter = Filters.and(Filters.in(SingleTypeInfo.SUB_TYPE,sensitiveParameters), filterOnResponse);
        pipeline.add(Aggregates.match(sensitiveSubTypeFilter));
        pipeline.add(Aggregates.group(groupedId,Accumulators.addToSet("subTypes", "$subType")));
        return pipeline;
    }

    public Map<Integer,List<String>> getSensitiveSubtypesDetectedForCollection(List<String> sensitiveParameters){
        BasicDBObject groupedId = new BasicDBObject(SingleTypeInfo._API_COLLECTION_ID, "$apiCollectionId");
        List<Bson> pipeline = generateFilterForSubtypes(sensitiveParameters, groupedId, false);
        MongoCursor<BasicDBObject> collectionsCursor = SingleTypeInfoDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
        Map<Integer,List<String>> result = new HashMap<>();
        while(collectionsCursor.hasNext()){
            try {
                BasicDBObject basicDBObject = collectionsCursor.next();
                int apiCollectionId = ((BasicDBObject) basicDBObject.get("_id")).getInt("apiCollectionId");
                List<String> subtypes = (List<String>) basicDBObject.get("subTypes");
                result.put(apiCollectionId, subtypes);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return result ;
    }

    public Integer getSensitiveApisCount(List<String> sensitiveParameters){
       
        BasicDBObject groupedId = new BasicDBObject(SingleTypeInfo._API_COLLECTION_ID, "$apiCollectionId")
                                        .append(SingleTypeInfo._URL, "$url")
                                        .append(SingleTypeInfo._METHOD, "$method");
        List<Bson> pipeline = generateFilterForSubtypes(sensitiveParameters, groupedId, true);
        pipeline.add(Aggregates.count("totalSensitiveApis"));

        MongoCursor<BasicDBObject> cursor = SingleTypeInfoDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();

        if(cursor.hasNext()){
            BasicDBObject basicDBObject = cursor.next();
            return basicDBObject.getInt("totalSensitiveApis");
        }else{
            return 0;
        }

    }

    public static final int LARGE_LIMIT = 10_000;
    public static final String _COUNT = "count";

    public int countEndpoints(Bson filters) {
        int ret = 0;

        List<Bson> pipeline = getPipelineForEndpoints(filters);
        pipeline.add(Aggregates.limit(LARGE_LIMIT));
        pipeline.add(Aggregates.count());

        MongoCursor<BasicDBObject> endpointsCursor = SingleTypeInfoDao.instance.getMCollection()
                .aggregate(pipeline, BasicDBObject.class).cursor();

        while (endpointsCursor.hasNext()) {
            ret = endpointsCursor.next().getInt(_COUNT);
            break;
        }

        return ret;
    }

    public Map<ApiInfo.ApiInfoKey, List<String>> fetchRequestParameters(List<ApiInfo.ApiInfoKey> apiInfoKeys) {
        Map<ApiInfo.ApiInfoKey, List<String>> result = new HashMap<>();
        if (apiInfoKeys == null || apiInfoKeys.isEmpty()) return result;

        List<Bson> pipeline = new ArrayList<>();

        List<Bson> filters = new ArrayList<>();
        for (ApiInfo.ApiInfoKey apiInfoKey: apiInfoKeys) {
            filters.add(
                    Filters.and(
                            Filters.eq(SingleTypeInfo._API_COLLECTION_ID, apiInfoKey.getApiCollectionId()),
                            Filters.eq(SingleTypeInfo._URL, apiInfoKey.getUrl()),
                            Filters.eq(SingleTypeInfo._METHOD, apiInfoKey.getMethod().name()),
                            Filters.eq(SingleTypeInfo._RESPONSE_CODE, -1),
                            Filters.eq(SingleTypeInfo._IS_HEADER, false)
                    )
            );
        }

        pipeline.add(Aggregates.match(Filters.or(filters)));

        BasicDBObject groupedId = new BasicDBObject("apiCollectionId", "$apiCollectionId")
                        .append("url", "$url")
                        .append("method", "$method");


        Bson projections = Projections.fields(
                Projections.include( "apiCollectionId", "url", "method", "param")
        );

        pipeline.add(Aggregates.project(projections));

        pipeline.add(Aggregates.group(groupedId,Accumulators.addToSet("params", "$param")));


        MongoCursor<BasicDBObject> stiCursor = instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
        while (stiCursor.hasNext()) {
            BasicDBObject next = stiCursor.next();
            BasicDBObject id = (BasicDBObject) next.get("_id");
            List<String> params = (List<String>) next.get("params");
            ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(id.getInt("apiCollectionId"), id.getString("url"), URLMethods.Method.fromString(id.getString("method")));
            result.put(apiInfoKey, params);
        }

        return result;
    }

    public Set<String> fetchHosts(List<Integer> apiCollectionIds) {
        List<Bson> pipeline = new ArrayList<>();
        Bson filter = Filters.and(
                Filters.in(SingleTypeInfo._API_COLLECTION_ID, apiCollectionIds),
                Filters.eq(SingleTypeInfo._RESPONSE_CODE, -1),
                Filters.eq(SingleTypeInfo._IS_HEADER, true)
        );

        pipeline.add(Aggregates.match(filter));
        pipeline.add(Aggregates.project(Projections.include(SingleTypeInfo._URL)));

        BasicDBObject groupedId =  new BasicDBObject("url", "$"+SingleTypeInfo._URL);
        pipeline.add(Aggregates.group(groupedId));


        Set<String> hosts = new HashSet<>();
        MongoCursor<BasicDBObject> stiCursor = instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
        while (stiCursor.hasNext()) {
            BasicDBObject next = stiCursor.next();
            BasicDBObject id = (BasicDBObject) next.get("_id");
            String url = id.getString("url");
            try {
                URI uri = new URI(url);
                String host = uri.getHost();
                hosts.add(host);
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }
        return hosts;
    }

}

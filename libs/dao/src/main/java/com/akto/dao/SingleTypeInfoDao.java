package com.akto.dao;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

import com.akto.dao.context.Context;
import com.akto.dto.AktoDataType;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.CollectionConditions.MethodCondition;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.CustomDataType;
import com.akto.dto.SensitiveParamInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.util.Constants;
import com.akto.dto.type.URLMethods.Method;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.*;

import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

public class SingleTypeInfoDao extends AccountsContextDaoWithRbac<SingleTypeInfo> {

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

        fieldNames = new String[]{SingleTypeInfo._RESPONSE_CODE, SingleTypeInfo._IS_HEADER, SingleTypeInfo._PARAM, SingleTypeInfo.SUB_TYPE, SingleTypeInfo._API_COLLECTION_ID, Constants.ID};
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

    public static List<Bson> filterForHostHostHeaderRaw() {
        List<Bson> filters = new ArrayList<>();
        filters.add(Filters.eq(SingleTypeInfo._RESPONSE_CODE, -1));
        filters.add(Filters.eq(SingleTypeInfo._IS_HEADER, true));
        filters.add(Filters.eq(SingleTypeInfo._PARAM, "host"));
        filters.add(Filters.eq(SingleTypeInfo.SUB_TYPE, SingleTypeInfo.GENERIC.getName()));

        return filters;
    }

    public static Bson filterForHostHeader(int apiCollectionId, boolean useApiCollectionId) {
        List<Bson> filters = filterForHostHostHeaderRaw();
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
                AktoDataType dt = SingleTypeInfo.getAktoDataTypeMap(Context.accountId.get()).get(subType.getName());
                if (dt != null && !dt.getActive()) {
                    continue;
                }
                sensitiveSubTypes.add(subType.getName());
            }
        }

        // Custom data type sensitive
        for (CustomDataType customDataType: SingleTypeInfo.getCustomDataTypeMap(Context.accountId.get()).values()) {
            if (customDataType.isSensitiveAlways() && customDataType.isActive()){
                sensitiveSubTypes.add(customDataType.getName());
            }
        }

        return sensitiveSubTypes;
    }

    public List<String> sensitiveSubTypeInRequestNames() {
        List<String> sensitiveInRequest = new ArrayList<>();
        for (SingleTypeInfo.SubType subType : SingleTypeInfo.subTypeMap.values()) {
            if (subType.isSensitiveAlways() ||
                    subType.getSensitivePosition().contains(SingleTypeInfo.Position.REQUEST_HEADER)
                    || subType.getSensitivePosition().contains(SingleTypeInfo.Position.REQUEST_PAYLOAD)) {

                AktoDataType dt = SingleTypeInfo.getAktoDataTypeMap(Context.accountId.get()).get(subType.getName());
                if (dt != null && !dt.getActive()) {
                    continue;
                }
                sensitiveInRequest.add(subType.getName());
            }
        }

        for (CustomDataType customDataType : SingleTypeInfo.getCustomDataTypeMap(Context.accountId.get()).values()) {
            if(!customDataType.isActive()){
                continue;
            }
            if (customDataType.isSensitiveAlways() ||
                    customDataType.getSensitivePosition().contains(SingleTypeInfo.Position.REQUEST_HEADER)
                    || customDataType.getSensitivePosition().contains(SingleTypeInfo.Position.REQUEST_PAYLOAD)) {
                sensitiveInRequest.add(customDataType.getName());
            }
        }
        return sensitiveInRequest;
    }

    public List<String> sensitiveSubTypeInResponseNames() {
        List<String> sensitiveInResponse = new ArrayList<>();
        for (SingleTypeInfo.SubType subType : SingleTypeInfo.subTypeMap.values()) {
            if (subType.isSensitiveAlways() ||
                    subType.getSensitivePosition().contains(SingleTypeInfo.Position.RESPONSE_HEADER) ||
                    subType.getSensitivePosition().contains(SingleTypeInfo.Position.RESPONSE_PAYLOAD)) {

                AktoDataType dt = SingleTypeInfo.getAktoDataTypeMap(Context.accountId.get()).get(subType.getName());
                if (dt != null && !dt.getActive()) {
                    continue;
                }

                sensitiveInResponse.add(subType.getName());
            }
        }
        for (CustomDataType customDataType : SingleTypeInfo.getCustomDataTypeMap(Context.accountId.get()).values()) {
            if(!customDataType.isActive()){
                continue;
            }
            if (customDataType.isSensitiveAlways() ||
                    customDataType.getSensitivePosition().contains(SingleTypeInfo.Position.RESPONSE_HEADER) ||
                    customDataType.getSensitivePosition().contains(SingleTypeInfo.Position.RESPONSE_PAYLOAD)) {
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

    public List<ApiInfo.ApiInfoKey> fetchEndpointsInCollection(Bson filter) {
        List<Bson> pipeline = getPipelineForEndpoints(filter);
        pipeline.add(Aggregates.limit(SingleTypeInfoDao.LARGE_LIMIT));
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
        pipeline.add(Aggregates.skip(skip));
        pipeline.add(Aggregates.limit(limit));
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
        pipeline.add(Aggregates.sort(Sorts.descending(SingleTypeInfo._TIMESTAMP)));
        pipeline.add(Aggregates.group(groupedId));
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

    public Map<String, Integer> execute(List<Bson> filterList) {
        Map<String, Integer> countMap = new HashMap<>();
        List<Bson> pipeline = new ArrayList<>();

        pipeline.add(Aggregates.match(Filters.and(filterList)));

        try {
            List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
            if(collectionIds != null) {
                pipeline.add(Aggregates.match(Filters.in(SingleTypeInfo._COLLECTION_IDS, collectionIds)));
            }
        } catch(Exception e){
        }

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

    public List<Bson> generateFilterForSubtypes(List<String> sensitiveParameters, BasicDBObject groupedId, Boolean inResponseOnly, Bson customFilter){
        List<Bson> pipeline = new ArrayList<>();

        List<String> sensitiveInRequest = SingleTypeInfoDao.instance.sensitiveSubTypeInRequestNames();
        sensitiveInRequest.addAll(SingleTypeInfoDao.instance.sensitiveSubTypeNames());
        Bson sensitiveSubTypeFilterRequest = Filters.in("subType",sensitiveInRequest);
        List<Bson> requestFilterList = new ArrayList<>();
        requestFilterList.add(sensitiveSubTypeFilterRequest);
        requestFilterList.add(customFilter);
        requestFilterList.add(Filters.eq("responseCode", -1));

        List<String> sensitiveInResponse = SingleTypeInfoDao.instance.sensitiveSubTypeInResponseNames();
        sensitiveInResponse.addAll(SingleTypeInfoDao.instance.sensitiveSubTypeNames());
        Bson sensitiveSubTypeFilterResponse = Filters.in("subType",sensitiveInResponse);
        List<Bson> responseFilterList = new ArrayList<>();
        responseFilterList.add(sensitiveSubTypeFilterResponse);
        responseFilterList.add(customFilter);
        responseFilterList.add(Filters.gt("responseCode", -1));

        pipeline.add(Aggregates.match(Filters.or(
            Filters.and(responseFilterList),
            Filters.and(requestFilterList)
        )));

        try {
            List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
            if(collectionIds != null) {
                pipeline.add(Aggregates.match(Filters.in(SingleTypeInfo._COLLECTION_IDS, collectionIds)));
            }
        } catch(Exception e){
        }

        pipeline.add(Aggregates.group(groupedId,Accumulators.addToSet("subTypes", "$subType")));
        /*
         * without the project stage, 
         * the number of documents and the count of documents using aggregate do not match.
         */
        pipeline.add(Aggregates.project(Projections.fields(Projections.include("_id", "subTypes"))));
        return pipeline;
    }

    public Map<Integer,List<String>> getSensitiveSubtypesDetectedForCollection(List<String> sensitiveParameters){
        BasicDBObject groupedId = new BasicDBObject(SingleTypeInfo._API_COLLECTION_ID, "$apiCollectionId");
        List<Bson> pipeline = generateFilterForSubtypes(sensitiveParameters, groupedId, false, Filters.empty());
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
    
    public List<BasicDBObject> getSensitiveSubtypesDetectedForUrl(List<String> sensitiveParameters) {
        BasicDBObject groupedId = new BasicDBObject(SingleTypeInfo._API_COLLECTION_ID, "$apiCollectionId")
                .append(SingleTypeInfo._URL, "$url")
                .append(SingleTypeInfo._METHOD, "$method");
        List<Bson> pipeline = generateFilterForSubtypes(sensitiveParameters, groupedId, false, Filters.empty());
        MongoCursor<BasicDBObject> collectionsCursor = SingleTypeInfoDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
        
        List<ApiInfo.ApiInfoKey> apiInfoKeys = new ArrayList<>();
        List<BasicDBObject> aggregationResults = new ArrayList<>();
        
        while (collectionsCursor.hasNext()) {
            try {
                BasicDBObject basicDBObject = collectionsCursor.next();
                BasicDBObject id = (BasicDBObject) basicDBObject.get("_id");
                int apiCollectionId = id.getInt("apiCollectionId");
                String url = id.getString("url");
                String method = id.getString("method");
                List<String> subtypes = (List<String>) basicDBObject.get("subTypes");
                
                if (subtypes != null && subtypes.size() > 1) {
                    ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(apiCollectionId, url, URLMethods.Method.fromString(method));
                    apiInfoKeys.add(apiInfoKey);
                    aggregationResults.add(basicDBObject);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        Map<ApiInfo.ApiInfoKey, ApiInfo> apiInfoMap = new HashMap<>();
        if (!apiInfoKeys.isEmpty()) {
            List<Bson> filters = apiInfoKeys.stream()
                    .map(ApiInfoDao::getFilter)
                    .collect(Collectors.toList());
            List<ApiInfo> apiInfos = ApiInfoDao.instance.findAll(Filters.or(filters));
            apiInfoMap = apiInfos.stream()
                    .collect(Collectors.toMap(ApiInfo::getId, apiInfo -> apiInfo));
        }
        
        List<BasicDBObject> records = new ArrayList<>();
        for (int i = 0; i < aggregationResults.size(); i++) {
            BasicDBObject basicDBObject = aggregationResults.get(i);
            ApiInfo.ApiInfoKey apiInfoKey = apiInfoKeys.get(i);
            List<String> subtypes = (List<String>) basicDBObject.get("subTypes");
            
            ApiInfo apiInfo = apiInfoMap.get(apiInfoKey);
            if (apiInfo == null) {
                apiInfo = new ApiInfo();
                apiInfo.setId(apiInfoKey);
            }
            
            BasicDBObject rec = new BasicDBObject();
            rec.put("apiInfo", apiInfo);
            rec.put("subTypes", subtypes);
            records.add(rec);
        }
        
        // Sort and limit to top 3 by subtypes count
        return records.stream()
                .sorted((a, b) -> Integer.compare(((List)b.get("subTypes")).size(), ((List)a.get("subTypes")).size()))
                .limit(3)
                .collect(Collectors.toList());
    }

    public Integer getSensitiveApisCount(List<String> sensitiveParameters, boolean inResponseOnly, Bson customFilter){
       
        BasicDBObject groupedId = new BasicDBObject(SingleTypeInfo._API_COLLECTION_ID, "$apiCollectionId")
                                        .append(SingleTypeInfo._URL, "$url")
                                        .append(SingleTypeInfo._METHOD, "$method");
        List<Bson> pipeline = generateFilterForSubtypes(sensitiveParameters, groupedId, inResponseOnly, customFilter);
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

    public List<Bson> createPipelineForFetchParams(List<ApiInfo.ApiInfoKey> apiInfoKeys, boolean isRequest, boolean shouldApplyResponseCodeFilter){
        List<Bson> pipeline = new ArrayList<>();

        List<Bson> filters = new ArrayList<>();
        Bson responseCodeFilter = Filters.gte(SingleTypeInfo._RESPONSE_CODE, -1);
        if(shouldApplyResponseCodeFilter){
            responseCodeFilter = isRequest ? Filters.eq(SingleTypeInfo._RESPONSE_CODE, -1) : Filters.gt(SingleTypeInfo._RESPONSE_CODE, -1);
        } 
        for (ApiInfo.ApiInfoKey apiInfoKey: apiInfoKeys) {
            filters.add(
                    Filters.and(
                            Filters.eq(SingleTypeInfo._API_COLLECTION_ID, apiInfoKey.getApiCollectionId()),
                            Filters.eq(SingleTypeInfo._URL, apiInfoKey.getUrl()),
                            Filters.eq(SingleTypeInfo._METHOD, apiInfoKey.getMethod().name()),
                            responseCodeFilter,
                            Filters.eq(SingleTypeInfo._IS_HEADER, false)
                    )
            );
        }

        pipeline.add(Aggregates.match(Filters.or(filters)));

        try {
            List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
            if(collectionIds != null) {
                pipeline.add(Aggregates.match(Filters.in(SingleTypeInfo._COLLECTION_IDS, collectionIds)));
            }
        } catch(Exception e){
        }

        BasicDBObject groupedId = new BasicDBObject(SingleTypeInfo._API_COLLECTION_ID, "$" + SingleTypeInfo._API_COLLECTION_ID)
                        .append(SingleTypeInfo._URL, "$" + SingleTypeInfo._URL)
                        .append(SingleTypeInfo._METHOD, "$" + SingleTypeInfo._METHOD);


        Bson projections = Projections.fields(
                Projections.include( SingleTypeInfo._API_COLLECTION_ID, SingleTypeInfo._URL, SingleTypeInfo._METHOD, SingleTypeInfo._PARAM, SingleTypeInfo._RESPONSE_CODE)
        );

        pipeline.add(Aggregates.project(projections));
        if(!shouldApplyResponseCodeFilter){
            groupedId.append(SingleTypeInfo._RESPONSE_CODE, "$" + SingleTypeInfo._RESPONSE_CODE);
        }
        pipeline.add(Aggregates.group(groupedId,Accumulators.addToSet("params", "$param")));
        
        return pipeline;

    }

    public Map<ApiInfo.ApiInfoKey, List<String>> fetchRequestParameters(List<ApiInfo.ApiInfoKey> apiInfoKeys) {
        Map<ApiInfo.ApiInfoKey, List<String>> result = new HashMap<>();
        if (apiInfoKeys == null || apiInfoKeys.isEmpty()) return result;

        List<Bson> pipeline = createPipelineForFetchParams(apiInfoKeys, true, true);

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
        try {
            List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
            if(collectionIds != null) {
                pipeline.add(Aggregates.match(Filters.in(SingleTypeInfo._COLLECTION_IDS, collectionIds)));
            }
        } catch(Exception e){
        }
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

    @Override
    public String getFilterKeyString() {
        return SingleTypeInfo._API_COLLECTION_ID;
    }

    public List<String> sensitiveApisList(List<String> sensitiveSubtypes,Bson customFilter, int limit){
        List<String> finalArrList = new ArrayList<>();
        BasicDBObject groupedId = new BasicDBObject(SingleTypeInfo._API_COLLECTION_ID, "$apiCollectionId")
                                    .append(SingleTypeInfo._URL, "$url")
                                    .append(SingleTypeInfo._METHOD, "$method");
        List<Bson> pipeline = SingleTypeInfoDao.instance.generateFilterForSubtypes(sensitiveSubtypes, groupedId, false, customFilter);
        pipeline.add(Aggregates.limit(limit));
        MongoCursor<BasicDBObject> cursor = SingleTypeInfoDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();

        while(cursor.hasNext()){
            BasicDBObject bdObject = cursor.next();
            BasicDBObject id = (BasicDBObject) bdObject.get("_id");

            String url = id.getString("method") + " " + id.getString("url");
            finalArrList.add(url);
        }

        return finalArrList;
    }

    public long fetchEndpointsCount(int startTimestamp, int endTimestamp, Set<Integer> deactivatedCollections) {
        return fetchEndpointsCount(startTimestamp, endTimestamp, deactivatedCollections, true);
    }

    public long fetchEndpointsCount(int startTimestamp, int endTimestamp, Set<Integer> deactivatedCollections, boolean useRbacUserCollections) {
        List <Integer> nonHostApiCollectionIds = ApiCollectionsDao.instance.fetchNonTrafficApiCollectionsIds();
        nonHostApiCollectionIds.addAll(deactivatedCollections);

        Bson hostFilterQ = SingleTypeInfoDao.filterForHostHeader(0, false);
        Bson userCollectionFilter = Filters.empty();
        if (useRbacUserCollections) {
            try {
                List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(),
                        Context.accountId.get());
                if (collectionIds != null) {
                    userCollectionFilter = Filters.in("collectionIds", collectionIds);
                }
            } catch (Exception e) {
            }
        }

        // OPTIMIZED APPROACH: Count all, then subtract excluded (total - excluded)
        // This is much faster than using $nin with large arrays

        // Query 1: Count ALL documents matching host filter + timestamp
        Bson filterAllWithTs = Filters.and(
                hostFilterQ,
                Filters.gte(SingleTypeInfo._TIMESTAMP, startTimestamp),
                Filters.lte(SingleTypeInfo._TIMESTAMP, endTimestamp),
                userCollectionFilter
        );

        long totalCount = 0;
        if (useRbacUserCollections) {
            totalCount = SingleTypeInfoDao.instance.count(filterAllWithTs);
        } else {
            totalCount = SingleTypeInfoDao.instance.getMCollection().countDocuments(filterAllWithTs);
        }

        // Query 2: Count EXCLUDED collections using $in (much faster than $nin)
        long excludedCount = 0;
        if (!nonHostApiCollectionIds.isEmpty()) {
            Bson filterExcludedWithTs = Filters.and(
                    hostFilterQ,
                    Filters.gte(SingleTypeInfo._TIMESTAMP, startTimestamp),
                    Filters.lte(SingleTypeInfo._TIMESTAMP, endTimestamp),
                    Filters.in(SingleTypeInfo._API_COLLECTION_ID, nonHostApiCollectionIds),
                    userCollectionFilter
            );

            if (useRbacUserCollections) {
                excludedCount = SingleTypeInfoDao.instance.count(filterExcludedWithTs);
            } else {
                excludedCount = SingleTypeInfoDao.instance.getMCollection().countDocuments(filterExcludedWithTs);
            }
        }

        long count = totalCount - excludedCount;

        nonHostApiCollectionIds.removeAll(deactivatedCollections);

        if (nonHostApiCollectionIds.size() > 0){
            List<Bson> pipeline = new ArrayList<>();

            pipeline.add(Aggregates.match(Filters.in("apiCollectionId", nonHostApiCollectionIds)));
            if (useRbacUserCollections) {
                try {
                    List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(),
                            Context.accountId.get());
                    if (collectionIds != null) {
                        pipeline.add(Aggregates.match(Filters.in(SingleTypeInfo._COLLECTION_IDS, collectionIds)));
                    }
                } catch (Exception e) {
                }
            }

            BasicDBObject groupedId = 
                new BasicDBObject("apiCollectionId", "$apiCollectionId")
                .append("url", "$url")
                .append("method", "$method");
            pipeline.add(Aggregates.group(groupedId, Accumulators.min("startTs", "$timestamp")));
            pipeline.add(Aggregates.match(Filters.gte("startTs", startTimestamp)));
            pipeline.add(Aggregates.match(Filters.lte("startTs", endTimestamp)));
            pipeline.add(Aggregates.sort(Sorts.descending("startTs")));
            pipeline.add(Aggregates.count());
            MongoCursor<BasicDBObject> endpointsCursor = SingleTypeInfoDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();

            while (endpointsCursor.hasNext()) {
                count += endpointsCursor.next().getInt(_COUNT);
                break;
            }
        }

        return count;
    }

    public Bson getFilterForHostApis(int startTimestamp, int endTimestamp, Set<Integer> deactivatedCollections, List <Integer> nonHostApiCollectionIds){

        Bson hostFilterQ = SingleTypeInfoDao.filterForHostHeader(0, false);
        nonHostApiCollectionIds.addAll(deactivatedCollections);
        Bson filterQWithTs = Filters.and(
                Filters.gte(SingleTypeInfo._TIMESTAMP, startTimestamp),
                Filters.lte(SingleTypeInfo._TIMESTAMP, endTimestamp),
                Filters.nin(SingleTypeInfo._API_COLLECTION_ID, nonHostApiCollectionIds),
                hostFilterQ
        );
        return filterQWithTs;
    }

    public List<Bson> buildPipelineForTrend(boolean isNotKubernetes){
        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.project(Projections.computed("dayOfYearFloat", new BasicDBObject("$divide", new Object[]{"$timestamp", 86400}))));
        Bson doyProj = Projections.computed("dayOfYear", new BasicDBObject("$divide", new Object[]{"$timestamp", 86400}));
        if (isNotKubernetes) {
            doyProj = Projections.computed("dayOfYear", new BasicDBObject("$floor", new Object[]{"$dayOfYearFloat"}));
        }
        pipeline.add(Aggregates.project(doyProj));
        pipeline.add(Aggregates.group("$dayOfYear", Accumulators.sum("count", 1)));

        return pipeline;
    }

    public List<BasicDBObject> fetchRecentEndpoints(int startTimestamp, int endTimestamp, Set<Integer> deactivatedCollections){
        List <Integer> nonHostApiCollectionIds = ApiCollectionsDao.instance.fetchNonTrafficApiCollectionsIds();
        nonHostApiCollectionIds.addAll(deactivatedCollections);
        List<BasicDBObject> endpoints = new ArrayList<>();
        Bson filterQWithTs = getFilterForHostApis(startTimestamp, endTimestamp, deactivatedCollections, nonHostApiCollectionIds);
        List<SingleTypeInfo> latestHosts = SingleTypeInfoDao.instance.findAll(filterQWithTs, 0, 20_000, Sorts.descending("timestamp"), Projections.exclude("values"));
        for(SingleTypeInfo sti: latestHosts) {
            BasicDBObject id = 
                new BasicDBObject("apiCollectionId", sti.getApiCollectionId())
                .append("url", sti.getUrl())
                .append("method", sti.getMethod());
            BasicDBObject endpoint = 
                new BasicDBObject("_id", id).append("startTs", sti.getTimestamp()).append("count", 1);
            endpoints.add(endpoint);
        }
        
        nonHostApiCollectionIds.removeAll(deactivatedCollections);

        if (nonHostApiCollectionIds != null && nonHostApiCollectionIds.size() > 0){
            List<Bson> pipeline = new ArrayList<>();

            pipeline.add(Aggregates.match(Filters.in("apiCollectionId", nonHostApiCollectionIds)));
            try {
                List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(),Context.accountId.get());
                if (collectionIds != null) {
                    pipeline.add(Aggregates.match(Filters.in(SingleTypeInfo._COLLECTION_IDS, collectionIds)));
                }
            } catch (Exception e) {
            }
            BasicDBObject groupedId = 
                new BasicDBObject("apiCollectionId", "$apiCollectionId")
                .append("url", "$url")
                .append("method", "$method");
            pipeline.add(Aggregates.group(groupedId, Accumulators.min("startTs", "$timestamp"),Accumulators.sum("countTs",1)));
            pipeline.add(Aggregates.match(Filters.gte("startTs", startTimestamp)));
            pipeline.add(Aggregates.match(Filters.lte("startTs", endTimestamp)));
            pipeline.add(Aggregates.sort(Sorts.descending("startTs")));
            MongoCursor<BasicDBObject> endpointsCursor = SingleTypeInfoDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
            while(endpointsCursor.hasNext()) {
                endpoints.add(endpointsCursor.next());
            }
        }
        return endpoints;
    }

    public static void deleteDuplicateHostsForSameApi(){
        List<ApiCollection> apiCollections = ApiCollectionsDao.instance.findAll(
            Filters.and(
                Filters.ne(ApiCollection._DEACTIVATED, true),
                Filters.exists(ApiCollection.HOST_NAME)
            ),
            Projections.include(Constants.ID)
        );
        for(ApiCollection apiCollection: apiCollections) {
            SingleTypeInfoDao.deleteDuplicateHostsForSameApi(apiCollection.getId());
        }
    }


    public static void deleteDuplicateHostsForSameApi(int apiCollectionId){
        List<SingleTypeInfo> allList = SingleTypeInfoDao.instance.findAll(filterForHostHeader(apiCollectionId, true), Projections.include(SingleTypeInfo._API_COLLECTION_ID, SingleTypeInfo._URL, SingleTypeInfo._METHOD));
        Set<String> uniqueKeys = new HashSet<>();
        List<ObjectId> deleteIds = new ArrayList<>();
        for (SingleTypeInfo sti : allList) {
            String key = sti.getApiCollectionId() + "_" + sti.getUrl() + "_" + sti.getMethod();
            if(uniqueKeys.contains(key)){
                deleteIds.add(sti.getId());
            } else {
                uniqueKeys.add(key);
            }
        }
        SingleTypeInfoDao.instance.getMCollection().deleteMany(Filters.in("_id", deleteIds));
    }


    public static BasicDBObject getApiInfoGroupedId() {
        BasicDBObject groupedId = 
                new BasicDBObject("apiCollectionId", "$apiCollectionId")
                .append("url", "$url")
                .append("method", "$method");
        return groupedId;
    }
}

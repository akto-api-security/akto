package com.akto.dao;

import java.util.*;

import com.akto.DaoInit;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.CustomDataType;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.SensitiveParamInfo;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.*;

import org.bson.Document;
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
        
        MongoCursor<Document> cursor = instance.getMCollection().listIndexes().cursor();
        int counter = 0;
        while (cursor.hasNext()) {
            counter++;
            cursor.next();
        }

        if (counter == 1) {
            String[] fieldNames = {"url", "method", "responseCode", "isHeader", "param", "subType", "apiCollectionId"};
            SingleTypeInfoDao.instance.getMCollection().createIndex(Indexes.ascending(fieldNames));    
        }

        if (counter == 2) {
            SingleTypeInfoDao.instance.getMCollection().createIndex(Indexes.ascending(new String[]{"apiCollectionId"}));
            counter++;
        }

        if (counter == 3) {
            SingleTypeInfoDao.instance.getMCollection().createIndex(Indexes.ascending(new String[]{"param", "apiCollectionId"}));
            counter++;
        }

        if (counter == 4) {
            SingleTypeInfoDao.instance.getMCollection().createIndex(Indexes.ascending(new String[]{SingleTypeInfo._RESPONSE_CODE, SingleTypeInfo._IS_HEADER, SingleTypeInfo._PARAM, SingleTypeInfo.SUB_TYPE, SingleTypeInfo._API_COLLECTION_ID}));
            counter++;
        }

        if (counter == 5) {
            SingleTypeInfoDao.instance.getMCollection().createIndex(Indexes.ascending(new String[]{SingleTypeInfo.SUB_TYPE, SingleTypeInfo._RESPONSE_CODE}));
            counter++;
        }

        if (counter == 6) {
            String[] fieldNames = {"responseCode", "subType", "timestamp",};
            SingleTypeInfoDao.instance.getMCollection().createIndex(Indexes.ascending(fieldNames));    
        }
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

    public List<SingleTypeInfo> fetchAll() {
        return this.findAll(new BasicDBObject());
    }

    public static Bson createFiltersWithoutSubType(SingleTypeInfo info) {
        List<Bson> filters = createFiltersBasic(info);
        return Filters.and(filters);
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
        for (CustomDataType customDataType: SingleTypeInfo.customDataTypeMap.values()) {
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

        for (CustomDataType customDataType: SingleTypeInfo.customDataTypeMap.values()) {
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
        for (CustomDataType customDataType: SingleTypeInfo.customDataTypeMap.values()) {
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
            filters.add(Filters.eq("apiCollectionId", apiCollectionId) );
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
        List<Bson> pipeline = new ArrayList<>();
        BasicDBObject groupedId =
                new BasicDBObject("apiCollectionId", "$apiCollectionId")
                        .append("url", "$url")
                        .append("method", "$method");

        if (apiCollectionId != -1) {
            pipeline.add(Aggregates.match(Filters.eq("apiCollectionId", apiCollectionId)));
        }

        Bson projections = Projections.fields(
                Projections.include("timestamp", "apiCollectionId", "url", "method")
        );

        pipeline.add(Aggregates.project(projections));
        pipeline.add(Aggregates.group(groupedId));
        pipeline.add(Aggregates.sort(Sorts.descending("startTs")));

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
}

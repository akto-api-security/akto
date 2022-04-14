package com.akto.dao;

import java.util.*;

import com.akto.dao.context.Context;
import com.akto.dto.CustomDataType;
import com.akto.dto.SensitiveParamInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;

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
    }

    public List<SingleTypeInfo> fetchAll() {
        return this.findAll(new BasicDBObject());
    }

    public static Bson createFilters(SingleTypeInfo info) {
        return Filters.and(
                Filters.eq("url", info.getUrl()),
                Filters.eq("method", info.getMethod()),
                Filters.eq("responseCode", info.getResponseCode()),
                Filters.eq("isHeader", info.getIsHeader()),
                Filters.eq("param", info.getParam()),
                Filters.eq("subType", info.getSubType().getName()),
                Filters.eq("apiCollectionId", info.getApiCollectionId())
        );
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

    public Bson filterForSensitiveParamsExcludingUserMarkedSensitive(Integer apiCollectionId, String url, String method) {
        // apiCollectionId null then no filter for apiCollectionId
        List<String> sensitiveSubTypes = sensitiveSubTypeNames();

        Bson alwaysSensitiveFilter = Filters.in("subType", sensitiveSubTypes);

        Bson sensitivityBasedOnPosition = Filters.and(
                Filters.in("subType", Arrays.asList(SingleTypeInfo.JWT.getName(), SingleTypeInfo.URL.getName(), SingleTypeInfo.IP_ADDRESS.getName())),
                Filters.gt("responseCode", -1)
        );

        List<Bson> filters = new ArrayList<>();
        filters.add(Filters.or(alwaysSensitiveFilter, sensitivityBasedOnPosition));

        if (apiCollectionId != null && apiCollectionId >= 0) {
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

        Bson filter = filterForSensitiveParamsExcludingUserMarkedSensitive(apiCollectionId, url, method);

        urls.addAll(instance.findDistinctFields("url", String.class, filter));

        return urls;
    }
    
}

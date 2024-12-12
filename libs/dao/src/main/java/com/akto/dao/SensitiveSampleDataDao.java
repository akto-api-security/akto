package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.ApiCollectionUsers;
import com.akto.dto.ApiInfo;
import com.akto.dto.SensitiveSampleData;
import com.akto.dto.testing.TestingEndpoints;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;

import java.util.HashMap;
import java.util.Map;

import org.bson.Document;

import org.bson.conversions.Bson;

public class SensitiveSampleDataDao extends AccountsContextDaoWithRbac<SensitiveSampleData>{

    public static final SensitiveSampleDataDao instance = new SensitiveSampleDataDao();
    @Override
    public String getCollName() {
        return "sensitive_sample_data";
    }

    @Override
    public Class<SensitiveSampleData> getClassT() {
        return SensitiveSampleData.class;
    }

    public static Bson getFilters(SingleTypeInfo singleTypeInfo) {
        return Filters.and(
                Filters.eq("_id.url", singleTypeInfo.getUrl()),
                Filters.eq("_id.method", singleTypeInfo.getMethod()),
                Filters.eq("_id.responseCode", singleTypeInfo.getResponseCode()),
                Filters.eq("_id.isHeader", singleTypeInfo.getIsHeader()),
                Filters.eq("_id.param", singleTypeInfo.getParam()),
                Filters.eq("_id.subType", singleTypeInfo.getSubType().getName()),
                Filters.eq("_id.apiCollectionId", singleTypeInfo.getApiCollectionId())
        );
    }

    public static Map<String, Object> getFiltersMap(SingleTypeInfo singleTypeInfo) {
        Map<String, Object> filterMap = new HashMap<>();

        filterMap.put("_id.url", singleTypeInfo.getUrl());
        filterMap.put("_id.method", singleTypeInfo.getMethod());
        filterMap.put("_id.responseCode", singleTypeInfo.getResponseCode());
        filterMap.put("_id.isHeader", singleTypeInfo.getIsHeader());
        filterMap.put("_id.param", singleTypeInfo.getParam());
        filterMap.put("_id.subType", singleTypeInfo.getSubType().getName());
        filterMap.put("_id.apiCollectionId", singleTypeInfo.getApiCollectionId());
        return filterMap;
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

        String[] fieldNames = {"_id.url", "_id.apiCollectionId", "_id.method"};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] { ApiInfo.ID_URL, SingleTypeInfo._COLLECTION_IDS, ApiInfo.ID_METHOD }, true);

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] { SingleTypeInfo._COLLECTION_IDS }, true);
    }

    @Override
    public String getFilterKeyString() {
        return TestingEndpoints.getFilterPrefix(ApiCollectionUsers.CollectionType.Id_ApiCollectionId) + ApiInfo.ApiInfoKey.API_COLLECTION_ID;
    }
}

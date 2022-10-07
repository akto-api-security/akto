package com.akto.dao;

import com.akto.dto.ApiCollection;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

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

    public Map<Integer, ApiCollection> generateApiCollectionMap() {
        Map<Integer, ApiCollection> apiCollectionMap = new HashMap<>();
        List<ApiCollection> apiCollections = ApiCollectionsDao.instance.findAll(new BasicDBObject());
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
}

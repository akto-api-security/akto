package com.akto.dto.CollectionConditions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.conversions.Bson;

import com.akto.dto.ApiCollectionUsers.CollectionType;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

public class ApiListCondition extends CollectionCondition{

    Set<ApiInfoKey> apiList;

    public Set<ApiInfoKey> getApiList() {
        return apiList;
    }

    public void setApiList(Set<ApiInfoKey> apiList) {
        this.apiList = apiList;
    }

    public ApiListCondition() {
        super(Type.API_LIST);
    }

    public ApiListCondition(Set<ApiInfoKey> apiList) {
        super(Type.API_LIST);
        this.apiList = apiList;
    }

    @Override
    public Set<ApiInfoKey> returnApis() {
        return apiList;
    }

    private Bson createApiFilters(CollectionType type, ApiInfoKey api) {

        String prefix = "";
        switch (type) {
            case Id_ApiCollectionId:
                prefix = "_id.";
                break;

            case Id_ApiInfoKey_ApiCollectionId:
                prefix = "_id.apiInfoKey.";
                break;

            case ApiCollectionId:
            default:
                break;
        }

        return Filters.and(
                Filters.in(SingleTypeInfo._COLLECTION_IDS, api.getApiCollectionId()),
                Filters.eq(prefix + SingleTypeInfo._METHOD, api.getMethod().toString()),
                Filters.eq(prefix + SingleTypeInfo._URL, api.getUrl()));

    }

    @Override
    public Map<CollectionType, Bson> returnFiltersMap(){
        Map<CollectionType, Bson> filtersMap = new HashMap<>();
        filtersMap.put(CollectionType.ApiCollectionId, new BasicDBObject());
        filtersMap.put(CollectionType.Id_ApiCollectionId, new BasicDBObject());
        filtersMap.put(CollectionType.Id_ApiInfoKey_ApiCollectionId, new BasicDBObject());

        for(Map.Entry<CollectionType, Bson> filters: filtersMap.entrySet()){
            List<Bson> apiFilters = new ArrayList<>();
            CollectionType type = filters.getKey();
            for (ApiInfoKey api : this.apiList) {
                apiFilters.add(createApiFilters(type, api));
            }
            filters.setValue(Filters.or(apiFilters));
        }
        return filtersMap;
    }

}

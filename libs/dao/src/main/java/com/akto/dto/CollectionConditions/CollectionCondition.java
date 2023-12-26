package com.akto.dto.CollectionConditions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bson.conversions.Bson;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.ApiCollectionUsers.CollectionType;
import com.akto.dto.testing.TestingEndpoints;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

public abstract class CollectionCondition extends TestingEndpoints {
    private Operator operator;

    public CollectionCondition(Type type, Operator operator) {
        super(type);
        this.operator = operator;
    }

    public enum Operator {
        AND, OR
    }

    public Operator getOperator() {
        return operator;
    }

    public void setOperator(Operator operator) {
        this.operator = operator;
    }

    private static Bson createApiFilters(CollectionType type, ApiInfoKey api) {

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
            Filters.eq(prefix + SingleTypeInfo._URL, api.getUrl()),
            Filters.eq(prefix + SingleTypeInfo._METHOD, api.getMethod().toString()),
            Filters.in(SingleTypeInfo._COLLECTION_IDS, api.getApiCollectionId()));

    }

    private static Map<CollectionType, Bson> createFiltersMapWithApiList(List<ApiInfoKey> apiList){
        Set<ApiInfoKey> apiSet = new HashSet<>(apiList);
        Map<CollectionType, Bson> filtersMap = new HashMap<>();
        filtersMap.put(CollectionType.ApiCollectionId, new BasicDBObject());
        filtersMap.put(CollectionType.Id_ApiCollectionId, new BasicDBObject());
        filtersMap.put(CollectionType.Id_ApiInfoKey_ApiCollectionId, new BasicDBObject());

        for(Map.Entry<CollectionType, Bson> filters: filtersMap.entrySet()){
            List<Bson> apiFilters = new ArrayList<>();
            CollectionType type = filters.getKey();
            if(apiSet != null && !apiSet.isEmpty()){
                for (ApiInfoKey api : apiSet) {
                    apiFilters.add(createApiFilters(type, api));
                }
                filters.setValue(Filters.or(apiFilters));
            } else {
                filters.setValue(Filters.nor(new BasicDBObject()));
            }
        }
        return filtersMap;
    }

    public Map<CollectionType, Bson> returnFiltersMap() {
        return createFiltersMapWithApiList(returnApis());
    };

}

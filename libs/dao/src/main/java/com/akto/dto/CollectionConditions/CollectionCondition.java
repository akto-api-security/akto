package com.akto.dto.CollectionConditions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bson.conversions.Bson;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods.Method;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.akto.dto.ApiCollectionUsers.CollectionType;

public abstract class CollectionCondition {
    private Type type;
    private Operator operator;

    public CollectionCondition(Type type, Operator operator) {
        this.type = type;
        this.operator = operator;
    }

    public abstract Set<ApiInfo.ApiInfoKey> returnApis();
    public abstract Map<CollectionType, Bson> returnFiltersMap();

    public enum Type {
        API_LIST, METHOD, PARAM, TIMESTAMP
    }

    public enum Operator {
        AND, OR
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public Operator getOperator() {
        return operator;
    }

    public void setOperator(Operator operator) {
        this.operator = operator;
    }

    public static CollectionCondition generateCondition(Type type, Operator operator, BasicDBObject data) {
        CollectionCondition condition = null;

        try {
            switch (type) {
                case API_LIST:
                    List<HashMap> list = (List<HashMap>) data.get("apiList");
                    List<ApiInfoKey> apiList = new ArrayList<>();
                    for (HashMap api : list) {
                        apiList.add(new ApiInfoKey(
                                ((Long) api.get("apiCollectionId")).intValue(),
                                (String) api.get("url"),
                                Method.valueOf((String) api.get("method"))));
                    }
                    condition = new ApiListCondition(new HashSet<ApiInfoKey>(apiList), operator);
                    break;
                case METHOD:
                    condition = new MethodCondition(operator, Method.valueOf(data.getString("method")));
                    break;
                case PARAM:
                    condition = new ParamCondition(operator,
                            data.getBoolean("isHeader"),
                            data.getBoolean("isRequest"),
                            data.getString("param"),
                            data.getString("value"));
                    break;
                case TIMESTAMP:
                    condition = new TimestampCondition(operator,
                            data.getString("key"),
                            data.getInt("endTimestamp"),
                            data.getInt("startTimestamp"),
                            data.getInt("periodInSeconds"));
                    break;
                default:
                    break;
            }
        } catch (Exception e) {

        }

        return condition;
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

    public static Map<CollectionType, Bson> createFiltersMapWithApiList(Set<ApiInfoKey> apiList){
        Map<CollectionType, Bson> filtersMap = new HashMap<>();
        filtersMap.put(CollectionType.ApiCollectionId, new BasicDBObject());
        filtersMap.put(CollectionType.Id_ApiCollectionId, new BasicDBObject());
        filtersMap.put(CollectionType.Id_ApiInfoKey_ApiCollectionId, new BasicDBObject());

        for(Map.Entry<CollectionType, Bson> filters: filtersMap.entrySet()){
            List<Bson> apiFilters = new ArrayList<>();
            CollectionType type = filters.getKey();
            if(apiList != null && !apiList.isEmpty()){
                for (ApiInfoKey api : apiList) {
                    apiFilters.add(createApiFilters(type, api));
                }
                filters.setValue(Filters.or(apiFilters));
            } else {
                filters.setValue(Filters.nor(new BasicDBObject()));
            }
        }
        return filtersMap;
    }

}

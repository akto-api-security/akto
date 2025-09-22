package com.akto.dto.testing;

import com.akto.dto.ApiCollectionUsers.CollectionType;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.CollectionConditions.MethodCondition;
import com.akto.dto.testing.TagsTestingEndpoints;
import com.akto.dto.type.URLMethods.Method;
import com.mongodb.BasicDBObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.conversions.Bson;

public abstract class TestingEndpoints {
    private Type type;
    private Operator operator;

    public TestingEndpoints(Type type) {
        this.type = type;
        this.operator = Operator.AND;
    }

    public TestingEndpoints(Type type, Operator operator) {
        this(type);
        this.operator = operator;
    }

    public enum Operator {
        AND, OR
    }

    public abstract List<ApiInfo.ApiInfoKey> returnApis();

    public abstract boolean containsApi (ApiInfo.ApiInfoKey key);


    public enum Type {
        CUSTOM, COLLECTION_WISE, WORKFLOW, LOGICAL_GROUP, METHOD, ALL, REGEX, RISK_SCORE, SENSITIVE_DATA, UNAUTHENTICATED, HOST_REGEX, TAGS
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

    public static TestingEndpoints generateCondition(Type type, Operator operator, BasicDBObject data) {
        TestingEndpoints condition = null;

        try {
            switch (type) {
                case CUSTOM:
                    List<HashMap> list = (List<HashMap>) data.get("apiList");
                    List<ApiInfoKey> apiList = new ArrayList<>();
                    for (HashMap api : list) {
                        apiList.add(new ApiInfoKey(
                                ((Long) api.get("apiCollectionId")).intValue(),
                                (String) api.get("url"),
                                Method.valueOf((String) api.get("method"))));
                    }
                    condition = new CustomTestingEndpoints(apiList, operator);
                    break;
                case METHOD:
                    condition = new MethodCondition(operator, Method.valueOf(data.getString("method")));
                    break;
                case REGEX:
                    condition = new RegexTestingEndpoints(operator, data.getString("regex"));
                    break;
                case HOST_REGEX:
                    condition = new HostRegexTestingEndpoints(operator, data.getString("host_regex"));
                    break;
                case TAGS:
                    String q = (data != null) ? data.getString("query") : null;
                    condition = new TagsTestingEndpoints(operator, q);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {

        }

        return condition;
    }

    public static String getFilterPrefix(CollectionType type) {
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
        return prefix;
    }

    public abstract Bson createFilters(CollectionType type);

    public Map<CollectionType, Bson> returnFiltersMap() {
        Map<CollectionType, Bson> filtersMap = new HashMap<>();
        filtersMap.put(CollectionType.ApiCollectionId, new BasicDBObject());
        filtersMap.put(CollectionType.Id_ApiCollectionId, new BasicDBObject());
        filtersMap.put(CollectionType.Id_ApiInfoKey_ApiCollectionId, new BasicDBObject());

        for (Map.Entry<CollectionType, Bson> filters : filtersMap.entrySet()) {
            CollectionType type = filters.getKey();
            Bson filter = createFilters(type);
            filters.setValue(filter);
        }
        return filtersMap;
    }    

    public static Boolean checkDeltaUpdateBased(Type type) {
        switch (type) {
            case RISK_SCORE:
                return true;
            default:
                return false;
        }
    }

    
}

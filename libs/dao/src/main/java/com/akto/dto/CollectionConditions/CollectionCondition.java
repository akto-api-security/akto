package com.akto.dto.CollectionConditions;

import java.util.HashMap;
import java.util.Map;
import org.bson.conversions.Bson;
import com.akto.dto.ApiCollectionUsers.CollectionType;
import com.akto.dto.testing.TestingEndpoints;
import com.mongodb.BasicDBObject;

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

    protected static String getFilterPrefix(CollectionType type) {
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

}

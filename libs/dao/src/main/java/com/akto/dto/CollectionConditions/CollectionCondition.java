package com.akto.dto.CollectionConditions;

import java.util.Map;
import java.util.Set;
import org.bson.conversions.Bson;
import com.akto.dto.ApiInfo;
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
        API_LIST
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

}

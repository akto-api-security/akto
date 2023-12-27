package com.akto.dto.CollectionConditions;

import com.mongodb.BasicDBObject;

public class ConditionUtils {

    private CollectionCondition.Type type;
    private CollectionCondition.Operator operator;
    private BasicDBObject data;

    public BasicDBObject getData() {
        return data;
    }

    public void setData(BasicDBObject data) {
        this.data = data;
    }

    public ConditionUtils() {
    }

    public CollectionCondition.Type getType() {
        return type;
    }

    public void setType(CollectionCondition.Type type) {
        this.type = type;
    }

    public CollectionCondition.Operator getOperator() {
        return operator;
    }

    public void setOperator(CollectionCondition.Operator operator) {
        this.operator = operator;
    }

}
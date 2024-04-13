package com.akto.dto.CollectionConditions;

import com.akto.dto.testing.TestingEndpoints;
import com.mongodb.BasicDBObject;

public class ConditionUtils {

    private TestingEndpoints.Type type;
    private TestingEndpoints.Operator operator;
    private BasicDBObject data;

    public BasicDBObject getData() {
        return data;
    }

    public void setData(BasicDBObject data) {
        this.data = data;
    }

    public ConditionUtils() {
    }

    public TestingEndpoints.Type getType() {
        return type;
    }

    public void setType(TestingEndpoints.Type type) {
        this.type = type;
    }

    public TestingEndpoints.Operator getOperator() {
        return operator;
    }

    public void setOperator(TestingEndpoints.Operator operator) {
        this.operator = operator;
    }

}
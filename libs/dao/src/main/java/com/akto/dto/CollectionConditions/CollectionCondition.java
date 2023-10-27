package com.akto.dto.CollectionConditions;

import java.util.Set;

import com.akto.dto.ApiInfo;

public abstract class CollectionCondition {
    private Type type;

    public CollectionCondition(Type type) {
        this.type = type;
    }

    public abstract Set<ApiInfo.ApiInfoKey> returnApis();

    public enum Type {
        API_LIST
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

}

package com.akto.dto.testing;

import com.akto.dto.ApiInfo;

import java.util.List;

public abstract class TestingEndpoints {
    private Type type;

    public TestingEndpoints(Type type) {
        this.type = type;
    }

    public abstract List<ApiInfo.ApiInfoKey> returnApis();


    public enum Type {
        CUSTOM, COLLECTION_WISE
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }
}

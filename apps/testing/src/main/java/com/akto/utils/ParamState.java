package com.akto.utils;

import com.akto.dto.ApiInfo;
import com.akto.dto.type.ParamTypeInfo;

import java.util.Map;

public class ParamState {

    public enum State {
        PUBLIC, PRIVATE, NA
    }

    public static State findState(Map<String, ParamTypeInfo> paramTypeInfoMap, String param,  boolean isUrlParam,
                                  ApiInfo.ApiInfoKey apiInfoKey, boolean isHeader) {

        String key = new ParamTypeInfo(
                apiInfoKey.getApiCollectionId(), apiInfoKey.url, apiInfoKey.method.name(), -1, isHeader,
                isUrlParam, param).composeKey();

        ParamTypeInfo paramTypeInfo = paramTypeInfoMap.get(key);
        if (paramTypeInfo == null) return State.NA;

        long publicCount = paramTypeInfo.getPublicCount();
        long uniqueCount = paramTypeInfo.getUniqueCount();

        double v = (1.0*publicCount) / uniqueCount;
        if (v <= 0.1) {
            return State.PRIVATE;
        } else {
            return State.PUBLIC;
        }

    }
}

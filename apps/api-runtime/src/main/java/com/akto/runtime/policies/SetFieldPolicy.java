package com.akto.runtime.policies;

import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.runtime_filters.RuntimeFilter;

import java.util.HashMap;
import java.util.Map;

public class SetFieldPolicy {
    public static boolean setField(HttpResponseParams httpResponseParams, ApiInfo apiInfo, RuntimeFilter filter) {
        Map<String, Integer> result = apiInfo.getViolations();
        if (result == null) result = new HashMap<>();
        String fieldName = filter.getCustomFieldName();
        result.put(fieldName, Context.now());
        apiInfo.setViolations(result);
        return true;
    }
}

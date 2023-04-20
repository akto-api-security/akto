package com.akto.dao.test_editor.executor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.akto.dao.test_editor.Utils;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.ExecutionResult;
import com.mongodb.BasicDBObject;

public class Operations {
    
    public static ExecutionResult addHeader(RawApi rawApi, String key, String value) {
        Map<String, List<String>> headers = rawApi.fetchReqHeaders();
        List<String> valList = Collections.singletonList(value);
        headers.put(key, valList);
        rawApi.modifyReqHeaders(headers);
        return new ExecutionResult(true, "");
    }

    public static ExecutionResult deleteHeader(RawApi rawApi, String key) {
        Map<String, List<String>> headers = rawApi.fetchReqHeaders();
        if (headers.containsKey(key)) {
            headers.remove(key);
        } else {
            return new ExecutionResult(false, "header key not present " + key);
        }
        rawApi.modifyReqHeaders(headers);
        return new ExecutionResult(true, "");
    }

    public static ExecutionResult modifyHeader(RawApi rawApi, String key, String value) {
        Map<String, List<String>> headers = rawApi.fetchReqHeaders();
        if (!headers.containsKey(key)) {
            return new ExecutionResult(false, "header key not present " + key);
        }
        List<String> valList = Collections.singletonList(value);
        headers.put(key, valList);
        rawApi.modifyReqHeaders(headers);
        return new ExecutionResult(true, "");
    }

    public static ExecutionResult addQueryParam(RawApi rawApi, String key, Object value) {
        BasicDBObject queryParamObj = rawApi.fetchQueryParam();
        queryParamObj.put(key, value);
        rawApi.modifyQueryParam(queryParamObj);
        return new ExecutionResult(true, "");
    }

    public static ExecutionResult deleteQueryParam(RawApi rawApi, String key) {
        BasicDBObject queryParamObj = rawApi.fetchQueryParam();
        if (queryParamObj.containsKey(key)) {
            queryParamObj.remove(key);
        } else {
            return new ExecutionResult(false, "query param key not present " + key);
        }
        rawApi.modifyQueryParam(queryParamObj);
        return new ExecutionResult(true, "");
    }

    public static ExecutionResult modifyQueryParam(RawApi rawApi, String key, Object value) {
        BasicDBObject queryParamObj = rawApi.fetchQueryParam();
        if (!queryParamObj.containsKey(key)) {
            return new ExecutionResult(false, "query param key not present " + key);
        }
        queryParamObj.put(key, value);
        rawApi.modifyQueryParam(queryParamObj);
        return new ExecutionResult(true, "");
    }

    public static ExecutionResult addBody(RawApi rawApi, String key, Object value) {
        BasicDBObject payload = rawApi.fetchReqPayload();
        payload.put(key, value);
        rawApi.modifyReqPayload(payload);
        return new ExecutionResult(true, "");
    }

    public static ExecutionResult deleteBodyParam(RawApi rawApi, String key) {
        BasicDBObject payload = rawApi.fetchReqPayload();
        boolean deleted = Utils.deleteKeyFromPayload(payload, null, key);
        if (!deleted) {
            return new ExecutionResult(false, "body param not present " + key);
        }
        rawApi.modifyReqPayload(payload);
        return new ExecutionResult(true, "");
    }

    public static ExecutionResult modifyBodyParam(RawApi rawApi, String key, Object value) {
        BasicDBObject payload = rawApi.fetchReqPayload();
        boolean modified = Utils.modifyValueInPayload(payload, null, key, value);
        if (!modified) {
            return new ExecutionResult(false, "body param not present " + key);
        }
        rawApi.modifyReqPayload(payload);
        return new ExecutionResult(true, "");
    }

    public static ExecutionResult modifyUrl(RawApi rawApi, String value) {
        rawApi.modifyUrl(value);
        return new ExecutionResult(true, "");
    }

    public static ExecutionResult modifyMethod(RawApi rawApi, String value) {
        rawApi.modifyMethod(value);
        return new ExecutionResult(true, "");
    }

    public static ExecutionResult replaceVarMapValue(Map<String, Object> varMap, String key, Object val) {

        if (!varMap.containsKey(key)) {
            return new ExecutionResult(false, "variable not declared in yaml config " + key);
        }
        
        varMap.put(key, val);

        return new ExecutionResult(true, "");

    }

}

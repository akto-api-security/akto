package com.akto.dao.test_editor.executor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.akto.dao.test_editor.Utils;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.ExecutorSingleOperationResp;
import com.mongodb.BasicDBObject;

public class Operations {
    
    public static ExecutorSingleOperationResp addHeader(RawApi rawApi, String key, String value) {
        Map<String, List<String>> headers = rawApi.fetchReqHeaders();
        List<String> valList = Collections.singletonList(value);
        headers.put(key, valList);
        rawApi.modifyReqHeaders(headers);
        return new ExecutorSingleOperationResp(true, "");
    }

    public static ExecutorSingleOperationResp deleteHeader(RawApi rawApi, String key) {
        Map<String, List<String>> headers = rawApi.fetchReqHeaders();
        if (headers.containsKey(key)) {
            headers.remove(key);
        } else {
            return new ExecutorSingleOperationResp(false, "header key not present " + key);
        }
        rawApi.modifyReqHeaders(headers);
        return new ExecutorSingleOperationResp(true, "");
    }

    public static ExecutorSingleOperationResp modifyHeader(RawApi rawApi, String key, String value) {
        Map<String, List<String>> headers = rawApi.fetchReqHeaders();
        if (!headers.containsKey(key)) {
            return new ExecutorSingleOperationResp(false, "header key not present " + key);
        }
        List<String> valList = Collections.singletonList(value);
        headers.put(key, valList);
        rawApi.modifyReqHeaders(headers);
        return new ExecutorSingleOperationResp(true, "");
    }

    public static ExecutorSingleOperationResp addQueryParam(RawApi rawApi, String key, Object value) {
        BasicDBObject queryParamObj = rawApi.fetchQueryParam();
        queryParamObj.put(key, value);
        rawApi.modifyQueryParam(queryParamObj);
        return new ExecutorSingleOperationResp(true, "");
    }

    public static ExecutorSingleOperationResp deleteQueryParam(RawApi rawApi, String key) {
        BasicDBObject queryParamObj = rawApi.fetchQueryParam();
        if (queryParamObj.containsKey(key)) {
            queryParamObj.remove(key);
        } else {
            return new ExecutorSingleOperationResp(false, "query param key not present " + key);
        }
        rawApi.modifyQueryParam(queryParamObj);
        return new ExecutorSingleOperationResp(true, "");
    }

    public static ExecutorSingleOperationResp modifyQueryParam(RawApi rawApi, String key, Object value) {
        BasicDBObject queryParamObj = rawApi.fetchQueryParam();
        if (!queryParamObj.containsKey(key)) {
            return new ExecutorSingleOperationResp(false, "query param key not present " + key);
        }
        queryParamObj.put(key, value);
        rawApi.modifyQueryParam(queryParamObj);
        return new ExecutorSingleOperationResp(true, "");
    }

    public static ExecutorSingleOperationResp addBody(RawApi rawApi, String key, Object value) {
        BasicDBObject payload = rawApi.fetchReqPayload();
        payload.put(key, value);
        rawApi.modifyReqPayload(payload);
        return new ExecutorSingleOperationResp(true, "");
    }

    public static ExecutorSingleOperationResp deleteBodyParam(RawApi rawApi, String key) {
        BasicDBObject payload = rawApi.fetchReqPayload();
        boolean deleted = Utils.deleteKeyFromPayload(payload, null, key);
        if (!deleted) {
            return new ExecutorSingleOperationResp(false, "body param not present " + key);
        }
        rawApi.modifyReqPayload(payload);
        return new ExecutorSingleOperationResp(true, "");
    }

    public static ExecutorSingleOperationResp modifyBodyParam(RawApi rawApi, String key, Object value) {
        BasicDBObject payload = rawApi.fetchReqPayload();
        boolean modified = Utils.modifyValueInPayload(payload, null, key, value);
        if (!modified) {
            return new ExecutorSingleOperationResp(false, "body param not present " + key);
        }
        rawApi.modifyReqPayload(payload);
        return new ExecutorSingleOperationResp(true, "");
    }

    public static ExecutorSingleOperationResp modifyUrl(RawApi rawApi, String value) {
        rawApi.modifyUrl(value);
        return new ExecutorSingleOperationResp(true, "");
    }

    public static ExecutorSingleOperationResp modifyMethod(RawApi rawApi, String value) {
        rawApi.modifyMethod(value);
        return new ExecutorSingleOperationResp(true, "");
    }

    public static ExecutorSingleOperationResp replaceVarMapValue(Map<String, Object> varMap, String key, Object val) {

        if (!varMap.containsKey(key)) {
            return new ExecutorSingleOperationResp(false, "variable not declared in yaml config " + key);
        }
        
        varMap.put(key, val);

        return new ExecutorSingleOperationResp(true, "");

    }

}

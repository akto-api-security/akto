package com.akto.test_editor.execution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.akto.graphql.GraphQLUtils;

import com.akto.dto.RawApi;
import com.akto.dto.test_editor.ExecutorSingleOperationResp;
import com.akto.test_editor.Utils;
import com.akto.util.CookieTransformer;
import com.mongodb.BasicDBObject;
import org.apache.commons.lang3.StringUtils;

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
        boolean modified = false;
        modified = deleteCookie(headers, key, null);

        if (headers.containsKey(key)) {
            headers.remove(key);
        } else if (!modified) {
            return new ExecutorSingleOperationResp(true, "header key not present " + key);
        }
        rawApi.modifyReqHeaders(headers);
        return new ExecutorSingleOperationResp(true, "");
    }

    public static boolean modifyCookie(Map<String, List<String>> headers, String key, String value) {
        List<String> cookieList = headers.getOrDefault("cookie", new ArrayList<>());
        if (CookieTransformer.isKeyPresentInCookie(cookieList, key)) {
            CookieTransformer.modifyCookie(cookieList, key, value);
            return true;
        }
        return false;
    }

    public static boolean deleteCookie(Map<String, List<String>> headers, String key, String value) {
        List<String> cookieList = headers.getOrDefault("cookie", new ArrayList<>());
        if (CookieTransformer.isKeyPresentInCookie(cookieList, key)) {
            CookieTransformer.deleteCookie(cookieList, key, value);
            return true;
        }
        return false;
    }

    public static ExecutorSingleOperationResp modifyHeader(RawApi rawApi, String key, String value) {
        return modifyHeader(rawApi, key, value, false);
    }
    public static ExecutorSingleOperationResp modifyHeader(RawApi rawApi, String key, String value, boolean upsert) {
        Map<String, List<String>> headers = rawApi.fetchReqHeaders();
        boolean modified = false;
        modified = modifyCookie(headers, key, value);

        if (!headers.containsKey(key)) {
            if (!modified) {
                if (!upsert) {
                    return new ExecutorSingleOperationResp(true, "header key not present " + key);
                }
            } else {
                rawApi.modifyReqHeaders(headers);
                return new ExecutorSingleOperationResp(true, "");
            }
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
            return new ExecutorSingleOperationResp(true, "query param key not present " + key);
        }
        rawApi.modifyQueryParam(queryParamObj);
        return new ExecutorSingleOperationResp(true, "");
    }

    public static ExecutorSingleOperationResp modifyQueryParam(RawApi rawApi, String key, Object value) {
        BasicDBObject queryParamObj = rawApi.fetchQueryParam();
        if (!queryParamObj.containsKey(key)) {
            return new ExecutorSingleOperationResp(true, "query param key not present " + key);
        }
        queryParamObj.put(key, value);
        rawApi.modifyQueryParam(queryParamObj);
        return new ExecutorSingleOperationResp(true, "");
    }

    public static ExecutorSingleOperationResp addBody(RawApi rawApi, String key, Object value) {
        Utils.modifyRawApiPayload(rawApi, key, value);
        return new ExecutorSingleOperationResp(true, "");
    }

    public static ExecutorSingleOperationResp deleteBodyParam(RawApi rawApi, String key) {
        BasicDBObject payload = rawApi.fetchReqPayload();
        boolean deleted = Utils.deleteKeyFromPayload(payload, null, key);
        if (!deleted) {
            return new ExecutorSingleOperationResp(true, "body param not present " + key);
        }
        rawApi.modifyReqPayload(payload);
        return new ExecutorSingleOperationResp(true, "");
    }

    public static ExecutorSingleOperationResp deleteGraphqlField(RawApi rawApi, String key) {
        if (StringUtils.isEmpty(key)) {
            return new ExecutorSingleOperationResp(true,"");
        }
        String payload = rawApi.getRequest().getBody();
        String modifiedPayload = GraphQLUtils.getUtils().deleteGraphqlField(payload, key);
        rawApi.getRequest().setBody(modifiedPayload);
        return new ExecutorSingleOperationResp(true,"");
    }

    public static ExecutorSingleOperationResp addGraphqlField(RawApi rawApi, String key, String value) {
        if (StringUtils.isEmpty(key) || StringUtils.isEmpty(value)) {
            return new ExecutorSingleOperationResp(true,"");
        }
        String payload = rawApi.getRequest().getBody();
        String modifiedPayload = GraphQLUtils.getUtils().addGraphqlField(payload, key, value);
        rawApi.getRequest().setBody(modifiedPayload);
        return new ExecutorSingleOperationResp(true,"");
    }

    public static ExecutorSingleOperationResp addUniqueGraphqlField(RawApi rawApi, String key, String value) {
        if (StringUtils.isEmpty(key) || StringUtils.isEmpty(value)) {
            return new ExecutorSingleOperationResp(true,"");
        }
        String payload = rawApi.getRequest().getBody();
        String modifiedPayload = GraphQLUtils.getUtils().addUniqueGraphqlField(payload, key, value);
        rawApi.getRequest().setBody(modifiedPayload);
        return new ExecutorSingleOperationResp(true,"");
    }

    public static ExecutorSingleOperationResp modifyGraphqlField (RawApi rawApi, String key, String value) {
        if (StringUtils.isEmpty(key) || StringUtils.isEmpty(value)) {
            return new ExecutorSingleOperationResp(true,"");
        }
        String payload = rawApi.getRequest().getBody();
        String modifiedPayload = GraphQLUtils.getUtils().modifyGraphqlField(payload, key, value);
        rawApi.getRequest().setBody(modifiedPayload);
        return new ExecutorSingleOperationResp(true,"");
    }
    public static ExecutorSingleOperationResp modifyBodyParam(RawApi rawApi, String key, Object value) {
        BasicDBObject payload = rawApi.fetchReqPayload();
        BasicDBObject obj = Utils.fetchJsonObjForString(value);
        if (obj != null) {
            value = obj;
        }
        boolean modified = Utils.modifyValueInPayload(payload, null, key, value);
        if (!modified) {
            return new ExecutorSingleOperationResp(true, "body param not present " + key);
        }
        rawApi.modifyReqPayload(payload);
        return new ExecutorSingleOperationResp(true, "");
    }

    public static ExecutorSingleOperationResp replaceBody(RawApi rawApi, Object key) {
        rawApi.getRequest().setBody(key.toString());
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

package com.akto.test_editor.execution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.dto.RawApi;
import com.akto.dto.test_editor.ExecutorSingleOperationResp;
import com.akto.test_editor.Utils;
import com.akto.util.CookieTransformer;
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
        Map<String, List<String>> headers = rawApi.fetchReqHeaders();
        boolean modified = false;
        modified = modifyCookie(headers, key, value);

        if (!headers.containsKey(key)) {
            if (!modified) {
                return new ExecutorSingleOperationResp(true, "header key not present " + key);
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
        BasicDBObject payload = rawApi.fetchReqPayload();
        payload.put(key, value);
        rawApi.modifyReqPayload(payload);
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

    public static List<String> buildNewUrl(Map<String, Object> varMap, Object key, String oldUrl) {

        List<String> newUrls = new ArrayList<>();

        if (key instanceof String) {
            newUrls.add(key.toString());
            return newUrls;
        }

        if (key instanceof Map) {
            Map<String, Map<String, Object>> operationMap = (Map) key;

            Map<String, Object> m = new HashMap<>();

            if (operationMap.containsKey("regex_replace")) {
                m = operationMap.get("regex_replace");
                String regex = m.get("regex").toString();
                String replaceWith = m.get("replace_with").toString();
                String url = Utils.applyRegexModifier(oldUrl, regex, replaceWith);
                newUrls.add(url);

            } else if (operationMap.containsKey("token_replace")) {
                m = operationMap.get("token_replace");
                List<String> positions = (List<String>) m.get("positions");
                Object replaceWith = m.get("replace_with");
                List<String> replaceWithArr = new ArrayList<>();
                if (replaceWith instanceof String) {
                    Object val = VariableResolver.resolveExpression(varMap, replaceWith.toString());
                    if (val instanceof String) {
                        replaceWithArr.add(val.toString());
                    } else if (val instanceof List) {
                        int index = 0;
                        List<Object> valList = (List<Object>) val;
                        for (Object v: valList) {
                            v = VariableResolver.resolveExpression(varMap, v.toString());
                            replaceWithArr.set(index, v.toString());
                            index++;
                        }
                        
                    }
                } else if (replaceWithArr instanceof List) {
                    List<Object> valList = (List<Object>) replaceWith;
                    int index = 0;
                    for (Object v: valList) {
                        v = VariableResolver.resolveExpression(varMap, v.toString());
                        replaceWithArr.set(index, v.toString());
                        index++;
                    }
                }

                String[] urlTokens = oldUrl.split("/");
                int idx = 0;
                if (urlTokens[0].contains("http")) {
                    idx = 2;
                }

                
                for (int i = 0; i < positions.size(); i++) {
                    if (i < idx) {
                        continue;
                    }
                    for (int j = 0; j < replaceWithArr.size(); j++) {
                        String[] tokenCopy = urlTokens.clone();
                        tokenCopy[i] = replaceWithArr.get(j);
                        newUrls.add(String.join( " ", tokenCopy));
                    }
                }

            } else if (operationMap.containsKey("token_insert")) {
                m = operationMap.get("token_insert");
                List<String> positions = (List<String>) m.get("positions");
                Object replaceWith = m.get("replace_with");
                List<String> replaceWithArr = new ArrayList<>();
                if (replaceWith instanceof String) {
                    Object val = VariableResolver.resolveExpression(varMap, replaceWith.toString());
                    if (val instanceof String) {
                        replaceWithArr.add(val.toString());
                    } else if (val instanceof List) {
                        int index = 0;
                        List<Object> valList = (List<Object>) val;
                        for (Object v: valList) {
                            v = VariableResolver.resolveExpression(varMap, v.toString());
                            replaceWithArr.set(index, v.toString());
                            index++;
                        }
                        
                    }
                } else if (replaceWithArr instanceof List) {
                    List<Object> valList = (List<Object>) replaceWith;
                    int index = 0;
                    for (Object v: valList) {
                        v = VariableResolver.resolveExpression(varMap, v.toString());
                        replaceWithArr.set(index, v.toString());
                        index++;
                    }
                }

                String[] urlTokens = oldUrl.split("/");
                int idx = 0;
                if (urlTokens[0].contains("http")) {
                    idx = 2;
                }

                
                for (int i = 0; i < positions.size(); i++) {
                    if (i < idx) {
                        continue;
                    }
                    for (int j = 0; j < replaceWithArr.size(); j++) {
                        String[] tokenCopy = new String[20];
                        for (int k = 0; k < urlTokens.length; k++) {
                        }
                        tokenCopy[i] = replaceWithArr.get(j);
                        newUrls.add(String.join( " ", tokenCopy));
                    }
                }

            }
        }

        return newUrls;


    }

}

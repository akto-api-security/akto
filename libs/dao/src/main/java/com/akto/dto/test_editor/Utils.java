package com.akto.dto.test_editor;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.type.RequestTemplate;
import com.akto.util.HttpRequestResponseUtils;
import com.akto.util.JSONUtils;
import com.mongodb.BasicDBObject;

public class Utils {
    
    public static Boolean checkIfContainsMatch(String text, String keyword) {
        Pattern pattern = Pattern.compile(keyword);
        Matcher matcher = pattern.matcher(text);
        String match = null;
        if (matcher.find()) {
            match = matcher.group(0);
        }

        return match != null;
    }

    public static Boolean checkIfParamExists(String param, List<String> searchIn, OriginalHttpRequest request, OriginalHttpResponse response) {
        
        for (String location : searchIn) {
                        
        }

        return true;
    }

    public static Boolean checkInQueryParams(OriginalHttpRequest request) {
        String queryJson = HttpRequestResponseUtils.convertFormUrlEncodedToJson(request.getQueryParams());
        if (queryJson != null) {
            BasicDBObject queryObj = BasicDBObject.parse(queryJson);
            for (String key: queryObj.keySet()) {
                Object valueObj = queryObj.get(key);
                if (valueObj == null) continue;
                String value = valueObj.toString();
                if (isUrlPresent(value)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static Boolean checkInRequestBody(OriginalHttpRequest request) {
        String jsonBody = request.getJsonRequestBody();
        if(jsonBody.length() > 0) {
            BasicDBObject payload = RequestTemplate.parseRequestPayload(jsonBody, null);
            Map<String, Set<Object>> flattenedPayload = JSONUtils.flatten(payload);
            Set<String> payloadKeysToFuzz = new HashSet<>();
            for (String key : flattenedPayload.keySet()) {
                Set<Object> values = flattenedPayload.get(key);
                for (Object v : values) {
                    if (v != null && isUrlPresent(v)) {
                        return true;
                    }
                }
            }

            Map<String, Object> store = new HashMap<>();
            for (String k : payloadKeysToFuzz) store.put(k, URL);

            String modifiedPayload = JSONUtils.modify(jsonBody, payloadKeysToFuzz, new SetValueModifier(store));
            req.setBody(modifiedPayload);
        }
        return false;
    }

    private static boolean isUrlPresent(String value) {
        if (value.contains("http://") || value.contains("https://")) {
            return true;
        }
        return false;
    }

}
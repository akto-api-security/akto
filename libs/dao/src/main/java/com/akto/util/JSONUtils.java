package com.akto.util;

import com.akto.dto.type.RequestTemplate;
import com.akto.util.modifier.PayloadModifier;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

import java.util.*;

public class JSONUtils {
    private static void flatten(Object obj, String prefix, Map<String, Set<Object>> ret) {
        if (obj instanceof BasicDBObject) {
            BasicDBObject basicDBObject = (BasicDBObject) obj;

            Set<String> keySet = basicDBObject.keySet();

            if (prefix != null && !prefix.isEmpty() && (keySet == null || keySet.isEmpty())) {
                Set<Object> values = ret.getOrDefault(prefix, new HashSet<>());
                values.add(obj);
                ret.put(prefix, values);
            }

            for(String key: keySet) {

                if (key == null) {
                    continue;
                }
                boolean anyAlphabetExists = false;

                final int sz = key.length();
                for (int i = 0; i < sz; i++) {
                    final char nowChar = key.charAt(i);
                    if (Character.isLetter(nowChar)) {
                        anyAlphabetExists = true;
                        break;
                    }
                }

                key = anyAlphabetExists ? key: "NUMBER";
                Object value = basicDBObject.get(key);
                flatten(value, prefix + (prefix.isEmpty() ? "" : "#") + key, ret);
            }
        } else if (obj instanceof BasicDBList) {
            for(Object elem: (BasicDBList) obj) {
                flatten(elem, prefix+(prefix.isEmpty() ? "$" : "#$"), ret);
            }
        } else {
            Set<Object> values = ret.getOrDefault(prefix, new HashSet<>());
            values.add(obj);
            ret.put(prefix, values);
        }
    }

    public static Map<String, Set<Object>> flatten(BasicDBObject object) {
        Map<String, Set<Object>> ret = new HashMap<>();
        if (object == null) return ret;
        String prefix = "";
        flatten(object, prefix, ret);
        return ret;
    }

    public static BasicDBObject flattenWithDots(Object object) {
        BasicDBObject ret = new BasicDBObject();
        String prefix = "";
        flattenWithDots(object, prefix, ret);
        return ret;
    }

    private static void flattenWithDots(Object obj, String prefix, BasicDBObject ret) {
        if (obj instanceof BasicDBObject) {
            BasicDBObject basicDBObject = (BasicDBObject) obj;

            Set<String> keySet = basicDBObject.keySet();

            if (prefix != null && !prefix.isEmpty() && (keySet == null || keySet.isEmpty())) {
                ret.put(prefix, obj);
            }

            for(String key: keySet) {

                if (key == null) {
                    continue;
                }
                boolean anyAlphabetExists = false;

                final int sz = key.length();
                for (int i = 0; i < sz; i++) {
                    final char nowChar = key.charAt(i);
                    if (Character.isLetter(nowChar)) {
                        anyAlphabetExists = true;
                        break;
                    }
                }

                key = anyAlphabetExists ? key: "NUMBER";
                Object value = basicDBObject.get(key);
                flattenWithDots(value, prefix + (prefix == null || prefix.isEmpty() ? "" : ".") + key, ret);
            }
        } else if (obj instanceof BasicDBList) {
            int idx = 0;
            for(Object elem: (BasicDBList) obj) {
                flattenWithDots(elem, prefix+("["+idx+"]"), ret);
                idx += 1;
            }
        } else {
            ret.put(prefix, obj);
        }
    }

    public static String modify(String jsonBody, Set<String> values, PayloadModifier payloadModifier) {
        try {
            BasicDBObject payload = RequestTemplate.parseRequestPayload(jsonBody, null);
            if (payload.isEmpty()) return jsonBody;
            BasicDBObject modifiedPayload = modify(payload, values, payloadModifier);
            if (modifiedPayload.containsKey("json")) {
                return new Gson().toJson(modifiedPayload.get("json"));
            }
            return new Gson().toJson(modifiedPayload);
        } catch (Exception e) {
            return jsonBody;
        }
    }

    public static BasicDBObject modify(BasicDBObject obj, Set<String> values, PayloadModifier payloadModifier) {
        BasicDBObject result = (BasicDBObject) obj.copy();
        modify(result, "" ,values, payloadModifier);
        return result;
    }

    private static void modify(Object obj, String prefix, Set<String> values, PayloadModifier payloadModifier) {
        if (obj instanceof BasicDBObject) {
            BasicDBObject basicDBObject = (BasicDBObject) obj;
            Set<String> keySet = basicDBObject.keySet();

            for(String key: keySet) {
                if (key == null) continue;
                String fullKey = prefix + (prefix.isEmpty() ? "" : "#") + key;
                Object value = basicDBObject.get(key);
                if (values.contains(fullKey)) {
                    basicDBObject.put(key, payloadModifier.modify(fullKey, value));
                }
                modify(value, fullKey, values, payloadModifier);
            }

        } else if (obj instanceof BasicDBList) {
            for (Object elem: (BasicDBList) obj) {
                modify(elem, prefix+(prefix.isEmpty() ? "$" : "#$"), values, payloadModifier);
            }
        }
    }

    public static String parseIfJsonP(String payload) {
        if (payload == null) return null;
        if (!payload.startsWith("{") && !payload.startsWith("[") && !payload.startsWith("<")) {//candidate for json with padding handleRequest ({abc : abc})
            int indexOfMethodStart = payload.indexOf('(');
            int indexOfMethodEnd = payload.lastIndexOf(')');
            try {
                String nextChar = payload.substring(indexOfMethodStart + 1, indexOfMethodStart + 5);
                if (nextChar.startsWith("{")) {
                    String json = payload.substring(indexOfMethodStart + 1, indexOfMethodEnd);//Getting the content of method
                    JsonParser.parseString(json);
                    return json;
                }
            }catch (Exception e) {
                return payload;
            }
        }
        return payload;
    }



    public static Map<String, List<String>> modifyHeaderValues(Map<String, List<String>> headers, PayloadModifier payloadModifier) {
        if (headers == null) return null;
        boolean flag = false;
        Map<String, List<String>> modifiedHeaders = new HashMap<>(headers);
        for (String header: modifiedHeaders.keySet()) {
            List<String> values = modifiedHeaders.get(header);
            List<String> newValues = new ArrayList<>();
            for (String value: values) {
                Object modifiedHeader = payloadModifier.modify(header, value);
                if (modifiedHeader != null) {
                    newValues.add(modifiedHeader.toString());
                    flag = true;
                } else {
                    newValues.add(value);
                }
            }
            modifiedHeaders.put(header, newValues);
        }

        if (!flag) return null;

        return modifiedHeaders;
    }


}

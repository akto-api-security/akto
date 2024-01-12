package com.akto.test_editor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.akto.dto.RawApi;
import com.akto.util.JSONUtils;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

public class Utils {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final JsonFactory factory = mapper.getFactory();

    public static Boolean checkIfContainsMatch(String text, String keyword) {
        Pattern pattern = Pattern.compile(keyword);
        Matcher matcher = pattern.matcher(text);
        String match = null;
        if (matcher.find()) {
            match = matcher.group(0);
        }

        return match != null;
    }

    public static boolean deleteKeyFromPayload(Object obj, String parentKey, String queryKey) {
        boolean res = false;
        if (obj instanceof BasicDBObject) {
            BasicDBObject basicDBObject = (BasicDBObject) obj;

            Set<String> keySet = basicDBObject.keySet();

            for(String key: keySet) {
                if (key == null) {
                    continue;
                }
                Object value = basicDBObject.get(key);
                if (!( (value instanceof BasicDBObject) || (value instanceof BasicDBList) )) {
                    if (key.equalsIgnoreCase(queryKey)) {
                        basicDBObject.remove(key);
                        return true;
                    }
                }
                res = deleteKeyFromPayload(value, key, queryKey);
                if (res) {
                    break;
                }
            }
        } else if (obj instanceof BasicDBList) {
            for(Object elem: (BasicDBList) obj) {
                res = deleteKeyFromPayload(elem, parentKey, queryKey);
                if (res) {
                    break;
                }
            }
        }

        return res;
    }

    public static boolean modifyValueInPayload(Object obj, String parentKey, String queryKey, Object queryVal) {
        boolean res = false;
        if (obj instanceof BasicDBObject) {
            BasicDBObject basicDBObject = (BasicDBObject) obj;

            Set<String> keySet = basicDBObject.keySet();

            for(String key: keySet) {
                if (key == null) {
                    continue;
                }
                Object value = basicDBObject.get(key);

                if (!( (value instanceof BasicDBObject) || (value instanceof BasicDBList) )) {
                    if (key.equalsIgnoreCase(queryKey)) {
                        basicDBObject.remove(key);
                        basicDBObject.put(queryKey, queryVal);
                        return true;
                    }
                }

                if (value instanceof BasicDBList) {
                    BasicDBList valList = (BasicDBList) value;
                    if (valList.size() == 0 && key.equalsIgnoreCase(queryKey)) {
                        List<Object> queryList = Collections.singletonList(queryVal);
                        basicDBObject.remove(key);
                        basicDBObject.put(queryKey, queryList);
                        return true;
                    } else if (valList.size() > 0 && !( (valList.get(0) instanceof BasicDBObject) || (valList.get(0) instanceof BasicDBList) ) && key.equalsIgnoreCase(queryKey)) {
                        List<Object> queryList = Collections.singletonList(queryVal);
                        basicDBObject.remove(key);
                        basicDBObject.put(queryKey, queryList);
                        return true;
                    }
                }

                res = modifyValueInPayload(value, key, queryKey, queryVal);
                if (res) {
                    break;
                }
            }
        } else if (obj instanceof BasicDBList) {
            for(Object elem: (BasicDBList) obj) {
                res = modifyValueInPayload(elem, parentKey, queryKey, queryVal);
                if (res) {
                    break;
                }
            }
        }

        return res;
    }

    public static String applyRegexModifier(String data, String regex, String replaceWith) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(data);
        if (matcher.find()) {
            data = data.replaceAll(regex, replaceWith);
        }
        return data;
    }

    public static Boolean applyIneqalityOperation(Object data, Object querySet, String operator) {
        Boolean result = false;
        try {
            if (data instanceof Integer) {
                List<Integer> queryList = (List) querySet;
                if (queryList == null || queryList.size() == 0) {
                    return false;
                }
                Integer dataInt = (Integer) data;
                Object query = queryList.get(0);
                if (query instanceof String) {
                    int queryInt = Integer.parseInt((String) query);
                    result = compareIntegers(operator, dataInt, queryInt);
                } else {
                    result = compareIntegers(operator, (int) dataInt, (int) queryList.get(0));
                }
            }
            
        } catch (Exception e) {
            return false;
        }

        return result;
    }

    public static Boolean compareIntegers(String operator, int a, int b) {
        Boolean result = false;
        switch (operator) {
            case "gte":
                result = (int) a >= b;
                break;
            case "gt":
                result = (int) a > b;
                break;
            case "lte":
                result = (int) a <= b;
                break;
            case "lt":
                result = (int) a < b;
                break;
            default:
                return false;
        }
        return result;
    }

    public static BasicDBObject fetchJsonObjForString(Object val) {
        if (!(val instanceof String)) {
            return null;
        }
        try {
            BasicDBObject obj = BasicDBObject.parse(val.toString());
            return obj;
        } catch (Exception e) {
            return null;
        }
    }

    public static List<String> findAllValuesForKey(String payload, String key, boolean isRegex) {
        JsonParser jp = null;
        JsonNode node;
        List<String> values = new ArrayList<>();
        try {
            jp = factory.createParser(payload);
            node = mapper.readTree(jp);
        } catch (IOException e) {
            return values;
        }
        if (node == null) {
            return values;
        }

        findAllValues(node, key, values, isRegex);
        return values;
    }

    public static void findAllValues(JsonNode node, String matchFieldName, List<String> values, boolean isRegex) {

        if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for (int i = 0; i < arrayNode.size(); i++) {
                JsonNode arrayElement = arrayNode.get(i);
                findAllValues(arrayElement, matchFieldName, values, isRegex);
            }
        } else {
            Iterator<String> fieldNames = node.fieldNames();
            while(fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                if (Utils.checkIfMatches(fieldName, matchFieldName, isRegex)) {
                    String val;
                    try {
                        TextNode n = (TextNode) node.get(fieldName);
                        val = n.asText();
                    } catch (Exception e) {
                        val = node.get(fieldName).toString();
                    }
                    values.add(val);
                }
                JsonNode fieldValue = node.get(fieldName);
                findAllValues(fieldValue, matchFieldName, values, isRegex);
            }
        }

    }

    public static boolean checkIfMatches(String data, String query, boolean isRegex) {
        if (!isRegex) {
            return data.equalsIgnoreCase(query);
        }

        return Utils.checkIfContainsMatch(data, query);
    }

    public static double structureMatch(RawApi orig, RawApi cur) {
        String payload = orig.getResponse().getBody().replaceAll("\\s+","");
        String compareWithPayload = cur.getResponse().getBody().replaceAll("\\s+","");
        return Utils.calcStructureMatchPercentage(payload, compareWithPayload);
    }

    public static double calcStructureMatchPercentage(String payload, String compareWithPayload) {

        boolean isOrigPAyloadJson = isJsonPayload(payload);
        boolean isCurPAyloadJson = isJsonPayload(compareWithPayload);
        if (!isOrigPAyloadJson && !isCurPAyloadJson) {
            return 100;
        }

        boolean areBothJson = isOrigPAyloadJson && isCurPAyloadJson;
        if (!areBothJson) {
            return 0;
        }

        BasicDBObject payloadObj = extractPayloadObj(payload);
        BasicDBObject comparePayloadObj = extractPayloadObj(compareWithPayload);

        payloadObj = JSONUtils.flattenWithDots(payloadObj);
        comparePayloadObj = JSONUtils.flattenWithDots(comparePayloadObj);

        if (payloadObj.size() == 0 && comparePayloadObj.size() == 0) {
            return 100;
        }

        if (payloadObj.size() == 0 || comparePayloadObj.size() == 0) {
            return 0;
        }

        Set<String> payloadObjSet = new HashSet<>();
        Set<String> comparePayloadObjSet = new HashSet<>();
        Set<String> intersectionSet = new HashSet<>();
        Set<String> unionSet = new HashSet<>();
        for (String k: payloadObj.keySet()) {
            payloadObjSet.add(k);
            intersectionSet.add(k);
            unionSet.add(k);
        }
        for (String k: comparePayloadObj.keySet()) {
            comparePayloadObjSet.add(k);
        }

        intersectionSet.retainAll(comparePayloadObjSet);
        unionSet.addAll(comparePayloadObjSet);

        return ((double) intersectionSet.size()/unionSet.size()) * 100;
    }

    public static BasicDBObject extractPayloadObj(String payload) {
        if (payload == null || payload.isEmpty()) {
            payload = "{}";
        }

        if(payload.startsWith("[")) {
            payload = "{\"json\": "+payload+"}";
        }

        BasicDBObject obj;
        try {
            obj = BasicDBObject.parse(payload);
        } catch (Exception e) {
            obj = BasicDBObject.parse("{}");
        }

        return obj;
    }

    public static boolean isJsonPayload(String payload) {
        try {
            Map<String, Object> m1 = (Map<String, Object>)(mapper.readValue(payload, Map.class));
        } catch (Exception e) {
            return false;
        }
        return true;
    }

}

package com.akto.test_editor;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.akto.dto.RawApi;
import com.akto.dto.testing.UrlModifierPayload;
import com.akto.util.JSONUtils;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.gson.Gson;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

public class Utils {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final JsonFactory factory = mapper.getFactory();
    private static final Gson gson = new Gson();

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
                    try {
                        int queryInt = Integer.parseInt((String) query);
                        result = compareIntegers(operator, dataInt, queryInt);
                    } catch (Exception e) {
                        Double queryDouble = Double.parseDouble(query.toString());
                        result = compareDoubles(operator, dataInt.doubleValue(), queryDouble);
                    }
                } else if (query instanceof Double) {
                    Double queryDouble = Double.parseDouble(query.toString());
                    result = compareDoubles(operator, dataInt.doubleValue(), queryDouble);
                } else {
                    result = compareIntegers(operator, (int) dataInt, (int) queryList.get(0));
                }
            }
            
        } catch (Exception e) {
            return false;
        }

        return result;
    }

    public static Boolean compareDoubles(String operator, double a, double b) {
        Boolean result = false;
        switch (operator) {
            case "gte":
                result = a >= b;
                break;
            case "gt":
                result = a > b;
                break;
            case "lte":
                result = a <= b;
                break;
            case "lt":
                result = a < b;
                break;
            default:
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

    public static UrlModifierPayload fetchUrlModifyPayload(String payload) {
        UrlModifierPayload urlModifierPayload = null;
        try {
            payload = payload.replaceAll("=", ":");
            Map<String, Object> json = gson.fromJson(payload, Map.class);
            String operation = "regex_replace";
            Map<String, Object> operationMap = new HashMap<>();
            if (json.containsKey("regex_replace")) {
                operationMap = (Map) json.get("regex_replace");
            } else if (json.containsKey("token_insert")) {
                operationMap = (Map) json.get("token_insert");
                operation = "token_insert";
            } else if (json.containsKey("token_replace")) {
                operationMap = (Map) json.get("token_replace");
                operation = "token_replace";
            }
            String locStr = operationMap.getOrDefault("location", "0").toString();
            Double loc = Double.parseDouble(locStr);
            Integer location = loc.intValue();

            String replaceWith = operationMap.getOrDefault("replace_with", "").toString();
            try {
                Double replaceWithDouble = Double.parseDouble(replaceWith);
                Integer replaceWithInt = replaceWithDouble.intValue();
                replaceWith = replaceWithInt.toString();
            } catch (Exception e) {
                // TODO: handle exception
            }

            urlModifierPayload = new UrlModifierPayload(operationMap.getOrDefault("regex", "").toString(), 
                location, replaceWith, operation);
        } catch (Exception e) {
            return urlModifierPayload;
        }
        return urlModifierPayload;
    }

    public static String buildNewUrl(UrlModifierPayload urlModifierPayload, String oldUrl) {
        String url = "";
        if (urlModifierPayload.getOperationType().equalsIgnoreCase("regex_replace") || urlModifierPayload.getOperationType().equalsIgnoreCase("token_replace")) {
            if (urlModifierPayload.getRegex() != null && !urlModifierPayload.getRegex().equals("")) {
                url = Utils.applyRegexModifier(oldUrl, urlModifierPayload.getRegex(), urlModifierPayload.getReplaceWith());
            } else {
                URI uri = fetchUri(oldUrl);
                oldUrl = fetchUrlPath(uri, oldUrl);

                String[] urlTokens = oldUrl.split("/");
                Integer position = urlModifierPayload.getPosition();
                if (position <= 0) {
                    // position is not valid
                    return fetchActualUrl(uri, oldUrl);
                }
                return replaceUrlWithToken(urlTokens, urlModifierPayload, position, uri);
            }
        } else {
            URI uri = fetchUri(oldUrl);
            oldUrl = fetchUrlPath(uri, oldUrl);

            String[] urlTokens = oldUrl.split("/");
            Integer position = urlModifierPayload.getPosition();
            if (position <= 0) {
                // position is not valid
                return fetchActualUrl(uri, oldUrl);
            }

            return insertUrlWithToken(urlTokens, urlModifierPayload, position, uri);
            
        }
        return url;
    }

    private static URI fetchUri(String url) {
        URI uri = null;
        try {
            uri = new URI(url);
        } catch (Exception e) {
            // TODO: handle exception
        }
        return uri;
    }

    private static String fetchUrlPath(URI uri, String url) {
        if (uri != null) {
            return uri.getPath();
        }
        return url;
    }

    private static String fetchActualUrl(URI uri, String url) {
        if (uri != null && uri.getHost() != null) {
            return uri.getScheme() + "://" + uri.getHost() + url;
        } else {
            return url;
        }
    }

    private static String replaceUrlWithToken(String[] urlTokens, UrlModifierPayload urlModifierPayload, int position, URI uri) {
       
        String[] urlTokensCopy;

        if (position >= urlTokens.length) {
            urlTokensCopy = new String[position+1];
            for (int i=0; i < urlTokens.length; i++) {
                urlTokensCopy[i] = urlTokens[i];
            }
            for (int i=urlTokens.length; i <= position; i++) {
                urlTokensCopy[i] = "/";
            }
            urlTokensCopy[position] = urlModifierPayload.getReplaceWith();
            String url = "/";
            for (int i=1; i < urlTokensCopy.length; i++) {
                if (urlTokensCopy[i].equals("/") || i == urlTokensCopy.length - 1) {
                    url = url + urlTokensCopy[i];
                } else {
                    url = url + urlTokensCopy[i] + "/";
                }
            }
            return fetchActualUrl(uri, url);
        }
        urlTokens[position] = urlModifierPayload.getReplaceWith();
        String url = String.join( "/", urlTokens);
        return fetchActualUrl(uri, url);
    }

    private static String insertUrlWithToken(String[] urlTokens, UrlModifierPayload urlModifierPayload, int position, URI uri) {
        
        String[] urlTokensCopy;

        if (position > urlTokens.length) {
            urlTokensCopy = new String[position];
            for (int i=0; i < urlTokens.length; i++) {
                urlTokensCopy[i] = urlTokens[i];
            }
            for (int i=urlTokens.length; i < position; i++) {
                urlTokensCopy[i] = "/";
            }
            
            String[] newUrlTokens = new String[urlTokensCopy.length];
            for (int i = 1; i < position; i++) {
                newUrlTokens[i-1] = urlTokensCopy[i];
            }
            newUrlTokens[position - 1] = urlModifierPayload.getReplaceWith();
            for (int i = position; i < urlTokensCopy.length - 1; i++) {
                newUrlTokens[i] = urlTokensCopy[i];
            }
            String url = "/";
            for (int i=0; i < newUrlTokens.length; i++) {
                if (newUrlTokens[i].equals("/") || i == newUrlTokens.length - 1) {
                    url = url + newUrlTokens[i];
                } else {
                    url = url + newUrlTokens[i] + "/";
                }
            }
            return fetchActualUrl(uri, url);

        }

        String[] newUrlTokens = new String[urlTokens.length];
        for (int i = 1; i < position; i++) {
            newUrlTokens[i-1] = urlTokens[i];
        }
        newUrlTokens[position - 1] = urlModifierPayload.getReplaceWith();
        for (int i = position; i < urlTokens.length; i++) {
            newUrlTokens[i] = urlTokens[i];
        }
        String url = String.join( "/", newUrlTokens);
        url = "/" + url;
        return fetchActualUrl(uri, url);
    }

}

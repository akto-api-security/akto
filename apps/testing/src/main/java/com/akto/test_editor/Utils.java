package com.akto.test_editor;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

import static com.akto.rules.TestPlugin.extractAllValuesFromPayload;

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


    public static Set<String> headerValuesUnchanged(Map<String, List<String>> originalRequestHeaders, Map<String, List<String>> testRequestHeaders) {
        Set<String> diff = new HashSet<>();
        if (originalRequestHeaders == null) return diff;
        for (String key: testRequestHeaders.keySet()) {
            List<String> originalHeaderValues = originalRequestHeaders.get(key);
            List<String> testHeaderValues = testRequestHeaders.get(key);
            if (originalHeaderValues == null || testHeaderValues == null) continue;
            if (areListsEqual(originalHeaderValues, testHeaderValues)) {
                diff.add(key);
            }
        }

        return diff;
    }

    public static boolean areListsEqual(List<String> list1, List<String> list2) {
        if (list1.size() != list2.size()) {
            return false;
        }

        List<String> copyOfList1 = new ArrayList<>(list1);
        List<String> copyOfList2 = new ArrayList<>(list2);

        Collections.sort(copyOfList1);
        Collections.sort(copyOfList2);

        return copyOfList1.equals(copyOfList2);
    }

    public static Set<String> bodyValuesUnchanged(String originalPayload, String testPayload) {
        Set<String> diff = new HashSet<>();

        Map<String, Set<String>> originalRequestParamMap = new HashMap<>();
        Map<String, Set<String>> testRequestParamMap= new HashMap<>();
        try {
            extractAllValuesFromPayload(originalPayload, originalRequestParamMap);
            extractAllValuesFromPayload(testPayload, testRequestParamMap);
        } catch (Exception e) {
        }

        for (String key: testRequestParamMap.keySet()) {
            Set<String> testValues = testRequestParamMap.get(key);
            Set<String> originalValues = originalRequestParamMap.get(key);
            if (testValues == null) continue;
            String[] keySplit = key.split("\\.");
            String finalKey = keySplit[keySplit.length - 1];
            if (testValues.equals(originalValues)) diff.add(finalKey); // todo: check null
        }

        return diff;
    }

}
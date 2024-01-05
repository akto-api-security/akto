package com.akto.test_editor.execution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bson.conversions.Bson;

import com.akto.dao.SampleDataDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.KeyTypes;
import com.akto.dto.type.RequestTemplate;
import com.akto.parsers.HttpCallParser;
import com.akto.test_editor.Utils;
import com.akto.util.modifier.AddJkuJWTModifier;
import com.akto.util.modifier.InvalidSignatureJWTModifier;
import com.akto.util.modifier.NoneAlgoJWTModifier;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

public class VariableResolver {
    
    public static Object getValue(Map<String, Object> varMap, String key) {
        if (!varMap.containsKey(key)) {
            return null;
        }
        Object obj = varMap.get(key);
        return obj;
    }

    public static String resolveExpression(Map<String, Object> varMap, String expression) {

        Pattern pattern = Pattern.compile("\\$\\{[^}]*\\}");
        Matcher matcher = pattern.matcher(expression);
        while (matcher.find()) {
            try {
                String match = matcher.group(0);
                match = match.substring(2, match.length());
                match = match.substring(0, match.length() - 1);
                Object val = getValue(varMap, match);
                String valString = val.toString();
                expression = expression.replaceFirst("(\\$\\{[^}]*\\})", valString);
            } catch (Exception e) {
                return expression;
            }
        }

        Object val = getValue(varMap, expression);
        if (val == null) {
            return expression;
        } else {
            return val.toString();
        }

    }

    public static Object resolveContextVariable(Map<String, Object> varMap, String expression) {

        Pattern pattern = Pattern.compile("\\$\\{[^}]*\\}");
        Matcher matcher = pattern.matcher(expression);
        if (matcher.find()) {
            try {

                // split with '.', check if length is 2 and second element should be key/value

                String match = matcher.group(0);
                match = match.substring(2, match.length());
                match = match.substring(0, match.length() - 1);

                String[] params = match.split("\\.");
                if (params.length < 2) {
                    return expression;
                }
                String firstParam = params[0];
                String secondParam = params[1];
                Object val = getValue(varMap, "context_" + firstParam);
                if (val == null) {
                    return expression;
                }
                ArrayList<String> listVal = new ArrayList<>();

                if (!(val instanceof ArrayList)) {
                    return expression;
                }
                ArrayList<BasicDBObject> contextListVal = (ArrayList<BasicDBObject>) val;
                for (BasicDBObject obj: contextListVal) {
                    if (secondParam.equalsIgnoreCase("key")) {
                        listVal.add(obj.get("key").toString());
                    } else if (secondParam.equalsIgnoreCase("value")) {
                        listVal.add(obj.get("value").toString());
                    }
                } 

                return listVal;
            } catch (Exception e) {
                return expression;
            }
        }
        return null;
    }

    public static Object resolveContextKey(Map<String, Object> varMap, String expression) {
        String[] params = expression.split("\\.");
        if (params.length < 2) {
            return expression;
        }
        String firstParam = params[0];
        String secondParam = params[1];
        Object val = getValue(varMap, "context_" + firstParam);
        if (val == null) {
            return expression;
        }
        ArrayList<String> listVal = new ArrayList<>();

        if (!(val instanceof ArrayList)) {
            return expression;
        }
        ArrayList<BasicDBObject> contextListVal = (ArrayList<BasicDBObject>) val;
        for (BasicDBObject obj: contextListVal) {
            if (secondParam.equalsIgnoreCase("key")) {
                listVal.add(obj.get("key").toString());
            } else if (secondParam.equalsIgnoreCase("value")) {
                listVal.add(obj.get("value").toString());
            }
        }
        return listVal;
    }

    public static Boolean isAuthContext(Object val) {
        if (!(val instanceof String)) {
            return false;
        }

        String expression = val.toString();

        Pattern pattern = Pattern.compile("\\$\\{[^}]*\\}");
        Matcher matcher = pattern.matcher(expression);
        if (matcher.find()) {
            try {

                // split with '.', check if length is 2 and second element should be key/value

                String match = matcher.group(0);
                match = match.substring(2, match.length());
                match = match.substring(0, match.length() - 1);

                String[] params = match.split("\\.");
                if (params.length < 2) {
                    return false;
                }
                String firstParam = params[0];
                String secondParam = params[1];

                if (!firstParam.equalsIgnoreCase("auth_context")) {
                    return false;
                }

                if (secondParam.equalsIgnoreCase("none_algo_token") || secondParam.equalsIgnoreCase("invalid_signature_token") 
                    || secondParam.equalsIgnoreCase("jku_added_token")) {
                        return true;
                }
            } catch (Exception e) {
                return false;
            }
        }

        return false;

    }

    public static String resolveAuthContext(String expression, Map<String, List<String>> headers, String headerKey) {
        expression = expression.substring(2, expression.length());
        expression = expression.substring(0, expression.length() - 1);

        String[] params = expression.split("\\.");
        String secondParam = params[1];

        if (!headers.containsKey(headerKey)) {
            return null;
        }

        String headerVal = headers.get(headerKey).get(0);

        String[] splitValue = headerVal.toString().split(" ");
        String modifiedHeaderVal = null;

        List<String> finalValue = new ArrayList<>();

        for (String val: splitValue) {
            if (!KeyTypes.isJWT(val)) {
                finalValue.add(val);
                continue;
            }
            if (secondParam.equalsIgnoreCase("none_algo_token")) {
                NoneAlgoJWTModifier noneAlgoJWTModifier = new NoneAlgoJWTModifier("none");
                try {
                    modifiedHeaderVal = noneAlgoJWTModifier.jwtModify("", val);
                } catch(Exception e) {
                    return null;
                }
            } else if (secondParam.equalsIgnoreCase("invalid_signature_token")) {
                InvalidSignatureJWTModifier invalidSigModified = new InvalidSignatureJWTModifier();
                modifiedHeaderVal = invalidSigModified.jwtModify("", val);
            } else if (secondParam.equalsIgnoreCase("jku_added_token")) {
                AddJkuJWTModifier addJkuJWTModifier = new AddJkuJWTModifier();
                try {
                    modifiedHeaderVal = addJkuJWTModifier.jwtModify("", val);
                } catch(Exception e) {
                    return null;
                }
            }
            finalValue.add(modifiedHeaderVal);
        }
        
        return String.join( " ", finalValue);
    }

    public static Boolean isWordListVariable(Object key, Map<String, Object> varMap) {
        if (key == null || !(key instanceof String)) {
            return false;
        }

        if (key.toString().length() < 3) {
            return false;
        }

        String expression = key.toString();

        Pattern pattern = Pattern.compile("\\$\\{[^}]*\\}");
        Matcher matcher = pattern.matcher(expression);
        while (matcher.find()) {
            try {
                String match = matcher.group(0);
                match = match.substring(2, match.length());
                match = match.substring(0, match.length() - 1);

                Boolean isWordListVar = varMap.containsKey("wordList_" + match);
                if (isWordListVar) return true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return false;
    }

    public static List<String> resolveWordListVar(String key, Map<String, Object> varMap) {
        String expression = key.toString();

        List<String> wordList = new ArrayList<>();
        String wordListKey = null;

        Pattern pattern = Pattern.compile("\\$\\{[^}]*\\}");
        Matcher matcher = pattern.matcher(expression);
        while (matcher.find()) {
            try {
                String match = matcher.group(0);
                String originalKey = match;
                match = match.substring(2, match.length());
                match = match.substring(0, match.length() - 1);

                Boolean isWordListVar = varMap.containsKey("wordList_" + match);
                if (isWordListVar) {
                    wordList = (List<String>) varMap.get("wordList_" + match);
                    wordListKey = originalKey;
                    break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        List<String> result = new ArrayList<>();
        for (String word: wordList) {
            result.add(expression.replace(wordListKey, word));
        }

        return result;
    }

    public static void resolveWordList(Map<String, Object> varMap, Map<ApiInfoKey, List<String>>  sampleDataMap, ApiInfo.ApiInfoKey apiInfoKey) {

        for (String k: varMap.keySet()) {
            if (!k.contains("wordList_")) {
                continue;
            }
            Map<String, String> m = new HashMap<>();
            Object keyObj;
            String key, location, dataType;
            boolean isRegex = false;
            boolean allApis = false;
            try {
                List<String> wordList = (List<String>) varMap.get(k);
                continue;
            } catch (Exception e) {
                try {
                    m = (Map) varMap.get(k);
                } catch (Exception er) {
                    continue;
                }
            }
            keyObj = m.get("key");
            if (keyObj instanceof Map) {
                Map<String, String> kMap = (Map) keyObj;
                key = (String) kMap.get("regex");
                isRegex = true;
            } else {
                key = (String) m.get("key");
            }
            location = m.get("location");
            if (m.containsKey("all_apis")) {
                allApis = Objects.equals(m.get("all_apis"), true);
            }

            Map<ApiInfoKey, List<String>> modifiedSampleDataMap = new HashMap<>();
            if (allApis) {
                for (ApiInfoKey infoKey: sampleDataMap.keySet()) {
                    if (infoKey.getApiCollectionId() != apiInfoKey.getApiCollectionId()) {
                        continue;
                    }
                    modifiedSampleDataMap.put(infoKey, sampleDataMap.get(infoKey));
                }
            } else {
                modifiedSampleDataMap.put(apiInfoKey, sampleDataMap.get(apiInfoKey));
            }

            Set<String> wordListSet = new HashSet<>();
            List<String> wordListVal = new ArrayList<>();

            for (ApiInfoKey infoKey: modifiedSampleDataMap.keySet()) {
                List<String> samples = modifiedSampleDataMap.get(infoKey);
                wordListSet.addAll(extractValuesFromSampleData(varMap, samples, key, location, isRegex));

            }

            for (String s : wordListSet) {
                wordListVal.add(s);
            }

            if (wordListSet.size() >= 100) {
                break;
            }

            varMap.put(k, wordListVal);
        }

    }

    public static Set<String> extractValuesFromSampleData(Map<String, Object> varMap, List<String> samples, String key, String location, boolean isRegex) {

        Set<String> worklistVal = new HashSet<>();
        for (String sample: samples) {
            HttpResponseParams httpResponseParams;
            HttpRequestParams httpRequestParams;
            try {
                httpResponseParams = HttpCallParser.parseKafkaMessage(sample);
                httpRequestParams = httpResponseParams.getRequestParams();
                if (location == null || location.equals("header")) {
                    Map<String, List<String>> headers = httpResponseParams.getHeaders();
                    for (String headerName: headers.keySet()) {
                        if (!Utils.checkIfMatches(headerName, key, isRegex)) {
                            continue;
                        }
                        List<String> headerValues = headers.get(headerName);
                        for (String value: headerValues) {
                            worklistVal.add(value);
                        }
                    }

                    Map<String, List<String>> reqHeaders = httpRequestParams.getHeaders();

                    for (String headerName: reqHeaders.keySet()) {
                        if (!Utils.checkIfMatches(headerName, key, isRegex)) {
                            continue;
                        }
                        List<String> headerValues = reqHeaders.get(headerName);
                        for (String value: headerValues) {
                            worklistVal.add(value);
                        }
                    }
                }

                if (location == null || location.equals("payload")) {
                    worklistVal.addAll(Utils.findAllValuesForKey(httpRequestParams.getPayload(), key, isRegex));
                    worklistVal.addAll(Utils.findAllValuesForKey(httpResponseParams.getPayload(), key, isRegex));
                }
                
                if (location == null || location.equals("query_param")) {
                    BasicDBObject queryParams = RequestTemplate.getQueryJSON(httpRequestParams.getURL());
                    for (String qu: queryParams.keySet()) {
                        if (!Utils.checkIfMatches(qu, key, isRegex)) {
                            continue;
                        }
                        worklistVal.add(queryParams.getString(qu));
                    }
                }

            } catch (Exception e) {
                continue;
            }
            
        }

        return worklistVal;
    }

    // public Object resolveExpression(Map<String, Object> varMap, String expression) {

    //     Object val = null;

    //     Pattern pattern = Pattern.compile("(\\S+)\\s?[\\+\\-\\*\\/]\\s?(\\S+)");
    //     Matcher matcher = pattern.matcher(expression);

    //     if (matcher.find()) {
    //         try {
    //             String operand1 = (String) resolveVariable(varMap, matcher.group(1));
    //             String operator = (String) resolveVariable(varMap, matcher.group(2));
    //             String operand2 = (String) resolveVariable(varMap, matcher.group(3));
    //             val = evaluateExpressionValue(operand1, operator, operand2);

    //         } catch(Exception e) {
    //             return expression;
    //         }
            
    //     }

    //     return val;

    // }

    // public Object evaluateExpressionValue(String operand1, String operator, String operand2) {

    //     switch(operator) {
    //         case "+":
    //             add(operand1, operator, operand2);
    //         case "-":
    //             subtract(operand1, operator, operand2);
    //         case "*":
    //             multiply(operand1, operator, operand2);
    //         case "/":
    //             divide(operand1, operator, operand2);
    //         default:
    //             // throw exception
    //     }

    //     return null;

    // }

    // public Object multiply(String operand1, String operator, String operand2) {
    //     try {
    //         int op1 = Integer.parseInt(operand1);
    //         int op2 = Integer.parseInt(operand2);
    //         return op1 * op2;
    //     } catch (Exception e) {
    //         return null;
    //     }

    // }

    // public Object divide(String operand1, String operator, String operand2) {
    //     try {
    //         int op1 = Integer.parseInt(operand1);
    //         int op2 = Integer.parseInt(operand2);
    //         if (op2 == 0) {
    //             throw new Exception("invalid operand2");
    //         }
    //         return op1 / op2;
    //     } catch (Exception e) {
    //         return null;
    //     }

    // }

    // public Object subtract(String operand1, String operator, String operand2) {
    //     try {
    //         int op1 = Integer.parseInt(operand1);
    //         int op2 = Integer.parseInt(operand2);
    //         return op1 - op2;
    //     } catch (Exception e) {
    //         return null;
    //     }

    // }

    // public Object add(String operand1, String operator, String operand2) {

    //     try {
    //         int op1 = Integer.parseInt(operand1);
    //         int op2 = Integer.parseInt(operand2);
    //         return op1 + op2;
    //     } catch (Exception e) {
    //         //return null;
    //     }

    //     try {
    //         String op1 = (String) operand1;
    //         String op2 = (String) operand2;
    //         return op1 + op2;
    //     } catch (Exception e) {
    //         //return null;
    //     }

    //     return null;

    // }

}

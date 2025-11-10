package com.akto.test_editor.execution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;

import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.KeyTypes;
import com.akto.dto.type.RequestTemplate;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.test_editor.Utils;
import com.akto.util.modifier.AddJWKModifier;
import com.akto.util.modifier.AddJkuJWTModifier;
import com.akto.util.modifier.AddKidParamModifier;
import com.akto.util.modifier.InvalidSignatureJWTModifier;
import com.akto.util.modifier.JwtKvModifier;
import com.akto.util.modifier.NoneAlgoJWTModifier;
import com.mongodb.BasicDBObject;
import static com.akto.runtime.parser.SampleParser.parseSampleMessage;

public class VariableResolver {
    
    private static final DataActor dataActor = DataActorFactory.fetchInstance();
    private static final ConcurrentHashMap<ApiInfoKey, SampleData> sampleDataCache = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 1000;

    public static Object getValue(Map<String, Object> varMap, String key) {
        if (!varMap.containsKey(key)) {
            return null;
        }
        Object obj = varMap.get(key);
        return obj;
    }

    public static List<Object> resolveExpression(Map<String, Object> varMap, Object key) {

        Object keyContext = null;
        List<Object> varList = new ArrayList<>();
        if (key instanceof String) {
            keyContext = VariableResolver.resolveContextKey(varMap, key.toString());
        }

        if (keyContext instanceof ArrayList) {
            return (List) keyContext;
        }

        if (key instanceof String) {
            keyContext = VariableResolver.resolveContextVariable(varMap, key.toString());
        }

        if (keyContext instanceof ArrayList) {
            return (List) keyContext;
        }

        if (key instanceof String && VariableResolver.isWordListVariable(key, varMap)) {
            varList = (List) VariableResolver.resolveWordListVar(key.toString(), varMap);
            for (int i = 0; i < varList.size(); i++) {
                List<Object> vals = VariableResolver.resolveExpression(varMap, varList.get(i).toString());
                Object wordObject = vals.get(0).toString();
                try {
                    wordObject = convertStringToNumber(wordObject.toString());
                } catch (Exception e) {
                }
                varList.set(i, wordObject);
            }
            return varList;
        }

        if (key instanceof String) {
            key = VariableResolver.resolveExpression(varMap, key.toString());
            if (key instanceof String) {
                varList.add(key.toString());
                return varList;
            } else if (key instanceof ArrayList) {
                return (List) key;
            }
        } else if (key instanceof Map) {
            varList.add(key);
            return varList;
        } else if (key instanceof ArrayList) {
            List<Object> keyList = (List) key;
            int index = 0;
            for (Object k: keyList) {
                List<Object> v = VariableResolver.resolveExpression(varMap, k);
                if (v != null && v.size() > 0) {
                    keyList.set(index, v.get(0).toString());
                }
                index++;
            }
        } else {
            varList.add(key);
            return varList;
        }

        varList.add(key);
        return varList;

    }

    public static List<Object> resolveExpression(Map<String, Object> varMap, String expression) {

        Pattern pattern = Pattern.compile("\\$\\{[^}]*\\}");
        Matcher matcher = pattern.matcher(expression);

        List<Object> expressionList = new ArrayList<>();
        expressionList.add(expression);

        int index = 0;

        while (index < expressionList.size()) {
            while (matcher.find()) {
                String param = expressionList.get(index).toString();
                try {
                    String match = matcher.group(0);
                    match = match.substring(2, match.length());
                    match = match.substring(0, match.length() - 1);

                    Object val = getValue(varMap, match);
                    if (val == null) {
                        continue;
                    }
                    if (val instanceof ArrayList){ 
                        List<Object> valList = (List) val;
                        if (valList.size() == 1) {
                            val = valList.get(0);
                        }
                    }
                    if (!(val instanceof ArrayList)) {
                        for (int i = 0; i < expressionList.size(); i++) {
                            param = expressionList.get(i).toString();

                            String finalVal = (val instanceof String) ? ((String) val) : val.toString();
                            expressionList.set(i, param.replaceFirst("(\\$\\{[^}]*\\})", Matcher.quoteReplacement(finalVal)));
                        }
                    } else {
                        expressionList.remove(index);
                        List<Object> valList = (List) val;
                        for (int i = expressionList.size(); i < valList.size(); i++) {
                            String finalVal = (valList.get(i) instanceof String) ? ((String) valList.get(i)) : valList.get(i).toString();
                            Object v = param.replaceFirst("(\\$\\{[^}]*\\})", Matcher.quoteReplacement(finalVal));
                            expressionList.add(v);
                        }
                    }
                    
                } catch (Exception e) {
                    return expressionList;
                }
            }
            index++;
        }

        for (int i = 0; i < expressionList.size(); i++) {
            Object val = resolveExpression(expressionList.get(i).toString());
            if (val == null) {
                val = getValue(varMap, expression);
                if (val != null) {
                    expressionList.set(i, val);
                }
            } else {
                expressionList.set(i, val);
            }
        }

        return expressionList;
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
        String expression = "";
        if (!(val instanceof String)) {
            if (val instanceof Map) {
                Map<String, Object> valMap = (Map) val;
                if (valMap.size() != 1) return false;

                expression = valMap.keySet().iterator().next();
                
            } else {
                return false;                
            }

        } else {
            expression = val.toString();
        }

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
                    || secondParam.equalsIgnoreCase("jku_added_token") || secondParam.startsWith("modify_jwt")
                    || secondParam.equalsIgnoreCase("jwk_added_token") || secondParam.equalsIgnoreCase("kid_added_token")) {
                        return true;
                }
            } catch (Exception e) {
                return false;
            }
        }

        return false;

    }

    public static String resolveAuthContext(Object resolveObj, Map<String, List<String>> headers, String headerKey) {

        String origExpression = null;
        if (!(resolveObj instanceof String)) {
            if (resolveObj instanceof Map) {
                Map<String, Object> resolveMap = (Map) resolveObj;
                if (resolveMap.size() != 1) return null;

                origExpression = resolveMap.keySet().iterator().next();
                
            } else {
                return null;
            }

        } else {
            origExpression = resolveObj.toString();
        }
        
        String expression = origExpression;
        expression = expression.substring(2, expression.length());
        expression = expression.substring(0, expression.length() - 1);

        String authContextConstant = "auth_context.";
        String secondParam = expression.substring(authContextConstant.length());// params[1];

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
            } else if (secondParam.equalsIgnoreCase("modify_jwt")) {
                try {
                    Map<String, Object> kvPairMap = (Map) ((Map)resolveObj).get(origExpression);
                    String kvKey = kvPairMap.keySet().iterator().next();
                    JwtKvModifier jwtKvModifier = new JwtKvModifier(kvKey, kvPairMap.get(kvKey).toString());
                    modifiedHeaderVal = jwtKvModifier.jwtModify("", val);
                } catch (Exception e) {
                    return null;
                }
            } else if (secondParam.equalsIgnoreCase("jwk_added_token")) {
                AddJWKModifier addJWKModifier = new AddJWKModifier();
                try {
                    modifiedHeaderVal = addJWKModifier.jwtModify("", val);
                } catch(Exception e) {
                    return null;
                }
            } else if (secondParam.equalsIgnoreCase("kid_added_token")) {
                AddKidParamModifier addKidParamModifier = new AddKidParamModifier();
                try {
                    modifiedHeaderVal = addKidParamModifier.jwtModify("", val);
                } catch(Exception e) {
                    return null;
                }
            } 
            finalValue.add(modifiedHeaderVal);
        }

        return finalValue.isEmpty() ? null : String.join( " ", finalValue);
    }

    public static String resolveAuthContextForPayload(Object resolveObj, String payload, String headerKey) {

        String origExpression = null;
        if (!(resolveObj instanceof String)) {
            if (resolveObj instanceof Map) {
                Map<String, Object> resolveMap = (Map) resolveObj;
                if (resolveMap.size() != 1) return null;

                origExpression = resolveMap.keySet().iterator().next();

            } else {
                return null;
            }

        } else {
            origExpression = resolveObj.toString();
        }

        String expression = origExpression;
        expression = expression.substring(2, expression.length());
        expression = expression.substring(0, expression.length() - 1);

        String authContextConstant = "auth_context.";
        String secondParam = expression.substring(authContextConstant.length());// params[1];

        BasicDBObject payloadObj = new BasicDBObject();
        if (payload != null && payload.startsWith("[")) {
            payload = "{\"json\": "+payload+"}";
        }
        try {
            payloadObj = BasicDBObject.parse(payload);
        } catch (Exception e) {
        }

        if (!payloadObj.containsKey(headerKey)) {
            return null;
        }

        String headerVal = payloadObj.getString(headerKey);

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
            } else if (secondParam.equalsIgnoreCase("modify_jwt")) {
                try {
                    Map<String, Object> kvPairMap = (Map) ((Map)resolveObj).get(origExpression);
                    String kvKey = kvPairMap.keySet().iterator().next();
                    JwtKvModifier jwtKvModifier = new JwtKvModifier(kvKey, kvPairMap.get(kvKey).toString());
                    modifiedHeaderVal = jwtKvModifier.jwtModify("", val);
                } catch (Exception e) {
                    return null;
                }
            } else if (secondParam.equalsIgnoreCase("jwk_added_token")) {
                AddJWKModifier addJWKModifier = new AddJWKModifier();
                try {
                    modifiedHeaderVal = addJWKModifier.jwtModify("", val);
                } catch(Exception e) {
                    return null;
                }
            } else if (secondParam.equalsIgnoreCase("kid_added_token")) {
                AddKidParamModifier addKidParamModifier = new AddKidParamModifier();
                try {
                    modifiedHeaderVal = addKidParamModifier.jwtModify("", val);
                } catch(Exception e) {
                    return null;
                }
            } 
            finalValue.add(modifiedHeaderVal);
        }

        return finalValue.isEmpty() ? null : String.join( " ", finalValue);
    }

    public static Boolean isWordListVariable(Object key, Map<String, Object> varMap) {
        if (key == null) {
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
        List<String> result = new ArrayList<>();
        result.add(expression);
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
                    List<String> tempResult = new ArrayList<>();
                    for (String temp : result) {
                        for (Object word : wordList) {
                            // TODO: handle case to use numbers as well.
                            String tempWord = temp.replace(wordListKey, word.toString());
                            expression = tempWord;
                            tempResult.add(tempWord);
                        }
                    }
                    result = tempResult;
                    matcher = pattern.matcher(expression);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    private static Number convertStringToNumber(String str) {
        if (str == null || str.trim().isEmpty()) {
            throw new IllegalArgumentException("Input string cannot be null or empty");
        }
        
        try {
            // Check if the string is a valid integer or long
            // 2^64 => ~ 1e19, so if length is more than 30, it can be a long or double
            if (str.length() > 30) {
                throw new IllegalArgumentException("Cannot convert to a valid number: " + str);
            }
        } catch (NumberFormatException e) {
            // Ignore and try parsing as long or double
        }

        try {
            // Try parsing as Integer
            return Integer.parseInt(str);
        } catch (NumberFormatException ignored) {}

        try {
            // Try parsing as Long
            return Long.parseLong(str);
        } catch (NumberFormatException ignored) {}

        try {
            // Try parsing as Double
            Double temp = Double.parseDouble(str);
            // Edge cases for double.
            if (str.length() <= 23) {
                return temp;
            }
        } catch (NumberFormatException ignored) {}

        throw new IllegalArgumentException("Cannot convert to a valid number: " + str);
    }

    public static Map<String, List<String>> resolveWordList(Map<String, List<String>> wordListsMap, ApiInfo.ApiInfoKey infoKey, Map<ApiInfo.ApiInfoKey, List<String>> newSampleDataMap) {

        for (String k: wordListsMap.keySet()) {

            if (k.contains("${") && k.contains("}")) {
                continue;
            }
            Map<String, String> m = new HashMap<>();
            Object keyObj;
            String key, location;
            boolean isRegex = false;
            boolean allApis = false;
            try {
                List<String> wordList = (List<String>) wordListsMap.get(k);
                continue;
            } catch (Exception e) {
                try {
                    m = (Map) wordListsMap.get(k);
                } catch (Exception er) {
                    continue;
                }
            }

            keyObj = m.get("key");
            location = m.get("location");
            if (keyObj instanceof Map) {
                Map<String, String> kMap = (Map) keyObj;
                key = (String) kMap.get("regex");
                isRegex = true;
            } else {
                key = (String) m.get("key");
            }

            
            if (m.containsKey("all_apis")) {
                allApis = Objects.equals(m.get("all_apis"), true);
            }
            if (!allApis) {
                continue;
            }

            List<SingleTypeInfo> singleTypeInfos = dataActor.fetchMatchParamSti(infoKey.getApiCollectionId(), key);
            for (SingleTypeInfo singleTypeInfo: singleTypeInfos) {
                ApiInfo.ApiInfoKey infKey = new ApiInfo.ApiInfoKey(infoKey.getApiCollectionId(), singleTypeInfo.getUrl(), URLMethods.Method.fromString(singleTypeInfo.getMethod()));
                if (infKey.equals(infoKey)) {
                    continue;
                }
                SampleData sd = dataActor.fetchSampleDataByIdMethod(infoKey.getApiCollectionId(), singleTypeInfo.getUrl(), singleTypeInfo.getMethod());
                newSampleDataMap.put(infKey, sd.getSamples());

            }
            List<String> wordListVal = VariableResolver.fetchWordList(newSampleDataMap, key, location, isRegex);
            wordListsMap.put(k, wordListVal);
        }

        return wordListsMap;

    }

    public static Map<String, Object> resolveDynamicWordList(Map<String, Object> varMap, ApiInfo.ApiInfoKey apiInfoKey, Map<ApiInfo.ApiInfoKey, List<String>> newSampleDataMap) {

        Map<String, Object> updatedVarMap = new HashMap<>();

        for (String k: varMap.keySet()) {
            if (!k.contains("wordList_") || !(k.startsWith("wordList_${"))) {
                updatedVarMap.put(k, varMap.get(k));
                continue;
            }
            Object kObj = k.substring(9);
            List<Object> keyList = VariableResolver.resolveExpression(varMap, kObj);
            for (Object iteratorKey: keyList) {
                newSampleDataMap = new HashMap<>();
                Map<String, Object> m = (Map) varMap.get(k);
                Map<String, Object> loopMap = (Map) m.get("for_all");
                Map.Entry<String,Object> entry = loopMap.entrySet().iterator().next();
                String mapKey = entry.getKey().replace("${iteratorKey}", iteratorKey.toString());
                Map<String, Object> mapValue = (Map) entry.getValue();
                Object key = mapValue.get("key");
                String location = null;
                boolean isRegex = false;
                boolean allApis = false;
                if (key instanceof Map) {
                    Map<String, String> kMap = (Map) key;
                    key = (String) kMap.get("regex");
                    key =  key.toString().replace("${iteratorKey}", iteratorKey.toString());
                    isRegex = true;
                } else {
                    key = (String) mapValue.get("key");
                    key = key.toString().replace("${iteratorKey}", iteratorKey.toString());
                }
            
                if (mapValue.get("location") != null) {
                    location = mapValue.get("location").toString();
                }
            
                if (mapValue.containsKey("all_apis")) {
                    allApis = Objects.equals(m.get("all_apis"), true);
                }
                List<SingleTypeInfo> singleTypeInfos = dataActor.fetchMatchParamSti(apiInfoKey.getApiCollectionId(), key.toString());

                for (SingleTypeInfo singleTypeInfo: singleTypeInfos) {
                    ApiInfo.ApiInfoKey infKey = new ApiInfo.ApiInfoKey(apiInfoKey.getApiCollectionId(), singleTypeInfo.getUrl(), URLMethods.Method.fromString(singleTypeInfo.getMethod()));
                    if (!allApis && !infKey.equals(apiInfoKey)) {
                        continue;
                    }

                    SampleData sd = getCachedSampleData(apiInfoKey.getApiCollectionId(), singleTypeInfo.getUrl(), singleTypeInfo.getMethod());
                    if (sd != null) {
                        newSampleDataMap.put(infKey, sd.getSamples());
                    }
                }
                
                List<String> wordListVal = VariableResolver.fetchWordList(newSampleDataMap, key.toString(), location, isRegex);
                updatedVarMap.put(mapKey, wordListVal);

            }

        }

        return updatedVarMap;

    }


    public static void resolveWordList(Map<String, Object> varMap, Map<ApiInfoKey, List<String>> sampleDataMap, ApiInfo.ApiInfoKey apiInfoKey) {

        for (String k: varMap.keySet()) {
            if (!k.contains("wordList_") || (k.contains("${") && k.contains("}"))) {
                continue;
            }
            Map<String, String> m = new HashMap<>();
            Object keyObj;
            String key, location;
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
                    List<String> sample = sampleDataMap.get(infoKey);
                    if (infoKey.getApiCollectionId() != apiInfoKey.getApiCollectionId()) {
                        continue;
                    }
                    // if (infoKey.equals(apiInfoKey)) {
                    //     sample.remove(0);
                    // }
                    modifiedSampleDataMap.put(infoKey, sample);
                }
            } else {
                modifiedSampleDataMap.put(apiInfoKey, sampleDataMap.get(apiInfoKey));
            }

            List<String> wordListVal = fetchWordList(modifiedSampleDataMap, key, location, isRegex);
            varMap.put(k, wordListVal);
        }

    }

    public static List<String> fetchWordList(Map<ApiInfoKey, List<String>> modifiedSampleDataMap, String key, String location, boolean isRegex) {
        Set<String> wordListSet = new HashSet<>();
        List<String> wordListVal = new ArrayList<>();
        for (ApiInfoKey infoKey: modifiedSampleDataMap.keySet()) {
            List<String> samples = modifiedSampleDataMap.get(infoKey);
            wordListSet.addAll(extractValuesFromSampleData(samples, key, location, isRegex));
        }
        for (String s : wordListSet) {
            wordListVal.add(s);
            if (wordListVal.size() >= 10 && !"terminal_keys".equals(location)) {
                break;
            }
        }
        return wordListVal;
    }

    public static Set<String> extractValuesFromSampleData(List<String> samples, String key, String location, boolean isRegex) {

        Set<String> worklistVal = new HashSet<>();
        for (String sample: samples) {
            HttpResponseParams httpResponseParams;
            HttpRequestParams httpRequestParams;
            try {
                httpResponseParams = parseSampleMessage(sample);
                httpRequestParams = httpResponseParams.getRequestParams();

                if ("terminal_keys".equals(location)) {
                    worklistVal.addAll(Utils.findAllTerminalKeys(httpResponseParams.getPayload(), key));
                    continue;
                }
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

    public static Object resolveExpression(String expression) {

        Object val = null;

        Pattern pattern = Pattern.compile("(\\S+)\\s?([\\*/])\\s?(\\S+)");
        Matcher matcher = pattern.matcher(expression);

        if (matcher.find()) {
            try {
                String operand1 = (String) matcher.group(1);
                String operator = (String) matcher.group(2);
                String operand2 = (String) matcher.group(3);
                val = evaluateExpressionValue(operand1, operator, operand2);

            } catch(Exception e) {
                return expression;
            }
            
        }

        return val;

    }

    public static Object evaluateExpressionValue(String operand1, String operator, String operand2) {

        switch(operator) {
            // case "+":
            //     add(operand1, operator, operand2);
            // case "-":
            //     subtract(operand1, operator, operand2);
            case "*":
                return multiply(operand1, operand2);
            // case "/":
            //     divide(operand1, operator, operand2);
            default:
                return null;
        }

    }

    public static Object multiply(String operand1, String operand2) {
        try {
            try {
                int op1 = Integer.parseInt(operand1);
                try {
                    int op2 = Integer.parseInt(operand2);
                    return op1 * op2;
                } catch (Exception e) {
                    Double op2 = Double.parseDouble(operand2);
                    return op1 * op2;
                }
            } catch (Exception e) {
                Double op1 = Double.parseDouble(operand1);
                try {
                    int op2 = Integer.parseInt(operand2);
                    return op1 * op2;
                } catch (Exception exc) {
                    Double op2 = Double.parseDouble(operand2);
                    return op1 * op2;
                }
            }
        } catch (Exception e) {
            return null;
        }

    }

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

    private static SampleData getCachedSampleData(int apiCollectionId, String url, String method) {
        ApiInfoKey cacheKey = new ApiInfoKey(apiCollectionId, url, URLMethods.Method.fromString(method));
        
        // Try to get from cache first
        SampleData cachedData = sampleDataCache.get(cacheKey);
        if (cachedData != null) {
            return cachedData;
        }

        // If cache is too large, remove some entries
        if (sampleDataCache.size() >= MAX_CACHE_SIZE) {
            int entriesToRemove = MAX_CACHE_SIZE / 10;
            Iterator<ApiInfoKey> iterator = sampleDataCache.keySet().iterator();
            for (int i = 0; i < entriesToRemove && iterator.hasNext(); i++) {
                iterator.next();
                iterator.remove();
            }
        }

        SampleData sd = dataActor.fetchSampleDataByIdMethod(apiCollectionId, url, method);
        if (sd != null) {
            sampleDataCache.put(cacheKey, sd);
        }
        return sd;
    }

    public static void clearSampleDataCache() {
        sampleDataCache.clear();
    }

}
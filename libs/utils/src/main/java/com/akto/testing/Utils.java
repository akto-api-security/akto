package com.akto.testing;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.bson.types.ObjectId;
import org.springframework.util.StringUtils;

import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dao.context.Context;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.RawApi;
import com.akto.dto.CollectionConditions.ConditionsType;
import com.akto.dto.test_editor.DataOperandsFilterResponse;
import com.akto.dto.test_editor.FilterNode;
import com.akto.dto.test_editor.Util;
import com.akto.dto.testing.GenericTestResult;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestResult.Confidence;
import com.akto.dto.testing.TestResult.TestError;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.UrlModifierPayload;
import com.akto.dto.testing.WorkflowUpdatedSampleData;
import com.akto.dto.type.RequestTemplate;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.test_editor.filter.Filter;
import com.akto.test_editor.filter.data_operands_impl.ValidationResult;
import com.akto.testing_db_layer_client.ClientLayer;
import com.akto.util.JSONUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

import okhttp3.MediaType;

import static com.akto.runtime.RuntimeUtil.extractAllValuesFromPayload;
import static com.akto.test_editor.Utils.deleteKeyFromPayload;
import static com.akto.test_editor.execution.Operations.deleteCookie;
import static com.akto.test_editor.execution.Operations.modifyCookie;

public class Utils {

    private static final LoggerMaker loggerMaker = new LoggerMaker(Utils.class, LogDb.TESTING);
    private static final ClientLayer clientLayer = new ClientLayer();
    private static final boolean shouldCallClientLayerForSampleData = System.getenv("TESTING_DB_LAYER_SERVICE_URL") != null && !System.getenv("TESTING_DB_LAYER_SERVICE_URL").isEmpty();

    public static void populateValuesMap(Map<String, Object> valuesMap, String payloadStr, String nodeId, Map<String,
            List<String>> headers, boolean isRequest, String queryParams) {
        boolean isList = false;
        String reqOrResp = isRequest ? "request"  : "response";

        if (payloadStr == null) payloadStr = "{}";
        if (payloadStr.startsWith("[")) {
            payloadStr = "{\"json\": "+payloadStr+"}";
            isList = true;
        }

        String fullBodyKey = nodeId + "." + reqOrResp + "." + "body";

        valuesMap.put(fullBodyKey, payloadStr);

        BasicDBObject payloadObj;
        try {
            payloadObj = BasicDBObject.parse(payloadStr);
        } catch (Exception e) {
            boolean isPostFormData = payloadStr.contains("&") && payloadStr.contains("=");
            if (isPostFormData) {
                String mockUrl = "url?"+ payloadStr; // because getQueryJSON function needs complete url
                payloadObj = RequestTemplate.getQueryJSON(mockUrl);
            } else {
                payloadObj = BasicDBObject.parse("{}");
            }
        }

        BasicDBObject queryParamsObject = null;
        if (queryParams != null) {
            try {
                String mockUrl = "url?"+ queryParams; // because getQueryJSON function needs complete url
                queryParamsObject = RequestTemplate.getQueryJSON(mockUrl);
            } catch (Exception e) {
                ;
            }
        }

        Object obj;
        if (isList) {
            obj = payloadObj.get("json");
        } else {
            obj = payloadObj;
        }

        BasicDBObject flattened = JSONUtils.flattenWithDots(obj);


        for (String param: flattened.keySet()) {
            String key = nodeId + "." + reqOrResp + "." + "body" + "." + param;
            valuesMap.put(key, flattened.get(param));
        }

        if (queryParamsObject != null) {
            BasicDBObject queryFlattened = JSONUtils.flattenWithDots(queryParamsObject);
            for (String param: queryFlattened.keySet()) {
                String key = nodeId + "." + reqOrResp + "." + "query" + "." + param;
                valuesMap.put(key, queryFlattened.get(param));
            }
        }

        for (String headerName: headers.keySet()) {
            List<String> headerValues = headers.get(headerName);
            String key = nodeId + "." + reqOrResp + "." + "header" + "." + headerName;

            switch (headerValues.size()) {
                case 0: 
                    continue;
                case 1: 
                    valuesMap.put(key, headerValues.get(0));
                    continue;
                default: 
                    String val =  String.join(";", headers.get(headerName));
                    valuesMap.put(key, val);
            }

            if (headerName.equalsIgnoreCase("cookie") || headerName.equalsIgnoreCase("set-cookie")) {
                for (String headerValue : headerValues) {
                    if (headerValue == null || headerValue.isEmpty()) {
                        continue;
                    }

                    // Parse cookies from the header value
                    // Cookie format: "key1=value1; key2=value2"
                    // Set-Cookie format: "key=value; Path=/; HttpOnly" (may have attributes)
                    String[] cookiePairs = headerValue.split(";");
                    for (String cookiePair : cookiePairs) {
                        cookiePair = cookiePair.trim();
                        int equalsIndex = cookiePair.indexOf('=');
                        if (equalsIndex > 0) {
                            String cookieKey = cookiePair.substring(0, equalsIndex).trim();
                            String cookieValue = cookiePair.substring(equalsIndex + 1).trim();

                            // Skip Set-Cookie attributes (Path, Domain, Secure, HttpOnly, SameSite, Max-Age, Expires)
                            if (headerName.equalsIgnoreCase("set-cookie")) {
                                if (cookieKey.equalsIgnoreCase("Path") ||
                                    cookieKey.equalsIgnoreCase("Domain") ||
                                    cookieKey.equalsIgnoreCase("Secure") ||
                                    cookieKey.equalsIgnoreCase("HttpOnly") ||
                                    cookieKey.equalsIgnoreCase("SameSite") ||
                                    cookieKey.equalsIgnoreCase("Max-Age") ||
                                    cookieKey.equalsIgnoreCase("Expires")) {
                                    continue;
                                }
                            }

                            // Store individual cookie key-value pair
                            String cookieMapKey = nodeId + "." + reqOrResp + "." + "header" + "." + headerName + "." + cookieKey;
                            valuesMap.put(cookieMapKey, cookieValue);
                        }
                    }
                }
            }

        }
    }

    public static OriginalHttpRequest buildHttpRequest(WorkflowUpdatedSampleData updatedSampleData, Map<String, Object> valuesMap) throws Exception {

        String sampleData = updatedSampleData.getOrig();
        OriginalHttpRequest request = new OriginalHttpRequest();
        request.buildFromSampleMessage(sampleData);

        String queryParams = updatedSampleData.getQueryParams();
        String requestHeaders = updatedSampleData.getRequestHeaders();
        String requestPayload = updatedSampleData.getRequestPayload();
        String requestUrl = updatedSampleData.getRequestUrl();

        String queryFromReplacedUrl = null;

        boolean userSuppliedQueryParamsNullOrEmpty = queryParams == null || queryParams.trim().length() == 0;
        if (requestUrl != null) {
            loggerMaker.infoAndAddToDb("requestUrl: " + requestUrl);
            String rawUrl = executeCode(requestUrl, valuesMap);
            loggerMaker.infoAndAddToDb("rawUrl: " + requestUrl);
            // this url might contain urlQueryParams. We need to move it queryParams
            String[] rawUrlArr = rawUrl.split("\\?");
            request.setUrl(rawUrlArr[0]);
            if (rawUrlArr.length > 1) {
                queryFromReplacedUrl = rawUrlArr[1];
            }
            loggerMaker.infoAndAddToDb("final url: " + request.getUrl());
            loggerMaker.infoAndAddToDb("queryFromReplacedUrl: " + queryFromReplacedUrl);
        }

        if (userSuppliedQueryParamsNullOrEmpty) {
            loggerMaker.infoAndAddToDb("setting null");
            request.setQueryParams(null);
        }

        if (requestPayload != null) {
            String finalPayload =  executeCode(requestPayload, valuesMap);
            request.setBody(finalPayload);
        }

        if (requestHeaders != null) {
            String finalPayload = executeCode(requestHeaders, valuesMap);
            Map<String, List<String>> res = OriginalHttpRequest.buildHeadersMap(finalPayload);
            request.setHeaders(res);
        }

        boolean queryFromReplacedUrlNullOrEmpty = queryFromReplacedUrl == null || queryFromReplacedUrl.trim().isEmpty();

        if (!userSuppliedQueryParamsNullOrEmpty) {
            loggerMaker.infoAndAddToDb("user has supplied query params");
            String finalQueryParams = executeCode(queryParams, valuesMap);
            loggerMaker.infoAndAddToDb("finalQueryParams: " + finalQueryParams);
            if (queryFromReplacedUrlNullOrEmpty) {
                request.setQueryParams(finalQueryParams);
            } else {
                // combine original query params and user defined query params and latter overriding former
                String combinedQueryParams = OriginalHttpRequest.combineQueryParams(queryFromReplacedUrl, finalQueryParams);
                loggerMaker.infoAndAddToDb("combinedQueryParams: " + combinedQueryParams);
                request.setQueryParams(combinedQueryParams);
            }
        } else if (!queryFromReplacedUrlNullOrEmpty) {
            request.setQueryParams(queryFromReplacedUrl);
        }

        return request;
    }

    private static final ScriptEngineManager factory = new ScriptEngineManager();

    public static String executeCode(String ogPayload, Map<String, Object> valuesMap) throws Exception {
        String variablesReplacedPayload = replaceVariables(ogPayload,valuesMap, true);

        String regex = "\\#\\[(.*?)]#";
        Pattern p = Pattern.compile(regex);
        Matcher matcher = p.matcher(variablesReplacedPayload);
        StringBuffer sb = new StringBuffer();

        // create a Nashorn script engine
        ScriptEngine engine = factory.getEngineByName("nashorn");

        while (matcher.find()) {
            String code = matcher.group(1);
            code = code.trim();
            if (!code.endsWith(";")) code = code+";";
            try {
                Object val = engine.eval(code);
                matcher.appendReplacement(sb, val.toString());
            } catch (final ScriptException se) {
            }

        }

        matcher.appendTail(sb); 
        return sb.toString();
    }


    public static String replaceVariables(String payload, Map<String, Object> valuesMap, boolean escapeString) throws Exception {
        String regex = "\\$\\{((x|step)\\d+\\.[\\w\\-\\[\\].]+|AKTO\\.changes_info\\..*?)\\}"; 
        Pattern p = Pattern.compile(regex);

        // replace with values
        Matcher matcher = p.matcher(payload);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            if (key == null) continue;
            Object obj = valuesMap.get(key);
            if (obj == null) {
                if (key.toLowerCase().startsWith("x0.unique_")) {
                    String suffix = key.substring(key.toLowerCase().indexOf("_")+1);
                    obj = suffix+"_"+System.nanoTime();
                } else {
                    loggerMaker.errorAndAddToDb("couldn't find: " + key);
                    throw new Exception("Couldn't find " + key);
                }
            }
            String val = obj.toString();
            if (escapeString) {
                val = val.replace("\\", "\\\\")
                        .replace("\t", "\\t")
                        .replace("\b", "\\b")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                        .replace("\f", "\\f")
                        .replace("\'", "\\'")
                        .replace("\"", "\\\"");
            }
            matcher.appendReplacement(sb, "");
            sb.append(val);
        }

        matcher.appendTail(sb);

        return sb.toString();
    }

    public static boolean validateTest(String testValidatorCode, Map<String, Object> valuesMap) {
        if (testValidatorCode == null) return false;
        testValidatorCode = testValidatorCode.trim();

        boolean vulnerable = false;
        if (testValidatorCode.length() == 0) return false;

        ScriptEngine engine = factory.getEngineByName("nashorn");
        try {
            String code = replaceVariables(testValidatorCode, valuesMap, true);
            loggerMaker.infoAndAddToDb("*******************************************************************");
            loggerMaker.infoAndAddToDb("TEST VALIDATOR CODE:");
            loggerMaker.infoAndAddToDb(code);
            Object o = engine.eval(code);
            loggerMaker.infoAndAddToDb("TEST VALIDATOR RESULT: " + o.toString());
            loggerMaker.infoAndAddToDb("*******************************************************************");
            vulnerable = ! (boolean) o;
        } catch (Exception e) {
            ;
        }

        return vulnerable;
    }

    public static MediaType getMediaType(String fileUrl) {
        String fileExtension = "";

        int dotIndex = fileUrl.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileUrl.length() - 1) {
            fileExtension = fileUrl.substring(dotIndex + 1).toLowerCase();
        }
        switch (fileExtension) {
            case "jpg":
            case "jpeg":
                return MediaType.parse("image/jpeg");
            case "png":
                return MediaType.parse("image/png");
            case "gif":
                return MediaType.parse("image/gif");
            case "txt":
                return MediaType.parse("text/plain");
            case "html":
                return MediaType.parse("text/html");
            case "pdf":
                return MediaType.parse("application/pdf");
            case "doc":
            case "docx":
                return MediaType.parse("application/msword");
            case "xls":
            case "xlsx":
                return MediaType.parse("application/vnd.ms-excel");
            case "json":
                return MediaType.parse("application/json");
            default:
                return MediaType.parse("application/octet-stream"); // Fallback for unknown types
        }
    }


    public static double compareWithOriginalResponse(String originalPayload, String currentPayload, Map<String, Boolean> comparisonExcludedKeys) {
        if (originalPayload == null && currentPayload == null) return 100;
        if (originalPayload == null || currentPayload == null) return 0;

        String trimmedOriginalPayload = originalPayload.trim();
        String trimmedCurrentPayload = currentPayload.trim();
        if (trimmedCurrentPayload.equals(trimmedOriginalPayload)) return 100;

        Map<String, Set<String>> originalResponseParamMap = new HashMap<>();
        Map<String, Set<String>> currentResponseParamMap = new HashMap<>();
        try {
            extractAllValuesFromPayload(originalPayload, originalResponseParamMap);
            extractAllValuesFromPayload(currentPayload, currentResponseParamMap);
        } catch (Exception e) {
            return 0.0;
        }

        if (originalResponseParamMap.keySet().size() == 0 && currentResponseParamMap.keySet().size() == 0) {
            return 100.0;
        }

        Set<String> visited = new HashSet<>();
        int matched = 0;
        for (String k1: originalResponseParamMap.keySet()) {
            if (visited.contains(k1) || comparisonExcludedKeys.containsKey(k1)) continue;
            visited.add(k1);
            Set<String> v1 = originalResponseParamMap.get(k1);
            Set<String> v2 = currentResponseParamMap.get(k1);
            if (Objects.equals(v1, v2)) matched +=1;
        }

        for (String k1: currentResponseParamMap.keySet()) {
            if (visited.contains(k1) || comparisonExcludedKeys.containsKey(k1)) continue;
            visited.add(k1);
            Set<String> v1 = originalResponseParamMap.get(k1);
            Set<String> v2 = currentResponseParamMap.get(k1);
            if (Objects.equals(v1, v2)) matched +=1;
        }

        int visitedSize = visited.size();
        if (visitedSize == 0) return 0.0;

        double result = (100.0*matched)/visitedSize;

        if (Double.isFinite(result)) {
            return result;
        } else {
            return 0.0;
        }

    }

    public static ValidationResult validateFilter(FilterNode filterNode, RawApi rawApi, ApiInfoKey apiInfoKey, Map<String, Object> varMap, String logId) {
        if (filterNode == null) return new ValidationResult(true, "");
        if (rawApi == null) return  new ValidationResult(true, "raw api is null");
        return validate(filterNode, rawApi, null, apiInfoKey,"filter", varMap, logId);
    }

    private static ValidationResult validate(FilterNode node, RawApi rawApi, RawApi testRawApi, ApiInfoKey apiInfoKey, String context, Map<String, Object> varMap, String logId) {
        Filter filter = new Filter();
        DataOperandsFilterResponse dataOperandsFilterResponse = filter.isEndpointValid(node, rawApi, testRawApi, apiInfoKey, null, null , false,context, varMap, logId, false);
        return new ValidationResult(dataOperandsFilterResponse.getResult(), dataOperandsFilterResponse.getValidationReason());
    }

    public static void modifyBodyOperations(OriginalHttpRequest httpRequest, List<ConditionsType> modifyOperations, List<ConditionsType> addOperations, List<ConditionsType> deleteOperations){
        String oldReqBody = httpRequest.getBody();
        if(oldReqBody == null || oldReqBody.isEmpty()){
            return ;
        }
        BasicDBObject payload;

        if (oldReqBody != null && oldReqBody.startsWith("[")) {
            oldReqBody = "{\"json\": "+oldReqBody+"}";
        }
        try {
            payload = BasicDBObject.parse(oldReqBody);
        } catch (Exception e) {
            payload = new BasicDBObject();
        }

        if(!modifyOperations.isEmpty()){
            for(ConditionsType condition : modifyOperations){
                Object value = condition.getValue();
                try {
                    value = replaceVariables(value.toString(), new HashMap<>(), false);
                } catch (Exception e) {
                    ;
                }
                Util.modifyValueInPayload(payload, null, condition.getKey(), value);
            }
        }
        if(!addOperations.isEmpty()){
            for(ConditionsType condition : addOperations){
                payload.put(condition.getKey(), condition.getValue());
            }
        }

        if(!deleteOperations.isEmpty()){
            for(ConditionsType condition : deleteOperations){
                deleteKeyFromPayload(payload, null, condition.getKey());
            }
        }

        String payloadStr = payload.toJson();

        if (payload.size() == 1 && payload.containsKey("json")) {
            Object jsonValue = payload.get("json");
            if (jsonValue instanceof BasicDBList) {
                payloadStr = payload.get("json").toString();
            }
        }


        httpRequest.setBody(payloadStr);
    }

    public static void modifyHeaderOperations(OriginalHttpRequest httpRequest, List<ConditionsType> modifyOperations, List<ConditionsType> addOperations, List<ConditionsType> deleteOperations){
        Map<String, List<String>> reqHeaders = httpRequest.getHeaders();

        if(!addOperations.isEmpty()){
            for(ConditionsType condition : addOperations){
                List<String> valList = Collections.singletonList(condition.getValue());
                reqHeaders.put(condition.getKey(), valList);
            }
        }

        if(!deleteOperations.isEmpty()){
            for(ConditionsType condition : deleteOperations){
                String key = condition.getKey();
                deleteCookie(reqHeaders, key, null);
                if (reqHeaders.containsKey(key)) {
                    reqHeaders.remove(key);
                }
            }
        }

        if(!modifyOperations.isEmpty()){
            for(ConditionsType condition : modifyOperations){
                String key = condition.getKey();
                modifyCookie(reqHeaders, key, condition.getValue());
                List<String> valList = Collections.singletonList(condition.getValue());
                reqHeaders.put(condition.getKey(), valList);
            }
        }
        
        
    }

    public static void modifyQueryOperations(OriginalHttpRequest httpRequest, List<ConditionsType> modifyOperations, List<ConditionsType> addOperations, List<ConditionsType> deleteOperations){

        // since this is being used with payload conditions, we are not supporting any add operations, operations are done only on existing query keys

        String query = httpRequest.getQueryParams();
        if(query == null || query.isEmpty()){
            return ;
        }

        BasicDBObject queryParamObj = RequestTemplate.getQueryJSON(httpRequest.getUrl() + "?" + query);

        if(!modifyOperations.isEmpty()){
            for(ConditionsType condition : modifyOperations){
                if(queryParamObj.containsKey(condition.getKey())){
                    queryParamObj.put(condition.getKey(), condition.getValue());
                }
            }
        }


        if(!deleteOperations.isEmpty()){
            for(ConditionsType condition : deleteOperations){
                if(queryParamObj.containsKey(condition.getKey())){
                    queryParamObj.remove(condition.getKey());
                }
            }
        } 
        
        String queryParams = "";
        for (String key: queryParamObj.keySet()) {
            queryParams +=  (key + "=" + queryParamObj.get(key) + "&");
        }
        if (queryParams.length() > 0) {
            queryParams = queryParams.substring(0, queryParams.length() - 1);
        }

        httpRequest.setQueryParams(queryParams);
    }

    public static void writeJsonContentInFile(String folderName, String fileName, Object content){
        try {
            File file = new File(folderName, fileName);
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, content);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static void modifyUrlParamOperations(OriginalHttpRequest originalHttpRequest, List<ConditionsType> modifyUrlParams, String operationType) {
        if (modifyUrlParams == null || modifyUrlParams.isEmpty()) {
            return;
        }

        for (ConditionsType condition : modifyUrlParams) {
            try {
                // get the urlsList for the filter condition
                Set<String> urlsList = condition.getUrlsList();
                if (urlsList == null || urlsList.isEmpty()) {
                    continue;
                }

                // iterate over the urlsList and modify the url
                String currentUrl = originalHttpRequest.getUrl();
                String currentMethod = originalHttpRequest.getMethod();
                if(!urlsList.contains(currentMethod + " " + currentUrl)){
                    continue;
                }

                // now we have to call modifyURL
                UrlModifierPayload urlModifierPayload = new UrlModifierPayload("", condition.getPosition(), condition.getValue(), operationType);
                String newUrl = com.akto.test_editor.Utils.buildNewUrl(urlModifierPayload, currentUrl);
                originalHttpRequest.setUrl(newUrl);
            } catch (Exception e) {
                // Log error but continue with other operations
                System.err.println("Error modifying URL parameter: " + e.getMessage());
            }
        }
    }

    
    public static <T> T readJsonContentFromFile(String folderName, String fileName, Class<T> valueType) {
        T result = null;
        try {
            File file = new File(folderName, fileName);
            ObjectMapper objectMapper = new ObjectMapper();
            result = objectMapper.readValue(file, valueType);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public static TestingRunResult generateFailedRunResultForMessage(ObjectId testingRunId,ApiInfoKey apiInfoKey, String testSuperType, 
        String testSubType, ObjectId testRunResultSummaryId, List<String> messages, String errorMessage) {

        TestingRunResult testingRunResult = null;       
        // Set<Integer> deactivatedCollections = UsageMetricCalculator.getDeactivated();
        List<GenericTestResult> testResults = new ArrayList<>();
        String failMessage = errorMessage;
        String msg = null;
        if(shouldCallClientLayerForSampleData){
            try {
                msg = clientLayer.fetchLatestSample(apiInfoKey);
                if(msg == null){
                    loggerMaker.infoAndAddToDb("No fetchLatestSample response found for " + apiInfoKey.toString() + " from testing db layer in generateFailedRunResultForMessage");
                }
            } catch (Exception e) {
            }   
        }
        
        if(!StringUtils.hasLength(failMessage) && (messages == null || messages.isEmpty()) && msg == null){
            failMessage = TestError.NO_PATH.getMessage();
        }
            
        if(failMessage != null){
            testResults.add(new TestResult(null, null, Collections.singletonList(failMessage),0, false, Confidence.HIGH, null));
            testingRunResult = new TestingRunResult(
                testingRunId, apiInfoKey, testSuperType, testSubType, testResults,
                false, new ArrayList<>(), 100, Context.now(),
                Context.now(), testRunResultSummaryId, null, Collections
                        .singletonList(new TestingRunResult.TestLog(TestingRunResult.TestLogType.INFO, failMessage)));
        }       
        return testingRunResult;
    }

    public static boolean createFolder(String folderName){
        File statusDir = new File(folderName);
        
        if (!statusDir.exists()) {
            boolean created = statusDir.mkdirs();
            if (!created) {
                System.err.println("Failed to create directory: " + folderName);
                return false;
            }
            return true;
        }
        return false;
    }
}

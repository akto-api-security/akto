package com.akto.testing;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.testing.WorkflowUpdatedSampleData;
import com.akto.dto.type.RequestTemplate;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.parsers.HttpCallParser;
import com.akto.util.JSONUtils;
import com.mongodb.BasicDBObject;

public class Utils {

    private static final LoggerMaker loggerMaker = new LoggerMaker(Utils.class);

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
            loggerMaker.infoAndAddToDb("requestUrl: " + requestUrl, LogDb.TESTING);
            String rawUrl = executeCode(requestUrl, valuesMap);
            loggerMaker.infoAndAddToDb("rawUrl: " + requestUrl, LogDb.TESTING);
            // this url might contain urlQueryParams. We need to move it queryParams
            String[] rawUrlArr = rawUrl.split("\\?");
            request.setUrl(rawUrlArr[0]);
            if (rawUrlArr.length > 1) {
                queryFromReplacedUrl = rawUrlArr[1];
            }
            loggerMaker.infoAndAddToDb("final url: " + request.getUrl(), LogDb.TESTING);
            loggerMaker.infoAndAddToDb("queryFromReplacedUrl: " + queryFromReplacedUrl, LogDb.TESTING);
        }

        if (userSuppliedQueryParamsNullOrEmpty) {
            loggerMaker.infoAndAddToDb("setting null", LogDb.TESTING);
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
            loggerMaker.infoAndAddToDb("user has supplied query params", LogDb.TESTING);
            String finalQueryParams = executeCode(queryParams, valuesMap);
            loggerMaker.infoAndAddToDb("finalQueryParams: " + finalQueryParams, LogDb.TESTING);
            if (queryFromReplacedUrlNullOrEmpty) {
                request.setQueryParams(finalQueryParams);
            } else {
                // combine original query params and user defined query params and latter overriding former
                String combinedQueryParams = OriginalHttpRequest.combineQueryParams(queryFromReplacedUrl, finalQueryParams);
                loggerMaker.infoAndAddToDb("combinedQueryParams: " + combinedQueryParams, LogDb.TESTING);
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
        String regex = "\\$\\{(x\\d+\\.[\\w\\-\\[\\].]+|AKTO\\.changes_info\\..*?)\\}"; 
        Pattern p = Pattern.compile(regex);

        // replace with values
        Matcher matcher = p.matcher(payload);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            if (key == null) continue;
            Object obj = valuesMap.get(key);
            if (obj == null) {
                loggerMaker.errorAndAddToDb("couldn't find: " + key, LogDb.TESTING);
                throw new Exception("Couldn't find " + key);
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
            loggerMaker.infoAndAddToDb("*******************************************************************", LogDb.TESTING);
            loggerMaker.infoAndAddToDb("TEST VALIDATOR CODE:", LogDb.TESTING);
            loggerMaker.infoAndAddToDb(code, LogDb.TESTING);
            Object o = engine.eval(code);
            loggerMaker.infoAndAddToDb("TEST VALIDATOR RESULT: " + o.toString(), LogDb.TESTING);
            loggerMaker.infoAndAddToDb("*******************************************************************", LogDb.TESTING);
            vulnerable = ! (boolean) o;
        } catch (Exception e) {
            ;
        }

        return vulnerable;
    }
    
}

package com.akto.testing.workflow_node_executor;


import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.akto.dto.testing.*;
import com.akto.test_editor.execution.Memory;

import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;
import org.json.JSONObject;

import com.akto.dao.OtpTestDataDao;
import com.akto.dao.RecordedLoginInputDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.LoginFlowStepsDao;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.RecordedLoginFlowInput;
import com.akto.dto.api_workflow.Graph;
import com.akto.dto.api_workflow.Node;
import com.akto.dto.type.KeyTypes;
import com.akto.dto.type.RequestTemplate;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.JSONUtils;
import com.akto.util.RecordedLoginFlowUtil;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;


public class Utils {
    
    private static final LoggerMaker loggerMaker = new LoggerMaker(Utils.class, LogDb.TESTING);
    private static final Gson gson = new Gson();

    public static WorkflowTestResult.NodeResult processOtpNode(Node node, Map<String, Object> valuesMap) {

        List<String> testErrors = new ArrayList<>();
        BasicDBObject resp = new BasicDBObject();
        BasicDBObject body = new BasicDBObject();
        BasicDBObject data = new BasicDBObject();
        String message;

        OtpTestData otpTestData = fetchOtpTestData(node, 10);
        WorkflowNodeDetails workflowNodeDetails = node.getWorkflowNodeDetails();
        String uuid = workflowNodeDetails.getOtpRefUuid();

        if (otpTestData == null) {
            message = "otp data not received for uuid " + uuid;
            data.put("error", message);
            body.put("body", data);
            resp.put("response", body);
            testErrors.add(message);
            return new WorkflowTestResult.NodeResult(resp.toString(), false, testErrors);
        }
        try {
            String otp = extractOtpCode(otpTestData.getOtpText(), workflowNodeDetails.getOtpRegex());
            if (otp == null) {
                data.put("error", "unable to extract otp for provided regex");
                testErrors.add("unable to extract otp for provided regex");
            } else {
                data.put("otp", otp);
                data.put("otpText", otpTestData.getOtpText());
            }
            body.put("body", data);
            resp.put("response", body);
            valuesMap.put(node.getId() + ".response.body.otp", otp);
        } catch(Exception e) {
            message ="Error extracting otp data for uuid " + uuid + " error " + e.getMessage();
            data.put("error", message);
            body.put("body", data);
            resp.put("response", body);
            testErrors.add(message);
            return new WorkflowTestResult.NodeResult(resp.toString(), false, testErrors);
        }
        return new WorkflowTestResult.NodeResult(resp.toString(), false, testErrors);
    }

    private static OtpTestData fetchOtpTestData(Node node, int retries) {
        OtpTestData otpTestData = null;
        WorkflowNodeDetails workflowNodeDetails = node.getWorkflowNodeDetails();
        for (int i=0; i<retries; i++) {
            try {
                int waitInSeconds = Math.min(workflowNodeDetails.getWaitInSeconds(), 60);
                if (waitInSeconds > 0) {
                    loggerMaker.debugAndAddToDb("WAITING: " + waitInSeconds + " seconds", LogDb.TESTING);
                    Thread.sleep(waitInSeconds*1000);
                    loggerMaker.debugAndAddToDb("DONE WAITING!!!!", LogDb.TESTING);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            String uuid = workflowNodeDetails.getOtpRefUuid();
            int curTime = Context.now() - 5 * 60;
            Bson filters = Filters.and(
                Filters.eq("uuid", uuid),
                Filters.gte("createdAtEpoch", curTime)
            );
            otpTestData = OtpTestDataDao.instance.findOne(filters);
            if (otpTestData != null) {
                break;
            }
        }
        return otpTestData;
    }

    private static String extractOtpCode(String text, String regex) {
        loggerMaker.debugAndAddToDb(regex, LogDb.TESTING);

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        String verificationCode = null;
        if (matcher.find()) {
            verificationCode = matcher.group(1);
        }

        return verificationCode;
    }

    public static WorkflowTestResult.NodeResult processRecorderNode(Node node, Map<String, Object> valuesMap, RecordedLoginFlowInput recordedLoginFlowInput) {

        List<String> testErrors = new ArrayList<>();
        BasicDBObject resp = new BasicDBObject();
        BasicDBObject body = new BasicDBObject();
        BasicDBObject data = new BasicDBObject();
        String message;
        
        String token = fetchToken(null, recordedLoginFlowInput, 5);

        if (token == null){
            message = "error processing reorder node";
            data.put("error", message);
            body.put("body", data);
            resp.put("response", body);
            testErrors.add(message);
            return new WorkflowTestResult.NodeResult(resp.toString(), false, testErrors);
        }

        // valuesMap.put(node.getId() + ".response.body.token", token);
        

        BasicDBObject flattened = JSONUtils.flattenWithDots(BasicDBObject.parse(token));

        for (String param: flattened.keySet()) {
            String key = node.getId() + ".response.body" + "." + param;
            valuesMap.put(key, flattened.get(param));
	        loggerMaker.debugAndAddToDb("kv pair: " + key + " " + flattened.get(param));
        }	

        data.put("token", token);
        body.put("body", data);
        resp.put("response", body);
        return new WorkflowTestResult.NodeResult(resp.toString(), false, testErrors);
    }

    public static String fetchToken(String roleName, RecordedLoginFlowInput recordedLoginFlowInput, int retries) {

        // need to cache
        String token = null;
        for (int i=0; i<retries; i++) {
            
            String payload = recordedLoginFlowInput.getContent().toString();
            File tmpOutputFile;
            File tmpErrorFile;
            try {
                tmpOutputFile = File.createTempFile("output", ".json");
                tmpErrorFile = File.createTempFile("recordedFlowOutput", ".txt");
                RecordedLoginFlowUtil.triggerFlow(recordedLoginFlowInput.getTokenFetchCommand(), payload, 
                tmpOutputFile.getPath(), tmpErrorFile.getPath(), 0);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("error running recorded flow, retrying " + e.toString(), LogDb.TESTING);
                continue;
            }

            try {
                token = RecordedLoginFlowUtil.fetchToken(tmpOutputFile.getPath(), tmpErrorFile.getPath());
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error fetching token, retrying " + e.toString(), LogDb.TESTING);
                continue;
            }
            if (token != null) {
                break;
            }
        }
        if(roleName == null){
            return token;
        }
        return token;
    }

    public static WorkflowTestResult.NodeResult processNode(Node node, Map<String, Object> valuesMap, Boolean allowAllStatusCodes, boolean debug, List<TestingRunResult.TestLog> testLogs, Memory memory, boolean allowAllCombinations) {
        RecordedLoginFlowInput recordedLoginFlowInput = RecordedLoginInputDao.instance.findOne(new BasicDBObject());
        return processNode(node, valuesMap, allowAllStatusCodes, debug, testLogs, memory, recordedLoginFlowInput, allowAllCombinations);
    }

    public static WorkflowTestResult.NodeResult processNode(Node node, Map<String, Object> valuesMap, Boolean allowAllStatusCodes, boolean debug, List<TestingRunResult.TestLog> testLogs, Memory memory, AuthMechanism authMechanism) {
        return processNode(node, valuesMap, allowAllStatusCodes, debug, testLogs, memory, authMechanism.getRecordedLoginFlowInput(), false);
    }

    public static WorkflowTestResult.NodeResult processNode(Node node, Map<String, Object> valuesMap, Boolean allowAllStatusCodes, boolean debug, List<TestingRunResult.TestLog> testLogs, Memory memory, RecordedLoginFlowInput recordedLoginFlowInput, boolean allowAllCombinations) {
        if (node.getWorkflowNodeDetails().getType() == WorkflowNodeDetails.Type.RECORDED) {
            return processRecorderNode(node, valuesMap, recordedLoginFlowInput);
        }
        else if (node.getWorkflowNodeDetails().getType() == WorkflowNodeDetails.Type.OTP) {
            return processOtpNode(node, valuesMap);
        }
        else {
            return processApiNode(node, valuesMap, allowAllStatusCodes, debug, testLogs, memory, allowAllCombinations);
        }
    }

    public static WorkflowTestResult.NodeResult processApiNode(Node node, Map<String, Object> valuesMap, Boolean allowAllStatusCodes, boolean debug, List<TestingRunResult.TestLog> testLogs, Memory memory, boolean allowAllCombinations) {
        
        NodeExecutorFactory nodeExecutorFactory = new NodeExecutorFactory(allowAllCombinations);
        NodeExecutor nodeExecutor = nodeExecutorFactory.getExecutor(node);
        return nodeExecutor.processNode(node, valuesMap, allowAllStatusCodes, debug, testLogs, memory);
    }

    public static WorkflowTestResult.NodeResult executeNode(Node node, Map<String, Object> valuesMap,boolean debug, List<TestingRunResult.TestLog> testLogs, Memory memory, boolean allowAllCombinations) {
        WorkflowTestResult.NodeResult nodeResult;
        try {
            nodeResult = Utils.processNode(node, valuesMap, true, debug, testLogs, memory, allowAllCombinations);
        } catch (Exception e) {
            ;
            List<String> testErrors = new ArrayList<>();
            testErrors.add("Something went wrong");
            nodeResult = new WorkflowTestResult.NodeResult("{}", false, testErrors);
        }

        return nodeResult;

    }

    public static LoginFlowResponse runLoginFlow(WorkflowTest workflowTest, AuthMechanism authMechanism, LoginFlowParams loginFlowParams, String roleName) throws Exception {
        Graph graph = new Graph();
        graph.buildGraph(workflowTest);
        int errorExpiryTime = Context.now() + 900; // 15 mins

        ArrayList<Object> responses = new ArrayList<Object>();

        List<Node> nodes = graph.sort();

        if (loginFlowParams != null && loginFlowParams.getFetchValueMap()) {
            nodes.get(0).setId(loginFlowParams.getNodeId());
        }
        
        Map<String, Object> valuesMap = constructValueMap(loginFlowParams);

        int index = 0;
        for (Node node: nodes) {
            boolean allowAllStatusCodes = false;
            WorkflowTestResult.NodeResult nodeResult;
            try {
                if (authMechanism.getRequestData() != null && authMechanism.getRequestData().size() > 0 && authMechanism.getRequestData().get(index).getAllowAllStatusCodes()) {
                    allowAllStatusCodes = authMechanism.getRequestData().get(0).getAllowAllStatusCodes();
                }
                nodeResult = processNode(node, valuesMap, allowAllStatusCodes, false, new ArrayList<>(), null, authMechanism);
            } catch (Exception e) {
                authMechanism.updateErrorCacheExpiryEpoch(errorExpiryTime);
                List<String> testErrors = new ArrayList<>();
                testErrors.add("Error Processing Node In Login Flow " + e.getMessage());
                nodeResult = new WorkflowTestResult.NodeResult("{}", false, testErrors);
            }

            JSONObject respString = new JSONObject();
            Map<String, Map<String, Object>> json = gson.fromJson(nodeResult.getMessage(), Map.class);

            if (json.get("response").get("headers") != null) {
                respString.put("headers", json.get("response").get("headers"));
            } else {
                respString.put("headers", "{}");
            }
            if (json.get("response").get("body") != null) {
                respString.put("body", json.get("response").get("body"));
            } else {
                respString.put("body", "{}");
            }
            // respString.put("body", json.get("response").get("body").toString());
            responses.add(respString.toString());
            
            if (nodeResult.getErrors().size() > 0) {
                authMechanism.updateErrorCacheExpiryEpoch(errorExpiryTime);
                return new LoginFlowResponse(responses, "Failed to process node " + node.getId(), false);
            } else {
                if (loginFlowParams != null && loginFlowParams.getFetchValueMap()) {
                    saveValueMapData(loginFlowParams, valuesMap);
                }
            }
            index++;

        }

        int newExpiryTime = Context.now() + 1800; // 30 mins
        List<AuthParam> calculatedAuthParams = new ArrayList<>();
        for (AuthParam param : authMechanism.getAuthParams()) {
            try {
                String value = executeCode(param.getValue(), valuesMap, false);
                if (!param.getValue().equals(value) && value == null) {
                    authMechanism.updateErrorCacheExpiryEpoch(errorExpiryTime);
                    return new LoginFlowResponse(responses, "auth param not found at specified path " + 
                    param.getValue(), false);
                }

                // checking on the value of if this is valid jwt token or valid cookie which has expiry time
                String tempVal = new String(value);
                if(tempVal.contains(" ")){
                    tempVal = value.split(" ")[1];
                }
                if(KeyTypes.isJWT(tempVal)){
                    try {
                        String[] parts = tempVal.split("\\.");
                        if (parts.length != 3) {
                            authMechanism.updateErrorCacheExpiryEpoch(errorExpiryTime);
                            throw new IllegalArgumentException("Invalid JWT token format");
                        }
                        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
                        JSONObject payloadJson = new JSONObject(payload);
                        if (payloadJson.has("exp")) {
                            newExpiryTime = Math.min(payloadJson.getInt("exp"), newExpiryTime);
                            
                        } else {
                            authMechanism.updateErrorCacheExpiryEpoch(errorExpiryTime);
                            throw new IllegalArgumentException("JWT does not have an 'exp' claim");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                calculatedAuthParams.add(new HardcodedAuthParam(param.getWhere(), param.getKey(), value, param.getShowHeader()));
            } catch(Exception e) {
                authMechanism.updateErrorCacheExpiryEpoch(errorExpiryTime);
                return new LoginFlowResponse(responses, "error resolving auth param " + param.getValue(), false);
            }
        }

        authMechanism.updateCacheExpiryEpoch(newExpiryTime);
        authMechanism.updateAuthParamsCached(calculatedAuthParams);

        return new LoginFlowResponse(responses, null, true);
    }

    public static Map<String, Object> constructValueMap(LoginFlowParams loginFlowParams) {
        Map<String, Object> valuesMap = new HashMap<>();
        if (loginFlowParams == null || !loginFlowParams.getFetchValueMap()) {
            return valuesMap;
        }
        Bson filters = Filters.and(
            Filters.eq("userId", loginFlowParams.getUserId())
        );
        LoginFlowStepsData loginFlowStepData = LoginFlowStepsDao.instance.findOne(filters);

        if (loginFlowStepData == null || loginFlowStepData.getValuesMap() == null) {
            return valuesMap;
        }

        valuesMap = loginFlowStepData.getValuesMap();

        Set<String> keysToRemove = new HashSet<String>();
        for(String key : valuesMap.keySet()){
            if(key.startsWith(loginFlowParams.getNodeId())){
                keysToRemove.add(key);
            }
        }

        valuesMap.keySet().removeAll(keysToRemove);

        return valuesMap;
    }

    public static Map<String, Object> saveValueMapData(LoginFlowParams loginFlowParams, Map<String, Object> valuesMap) {

        Integer userId = loginFlowParams.getUserId();

        Bson filter = Filters.and(
            Filters.eq("userId", loginFlowParams.getUserId())
        );
        Bson update = Updates.set("valuesMap", valuesMap);
        LoginFlowStepsDao.instance.updateOne(filter, update);
        return valuesMap;
    }

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
            loggerMaker.debugAndAddToDb("requestUrl: " + requestUrl, LogDb.TESTING);
            String rawUrl = executeCode(requestUrl, valuesMap);
            loggerMaker.debugAndAddToDb("rawUrl: " + requestUrl, LogDb.TESTING);
            // this url might contain urlQueryParams. We need to move it queryParams
            String[] rawUrlArr = rawUrl.split("\\?");
            request.setUrl(rawUrlArr[0]);
            if (rawUrlArr.length > 1) {
                queryFromReplacedUrl = rawUrlArr[1];
            }
            loggerMaker.debugAndAddToDb("final url: " + request.getUrl(), LogDb.TESTING);
            loggerMaker.debugAndAddToDb("queryFromReplacedUrl: " + queryFromReplacedUrl, LogDb.TESTING);
        }

        if (userSuppliedQueryParamsNullOrEmpty) {
            loggerMaker.debugAndAddToDb("setting null", LogDb.TESTING);
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
            loggerMaker.debugAndAddToDb("user has supplied query params", LogDb.TESTING);
            String finalQueryParams = executeCode(queryParams, valuesMap);
            loggerMaker.debugAndAddToDb("finalQueryParams: " + finalQueryParams, LogDb.TESTING);
            if (queryFromReplacedUrlNullOrEmpty) {
                request.setQueryParams(finalQueryParams);
            } else {
                // combine original query params and user defined query params and latter overriding former
                String combinedQueryParams = OriginalHttpRequest.combineQueryParams(queryFromReplacedUrl, finalQueryParams);
                loggerMaker.debugAndAddToDb("combinedQueryParams: " + combinedQueryParams, LogDb.TESTING);
                request.setQueryParams(combinedQueryParams);
            }
        } else if (!queryFromReplacedUrlNullOrEmpty) {
            request.setQueryParams(queryFromReplacedUrl);
        }

        return request;
    }

    public static String executeCode(String ogPayload, Map<String, Object> valuesMap, boolean shouldThrowException) throws Exception {
        return replaceVariables(ogPayload,valuesMap, true, shouldThrowException);
    }

    public static String executeCode(String ogPayload, Map<String, Object> valuesMap) throws Exception {
        return replaceVariables(ogPayload,valuesMap, true, true);
    }


    public static String replaceVariables(String payload, Map<String, Object> valuesMap, boolean escapeString, boolean shouldThrowException) throws Exception {
        return com.akto.testing.Utils.replaceVariables(payload, valuesMap, escapeString, shouldThrowException);
    }

    public static String generateKey(String nodeId, boolean isHeader, String param, boolean isRequest) {
        return StringUtils.joinWith("@", nodeId, isHeader, param, isRequest);
    }

    public static String evaluateNextNodeId(String nodeId) {
        String numStr = nodeId.substring(1, nodeId.length());
        Integer num = Integer.valueOf(numStr);
        return "x" + (num+1);
    }

}

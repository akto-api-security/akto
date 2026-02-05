package com.akto.testing.workflow_node_executor;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Base64;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import com.akto.dto.testing.*;
import com.akto.test_editor.execution.Memory;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

import com.akto.dao.context.Context;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
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

public class Utils {
    
    private static final LoggerMaker loggerMaker = new LoggerMaker(Utils.class, LogDb.TESTING);
    private static final DataActor dataActor = DataActorFactory.fetchInstance();

    private static final Gson gson = new Gson();

    public static WorkflowTestResult.NodeResult processOtpNode(Node node, Map<String, Object> valuesMap) {

        loggerMaker.infoAndAddToDb("processOtpNode started", LogDb.TESTING);

        List<String> testErrors = new ArrayList<>();
        BasicDBObject resp = new BasicDBObject();
        BasicDBObject body = new BasicDBObject();
        BasicDBObject data = new BasicDBObject();
        String message;

        OtpTestData otpTestData = fetchOtpTestData(node, 4);
        WorkflowNodeDetails workflowNodeDetails = node.getWorkflowNodeDetails();
        loggerMaker.infoAndAddToDb("workflowNodeDetails: " + workflowNodeDetails.toString(), LogDb.TESTING);
        String uuid = workflowNodeDetails.getOtpRefUuid();

        if (otpTestData == null) {
            loggerMaker.infoAndAddToDb("no otp data received for uuid=" + uuid, LogDb.TESTING);
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
            loggerMaker.errorAndAddToDb(e, message);
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
            loggerMaker.infoAndAddToDb("FETCH_OTP_REQUEST: attempt=" + (i + 1) + ", otpRefUuid=" + workflowNodeDetails.getOtpRefUuid(), LogDb.TESTING);
            try {
                int waitInSeconds = Math.min(workflowNodeDetails.getWaitInSeconds(), 60);
                if (waitInSeconds > 0) {
                    loggerMaker.infoAndAddToDb("WAITING: " + waitInSeconds + " seconds", LogDb.TESTING);
                    Thread.sleep(waitInSeconds*1000);
                    loggerMaker.infoAndAddToDb("DONE WAITING!!!!", LogDb.TESTING);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            String uuid = workflowNodeDetails.getOtpRefUuid();
            int curTime = Context.now() - 5 * 60;
            otpTestData = dataActor.fetchOtpTestData(uuid, curTime); //cyborg call

            loggerMaker.infoAndAddToDb("FETCH_OTP_RESPONSE: attempt=" + (i + 1) + ", otpRefUuid=" + workflowNodeDetails.getOtpRefUuid() + ", hasData=" + (otpTestData != null), LogDb.TESTING);
            if (otpTestData != null) {
                break;
            }
        }
        return otpTestData;
    }

    private static String extractOtpCode(String text, String regex) {
        loggerMaker.infoAndAddToDb("OTP_EXTRACT_REQUEST: regex=" + regex + ", textPreview=" + (text == null ? "null" : text.substring(0, Math.min(text.length(), 200))), LogDb.TESTING);

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        String verificationCode = null;
        if (matcher.find()) {
            verificationCode = matcher.group(1);
        }

        loggerMaker.infoAndAddToDb("OTP_EXTRACT_RESPONSE: verificationCode=" + verificationCode, LogDb.TESTING);

        return verificationCode;
    }

    public static WorkflowTestResult.NodeResult processRecorderNode(Node node, Map<String, Object> valuesMap, RecordedLoginFlowInput recordedLoginFlowInput) {
        List<String> testErrors = new ArrayList<>();
        BasicDBObject resp = new BasicDBObject();
        BasicDBObject body = new BasicDBObject();
        BasicDBObject data = new BasicDBObject();
        String message;
        
        String token = fetchToken(recordedLoginFlowInput, 5);

        if (token == null){
            message = "error processing reorder node";
            data.put("error", message);
            body.put("body", data);
            resp.put("response", body);
            testErrors.add(message);
            return new WorkflowTestResult.NodeResult(resp.toString(), false, testErrors);
        }

        BasicDBObject flattened = JSONUtils.flattenWithDots(BasicDBObject.parse(token));

        for (String param: flattened.keySet()) {
            String key = node.getId() + ".response.body" + "." + param;
            valuesMap.put(key, flattened.get(param));
	        loggerMaker.infoAndAddToDb("kv pair: " + key + " " + flattened.get(param));
        }
        
        data.put("token", token);
        body.put("body", data);
        resp.put("response", body);
        return new WorkflowTestResult.NodeResult(resp.toString(), false, testErrors);
    }

    public static String fetchToken(RecordedLoginFlowInput recordedLoginFlowInput, int retries) {

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

        return token;
    }

    public static WorkflowTestResult.NodeResult processNode(Node node, Map<String, Object> valuesMap, Boolean allowAllStatusCodes, boolean debug, List<TestingRunResult.TestLog> testLogs, Memory memory) {
        RecordedLoginFlowInput recordedLoginFlowInput = dataActor.fetchRecordedLoginFlowInput();
        return processNode(node, valuesMap, allowAllStatusCodes, debug, testLogs, memory, recordedLoginFlowInput);
    }

    public static WorkflowTestResult.NodeResult processNode(Node node, Map<String, Object> valuesMap, Boolean allowAllStatusCodes, boolean debug, List<TestingRunResult.TestLog> testLogs, Memory memory, AuthMechanism authMechanism) {
        return processNode(node, valuesMap, allowAllStatusCodes, debug, testLogs, memory, authMechanism.getRecordedLoginFlowInput());
    }

    public static WorkflowTestResult.NodeResult processNode(Node node, Map<String, Object> valuesMap, Boolean allowAllStatusCodes, boolean debug, List<TestingRunResult.TestLog> testLogs, Memory memory, RecordedLoginFlowInput recordedLoginFlowInput) {
        if (node.getWorkflowNodeDetails().getType() == WorkflowNodeDetails.Type.RECORDED) {
            return processRecorderNode(node, valuesMap, recordedLoginFlowInput);
        }
        else if (node.getWorkflowNodeDetails().getType() == WorkflowNodeDetails.Type.OTP) {
            return processOtpNode(node, valuesMap);
        }
        else {
            return processApiNode(node, valuesMap, allowAllStatusCodes, debug, testLogs, memory);
        }
    }

    public static WorkflowTestResult.NodeResult processApiNode(Node node, Map<String, Object> valuesMap, Boolean allowAllStatusCodes, boolean debug, List<TestingRunResult.TestLog> testLogs, Memory memory) {
        
        NodeExecutorFactory nodeExecutorFactory = new NodeExecutorFactory();
        NodeExecutor nodeExecutor = nodeExecutorFactory.getExecutor(node);
        return nodeExecutor.processNode(node, valuesMap, allowAllStatusCodes, debug, testLogs, memory);
    }

    public static WorkflowTestResult.NodeResult executeNode(Node node, Map<String, Object> valuesMap,boolean debug, List<TestingRunResult.TestLog> testLogs, Memory memory) {
        WorkflowTestResult.NodeResult nodeResult;
        try {
            nodeResult = Utils.processNode(node, valuesMap, true, debug, testLogs, memory);
        } catch (Exception e) {
            // Categorize the error similar to single execution mode
            String errorMessage = "Error executing test request: " + e.getMessage();
            if (testLogs != null) {
                testLogs.add(new TestingRunResult.TestLog(TestingRunResult.TestLogType.ERROR, errorMessage));
            }

            TestResult.TestError categorizedError = com.akto.test_editor.execution.Executor.categorizeError(errorMessage);
            List<String> testErrors = new ArrayList<>();
            testErrors.add(categorizedError.getMessage());
            nodeResult = new WorkflowTestResult.NodeResult("{}", false, testErrors);
        }

        return nodeResult;

    }

    public static LoginFlowResponse runLoginFlow(WorkflowTest workflowTest, AuthMechanism authMechanism, LoginFlowParams loginFlowParams) throws Exception {
        Graph graph = new Graph();
        graph.buildGraph(workflowTest);

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
                ;
                List<String> testErrors = new ArrayList<>();
                testErrors.add("Error Processing Node In Login Flow " + e.getMessage());
                nodeResult = new WorkflowTestResult.NodeResult("{}", false, testErrors);
            }

            JSONObject respString = new JSONObject();
            Map<String, Map<String, Object>> json = gson.fromJson(nodeResult.getMessage(), Map.class);

            respString.put("headers", json.get("response").get("headers"));
            respString.put("body", json.get("response").get("body"));
            responses.add(respString.toString());
            
            if (nodeResult.getErrors().size() > 0) {
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
                String value = executeCode(param.getValue(), valuesMap);
                if (!param.getValue().equals(value) && value == null) {
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
                            throw new IllegalArgumentException("Invalid JWT token format");
                        }
                        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
                        JSONObject payloadJson = new JSONObject(payload);
                        if (payloadJson.has("exp")) {
                            newExpiryTime = Math.min(payloadJson.getInt("exp"), newExpiryTime);

                        } else {
                            throw new IllegalArgumentException("JWT does not have an 'exp' claim");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                calculatedAuthParams.add(new HardcodedAuthParam(param.getWhere(), param.getKey(), value, param.getShowHeader()));
            } catch(Exception e) {
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
        LoginFlowStepsData loginFlowStepData = dataActor.fetchLoginFlowStepsData(loginFlowParams.getUserId());

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
        dataActor.updateLoginFlowStepsData(userId, valuesMap);
        return valuesMap;
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


    public static String executeCode(String ogPayload, Map<String, Object> valuesMap) throws Exception {
        ScriptEngineManager factory = new ScriptEngineManager();
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
        String regex = "\\$\\{((step|x)\\d+\\.[\\w\\-\\[\\].]+|AKTO\\.changes_info\\..*?)\\}"; 
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

    public static String generateKey(String nodeId, boolean isHeader, String param, boolean isRequest) {
        return StringUtils.joinWith("@", nodeId, isHeader, param, isRequest);
    }

    public static String evaluateNextNodeId(String nodeId) {
        String numStr = nodeId.substring(1, nodeId.length());
        Integer num = Integer.valueOf(numStr);
        return "x" + (num+1);
    }

}

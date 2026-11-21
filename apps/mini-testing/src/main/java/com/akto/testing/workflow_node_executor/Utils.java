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
import com.akto.testing.login_flow.LoginFlowNodes;
import com.akto.testing.login_flow.PlaceholderResolver;
import com.akto.util.JSONUtils;
import com.akto.util.RecordedLoginFlowUtil;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;

public class Utils {
    
    private static final LoggerMaker loggerMaker = new LoggerMaker(Utils.class, LogDb.TESTING);
    private static final DataActor dataActor = DataActorFactory.fetchInstance();

    private static final Gson gson = new Gson();

    public static WorkflowTestResult.NodeResult processOtpNode(Node node, Map<String, Object> valuesMap) {

        loggerMaker.warnAndAddToDb("PROCESS_OTP_NODE_STARTED");

        List<String> testErrors = new ArrayList<>();
        BasicDBObject resp = new BasicDBObject();
        BasicDBObject body = new BasicDBObject();
        BasicDBObject data = new BasicDBObject();
        String message;

        OtpTestData otpTestData = fetchOtpTestData(node, 4);
        WorkflowNodeDetails workflowNodeDetails = node.getWorkflowNodeDetails();
        loggerMaker.warnAndAddToDb("OTP REQUEST [Node " + node.getId() + "]: type=" + workflowNodeDetails.getType());
        String uuid = workflowNodeDetails.getOtpRefUuid();

        if (otpTestData == null) {
            loggerMaker.warnAndAddToDb("NO OTP DATA RECEIVED FOR UUID: " + uuid);
            message = "otp data not received for uuid " + uuid;
            data.put("error", message);
            body.put("body", data);
            resp.put("response", body);
            testErrors.add(message);
            loggerMaker.warnAndAddToDb("VALUES_MAP_AFTER_OTP_NODE_ERROR: keys=" + valuesMap.size());
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
            loggerMaker.warnAndAddToDb("VALUES_MAP_AFTER_OTP_EXTRACTION: keys=" + valuesMap.size());
            loggerMaker.warnAndAddToDb("OTP_NODE_RESPONSE [Node " + node.getId() + "]: errors=" + testErrors.size());
        } catch(Exception e) {
            message ="Error extracting otp data for uuid " + uuid + " error " + e.getMessage();
            loggerMaker.errorAndAddToDb(e, message);
            data.put("error", message);
            body.put("body", data);
            resp.put("response", body);
            testErrors.add(message);
            loggerMaker.warnAndAddToDb("VALUES_MAP_AFTER_OTP_NODE_EXCEPTION: keys=" + valuesMap.size());
            loggerMaker.warnAndAddToDb("OTP_NODE_RESPONSE [Node " + node.getId() + "]: errors=" + testErrors.size());
            return new WorkflowTestResult.NodeResult(resp.toString(), false, testErrors);
        }
        loggerMaker.warnAndAddToDb("OTP_NODE_RESPONSE [Node " + node.getId() + "]: errors=" + testErrors.size());
        loggerMaker.warnAndAddToDb("VALUES_MAP_AFTER_OTP_NODE: keys=" + valuesMap.size());
        return new WorkflowTestResult.NodeResult(resp.toString(), false, testErrors);
    }

    private static OtpTestData fetchOtpTestData(Node node, int retries) {
        OtpTestData otpTestData = null;
        WorkflowNodeDetails workflowNodeDetails = node.getWorkflowNodeDetails();
        for (int i=0; i<retries; i++) {
            loggerMaker.warnAndAddToDb("FETCH_OTP_REQUEST: attempt=" + (i + 1) );
            try {
                int waitInSeconds = Math.min(workflowNodeDetails.getWaitInSeconds(), 60);
                if (waitInSeconds > 0) {
                    loggerMaker.warnAndAddToDb("WAITING: " + waitInSeconds + " seconds");
                    Thread.sleep(waitInSeconds*1000);
                    loggerMaker.warnAndAddToDb("DONE WAITING!!!!");
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            String uuid = workflowNodeDetails.getOtpRefUuid();
            int curTime = Context.now() - 5 * 60;
            otpTestData = dataActor.fetchOtpTestData(uuid, curTime); //cyborg call

            loggerMaker.warnAndAddToDb("FETCH_OTP_RESPONSE: attempt=" + (i + 1)  + ", hasData=" + (otpTestData != null));
            if (otpTestData != null) {
                break;
            }
        }
        return otpTestData;
    }

    private static String extractOtpCode(String text, String regex) {
        loggerMaker.warnAndAddToDb("OTP_EXTRACT_REQUEST: regex=" + regex + ", textLength=" + (text == null ? "null" : text.length()));

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        String verificationCode = null;
        if (matcher.find()) {
            verificationCode = matcher.group(1);
        }

        loggerMaker.warnAndAddToDb("OTP_EXTRACT_RESPONSE: found=" + (verificationCode != null));

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
            loggerMaker.infoAndAddToDb("kv pair: " + key);
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
        // fetchRecordedLoginFlowInput() is only ever used by the RECORDED node branch below - fetch it
        // lazily, only for that type, instead of unconditionally on every node (OTP/API nodes - the
        // common case - discarded it immediately, at the cost of one wasted remote call per node).
        if (node.getWorkflowNodeDetails().getType() == WorkflowNodeDetails.Type.RECORDED) {
            RecordedLoginFlowInput recordedLoginFlowInput = dataActor.fetchRecordedLoginFlowInput();
            return processNode(node, valuesMap, allowAllStatusCodes, debug, testLogs, memory, recordedLoginFlowInput);
        }
        return processNode(node, valuesMap, allowAllStatusCodes, debug, testLogs, memory, (RecordedLoginFlowInput) null);
    }

    // login-flow entry: API steps resolve placeholders in login-flow mode (stepK, ${cookies...})
    public static WorkflowTestResult.NodeResult processNode(Node node, Map<String, Object> valuesMap, Boolean allowAllStatusCodes, boolean debug, List<TestingRunResult.TestLog> testLogs, Memory memory, AuthMechanism authMechanism) {
        WorkflowNodeDetails.Type type = node.getWorkflowNodeDetails().getType();
        if (type == WorkflowNodeDetails.Type.RECORDED || type == WorkflowNodeDetails.Type.OTP
                || node.getWorkflowNodeDetails() instanceof YamlNodeDetails) {
            return processNode(node, valuesMap, allowAllStatusCodes, debug, testLogs, memory, authMechanism.getRecordedLoginFlowInput());
        }
        return new ApiNodeExecutor(true).processNode(node, valuesMap, allowAllStatusCodes, debug, testLogs, memory);
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
        loggerMaker.warnAndAddToDb("RUN_LOGIN_FLOW_STARTED");
        Graph graph = new Graph();
        graph.buildGraph(workflowTest);

        ArrayList<Object> responses = new ArrayList<Object>();

        List<Node> nodes = graph.sort();

        if (loginFlowParams != null && loginFlowParams.getFetchValueMap()) {
            nodes.get(0).setId(loginFlowParams.getNodeId());
        }
        
        Map<String, Object> valuesMap = constructValueMap(loginFlowParams);
        loggerMaker.warnAndAddToDb("VALUES_MAP_CONSTRUCTED: keys=" + valuesMap.size());

        int index = 0;
        for (Node node: nodes) {
            loggerMaker.warnAndAddToDb("PROCESSING_NODE: " + node.getId());
            boolean allowAllStatusCodes = allowAllStatusCodes(authMechanism.getRequestData(), index);
            WorkflowTestResult.NodeResult nodeResult;
            try {
                nodeResult = processNode(node, valuesMap, allowAllStatusCodes, false, new ArrayList<>(), null, authMechanism);
            } catch (Exception e) {
                loggerMaker.warnAndAddToDb("ERROR_PROCESSING_NODE: " + node.getId() + " " + e.getClass().getSimpleName());
                List<String> testErrors = new ArrayList<>();
                testErrors.add("Error Processing Node In Login Flow: " + describeCause(e));
                nodeResult = new WorkflowTestResult.NodeResult(null, false, testErrors);
            }

            responses.add(buildStepResponse(nodeResult.getMessage()));

            if (nodeResult.getErrors() != null && !nodeResult.getErrors().isEmpty()) {
                String error = "Failed to process node " + LoginFlowNodes.label(node.getId()) + ": "
                        + String.join("; ", nodeResult.getErrors());
                loggerMaker.warnAndAddToDb("FAILED_TO_PROCESS_NODE: " + error);
                return new LoginFlowResponse(responses, error, false);
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
            String value;
            try {
                value = resolveAuthParamValue(param.getValue(), valuesMap);
            } catch (Exception e) {
                // Nothing is cached on this path, so a literal ${...} can never become the role's token.
                String error = "auth param " + param.getKey() + ": " + describeCause(e);
                loggerMaker.warnAndAddToDb("AUTH_PARAM_RESOLUTION_FAILED: " + error);
                return new LoginFlowResponse(responses, error, false);
            }
            // checking on the value of if this is valid jwt token or valid cookie which has expiry time
            String ogValue = param.getValue();
            String tempVal = value;
            if (ogValue != null && ogValue.endsWith("response.body.cookieHeader}")) {
                // Cookie header value is a semicolon-separated list of key=value pairs;
                // expand it and check each cookie value for a JWT with an exp claim.
                String[] cookieParts = tempVal.split(";\\s*");
                for (String cookiePart : cookieParts) {
                    int eqIdx = cookiePart.indexOf('=');
                    if (eqIdx < 0) continue;
                    String cookieVal = cookiePart.substring(eqIdx + 1).trim();
                    newExpiryTime = Math.min(extractJwtExpiry(cookieVal), newExpiryTime);
                }
            } else {
                String[] parts = tempVal.split(" ");
                if (parts.length > 1) {
                    tempVal = parts[1];
                }
                newExpiryTime = Math.min(extractJwtExpiry(tempVal), newExpiryTime);
            }

            calculatedAuthParams.add(new HardcodedAuthParam(param.getWhere(), param.getKey(), value, param.getShowHeader()));
        }

        authMechanism.updateCacheExpiryEpoch(newExpiryTime);
        authMechanism.updateAuthParamsCached(calculatedAuthParams);

        return new LoginFlowResponse(responses, null, true);
    }
    
    static boolean allowAllStatusCodes(List<RequestData> requestData, int index) {
        if (requestData == null || index < 0 || index >= requestData.size()) return false;
        RequestData step = requestData.get(index);
        return step != null && step.getAllowAllStatusCodes();
    }

    private static final Pattern ANY_PLACEHOLDER = Pattern.compile("\\$\\{[^{}]*\\}");

    // Strict, login-flow mode with no request URL (so ${cookies} is an error, ${cookies.<name>} works): a missing
    // key, unsupported placeholder or failed filter throws with the path, and the caller fails the login instead of
    // caching "Bearer ${x15.response.body.access_token}". mini-only: placeholders that all resolve to "" (which
    // would cache "Bearer ") are rejected too.
    static String resolveAuthParamValue(String template, Map<String, Object> valuesMap) throws Exception {
        if (template == null) throw new Exception("value is not set");
        // resolveTemplate: auth params went through executeCode before, so #[ ]# blocks keep being evaluated
        String value = com.akto.testing.Utils.resolveTemplate(template, valuesMap,
                PlaceholderResolver.Options.loginFlow(null, null));
        List<String> placeholders = new ArrayList<>();
        Matcher matcher = ANY_PLACEHOLDER.matcher(template);
        while (matcher.find()) placeholders.add(matcher.group());
        if (!placeholders.isEmpty() && value.trim().equals(ANY_PLACEHOLDER.matcher(template).replaceAll("").trim())) {
            throw new Exception(String.join(", ", placeholders) + " resolved to an empty value");
        }
        return value;
    }

    // headers/body of one step for the LoginForm (same shape as akto's apps/testing buildStepResponse, which the
    // dashboard renders in hybrid mode too); a step that failed before sending has no message
    static String buildStepResponse(String message) {
        JSONObject respString = new JSONObject();
        Object headers = null;
        Object body = null;
        if (message != null) {
            Map<String, Object> json = gson.fromJson(message, Map.class);
            Object response = json == null ? null : json.get("response");
            if (response instanceof Map) {
                headers = ((Map<?, ?>) response).get("headers");
                body = ((Map<?, ?>) response).get("body");
            }
        }
        respString.put("headers", headers != null ? headers : "{}");
        respString.put("body", body != null ? body : "{}");
        return respString.toString();
    }

    static String describeCause(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    public static Map<String, Object> constructValueMap(LoginFlowParams loginFlowParams) {
        Map<String, Object> valuesMap = new HashMap<>();
        if (loginFlowParams == null || !loginFlowParams.getFetchValueMap()) {
            loggerMaker.warnAndAddToDb("NO LOGIN FLOW PARAMS OR NO FETCH VALUE MAP");
            return valuesMap;
        }
        LoginFlowStepsData loginFlowStepData = dataActor.fetchLoginFlowStepsData(loginFlowParams.getUserId());

        if (loginFlowStepData == null || loginFlowStepData.getValuesMap() == null) {
            loggerMaker.warnAndAddToDb("NO LOGIN FLOW STEPS DATA OR NO VALUES MAP");
            return valuesMap;
        }

        valuesMap = loginFlowStepData.getValuesMap();
        removeNodeKeys(valuesMap, loginFlowParams.getNodeId());
        return valuesMap;
    }

    // drops the re-run node's own values; "x1." so that x11, x13 ... keep theirs
    public static void removeNodeKeys(Map<String, Object> valuesMap, String nodeId) {
        Set<String> keysToRemove = new HashSet<String>();
        for(String key : valuesMap.keySet()){
            if(key.startsWith(nodeId + ".")){
                keysToRemove.add(key);
            }
        }
        valuesMap.keySet().removeAll(keysToRemove);
    }

    public static Map<String, Object> saveValueMapData(LoginFlowParams loginFlowParams, Map<String, Object> valuesMap) {
        Integer userId = loginFlowParams.getUserId();
        dataActor.updateLoginFlowStepsData(userId, valuesMap);
        return valuesMap;
    }

    // The three helpers below delegate to libs/utils so login-flow placeholders are resolved in exactly one place
    // (ApiNodeExecutor already calls com.akto.testing.Utils directly). The old copies here evaluated #[ ]# with
    // Nashorn, which is absent on the JDK 17 runtime.
    public static OriginalHttpRequest buildHttpRequest(WorkflowUpdatedSampleData updatedSampleData, Map<String, Object> valuesMap) throws Exception {
        return com.akto.testing.Utils.buildHttpRequest(updatedSampleData, valuesMap);
    }

    public static String executeCode(String ogPayload, Map<String, Object> valuesMap) throws Exception {
        return com.akto.testing.Utils.executeCode(ogPayload, valuesMap);
    }

    public static String replaceVariables(String payload, Map<String, Object> valuesMap, boolean escapeString) throws Exception {
        return com.akto.testing.Utils.replaceVariables(payload, valuesMap, escapeString);
    }

    private static int extractJwtExpiry(String token) {
        if (!KeyTypes.isJWT(token)) return Integer.MAX_VALUE;
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return Integer.MAX_VALUE;
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            JSONObject payloadJson = new JSONObject(payload);
            if (payloadJson.has("exp")) {
                return payloadJson.getInt("exp");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Integer.MAX_VALUE;
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

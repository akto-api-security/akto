package com.akto.testing;

import com.akto.DaoInit;
import com.akto.dao.OtpTestDataDao;
import com.akto.dao.RecordedLoginInputDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.LoginFlowStepsDao;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.WorkflowTestResultsDao;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RecordedLoginFlowInput;
import com.akto.dto.api_workflow.Graph;
import com.akto.dto.api_workflow.Node;
import com.akto.dto.testing.*;
import com.akto.dto.type.RequestTemplate;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.JSONUtils;
import com.akto.utils.RedactSampleData;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.akto.util.RecordedLoginFlowUtil;

import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.common.protocol.types.Field.Str;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.json.JSONObject;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApiWorkflowExecutor {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ApiWorkflowExecutor.class);
    private static final Gson gson = new Gson();

    public static void main(String[] args) {
        DaoInit.init(new ConnectionString("mongodb://localhost:27017/admini"));
        Context.accountId.set(1_000_000);

        TestingRun testingRun = TestingRunDao.instance.findOne(Filters.eq("_id", new ObjectId("631cac6b09119467be6a1640")));
        WorkflowTestingEndpoints workflowTestingEndpoints = (WorkflowTestingEndpoints) testingRun.getTestingEndpoints();
        ApiWorkflowExecutor apiWorkflowExecutor = new ApiWorkflowExecutor();
        apiWorkflowExecutor.init(workflowTestingEndpoints.getWorkflowTest(), testingRun.getId(), null);
    }

    public void init(WorkflowTest workflowTest, ObjectId testingRunId, ObjectId testingRunSummaryId) {
        Graph graph = new Graph();
        graph.buildGraph(workflowTest);

        List<Node> nodes = graph.sort();
        Map<String, Object> valuesMap = new HashMap<>();

        int id = Context.now();
        WorkflowTestResult workflowTestResult = new WorkflowTestResult(id, workflowTest.getId(), new HashMap<>(), testingRunId, testingRunSummaryId);
        Map<String, WorkflowTestResult.NodeResult> testResultMap = workflowTestResult.getNodeResultMap();
        for (Node node: nodes) {
            WorkflowTestResult.NodeResult nodeResult;
            try {
                nodeResult = processNode(node, valuesMap, true);
            } catch (Exception e) {
                e.printStackTrace();
                List<String> testErrors = new ArrayList<>();
                testErrors.add("Something went wrong");
                nodeResult = new WorkflowTestResult.NodeResult("{}", false, testErrors);
            }

            testResultMap.put(node.getId(), nodeResult);

            if (nodeResult.getErrors().size() > 0) break;
        }

        WorkflowTestResultsDao.instance.insertOne(workflowTestResult);
    }

    public LoginFlowResponse runLoginFlow(WorkflowTest workflowTest, AuthMechanism authMechanism, LoginFlowParams loginFlowParams) throws Exception {
        Graph graph = new Graph();
        graph.buildGraph(workflowTest);

        ArrayList<Object> responses = new ArrayList<Object>();

        List<Node> nodes = graph.sort();

        if (loginFlowParams != null && loginFlowParams.getFetchValueMap()) {
            nodes.get(0).setId(loginFlowParams.getNodeId());
        }
        
        Map<String, Object> valuesMap = constructValueMap(loginFlowParams);

        for (Node node: nodes) {
            WorkflowTestResult.NodeResult nodeResult;
            try {
                nodeResult = processNode(node, valuesMap, false);
            } catch (Exception e) {
                e.printStackTrace();
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

        }

        for (AuthParam param : authMechanism.getAuthParams()) {
            try {
                String value = executeCode(param.getValue(), valuesMap);
                if (!param.getValue().equals(value) && value == null) {
                    return new LoginFlowResponse(responses, "auth param not found at specified path " + 
                    param.getValue(), false);
                }
                param.setValue(value);
            } catch(Exception e) {
                return new LoginFlowResponse(responses, "error resolving auth param " + param.getValue(), false);
            }
        }
        return new LoginFlowResponse(responses, null, true);
    }

    public Map<String, Object> constructValueMap(LoginFlowParams loginFlowParams) {
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

    public Map<String, Object> saveValueMapData(LoginFlowParams loginFlowParams, Map<String, Object> valuesMap) {

        Integer userId = loginFlowParams.getUserId();

        Bson filter = Filters.and(
            Filters.eq("userId", loginFlowParams.getUserId())
        );
        Bson update = Updates.set("valuesMap", valuesMap);
        LoginFlowStepsDao.instance.updateOne(filter, update);
        return valuesMap;
    }

    //todo: make this generic
    public WorkflowTestResult.NodeResult processOtpNode(Node node, Map<String, Object> valuesMap) {

        List<String> testErrors = new ArrayList<>();
        BasicDBObject resp = new BasicDBObject();
        BasicDBObject body = new BasicDBObject();
        BasicDBObject data = new BasicDBObject();
        String message;

        OtpTestData otpTestData = fetchOtpTestData(node, 4);
        String uuid = node.getWorkflowNodeDetails().getOtpRefUuid();

        if (otpTestData == null) {
            message = "otp data not received for uuid " + uuid;
            data.put("error", message);
            body.put("body", data);
            resp.put("response", body);
            testErrors.add(message);
            return new WorkflowTestResult.NodeResult(resp.toString(), false, testErrors);
        }
        try {
            String otp = extractOtpCode(otpTestData.getOtpText(), node.getWorkflowNodeDetails().getOtpRegex());
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

    private OtpTestData fetchOtpTestData(Node node, int retries) {
        OtpTestData otpTestData = null;
        for (int i=0; i<retries; i++) {
            try {
                int waitInSeconds = Math.min(node.getWorkflowNodeDetails().getWaitInSeconds(), 60);
                if (waitInSeconds > 0) {
                    loggerMaker.infoAndAddToDb("WAITING: " + waitInSeconds + " seconds", LogDb.TESTING);
                    Thread.sleep(waitInSeconds*1000);
                    loggerMaker.infoAndAddToDb("DONE WAITING!!!!", LogDb.TESTING);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            String uuid = node.getWorkflowNodeDetails().getOtpRefUuid();
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

    private String extractOtpCode(String text, String regex) {
        loggerMaker.infoAndAddToDb(regex, LogDb.TESTING);

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        String verificationCode = null;
        if (matcher.find()) {
            verificationCode = matcher.group(1);
        }

        return verificationCode;
    }

    public WorkflowTestResult.NodeResult processRecorderNode(Node node, Map<String, Object> valuesMap) {

        List<String> testErrors = new ArrayList<>();
        BasicDBObject resp = new BasicDBObject();
        BasicDBObject body = new BasicDBObject();
        BasicDBObject data = new BasicDBObject();
        String message;

        RecordedLoginFlowInput recordedLoginFlowInput = RecordedLoginInputDao.instance.findOne(new BasicDBObject());
        
        String token = fetchToken(recordedLoginFlowInput, 5);

        if (token == null){
            message = "error processing reorder node";
            data.put("error", message);
            body.put("body", data);
            resp.put("response", body);
            testErrors.add(message);
            return new WorkflowTestResult.NodeResult(resp.toString(), false, testErrors);
        }

        valuesMap.put(node.getId() + ".response.body.token", token);
        
        data.put("token", token);
        body.put("body", data);
        resp.put("response", body);
        return new WorkflowTestResult.NodeResult(resp.toString(), false, testErrors);
    }

    private String fetchToken(RecordedLoginFlowInput recordedLoginFlowInput, int retries) {

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

    public WorkflowTestResult.NodeResult processNode(Node node, Map<String, Object> valuesMap, Boolean allowAllStatusCodes) {
        if (node.getWorkflowNodeDetails().getType() == WorkflowNodeDetails.Type.RECORDED) {
            return processRecorderNode(node, valuesMap);
        }
        else if (node.getWorkflowNodeDetails().getType() == WorkflowNodeDetails.Type.OTP) {
            return processOtpNode(node, valuesMap);
        }
        else {
            return processApiNode(node, valuesMap, allowAllStatusCodes);
        }
    }


    public WorkflowTestResult.NodeResult processApiNode(Node node, Map<String, Object> valuesMap, Boolean allowAllStatusCodes) {
        loggerMaker.infoAndAddToDb("\n", LogDb.TESTING);
        loggerMaker.infoAndAddToDb("NODE: " + node.getId(), LogDb.TESTING);
        List<String> testErrors = new ArrayList<>();
        String nodeId = node.getId();
        WorkflowNodeDetails workflowNodeDetails = node.getWorkflowNodeDetails();
        WorkflowUpdatedSampleData updatedSampleData = workflowNodeDetails.getUpdatedSampleData();
        WorkflowNodeDetails.Type type = workflowNodeDetails.getType();
        boolean followRedirects = !workflowNodeDetails.getOverrideRedirect();

        OriginalHttpRequest request;
        try {
            request = buildHttpRequest(updatedSampleData, valuesMap);
            if (request == null) throw new Exception();
        } catch (Exception e) {
            e.printStackTrace();
            return new WorkflowTestResult.NodeResult(null, false, Collections.singletonList("Failed building request body"));
        }

        String url = request.getUrl();
        valuesMap.put(nodeId + ".request.url", url);

        populateValuesMap(valuesMap, request.getBody(), nodeId, request.getHeaders(),
                true, request.getQueryParams());

        OriginalHttpResponse response = null;
        int maxRetries = type.equals(WorkflowNodeDetails.Type.POLL) ? node.getWorkflowNodeDetails().getMaxPollRetries() : 1;

        try {
            int waitInSeconds = Math.min(workflowNodeDetails.getWaitInSeconds(),60);
            if (waitInSeconds > 0) {
                loggerMaker.infoAndAddToDb("WAITING: " + waitInSeconds + " seconds", LogDb.TESTING);
                Thread.sleep(waitInSeconds*1000);
                loggerMaker.infoAndAddToDb("DONE WAITING!!!!", LogDb.TESTING);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        for (int i = 0; i < maxRetries; i++) {
            try {
                if (i > 0) {
                    int sleep = node.getWorkflowNodeDetails().getPollRetryDuration();
                    loggerMaker.infoAndAddToDb("Waiting "+ (sleep/1000) +" before sending another request......", LogDb.TESTING);
                    Thread.sleep(sleep);
                }

                response = ApiExecutor.sendRequest(request, followRedirects, null);

                int statusCode = response.getStatusCode();

                String statusKey =   nodeId + "." + "response" + "." + "status_code";
                valuesMap.put(statusKey, statusCode);

                populateValuesMap(valuesMap, response.getBody(), nodeId, response.getHeaders(), false, null);
                if (!allowAllStatusCodes && (statusCode >= 400)) {
                    testErrors.add("process node failed with status code " + statusCode);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                testErrors.add("API request failed");
                e.printStackTrace();
            }
        }

        String message = null;
        try {
            message = RedactSampleData.convertOriginalReqRespToString(request, response);
        } catch (Exception e) {
            // todo: what to do if message = null
            e.printStackTrace();
        }

        boolean vulnerable = validateTest(workflowNodeDetails.getTestValidatorCode(), valuesMap);
        return new WorkflowTestResult.NodeResult(message,vulnerable, testErrors);

    }

    public boolean validateTest(String testValidatorCode, Map<String, Object> valuesMap) {
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
            e.printStackTrace();
        }

        return vulnerable;
    }

    public void populateValuesMap(Map<String, Object> valuesMap, String payloadStr, String nodeId, Map<String,
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
                e.printStackTrace();
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




    public OriginalHttpRequest buildHttpRequest(WorkflowUpdatedSampleData updatedSampleData, Map<String, Object> valuesMap) throws Exception {

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


    private final ScriptEngineManager factory = new ScriptEngineManager();

    public String executeCode(String ogPayload, Map<String, Object> valuesMap) throws Exception {
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
                se.printStackTrace();
            }

        }

        matcher.appendTail(sb); // todo: check if it needs to be called only after appendReplacement



        // evaluate JavaScript statement

        return sb.toString();
    }


    // todo: test invalid cases
    public String replaceVariables(String payload, Map<String, Object> valuesMap, boolean escapeString) throws Exception {
        String regex = "\\$\\{(x\\d+\\.[\\w\\-\\[\\].]+|AKTO\\.changes_info\\..*?)\\}"; // todo: integer inside brackets
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

        matcher.appendTail(sb); // todo: check if it needs to be called only after appendReplacement

        return sb.toString();
    }

    public static String generateKey(String nodeId, boolean isHeader, String param, boolean isRequest) {
        return StringUtils.joinWith("@", nodeId, isHeader, param, isRequest);
    }
}
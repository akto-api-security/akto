package com.akto.testing;

import com.akto.DaoInit;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.WorkflowTestResultsDao;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.api_workflow.Graph;
import com.akto.dto.api_workflow.Node;
import com.akto.dto.testing.*;
import com.akto.dto.type.RequestTemplate;
import com.akto.util.JSONUtils;
import com.akto.utils.RedactSampleData;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApiWorkflowExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ApiWorkflowExecutor.class);

    public static void main(String[] args) {
        DaoInit.init(new ConnectionString("mongodb://localhost:27017/admini"));
        Context.accountId.set(1_000_000);

        TestingRun testingRun = TestingRunDao.instance.findOne(Filters.eq("_id", new ObjectId("631cac6b09119467be6a1640")));
        WorkflowTestingEndpoints workflowTestingEndpoints = (WorkflowTestingEndpoints) testingRun.getTestingEndpoints();
        ApiWorkflowExecutor apiWorkflowExecutor = new ApiWorkflowExecutor();
        apiWorkflowExecutor.init(workflowTestingEndpoints.getWorkflowTest(), testingRun.getId());
    }

    public void init(WorkflowTest workflowTest, ObjectId testingRunId) {
        Graph graph = new Graph();
        graph.buildGraph(workflowTest);

        List<Node> nodes = graph.sort();
        Map<String, Object> valuesMap = new HashMap<>();

        int id = Context.now();
        WorkflowTestResult workflowTestResult = new WorkflowTestResult(id, workflowTest.getId(), new HashMap<>(), testingRunId);
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

    public void runLoginFlow(WorkflowTest workflowTest, AuthMechanism authMechanism) throws Exception {
        Graph graph = new Graph();
        graph.buildGraph(workflowTest);

        List<Node> nodes = graph.sort();
        Map<String, Object> valuesMap = new HashMap<>();

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

            if (nodeResult.getErrors().size() > 0) break;
        }

        for (AuthParam param : authMechanism.getAuthParams()) {
            try {
                String value = executeCode(param.getValue(), valuesMap);
                if (value == null) {
                    throw new Exception("auth param not found at specified path " + param.getValue());
                }
                param.setValue(value);
            } catch(Exception e) {
                throw new Exception("error resolving auth param " + param.getValue());
            }
        }
    }


    public WorkflowTestResult.NodeResult processNode(Node node, Map<String, Object> valuesMap, Boolean allowAllStatusCodes) {
        System.out.println("\n");
        System.out.println("NODE: " + node.getId());
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
        int maxRetries = type.equals(WorkflowNodeDetails.Type.POLL) ? 20 : 1;

        try {
            int waitInSeconds = Math.min(workflowNodeDetails.getWaitInSeconds(),60);
            if (waitInSeconds > 0) {
                System.out.println("WAITING: " + waitInSeconds + " seconds");
                Thread.sleep(waitInSeconds*1000);
                System.out.println("DONE WAITING!!!!");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        for (int i = 0; i < maxRetries; i++) {
            try {
                if (i > 0) {
                    int sleep = 6000;
                    logger.info("Waiting "+ (sleep/1000) +" before sending another request......");
                    Thread.sleep(sleep);
                }

                response = ApiExecutor.sendRequest(request, followRedirects);

                int statusCode = response.getStatusCode();

                if (!allowAllStatusCodes && (statusCode >= 400)) {
                    testErrors.add("process node failed with status code " + statusCode);
                }
                String statusKey =   nodeId + "." + "response" + "." + "status_code";
                valuesMap.put(statusKey, statusCode);

                populateValuesMap(valuesMap, response.getBody(), nodeId, response.getHeaders(), false, null);
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
            String code = replaceVariables(testValidatorCode, valuesMap);
            System.out.println("*******************************************************************");
            System.out.println("TEST VALIDATOR CODE:");
            System.out.println(code);
            Object o = engine.eval(code);
            System.out.println("TEST VALIDATOR RESULT: " + o.toString());
            System.out.println("*******************************************************************");
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
            for (String val: headers.get(headerName)) {
                String key = nodeId + "." + reqOrResp + "." + "header" + "." + headerName;
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
            System.out.println("requestUrl: " + requestUrl);
            String rawUrl = executeCode(requestUrl, valuesMap);
            System.out.println("rawUrl: " + requestUrl);
            // this url might contain urlQueryParams. We need to move it queryParams
            String[] rawUrlArr = rawUrl.split("\\?");
            request.setUrl(rawUrlArr[0]);
            if (rawUrlArr.length > 1) {
                queryFromReplacedUrl = rawUrlArr[1];
            }
            System.out.println("final url: " + request.getUrl());
            System.out.println("queryFromReplacedUrl: " + queryFromReplacedUrl);
        }

        if (userSuppliedQueryParamsNullOrEmpty) {
            System.out.println("setting null");
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
            System.out.println("user has supplied query params");
            String finalQueryParams = executeCode(queryParams, valuesMap);
            System.out.println("finalQueryParams: " + finalQueryParams);
            if (queryFromReplacedUrlNullOrEmpty) {
                request.setQueryParams(finalQueryParams);
            } else {
                // combine original query params and user defined query params and latter overriding former
                String combinedQueryParams = OriginalHttpRequest.combineQueryParams(queryFromReplacedUrl, finalQueryParams);
                System.out.println("combinedQueryParams: " + combinedQueryParams);
                request.setQueryParams(combinedQueryParams);
            }
        } else if (!queryFromReplacedUrlNullOrEmpty) {
            request.setQueryParams(queryFromReplacedUrl);
        }

        return request;
    }


    private final ScriptEngineManager factory = new ScriptEngineManager();

    public String executeCode(String ogPayload, Map<String, Object> valuesMap) throws Exception {
        String variablesReplacedPayload = replaceVariables(ogPayload,valuesMap);

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
    public String replaceVariables(String payload, Map<String, Object> valuesMap) throws Exception {
        String regex = "\\$\\{(x\\d+\\.[\\w\\[\\].]+|AKTO\\.changes_info\\..*?)\\}"; // todo: integer inside brackets
        Pattern p = Pattern.compile(regex);

        // replace with values
        Matcher matcher = p.matcher(payload);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            if (key == null) continue;
            Object obj = valuesMap.get(key);
            if (obj == null) {
                // todo: check for nested objects
                logger.error("couldn't find: " + key);
                throw new Exception("Couldn't find " + key);
            }
            String val = obj.toString()
                    .replace("\\", "\\\\")
                    .replace("\t", "\\t")
                    .replace("\b", "\\b")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\f", "\\f")
                    .replace("\'", "\\'")
                    .replace("\"", "\\\"");
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

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
import com.akto.runtime.URLAggregator;
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

    public void init(WorkflowTest workflowTest, ObjectId testingRunId) {
        Graph graph = new Graph();
        graph.buildGraph(workflowTest);

        List<Node> nodes = graph.sort();
        Map<String, Object> valuesMap = new HashMap<>();

        int id = Context.now();
        WorkflowTestResult workflowTestResult = new WorkflowTestResult(id, workflowTest.getId(), new HashMap<>(), testingRunId);
        Map<String, TestResult> testResultMap = workflowTestResult.getTestResultMap();
        for (Node node: nodes) {
            TestResult testResult;
            try {
                testResult = processNode(node, valuesMap);
            } catch (Exception e) {
                e.printStackTrace();
                List<TestResult.TestError> testErrors = new ArrayList<>();
                testErrors.add(TestResult.TestError.SOMETHING_WENT_WRONG);
                testResult = new TestResult("{}", false, testErrors, new ArrayList<>());
            }

            testResultMap.put(node.getId(), testResult);

            if (testResult.getErrors().size() > 0) break;
        }

        WorkflowTestResultsDao.instance.insertOne(workflowTestResult);
    }

    public TestResult processNode(Node node, Map<String, Object> valuesMap) {
        List<TestResult.TestError> testErrors = new ArrayList<>();
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
            return new TestResult(null, false, Collections.singletonList(TestResult.TestError.FAILED_BUILDING_REQUEST_BODY), new ArrayList<>());
        }

        populateValuesMap(valuesMap, request.getBody(), node.getId(), request.getHeaders(),
                true, request.getQueryParams());

        OriginalHttpResponse response = null;
        int maxRetries = type.equals(WorkflowNodeDetails.Type.POLL) ? 20 : 1;
        for (int i = 0; i < maxRetries; i++) {
            try {
                if (i > 0) {
                    int sleep = 6000;
                    logger.info("Waiting "+ (sleep/1000) +" before sending another request......");
                    Thread.sleep(sleep);
                }

                response = ApiExecutor.sendRequest(request, followRedirects);
                if (HttpResponseParams.validHttpResponseCode(response.getStatusCode())) {
                    populateValuesMap(valuesMap, response.getBody(), node.getId(), response.getHeaders(), false, null);
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                testErrors.add(TestResult.TestError.API_REQUEST_FAILED);
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

        return new TestResult(message, false, testErrors, new ArrayList<>());
    }

    public void populateValuesMap(Map<String, Object> valuesMap, String payloadStr, String nodeId, Map<String,
            List<String>> headers, boolean isRequest, String queryParams) {
        boolean isList = false;
        if (payloadStr == null) payloadStr = "{}";
        if (payloadStr.startsWith("[")) {
            payloadStr = "{\"json\": "+payloadStr+"}";
            isList = true;
        }

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

        String reqOrResp = isRequest ? "request"  : "response";

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

        if (requestUrl != null) {
            String rawUrl = executeCode(requestUrl, valuesMap);
            // this url might contain urlQueryParams. We need to move it queryParams
            String[] rawUrlArr = rawUrl.split("\\?");
            request.setUrl(rawUrlArr[0]);
            if (rawUrlArr.length > 1) {
                request.setQueryParams(rawUrlArr[1]);
            }
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

        if (queryParams != null) {
            String finalQueryParams = executeCode(queryParams, valuesMap);
            String ogQuery = request.getQueryParams();
            if (ogQuery == null || ogQuery.isEmpty()) {
                request.setQueryParams(finalQueryParams);
            } else {
                // combine original query params and user defined query params and latter overriding former
                String combinedQueryParams = combineQueryParams(ogQuery, finalQueryParams);
                request.setQueryParams(combinedQueryParams);
            }
        }

        return request;
    }

    // queryString2 overrides queryString1 use accordingly
    public String combineQueryParams(String queryString1, String queryString2) {
        if (queryString1 == null) return queryString2;
        if (queryString2 == null) return queryString1;

        String mockUrl1 = "url?" + queryString1;
        String mockUrl2 = "url?" + queryString2;

        BasicDBObject queryParamsObject1 = RequestTemplate.getQueryJSON(mockUrl1);
        BasicDBObject queryParamsObject2 = RequestTemplate.getQueryJSON(mockUrl2);

        for (String key: queryParamsObject2.keySet()) {
            queryParamsObject1.put(key, queryParamsObject2.get(key));
        }

        String json = queryParamsObject1.toJson();

        return ApiExecutor.getRawQueryFromJson(json);
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
        String regex = "\\$\\{x(\\d+)\\.([\\w\\[\\].]+)\\}"; // todo: integer inside brackets
        Pattern p = Pattern.compile(regex);

        // replace with values
        Matcher matcher = p.matcher(payload);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String nodeId = "x"+matcher.group(1);
            String param = matcher.group(2);
            if (param == null || param.isEmpty()) continue;
            String key = nodeId+"."+param;
            Object obj = valuesMap.get(key);
            if (obj == null) {
                // todo: check for nested objects
                logger.error("couldn't find: " + key);
                throw new Exception("Couldn't find " + key);
            }
            String val = obj.toString();
            matcher.appendReplacement(sb, val);
        }

        matcher.appendTail(sb); // todo: check if it needs to be called only after appendReplacement

        return sb.toString();
    }

    public static String generateKey(String nodeId, boolean isHeader, String param, boolean isRequest) {
        return StringUtils.joinWith("@", nodeId, isHeader, param, isRequest);
    }
}

package com.akto.testing;

import com.akto.DaoInit;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.WorkflowTestResultsDao;
import com.akto.dao.testing.WorkflowTestsDao;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.api_workflow.Graph;
import com.akto.dto.api_workflow.Node;
import com.akto.dto.testing.*;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.parsers.HttpCallParser;
import com.akto.util.JSONUtils;
import com.akto.utils.RedactSampleData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApiWorkflowExecutor {

    public static void main(String[] args) {
        DaoInit.init(new ConnectionString("mongodb://localhost:27017/admini"));
        Context.accountId.set(1_000_000);
        TestingRun testingRun = TestingRunDao.instance.findOne(new BasicDBObject());
        ApiWorkflowExecutor apiWorkflowExecutor = new ApiWorkflowExecutor();
        WorkflowTestingEndpoints workflowTestingEndpoints = (WorkflowTestingEndpoints) testingRun.getTestingEndpoints();
        apiWorkflowExecutor.init(workflowTestingEndpoints.getWorkflowTest(), testingRun.getId());

    }

    public void init(WorkflowTest workflowTest, ObjectId testingRunId) {
        Graph graph = new Graph();
        graph.buildGraph(workflowTest);

        List<Node> nodes = graph.sort();
        Map<String, Object> valuesMap = new HashMap<>();

        int id = Context.now();
        WorkflowTestResult workflowTestResult = new WorkflowTestResult(id, workflowTest.getId(), new HashMap<>(), testingRunId);
        for (Node node: nodes) {
            List<TestResult.TestError> testErrors = new ArrayList<>();
            WorkflowNodeDetails workflowNodeDetails = node.getWorkflowNodeDetails();
            WorkflowUpdatedSampleData updatedSampleData = workflowNodeDetails.getUpdatedSampleData();

            HttpResponseParams originalHttpResponseParams = buildHttpResponseParam(updatedSampleData, valuesMap);
            if (originalHttpResponseParams == null) return;

            HttpRequestParams originalHttpRequestParams = originalHttpResponseParams.getRequestParams();

            HttpResponseParams httpResponseParams;
            try {
                httpResponseParams = ApiExecutor.sendRequest(originalHttpRequestParams);
                populateValuesMap(valuesMap, httpResponseParams, node.getId());
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }

            String message = null;
            try {
                message = RedactSampleData.convertHttpRespToOriginalString(httpResponseParams);
            } catch (Exception e) {
                // todo: what to do if message = null
                e.printStackTrace();
            }

            workflowTestResult.addNodeResult(node.getId(), message, testErrors);
        }

        WorkflowTestResultsDao.instance.insertOne(workflowTestResult);
    }

    public void populateValuesMap(Map<String, Object> valuesMap, HttpResponseParams httpResponseParams,
                                  String nodeId) {
        String respPayload = httpResponseParams.getPayload();
        if (respPayload == null) return;
        populateValuesMap(valuesMap, respPayload, nodeId, httpResponseParams.getHeaders(), false);

        HttpRequestParams httpRequestParams = httpResponseParams.requestParams;
        String requestPayload = httpRequestParams.getPayload();
        populateValuesMap(valuesMap, requestPayload, nodeId, httpRequestParams.getHeaders(),true);

    }

    public void populateValuesMap(Map<String, Object> valuesMap, String payloadStr, String nodeId, Map<String,
            List<String>> headers, boolean isRequest) {
        boolean isList = false;
        if (payloadStr.startsWith("[")) {
            payloadStr = "{\"json\": "+payloadStr+"}";
            isList = true;
        }

        BasicDBObject payloadObj;
        try {
            payloadObj = BasicDBObject.parse(payloadStr);
        } catch (Exception e) {
            e.printStackTrace();
            payloadObj = BasicDBObject.parse("{}");
        }

        Object obj = payloadObj;
        if (isList) {
            obj = payloadObj.get("json");
        }

        BasicDBObject flattened = JSONUtils.flattenWithDots(obj);

        String reqOrResp = isRequest ? "request"  : "response";

        for (String param: flattened.keySet()) {
            String key = nodeId + "." + reqOrResp + "." + "body" + "." + param;
            valuesMap.put(key, flattened.get(param));
        }

        for (String headerName: headers.keySet()) {
            for (String val: headers.get(headerName)) {
                String key = nodeId + "." + reqOrResp + "." + "header" + "." + headerName;
                valuesMap.put(key, val);
            }
        }

    }



    public HttpResponseParams buildHttpResponseParam(WorkflowUpdatedSampleData updatedSampleData, Map<String, Object> valuesMap) {

        String sampleData = updatedSampleData.getOrig();
        HttpResponseParams httpResponseParams = null;
        try {
            httpResponseParams = HttpCallParser.parseKafkaMessage(sampleData);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        String queryParams = updatedSampleData.getQueryParams(); // todo:
        String requestHeaders = updatedSampleData.getRequestHeaders();
        String requestPayload = updatedSampleData.getRequestPayload();

        HttpRequestParams httpRequestParams = httpResponseParams.requestParams;

        String regex = "\\$\\{x(\\d+)\\.([\\w\\[\\].]+)\\}"; // todo: integer inside brackets
        Pattern p = Pattern.compile(regex);

        if (requestPayload != null) {
            String finalPayload = replaceVariables(p,requestPayload, valuesMap);
            httpRequestParams.setPayload(finalPayload);
        }

        if (requestHeaders != null) {
            String finalPayload = replaceVariables(p, requestHeaders, valuesMap);
            Map<String, List<String>> res = HttpCallParser.getHeaders(finalPayload);
            httpRequestParams.setHeaders(res);
        }

        return httpResponseParams;
    }

    // todo: test invalid cases
    public String replaceVariables(Pattern p, String payload, Map<String, Object> valuesMap)  {
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
                System.out.println("couldn't find: ");
                System.out.println(key);
                System.out.println(valuesMap.keySet());
                return null;
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

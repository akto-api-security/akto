package com.akto.testing;

import com.akto.DaoInit;
import com.akto.dao.OtpMessagesDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.WorkflowTestResultsDao;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.OTPMessage;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.api_workflow.Graph;
import com.akto.dto.api_workflow.Node;
import com.akto.dto.testing.*;
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

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApiWorkflowExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ApiWorkflowExecutor.class);

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
            WorkflowNodeDetails.Type type = workflowNodeDetails.getType();

            OriginalHttpRequest request = buildHttpRequest(updatedSampleData, valuesMap);
            if (request == null) return;
            populateValuesMap(valuesMap, request.getBody(), node.getId(), request.getHeaders(),
                    true, request.getQueryParams());

            OriginalHttpResponse response = null;
            int maxRetries = type.equals(WorkflowNodeDetails.Type.POLL) ? 20 : 1;
            for (int i = 0; i < maxRetries; i++) {
                try {
                    System.out.println(request.getFullUrlWithParams() + " " + request.getBody());
                    response = ApiExecutor.sendRequest(request);
                    if (HttpResponseParams.validHttpResponseCode(response.getStatusCode())) {
                        populateValuesMap(valuesMap, response.getBody(), node.getId(), response.getHeaders(), false, null);
                        break;
                    }
                    int sleep = 6000;
                    logger.info("Waiting "+ (sleep/1000) +" before sending another request......");
                    Thread.sleep(sleep);
                } catch (Exception e) {
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

            workflowTestResult.addNodeResult(node.getId(), message, testErrors);
        }

        WorkflowTestResultsDao.instance.insertOne(workflowTestResult);
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
                payloadObj = URLAggregator.getQueryJSON(mockUrl);
            } else {
                payloadObj = BasicDBObject.parse("{}");
            }
        }

        BasicDBObject queryParamsObject = null;
        if (queryParams != null) {
            try {
                String mockUrl = "url?"+ queryParams; // because getQueryJSON function needs complete url
                queryParamsObject = URLAggregator.getQueryJSON(mockUrl);
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
                valuesMap.put(key, flattened.get(param));
            }
        }

        for (String headerName: headers.keySet()) {
            for (String val: headers.get(headerName)) {
                String key = nodeId + "." + reqOrResp + "." + "header" + "." + headerName;
                valuesMap.put(key, val);
            }
        }

    }




    public OriginalHttpRequest buildHttpRequest(WorkflowUpdatedSampleData updatedSampleData, Map<String, Object> valuesMap) {

        String sampleData = updatedSampleData.getOrig();
        OriginalHttpRequest request = new OriginalHttpRequest();
        request.buildFromSampleMessage(sampleData);

        String queryParams = updatedSampleData.getQueryParams();
        String requestHeaders = updatedSampleData.getRequestHeaders();
        String requestPayload = updatedSampleData.getRequestPayload();
        String requestUrl = updatedSampleData.getRequestUrl();

        String regex = "\\$\\{x(\\d+)\\.([\\w\\[\\].]+)\\}"; // todo: integer inside brackets
        Pattern p = Pattern.compile(regex);

        if (requestUrl != null) {
            String rawUrl = replaceVariables(p, requestUrl, valuesMap);
            // this url might contain urlQueryParams. We need to move it queryParams
            String[] rawUrlArr = rawUrl.split("\\?");
            request.setUrl(rawUrlArr[0]);
            if (rawUrlArr.length > 1) {
                request.setQueryParams(rawUrlArr[1]);
            }
        }

        if (requestPayload != null) {
            String finalPayload = replaceVariables(p,requestPayload, valuesMap);
            request.setBody(finalPayload);
        }

        if (requestHeaders != null) {
            String finalPayload = replaceVariables(p, requestHeaders, valuesMap);
            Map<String, List<String>> res = OriginalHttpRequest.buildHeadersMap(finalPayload);
            request.setHeaders(res);
        }

        if (queryParams != null) {
            String ogQuery = request.getQueryParams();
            if (ogQuery == null || ogQuery.isEmpty()) {
                request.setQueryParams(queryParams);
            } else {
                request.setQueryParams(ogQuery+"&"+ queryParams);
            }
        }

        return request;
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

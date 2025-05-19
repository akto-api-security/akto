package com.akto.testing.workflow_node_executor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Collections;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.api_workflow.Node;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.WorkflowNodeDetails;
import com.akto.dto.testing.WorkflowTestResult;
import com.akto.dto.testing.WorkflowUpdatedSampleData;
import com.akto.dto.testing.WorkflowTestResult.NodeResult;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.test_editor.execution.Memory;
import com.akto.testing.ApiExecutor;
import com.akto.testing.Utils;
import static com.akto.runtime.utils.Utils.convertOriginalReqRespToString;

public class ApiNodeExecutor extends NodeExecutor {
    
    private static final LoggerMaker loggerMaker = new LoggerMaker(ApiNodeExecutor.class);

    public ApiNodeExecutor(boolean allowAllCombinations) {
        super(allowAllCombinations);
    }

    public NodeResult processNode(Node node, Map<String, Object> valuesMap, Boolean allowAllStatusCodes, boolean debug, List<TestingRunResult.TestLog> testLogs, Memory memory) {
        loggerMaker.debugAndAddToDb("\n", LogDb.TESTING);
        loggerMaker.debugAndAddToDb("NODE: " + node.getId(), LogDb.TESTING);
        List<String> testErrors = new ArrayList<>();
        String nodeId = node.getId();
        WorkflowNodeDetails workflowNodeDetails = node.getWorkflowNodeDetails();
        WorkflowUpdatedSampleData updatedSampleData = workflowNodeDetails.getUpdatedSampleData();
        WorkflowNodeDetails.Type type = workflowNodeDetails.getType();
        boolean followRedirects = !workflowNodeDetails.getOverrideRedirect();

        OriginalHttpRequest request;
        try {
            request =  Utils.buildHttpRequest(updatedSampleData, valuesMap);
            if (request == null) throw new Exception();
        } catch (Exception e) {
            ;
            return new WorkflowTestResult.NodeResult(null, false, Collections.singletonList("Failed building request body"));
        }

        String url = request.getUrl();
        valuesMap.put(nodeId + ".request.url", url);

        Utils.populateValuesMap(valuesMap, request.getBody(), nodeId, request.getHeaders(),
                true, request.getQueryParams());

        OriginalHttpResponse response = null;
        int maxRetries = type.equals(WorkflowNodeDetails.Type.POLL) ? workflowNodeDetails.getMaxPollRetries() : 1;

        try {
            int waitInSeconds = Math.min(workflowNodeDetails.getWaitInSeconds(),60);
            if (waitInSeconds > 0) {
                loggerMaker.debugAndAddToDb("WAITING: " + waitInSeconds + " seconds", LogDb.TESTING);
                Thread.sleep(waitInSeconds*1000);
                loggerMaker.debugAndAddToDb("DONE WAITING!!!!", LogDb.TESTING);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        for (int i = 0; i < maxRetries; i++) {
            try {
                if (i > 0) {
                    int sleep = workflowNodeDetails.getPollRetryDuration();
                    loggerMaker.debugAndAddToDb("Waiting "+ (sleep/1000) +" before sending another request......", LogDb.TESTING);
                    Thread.sleep(sleep);
                }

                response = ApiExecutor.sendRequest(request, followRedirects, null, debug, testLogs, com.akto.test_editor.Utils.SKIP_SSRF_CHECK);

                int statusCode = response.getStatusCode();

                String statusKey =   nodeId + "." + "response" + "." + "status_code";
                valuesMap.put(statusKey, statusCode);

                Utils.populateValuesMap(valuesMap, response.getBody(), nodeId, response.getHeaders(), false, null);
                if (!allowAllStatusCodes && (statusCode >= 400)) {
                    testErrors.add("process node failed with status code " + statusCode);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                testErrors.add("API request failed");
                ;
            }
        }

        String message = null;
        try {
            message = convertOriginalReqRespToString(request, response);
        } catch (Exception e) {
            ;
        }

        boolean vulnerable = Utils.validateTest(workflowNodeDetails.getTestValidatorCode(), valuesMap);
        return new WorkflowTestResult.NodeResult(message,vulnerable, testErrors);

    }

}

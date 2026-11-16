package com.akto.testing.workflow_node_executor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Set;

import com.akto.dto.ApiInfo;
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
import com.akto.testing.Main;
import com.akto.testing.Utils;

import static com.akto.runtime.utils.Utils.convertOriginalReqRespToString;

public class ApiNodeExecutor extends NodeExecutor {
    
    private static final LoggerMaker loggerMaker = new LoggerMaker(ApiNodeExecutor.class, LogDb.TESTING);

    // login-flow steps resolve placeholders in login mode: stepK aliases, ${cookies...}, strict (libs/utils .../login_flow/CLAUDE.md)
    private final boolean loginFlow;

    public ApiNodeExecutor() {
        this(false);
    }

    public ApiNodeExecutor(boolean loginFlow) {
        this.loginFlow = loginFlow;
    }

    public NodeResult processNode(Node node, Map<String, Object> valuesMap, Boolean allowAllStatusCodes, boolean debug, List<TestingRunResult.TestLog> testLogs, Memory memory) {
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
            request = Utils.buildHttpRequest(updatedSampleData, valuesMap, loginFlow ? nodeId : null);
            if (request == null) throw new Exception("request could not be built");
        } catch (Exception e) {
            // keep the cause (e.g. "Couldn't find x3.response.body.forward"); callers add the node/step label
            String error = "Failed building request: " + com.akto.testing.workflow_node_executor.Utils.describeCause(e);
            loggerMaker.warnAndAddToDb("API_NODE_BUILD_FAILED [Node " + nodeId + "]: " + error);
            return new WorkflowTestResult.NodeResult(null, false, Collections.singletonList(error));
        }

        String url = request.getUrl();
        valuesMap.put(nodeId + ".request.url", url);
        Utils.populateValuesMap(valuesMap, request.getBody(), nodeId, request.getHeaders(),
                true, request.getQueryParams());
        loggerMaker.warnAndAddToDb("VALUES_MAP_AFTER_POPULATE [Node " + nodeId + "]: keys=" + valuesMap.size());

        OriginalHttpResponse response = null;
        int maxRetries = type.equals(WorkflowNodeDetails.Type.POLL) ? workflowNodeDetails.getMaxPollRetries() : 1;

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
                    int sleep = workflowNodeDetails.getPollRetryDuration();
                    loggerMaker.infoAndAddToDb("Waiting "+ (sleep/1000) +" before sending another request......", LogDb.TESTING);
                    Thread.sleep(sleep);
                }

                // Names and sizes only: values carry passwords, cookies and tokens.
                loggerMaker.warnAndAddToDb("API_NODE_REQUEST [Node " + nodeId + "]: method=" + request.getMethod() +
                    ", url=" + request.getUrl() +
                    ", queryParamsLength=" + lengthOf(request.getQueryParams()) +
                    ", headerNames=" + headerNames(request.getHeaders()) +
                    ", bodyLength=" + lengthOf(request.getBody()));

                response = ApiExecutor.sendRequest(request, followRedirects, null, debug, testLogs, Main.SKIP_SSRF_CHECK);

                loggerMaker.warnAndAddToDb("API_NODE_RESPONSE [Node " + nodeId + "]: statusCode=" + response.getStatusCode() +
                    ", headerNames=" + headerNames(response.getHeaders()) +
                    ", bodyLength=" + lengthOf(response.getBody()));

                int statusCode = response.getStatusCode();

                String statusKey =   nodeId + "." + "response" + "." + "status_code";
                valuesMap.put(statusKey, statusCode);
                Utils.populateValuesMap(valuesMap, response.getBody(), nodeId, response.getHeaders(), false, null);
                Utils.populateRegexValues(valuesMap, response.getBody(), nodeId, workflowNodeDetails.getOtpRegex());
                loggerMaker.warnAndAddToDb("VALUES_MAP_AFTER_POPULATE [Node " + nodeId + "]: keys=" + valuesMap.size());
                if (!allowAllStatusCodes && (statusCode >= 400)) {
                    testErrors.add("process node failed with status code " + statusCode);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                testErrors.add("interrupted before the request completed");
                break;
            } catch (Exception e) {
                // keep the cause (e.g. "Api Call failed: Connection refused", "SSRF attack attempt")
                testErrors.add("API request failed: " + com.akto.testing.workflow_node_executor.Utils.describeCause(e));
            }
        }

        String message = null;
        try {
            message = convertOriginalReqRespToString(request, response);
        } catch (Exception e) {
            ;
        }

        String validator = workflowNodeDetails.getTestValidatorCode();
        Boolean vulnerableOrNotEvaluated = Utils.evaluateTestValidator(validator, valuesMap);
        if (Utils.hasTestValidator(validator) && vulnerableOrNotEvaluated == null) {
            // no JS engine, or the script failed: say so instead of letting it read as "not vulnerable"
            testErrors.add(Utils.TEST_VALIDATOR_NOT_EVALUATED);
        }
        boolean vulnerable = Boolean.TRUE.equals(vulnerableOrNotEvaluated);
        return new WorkflowTestResult.NodeResult(message,vulnerable, testErrors);

    }

    private static int lengthOf(String s) {
        return s == null ? 0 : s.length();
    }

    private static Set<String> headerNames(Map<String, List<String>> headers) {
        return headers == null ? Collections.<String>emptySet() : headers.keySet();
    }

}

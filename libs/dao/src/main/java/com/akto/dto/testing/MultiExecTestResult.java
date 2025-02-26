package com.akto.dto.testing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import com.akto.dto.testing.TestResult.TestError;

import com.akto.dto.testing.TestResult.Confidence;
import com.akto.dto.testing.WorkflowTestResult.NodeResult;

public class MultiExecTestResult extends GenericTestResult {

    public static final String NODE_RESULT_MAP = "nodeResultMap";
    Map<String, NodeResult> nodeResultMap;
    private List<String> executionOrder;

    public MultiExecTestResult(Map<String, NodeResult> nodeResultMap, boolean vulnerable, Confidence confidence, List<String> executionOrder) {
        super(vulnerable, confidence);
        this.nodeResultMap = nodeResultMap;
        this.executionOrder = executionOrder;
    }

    public MultiExecTestResult() {
    }

    public Map<String, NodeResult> getNodeResultMap() {
        return nodeResultMap;
    }

    public void setNodeResultMap(Map<String, NodeResult> nodeResultMap) {
        this.nodeResultMap = nodeResultMap;
    }

    public List<String> getExecutionOrder() {
        if (executionOrder == null) {
            executionOrder = new ArrayList<>();
        }
        return executionOrder;
    }

    public void setExecutionOrder(List<String> executionOrder) {
        this.executionOrder = executionOrder;
    }

    public List<GenericTestResult> convertToExistingTestResult(TestingRunResult testingRunResult) {
        List<GenericTestResult> runResults = new ArrayList<>();
        
        Map<String, NodeResult> nodeResultMap = this.getNodeResultMap();
        TestResult res;
        for (int i=0; i < this.executionOrder.size(); i++) {
            String k = this.executionOrder.get(i);
            NodeResult nodeRes = nodeResultMap.get(k);
            String confidence = "HIGH";
            List<String> errors = nodeRes.getErrors();
            List<String> messageList = Arrays.asList(nodeRes.getMessage().split("\"request\": "));
            boolean vulnerable = nodeRes.isVulnerable();
            double percentageMatch = 0;
            // pick from workflow
            Map<String, WorkflowNodeDetails> mapNodeIdToWorkflowNodeDetails = testingRunResult.getWorkflowTest().getMapNodeIdToWorkflowNodeDetails();
            YamlNodeDetails workflowNodeDetails = (YamlNodeDetails) mapNodeIdToWorkflowNodeDetails.get(k);
            String originalMessage = workflowNodeDetails.getOriginalMessage();
            if (messageList.size() <= 1) {
                List<String> error_messages = new ArrayList<>();
                if (errors.size() > 0) {
                    error_messages = errors;
                } else {
                    error_messages.add(TestError.NO_API_REQUEST.getMessage());
                    /*
                     * In case no API requests are created,
                     * do not store the original message
                     */
                    originalMessage = "";
                }
                runResults.add(new TestResult(null, originalMessage, error_messages, 0, false, TestResult.Confidence.HIGH, null));
            }

            for (int j = 1; j<messageList.size(); j++) {
                String message = "{\"request\": " + messageList.get(j);
                if (j != messageList.size() - 1) {
                    message = message.substring(0, message.length() - 3);
                } else {
                    message = message.substring(0, message.length() - 2);
                    message = message + "}";
                }
                res = new TestResult(message, originalMessage, errors, percentageMatch, vulnerable, Confidence.HIGH, null);
                runResults.add(res);
            }
        }

        return runResults;
    }

    @Override
    public List<String> getResponses() {
        List<String> ret = new ArrayList<>();
        
        Map<String, NodeResult> nodeResultMap = this.getNodeResultMap();
        for (int i=0; i < this.executionOrder.size(); i++) {
            String k = this.executionOrder.get(i);
            NodeResult nodeRes = nodeResultMap.get(k);
            List<String> messageList = Arrays.asList(nodeRes.getMessage().split("\"request\": "));

            for (int j = 1; j<messageList.size(); j++) {
                String message = "{\"request\": " + messageList.get(j);
                if (j != messageList.size() - 1) {
                    message = message.substring(0, message.length() - 3);
                } else {
                    message = message.substring(0, message.length() - 2);
                    message = message + "}";
                }
                ret.add(message);
            }       
        }

        return ret;
    }
}

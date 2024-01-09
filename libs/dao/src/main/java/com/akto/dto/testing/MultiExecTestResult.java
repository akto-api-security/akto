package com.akto.dto.testing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import com.akto.dto.testing.TestResult.TestError;

import com.akto.dto.testing.TestResult.Confidence;
import com.akto.dto.testing.WorkflowTestResult.NodeResult;
import com.akto.dto.testing.NodeDetails.YamlNodeDetails;

public class MultiExecTestResult extends GenericTestResult {

    Map<String, NodeResult> nodeResultMap;

    public MultiExecTestResult(Map<String, NodeResult> nodeResultMap, boolean vulnerable, Confidence confidence) {
        super(vulnerable, confidence);
        this.nodeResultMap = nodeResultMap;
    }

    public MultiExecTestResult() {
    }

    public Map<String, NodeResult> getNodeResultMap() {
        return nodeResultMap;
    }

    public void setNodeResultMap(Map<String, NodeResult> nodeResultMap) {
        this.nodeResultMap = nodeResultMap;
    }

    public List<GenericTestResult> convertToExistingTestResult(TestingRunResult testingRunResult) {
        List<GenericTestResult> runResults = new ArrayList<>();
        
        Map<String, NodeResult> nodeResultMap = this.getNodeResultMap();
        TestResult res;
        for (String k: nodeResultMap.keySet()) {
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
                error_messages.add(TestError.NO_API_REQUEST.getMessage());
                runResults.add(new TestResult(null, originalMessage, error_messages, 0, false, TestResult.Confidence.HIGH, null));
            }

            for (int i = 1; i<messageList.size(); i++) {
                String message = "{\"request\": " + messageList.get(i);
                if (i != messageList.size() - 1) {
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

}

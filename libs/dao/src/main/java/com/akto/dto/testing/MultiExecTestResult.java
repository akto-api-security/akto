package com.akto.dto.testing;

import java.util.Map;

import com.akto.dto.testing.TestResult.Confidence;
import com.akto.dto.testing.WorkflowTestResult.NodeResult;

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

}

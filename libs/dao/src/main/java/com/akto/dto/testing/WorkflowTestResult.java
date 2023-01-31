package com.akto.dto.testing;

import org.bson.types.ObjectId;

import java.util.*;

public class WorkflowTestResult {
    private int id;
    private int workflowTestId;
    public static final String _TEST_RUN_ID = "testRunId";
    private ObjectId testRunId;
    public static final String TESTING_RUN_RESULT_SUMMARY_ID = "testingRunResultSummaryId";

    private ObjectId testingRunResultSummaryId;
    Map<String, NodeResult> nodeResultMap;

    public static class NodeResult {
        private String message;
        private boolean vulnerable;
        private List<String> errors;

        public NodeResult() { }

        public NodeResult(String message, boolean vulnerable, List<String> errors) {
            this.message = message;
            this.vulnerable = vulnerable;
            this.errors = errors;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public boolean isVulnerable() {
            return vulnerable;
        }

        public void setVulnerable(boolean vulnerable) {
            this.vulnerable = vulnerable;
        }

        public List<String> getErrors() {
            return errors;
        }

        public void setErrors(List<String> errors) {
            this.errors = errors;
        }
    }

    public WorkflowTestResult() {
    }

    public WorkflowTestResult(int id, int workflowTestId, Map<String, NodeResult> nodeResultMap, ObjectId testRunId, ObjectId testingRunResultSummaryId) {
        this.id = id;
        this.workflowTestId = workflowTestId;
        this.nodeResultMap = nodeResultMap;
        this.testRunId = testRunId;
        this.testingRunResultSummaryId = testingRunResultSummaryId;
    }

    public void addNodeResult(String nodeId, String message, List<String> testErrors) {
        if (this.nodeResultMap == null) this.nodeResultMap = new HashMap<>();
        NodeResult nodeResult = new NodeResult(message, false, testErrors);
        this.nodeResultMap.put(nodeId, nodeResult);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getWorkflowTestId() {
        return workflowTestId;
    }

    public void setWorkflowTestId(int workflowTestId) {
        this.workflowTestId = workflowTestId;
    }

    public Map<String, NodeResult> getNodeResultMap() {
        return nodeResultMap;
    }

    public void setNodeResultMap(Map<String, NodeResult> nodeResultMap) {
        this.nodeResultMap = nodeResultMap;
    }

    public ObjectId getTestRunId() {
        return testRunId;
    }

    public void setTestRunId(ObjectId testRunId) {
        this.testRunId = testRunId;
    }

    public ObjectId getTestingRunResultSummaryId() {
        return testingRunResultSummaryId;
    }

    public void setTestingRunResultSummaryId(ObjectId testingRunResultSummaryId) {
        this.testingRunResultSummaryId = testingRunResultSummaryId;
    }
}

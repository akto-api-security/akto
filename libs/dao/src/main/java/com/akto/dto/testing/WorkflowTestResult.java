package com.akto.dto.testing;

import org.bson.types.ObjectId;

import java.util.*;

public class WorkflowTestResult {
    private int id;
    private int workflowTestId;
    public static final String _TEST_RUN_ID = "testRunId";
    private ObjectId testRunId;
    Map<String, TestResult> testResultMap;

    public WorkflowTestResult() {
    }

    public WorkflowTestResult(int id, int workflowTestId, Map<String, TestResult> testResultMap, ObjectId testRunId) {
        this.id = id;
        this.workflowTestId = workflowTestId;
        this.testResultMap = testResultMap;
        this.testRunId = testRunId;
    }

    public void addNodeResult(String nodeId, String message, List<TestResult.TestError> testErrors) {
        if (this.testResultMap == null) this.testResultMap = new HashMap<>();
        TestResult testResult = new TestResult(message, false, testErrors);
        this.testResultMap.put(nodeId, testResult);
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

    public Map<String, TestResult> getTestResultMap() {
        return testResultMap;
    }

    public void setTestResultMap(Map<String, TestResult> testResultMap) {
        this.testResultMap = testResultMap;
    }

    public ObjectId getTestRunId() {
        return testRunId;
    }

    public void setTestRunId(ObjectId testRunId) {
        this.testRunId = testRunId;
    }
}

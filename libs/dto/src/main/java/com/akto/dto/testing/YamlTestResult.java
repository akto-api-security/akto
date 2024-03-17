package com.akto.dto.testing;

import java.util.List;

public class YamlTestResult {
    
    List<GenericTestResult> testResults;
    WorkflowTest workflowTest;

    public YamlTestResult() {
        
    }

    public YamlTestResult(List<GenericTestResult> testResults, WorkflowTest workflowTest) {
        this.testResults = testResults;
        this.workflowTest = workflowTest;
    }

    public List<GenericTestResult> getTestResults() {
        return testResults;
    }

    public void setTestResults(List<GenericTestResult> testResults) {
        this.testResults = testResults;
    }

    public WorkflowTest getWorkflowTest() {
        return workflowTest;
    }

    public void setWorkflowTest(WorkflowTest workflowTest) {
        this.workflowTest = workflowTest;
    }

}

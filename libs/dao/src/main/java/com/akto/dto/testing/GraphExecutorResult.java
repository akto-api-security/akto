package com.akto.dto.testing;

import java.util.List;

public class GraphExecutorResult {
    
    WorkflowTestResult workflowTestResult;
    boolean vulnerable;
    List<String> errors;
    
    public GraphExecutorResult() {}
    public GraphExecutorResult(WorkflowTestResult workflowTestResult, boolean vulnerable, List<String> errors) {
        this.workflowTestResult = workflowTestResult;
        this.vulnerable = vulnerable;
        this.errors = errors;
    }

    public WorkflowTestResult getWorkflowTestResult() {
        return workflowTestResult;
    }
    public void setWorkflowTestResult(WorkflowTestResult workflowTestResult) {
        this.workflowTestResult = workflowTestResult;
    }
    public boolean getVulnerable() {
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

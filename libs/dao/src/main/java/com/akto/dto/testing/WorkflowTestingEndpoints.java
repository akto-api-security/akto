package com.akto.dto.testing;

import com.akto.dto.ApiInfo;

import java.util.List;

public class WorkflowTestingEndpoints extends TestingEndpoints{
    public static final String _WORK_FLOW_TEST = "workflowTest";
    private WorkflowTest workflowTest;

    public WorkflowTestingEndpoints() {
        super(Type.WORKFLOW);
    }

    public WorkflowTestingEndpoints(WorkflowTest workflowTest) {
        super(Type.WORKFLOW);
        this.workflowTest = workflowTest;
    }

    @Override
    public List<ApiInfo.ApiInfoKey> returnApis() {
        return null;
    }

    public WorkflowTest getWorkflowTest() {
        return workflowTest;
    }

    public void setWorkflowTest(WorkflowTest workflowTest) {
        this.workflowTest = workflowTest;
    }
}

package com.akto.dto.testing;

import com.akto.dao.MCollection;
import com.akto.dto.ApiCollectionUsers.CollectionType;
import com.akto.dto.ApiInfo;

import java.util.List;

import org.bson.conversions.Bson;

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

    @Override
    public boolean containsApi(ApiInfo.ApiInfoKey key) {
        return false;
    }

    public WorkflowTest getWorkflowTest() {
        return workflowTest;
    }

    public void setWorkflowTest(WorkflowTest workflowTest) {
        this.workflowTest = workflowTest;
    }

    @Override
    public Bson createFilters(CollectionType type) {
        return MCollection.noMatchFilter;
    }
}

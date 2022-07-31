package com.akto.dto.testing;

import com.akto.dto.type.URLMethods.Method;

public class WorkflowNodeDetails {
    int apiCollectionId;
    String endpoint;
    Method method;
    WorkflowUpdatedSampleData updatedSampleData;

    public WorkflowNodeDetails() {
    }

    public WorkflowNodeDetails(int apiCollectionId, String endpoint, Method method, WorkflowUpdatedSampleData updatedSampleData) {
        this.apiCollectionId = apiCollectionId;
        this.endpoint = endpoint;
        this.method = method;
        this.updatedSampleData = updatedSampleData;
    }

    public int getApiCollectionId() {
        return this.apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public String getEndpoint() {
        return this.endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public Method getMethod() {
        return this.method;
    }

    public void setMethod(Method method) {
        this.method = method;
    }

    public WorkflowUpdatedSampleData getUpdatedSampleData() {
        return this.updatedSampleData;
    }

    public void setUpdatedSampleData(WorkflowUpdatedSampleData updatedSampleData) {
        this.updatedSampleData = updatedSampleData;
    }

    @Override
    public String toString() {
        return "{" +
            " apiCollectionId='" + getApiCollectionId() + "'" +
            ", endpoint='" + getEndpoint() + "'" +
            ", method='" + getMethod() + "'" +
            ", updatedSampleData='" + getUpdatedSampleData() + "'" +
            "}";
    }

}
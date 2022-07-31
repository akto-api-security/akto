package com.akto.dto.testing;

import com.akto.dto.ApiInfo.ApiInfoKey;

public class WorkflowNodeDetails {
    ApiInfoKey id;
    WorkflowUpdatedSampleData updatedSampleData;

    public WorkflowNodeDetails() {
    }

    public WorkflowNodeDetails(ApiInfoKey id, WorkflowUpdatedSampleData updatedSampleData) {
        this.id = id;
        this.updatedSampleData = updatedSampleData;
    }

    public ApiInfoKey getId() {
        return this.id;
    }

    public void setId(ApiInfoKey id) {
        this.id = id;
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
            " id='" + getId() + "'" +
            ", updatedSampleData='" + getUpdatedSampleData() + "'" +
            "}";
    }    

}
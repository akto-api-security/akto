package com.akto.dto.testing;

import com.akto.dto.testing.NodeDetails.NodeDetails;

public class WorkflowNodeDetails {
    
    Type type = Type.API;

    NodeDetails nodeDetails;

    public enum Type {
        POLL, API, OTP, RECORDED
    }

    // call this function to see if data being passed is legit or not
    // public String validate() {
    //     if (this.type == null ) return "Type can't be null";
    //     if (this.endpoint == null ) return "URL can't be null";
    //     if (this.method == null ) return "Method can't be null";
    //     int waitThreshold = 60;
    //     if (waitInSeconds > waitThreshold) return "Wait time should be <= " + waitThreshold;

    //     return null;
    // }

    public WorkflowNodeDetails() {
    }

    public WorkflowNodeDetails(Type type, NodeDetails nodeDetails) {
        this.type = type;
        this.nodeDetails = nodeDetails;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public NodeDetails getNodeDetails() {
        return nodeDetails;
    }

    public void setNodeDetails(NodeDetails nodeDetails) {
        this.nodeDetails = nodeDetails;
    }

    // @Override
    // public String toString() {
    //     return "{" +
    //         " apiCollectionId='" + getApiCollectionId() + "'" +
    //         ", endpoint='" + getEndpoint() + "'" +
    //         ", method='" + getMethod() + "'" +
    //         ", updatedSampleData='" + getUpdatedSampleData() + "'" +
    //         "}";
    // }

}
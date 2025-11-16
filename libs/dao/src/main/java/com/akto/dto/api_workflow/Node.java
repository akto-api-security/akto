package com.akto.dto.api_workflow;

import com.akto.dto.testing.WorkflowNodeDetails;

import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Node {
    private String id;
    private WorkflowNodeDetails workflowNodeDetails;
    private Set<String> neighbours = new HashSet<>();
    private String successChildNode;
    private String failureChildNode;
    @Getter
    @Setter
    private int waitInSeconds;

    public Node(String id, WorkflowNodeDetails workflowNodeDetails) {
        this.id = id;
        this.workflowNodeDetails = workflowNodeDetails;
    }

    public Node(String id, WorkflowNodeDetails workflowNodeDetails, String successChildNode, String failureChildNode, int waitInSeconds) {
        this.id = id;
        this.workflowNodeDetails = workflowNodeDetails;
        this.successChildNode = successChildNode;
        this.failureChildNode = failureChildNode;
        this.waitInSeconds = waitInSeconds;
    }

    public void addNeighbours(String... ids) {
        neighbours.addAll(Arrays.asList(ids));
    }


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public WorkflowNodeDetails getWorkflowNodeDetails() {
        return workflowNodeDetails;
    }

    public void setWorkflowNodeDetails(WorkflowNodeDetails workflowNodeDetails) {
        this.workflowNodeDetails = workflowNodeDetails;
    }

    public Set<String> getNeighbours() {
        return neighbours;
    }

    public void setNeighbours(Set<String> neighbours) {
        this.neighbours = neighbours;
    }

    public String getSuccessChildNode() {
        return successChildNode;
    }

    public void setSuccessChildNode(String successChildNode) {
        this.successChildNode = successChildNode;
    }

    public String getFailureChildNode() {
        return failureChildNode;
    }

    public void setFailureChildNode(String failureChildNode) {
        this.failureChildNode = failureChildNode;
    }

    @Override
    public String toString() {
        return "Node{" +
                "id='" + id + '\'' +
                '}';
    }
}

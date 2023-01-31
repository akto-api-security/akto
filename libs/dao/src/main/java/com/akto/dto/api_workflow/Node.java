package com.akto.dto.api_workflow;

import com.akto.dto.testing.WorkflowNodeDetails;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Node {
    private String id;
    private WorkflowNodeDetails workflowNodeDetails;
    private Set<String> neighbours = new HashSet<>();

    public Node(String id, WorkflowNodeDetails workflowNodeDetails) {
        this.id = id;
        this.workflowNodeDetails = workflowNodeDetails;
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

    @Override
    public String toString() {
        return "Node{" +
                "id='" + id + '\'' +
                '}';
    }
}

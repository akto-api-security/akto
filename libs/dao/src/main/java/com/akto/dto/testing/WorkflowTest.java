package com.akto.dto.testing;

import java.util.List;
import java.util.Map;

import org.bson.codecs.pojo.annotations.BsonId;

public class WorkflowTest {

    public enum State {
        DRAFT, COMPLETE, STOPPED;
    }

    @BsonId
    int id;

    int apiCollectionId;
    String author;
    int createdTimestamp;

    String editor;
    int lastEdited;

    List<String> nodes;
    List<String> edges;

    public static final String MAP_NODE_ID_TO_WORKFLOW_NODE_DETAILS = "mapNodeIdToWorkflowNodeDetails";
    Map<String, WorkflowNodeDetails> mapNodeIdToWorkflowNodeDetails;
    State state;

    public WorkflowTest() {}

    public WorkflowTest(int id, int apiCollectionId, String author, int createdTimestamp, String editor, int lastEdited, List<String> nodes, List<String> edges, Map<String,WorkflowNodeDetails> mapNodeIdToWorkflowNodeDetails, State state) {
        this.id = id;
        this.apiCollectionId = apiCollectionId;
        this.author = author;
        this.createdTimestamp = createdTimestamp;
        this.editor = editor;
        this.lastEdited = lastEdited;
        this.nodes = nodes;
        this.edges = edges;
        this.mapNodeIdToWorkflowNodeDetails = mapNodeIdToWorkflowNodeDetails;
        this.state = state;
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getApiCollectionId() {
        return this.apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public String getAuthor() {
        return this.author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public int getCreatedTimestamp() {
        return this.createdTimestamp;
    }

    public void setCreatedTimestamp(int createdTimestamp) {
        this.createdTimestamp = createdTimestamp;
    }

    public String getEditor() {
        return this.editor;
    }

    public void setEditor(String editor) {
        this.editor = editor;
    }

    public int getLastEdited() {
        return this.lastEdited;
    }

    public void setLastEdited(int lastEdited) {
        this.lastEdited = lastEdited;
    }

    public List<String> getNodes() {
        return this.nodes;
    }

    public void setNodes(List<String> nodes) {
        this.nodes = nodes;
    }

    public List<String> getEdges() {
        return this.edges;
    }

    public void setEdges(List<String> edges) {
        this.edges = edges;
    }

    public Map<String,WorkflowNodeDetails> getMapNodeIdToWorkflowNodeDetails() {
        return this.mapNodeIdToWorkflowNodeDetails;
    }

    public void setMapNodeIdToWorkflowNodeDetails(Map<String,WorkflowNodeDetails> mapNodeIdToWorkflowNodeDetails) {
        this.mapNodeIdToWorkflowNodeDetails = mapNodeIdToWorkflowNodeDetails;
    }

    public State getState() {
        return this.state;
    }

    public void setState(State state) {
        this.state = state;
    }

    @Override
    public String toString() {
        return "{" +
            " id='" + getId() + "'" +
            ", apiCollectionId='" + getApiCollectionId() + "'" +
            ", author='" + getAuthor() + "'" +
            ", createdTimestamp='" + getCreatedTimestamp() + "'" +
            ", editor='" + getEditor() + "'" +
            ", lastEdited='" + getLastEdited() + "'" +
            ", nodes='" + getNodes() + "'" +
            ", edges='" + getEdges() + "'" +
            ", mapNodeIdToWorkflowNodeDetails='" + getMapNodeIdToWorkflowNodeDetails() + "'" +
            ", state='" + getState() + "'" +
            "}";
    }

}

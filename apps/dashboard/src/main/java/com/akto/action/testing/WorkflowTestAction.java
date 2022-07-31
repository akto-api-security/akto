package com.akto.action.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.dao.testing.WorkflowTestsDao;
import com.akto.dto.testing.WorkflowNodeDetails;
import com.akto.dto.testing.WorkflowTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.InsertOneResult;

import org.bson.conversions.Bson;

public class WorkflowTestAction extends UserAction {
    int apiCollectionId;
    private List<String> nodeIds;
    private List<String> edges;
    private Map<String, WorkflowNodeDetails> mapNodeIdToWorkflowNodeDetails;
    WorkflowTest.State state;
    int id;
    String str;
    private List<WorkflowTest> workflowTests;

    public String createWorkflowTest() {
        int id = Context.now();
        String author = getSUser().getLogin();
        int createdTimestamp = id;
        String editor = author;
        int lastEdited = createdTimestamp;
        WorkflowTest workflowTest = new WorkflowTest(id, apiCollectionId, author, createdTimestamp, editor, 
            lastEdited, nodeIds, edges, mapNodeIdToWorkflowNodeDetails, state);

        InsertOneResult result = WorkflowTestsDao.instance.insertOne(workflowTest);
        int newId = result.getInsertedId().asInt32().intValue();
        workflowTests = new ArrayList<WorkflowTest>();
        workflowTests.add(WorkflowTestsDao.instance.findOne("_id", newId));

        return SUCCESS.toUpperCase();
    }

    public String editWorkflowTest() {
        WorkflowTest workflowTest = findWorkflowTest(id);
        if (workflowTest == null) {
            return ERROR.toUpperCase();
        }

        Bson updates = Updates.combine(
            Updates.set("nodes", nodeIds),
            Updates.set("edges", edges),
            Updates.set("mapNodeIdToWorkflowNodeDetails", mapNodeIdToWorkflowNodeDetails)
        );
        
        updateWorkflowTest(id, updates);
        
        return SUCCESS.toUpperCase();
    }

    public String setStatus() {
        WorkflowTest workflowTest = findWorkflowTest(id);
        if (workflowTest == null) {
            return ERROR.toUpperCase();
        }

        updateWorkflowTest(id, Updates.set("state", state));
        
        return SUCCESS.toUpperCase();
    }

    public String exportAsString() {
        WorkflowTest workflowTest = findWorkflowTest(id);
        if (workflowTest == null) {
            return ERROR.toUpperCase();
        }
        
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            str = objectMapper.writeValueAsString(workflowTest);
        } catch (JsonProcessingException e) {
            return ERROR.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }

    public String editNodeDetails() {
        WorkflowTest workflowTest = findWorkflowTest(id);
        if (workflowTest == null || mapNodeIdToWorkflowNodeDetails.size() != 1) {
            return ERROR.toUpperCase();
        }

        String nodeId = mapNodeIdToWorkflowNodeDetails.keySet().iterator().next();
        Map<String, WorkflowNodeDetails> m = workflowTest.getMapNodeIdToWorkflowNodeDetails();
        if (m == null || m.get(nodeId) == null) {
            return ERROR.toUpperCase();
        }

        WorkflowNodeDetails details = mapNodeIdToWorkflowNodeDetails.get(nodeId);
        Bson update = Updates.set("mapNodeIdToWorkflowNodeDetails."+nodeId, details);
        updateWorkflowTest(id, update);

        return SUCCESS.toUpperCase();
    }

    private WorkflowTest findWorkflowTest(int workflowTestId) {
        WorkflowTest workflowTest = WorkflowTestsDao.instance.findOne("_id", workflowTestId);
        return workflowTest;
    }

    private void updateWorkflowTest(int workflowTestId, Bson updates) {
        String editor = getSUser().getLogin();
        int lastEdited = Context.now();
        Bson allUpdates = Updates.combine(
            Updates.set("editor", editor),
            Updates.set("lastEdited", lastEdited),
            updates
        );
        WorkflowTestsDao.instance.updateOne("_id", id, allUpdates);
        workflowTests = new ArrayList<WorkflowTest>();
        workflowTests.add(WorkflowTestsDao.instance.findOne("_id", id));
    }

    public int getApiCollectionId() {
        return this.apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public List<String> getNodeIds() {
        return this.nodeIds;
    }

    public void setNodeIds(List<String> nodeIds) {
        this.nodeIds = nodeIds;
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

    public WorkflowTest.State getState() {
        return this.state;
    }

    public void setState(WorkflowTest.State state) {
        this.state = state;
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public List<WorkflowTest> getWorkflowTests() {
        return this.workflowTests;
    }

    public void setWorkflowTests(List<WorkflowTest> workflowTests) {
        this.workflowTests = workflowTests;
    }

    public String getStr() {
        return this.str;
    }

    public void setStr(String str) {
        this.str = str;
    }    
}

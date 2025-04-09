package com.akto.action.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.dao.testing.WorkflowTestsDao;
import com.akto.dto.Log;
import com.akto.dto.testing.WorkflowNodeDetails;
import com.akto.dto.testing.WorkflowTest;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.InsertOneResult;

import org.bson.conversions.Bson;

public class WorkflowTestAction extends UserAction {

    private static final LoggerMaker logger = new LoggerMaker(WorkflowTestAction.class, LogDb.DASHBOARD);;

    int apiCollectionId;
    private List<String> nodes;
    private List<String> edges;
    private Map<String, WorkflowNodeDetails> mapNodeIdToWorkflowNodeDetails;
    WorkflowTest.State state;
    int id;
    String str;
    private List<WorkflowTest> workflowTests;
    private List<Log> testingLogs;
    int logFetchStartTime;
    int logFetchEndTime;


    public String fetchWorkflowTests() {
        workflowTests = WorkflowTestsDao.instance.findAll(new BasicDBObject());
        return SUCCESS.toUpperCase();
    }

    public String createWorkflowTest() {
        int id = Context.now();
        String author = getSUser().getLogin();
        int createdTimestamp = id;
        String editor = author;
        int lastEdited = createdTimestamp;

        if (mapNodeIdToWorkflowNodeDetails == null) {
            addActionError("nodes can't be null");
            return ERROR.toUpperCase();
        }

        for (WorkflowNodeDetails workflowNodeDetails: mapNodeIdToWorkflowNodeDetails.values()) {
            WorkflowNodeDetails nodeDetails = workflowNodeDetails;
            String err = nodeDetails.validate();
            if (err != null) {
                addActionError(err);
                return ERROR.toUpperCase();
            }
        }

        WorkflowTest workflowTest = new WorkflowTest(id, apiCollectionId, author, createdTimestamp, editor,
            lastEdited, nodes, edges, mapNodeIdToWorkflowNodeDetails, state);

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
            Updates.set("nodes", nodes),
            Updates.set("edges", edges),
            Updates.set("mapNodeIdToWorkflowNodeDetails", mapNodeIdToWorkflowNodeDetails)
        );

        updateWorkflowTest(id, updates);

        return SUCCESS.toUpperCase();
    }

    public String setWorkflowTestState() {
        WorkflowTest workflowTest = findWorkflowTest(id);
        if (workflowTest == null) {
            return ERROR.toUpperCase();
        }

        updateWorkflowTest(id, Updates.set("state", state));

        return SUCCESS.toUpperCase();
    }

    public String exportWorkflowTestAsString() {
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

    public String editWorkflowNodeDetails() {
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

    private String workflowTestJson;
    public String downloadWorkflowAsJson() {
        WorkflowTest workflowTest = findWorkflowTest(id);
        if (workflowTest == null) {
            addActionError("Invalid workflow test");
            return ERROR.toUpperCase();
        }

        workflowTestJson = new Gson().toJson(workflowTest);

        return SUCCESS.toUpperCase();
    }

    public String uploadWorkflowJson() {
        WorkflowTest workflowTest = new Gson().fromJson(workflowTestJson,WorkflowTest.class);

        if (workflowTest == null) {
            addActionError("Invalid workflow test json");
            return ERROR.toUpperCase();
        }

        int id = Context.now();
        String author = getSUser().getLogin();
        int createdTimestamp = id;
        String editor = author;
        int lastEdited = createdTimestamp;

        workflowTest.setId(id);
        workflowTest.setAuthor(author);
        workflowTest.setCreatedTimestamp(createdTimestamp);
        workflowTest.setEditor(author);
        workflowTest.setLastEdited(lastEdited);
        workflowTest.setApiCollectionId(apiCollectionId);

        WorkflowTestsDao.instance.insertOne(workflowTest);

        workflowTests = new ArrayList<>();
        workflowTests.add(workflowTest);

        return SUCCESS.toUpperCase();
    }

    public String fetchTestingLogs() {

        testingLogs = logger.fetchLogRecords(logFetchStartTime, logFetchEndTime, LogDb.TESTING);
        return SUCCESS.toUpperCase();
    }

    public int getApiCollectionId() {
        return this.apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
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

    public String getWorkflowTestJson() {
        return workflowTestJson;
    }

    public void setWorkflowTestJson(String workflowTestJson) {
        this.workflowTestJson = workflowTestJson;
    }

    public int getLogFetchStartTime() {
        return logFetchStartTime;
    }

    public void setLogFetchStartTime(int logFetchStartTime) {
        this.logFetchStartTime = logFetchStartTime;
    }

    public int getLogFetchEndTime() {
        return logFetchEndTime;
    }

    public void setLogFetchEndTime(int logFetchEndTime) {
        this.logFetchEndTime = logFetchEndTime;
    }

    public List<Log> getTestingLogs() {
        return testingLogs;
    }

    public void setTestingLogs(List<Log> testingLogs) {
        this.testingLogs = testingLogs;
    }
}

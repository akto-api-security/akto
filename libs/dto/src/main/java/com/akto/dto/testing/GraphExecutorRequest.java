package com.akto.dto.testing;

import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;

import com.akto.dto.api_workflow.Graph;
import com.akto.dto.api_workflow.Node;

public class GraphExecutorRequest {
    
    Graph graph;
    Node node;
    WorkflowTest workflowTest;
    ObjectId testingRunId;
    ObjectId testingRunSummaryId;
    Map<String, Object> valuesMap;
    boolean skipIfNotVulnerable;
    String executionType;
    WorkflowTestResult workflowTestResult;
    Map<String, Boolean> visitedMap;
    List<String> executionOrder;

    public GraphExecutorRequest(Graph graph, WorkflowTest workflowTest, ObjectId testingRunId,
            ObjectId testingRunSummaryId, Map<String, Object> valuesMap, boolean skipIfNotVulnerable, 
            String executionType) {
        this.graph = graph;
        this.workflowTest = workflowTest;
        this.testingRunId = testingRunId;
        this.testingRunSummaryId = testingRunSummaryId;
        this.valuesMap = valuesMap;
        this.skipIfNotVulnerable = skipIfNotVulnerable;
        this.executionType = executionType;
    }

    public GraphExecutorRequest(Graph graph, Node node, WorkflowTest workflowTest, ObjectId testingRunId,
            ObjectId testingRunSummaryId, Map<String, Object> valuesMap, String executionType, 
            WorkflowTestResult workflowTestResult, Map<String, Boolean> visitedMap, List<String> executionOrder) {
        this.graph = graph;
        this.node = node;
        this.workflowTest = workflowTest;
        this.testingRunId = testingRunId;
        this.testingRunSummaryId = testingRunSummaryId;
        this.valuesMap = valuesMap;
        this.executionType = executionType;
        this.workflowTestResult = workflowTestResult;
        this.visitedMap = visitedMap;
        this.executionOrder = executionOrder;
    }

    public GraphExecutorRequest(GraphExecutorRequest graphExecutorRequest, Node node, 
        WorkflowTestResult workflowTestResult, Map<String, Boolean> visitedMap, List<String> executionOrder) {
        this.graph = graphExecutorRequest.getGraph();
        this.node = node;
        this.workflowTest = graphExecutorRequest.getWorkflowTest();
        this.testingRunId = graphExecutorRequest.getTestingRunId();
        this.testingRunSummaryId = graphExecutorRequest.getTestingRunSummaryId();
        this.valuesMap = graphExecutorRequest.getValuesMap();
        this.skipIfNotVulnerable = graphExecutorRequest.getSkipIfNotVulnerable();
        this.executionType = graphExecutorRequest.getExecutionType();
        this.workflowTestResult = workflowTestResult;
        this.visitedMap = visitedMap;
        this.executionOrder = executionOrder;
    }


    public GraphExecutorRequest() {}

    public Graph getGraph() {
        return graph;
    }

    public void setGraph(Graph graph) {
        this.graph = graph;
    }

    public Node getNode() {
        return node;
    }

    public void setNode(Node node) {
        this.node = node;
    }

    public WorkflowTest getWorkflowTest() {
        return workflowTest;
    }

    public void setWorkflowTest(WorkflowTest workflowTest) {
        this.workflowTest = workflowTest;
    }

    public ObjectId getTestingRunId() {
        return testingRunId;
    }

    public void setTestingRunId(ObjectId testingRunId) {
        this.testingRunId = testingRunId;
    }

    public ObjectId getTestingRunSummaryId() {
        return testingRunSummaryId;
    }

    public void setTestingRunSummaryId(ObjectId testingRunSummaryId) {
        this.testingRunSummaryId = testingRunSummaryId;
    }

    public Map<String, Object> getValuesMap() {
        return valuesMap;
    }

    public void setValuesMap(Map<String, Object> valuesMap) {
        this.valuesMap = valuesMap;
    }

    public boolean getSkipIfNotVulnerable() {
        return skipIfNotVulnerable;
    }

    public void setSkipIfNotVulnerable(boolean skipIfNotVulnerable) {
        this.skipIfNotVulnerable = skipIfNotVulnerable;
    }

    public String getExecutionType() {
        return executionType;
    }

    public void setExecutionType(String executionType) {
        this.executionType = executionType;
    }

    public WorkflowTestResult getWorkflowTestResult() {
        return workflowTestResult;
    }

    public void setWorkflowTestResult(WorkflowTestResult workflowTestResult) {
        this.workflowTestResult = workflowTestResult;
    }

    public Map<String, Boolean> getVisitedMap() {
        return visitedMap;
    }

    public void setVisitedMap(Map<String, Boolean> visitedMap) {
        this.visitedMap = visitedMap;
    }

    public List<String> getExecutionOrder() {
        return executionOrder;
    }

    public void setExecutionOrder(List<String> executionOrder) {
        this.executionOrder = executionOrder;
    }


}

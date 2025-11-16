package com.akto.dto.api_workflow;

import com.akto.dto.testing.WorkflowNodeDetails;
import com.akto.dto.testing.WorkflowTest;
import com.akto.dto.testing.YamlNodeDetails;
import com.mongodb.BasicDBObject;

import java.util.*;

public class Graph {
    private Map<String, Node> nodes = new HashMap<>();

    public Graph() { }

    public void buildGraph(WorkflowTest workflowTest) {
        List<String> edges = workflowTest.getEdges();
        Map<String, WorkflowNodeDetails> mapNodeIdToWorkflowNodeDetails = workflowTest.getMapNodeIdToWorkflowNodeDetails();

        for (String edge: edges) {
            BasicDBObject basicDBObject = BasicDBObject.parse(edge);
            String sourceId = basicDBObject.getString("source");
            String targetId = basicDBObject.getString("target");
            if (sourceId == null || targetId == null) continue;

            // Skip if START node
            if (sourceId.equals("1")) continue;

            Node node = getNode(sourceId);
            if (node == null) {
                if (mapNodeIdToWorkflowNodeDetails.get(sourceId) instanceof YamlNodeDetails) {
                    YamlNodeDetails workflowNodeDetails = (YamlNodeDetails) mapNodeIdToWorkflowNodeDetails.get(sourceId);
                    node = new Node(sourceId, workflowNodeDetails, workflowNodeDetails.getSuccess(), workflowNodeDetails.getFailure(), workflowNodeDetails.getWaitInSeconds());
                } else {
                    WorkflowNodeDetails workflowNodeDetails = mapNodeIdToWorkflowNodeDetails.get(sourceId);
                    node = new Node(sourceId, workflowNodeDetails);
                }
            }
            node.addNeighbours(targetId);
            addNode(node);
        }

    }

    public List<Node> sort() {
        List<Node> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        for (Node node: nodes.values()) {
            if (visited.contains(node.getId())) continue;
            rec(node, visited, result);
        }

        Collections.reverse(result);
        return result;
    }

    private void rec(Node node, Set<String> visited, List<Node> result) {
        visited.add(node.getId());

        for (String neighbourId: node.getNeighbours()) {
            Node neighbour = getNode(neighbourId);
            if (neighbour == null || visited.contains(neighbourId)) continue;
            rec(neighbour, visited, result);
        }

        result.add(node);
    }

    public void addNode(Node node) {
        nodes.put(node.getId(), node);
    }

    public void addNodes(Node... nodes) {
        for (Node node: nodes) {
            addNode(node);
        }
    }

    public Node getNode(String id) {
        return nodes.get(id);
    }

    public Map<String, Node> getNodes() {
        return nodes;
    }

    public void setNodes(Map<String, Node> nodes) {
        this.nodes = nodes;
    }
}

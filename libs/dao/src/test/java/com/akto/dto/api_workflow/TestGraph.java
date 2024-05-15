package com.akto.dto.api_workflow;

import com.akto.dto.testing.WorkflowNodeDetails;
import com.akto.dto.testing.WorkflowTest;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class TestGraph {

    @Test
    public void testBuildGraph() {
        Graph graph = new Graph();

        WorkflowTest workflowTest = new WorkflowTest();

        Map<String, WorkflowNodeDetails> mapNodeIdToWorkflowNodeDetails = new HashMap<>();
        WorkflowNodeDetails x1 = new WorkflowNodeDetails();
        mapNodeIdToWorkflowNodeDetails.put("x1", x1);
        WorkflowNodeDetails x1659328241 = new WorkflowNodeDetails();
        mapNodeIdToWorkflowNodeDetails.put("x1659328241", x1659328241);
        workflowTest.setMapNodeIdToWorkflowNodeDetails(mapNodeIdToWorkflowNodeDetails);

        List<String> edges = new ArrayList<>();
        edges.add("{\"source\":\"1\",\"target\":\"x1\",\"id\":\"x1\",\"selected\":false,\"markerEnd\":{\"type\":\"arrow\"}}");
        edges.add("{\"source\":\"x1659328241\",\"target\":\"3\",\"id\":\"x1659328245\",\"selected\":true,\"markerEnd\":{\"type\":\"arrow\"}}");
        edges.add("{\"source\":\"x1\",\"target\":\"x1659328241\",\"id\":\"x1659328241\",\"selected\":false,\"markerEnd\":{\"type\":\"arrow\"}}");
        workflowTest.setEdges(edges);

        graph.buildGraph(workflowTest);

        Map<String, Node> nodes = graph.getNodes();
        assertEquals(2, nodes.size());
        assertNotNull(nodes.get("x1"));
        assertNotNull(nodes.get("x1659328241"));

        List<Node> sortedNodes = graph.sort();
        System.out.println(sortedNodes);
    }

    @Test
    public void testSort() {
        Graph graph = new Graph();
        Node node1 = new Node("1", new WorkflowNodeDetails());
        Node node2 = new Node("2", new WorkflowNodeDetails());
        Node node3 = new Node("3", new WorkflowNodeDetails());
        Node node4 = new Node("4", new WorkflowNodeDetails());
        Node node5 = new Node("5", new WorkflowNodeDetails());
        Node node6 = new Node("6", new WorkflowNodeDetails());
        Node node7 = new Node("7", new WorkflowNodeDetails());
        Node node8 = new Node("8", new WorkflowNodeDetails());
        Node node9 = new Node("9", new WorkflowNodeDetails());
        Node node10 = new Node("10", new WorkflowNodeDetails());

        // all arrows move from left to right (1->2->4->5->9 and 7)
        //         2 -- 4 -- 5---------------9
        //         /          \               \
        //        /            \                10
        //   1 __/              \__7___8_______/
        //   |   \              /  |
        //   |    \            /   |
        //   |     3 ------- 6     |
        //   |_____________________|
        //

        node1.addNeighbours(node2.getId(), node3.getId(), node7.getId());
        node2.addNeighbours(node4.getId());
        node4.addNeighbours(node5.getId());
        node5.addNeighbours(node7.getId(), node9.getId());
        node9.addNeighbours(node10.getId());
        node3.addNeighbours(node6.getId());
        node6.addNeighbours(node7.getId());
        node7.addNeighbours(node8.getId());
        node8.addNeighbours(node10.getId());

        graph.addNodes(node7, node8, node9, node10, node1, node2, node3, node4, node5, node6);


        List<Node> nodes = graph.sort();
        System.out.println(nodes);
    }


}

package com.akto.testing.workflow_node_executor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.akto.dto.api_workflow.Node;
import com.akto.dto.testing.GraphExecutorRequest;
import com.akto.dto.testing.GraphExecutorResult;
import com.akto.dto.testing.WorkflowTestResult;

public class ConditionalGraphExecutor extends GraphExecutor {
    
    public GraphExecutorResult executeGraph(GraphExecutorRequest graphExecutorRequest) {

        Map<String, Boolean> visitedMap = graphExecutorRequest.getVisitedMap();
        List<String> errors = new ArrayList<>();
        Node node = graphExecutorRequest.getNode();
        
        if (visitedMap.containsKey(node.getId())) {
            errors.add("invalid graph, node " + node.getId() + "being visited multiple times");
            return new GraphExecutorResult(graphExecutorRequest.getWorkflowTestResult(), false, errors);
        }
        
        // if (node.getNeighbours().size() > 2) {
        //     errors.add("invalid graph, node " + node.getId() + "cannot have more than 2 paths to other nodes");
        //     return new GraphExecutorResult(graphExecutorRequest.getWorkflowTestResult(), false, errors);
        // }

        visitedMap.put(node.getId(), true);

        boolean success = false;

        WorkflowTestResult.NodeResult nodeResult;
        nodeResult = Utils.executeNode(node, graphExecutorRequest.getValuesMap());

        graphExecutorRequest.getWorkflowTestResult().getNodeResultMap().put(node.getId(), nodeResult);

        if (nodeResult.isVulnerable()) {
            success = true;
        }

        String childNodeId;
        if (success) {
            if (node.getSuccessChildNode() == null || node.getSuccessChildNode().equals("")) {
                String curNodeId = node.getId();
                childNodeId = Utils.evaluateNextNodeId(curNodeId);
            } else {
                if (node.getSuccessChildNode().equals("vulnerable")) {
                    return new GraphExecutorResult(graphExecutorRequest.getWorkflowTestResult(), true, errors);
                } else if (node.getSuccessChildNode().equals("exit")) {
                    return new GraphExecutorResult(graphExecutorRequest.getWorkflowTestResult(), false, errors);
                } else if (node.getSuccessChildNode().equals("terminal")) {
                    return new GraphExecutorResult(graphExecutorRequest.getWorkflowTestResult(), true, errors);
                }
                childNodeId = node.getSuccessChildNode();
            }
        } else {
            if (node.getFailureChildNode() == null || node.getFailureChildNode().equals("")) {
                return new GraphExecutorResult(graphExecutorRequest.getWorkflowTestResult(), false, errors);
            } else {
                if (node.getSuccessChildNode().equals("vulnerable")) {
                    return new GraphExecutorResult(graphExecutorRequest.getWorkflowTestResult(), true, errors);
                } else if (node.getSuccessChildNode().equals("exit")) {
                    return new GraphExecutorResult(graphExecutorRequest.getWorkflowTestResult(), false, errors);
                }
                childNodeId = node.getFailureChildNode();
            }
        }

        Node childNode = graphExecutorRequest.getGraph().getNode(childNodeId);

        boolean vulnerable = success;
        if (childNode != null) {
            GraphExecutorRequest childExecReq = new GraphExecutorRequest(graphExecutorRequest, childNode, graphExecutorRequest.getWorkflowTestResult(), visitedMap);
            GraphExecutorResult childExecResult = executeGraph(childExecReq);
            vulnerable = vulnerable & childExecResult.getVulnerable();
        }

        return new GraphExecutorResult(graphExecutorRequest.getWorkflowTestResult(), vulnerable, errors);
    }

}

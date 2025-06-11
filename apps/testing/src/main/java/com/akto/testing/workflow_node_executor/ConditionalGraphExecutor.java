package com.akto.testing.workflow_node_executor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.dao.test_editor.TestEditorEnums;
import com.akto.dto.api_workflow.Node;
import com.akto.dto.test_editor.DataOperandsFilterResponse;
import com.akto.dto.test_editor.ExecutorNode;
import com.akto.dto.test_editor.FilterNode;
import com.akto.dto.testing.*;
import com.akto.test_editor.execution.Memory;
import com.akto.test_editor.filter.Filter;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConditionalGraphExecutor extends GraphExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ConditionalGraphExecutor.class);
    boolean allowAllCombinations;
    public ConditionalGraphExecutor(boolean allowAllCombinations) {
        this.allowAllCombinations = allowAllCombinations;
    }
    
    public GraphExecutorResult executeGraph(GraphExecutorRequest graphExecutorRequest, boolean debug, List<TestingRunResult.TestLog> testLogs, Memory memory) {

        Map<String, Boolean> visitedMap = graphExecutorRequest.getVisitedMap();
        List<String> errors = new ArrayList<>();
        Node node = graphExecutorRequest.getNode();
        
        if (visitedMap.containsKey(node.getId())) {
            errors.add("invalid graph, node " + node.getId() + "being visited multiple times");
            return new GraphExecutorResult(graphExecutorRequest.getWorkflowTestResult(), false, errors);
        }

        visitedMap.put(node.getId(), true);

        boolean success = false;

        WorkflowTestResult.NodeResult nodeResult;
        try {
            int waitInSeconds = node.getWaitInSeconds();
            if (waitInSeconds > 0) {
                if (waitInSeconds > 100) {
                    waitInSeconds = 100;
                }
                logger.info("encountered sleep command in node " + node.getId() + " sleeping for " + waitInSeconds + " seconds");
                Thread.sleep(waitInSeconds * 1000);
            }
        } catch (Exception e) {
            // TODO: handle exception
        }

        nodeResult = Utils.executeNode(node, graphExecutorRequest.getValuesMap(), debug, testLogs, memory, this.allowAllCombinations);

        graphExecutorRequest.getWorkflowTestResult().getNodeResultMap().put(node.getId(), nodeResult);
        graphExecutorRequest.getExecutionOrder().add(node.getId());

        if (nodeResult.getErrors() != null && nodeResult.getErrors().size() > 0) {
            return new GraphExecutorResult(graphExecutorRequest.getWorkflowTestResult(), success, nodeResult.getErrors());
        }

        if (nodeResult.isVulnerable()) {
            success = true;
        }

        String childNodeId = "";
        if (success) {
            if (node.getSuccessChildNode() == null || node.getSuccessChildNode().equals("")) {
                String curNodeId = node.getId();
                childNodeId = Utils.evaluateNextNodeId(curNodeId);
            } else {
                if (node.getSuccessChildNode().equals("vulnerable")) {
                    return new GraphExecutorResult(graphExecutorRequest.getWorkflowTestResult(), true, errors);
                } else if (node.getSuccessChildNode().equals("exit")) {
                    return new GraphExecutorResult(graphExecutorRequest.getWorkflowTestResult(), false, errors);
                } // else if (node.getSuccessChildNode().equals("terminal")) {
                //     childNodeId = "terminal";
                //     //return new GraphExecutorResult(graphExecutorRequest.getWorkflowTestResult(), true, errors);
                // }
                childNodeId = node.getSuccessChildNode();
            }
        } else {
            if (node.getFailureChildNode() == null || node.getFailureChildNode().equals("")) {
                //return new GraphExecutorResult(graphExecutorRequest.getWorkflowTestResult(), false, errors);
            } else {
                if (node.getFailureChildNode().equals("vulnerable")) {
                    return new GraphExecutorResult(graphExecutorRequest.getWorkflowTestResult(), true, errors);
                } else if (node.getFailureChildNode().equals("exit")) {
                    return new GraphExecutorResult(graphExecutorRequest.getWorkflowTestResult(), false, errors);
                }
                childNodeId = node.getFailureChildNode();
            }
        }

        Node childNode = graphExecutorRequest.getGraph().getNode(childNodeId);

        boolean vulnerable = success;
        if (childNode != null) {
            GraphExecutorRequest childExecReq = new GraphExecutorRequest(graphExecutorRequest, childNode, graphExecutorRequest.getWorkflowTestResult(), visitedMap, graphExecutorRequest.getExecutionOrder());
            GraphExecutorResult childExecResult = executeGraph(childExecReq, debug, testLogs, memory);
            vulnerable = childExecResult.getVulnerable();
            return new GraphExecutorResult(graphExecutorRequest.getWorkflowTestResult(), vulnerable, errors);
        } else {
            Filter filter = new Filter();
            YamlNodeDetails yamlNodeDetails = (YamlNodeDetails) node.getWorkflowNodeDetails();
            FilterNode validatorParentNode = null;
            for (ExecutorNode execNode: yamlNodeDetails.getExecutorNode().getChildNodes()) {
                if (execNode.getNodeType().equalsIgnoreCase(TestEditorEnums.ValidateExecutorDataOperands.Validate.toString())) {
                    validatorParentNode = (FilterNode) execNode.getChildNodes().get(0).getValues();
                }
            }

            if (shouldConsiderParentValidateBlock(graphExecutorRequest, yamlNodeDetails, validatorParentNode, node.getId(), success)) {
                DataOperandsFilterResponse dataOperandsFilterResponse = filter.isEndpointValid(yamlNodeDetails.getValidatorNode(), null, null, null, null, null , false, "", graphExecutorRequest.getValuesMap(), "", false);
                return new GraphExecutorResult(graphExecutorRequest.getWorkflowTestResult(), dataOperandsFilterResponse.getResult(), errors);
            } else {
                return new GraphExecutorResult(graphExecutorRequest.getWorkflowTestResult(), success, errors);
            }
        }
    }

    public boolean shouldConsiderParentValidateBlock(GraphExecutorRequest graphExecutorRequest, YamlNodeDetails yamlNodeDetails, FilterNode validatorParentNode, String nodeId, Boolean success) {

        if (yamlNodeDetails.getValidatorNode() != null && validatorParentNode != null) {
            if (!success) {
                try {
                    String nextNodeId = Utils.evaluateNextNodeId(nodeId);
                    return graphExecutorRequest.getGraph().getNode(nextNodeId) == null;
                } catch (Exception e) {
                    return true;
                }
            }
            return true;
        } else {
            return false;
        }

    }

}

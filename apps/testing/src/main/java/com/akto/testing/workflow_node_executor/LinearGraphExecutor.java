package com.akto.testing.workflow_node_executor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.dao.context.Context;
import com.akto.dto.api_workflow.Node;
import com.akto.dto.testing.GraphExecutorRequest;
import com.akto.dto.testing.GraphExecutorResult;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.WorkflowTestResult;
import com.akto.test_editor.execution.Memory;

public class LinearGraphExecutor extends GraphExecutor {

    public GraphExecutorResult executeGraph(GraphExecutorRequest graphExecutorRequest, boolean debug, List<TestingRunResult.TestLog> testLogs, Memory memory) {
        List<Node> nodes = graphExecutorRequest.getGraph().sort();

        int id = Context.now();
        WorkflowTestResult workflowTestResult = new WorkflowTestResult(id, graphExecutorRequest.getWorkflowTest().getId(), new HashMap<>(), graphExecutorRequest.getTestingRunId(), graphExecutorRequest.getTestingRunSummaryId());
        Map<String, WorkflowTestResult.NodeResult> testResultMap = workflowTestResult.getNodeResultMap();
        for (Node node: nodes) {
            WorkflowTestResult.NodeResult nodeResult;
            nodeResult = Utils.executeNode(node, graphExecutorRequest.getValuesMap(), debug, testLogs, memory, false);
            testResultMap.put(node.getId(), nodeResult);
            if (nodeResult.getErrors().size() > 0) break;
            if (graphExecutorRequest.getSkipIfNotVulnerable() && !nodeResult.isVulnerable()) {
                break;
            }
        }
        List<String> errors = new ArrayList<>();
        return new GraphExecutorResult(workflowTestResult, true, errors);

    }

}

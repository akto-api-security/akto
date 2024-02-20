package com.akto.testing;

import com.akto.dto.testing.*;
import com.akto.testing.workflow_node_executor.GraphExecutor;
import com.akto.testing.workflow_node_executor.GraphExecutorFactory;

import java.util.List;

public class ApiWorkflowExecutor {

    public GraphExecutorResult init(GraphExecutorRequest graphExecutorRequest, boolean debug, List<TestingRunResult.TestLog> testLogs) {
        GraphExecutor graphExecutor = GraphExecutorFactory.fetchExecutor(graphExecutorRequest);
        GraphExecutorResult graphExecutorResult = graphExecutor.executeGraph(graphExecutorRequest,debug,testLogs);
        return graphExecutorResult;
    }

}
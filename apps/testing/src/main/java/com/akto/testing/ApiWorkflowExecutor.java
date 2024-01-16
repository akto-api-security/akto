package com.akto.testing;

import com.akto.dto.testing.*;
import com.akto.testing.workflow_node_executor.GraphExecutor;
import com.akto.testing.workflow_node_executor.GraphExecutorFactory;

public class ApiWorkflowExecutor {

    public GraphExecutorResult init(GraphExecutorRequest graphExecutorRequest) {
        GraphExecutor graphExecutor = GraphExecutorFactory.fetchExecutor(graphExecutorRequest);
        GraphExecutorResult graphExecutorResult = graphExecutor.executeGraph(graphExecutorRequest);
        return graphExecutorResult;
    }

}
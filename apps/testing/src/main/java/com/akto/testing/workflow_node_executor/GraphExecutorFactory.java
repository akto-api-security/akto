package com.akto.testing.workflow_node_executor;

import com.akto.dto.testing.GraphExecutorRequest;

public class GraphExecutorFactory {
    

    public static GraphExecutor fetchExecutor(GraphExecutorRequest graphExecutorRequest) {

        if (graphExecutorRequest.getExecutionType().equalsIgnoreCase("conditional")) {
            return new ConditionalGraphExecutor();
        }

        return new LinearGraphExecutor();

    }


}

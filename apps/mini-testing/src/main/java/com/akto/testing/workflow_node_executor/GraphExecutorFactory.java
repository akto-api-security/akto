package com.akto.testing.workflow_node_executor;

import com.akto.dto.testing.GraphExecutorRequest;

public class GraphExecutorFactory {
    

    public static GraphExecutor fetchExecutor(GraphExecutorRequest graphExecutorRequest) {

        String executionType = graphExecutorRequest.getExecutionType();

        if (executionType != null && executionType.equalsIgnoreCase("parallel")) {
            return new ParallelGraphExecutor();
        }

        if (executionType != null && executionType.equalsIgnoreCase("conditional")) {
            return new ConditionalGraphExecutor();
        }

        return new LinearGraphExecutor();

    }


}

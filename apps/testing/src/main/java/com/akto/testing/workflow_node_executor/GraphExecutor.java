package com.akto.testing.workflow_node_executor;

import com.akto.dto.testing.GraphExecutorRequest;
import com.akto.dto.testing.GraphExecutorResult;

public abstract class GraphExecutor {
    
    public abstract GraphExecutorResult executeGraph(GraphExecutorRequest graphExecutorRequest);

}

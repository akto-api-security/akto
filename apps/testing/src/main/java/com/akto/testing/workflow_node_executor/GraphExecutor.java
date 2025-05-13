package com.akto.testing.workflow_node_executor;

import com.akto.dto.testing.GraphExecutorRequest;
import com.akto.dto.testing.GraphExecutorResult;
import com.akto.dto.testing.TestingRunResult;
import com.akto.test_editor.execution.Memory;


import java.util.List;

public abstract class GraphExecutor {
    public abstract GraphExecutorResult executeGraph(GraphExecutorRequest graphExecutorRequest, boolean debug, List<TestingRunResult.TestLog> testLogs, Memory memory);
}

package com.akto.testing;

import com.akto.dto.ApiInfo;
import com.akto.dto.testing.*;
import com.akto.test_editor.execution.Memory;
import com.akto.testing.workflow_node_executor.GraphExecutor;
import com.akto.testing.workflow_node_executor.GraphExecutorFactory;

import java.util.List;
import java.util.Map;

public class ApiWorkflowExecutor {

    public GraphExecutorResult init(GraphExecutorRequest graphExecutorRequest, boolean debug, List<TestingRunResult.TestLog> testLogs, Memory memory) {
        GraphExecutor graphExecutor = GraphExecutorFactory.fetchExecutor(graphExecutorRequest);
        GraphExecutorResult graphExecutorResult = graphExecutor.executeGraph(graphExecutorRequest,debug,testLogs, memory);
        return graphExecutorResult;
    }

}
package com.akto.testing.workflow_node_executor;

import java.util.List;
import java.util.Map;

import com.akto.dto.api_workflow.Node;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.WorkflowTestResult;
import com.akto.test_editor.execution.Memory;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class NodeExecutor {
    boolean allowAllCombinations;
    public NodeExecutor(boolean allowAllCombinations) {
        this.allowAllCombinations = allowAllCombinations;
    }
    public abstract WorkflowTestResult.NodeResult processNode(Node node, Map<String, Object> valuesMap, Boolean allowAllStatusCodes, boolean debug, List<TestingRunResult.TestLog> testLogs, Memory memory);

}

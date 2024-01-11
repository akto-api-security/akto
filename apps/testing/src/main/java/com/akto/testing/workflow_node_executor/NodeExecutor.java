package com.akto.testing.workflow_node_executor;

import java.util.Map;

import com.akto.dto.api_workflow.Node;
import com.akto.dto.testing.WorkflowTestResult;

public abstract class NodeExecutor {
    
    public abstract WorkflowTestResult.NodeResult processNode(Node node, Map<String, Object> valuesMap, Boolean allowAllStatusCodes);
}

package com.akto.testing.workflow_node_executor;

import com.akto.dto.api_workflow.Node;
import com.akto.dto.testing.YamlNodeDetails;

public class NodeExecutorFactory {
    
    public NodeExecutor getExecutor(Node node) {
    
        if (node.getWorkflowNodeDetails() instanceof YamlNodeDetails) {
            return new YamlNodeExecutor();
        }
        return new ApiNodeExecutor();

    }

}

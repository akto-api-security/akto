package com.akto.testing.workflow_node_executor;

import com.akto.dto.api_workflow.Node;
import com.akto.dto.testing.NodeDetails.DefaultNodeDetails;
import com.akto.dto.testing.NodeDetails.NodeDetails;

public class NodeExecutorFactory {
    
    public NodeExecutor getExecutor(Node node) {
    
        NodeDetails nodeDetails = node.getWorkflowNodeDetails().getNodeDetails();
        if (nodeDetails instanceof DefaultNodeDetails) {
            return new ApiNodeExecutor();
        }
        return new YamlNodeExecutor();

    }

}

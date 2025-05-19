package com.akto.testing.workflow_node_executor;

import com.akto.dto.api_workflow.Node;
import com.akto.dto.testing.YamlNodeDetails;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NodeExecutorFactory {
    private boolean allowAllCombinations;
    public NodeExecutorFactory(boolean allowAllCombinations) {
        this.allowAllCombinations = allowAllCombinations;
    }
    
    public NodeExecutor getExecutor(Node node) {
    
        if (node.getWorkflowNodeDetails() instanceof YamlNodeDetails) {
            return new YamlNodeExecutor(this.allowAllCombinations);
        }
        return new ApiNodeExecutor(this.allowAllCombinations);

    }

}

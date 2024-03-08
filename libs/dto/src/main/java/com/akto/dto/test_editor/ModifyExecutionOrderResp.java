package com.akto.dto.test_editor;

import java.util.List;

public class ModifyExecutionOrderResp {
    
    List<ExecutorNode> executorNodes;
    String error;

    public ModifyExecutionOrderResp() {
    }

    public ModifyExecutionOrderResp(List<ExecutorNode> executorNodes, String error) {
        this.executorNodes = executorNodes;
        this.error = error;
    }

    public List<ExecutorNode> getExecutorNodes() {
        return executorNodes;
    }

    public void setExecutorNodes(List<ExecutorNode> executorNodes) {
        this.executorNodes = executorNodes;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

}

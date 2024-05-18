package com.akto.dto.test_editor;

import java.util.List;

public class ModifyExecutionOrderResp {
    
    List<ExecutorNode> executorNodes;
    List<ExecutorNode> forOneExecutorNodes;
    String error;

    public ModifyExecutionOrderResp() {
    }

    public ModifyExecutionOrderResp(List<ExecutorNode> executorNodes, String error) {
        this.executorNodes = executorNodes;
        this.error = error;
        this.forOneExecutorNodes = null;
    }

    public ModifyExecutionOrderResp(List<ExecutorNode> executorNodes,List<ExecutorNode> forOneExecutorNodes, String error) {
        this.executorNodes = executorNodes;
        this.error = error;
        this.forOneExecutorNodes = forOneExecutorNodes;
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

    public List<ExecutorNode> getForOneExecutorNodes() {return forOneExecutorNodes;}

    public void setForOneExecutorNodes(List<ExecutorNode> forOneExecutorNodes) {
        this.forOneExecutorNodes = forOneExecutorNodes;
    }
}

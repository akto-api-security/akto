package com.akto.dto.test_editor;

import java.util.List;

public class ExecutorNode {
    
    private String nodeType;
    
    private List<ExecutorNode> childNodes;

    private Object values;

    private String operationType;

    public ExecutorNode(String nodeType, List<ExecutorNode> childNodes, Object values, String operationType) {
        this.nodeType = nodeType;
        this.childNodes = childNodes;
        this.values = values;
        this.operationType = operationType;
    }

    public ExecutorNode() { }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public List<ExecutorNode> getChildNodes() {
        return childNodes;
    }

    public void setChildNodes(List<ExecutorNode> childNodes) {
        this.childNodes = childNodes;
    }
    
    public Object getValues() {
        return values;
    }

    public void setValues(Object values) {
        this.values = values;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }
    
}

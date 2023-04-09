package com.akto.dto.test_editor;

import java.util.List;

public class FilterNode {
    
    private String operand;
    private Boolean result;
    private String concernedProperty;
    private Object values;
    private String nodeType;
    private List<FilterNode> childNodes;
    private String subConcernedProperty;

    public FilterNode(String operand, Boolean result, String concernedProperty, Object values, String nodeType, 
        List<FilterNode> childNodes, String subConcernedProperty) {
        this.operand = operand;
        this.result = result;
        this.concernedProperty = concernedProperty;
        this.values = values;
        this.nodeType = nodeType;
        this.childNodes = childNodes;
        this.subConcernedProperty = subConcernedProperty;
    }

    public FilterNode() { }

    public String getOperand() {
        return operand;
    }

    public void setOperand(String operand) {
        this.operand = operand;
    }

    public Boolean getResult() {
        return result;
    }

    public void setResult(Boolean result) {
        this.result = result;
    }

    public String getConcernedProperty() {
        return concernedProperty;
    }

    public void setConcernedProperty(String concernedProperty) {
        this.concernedProperty = concernedProperty;
    }

    public Object getValues() {
        return values;
    }

    public void setValues(Object values) {
        this.values = values;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public List<FilterNode> getChildNodes() {
        return childNodes;
    }

    public void setChildNodes(List<FilterNode> childNodes) {
        this.childNodes = childNodes;
    }

    public String getSubConcernedProperty() {
        return subConcernedProperty;
    }

    public void setSubConcernedProperty(String subConcernedProperty) {
        this.subConcernedProperty = subConcernedProperty;
    }

}


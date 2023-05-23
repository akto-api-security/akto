package com.akto.dto.test_editor;

import java.util.ArrayList;
import java.util.List;

public class FilterNode {
    
    private String operand;
    private Boolean result;
    private String concernedProperty;
    private Object values;
    private String nodeType;
    private List<FilterNode> childNodes;
    private String subConcernedProperty;
    private String bodyOperand;
    private String contextProperty;

    public FilterNode(String operand, Boolean result, String concernedProperty, Object values, String nodeType, 
        List<FilterNode> childNodes, String subConcernedProperty, String bodyOperand, String contextProperty) {
        this.operand = operand;
        this.result = result;
        this.concernedProperty = concernedProperty;
        this.values = values;
        this.nodeType = nodeType;
        this.childNodes = childNodes;
        this.subConcernedProperty = subConcernedProperty;
        this.bodyOperand = bodyOperand;
        this.contextProperty = contextProperty;
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

    public String getBodyOperand() {
        return bodyOperand;
    }

    public void setBodyOperand(String bodyOperand) {
        this.bodyOperand = bodyOperand;
    }

    public String getContextProperty() {
        return contextProperty;
    }

    public void setContextProperty(String contextProperty) {
        this.contextProperty = contextProperty;
    }

    public List<Object> fetchNodeValues() {
        List<Object> valListCopy = new ArrayList<>();
        List<Object> valueList = (List) this.values;
        for (Object objVal: valueList) {
            valListCopy.add(objVal);
        }
        return valListCopy;
    }

}


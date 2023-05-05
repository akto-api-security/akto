package com.akto.dto.test_editor;

public class DataOperandFilterRequest {
    
    private Object data;
    private Object queryset;
    // private List<String> matchingKeySet;
    private String operand;
    // private String concernedSubProperty;

    public DataOperandFilterRequest(Object data, Object queryset, String operand) {
        this.data = data;
        this.queryset = queryset;
        this.operand = operand;
    }

    public DataOperandFilterRequest() { }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public Object getQueryset() {
        return queryset;
    }

    public void setQueryset(Object queryset) {
        this.queryset = queryset;
    }

    public String getOperand() {
        return operand;
    }

    public void setOperand(String operand) {
        this.operand = operand;
    }

}

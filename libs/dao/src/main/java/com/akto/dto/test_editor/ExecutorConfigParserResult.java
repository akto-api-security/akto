package com.akto.dto.test_editor;

public class ExecutorConfigParserResult {
    
    private ExecutorNode node;
    private Boolean isValid;
    private String errMsg;

    public ExecutorConfigParserResult(ExecutorNode node, Boolean isValid, String errMsg) {
        this.node = node;
        this.isValid = isValid;
        this.errMsg = errMsg;
    }

    public ExecutorConfigParserResult() { }

    public ExecutorNode getNode() {
        return node;
    }

    public void setNode(ExecutorNode node) {
        this.node = node;
    }

    public Boolean getIsValid() {
        return isValid;
    }

    public void setIsValid(Boolean isValid) {
        this.isValid = isValid;
    }

    public String getErrMsg() {
        return errMsg;
    }

    public void setErrMsg(String errMsg) {
        this.errMsg = errMsg;
    }

}

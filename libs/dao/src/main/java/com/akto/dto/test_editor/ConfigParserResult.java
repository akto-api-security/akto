package com.akto.dto.test_editor;

public class ConfigParserResult {
    
    private FilterNode node;
    private Boolean isValid;
    private String errMsg;

    public ConfigParserResult(FilterNode node, Boolean isValid, String errMsg) {
        this.node = node;
        this.isValid = isValid;
        this.errMsg = errMsg;
    }

    public ConfigParserResult() { }

    public FilterNode getNode() {
        return node;
    }

    public void setNode(FilterNode node) {
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

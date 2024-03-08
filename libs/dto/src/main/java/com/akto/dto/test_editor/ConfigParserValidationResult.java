package com.akto.dto.test_editor;

public class ConfigParserValidationResult {
    
    private Boolean isValid;
    private String errMsg;
    public ConfigParserValidationResult(Boolean isValid, String errMsg) {
        this.isValid = isValid;
        this.errMsg = errMsg;
    }
    
    public ConfigParserValidationResult() { }

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

package com.akto.dto.test_editor;

public class ExecutionResult {
    
    private Boolean success;
    private String errMsg;
    
    public ExecutionResult(Boolean success, String errMsg) {
        this.success = success;
        this.errMsg = errMsg;
    }

    public ExecutionResult() { }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getErrMsg() {
        return errMsg;
    }

    public void setErrMsg(String errMsg) {
        this.errMsg = errMsg;
    }
    
}

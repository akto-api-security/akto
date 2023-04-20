package com.akto.dto.test_editor;

import com.akto.dto.RawApi;

public class ExecutorSingleRequest {
    
    private Boolean success;
    private String errMsg;
    private RawApi rawApi;
    
    public ExecutorSingleRequest(Boolean success, String errMsg, RawApi rawApi) {
        this.success = success;
        this.errMsg = errMsg;
        this.rawApi = rawApi;
    }

    public ExecutorSingleRequest() { }

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

    public RawApi getRawApi() {
        return rawApi;
    }

    public void setRawApi(RawApi rawApi) {
        this.rawApi = rawApi;
    }

}

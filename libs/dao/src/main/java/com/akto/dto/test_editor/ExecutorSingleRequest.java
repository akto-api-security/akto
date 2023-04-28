package com.akto.dto.test_editor;

import com.akto.dto.RawApi;

public class ExecutorSingleRequest {
    
    private Boolean success;
    private String errMsg;
    private RawApi rawApi;
    private Boolean followRedirect;

    public ExecutorSingleRequest(Boolean success, String errMsg, RawApi rawApi, Boolean followRedirect) {
        this.success = success;
        this.errMsg = errMsg;
        this.rawApi = rawApi;
        this.followRedirect = followRedirect;
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

    public Boolean getFollowRedirect() {
        return followRedirect;
    }

    public void setFollowRedirect(Boolean followRedirect) {
        this.followRedirect = followRedirect;
    }
    
}

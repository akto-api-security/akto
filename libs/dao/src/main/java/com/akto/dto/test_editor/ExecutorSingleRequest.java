package com.akto.dto.test_editor;

import java.util.List;

import com.akto.dto.RawApi;

public class ExecutorSingleRequest {
    
    private Boolean success;
    private String errMsg;
    private List<RawApi> rawApis;
    private Boolean followRedirect;

    public ExecutorSingleRequest(Boolean success, String errMsg, List<RawApi> rawApis, Boolean followRedirect) {
        this.success = success;
        this.errMsg = errMsg;
        this.rawApis = rawApis;
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

    public List<RawApi> getRawApis() {
        return rawApis;
    }

    public void setRawApis(List<RawApi> rawApis) {
        this.rawApis = rawApis;
    }

    public Boolean getFollowRedirect() {
        return followRedirect;
    }

    public void setFollowRedirect(Boolean followRedirect) {
        this.followRedirect = followRedirect;
    }
    
}

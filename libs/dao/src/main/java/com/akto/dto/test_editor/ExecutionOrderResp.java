package com.akto.dto.test_editor;

public class ExecutionOrderResp {
    
    private String error;

    private boolean followRedirect;

    public ExecutionOrderResp(String error, boolean followRedirect) {
        this.error = error;
        this.followRedirect = followRedirect;
    }

    public ExecutionOrderResp(){
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public boolean getFollowRedirect() {
        return followRedirect;
    }

    public void setFollowRedirect(boolean followRedirect) {
        this.followRedirect = followRedirect;
    }

}

package com.akto.dto.testing;

import java.util.ArrayList;

public class LoginFlowResponse {
    
    private ArrayList<Object> responses;

    private String error;

    private Boolean success;


    public LoginFlowResponse() { }
    public LoginFlowResponse(ArrayList<Object> responses, String error, Boolean success) {
        this.responses = responses;
        this.error = error;
        this.success = success;
    }

    public ArrayList<Object> getResponses() {
        return this.responses;
    }

    public Boolean getSuccess() {
        return this.success;
    }

    public String getError() {
        return this.error;
    }

    public void setResponses(ArrayList<Object> responses) {
        this.responses = responses;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public void setError(String error) {
        this.error = error;
    }

}

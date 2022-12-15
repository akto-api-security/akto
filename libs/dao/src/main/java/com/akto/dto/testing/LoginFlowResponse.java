package com.akto.dto.testing;

import java.util.ArrayList;

public class LoginFlowResponse {
    
    private ArrayList<Object> responses;

    private Boolean success;


    public LoginFlowResponse() { }
    public LoginFlowResponse(ArrayList<Object> responses, Boolean success) {
        this.responses = responses;
        this.success = success;
    }

    public ArrayList<Object> getResponses() {
        return this.responses;
    }

    public Boolean getSuccess() {
        return this.success;
    }

    public void setResponses(ArrayList<Object> responses) {
        this.responses = responses;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

}

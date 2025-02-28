package com.akto.dto.testing;

import java.util.ArrayList;

import com.google.gson.Gson;

public class LoginFlowResponse {
    
    private ArrayList<Object> responses;
    private static final Gson gson = new Gson();

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

    public static LoginFlowResponse getLoginFlowResponse(String responseString) {
        LoginFlowResponse response = gson.fromJson(responseString, LoginFlowResponse.class);
        return response;
    }

    @Override
    public String toString() {
        return gson.toJson(this);
    }

}

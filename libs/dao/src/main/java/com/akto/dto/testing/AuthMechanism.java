package com.akto.dto.testing;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.OriginalHttpRequest;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;

public class AuthMechanism {
    private ObjectId id;
    private List<AuthParam> authParams;

    private String type;

    private ArrayList<RequestData> requestData;

    public AuthMechanism() {
    }

    public AuthMechanism(List<AuthParam> authParams, ArrayList<RequestData> requestData, String type) {
        this.authParams = authParams;
        this.requestData = requestData;
        this.type = type;
    }

    public boolean addAuthToRequest(OriginalHttpRequest request) {
        for (AuthParam authParamPair : authParams) {
            boolean result = authParamPair.addAuthTokens(request);
            if (!result) return false;
        }
        return true;
    }

    public boolean removeAuthFromRequest(OriginalHttpRequest request) {
        for (AuthParam authParamPair : authParams) {
            boolean result = authParamPair.removeAuthTokens(request);
            if (!result) return false;
        }
        return true;
    }

    public boolean authTokenPresent(OriginalHttpRequest request) {
        boolean result = true;
        for (AuthParam authParamPair : authParams) {
            result = result && authParamPair.authTokenPresent(request);
        }
        return result;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setRequestData(ArrayList<RequestData> requestData) {
        this.requestData = requestData;
    }

    public List<AuthParam> getAuthParams() {
        return authParams;
    }

    public AuthParam fetchFirstAuthParam() {
        return authParams.get(0);
    }

    //public AuthParam set

    public void setAuthParams(List<AuthParam> authParams) {
        this.authParams = authParams;
    }

    public ArrayList<RequestData> getRequestData() {
        return this.requestData;
    }

    public String getType() {
        return this.type;
    }
}

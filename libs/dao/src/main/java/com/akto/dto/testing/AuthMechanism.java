package com.akto.dto.testing;

import com.akto.dto.HttpRequestParams;
import org.bson.types.ObjectId;

import java.util.List;

public class AuthMechanism {
    private ObjectId id;
    private List<AuthParam> authParams;

    public AuthMechanism() {
    }

    public AuthMechanism(List<AuthParam> authParams) {
        this.authParams = authParams;
    }

    public boolean addAuthToRequest(HttpRequestParams httpRequestParams) {
        for (AuthParam authParamPair : authParams) {
            boolean result = authParamPair.addAuthTokens(httpRequestParams);
            if (!result) return false;
        }
        return true;
    }

    public boolean removeAuthFromRequest(HttpRequestParams httpRequestParams) {
        for (AuthParam authParamPair : authParams) {
            boolean result = authParamPair.removeAuthTokens(httpRequestParams);
            if (!result) return false;
        }
        return true;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public List<AuthParam> getAuthParams() {
        return authParams;
    }

    public void setAuthParams(List<AuthParam> authParams) {
        this.authParams = authParams;
    }

}

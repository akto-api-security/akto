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

    public void addAuthToRequest(HttpRequestParams httpRequestParams) {
        for (AuthParam authParamPair : authParams) {
            authParamPair.addAuthTokens(httpRequestParams);
        }
    }

    public void removeAuthFromRequest(HttpRequestParams httpRequestParams) {
        for (AuthParam authParamPair : authParams) {
            authParamPair.removeAuthTokens(httpRequestParams);
        }
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

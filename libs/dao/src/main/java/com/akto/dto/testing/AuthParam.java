package com.akto.dto.testing;

import com.akto.dto.HttpRequestParams;

public abstract class AuthParam {

    public abstract void addAuthTokens(HttpRequestParams httpRequestParams);
    public abstract void removeAuthTokens(HttpRequestParams httpRequestParams);

    public enum Location {
        HEADER
    }
}

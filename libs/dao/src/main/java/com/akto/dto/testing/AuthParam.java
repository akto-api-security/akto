package com.akto.dto.testing;

import com.akto.dto.HttpRequestParams;

public abstract class AuthParam {

    public abstract boolean addAuthTokens(HttpRequestParams httpRequestParams);
    public abstract boolean removeAuthTokens(HttpRequestParams httpRequestParams);

    public enum Location {
        HEADER
    }
}

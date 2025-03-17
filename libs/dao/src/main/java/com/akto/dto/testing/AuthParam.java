package com.akto.dto.testing;

import com.akto.dto.OriginalHttpRequest;

import java.util.ArrayList;

public abstract class AuthParam {

    protected abstract AuthParam copy();
    public abstract boolean addAuthTokens(OriginalHttpRequest request);
    public abstract boolean removeAuthTokens(OriginalHttpRequest request);

    public abstract boolean authTokenPresent(OriginalHttpRequest request);

    public abstract String getValue();

    public abstract String getKey();

    public abstract void setValue(String value);

    public enum Location {
        HEADER,
        BODY
    }

}

package com.akto.dto.testing;

import com.akto.dto.OriginalHttpRequest;

public abstract class AuthParam {

    abstract boolean addAuthTokens(OriginalHttpRequest request);
    public abstract boolean removeAuthTokens(OriginalHttpRequest request);

    public abstract boolean authTokenPresent(OriginalHttpRequest request);

    public abstract String getValue();

    public abstract String getKey();

    public abstract Boolean getShowHeader();

    public abstract Location getWhere();

    public abstract void setValue(String value);

    public enum Location {
        HEADER,
        BODY,
        TLS
    }
}

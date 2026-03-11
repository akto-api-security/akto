package com.akto.dto.testing;

import com.akto.dto.OriginalHttpRequest;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public abstract class AuthParam {

    public abstract boolean addAuthTokens(OriginalHttpRequest request);
    public abstract boolean removeAuthTokens(OriginalHttpRequest request);

    public abstract boolean authTokenPresent(OriginalHttpRequest request);

    public abstract String getValue();

    public abstract String getKey();

    public abstract void setValue(String value);

    private String username;
    private String password;
    private String targetUrl;
    private String method;
    private String algorithm;

    public enum Location {
        HEADER,
        BODY,
        TLS
    }
}

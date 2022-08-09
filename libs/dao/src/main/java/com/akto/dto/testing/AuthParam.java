package com.akto.dto.testing;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.OriginalHttpRequest;

public abstract class AuthParam {

    public abstract boolean addAuthTokens(OriginalHttpRequest request);
    public abstract boolean removeAuthTokens(OriginalHttpRequest request);

    public enum Location {
        HEADER
    }
}

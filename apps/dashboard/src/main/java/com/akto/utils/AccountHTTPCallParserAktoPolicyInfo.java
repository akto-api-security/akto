package com.akto.utils;

import com.akto.parsers.HttpCallParser;
import com.akto.runtime.policies.AktoPolicy;

public class AccountHTTPCallParserAktoPolicyInfo {
    private HttpCallParser httpCallParser;
    private AktoPolicy policy;

    public HttpCallParser getHttpCallParser() {
        return httpCallParser;
    }

    public void setHttpCallParser(HttpCallParser httpCallParser) {
        this.httpCallParser = httpCallParser;
    }

    public AktoPolicy getPolicy() {
        return policy;
    }

    public void setPolicy(AktoPolicy policy) {
        this.policy = policy;
    }
}

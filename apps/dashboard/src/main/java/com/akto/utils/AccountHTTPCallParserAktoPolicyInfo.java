package com.akto.utils;

import com.akto.parsers.HttpCallParser;
import com.akto.runtime.policies.AktoPolicyNew;

public class AccountHTTPCallParserAktoPolicyInfo {
    private HttpCallParser httpCallParser;
    private AktoPolicyNew policy;

    public HttpCallParser getHttpCallParser() {
        return httpCallParser;
    }

    public void setHttpCallParser(HttpCallParser httpCallParser) {
        this.httpCallParser = httpCallParser;
    }

    public AktoPolicyNew getPolicy() {
        return policy;
    }

    public void setPolicy(AktoPolicyNew policy) {
        this.policy = policy;
    }
}

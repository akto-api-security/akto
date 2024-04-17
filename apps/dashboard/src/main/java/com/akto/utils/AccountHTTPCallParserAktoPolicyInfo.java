package com.akto.utils;

import com.akto.analyser.ResourceAnalyser;
import com.akto.parsers.HttpCallParser;

public class AccountHTTPCallParserAktoPolicyInfo {
    private HttpCallParser httpCallParser;
    private ResourceAnalyser resourceAnalyser;

    public HttpCallParser getHttpCallParser() {
        return httpCallParser;
    }

    public void setHttpCallParser(HttpCallParser httpCallParser) {
        this.httpCallParser = httpCallParser;
    }

    public ResourceAnalyser getResourceAnalyser() {
        return resourceAnalyser;
    }

    public void setResourceAnalyser(ResourceAnalyser resourceAnalyser) {
        this.resourceAnalyser = resourceAnalyser;
    }
}

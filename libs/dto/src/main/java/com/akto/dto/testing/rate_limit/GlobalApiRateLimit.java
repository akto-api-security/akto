package com.akto.dto.testing.rate_limit;

import okhttp3.Request;

public class GlobalApiRateLimit implements ApiRateLimit {

    int maxRequests;
    public GlobalApiRateLimit(int maxRequests) {
        this.maxRequests = maxRequests;
    }

    public GlobalApiRateLimit() {
        this.maxRequests = 0;
    }

    @Override
    public int getMaxRequests(Request request) {
        return maxRequests;
    }

    public int getMaxRequests() {
        return maxRequests;
    }

    public void setMaxRequests(int maxRequests) {
        this.maxRequests = maxRequests;
    }
}

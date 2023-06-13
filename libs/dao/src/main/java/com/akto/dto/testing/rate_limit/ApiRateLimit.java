package com.akto.dto.testing.rate_limit;

import okhttp3.Request;

public interface ApiRateLimit {
    public int getMaxRequests(Request request);
}

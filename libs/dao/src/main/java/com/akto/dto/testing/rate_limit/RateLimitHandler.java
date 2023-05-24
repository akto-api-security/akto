package com.akto.dto.testing.rate_limit;

import okhttp3.Request;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimitHandler {
    Map<ApiRateLimit, Integer> rateLimits = new ConcurrentHashMap<>();
    private static final RateLimitHandler instance = new RateLimitHandler();
    public static RateLimitHandler getInstance() {
        return instance;
    }

    public Map<ApiRateLimit, Integer> getRateLimitsMap() {
        return rateLimits;
    }

    public boolean shouldWait (Request request) {
        Set<ApiRateLimit> rateLimits = this.rateLimits.keySet();
        for (ApiRateLimit rateLimit : rateLimits) {
            int availableRequests = this.rateLimits.get(rateLimit);
            if (availableRequests > 0) {
                this.rateLimits.put(rateLimit, availableRequests - 1);
                return false;
            }
        }
        return true;
    }

    private RateLimitHandler() {
    }
}

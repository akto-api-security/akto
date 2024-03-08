package com.akto.dto.testing.rate_limit;

import okhttp3.Request;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimitHandler {
    Map<ApiRateLimit, Integer> rateLimits = new ConcurrentHashMap<>();
    private static final Map<Integer, RateLimitHandler> handlerMap = new HashMap<>();

    public static RateLimitHandler getInstance(int accountId) {
        RateLimitHandler handler = handlerMap.get(accountId);
        if (handler == null) {
            synchronized (handlerMap) {
                handler = handlerMap.get(accountId);
                if (handler == null) {
                    handler = new RateLimitHandler();
                    handlerMap.put(accountId, handler);
                }
            }
        }
        return handler;
    }

    public Map<ApiRateLimit, Integer> getRateLimitsMap() {
        return rateLimits;
    }

    public boolean shouldWait (Request request) {
        if (this.rateLimits.isEmpty()) {
            return false;
        }
        Set<ApiRateLimit> rateLimits = this.rateLimits.keySet();
        for (ApiRateLimit rateLimit : rateLimits) {
            if (rateLimit.getMaxRequests(request) == 0) {
                return false;
            }
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

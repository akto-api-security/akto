package com.akto.service.insights;

import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Immutable request scope, captured once so it can be propagated into worker threads. */
@Getter
@AllArgsConstructor
public class InsightContext {
    private final int accountId;
    private final int userId;
    private final CONTEXT_SOURCE contextSource;
    private final int startTs;
    private final int endTs;

    /** Bundle cache key — traffic/risk maps are RBAC-scoped by user, so userId must be part of it. */
    public String bundleCacheKey() {
        return accountId + "_" + userId + "_" + (contextSource != null ? contextSource.name() : "null");
    }
}

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

    /**
     * Bundle cache key — traffic/risk maps are RBAC-scoped by user, so userId must be part of it.
     * The date range is part of it too: the bundle holds range-scoped reads (hostSeverityCounts,
     * subCategoryCounts, skillSeverityCounts are all fetched for [startTs, endTs]), so two
     * requests differing only by range must not share an entry — a dashboard whose date filter
     * changes within the cache TTL would otherwise keep serving the previous range's numbers.
     */
    public String bundleCacheKey() {
        return accountId + "_" + userId + "_" + (contextSource != null ? contextSource.name() : "null")
                + "_" + startTs + "_" + endTs;
    }
}

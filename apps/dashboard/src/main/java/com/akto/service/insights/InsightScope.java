package com.akto.service.insights;

/**
 * LIST: fetchInsightsList runs all providers under this scope — must stay cheap (in-memory over
 * the shared bundle, no per-provider network round-trips). DETAIL: fetchInsightDetail only, may
 * do the heavier bounded paging a provider needs for its evidence table.
 */
public enum InsightScope {
    LIST,
    DETAIL
}

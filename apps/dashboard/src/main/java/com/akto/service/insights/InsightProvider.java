package com.akto.service.insights;

public interface InsightProvider {

    /**
     * LIST: fast path, no external (ES/threat-backend detail) round trips — providers
     * may set InsightResult.metricsComplete=false and skip expensive work.
     * DETAIL: full computation, one LLM narrative call on cache miss.
     */
    enum Scope { LIST, DETAIL }

    InsightId id();

    /** Bump whenever the metric shape changes — baked into the narrative cache key. */
    int providerVersion();

    InsightResult compute(InsightDataBundle bundle, InsightContext ctx, Scope scope);
}

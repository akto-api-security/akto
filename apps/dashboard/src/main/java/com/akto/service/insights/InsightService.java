package com.akto.service.insights;

import com.akto.dao.context.Context;
import com.akto.dao.insights.InsightNarrativeCacheDao;
import com.akto.dto.insights.InsightNarrativeCache;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.service.insights.narrative.AgenticNarrativeStrategy;
import com.akto.service.insights.narrative.EndpointNarrativeStrategy;
import com.akto.service.insights.narrative.InsightNarrativeStrategy;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.BasicDBObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrates the insights feature: shared-bundle load, provider dispatch, evidence
 * bounding, and (detail only) the narrative cache + LLM call. This is the one class
 * InsightsAction talks to.
 */
public class InsightService {

    private static final LoggerMaker logger = new LoggerMaker(InsightService.class, LogDb.DASHBOARD);
    private static final int EVIDENCE_ROW_CAP = 20;
    private static final long BUNDLE_CACHE_TTL_MS = 60_000;
    private static final long NARRATIVE_TTL_DAYS = 7;
    private static final int PROVIDER_COMPUTE_TIMEOUT_SECONDS = 10;

    private static final Map<String, CachedBundle> BUNDLE_CACHE = new ConcurrentHashMap<>();
    // Providers are pure, independent functions over the same immutable bundle, so
    // fanning them out is safe; the pool is separate from InsightDataLoader's own
    // (that one is sized for 2 concurrent I/O calls, this one for up to 10 CPU-bound ones).
    private static final ExecutorService PROVIDER_EXECUTOR = Executors.newFixedThreadPool(6);

    private final InsightDataLoader loader = new InsightDataLoader();
    private final InsightNarrativeStrategy endpointNarrativeStrategy = new EndpointNarrativeStrategy();
    private final InsightNarrativeStrategy agenticNarrativeStrategy = new AgenticNarrativeStrategy();

    private static final class CachedBundle {
        final InsightDataBundle bundle;
        final long loadedAtMs;
        CachedBundle(InsightDataBundle bundle, long loadedAtMs) { this.bundle = bundle; this.loadedAtMs = loadedAtMs; }
    }

    /**
     * Public so the Security Posture page can share this cache rather than building a second,
     * near-identical read layer: that page renders posture widgets and the "Act now" insights
     * side by side off the same account data, so a private cache here would mean loading
     * everything twice per page view. BUNDLE_CACHE is static, so every InsightService instance
     * (one per action) shares the entry.
     */
    public InsightDataBundle getOrLoadBundle(InsightContext ctx) {
        String key = ctx.bundleCacheKey();
        CachedBundle cached = BUNDLE_CACHE.compute(key, (k, existing) -> {
            if (existing != null && System.currentTimeMillis() - existing.loadedAtMs < BUNDLE_CACHE_TTL_MS) return existing;
            return new CachedBundle(loader.load(ctx), System.currentTimeMillis());
        });
        return cached.bundle;
    }

    public List<InsightResult> listInsights(InsightContext ctx, InsightId.Group group) {
        InsightDataBundle bundle = getOrLoadBundle(ctx);
        final int accountId = ctx.getAccountId();
        final Integer userId = ctx.getUserId();
        final CONTEXT_SOURCE contextSource = ctx.getContextSource();

        List<InsightProvider> providers = new ArrayList<>();
        for (InsightProvider provider : InsightProviderRegistry.all().values()) {
            if (provider.id().getGroup() == group) providers.add(provider);
        }
        List<Future<InsightResult>> futures = new ArrayList<>(providers.size());
        for (InsightProvider provider : providers) {
            futures.add(PROVIDER_EXECUTOR.submit(withContext(accountId, userId, contextSource,
                    () -> computeSafely(provider, bundle, ctx, InsightProvider.Scope.LIST))));
        }

        List<InsightResult> results = new ArrayList<>(providers.size());
        for (int i = 0; i < futures.size(); i++) {
            try {
                results.add(futures.get(i).get(PROVIDER_COMPUTE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            } catch (Exception e) {
                logger.errorAndAddToDb("Insight provider " + providers.get(i).id() + " timed out or failed: " + e.getMessage());
                results.add(failureResult(providers.get(i)));
            }
        }
        // Worst-first: a reader should see CRITICAL/HIGH cards before LOW ones, not the fixed
        // cheapest-first build order the registry iterates in. Disabled ("Coming soon") cards sort
        // last regardless of severity — there's nothing to act on yet. List.sort is stable, so
        // insights tied on both keep their original registry order as the final tiebreak.
        results.sort(Comparator
                .comparing(InsightResult::isDisabled)
                .thenComparingInt(r -> severityRank(r.getSeverity())));
        return results;
    }

    /** CRITICAL first, matching the same rank convention the Violations grid's severity column
     *  sort already uses. Missing/unrecognized severity sorts last, after LOW. */
    private static int severityRank(String severity) {
        if (severity == null) return 5;
        switch (severity.toUpperCase(java.util.Locale.US)) {
            case "CRITICAL": return 1;
            case "HIGH": return 2;
            case "MEDIUM": return 3;
            case "LOW": return 4;
            default: return 5;
        }
    }

    private <T> Callable<T> withContext(int accountId, Integer userId, CONTEXT_SOURCE contextSource, Callable<T> body) {
        return () -> {
            Context.accountId.set(accountId);
            Context.userId.set(userId);
            Context.contextSource.set(contextSource);
            try {
                return body.call();
            } finally {
                Context.accountId.remove();
                Context.userId.remove();
                Context.contextSource.remove();
            }
        };
    }

    public InsightResult getInsightDetail(InsightContext ctx, InsightId id, boolean forceRefresh) {
        InsightProvider provider = InsightProviderRegistry.get(id);
        if (provider == null) throw new IllegalArgumentException("Unknown insightId: " + id);

        InsightDataBundle bundle = getOrLoadBundle(ctx);
        InsightResult r = computeSafely(provider, bundle, ctx, InsightProvider.Scope.DETAIL);

        InsightNarrativeStrategy strategy = narrativeStrategyFor(provider.id().getGroup());
        BasicDBObject narrativeInput = strategy.buildNarrativeInput(r);
        r.setNarrativeInput(narrativeInput);
        String fingerprint = fingerprint(ctx, provider, narrativeInput, strategy.promptTag());

        if (!forceRefresh) {
            InsightNarrativeCache cached = InsightNarrativeCacheDao.instance.get(fingerprint);
            if (cached != null) {
                strategy.apply(r, strategy.fromCache(cached));
                r.setNarrativeStatus("OK");
                return r;
            }
        }
        generateAndCacheNarrative(strategy, r, narrativeInput, fingerprint, provider.providerVersion());
        return r;
    }

    /** ARGUS_POSTURE (Argus/agentic posture findings) gets its own strategy — every other group
     *  (ATLAS_DISCOVERY, GUARDRAIL_VIOLATIONS) shares the original concern/impact/remediation one. */
    private InsightNarrativeStrategy narrativeStrategyFor(InsightId.Group group) {
        return group == InsightId.Group.ARGUS_POSTURE ? agenticNarrativeStrategy : endpointNarrativeStrategy;
    }

    private InsightResult computeSafely(InsightProvider provider, InsightDataBundle bundle, InsightContext ctx, InsightProvider.Scope scope) {
        try {
            InsightResult r = provider.compute(bundle, ctx, scope);
            boundEvidence(r);
            return r;
        } catch (Exception e) {
            logger.errorAndAddToDb("Insight provider " + provider.id() + " failed: " + e.getMessage());
            return failureResult(provider);
        }
    }

    private InsightResult failureResult(InsightProvider provider) {
        InsightResult r = new InsightResult();
        r.setInsightId(provider.id().name());
        r.setTitle(provider.id().getTitle());
        r.setCategory(provider.id().getCategory().name());
        r.setGroup(provider.id().getGroup().name());
        r.setStatus(InsightResult.Status.NO_DATA.name());
        r.setHeadline("This insight could not be computed.");
        r.setDisabled(provider.id().isDisabled());
        return r;
    }

    /** A 50-row evidence table reaching the browser is exactly the mistake AgenticObserveAction's
     * GroupSummary comments warn about — bound every table before it leaves this service. */
    private void boundEvidence(InsightResult r) {
        for (InsightResult.Evidence e : r.getEvidence()) {
            if (e.getRows() != null && e.getRows().size() > EVIDENCE_ROW_CAP) {
                int total = Math.max(e.getTotalRowCount(), e.getRows().size());
                e.setRows(e.getRows().subList(0, EVIDENCE_ROW_CAP));
                e.setTotalRowCount(total);
                e.setTruncated(true);
            }
        }
    }

    /** Fingerprint over the exact bytes sent to the LLM plus the strategy's own promptTag — a
     * changed metric changes the key, so stale prose can never outlive the numbers it describes;
     * the promptTag guards against the two strategies' narrativeInput JSON ever colliding for some
     * degenerate input. TTL below is only a GC backstop. */
    private String fingerprint(InsightContext ctx, InsightProvider provider, BasicDBObject narrativeInput, String promptTag) {
        String raw = ctx.getAccountId() + "|" + ctx.getContextSource() + "|" + provider.id().name() + "|"
                + provider.providerVersion() + "|" + promptTag + "|" + narrativeInput.toJson();
        return InsightUtil.md5(raw);
    }

    private void generateAndCacheNarrative(InsightNarrativeStrategy strategy, InsightResult r, BasicDBObject narrativeInput,
                                            String fingerprint, int providerVersion) {
        BasicDBObject out = strategy.generate(narrativeInput);
        if (out.containsField("error")) {
            logger.error("Narrative generation failed for " + r.getInsightId() + ": " + out.getString("error"));
            r.setNarrativeStatus("UNAVAILABLE");
            return;
        }
        strategy.apply(r, out);
        r.setNarrativeStatus("OK");

        long now = System.currentTimeMillis() / 1000;
        InsightNarrativeCache cache = strategy.toCache(fingerprint, r, providerVersion, out, now,
                new Date((now + TimeUnit.DAYS.toSeconds(NARRATIVE_TTL_DAYS)) * 1000L));
        InsightNarrativeCacheDao.instance.put(cache);
    }
}

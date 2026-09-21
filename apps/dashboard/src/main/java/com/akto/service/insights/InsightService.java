package com.akto.service.insights;

import com.akto.dao.RBACDao;
import com.akto.dao.context.Context;
import com.akto.dao.insights.InsightNarrativeCacheDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.RBAC;
import com.akto.dto.insights.InsightNarrativeCache;
import com.akto.dto.rbac.RbacEnums.ReadWriteAccess;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.gpt.handlers.gpt_prompts.InsightNarrativeHandler;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.service.ask.RecommendationCatalog;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.DashboardMode;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.BasicDBObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private static final class CachedBundle {
        final InsightDataBundle bundle;
        final long loadedAtMs;
        CachedBundle(InsightDataBundle bundle, long loadedAtMs) { this.bundle = bundle; this.loadedAtMs = loadedAtMs; }
    }

    private InsightDataBundle getOrLoadBundle(InsightContext ctx) {
        String key = ctx.bundleCacheKey();
        CachedBundle cached = BUNDLE_CACHE.compute(key, (k, existing) -> {
            if (existing != null && System.currentTimeMillis() - existing.loadedAtMs < BUNDLE_CACHE_TTL_MS) return existing;
            return new CachedBundle(loader.load(ctx), System.currentTimeMillis());
        });
        return cached.bundle;
    }

    public List<InsightResult> listInsights(InsightContext ctx, InsightId.Group group) {
        InsightDataBundle bundle = getOrLoadBundle(ctx);
        prewarm(bundle, group);
        return listInsights(bundle, ctx, group);
    }

    /**
     * Same fan-out/sort/timeout logic as the public overload above, but over a bundle the caller
     * already holds — used by listBrief (Ask Akto overlay) so N groups computed in one request
     * share a single bundle load/cache entry instead of each re-deriving it. Callers of this
     * overload are responsible for calling prewarm(bundle, group) themselves first if they want
     * deterministic per-provider timing (see prewarm()'s javadoc); listInsights(ctx, group) above
     * always does.
     */
    List<InsightResult> listInsights(InsightDataBundle bundle, InsightContext ctx, InsightId.Group group) {
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

    // Which lazy bundle reads each group needs, run on the CALLING thread before providers are
    // fanned out onto PROVIDER_EXECUTOR. Declared beside the group definitions in spirit (see
    // InsightId.Group) — adding a group here forces answering "what does it need warmed".
    //
    // Why prewarm at all, given the reads are already lazy/memoized: lazy alone means whichever
    // provider happens to be scheduled FIRST on the 6-wide, 10-second-per-provider executor pays
    // the whole cost of a read shared by several providers inside its own timeout budget — a
    // nondeterministic failureResult on a random card while siblings that ran later see the
    // already-warm value and succeed. Prewarming on the calling thread (outside any
    // per-provider timeout) fixes the timing without touching the bundle cache key — see
    // InsightDataBundle's "lazy reads" section for why a group-aware cache key was rejected.
    private static final Map<InsightId.Group, List<java.util.function.Consumer<InsightDataBundle>>> PREWARM = buildPrewarmMap();

    private static Map<InsightId.Group, List<java.util.function.Consumer<InsightDataBundle>>> buildPrewarmMap() {
        Map<InsightId.Group, List<java.util.function.Consumer<InsightDataBundle>>> m =
                new java.util.EnumMap<>(InsightId.Group.class);
        m.put(InsightId.Group.ATLAS_DISCOVERY, Collections.emptyList());
        m.put(InsightId.Group.GUARDRAIL_VIOLATIONS, Collections.emptyList());
        m.put(InsightId.Group.API_POSTURE, java.util.Arrays.asList(
                InsightDataBundle::apiInfoRows,
                InsightDataBundle::sensitiveApiCountBySubType));
        m.put(InsightId.Group.TESTING_POSTURE, java.util.Arrays.asList(
                InsightDataBundle::apiInfoRows,
                InsightDataBundle::openIssueSeverityByCollection,
                InsightDataBundle::agingOpenIssues,
                InsightDataBundle::issueRecurrence));
        return m;
    }

    private void prewarm(InsightDataBundle bundle, InsightId.Group group) {
        List<java.util.function.Consumer<InsightDataBundle>> warmers = PREWARM.get(group);
        if (warmers == null) return;
        for (java.util.function.Consumer<InsightDataBundle> warmer : warmers) {
            try {
                warmer.accept(bundle);
            } catch (Exception e) {
                logger.error("InsightService: prewarm failed for group " + group + ": " + e.getMessage());
            }
        }
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

        BasicDBObject narrativeInput = buildNarrativeInput(r);
        r.setNarrativeInput(narrativeInput);
        String fingerprint = fingerprint(ctx, provider, narrativeInput);

        if (!forceRefresh) {
            InsightNarrativeCache cached = InsightNarrativeCacheDao.instance.get(fingerprint);
            if (cached != null) {
                r.setMarkdown(cached.getNarrativeMarkdown());
                r.setNarrativeStatus("OK");
                applyNarrativeSummaryFields(r, cached.getNarrativeConcern(), cached.getNarrativeImpact(), cached.getNarrativeRemediation());
                return r;
            }
        }
        generateAndCacheNarrative(r, narrativeInput, fingerprint, provider.providerVersion());
        return r;
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

    private BasicDBObject buildNarrativeInput(InsightResult r) {
        List<BasicDBObject> metrics = new ArrayList<>();
        for (InsightResult.Metric m : r.getMetrics()) {
            metrics.add(new BasicDBObject("key", m.getKey()).append("label", m.getLabel()).append("formatted", m.getFormatted()));
        }
        List<BasicDBObject> evidence = new ArrayList<>();
        for (InsightResult.Evidence e : r.getEvidence()) {
            evidence.add(new BasicDBObject("id", e.getId()).append("title", e.getTitle())
                    .append("rows", e.getRows()).append("totalRowCount", e.getTotalRowCount()));
        }
        List<BasicDBObject> gaps = new ArrayList<>();
        for (InsightResult.Gap g : r.getDataGaps()) {
            gaps.add(new BasicDBObject("source", g.getSource()).append("reason", g.getReason()).append("impact", g.getImpact()));
        }
        return new BasicDBObject("insightId", r.getInsightId())
                .append("metrics", metrics)
                .append("evidence", evidence)
                .append("caveats", r.getCaveats())
                .append("dataGaps", gaps)
                .append("draftConcern", r.getConcern() != null ? r.getConcern() : "")
                .append("draftImpact", r.getImpact() != null ? r.getImpact() : "")
                .append("draftRemediation", r.getRemediation() != null ? r.getRemediation() : "");
    }

    /** The provider's own concern/impact/remediation are a guaranteed, deterministic fallback —
     *  only replace a field when the model actually returned something non-empty for it. */
    private void applyNarrativeSummaryFields(InsightResult r, String concern, String impact, String remediation) {
        if (concern != null && !concern.isEmpty()) r.setConcern(concern);
        if (impact != null && !impact.isEmpty()) r.setImpact(impact);
        if (remediation != null && !remediation.isEmpty()) r.setRemediation(remediation);
    }

    /** Fingerprint over the exact bytes sent to the LLM — a changed metric changes the key, so
     * stale prose can never outlive the numbers it describes. TTL below is only a GC backstop. */
    private String fingerprint(InsightContext ctx, InsightProvider provider, BasicDBObject narrativeInput) {
        String raw = ctx.getAccountId() + "|" + ctx.getContextSource() + "|" + provider.id().name() + "|"
                + provider.providerVersion() + "|" + InsightNarrativeHandler.PROMPT_VERSION + "|" + narrativeInput.toJson();
        return InsightUtil.md5(raw);
    }

    private void generateAndCacheNarrative(InsightResult r, BasicDBObject narrativeInput, String fingerprint, int providerVersion) {
        BasicDBObject input = new BasicDBObject(InsightNarrativeHandler.NARRATIVE_INPUT, narrativeInput.toJson());
        BasicDBObject out = new InsightNarrativeHandler().handle(input);
        if (out.containsField("error")) {
            logger.error("InsightNarrativeHandler failed for " + r.getInsightId() + ": " + out.getString("error"));
            r.setNarrativeStatus("UNAVAILABLE");
            return;
        }
        String markdown = out.getString("markdown");
        String concern = out.getString("concern");
        String impact = out.getString("impact");
        String remediation = out.getString("remediation");
        r.setMarkdown(markdown);
        r.setNarrativeStatus("OK");
        applyNarrativeSummaryFields(r, concern, impact, remediation);

        long now = System.currentTimeMillis() / 1000;
        InsightNarrativeCache cache = new InsightNarrativeCache(fingerprint, r.getInsightId(), providerVersion,
                InsightNarrativeHandler.PROMPT_VERSION, markdown, concern, impact, remediation, now,
                new Date((now + TimeUnit.DAYS.toSeconds(NARRATIVE_TTL_DAYS)) * 1000L));
        InsightNarrativeCacheDao.instance.put(cache);
    }

    // ---------------------------------------------------------------------------------------
    // Ask Akto overlay — buildAskOverlay is the one entry point AskOverlayAction calls.
    // ---------------------------------------------------------------------------------------

    private static final int TILES_PER_GROUP_CAP = 2;
    private static final int WHAT_CHANGED_LOOKBACK_SECONDS = 14 * 24 * 3600;

    public AskOverlayResponse buildAskOverlay(InsightContext ctx, CONTEXT_SOURCE domain,
            Set<InsightId.Group> groups, int tileLimit, int feedLimit) {
        AskOverlayResponse response = new AskOverlayResponse();
        response.setGeneratedAt(System.currentTimeMillis() / 1000);

        // Layer 1 — cheap, always-on, independent of everything below.
        try {
            response.setRecommendations(RecommendationCatalog.compute(domain));
        } catch (Exception e) {
            logger.error("InsightService.buildAskOverlay: recommendations failed: " + e.getMessage());
        }

        // Layer 2 — insight tiles, CRITICAL/HIGH only, one shared bundle across every group.
        InsightDataBundle bundle = getOrLoadBundle(ctx);
        List<InsightTile> tiles = new ArrayList<>();
        List<String> omitted = new ArrayList<>();
        for (InsightId.Group group : groups) {
            if (!groupVisible(ctx, group)) {
                omitted.add(group.name());
                continue;
            }
            prewarm(bundle, group);
            int addedForGroup = 0;
            for (InsightResult r : listInsights(bundle, ctx, group)) {
                if (addedForGroup >= TILES_PER_GROUP_CAP) break;
                if (!isTileWorthy(r)) continue;
                tiles.add(toTile(r));
                addedForGroup++;
            }
        }
        // Worst-first across groups, same severity convention as listInsights' own sort, capped
        // to tileLimit so one very noisy account doesn't return an unbounded hero row.
        tiles.sort(Comparator.comparingInt(t -> severityRank(t.getSeverity())));
        if (tiles.size() > tileLimit) tiles = new ArrayList<>(tiles.subList(0, tileLimit));
        response.setInsightTiles(tiles);
        response.setOmittedGroups(omitted);

        // The change feed — computed on the fly from data already in the bundle, not from the
        // (stale, collection-unscoped) Activity feed.
        try {
            response.setWhatChanged(computeWhatChanged(bundle, feedLimit));
        } catch (Exception e) {
            logger.error("InsightService.buildAskOverlay: whatChanged failed: " + e.getMessage());
        }

        return response;
    }

    /** Only CRITICAL/HIGH, non-disabled, non-NO_DATA insights are "worth" a tile on the overlay —
     *  everything else stays reachable through the full insights list, just not surfaced here. */
    private boolean isTileWorthy(InsightResult r) {
        if (r.isDisabled()) return false;
        if (InsightResult.Status.NO_DATA.name().equals(r.getStatus())) return false;
        String severity = r.getSeverity();
        return "CRITICAL".equals(severity) || "HIGH".equals(severity);
    }

    /** Copies exactly what a tile renders and nothing more — see InsightTile's own javadoc for
     *  why this is a narrower type rather than a nulled-out InsightResult. */
    private InsightTile toTile(InsightResult r) {
        InsightTile t = new InsightTile();
        t.setInsightId(r.getInsightId());
        t.setTitle(r.getTitle());
        t.setGroup(r.getGroup());
        t.setCategory(r.getCategory());
        t.setStatus(r.getStatus());
        t.setSeverity(r.getSeverity());
        t.setHeadline(r.getHeadline());
        t.setDisabled(r.isDisabled());
        t.setMetricsComplete(r.isMetricsComplete());
        t.setDataGapCount(r.getDataGaps() == null ? 0 : r.getDataGaps().size());
        List<InsightResult.Metric> metrics = r.getMetrics();
        t.setMetrics(metrics == null ? new ArrayList<>() : new ArrayList<>(metrics.subList(0, Math.min(3, metrics.size()))));
        InsightResult.Cta primary = null;
        if (r.getCtas() != null) {
            for (InsightResult.Cta c : r.getCtas()) {
                if (c.isPrimary()) { primary = c; break; }
            }
        }
        t.setPrimaryCta(primary);
        return t;
    }

    /**
     * Per-group RBAC gate for the aggregate response — "omit, don't fail" (the single-label,
     * all-or-nothing roleAccessInterceptor on the endpoint can only 403 the WHOLE request, so
     * per-group filtering has to live here). The first two guards are not optional: they mirror
     * RoleAccessInterceptor's own two early-outs exactly. Without them, an on-prem/unmetered
     * deployment (DashboardMode.isMetered() == false) or an account without the RBAC feature
     * licensed falls through to "role == null" for every group, and the overlay would render
     * completely empty for every user on that deployment — a worse failure than being
     * over-permissive. Fail-closed (role == null -> false) only applies once RBAC is actually in
     * force.
     */
    public boolean groupVisible(InsightContext ctx, InsightId.Group group) {
        if (!DashboardMode.isMetered()) return true;
        if (!UsageMetricCalculator.isRbacFeatureAvailable(ctx.getAccountId())) return true;
        RBAC.Role role = RBACDao.getCurrentRoleForUser(ctx.getUserId(), ctx.getAccountId());
        if (role == null) return false;
        return role.getReadWriteAccessForFeature(group.getRequiredFeature()) != ReadWriteAccess.NO_ACCESS;
    }

    /**
     * "What changed" — computed fresh from data the bundle already has (apiInfoRows) plus one
     * small, independent recent-issues read, rather than the Activity collection (stale, and
     * Activity carries no collection field so it could never be RBAC-scoped anyway). This is a
     * deliberately narrower v1: it covers newly-discovered APIs and newly-opened issues, not yet
     * recently-finished test runs — a source that would need TestingRunDao/
     * TestingRunResultSummariesDao work this pass didn't reach. Extending it later doesn't change
     * the wire contract, just which FeedItem kinds appear.
     */
    private List<FeedItem> computeWhatChanged(InsightDataBundle bundle, int limit) {
        List<FeedItem> items = new ArrayList<>();
        int lookbackCutoff = (int) (System.currentTimeMillis() / 1000 - WHAT_CHANGED_LOOKBACK_SECONDS);

        try {
            for (ApiInfo info : bundle.apiInfoRows()) {
                int discovered = info.getDiscoveredTimestamp();
                if (discovered < lookbackCutoff) continue;
                Map<String, Object> params = new HashMap<>();
                params.put("apiCollectionId", info.getId().getApiCollectionId());
                items.add(new FeedItem("NEW_API",
                        "New endpoint: " + info.getId().getMethod() + " " + info.getId().getUrl(),
                        InsightRoutes.INVENTORY, params, discovered));
            }
        } catch (Exception e) {
            logger.error("InsightService.computeWhatChanged: new-API scan failed: " + e.getMessage());
        }

        try {
            for (TestingRunIssues issue : recentlyOpenedIssues(lookbackCutoff)) {
                Map<String, Object> params = new HashMap<>();
                params.put("status", Collections.singletonList("OPEN"));
                String severity = issue.getSeverity() != null ? issue.getSeverity().name() : "UNKNOWN";
                String url = issue.getId().getApiInfoKey() != null ? issue.getId().getApiInfoKey().getUrl() : "";
                items.add(new FeedItem("NEW_ISSUE", severity + ": new issue on " + url,
                        InsightRoutes.ISSUES, params, issue.getCreationTime()));
            }
        } catch (Exception e) {
            logger.error("InsightService.computeWhatChanged: new-issues scan failed: " + e.getMessage());
        }

        items.sort((a, b) -> Integer.compare(b.getTimestamp(), a.getTimestamp()));
        return items.size() > limit ? new ArrayList<>(items.subList(0, limit)) : items;
    }

    private static final int RECENT_ISSUES_ROW_CAP = 500;

    private List<TestingRunIssues> recentlyOpenedIssues(int cutoff) {
        try {
            org.bson.conversions.Bson baseFilter = com.mongodb.client.model.Filters.gte(TestingRunIssues.CREATION_TIME, cutoff);
            org.bson.conversions.Bson rbacFilter = com.akto.dao.testing_run_findings.TestingRunIssuesDao.instance
                    .addCollectionsFilterForDashboard(baseFilter);
            List<TestingRunIssues> rows = new ArrayList<>();
            try (com.mongodb.client.MongoCursor<TestingRunIssues> cursor = com.akto.dao.testing_run_findings.TestingRunIssuesDao.instance
                    .getMCollection().find(rbacFilter).limit(RECENT_ISSUES_ROW_CAP).cursor()) {
                while (cursor.hasNext()) rows.add(cursor.next());
            }
            return rows;
        } catch (Exception e) {
            logger.error("InsightService.recentlyOpenedIssues failed: " + e.getMessage());
            return Collections.emptyList();
        }
    }
}

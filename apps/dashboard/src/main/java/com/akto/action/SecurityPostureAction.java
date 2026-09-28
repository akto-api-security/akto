package com.akto.action;

import com.akto.action.threat_detection.AbstractThreatDetectionAction;
import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.action.threat_detection.HostSeverityCount;
import com.akto.action.threat_detection.ThreatCategoryCount;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.service.insights.InsightContext;
import com.akto.service.insights.InsightDataBundle;
import com.akto.service.insights.InsightId;
import com.akto.service.insights.InsightResult;
import com.akto.service.insights.InsightService;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListGuardrailViolationPayloadsResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListGuardrailViolationPayloadsResponse.ViolationPayload;
import com.akto.service.posture.PostureDrillNarrativeService;
import com.akto.service.posture.PostureDrillResult;
import com.akto.service.posture.PostureService;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.akto.utils.search.SearchClient;
import com.akto.utils.search.SearchClientFactory;
import com.akto.utils.threat_detection.ThreatDetectionBackendClient;
import com.mongodb.BasicDBObject;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * The AI Security Posture page. One consolidated call returns the whole page — the same shape
 * AgenticDashboardAction uses, and the reason the page is one round trip instead of one per
 * widget. The risk score's own sub-score/vendor-table breakdown is the one exception: it's a
 * separate on-demand call ({@link #fetchPostureDrill} with drillId=PostureService.DRILL_RISK_SCORE),
 * not part of this response — folded into the same generic drilldown mechanism the other 5 panels
 * use (see the posture package's own CLAUDE.md), rather than the standalone flyout/action this
 * used to be.
 *
 * This class owns the reads; PostureService owns the arithmetic. Extends
 * AbstractThreatDetectionAction for its threat-backend aggregations, which are the cheap
 * server-side counts the KPIs are built from (no raw-event sweep).
 */
public class SecurityPostureAction extends AbstractThreatDetectionAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(SecurityPostureAction.class, LogDb.DASHBOARD);
    // Matches AgenticDashboardAction's own MAX_THREAT_FETCH_LIMIT — large enough to be
    // effectively "all events in the window" for the raw-event fetches below (biggest movers,
    // and the risk-score breakdown's DLP device-movements).
    private static final int MAX_THREAT_FETCH_LIMIT = 100000;
    private static final int EXTERNAL_CALL_TIMEOUT_SECONDS = 15;
    // Every fetch this action makes (bundle load, threat-backend aggregations, the search-backend
    // total) is independent of every other one — this pool runs them concurrently instead of the
    // sequential round-trips that used to make one page load take ~6s locally.
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(8);

    @Setter
    private int startTimestamp;

    @Setter
    private int endTimestamp;

    @Getter
    private BasicDBObject response = new BasicDBObject();

    // ── fetchPostureDrill's own request/response fields ──
    @Setter
    private String drillId;
    /** Slash-joined segments, e.g. "chatgpt.com" — empty/null for the drilldown's root (group)
     *  level. Mirrors the frontend's own `?drill=&path=` URL query params 1:1, so the flyout's
     *  current location is always reconstructible from the URL alone. */
    @Setter
    private String path;
    @Setter
    private int skip;
    @Setter
    private int limit;
    @Getter
    private PostureDrillResult postureDrill;

    private final InsightService insightService = new InsightService();
    private final PostureService postureService = new PostureService();

    public String fetchPostureSummary() {
        long callStart = System.currentTimeMillis();
        try {
            if (endTimestamp == 0) {
                endTimestamp = Context.now();
            }

            final int accountId = Context.accountId.get();
            final Integer userId = Context.userId.get();
            final CONTEXT_SOURCE contextSource = Context.contextSource.get();

            InsightContext ctx = new InsightContext(accountId, userId, contextSource, startTimestamp, endTimestamp);

            // The immediately preceding window of equal length — every "+3" / "+21%" on the page
            // is a comparison against this, so its length must match or the deltas are nonsense.
            //
            // An unbounded start (0 = "all time") has no comparable prior window: shifting back
            // by the window length would query a negative range, which returns nothing and makes
            // every delta read as "all of this is new". Null tells PostureService to withhold the
            // change figures instead of inventing them.
            boolean hasPriorWindow = startTimestamp > 0;
            int windowLength = endTimestamp - startTimestamp;
            int priorStart = hasPriorWindow ? Math.max(0, startTimestamp - windowLength) : 0;

            // Trend/sparkline panels ("Attack attempts", the Critical alerts / Sensitive data
            // incidents KPI sparklines) bucket the page's own SELECTED range, same as the KPI
            // values themselves — not a fixed rolling window. An unbounded "all time" start (0)
            // has no natural lower edge to bucket against, so it falls back to a fixed
            // TREND_BUCKET_COUNT-week lookback, same convention priorStart above already uses for
            // an unbounded range.
            int trendEndTs = endTimestamp;
            int trendStartTs = startTimestamp > 0 ? startTimestamp
                    : trendEndTs - (PostureService.TREND_BUCKET_COUNT * 7 * 86400);
            List<Integer> trendBoundaries = PostureService.trendBucketBoundaries(
                    trendStartTs, trendEndTs, PostureService.TREND_BUCKET_COUNT);

            // "Biggest movers" deliberately does NOT follow the selected range (see
            // PostureService#buildSummary's own comment on BIGGEST_MOVERS_WINDOW_DAYS) — a fixed
            // 30-day lookback regardless of filter. When the selected range is narrower than that
            // (e.g. "last 1 hour"), trendStartTs alone wouldn't reach back far enough to cover it,
            // so the raw-event fetch below spans whichever of the two windows is wider.
            int biggestMoversStartTs = trendEndTs - (PostureService.BIGGEST_MOVERS_WINDOW_DAYS * 86400);
            int rawEventFetchStartTs = Math.min(trendStartTs, biggestMoversStartTs);

            // Shared with the Insights feature rather than loaded again: the same page renders
            // "Act now" from insights, so one bundle serves both.
            Future<InsightDataBundle> bundleFuture = EXECUTOR.submit(withContext(accountId, userId, contextSource,
                    () -> insightService.getOrLoadBundle(ctx)));
            Future<List<HostSeverityCount>> priorHostSeverityFuture = hasPriorWindow
                    ? EXECUTOR.submit(withContext(accountId, userId, contextSource,
                            () -> fetchHostSeverityCounts(priorStart, startTimestamp)))
                    : null;
            Future<List<ThreatCategoryCount>> priorSubCategoryFuture = hasPriorWindow
                    ? EXECUTOR.submit(withContext(accountId, userId, contextSource,
                            () -> fetchSubcategoryWiseCounts(priorStart, startTimestamp, null, null)))
                    : null;
            Future<Long> totalInspectedActionsFuture = EXECUTOR.submit(withContext(accountId, userId, contextSource,
                    () -> fetchTotalInspectedActions(accountId, startTimestamp, endTimestamp)));
            Future<List<Integer>> weeklyAttackCountsFuture = EXECUTOR.submit(withContext(accountId, userId, contextSource,
                    () -> fetchViolationsMonthlyTotals(trendStartTs, trendEndTs, trendBoundaries, null)));
            // Raw events over [rawEventFetchStartTs, trendEndTs] — the wider of the trend window
            // and biggestMoversStartTs (see above), so one fetch serves both "Biggest movers"
            // (which filters back down to its own narrower window itself — see biggestMovers) and
            // the Critical alerts / Sensitive data incidents KPI sparklines (which need per-event
            // severity/category to bucket by the selected range; the bucketed aggregation
            // fetchViolationsMonthlyTotals uses has no severity/category filter).
            Future<List<DashboardMaliciousEvent>> trendWindowEventsFuture = EXECUTOR.submit(withContext(accountId, userId, contextSource,
                    () -> fetchAllMaliciousEvents(rawEventFetchStartTs, trendEndTs, MAX_THREAT_FETCH_LIMIT, null, null, true)));

            // Each .get() below only blocks on ITS OWN future (all were submitted above and are
            // already running), so its timing is that call's real duration, not a sum of the
            // ones before it — this is what tells us which specific fetch to fix with a
            // projection/limit, rather than just knowing the whole request is slow.
            InsightDataBundle bundle = timedGet("bundleFuture (InsightDataLoader.load)", bundleFuture);
            List<HostSeverityCount> priorHostSeverity = hasPriorWindow
                    ? timedGet("priorHostSeverityFuture", priorHostSeverityFuture) : null;
            List<ThreatCategoryCount> priorSubCategory = hasPriorWindow
                    ? timedGet("priorSubCategoryFuture", priorSubCategoryFuture) : null;
            Long totalInspectedActions = timedGet("totalInspectedActionsFuture (SearchClient)", totalInspectedActionsFuture);
            List<Integer> weeklyAttackCounts = timedGet("weeklyAttackCountsFuture", weeklyAttackCountsFuture);
            List<DashboardMaliciousEvent> trendWindowEvents =
                    timedGet("trendWindowEventsFuture (limit " + MAX_THREAT_FETCH_LIMIT + ")", trendWindowEventsFuture);

            List<ApiCollection> endpointCollections = endpointCollectionsOf(bundle);

            response = postureService.buildSummary(bundle, priorHostSeverity, priorSubCategory,
                    endpointCollections, totalInspectedActions,
                    weeklyAttackCounts, trendWindowEvents);

            // "Act now" — reuses the Insights feature wholesale rather than a parallel action
            // list: same bundle (already cached above under this exact ctx), same worst-first/
            // disabled-last ordering InsightService already computes. Top 3 non-disabled per
            // group, matching the design's "top 3 discovery + top 3 guardrail" panel.
            long actNowStart = System.currentTimeMillis();
            BasicDBObject actNow = new BasicDBObject();
            actNow.put("discovery", topEnabledInsights(insightService.listInsights(ctx, InsightId.Group.ATLAS_DISCOVERY), 3));
            actNow.put("guardrail", topEnabledInsights(insightService.listInsights(ctx, InsightId.Group.GUARDRAIL_VIOLATIONS), 3));
            response.put("actNow", actNow);
            loggerMaker.infoAndAddToDb("SecurityPostureAction: actNow (2x listInsights, off the cached bundle) took "
                    + (System.currentTimeMillis() - actNowStart) + "ms");

            loggerMaker.infoAndAddToDb("SecurityPostureAction: fetchPostureSummary total "
                    + (System.currentTimeMillis() - callStart) + "ms");
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching posture summary: " + e.getMessage());
            addActionError("Error fetching posture summary: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    /**
     * One panel's paginated drilldown flyout — see PostureService#fetchDrill's own javadoc for the
     * drillId/path/level contract. A second on-demand call: reuses the same 60s-cached bundle
     * fetchPostureSummary already populated for this ctx, fired only once someone actually opens a
     * flyout.
     *
     * DRILL_RISK_SCORE is the one exception to "reuses trendWindowEvents": it's the former
     * standalone "Risk score breakdown" flyout/action (fetchRiskScoreBreakdown), folded into this
     * same mechanism so it gets a real breadcrumb/URL/AI-summary like the other 5 panels. Its root
     * level (path="") needs the CURRENT window's full raw-event list (not the trend-bucketed one)
     * plus a real prior-window comparison for "what moved the score" — the same fetches that
     * standalone action used to make — so it gets its own branch below rather than sharing
     * trendWindowEvents with the other 5 drillIds.
     */
    public String fetchPostureDrill() {
        long callStart = System.currentTimeMillis();
        try {
            if (endTimestamp == 0) {
                endTimestamp = Context.now();
            }

            final int accountId = Context.accountId.get();
            final Integer userId = Context.userId.get();
            final CONTEXT_SOURCE contextSource = Context.contextSource.get();

            InsightContext ctx = new InsightContext(accountId, userId, contextSource, startTimestamp, endTimestamp);

            if (PostureService.DRILL_RISK_SCORE.equals(drillId)) {
                // Every sub-score level EXCEPT root and "dlpIncidents" (shadowAiExposure, vendorRisk,
                // complianceGaps, threatActivity — see PostureService#fetchRiskScoreDrill's own
                // dispatch) reads only `bundle`; none of them ever touch allThreats/priorAllThreats/
                // priorHostSeverity, so this skips fetchAllMaliciousEvents entirely for those 4.
                String[] pathSegments = (path == null || path.isEmpty()) ? new String[0] : path.split("/");
                String firstSegment = pathSegments.length > 0 ? pathSegments[0] : "";
                boolean isRiskScoreRoot = firstSegment.isEmpty();
                boolean needsAllThreats = isRiskScoreRoot || "dlpIncidents".equals(firstSegment);
                // The 3rd level (one entity — a tool/device/vendor) for shadowAiExposure/vendorRisk/
                // threatActivity needs the full, UNFILTERED window event list (dlpIncidents' own
                // entity level reuses `allThreats` above instead — already PII-filtered, no 2nd
                // fetch needed; complianceGaps' own entity level needs no events at all).
                boolean isEntityLevel = pathSegments.length >= 2;
                boolean needsWindowEvents = isEntityLevel && ("shadowAiExposure".equals(firstSegment)
                        || "vendorRisk".equals(firstSegment) || "threatActivity".equals(firstSegment));
                // Only the root's own top-2 "what's driving this" hints and "what moved the score"
                // table need a prior-window comparison — dlpIncidentsDrill's own full device table
                // is a current-period snapshot, no diff.
                boolean hasPriorWindow = isRiskScoreRoot && startTimestamp > 0;
                int windowLength = endTimestamp - startTimestamp;
                int priorStart = hasPriorWindow ? Math.max(0, startTimestamp - windowLength) : 0;

                // bundle must resolve before the allThreats-family fetches below can be submitted —
                // narrowing them to PII policies needs bundle.policies first, so this one call can no
                // longer run in parallel with them (a bundle load is normally near-free: 60s-cached,
                // ~0ms on a warm hit, per this action's own timedGet logs).
                InsightDataBundle bundle = timedGet("fetchPostureDrill(riskScore): bundleFuture",
                        EXECUTOR.submit(withContext(accountId, userId, contextSource, () -> insightService.getOrLoadBundle(ctx))));

                // Server-side filter, not a client-side one: fetchAllMaliciousEvents is the heaviest
                // call this page makes (unbounded up to MAX_THREAT_FETCH_LIMIT), and every consumer of
                // its result here (RiskScoreCalculator's DLP hint + this drill's own dlpIncidents
                // table) only cares about PII-detecting-policy events in the first place. `latestAttack`
                // is the exact filter ViolationsPage.jsx's own policy-name filter chip already sends —
                // MaliciousEventService matches it against the stored filterId, which is already the
                // firing policy's name for these events (see ViolationsPage.jsx's own `event.filterId`
                // policyName fallback) — no new backend field, no redeploy, this filter already exists.
                List<String> piiPolicyNames = needsAllThreats ? PostureService.piiPolicyNames(bundle.policies) : Collections.emptyList();
                boolean shouldFetchAllThreats = needsAllThreats && !piiPolicyNames.isEmpty();
                Map<String, Object> piiFilter = shouldFetchAllThreats
                        ? Collections.<String, Object>singletonMap("latestAttack", piiPolicyNames) : null;

                // One combined fetch spanning both windows (current + prior, when both are needed),
                // same "one raw-event fetch, shared" principle fetchPostureSummary's own
                // rawEventFetchStartTs already uses elsewhere in this class — two separate calls to
                // the identical endpoint/filter/limit for two adjacent, non-overlapping windows was a
                // wasted round trip; splitting one wider fetch by timestamp costs nothing extra, since
                // both consumers already do their own in-memory pass over the list either way. Trades
                // away one thing: MAX_THREAT_FETCH_LIMIT is now shared across both windows instead of
                // each getting its own full budget — a live concern only if PII-matching events in the
                // combined span approach 100k, far less likely now that this fetch is filtered
                // server-side to PII policies only (previously an unfiltered, genuinely "all events"
                // budget).
                int combinedFetchStart = hasPriorWindow ? priorStart : startTimestamp;
                Future<List<DashboardMaliciousEvent>> combinedThreatsFuture = shouldFetchAllThreats
                        ? EXECUTOR.submit(withContext(accountId, userId, contextSource,
                                () -> fetchAllMaliciousEvents(combinedFetchStart, endTimestamp, MAX_THREAT_FETCH_LIMIT, piiFilter, null, true)))
                        : null;
                Future<List<HostSeverityCount>> priorHostSeverityFuture = hasPriorWindow
                        ? EXECUTOR.submit(withContext(accountId, userId, contextSource,
                                () -> fetchHostSeverityCounts(priorStart, startTimestamp)))
                        : null;
                // Same trend-window convention the non-risk-score drillIds' own
                // trendWindowEventsFuture below uses. shadowAiExposure's own entity level scopes
                // this server-side to just that tool's own collections (apiCollectionId $in) —
                // safe ONLY for this sub-score, see RiskScoreProfileDrillService#collectionIdsForTool's
                // own javadoc for why vendorRisk/threatActivity deliberately stay unfiltered instead
                // (scoping either of those by known-collection-id would silently under-count real
                // activity a live collection no longer exists for). vendorRisk/threatActivity keep
                // the exact same params the non-risk-score branch's own fetch below uses, so THEIR
                // call can still land in AbstractThreatDetectionAction's maliciousEventsCache if
                // something else already warmed it for this window.
                int entityTrendEndTs = endTimestamp;
                int entityTrendStartTs = startTimestamp > 0 ? startTimestamp
                        : entityTrendEndTs - (PostureService.TREND_BUCKET_COUNT * 7 * 86400);
                boolean isShadowAiEntity = needsWindowEvents && "shadowAiExposure".equals(firstSegment);
                List<Integer> shadowAiToolCollectionIds = isShadowAiEntity
                        ? PostureService.shadowAiToolCollectionIds(bundle, pathSegments[1])
                        : null;
                // Empty means this tool genuinely has zero live collections right now — nothing to
                // fetch. Passing an empty apiCollectionId list instead of skipping the fetch would
                // be silently ignored server-side (MaliciousEventService only applies the filter
                // when the list is non-empty), turning "nothing to show" into an accidental
                // unfiltered fetch — the exact cost this scoping exists to avoid.
                boolean shadowAiEntityHasNoCollections = isShadowAiEntity && shadowAiToolCollectionIds.isEmpty();
                Map<String, Object> windowEventsFilter = (isShadowAiEntity && !shadowAiToolCollectionIds.isEmpty())
                        ? Collections.<String, Object>singletonMap("apiCollectionId", shadowAiToolCollectionIds) : null;
                Future<List<DashboardMaliciousEvent>> windowEventsFuture = (needsWindowEvents && !shadowAiEntityHasNoCollections)
                        ? EXECUTOR.submit(withContext(accountId, userId, contextSource,
                                () -> fetchAllMaliciousEvents(entityTrendStartTs, entityTrendEndTs, MAX_THREAT_FETCH_LIMIT, windowEventsFilter, null, true)))
                        : null;

                List<DashboardMaliciousEvent> allThreats = new ArrayList<>();
                List<DashboardMaliciousEvent> priorAllThreats = hasPriorWindow ? new ArrayList<>() : null;
                if (shouldFetchAllThreats) {
                    List<DashboardMaliciousEvent> combined = timedGet("fetchPostureDrill(riskScore): combinedThreatsFuture (filtered to "
                            + piiPolicyNames.size() + " PII polic" + (piiPolicyNames.size() == 1 ? "y" : "ies") + ")", combinedThreatsFuture);
                    for (DashboardMaliciousEvent e : combined) {
                        if (e == null) continue;
                        if (e.getTimestamp() >= startTimestamp) {
                            allThreats.add(e);
                        } else if (priorAllThreats != null) {
                            priorAllThreats.add(e);
                        }
                    }
                }
                List<HostSeverityCount> priorHostSeverity = hasPriorWindow
                        ? timedGet("fetchPostureDrill(riskScore): priorHostSeverityFuture", priorHostSeverityFuture) : null;
                List<DashboardMaliciousEvent> windowEvents;
                if (!needsWindowEvents) {
                    windowEvents = null;
                } else if (shadowAiEntityHasNoCollections) {
                    windowEvents = new ArrayList<>(); // nothing to fetch — see the comment above
                } else {
                    windowEvents = timedGet("fetchPostureDrill(riskScore): windowEventsFuture", windowEventsFuture);
                }

                List<ApiCollection> endpointCollections = endpointCollectionsOf(bundle);

                postureDrill = postureService.fetchRiskScoreDrill(bundle, endpointCollections, allThreats,
                        priorAllThreats, priorHostSeverity, windowEvents, path, skip, limit);
            } else {
                // Same trend-window convention buildSummary/shadowAiTrend use — the page's own
                // selected range, falling back to a fixed lookback only for an unbounded "all time"
                // start. See PostureService.TREND_BUCKET_COUNT's own javadoc.
                int trendEndTs = endTimestamp;
                int trendStartTs = startTimestamp > 0 ? startTimestamp
                        : trendEndTs - (PostureService.TREND_BUCKET_COUNT * 7 * 86400);

                Future<InsightDataBundle> bundleFuture = EXECUTOR.submit(withContext(accountId, userId, contextSource,
                        () -> insightService.getOrLoadBundle(ctx)));
                Future<List<DashboardMaliciousEvent>> trendWindowEventsFuture = EXECUTOR.submit(withContext(accountId, userId, contextSource,
                        () -> fetchAllMaliciousEvents(trendStartTs, trendEndTs, MAX_THREAT_FETCH_LIMIT, null, null, true)));

                InsightDataBundle bundle = timedGet("fetchPostureDrill: bundleFuture", bundleFuture);
                List<DashboardMaliciousEvent> trendWindowEvents =
                        timedGet("fetchPostureDrill: trendWindowEventsFuture", trendWindowEventsFuture);

                List<ApiCollection> endpointCollections = endpointCollectionsOf(bundle);

                postureDrill = postureService.fetchDrill(bundle, endpointCollections, trendWindowEvents,
                        trendStartTs, trendEndTs, drillId, path, skip, limit);

                // Ground the AI summary in real traffic, not just aggregate counts — see
                // attachEvidenceSamples' own javadoc for why only these two drills get this.
                if (PostureService.DRILL_CRITICAL_ALERTS.equals(drillId) || PostureService.DRILL_SENSITIVE_DATA.equals(drillId)) {
                    attachEvidenceSamples(postureDrill, accountId, contextSource != null ? contextSource.name() : "");
                }
            }

            // AI summary for this exact level — cache hit attaches it synchronously; a miss marks
            // it PENDING and generates in the background, so a slow LLM call never adds latency
            // here (see PostureDrillNarrativeService's own javadoc).
            PostureDrillNarrativeService.attachNarrative(postureDrill, ctx, drillId, path);

            loggerMaker.infoAndAddToDb("SecurityPostureAction: fetchPostureDrill (" + drillId + ") total "
                    + (System.currentTimeMillis() - callStart) + "ms");
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching posture drill: " + e.getMessage());
            addActionError("Error fetching posture drill: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    private Long fetchTotalInspectedActions(int accountId, int startTimestamp, int endTimestamp) {
        // The funnel's inspected-actions denominator — total gateway-inspected traffic
        // (isAtlasTraffic=true), not just the subset that matched a policy. searchPrompts is the
        // only existing SearchClient call that returns this as a real aggregation total; it
        // happens to also compute top-apps/token sums we don't need here, but there is no
        // lighter-weight count-only method on SearchClient today.
        try {
            SearchClient searchClient = SearchClientFactory.instance();
            if (!searchClient.isConfigured()) return null;
            long startMs = (long) Math.max(startTimestamp, 0) * 1000L;
            long endMs = (long) endTimestamp * 1000L;
            SearchClient.SearchResult result = searchClient.searchPrompts(accountId, startMs, endMs, 0, 1,
                    "", false, "", new HashMap<>(), true, "", false);
            return result.total;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching inspected-actions total: " + e.getMessage());
            return null;
        }
    }

    /** bundle.collections filtered to active endpoint-shield collections — the same filter was
     *  copy-pasted at all 3 call sites in this class (fetchPostureSummary, and both branches of
     *  fetchPostureDrill); pulled into one method so the predicate can't drift between them.
     *  bundle.collections, not bundle.activeCollections: the latter is loaded via a narrow
     *  projection (id/hostName/startTs only, for PolicyHygieneProvider's cheap uncovered-asset
     *  check) that omits tagsList, so isEndpointCollection() would always read false against it.
     *  bundle.collections already carries tagsList. Cheap in-memory filter, not worth its own
     *  future. */
    private static List<ApiCollection> endpointCollectionsOf(InsightDataBundle bundle) {
        return bundle.collections.stream()
                .filter(c -> c != null && !c.isDeactivated() && c.isEndpointCollection())
                .collect(Collectors.toList());
    }

    /** Top N rows enriched with their own real intercepted request/response sample; the rest of
     *  the drill's own AI-summary evidence (metrics, gaps) stays exactly as PostureDrillNarrativeService
     *  already builds it — this only adds one more field to a handful of rows. */
    private static final int EVIDENCE_ENRICHMENT_ROW_CAP = 5;
    /** Long enough to ground a claim, short enough not to balloon the narrative prompt with a raw
     *  payload dump — this is a sample for the LLM to cite from, not a display value. */
    private static final int EVIDENCE_SAMPLE_MAX_CHARS = 600;

    /** Critical alerts and Sensitive data incidents both build rows that already carry a hidden
     *  "refId" (and, where the row is itself an aggregate — Sensitive data incidents' own per-user
     *  rows — a "policy" naming that user's own most recent hit) rather than a displayed column —
     *  see PostureService#criticalAlertsDrill/#sensitiveDataDrill's own javadoc. This attaches
     *  each of the first {@link #EVIDENCE_ENRICHMENT_ROW_CAP} rows' own real intercepted request/
     *  response sample (the same "latestApiOrig" {@code ThreatDetectionBackendClient
     *  #listGuardrailViolationPayloads} already serves ComplianceClauseScanService, keyed by
     *  refId — see that client method's own javadoc) as a new "evidenceSample" field on that same
     *  row map. PostureDrillNarrativeService's own buildNarrativeInput already serializes a
     *  drill's rows verbatim into the LLM's EVIDENCE block, so this one attach is the ONLY change
     *  needed to ground that narrative in real traffic instead of just the aggregate counts
     *  already in FACTS — no changes to the narrative service itself. Root-cause "the new function
     *  ... used for both 1 and 3" the request asked for: this method, called identically for both
     *  drills. A failure here (backend down, no row had a refId) silently no-ops — the narrative
     *  still gets everything else. */
    private void attachEvidenceSamples(PostureDrillResult result, int accountId, String contextSourceValue) {
        if (result == null || result.getRows() == null || result.getRows().isEmpty()) return;
        List<Map<String, Object>> topRows = result.getRows().size() > EVIDENCE_ENRICHMENT_ROW_CAP
                ? result.getRows().subList(0, EVIDENCE_ENRICHMENT_ROW_CAP) : result.getRows();

        Set<String> filterIds = new HashSet<>();
        Set<String> refIds = new HashSet<>();
        for (Map<String, Object> row : topRows) {
            Object refId = row.get("refId");
            Object policy = row.get("policy");
            if (refId == null || policy == null) continue;
            refIds.add(String.valueOf(refId));
            filterIds.add(String.valueOf(policy));
        }
        if (refIds.isEmpty()) return;

        try {
            ListGuardrailViolationPayloadsResponse resp = ThreatDetectionBackendClient.listGuardrailViolationPayloads(
                    accountId, startTimestamp, endTimestamp, new ArrayList<>(filterIds), null, 50, true, contextSourceValue);
            if (resp == null) return;

            Map<String, String> origByRefId = new HashMap<>();
            for (ViolationPayload vp : resp.getPayloadsList()) {
                if (refIds.contains(vp.getRefId()) && vp.getOrig() != null && !vp.getOrig().isEmpty()) {
                    origByRefId.put(vp.getRefId(), vp.getOrig());
                }
            }
            for (Map<String, Object> row : topRows) {
                Object refId = row.get("refId");
                if (refId == null) continue;
                String orig = origByRefId.get(String.valueOf(refId));
                if (orig == null) continue;
                row.put("evidenceSample", orig.length() > EVIDENCE_SAMPLE_MAX_CHARS
                        ? orig.substring(0, EVIDENCE_SAMPLE_MAX_CHARS) + "…" : orig);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching evidence samples for \"" + result.getTitle() + "\": " + e.getMessage());
        }
    }

    /** Blocks on one future and logs how long that specific wait took — see the call site's own
     *  note on why per-future timing (not just a total) is the point. */
    private static <T> T timedGet(String label, Future<T> future)
            throws java.util.concurrent.ExecutionException, InterruptedException, java.util.concurrent.TimeoutException {
        long t0 = System.currentTimeMillis();
        T result = future.get(EXTERNAL_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        loggerMaker.infoAndAddToDb("SecurityPostureAction: " + label + " took " + (System.currentTimeMillis() - t0) + "ms");
        return result;
    }

    /** Threat-backend/search-backend calls run in worker threads — Context ThreadLocals must be
     *  captured by the caller and re-set inside each task, or the worker queries the wrong account
     *  (same convention and same reasoning as InsightDataLoader#withContext). */
    private static <T> Callable<T> withContext(int accountId, Integer userId, CONTEXT_SOURCE contextSource, Callable<T> body) {
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

    /** InsightService already sorts worst-first with disabled ("Coming soon") cards last — this
     *  just drops the disabled ones and caps the count, rather than re-sorting. */
    private static List<InsightResult> topEnabledInsights(List<InsightResult> all, int limit) {
        List<InsightResult> enabled = new ArrayList<>();
        for (InsightResult r : all) {
            if (r != null && !r.isDisabled()) enabled.add(r);
        }
        return enabled.subList(0, Math.min(limit, enabled.size()));
    }
}

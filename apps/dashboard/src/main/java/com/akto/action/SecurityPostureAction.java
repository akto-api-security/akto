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
import com.akto.service.posture.PostureService;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.akto.utils.search.SearchClient;
import com.akto.utils.search.SearchClientFactory;
import com.mongodb.BasicDBObject;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
 * separate on-demand call ({@link #fetchRiskScoreBreakdown}), not part of this response — see its
 * own javadoc for why.
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

    @Getter
    private BasicDBObject riskScoreBreakdown = new BasicDBObject();

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

            // bundle.collections, not bundle.activeCollections: the latter is loaded via a
            // narrow projection (id/hostName/startTs only, for PolicyHygieneProvider's cheap
            // uncovered-asset check) that omits tagsList, so isEndpointCollection() would always
            // read false against it. bundle.collections already carries tagsList. Cheap in-memory
            // filter, not worth its own future.
            List<ApiCollection> endpointCollections = bundle.collections.stream()
                    .filter(c -> c != null && !c.isDeactivated() && c.isEndpointCollection())
                    .collect(Collectors.toList());

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
     * The risk score flyout's own detail — the five sub-score rows and the vendor-risk table.
     * Split out of {@link #fetchPostureSummary} rather than embedded in it: every page load needs
     * the composite KPI (value + delta), but the row-by-row breakdown is only needed once someone
     * actually opens the flyout, so it's fetched then instead of on every load. bundle is the same
     * 60s-cached bundle fetchPostureSummary already populated for this ctx, so this call is just
     * the raw-event fetches (endpointCollections/allThreats/priorAllThreats) unique to the
     * breakdown's per-device DLP movements.
     */
    public String fetchRiskScoreBreakdown() {
        long callStart = System.currentTimeMillis();
        try {
            if (endTimestamp == 0) {
                endTimestamp = Context.now();
            }

            final int accountId = Context.accountId.get();
            final Integer userId = Context.userId.get();
            final CONTEXT_SOURCE contextSource = Context.contextSource.get();

            InsightContext ctx = new InsightContext(accountId, userId, contextSource, startTimestamp, endTimestamp);

            // Same "immediately preceding equal-length window" convention as fetchPostureSummary —
            // null (not fetched) for an unbounded "all time" range, so "what moved the score" can
            // tell "nothing changed" apart from "nothing to compare against".
            boolean hasPriorWindow = startTimestamp > 0;
            int windowLength = endTimestamp - startTimestamp;
            int priorStart = hasPriorWindow ? Math.max(0, startTimestamp - windowLength) : 0;

            Future<InsightDataBundle> bundleFuture = EXECUTOR.submit(withContext(accountId, userId, contextSource,
                    () -> insightService.getOrLoadBundle(ctx)));
            Future<List<DashboardMaliciousEvent>> allThreatsFuture = EXECUTOR.submit(withContext(accountId, userId, contextSource,
                    () -> fetchAllMaliciousEvents(startTimestamp, endTimestamp, MAX_THREAT_FETCH_LIMIT, null, null, true)));
            Future<List<HostSeverityCount>> priorHostSeverityFuture = hasPriorWindow
                    ? EXECUTOR.submit(withContext(accountId, userId, contextSource,
                            () -> fetchHostSeverityCounts(priorStart, startTimestamp)))
                    : null;
            // For the DLP device-movements breakdown's own delta (piiEventCountByDevice needs a
            // per-event host, which bundle.subCategoryCounts doesn't carry).
            Future<List<DashboardMaliciousEvent>> priorAllThreatsFuture = hasPriorWindow
                    ? EXECUTOR.submit(withContext(accountId, userId, contextSource,
                            () -> fetchAllMaliciousEvents(priorStart, startTimestamp, MAX_THREAT_FETCH_LIMIT, null, null, true)))
                    : null;

            InsightDataBundle bundle = timedGet("fetchRiskScoreBreakdown: bundleFuture", bundleFuture);
            List<DashboardMaliciousEvent> allThreats =
                    timedGet("fetchRiskScoreBreakdown: allThreatsFuture (limit " + MAX_THREAT_FETCH_LIMIT + ")", allThreatsFuture);
            List<HostSeverityCount> priorHostSeverity = hasPriorWindow
                    ? timedGet("fetchRiskScoreBreakdown: priorHostSeverityFuture", priorHostSeverityFuture) : null;
            List<DashboardMaliciousEvent> priorAllThreats = hasPriorWindow
                    ? timedGet("fetchRiskScoreBreakdown: priorAllThreatsFuture (limit " + MAX_THREAT_FETCH_LIMIT + ")",
                            priorAllThreatsFuture)
                    : null;

            List<ApiCollection> endpointCollections = bundle.collections.stream()
                    .filter(c -> c != null && !c.isDeactivated() && c.isEndpointCollection())
                    .collect(Collectors.toList());

            riskScoreBreakdown = postureService.buildRiskScoreBreakdown(bundle, endpointCollections, allThreats,
                    priorAllThreats, priorHostSeverity);
            loggerMaker.infoAndAddToDb("SecurityPostureAction: fetchRiskScoreBreakdown total "
                    + (System.currentTimeMillis() - callStart) + "ms");
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching risk score breakdown: " + e.getMessage());
            addActionError("Error fetching risk score breakdown: " + e.getMessage());
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

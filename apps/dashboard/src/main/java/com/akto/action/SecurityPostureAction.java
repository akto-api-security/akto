package com.akto.action;

import com.akto.action.threat_detection.AbstractThreatDetectionAction;
import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.action.threat_detection.HostSeverityCount;
import com.akto.action.threat_detection.ThreatCategoryCount;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.threat_detection.ThreatComplianceInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.service.insights.InsightContext;
import com.akto.service.insights.InsightDataBundle;
import com.akto.service.insights.InsightId;
import com.akto.service.insights.InsightResult;
import com.akto.service.insights.InsightService;
import com.akto.service.posture.PostureService;
import com.akto.util.GuardrailMetricsProcessor;
import com.akto.utils.search.SearchClient;
import com.akto.utils.search.SearchClientFactory;
import com.mongodb.BasicDBObject;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The AI Security Posture page. One consolidated call returns the whole page — the same shape
 * AgenticDashboardAction uses, and the reason the page is one round trip instead of one per
 * widget.
 *
 * This class owns the reads; PostureService owns the arithmetic. Extends
 * AbstractThreatDetectionAction for its threat-backend aggregations, which are the cheap
 * server-side counts the KPIs are built from (no raw-event sweep).
 */
public class SecurityPostureAction extends AbstractThreatDetectionAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(SecurityPostureAction.class, LogDb.DASHBOARD);
    // Matches AgenticDashboardAction's own MAX_THREAT_FETCH_LIMIT — large enough to be
    // effectively "all events in the window" for the compliance-gaps sub-score.
    private static final int MAX_THREAT_FETCH_LIMIT = 100000;

    @Setter
    private int startTimestamp;

    @Setter
    private int endTimestamp;

    @Getter
    private BasicDBObject response = new BasicDBObject();

    private final InsightService insightService = new InsightService();
    private final PostureService postureService = new PostureService();

    public String fetchPostureSummary() {
        try {
            if (endTimestamp == 0) {
                endTimestamp = Context.now();
            }

            InsightContext ctx = new InsightContext(Context.accountId.get(), Context.userId.get(),
                    Context.contextSource.get(), startTimestamp, endTimestamp);

            // Shared with the Insights feature rather than loaded again: the same page renders
            // "Act now" from insights, so one bundle serves both.
            InsightDataBundle bundle = insightService.getOrLoadBundle(ctx);

            // The immediately preceding window of equal length — every "+3" / "+21%" on the page
            // is a comparison against this, so its length must match or the deltas are nonsense.
            //
            // An unbounded start (0 = "all time") has no comparable prior window: shifting back
            // by the window length would query a negative range, which returns nothing and makes
            // every delta read as "all of this is new". Null tells PostureService to withhold the
            // change figures instead of inventing them.
            List<HostSeverityCount> priorHostSeverity = null;
            List<ThreatCategoryCount> priorSubCategory = null;
            if (startTimestamp > 0) {
                int windowLength = endTimestamp - startTimestamp;
                int priorStart = Math.max(0, startTimestamp - windowLength);
                priorHostSeverity = fetchHostSeverityCounts(priorStart, startTimestamp);
                priorSubCategory = fetchSubcategoryWiseCounts(priorStart, startTimestamp, null, null);
            }

            // Compliance gaps needs the raw malicious events (for their filterId), not the
            // pre-aggregated subCategoryCounts the KPIs above use — same fetch
            // AgenticDashboardAction#fetchGuardrailData already makes for the same purpose.
            List<DashboardMaliciousEvent> allThreats = fetchAllMaliciousEvents(
                    startTimestamp, endTimestamp, MAX_THREAT_FETCH_LIMIT, null);
            Map<String, ThreatComplianceInfo> threatComplianceMap = GuardrailMetricsProcessor.fetchThreatComplianceMap();

            // bundle.collections, not bundle.activeCollections: the latter is loaded via a
            // narrow projection (id/hostName/startTs only, for PolicyHygieneProvider's cheap
            // uncovered-asset check) that omits tagsList, so isEndpointCollection() would always
            // read false against it. bundle.collections already carries tagsList.
            List<ApiCollection> endpointCollections = bundle.collections.stream()
                    .filter(c -> c != null && !c.isDeactivated() && c.isEndpointCollection())
                    .collect(Collectors.toList());

            // The funnel's inspected-actions denominator — total gateway-inspected traffic
            // (isAtlasTraffic=true), not just the subset that matched a policy. fetchArgusStats is
            // the only existing SearchClient call that returns this as a real aggregation total;
            // it happens to also compute top-apps/token sums we don't need here, but there is no
            // lighter-weight count-only method on SearchClient today.
            Long totalInspectedActions = null;
            try {
                SearchClient searchClient = SearchClientFactory.instance();
                if (searchClient.isConfigured()) {
                    long startMs = (long) Math.max(startTimestamp, 0) * 1000L;
                    long endMs = (long) endTimestamp * 1000L;
                    SearchClient.SearchResult result = searchClient.searchPrompts(Context.accountId.get(), startMs, endMs, 0, 1,"", false,"", new HashMap<>(),true, "", false);
                    totalInspectedActions = result.total;
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error fetching inspected-actions total: " + e.getMessage());
            }

            // "Attack attempts" trend — always a fixed 8-week window ending now, independent of
            // the page's selected date range (same convention shadowAiTrend already uses), via
            // the same cheap $bucket aggregation AgenticObserveAction's own violations sparkline
            // uses (get_host_severity_counts with arbitrary ascending boundaries, despite the
            // "monthly" name).
            int attackTrendEndTs = endTimestamp;
            List<Integer> attackTrendBoundaries = PostureService.attackTrendWeekBoundaries(attackTrendEndTs);
            int attackTrendStartTs = attackTrendBoundaries.get(0) - (attackTrendBoundaries.get(1) - attackTrendBoundaries.get(0));
            List<Integer> weeklyAttackCounts = fetchViolationsMonthlyTotals(
                    attackTrendStartTs, attackTrendEndTs, attackTrendBoundaries, null);

            response = postureService.buildSummary(bundle, priorHostSeverity, priorSubCategory,
                    endpointCollections, allThreats, threatComplianceMap, totalInspectedActions,
                    weeklyAttackCounts);

            // "Act now" — reuses the Insights feature wholesale rather than a parallel action
            // list: same bundle (already cached above under this exact ctx), same worst-first/
            // disabled-last ordering InsightService already computes. Top 3 non-disabled per
            // group, matching the design's "top 3 discovery + top 3 guardrail" panel.
            BasicDBObject actNow = new BasicDBObject();
            actNow.put("discovery", topEnabledInsights(insightService.listInsights(ctx, InsightId.Group.ATLAS_DISCOVERY), 3));
            actNow.put("guardrail", topEnabledInsights(insightService.listInsights(ctx, InsightId.Group.GUARDRAIL_VIOLATIONS), 3));
            response.put("actNow", actNow);

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching posture summary: " + e.getMessage());
            addActionError("Error fetching posture summary: " + e.getMessage());
            return ERROR.toUpperCase();
        }
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

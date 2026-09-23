package com.akto.service.posture;

import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.action.threat_detection.HostSeverityCount;
import com.akto.action.threat_detection.ThreatCategoryCount;
import com.akto.dao.threat_detection.ComplianceClauseCoverageDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.threat_detection.ComplianceClauseCoverage;
import com.akto.dto.threat_detection.ComplianceClauseCoverage.ClauseHit;
import com.akto.service.insights.InsightDataBundle;
import com.akto.service.insights.InsightResult;
import com.akto.service.insights.InsightRoutes;
import com.akto.service.insights.InsightUtil;
import com.akto.service.insights.InsightUtil.GovernanceBucket;
import com.akto.util.compliance.ComplianceSubClauseCatalog;
import com.mongodb.BasicDBObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Builds the AI Security Posture page payload. One method per panel, each writing a single key
 * into the response — the same consolidated-call shape AgenticDashboardAction already uses, so
 * the page is one round trip rather than one per widget.
 *
 * Deliberately a pure function over its inputs: every read (the shared InsightDataBundle, and
 * the prior-window threat-backend aggregations) is performed by SecurityPostureAction and passed
 * in. That keeps this class unit-testable with no Mongo and no HTTP, which matters because the
 * arithmetic here — deltas, coverage ratios, taxonomy joins — is where the bugs live, not in the
 * reads.
 *
 * KPI 1 (the risk-score composite, its five sub-scores, and the vendor table) lives in
 * {@link RiskScoreCalculator} instead of here: it is the one piece of this page big enough and
 * self-contained enough (one caller, its own nested type, its own constants) to be worth a
 * second file. The three panels below and the other three KPIs stay here because they share
 * {@link #matchedPolicyCounts}/{@link InsightUtil#governanceBucket} with each other — splitting
 * along "KPI vs. panel" would only move that coupling across a file boundary, not remove it.
 */
public class PostureService {

    /** Response keys. The frontend reads these; keep them stable. */
    public static final String KEY_KPIS              = "kpis";
    public static final String KEY_SHADOW_AI_TREND   = "shadowAiTrend";
    public static final String KEY_DATA_LEAVING      = "dataLeaving";
    public static final String KEY_ENFORCEMENT_FUNNEL = "enforcementFunnel";
    public static final String KEY_ATTACK_ATTEMPTS   = "attackAttempts";
    public static final String KEY_BIGGEST_MOVERS    = "biggestMovers";
    public static final String KEY_FRAMEWORK_READINESS = "frameworkReadiness";

    // KPI ids, also what the frontend keys its cards off.
    public static final String KPI_RISK_SCORE         = "riskScore";
    public static final String KPI_CRITICAL_ALERTS    = "criticalAlerts";
    public static final String KPI_MONITORING_COVERAGE = "monitoringCoverage";
    public static final String KPI_SENSITIVE_INCIDENTS = "sensitiveDataIncidents";

    // Enforcement funnel stages ("what happened after a policy matched"), and the top N data
    // types kept before rolling the rest into "Other" on the data-leaving donut.
    private static final int TOP_DATA_TYPES = 5;
    private static final int SHADOW_TREND_BUCKET_COUNT = 12;
    /** Also the fallback lookback for an unbounded "all time" range (start=0), which has no
     *  natural lower edge to bucket the trend/sparkline panels against — see trendBucketBoundaries'
     *  callers. Not a rolling window otherwise: every trend/sparkline panel below buckets the
     *  page's own SELECTED [startTs, endTs] range, not a fixed lookback ending "now". */
    private static final long WEEK_SECONDS = 7L * 24 * 3600;
    /** Also the number of boundaries SecurityPostureAction must request via
     *  {@link #trendBucketBoundaries}, and the point count "Attack attempts" and the Critical
     *  alerts / Sensitive data incidents KPI sparklines all share. */
    public static final int TREND_BUCKET_COUNT = 8;

    // "Biggest movers" — a fixed lookback window, deliberately NOT the page's own selected range
    // like the trend/sparkline panels above: a mover is about "did this cross a threshold
    // recently", which should read the same regardless of what range someone happens to have the
    // page filtered to.
    public static final int BIGGEST_MOVERS_WINDOW_DAYS = 30;
    private static final int BIGGEST_MOVERS_DEVICE_THRESHOLD = 20;
    private static final int BIGGEST_MOVERS_ATTACK_THRESHOLD = 1000;
    private static final int BIGGEST_MOVERS_MAX_PER_CONDITION = 3;
    private static final int BIGGEST_MOVERS_MAX_TOTAL = 5;

    /**
     * Posture history doesn't exist yet, so any figure that is a comparison against an earlier
     * snapshot (rather than against a prior window of events) has no source. Reported as a gap
     * instead of a fabricated zero — see the posture_score_history item on the build plan.
     *
     * Package-private (not private): also used by RiskScoreCalculator, which needs the same gap
     * vocabulary for its sub-scores.
     */
    static final String GAP_POSTURE_HISTORY = "POSTURE_HISTORY";
    private static final String GAP_DEVICE_IDENTITY = "DEVICE_IDENTITY";
    static final String GAP_THREAT_BACKEND  = "THREAT_BACKEND";
    static final String GAP_GUARDRAIL_POLICIES = "GUARDRAIL_POLICIES";
    /** No compliance-clause scan has been run yet, or the scan found nothing for this window —
     *  distinct from GAP_GUARDRAIL_POLICIES (no framework even mapped), see frameworkReadiness. */
    static final String GAP_COMPLIANCE_SCAN = "COMPLIANCE_SCAN";
    /** The selected range has no comparable preceding window (an unbounded "all time" start). */
    private static final String GAP_NO_PRIOR_WINDOW = "PRIOR_WINDOW";

    static final String REASON_NO_ROWS        = "NO_ROWS";
    static final String REASON_NOT_CONFIGURED = "NOT_CONFIGURED";
    static final String REASON_REQUEST_FAILED = "REQUEST_FAILED";

    private static final String NO_PRIOR_WINDOW_IMPACT =
            "This range has no comparable preceding period, so the change figure is unavailable. "
                    + "Pick a bounded range to see it.";
    static final String THREAT_BACKEND_DOWN_IMPACT =
            "The threat backend did not respond, so this count and its change figure are unavailable.";

    /**
     * The risk score flyout's own on-demand detail (five sub-score rows + vendor table + the
     * device/vendor/policy breakdowns behind each of them) — deliberately not part of
     * {@link #buildSummary}'s response. See RiskScoreCalculator#computeBreakdown for why this is
     * split into its own call. priorHostSeverity/priorAllThreats are null for an unbounded
     * "all time" range (no prior window to diff against) — same convention every other delta on
     * this page already uses.
     */
    public BasicDBObject buildRiskScoreBreakdown(InsightDataBundle bundle, List<ApiCollection> endpointCollections,
                                                  List<DashboardMaliciousEvent> allThreatsForDlp,
                                                  List<DashboardMaliciousEvent> priorAllThreatsForDlp,
                                                  List<HostSeverityCount> priorHostSeverity) {
        return RiskScoreCalculator.computeBreakdown(bundle, endpointCollections, allThreatsForDlp,
                priorAllThreatsForDlp, priorHostSeverity);
    }

    /**
     * @param bundle               shared insights bundle, holding this window's aggregations
     * @param priorHostSeverity    hostSeverityCounts for the immediately preceding equal-length window
     * @param priorSubCategory     subcategory-wise counts for that same preceding window
     * @param endpointCollections  ApiCollections fetched under CONTEXT_SOURCE.ENDPOINT — a
     *                             separate query from bundle.collections (which is scoped to
     *                             whatever context this request is running under), needed only
     *                             for the risk score's vendor-risk sub-score/table
     * @param totalInspectedActions the funnel's denominator — total gateway-inspected
     *                             (isAtlasTraffic) traffic in the window. Null when the trace
     *                             search backend isn't configured or didn't respond.
     * @param weeklyAttackCounts   {@link #TREND_BUCKET_COUNT} malicious-event counts, one per
     *                             boundary from {@link #trendBucketBoundaries} over the page's own
     *                             selected range, ascending (oldest bucket first). Null/short when
     *                             the threat backend didn't return a full set — see
     *                             attackAttemptsTrend's own gap handling.
     * @param trendWindowEvents    malicious events over the page's selected range (unfiltered by
     *                             label; unbounded "all time" falls back to a fixed lookback — see
     *                             buildSummary's own trendStartTs computation), widened to also
     *                             cover BIGGEST_MOVERS_WINDOW_DAYS when the selected range is
     *                             narrower than that, so one raw-event fetch serves both "Biggest
     *                             movers"' attack-count condition (which filters back down to its
     *                             own fixed window itself — see biggestMovers) and the Critical
     *                             alerts / Sensitive data incidents KPI sparklines, which bucket it
     *                             client-side rather than firing a separate backend call per bucket
     *                             per KPI (see bucketedSparkline's own javadoc for why).
     */
    public BasicDBObject buildSummary(InsightDataBundle bundle,
                                       List<HostSeverityCount> priorHostSeverity,
                                       List<ThreatCategoryCount> priorSubCategory,
                                       List<ApiCollection> endpointCollections,
                                       Long totalInspectedActions,
                                       List<Integer> weeklyAttackCounts,
                                       List<DashboardMaliciousEvent> trendWindowEvents) {
        BasicDBObject response = new BasicDBObject();

        // Shared boundary source for every trend/sparkline panel — the page's own SELECTED range,
        // not a fixed rolling window (see trendBucketBoundaries' own javadoc). Unbounded "all
        // time" start (0) falls back to a fixed lookback, same convention every other delta on
        // this page already uses for an unbounded range.
        int trendEndTs = bundle.ctx.getEndTs() > 0 ? bundle.ctx.getEndTs() : (int) (System.currentTimeMillis() / 1000);
        int trendStartTs = bundle.ctx.getStartTs() > 0 ? bundle.ctx.getStartTs()
                : trendEndTs - (int) (TREND_BUCKET_COUNT * WEEK_SECONDS);
        List<Integer> trendBoundaries = trendBucketBoundaries(trendStartTs, trendEndTs, TREND_BUCKET_COUNT);

        List<BasicDBObject> kpis = new ArrayList<>();
        kpis.add(RiskScoreCalculator.compute(bundle, endpointCollections, priorHostSeverity, priorSubCategory));
        kpis.add(criticalAlertsKpi(bundle, priorHostSeverity, trendWindowEvents, trendStartTs, trendBoundaries));
        kpis.add(monitoringCoverageKpi(bundle));
        kpis.add(sensitiveDataIncidentsKpi(bundle, priorSubCategory, trendWindowEvents, trendStartTs, trendBoundaries));
        response.put(KEY_KPIS, kpis);

        response.put(KEY_SHADOW_AI_TREND, shadowAiTrend(bundle));
        response.put(KEY_DATA_LEAVING, dataLeavingBreakdown(bundle));
        response.put(KEY_ENFORCEMENT_FUNNEL, enforcementFunnel(bundle, totalInspectedActions));

        response.put(KEY_ATTACK_ATTEMPTS, attackAttemptsTrend(weeklyAttackCounts, trendBoundaries));

        // Biggest movers deliberately does NOT follow the selected range — see its own comment on
        // BIGGEST_MOVERS_WINDOW_DAYS: "did this cross a threshold recently" should read the same
        // regardless of the page's date filter, unlike the trend panels above.
        int biggestMoversWindowStartTs = trendEndTs - (BIGGEST_MOVERS_WINDOW_DAYS * 86400);
        response.put(KEY_BIGGEST_MOVERS, biggestMovers(endpointCollections, bundle.collectionLastTrafficSeen,
                trendWindowEvents, biggestMoversWindowStartTs));

        response.put(KEY_FRAMEWORK_READINESS, frameworkReadiness(trendStartTs, trendEndTs));

        return response;
    }

    // ── KPI 2 · Critical alerts ──────────────────────────────────────────────────
    // hostSeverityCounts is a single cheap server-side aggregation the bundle already holds, so
    // this needs no raw-event fetch. The delta is a real prior-window comparison, hence absolute
    // ("+3") rather than a percentage — at these magnitudes a percentage swings wildly.

    private BasicDBObject criticalAlertsKpi(InsightDataBundle bundle, List<HostSeverityCount> priorHostSeverity,
                                             List<DashboardMaliciousEvent> trendWindowEvents,
                                             int trendStartTs, List<Integer> trendBoundaries) {
        long current = sumCritical(bundle.hostSeverityCounts);
        BasicDBObject kpi = kpi(KPI_CRITICAL_ALERTS, "Critical alerts", current, null, InsightRoutes.GUARDRAIL_VIOLATIONS);
        kpi.put("unit", "count");
        kpi.put("linkParams", new BasicDBObject("severity", "CRITICAL"));

        // The threat-backend helpers swallow failures into an empty list, so an empty prior
        // window is indistinguishable from a failed read by inspecting the list alone.
        // threatBackendAvailable is the one signal that does tell them apart, which is why the
        // bundle carries it — without this check a backend outage would render as "-7", a
        // dramatic improvement that never happened.
        if (!bundle.threatBackendAvailable) {
            addGap(kpi, GAP_THREAT_BACKEND, REASON_REQUEST_FAILED, THREAT_BACKEND_DOWN_IMPACT);
            kpi.put("value", null);
            return kpi;
        }

        // Bucketed over the page's own selected range — same convention attackAttemptsTrend/
        // shadowAiTrend now use for their own trends.
        kpi.put("sparkline", bucketedSparkline(trendWindowEvents, trendStartTs, trendBoundaries,
                e -> "CRITICAL".equalsIgnoreCase(e.getSeverity())));

        if (priorHostSeverity == null) {
            addGap(kpi, GAP_NO_PRIOR_WINDOW, REASON_NOT_CONFIGURED, NO_PRIOR_WINDOW_IMPACT);
            return kpi;
        }

        long prior = sumCritical(priorHostSeverity);
        kpi.put("delta", current - prior);
        kpi.put("deltaKind", "absolute");
        // More criticals is worse, so a positive delta is a bad trend.
        kpi.put("deltaTone", current > prior ? "critical" : current < prior ? "success" : "neutral");
        return kpi;
    }

    private static long sumCritical(List<HostSeverityCount> counts) {
        if (counts == null) return 0;
        long total = 0;
        for (HostSeverityCount c : counts) {
            if (c != null) total += c.getCritical();
        }
        return total;
    }

    // ── KPI 3 · Monitoring coverage ──────────────────────────────────────────────

    /**
     * Share of live devices that at least one active guardrail policy applies to.
     *
     * The device universe is module_info's live heartbeat data (the bundle's deviceIdToUsername
     * is built from ModuleInfoDao.fetchUsernameToDeviceIdsForEndpointShield), not
     * AgenticUsers.devices, which is only ever backfilled once.
     *
     * Per-policy targeting is already resolved into applyToDeviceIds by InsightDataLoader, which
     * calls AgentUsersDao.findDeviceIdsByTags for each policy. Two traps in reading it:
     *  - null means no targeting was configured, which means the policy applies to ALL devices.
     *    A non-null but EMPTY list means targeting resolved to zero devices, so it applies to
     *    none. These are not interchangeable and a falsy/empty check inverts the result.
     *  - explicitly-picked targetDeviceIds are trusted straight from a dropdown, so they can name
     *    a device that is no longer reporting. The covered set is therefore intersected with the
     *    live universe before the ratio is taken, or coverage can exceed 100%.
     */
    private BasicDBObject monitoringCoverageKpi(InsightDataBundle bundle) {
        Set<String> liveDevices = bundle.deviceIdToUsername == null
                ? new HashSet<>() : new HashSet<>(bundle.deviceIdToUsername.keySet());

        BasicDBObject kpi = kpi(KPI_MONITORING_COVERAGE, "Monitoring coverage", null, null, InsightRoutes.ENDPOINT_SHIELD);
        kpi.put("unit", "percent");

        if (liveDevices.isEmpty()) {
            addGap(kpi, GAP_DEVICE_IDENTITY, REASON_NO_ROWS,
                    "No devices are reporting heartbeats, so coverage cannot be calculated.");
            return kpi;
        }

        Set<String> covered = new HashSet<>();
        boolean anyPolicyCoversEverything = false;
        for (GuardrailPolicies p : safe(bundle.policies)) {
            if (p == null || !p.isActive()) continue;
            List<String> applyTo = p.getApplyToDeviceIds();
            if (applyTo == null) {
                anyPolicyCoversEverything = true;
                break;
            }
            covered.addAll(applyTo);
        }

        long coveredCount = anyPolicyCoversEverything ? liveDevices.size() : intersectionSize(covered, liveDevices);
        kpi.put("value", percentOf(coveredCount, liveDevices.size()));
        kpi.put("numerator", coveredCount);
        // Cast, not left as the raw int liveDevices.size() returns — numerator is already a long,
        // and a caller/test that (reasonably) expects both fields of one ratio to share a type
        // would otherwise see Long here and Integer there.
        kpi.put("denominator", (long) liveDevices.size());
        kpi.put("footnote", (liveDevices.size() - coveredCount) + " devices unmonitored");

        // Coverage is a point-in-time property of policy configuration, not a count of events in
        // a window, so there is no prior window to diff against — only a stored snapshot would
        // give the change figure the design shows.
        addGap(kpi, GAP_POSTURE_HISTORY, REASON_NO_ROWS,
                "Coverage has no history yet, so the change figure is unavailable.");
        return kpi;
    }

    private static int intersectionSize(Set<String> a, Set<String> b) {
        int n = 0;
        for (String v : a) {
            if (b.contains(v)) n++;
        }
        return n;
    }

    // ── KPI 4 · Sensitive data incidents ─────────────────────────────────────────

    /**
     * Counted off the bundle's pre-aggregated subcategory counts rather than a raw-event sweep.
     *
     * The join is deliberately by POLICY NAME: on real accounts a violation's subCategory is
     * usually the firing policy's own name, not a "PII-<type>" literal — the taxonomy-mismatch
     * fallback InsightUtil documents and SensitiveDataDestinationsProvider already relies on. So
     * a subcategory counts as sensitive-data when it matches the name of a policy that actually
     * has PII detection configured.
     */
    private BasicDBObject sensitiveDataIncidentsKpi(InsightDataBundle bundle, List<ThreatCategoryCount> priorSubCategory,
                                                      List<DashboardMaliciousEvent> trendWindowEvents,
                                                      int trendStartTs, List<Integer> trendBoundaries) {
        Set<String> piiPolicyNamesLower = piiPolicyNamesLower(bundle);

        long current = sumMatching(bundle.subCategoryCounts, piiPolicyNamesLower);
        BasicDBObject kpi = kpi(KPI_SENSITIVE_INCIDENTS, "Sensitive data incidents", current, null,
                InsightRoutes.GUARDRAIL_VIOLATIONS);
        kpi.put("unit", "count");

        if (piiPolicyNamesLower.isEmpty()) {
            addGap(kpi, GAP_GUARDRAIL_POLICIES, REASON_NOT_CONFIGURED,
                    "No policy has PII detection configured, so there is nothing to count.");
            return kpi;
        }

        // Same reasoning as critical alerts: an empty prior window and a failed read look
        // identical in the list, so gate on the bundle's availability flag instead.
        if (!bundle.threatBackendAvailable) {
            addGap(kpi, GAP_THREAT_BACKEND, REASON_REQUEST_FAILED, THREAT_BACKEND_DOWN_IMPACT);
            kpi.put("value", null);
            return kpi;
        }

        // Same category (not subCategory) join sumMatching uses for the headline count — see
        // bucketedSparkline's own javadoc for why this can't come from a single bucketed backend call.
        kpi.put("sparkline", bucketedSparkline(trendWindowEvents, trendStartTs, trendBoundaries,
                e -> e.getCategory() != null && piiPolicyNamesLower.contains(e.getCategory().toLowerCase(Locale.ROOT))));

        if (priorSubCategory == null) {
            addGap(kpi, GAP_NO_PRIOR_WINDOW, REASON_NOT_CONFIGURED, NO_PRIOR_WINDOW_IMPACT);
            return kpi;
        }

        long prior = sumMatching(priorSubCategory, piiPolicyNamesLower);
        Double deltaPercent = percentChange(current, prior);
        if (deltaPercent == null) {
            // No prior activity at all: a percentage from a zero base is undefined, and "+100%"
            // would be a fabrication, so report the absolute rise and say why.
            kpi.put("delta", current);
            kpi.put("deltaKind", "absolute");
            kpi.put("deltaTone", current > 0 ? "critical" : "neutral");
            kpi.put("footnote", "No incidents in the previous period");
        } else {
            kpi.put("delta", deltaPercent);
            kpi.put("deltaKind", "percent");
            kpi.put("deltaTone", current > prior ? "critical" : current < prior ? "success" : "neutral");
        }
        return kpi;
    }

    private static Set<String> piiPolicyNamesLower(InsightDataBundle bundle) {
        Set<String> names = new HashSet<>();
        for (GuardrailPolicies p : safe(bundle.policies)) {
            if (p == null || p.getName() == null) continue;
            if (InsightUtil.policyHasPiiDetection(p)) {
                names.add(p.getName().toLowerCase(Locale.ROOT));
            }
        }
        return names;
    }

    /** category (not subCategory) is the field that actually carries the firing policy's name on
     *  real accounts — same join matchedPolicyCounts/AlertModeRealHitsProvider use. subCategory
     *  carries finer-grained detail instead (e.g. "PII-<type>"/"Secrets"), which is why joining on
     *  it here matched nothing and this KPI always read 0. */
    private static long sumMatching(List<ThreatCategoryCount> counts, Set<String> policyNamesLower) {
        if (counts == null) return 0;
        long total = 0;
        for (ThreatCategoryCount c : counts) {
            if (c == null || c.getCategory() == null) continue;
            if (policyNamesLower.contains(c.getCategory().toLowerCase(Locale.ROOT))) {
                total += c.getCount();
            }
        }
        return total;
    }

    // ── Shadow AI trend ──────────────────────────────────────────────────────────

    /**
     * SHADOW_TREND_BUCKET_COUNT buckets of cumulative sanctioned vs. unsanctioned tool counts, by
     * first-seen date ({@code ApiCollection.startTs}), spanning the page's own selected
     * [startTs, endTs] — not a fixed rolling window — so the trend's shape always matches whatever
     * period is currently filtered.
     *
     * This is an approximation worth naming explicitly: a tool's sanction status is applied at
     * TODAY's classification across its *entire* history, because — same as the risk-score
     * history the PRD flags as a gap — nothing stores how a tool's audit status changed over
     * time. A tool sanctioned yesterday reads as sanctioned for every bucket, not just the last
     * one. Real (not fabricated) counts, wrong-shaped history; the gap says so.
     */
    private BasicDBObject shadowAiTrend(InsightDataBundle bundle) {
        int endTs = bundle.ctx.getEndTs() > 0 ? bundle.ctx.getEndTs() : (int) (System.currentTimeMillis() / 1000);
        // Unbounded "all time" start (0) has no natural lower edge to bucket against — falls back
        // to a fixed lookback for that one case only, same convention every other delta on this
        // page already uses for an unbounded range.
        int startTs = bundle.ctx.getStartTs() > 0 ? bundle.ctx.getStartTs()
                : endTs - (int) (SHADOW_TREND_BUCKET_COUNT * WEEK_SECONDS);
        List<Integer> boundaries = trendBucketBoundaries(startTs, endTs, SHADOW_TREND_BUCKET_COUNT);
        Map<String, String> remarksByService = InsightUtil.remarksByServiceName(bundle.auditRows);

        long[] sanctioned = new long[SHADOW_TREND_BUCKET_COUNT];
        long[] unsanctioned = new long[SHADOW_TREND_BUCKET_COUNT];

        for (ApiCollection c : safe(bundle.collections)) {
            if (c == null || c.isDeactivated() || c.getHostName() == null) continue;
            int firstSeen = c.getStartTs();
            if (firstSeen <= 0 || firstSeen > endTs) continue;

            boolean isSanctioned = InsightUtil.governanceBucket(c, bundle.allowlistNamesLower, remarksByService)
                    == GovernanceBucket.SANCTIONED;
            for (int i = 0; i < SHADOW_TREND_BUCKET_COUNT; i++) {
                if (firstSeen <= boundaries.get(i)) {
                    if (isSanctioned) sanctioned[i]++; else unsanctioned[i]++;
                }
            }
        }

        List<List<Object>> sanctionedSeries = new ArrayList<>();
        List<List<Object>> unsanctionedSeries = new ArrayList<>();
        for (int i = 0; i < SHADOW_TREND_BUCKET_COUNT; i++) {
            long bucketEndMs = boundaries.get(i) * 1000L;
            sanctionedSeries.add(Arrays.asList((Object) bucketEndMs, (Object) sanctioned[i]));
            unsanctionedSeries.add(Arrays.asList((Object) bucketEndMs, (Object) unsanctioned[i]));
        }

        BasicDBObject panel = new BasicDBObject();
        List<BasicDBObject> series = new ArrayList<>();
        series.add(namedSeries("Sanctioned", sanctionedSeries));
        series.add(namedSeries("Unsanctioned", unsanctionedSeries));
        panel.put("series", series);
        panel.put("currentSanctioned", sanctioned[SHADOW_TREND_BUCKET_COUNT - 1]);
        panel.put("currentUnsanctioned", unsanctioned[SHADOW_TREND_BUCKET_COUNT - 1]);
        panel.put("route", InsightRoutes.AGENTIC_ASSETS);

        List<Map<String, Object>> gaps = new ArrayList<>();
        gaps.add(gapRow(GAP_POSTURE_HISTORY, REASON_NOT_CONFIGURED,
                "This trend applies each tool's CURRENT sanction status across its whole history — "
                        + "we don't yet store how a tool's audit status changed over time, so a "
                        + "recently-sanctioned tool appears sanctioned for all 12 weeks, not just "
                        + "since it was approved."));
        panel.put("dataGaps", gaps);
        return panel;
    }

    private static BasicDBObject namedSeries(String name, List<List<Object>> data) {
        BasicDBObject row = new BasicDBObject();
        row.put("name", name);
        row.put("data", data);
        return row;
    }

    // ── Data leaving (what data type is in violating traffic) ───────────────────

    /**
     * PII-detecting policies matched against this window's subcategory counts, same join as
     * {@link #sensitiveDataIncidentsKpi} but broken out per policy rather than summed — the
     * closest available proxy for "what data type left the building", since a violation event
     * only carries the policy that fired, not which specific configured PII type matched (that
     * requires per-event inspection, which the LIST-scope aggregation the bundle holds doesn't
     * carry). Top {@value #TOP_DATA_TYPES} policies by volume, the rest rolled into "Other".
     */
    private BasicDBObject dataLeavingBreakdown(InsightDataBundle bundle) {
        Map<String, Long> countByPolicyName = new LinkedHashMap<>();
        Map<String, String> hexIdByPolicyName = new HashMap<>();
        for (PolicyMatch m : matchedPolicyCounts(bundle)) {
            String name = m.policy.getName();
            if (InsightUtil.policyHasPiiDetection(m.policy)){
                hexIdByPolicyName.putIfAbsent(name, m.policy.getHexId());
            }
            if(RiskScoreCalculator.DEFAULT_DATA_LEAVING_POLICIES.containsKey(name)){
                String userForName = RiskScoreCalculator.DEFAULT_DATA_LEAVING_POLICIES.get(name);
                countByPolicyName.merge(userForName, m.count, Long::sum);
            }else{
                countByPolicyName.merge("Others", m.count, Long::sum);
            }
            
        }

        long total = 0;
        for (long c : countByPolicyName.values()) total += c;

        BasicDBObject panel = new BasicDBObject();
        panel.put("total", total);
        panel.put("route", InsightRoutes.GUARDRAIL_VIOLATIONS);

        List<Map.Entry<String, Long>> sorted = new ArrayList<>(countByPolicyName.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        List<BasicDBObject> segments = new ArrayList<>();
        long shown = 0;
        for (int i = 0; i < Math.min(TOP_DATA_TYPES, sorted.size()); i++) {
            Map.Entry<String, Long> e = sorted.get(i);
            segments.add(dataTypeSegment(e.getKey(), e.getValue(), total, hexIdByPolicyName.get(e.getKey())));
            shown += e.getValue();
        }
        if (total - shown > 0) {
            segments.add(dataTypeSegment("Other", total - shown, total, null));
        }
        panel.put("segments", segments);

        List<Map<String, Object>> gaps = new ArrayList<>();
        if (total == 0) {
            gaps.add(gapRow(GAP_GUARDRAIL_POLICIES, REASON_NOT_CONFIGURED,
                    "No PII-detecting policy has matching activity in this window."));
        }
        if (!bundle.threatBackendAvailable) {
            gaps.add(gapRow(GAP_THREAT_BACKEND, REASON_REQUEST_FAILED, THREAT_BACKEND_DOWN_IMPACT));
        }
        panel.put("dataGaps", gaps);
        return panel;
    }

    private static BasicDBObject dataTypeSegment(String label, long count, long total, String filterId) {
        BasicDBObject seg = new BasicDBObject();
        seg.put("label", label);
        seg.put("count", count);
        seg.put("percent", percentOf(count, total));
        seg.put("filterId", filterId);
        return seg;
    }

    // ── Enforcement funnel ───────────────────────────────────────────────────────

    /**
     * "Matched a policy" is the sum of {@link #matchedPolicyCounts} — every event this window
     * whose category attributes back to a still-active, still-configured policy by name — against
     * the total gateway-inspected traffic (totalInspectedActions) as its denominator, the "N% of M
     * inspected actions" figure the design shows. This used to be a separately-fetched raw total
     * (SecurityPostureAction#getTotalEvents via ThreatDetectionBackendClient), unscoped by policy
     * attribution; that path mints its own JWT independent of getApiToken() and wasn't reliably
     * authenticating, so "matched" now comes from the same bundle.subCategoryCounts aggregation
     * the three downstream stages already depend on, instead of a second, less trustworthy fetch.
     * The stages split by {@code GuardrailPolicies.behaviour}: block -> hard-blocked, warn ->
     * warned only, alert -> warning overridden (an alert-mode policy logs and lets the action
     * through — the same "warned but not stopped" outcome the design calls "overridden" — rather
     * than waiting on the gateway's own per-event guardrailAction field, which nothing reads yet).
     */
    private BasicDBObject enforcementFunnel(InsightDataBundle bundle, Long totalInspectedActions) {
        long hardBlocked = 0, warnedOnly = 0, warningOverridden = 0, unclassified = 0;
        for (PolicyMatch m : matchedPolicyCounts(bundle)) {
            String behaviour = m.policy.getBehaviour();
            if (InsightUtil.isBlockingPolicy(m.policy)) {
                hardBlocked += m.count;
            } else if (behaviour != null && "warn".equalsIgnoreCase(behaviour.trim())) {
                warnedOnly += m.count;
            } else if (behaviour != null && "alert".equalsIgnoreCase(behaviour.trim())) {
                warningOverridden += m.count;
            } else {
                unclassified += m.count; // approval / unset
            }
        }

        long matched = hardBlocked + warnedOnly + warningOverridden + unclassified;
        long inspectedDenominator = totalInspectedActions != null ? totalInspectedActions : matched;

        BasicDBObject panel = new BasicDBObject();
        List<BasicDBObject> stages = new ArrayList<>();
        stages.add(funnelStage("matched", "Matched a policy", matched, inspectedDenominator));
        stages.add(funnelStage("hardBlocked", "Hard-blocked", hardBlocked, matched));
        stages.add(funnelStage("warnedOnly", "Warned only", warnedOnly, matched));
        stages.add(funnelStage("warningOverridden", "Warning overridden", warningOverridden, matched));
        panel.put("stages", stages);
        panel.put("route", InsightRoutes.GUARDRAIL_POLICIES);
        panel.put("inspectedActions", totalInspectedActions);

        List<Map<String, Object>> gaps = new ArrayList<>();
        if (!bundle.threatBackendAvailable) {
            gaps.add(gapRow(GAP_THREAT_BACKEND, REASON_REQUEST_FAILED, THREAT_BACKEND_DOWN_IMPACT));
        }
        if (totalInspectedActions == null) {
            gaps.add(gapRow("INSPECTED_ACTIONS", REASON_NOT_CONFIGURED,
                    "The trace search backend isn't configured or didn't respond, so the total "
                            + "inspected-actions denominator isn't available."));
        }
        if (unclassified > 0) {
            gaps.add(gapRow(GAP_GUARDRAIL_POLICIES, REASON_NOT_CONFIGURED,
                    unclassified + " matched events came from policies in approval mode or with no "
                            + "behaviour configured, which this funnel has no stage for."));
        }
        panel.put("dataGaps", gaps);
        return panel;
    }

    private static BasicDBObject funnelStage(String id, String label, long count, long matchedTotal) {
        BasicDBObject stage = new BasicDBObject();
        stage.put("id", id);
        stage.put("label", label);
        stage.put("count", count);
        stage.put("percentOfMatched", percentOf(count, matchedTotal));
        return stage;
    }

    // ── Attack attempts ──────────────────────────────────────────────────────────

    /**
     * bucketCount ascending epoch-second boundaries evenly dividing [startTs, endTs] — the shared
     * boundary source for every trend/sparkline panel on this page. SecurityPostureAction passes
     * these straight into AbstractThreatDetectionAction#fetchViolationsMonthlyTotals (the same
     * cheap $bucket aggregation AgenticObserveAction's own violations sparkline uses; despite the
     * name it buckets by whatever ascending boundaries it's given, not literally calendar months)
     * to get one malicious-event count per bucket back. Deliberately the page's own SELECTED
     * range, not a fixed rolling window ending "now" — a trend's shape should match whatever
     * period the filter is currently showing, same as its KPI's own headline value does.
     */
    public static List<Integer> trendBucketBoundaries(int startTs, int endTs, int bucketCount) {
        List<Integer> boundaries = new ArrayList<>();
        long rangeLength = Math.max(1, (long) endTs - startTs);
        for (int i = 1; i <= bucketCount; i++) {
            boundaries.add(i == bucketCount ? endTs : (int) (startTs + rangeLength * i / bucketCount));
        }
        return boundaries;
    }

    /**
     * One count per entry in boundaries — the number of events matching `matches` whose timestamp
     * falls in that bucket (the half-open "(previous boundary, this boundary]" range; the first
     * bucket's lower edge is rangeStartTs itself). Backs the Critical alerts / Sensitive data
     * incidents KPI sparklines: the threat backend's own bucketed aggregation
     * (AbstractThreatDetectionAction#fetchViolationsMonthlyTotals) has no severity or category
     * filter, so there's no single backend call that returns "critical-only" or "PII-policy-only"
     * counts per bucket. Bucketing one raw-event fetch client-side is cheaper than firing one
     * backend call per bucket per KPI.
     */
    static List<Long> bucketedSparkline(List<DashboardMaliciousEvent> events, int rangeStartTs,
                                         List<Integer> boundaries, Predicate<DashboardMaliciousEvent> matches) {
        List<Long> counts = new ArrayList<>(Collections.nCopies(boundaries.size(), 0L));
        for (DashboardMaliciousEvent event : safe(events)) {
            if (event == null || !matches.test(event)) continue;
            long ts = event.getTimestamp();
            for (int i = 0; i < boundaries.size(); i++) {
                int lowerExclusive = i == 0 ? rangeStartTs : boundaries.get(i - 1);
                int upperInclusive = boundaries.get(i);
                if (ts > lowerExclusive && ts <= upperInclusive) {
                    counts.set(i, counts.get(i) + 1);
                    break;
                }
            }
        }
        return counts;
    }

    /**
     * "Attack attempts" — bucketed malicious-event counts as a Blocked/Got-through stacked series
     * over the page's selected range, plus the latest bucket's headline numbers.
     *
     * "Got through" (a malicious event that reached its target rather than being stopped) has no
     * data source yet — every malicious event is counted as Blocked here. Kept as its own
     * always-present series/field (zeroed, not omitted) so the chart and legend are already
     * shaped for the day a per-event outcome filter exists: wiring it in is then "fetch this same
     * bucketed query again with that filter", not a redesign. Same gap this funnel already has
     * for the gateway's unread per-event guardrailAction field.
     */
    private BasicDBObject attackAttemptsTrend(List<Integer> weeklyMaliciousCounts, List<Integer> boundaries) {
        boolean fetchFailed = weeklyMaliciousCounts == null || weeklyMaliciousCounts.size() < boundaries.size();

        List<BasicDBObject> series = new ArrayList<>();
        List<List<Object>> blockedPoints = new ArrayList<>();
        List<List<Object>> gotThroughPoints = new ArrayList<>();
        long currentTotal = 0;
        for (int i = 0; i < boundaries.size(); i++) {
            long bucketEndMs = boundaries.get(i) * 1000L;
            long count = (!fetchFailed) ? weeklyMaliciousCounts.get(i) : 0;
            blockedPoints.add(Arrays.asList((Object) bucketEndMs, (Object) count));
            gotThroughPoints.add(Arrays.asList((Object) bucketEndMs, (Object) 0L));
            if (i == boundaries.size() - 1) currentTotal = count;
        }
        series.add(namedSeries("Blocked", blockedPoints));
        series.add(namedSeries("Got through", gotThroughPoints));

        BasicDBObject panel = new BasicDBObject();
        panel.put("series", series);
        panel.put("currentTotal", currentTotal);
        panel.put("currentBlocked", currentTotal); // "assume all blocked" placeholder — see above
        panel.put("currentGotThrough", 0L);
        panel.put("route", InsightRoutes.GUARDRAIL_VIOLATIONS);

        List<Map<String, Object>> gaps = new ArrayList<>();
        if (fetchFailed) {
            gaps.add(gapRow(GAP_THREAT_BACKEND, REASON_REQUEST_FAILED, THREAT_BACKEND_DOWN_IMPACT));
        }
        gaps.add(gapRow("GUARDRAIL_ACTION_FIELD", REASON_NOT_CONFIGURED,
                "Whether an attack got through isn't read yet, so every malicious event here is "
                        + "counted as blocked. The gateway already writes the real per-event outcome; "
                        + "ElasticSearchClient/AzureDataExplorerClient don't query it."));
        panel.put("dataGaps", gaps);
        return panel;
    }

    // ── Biggest movers ───────────────────────────────────────────────────────────
    //
    // Two independent conditions, evaluated over the same fixed BIGGEST_MOVERS_WINDOW_DAYS
    // lookback: (1) a vendor's device count (collections active within the window) crossed the
    // device threshold, (2) a vendor's malicious-event count within the window crossed the attack
    // threshold. Each condition contributes at most BIGGEST_MOVERS_MAX_PER_CONDITION rows; the
    // combined list is capped at BIGGEST_MOVERS_MAX_TOTAL, ranked by how far over its OWN
    // threshold each row is (a ratio, not the raw value) so the attack condition's much larger
    // numbers don't always crowd out the device condition's.

    private BasicDBObject biggestMovers(List<ApiCollection> endpointCollections, Map<Integer, Integer> collectionLastTrafficSeen,
                                         List<DashboardMaliciousEvent> trendWindowEvents, int windowStartTs) {
        Map<String, Set<String>> devicesByVendor = new HashMap<>();
        Map<Integer, String> vendorByCollectionId = new HashMap<>();
        for (ApiCollection c : safe(endpointCollections)) {
            if (c == null || c.isDeactivated()) continue;
            String vendor = InsightUtil.endpointVendorName(c);
            if (vendor == null) continue;
            vendorByCollectionId.put(c.getId(), vendor);
            Integer lastSeen = collectionLastTrafficSeen != null ? collectionLastTrafficSeen.get(c.getId()) : null;
            if (lastSeen == null || lastSeen < windowStartTs) continue; // not active within the window
            String deviceId = InsightUtil.deviceIdOf(c);
            devicesByVendor.computeIfAbsent(vendor, k -> new HashSet<>()).add(deviceId != null ? deviceId : "unknown");
        }

        List<BasicDBObject> deviceMovers = new ArrayList<>();
        for (Map.Entry<String, Set<String>> e : devicesByVendor.entrySet()) {
            int count = e.getValue().size();
            if (count > BIGGEST_MOVERS_DEVICE_THRESHOLD) {
                deviceMovers.add(moverRow("devices", e.getKey(), count, BIGGEST_MOVERS_DEVICE_THRESHOLD,
                        e.getKey() + " crossed " + BIGGEST_MOVERS_DEVICE_THRESHOLD + " devices in the last "
                                + BIGGEST_MOVERS_WINDOW_DAYS + " days"));
            }
        }
        List<BasicDBObject> topDeviceMovers = topByRatio(deviceMovers, BIGGEST_MOVERS_MAX_PER_CONDITION);

        Map<String, Long> attacksByVendor = new HashMap<>();
        // trendWindowEvents spans the wider attack-trend window (its own caller reuses one raw
        // fetch for both purposes rather than fetching this narrower range twice — see
        // SecurityPostureAction's own comment on trendWindowEventsFuture), so this condition must
        // filter down to windowStartTs itself instead of trusting the list's own bounds.
        for (DashboardMaliciousEvent event : safe(trendWindowEvents)) {
            if (event == null || event.getTimestamp() < windowStartTs) continue;
            String vendor = vendorByCollectionId.get(event.getApiCollectionId());
            if (vendor == null) continue;
            attacksByVendor.merge(vendor, 1L, Long::sum);
        }
        List<BasicDBObject> attackMovers = new ArrayList<>();
        for (Map.Entry<String, Long> e : attacksByVendor.entrySet()) {
            if (e.getValue() > BIGGEST_MOVERS_ATTACK_THRESHOLD) {
                attackMovers.add(moverRow("attacks", e.getKey(), e.getValue(), BIGGEST_MOVERS_ATTACK_THRESHOLD,
                        e.getKey() + " crossed " + BIGGEST_MOVERS_ATTACK_THRESHOLD + " attack attempts in the last "
                                + BIGGEST_MOVERS_WINDOW_DAYS + " days"));
            }
        }
        List<BasicDBObject> topAttackMovers = topByRatio(attackMovers, BIGGEST_MOVERS_MAX_PER_CONDITION);

        List<BasicDBObject> combined = new ArrayList<>();
        combined.addAll(topDeviceMovers);
        combined.addAll(topAttackMovers);
        List<BasicDBObject> top = topByRatio(combined, BIGGEST_MOVERS_MAX_TOTAL);

        BasicDBObject panel = new BasicDBObject();
        panel.put("movers", top);
        return panel;
    }

    private static BasicDBObject moverRow(String condition, String vendor, long value, long threshold, String headline) {
        BasicDBObject row = new BasicDBObject();
        row.put("condition", condition);
        row.put("vendor", vendor);
        row.put("value", value);
        row.put("threshold", threshold);
        row.put("headline", headline);
        return row;
    }

    /** Sorted by value/threshold descending (not raw value) so two conditions with very different
     *  units — a device count in the tens, an attack count in the thousands — rank fairly against
     *  each other, then trimmed to topN. */
    private static List<BasicDBObject> topByRatio(List<BasicDBObject> rows, int topN) {
        rows.sort((a, b) -> Double.compare(overThresholdRatio(b), overThresholdRatio(a)));
        return rows.subList(0, Math.min(topN, rows.size()));
    }

    private static double overThresholdRatio(BasicDBObject row) {
        long threshold = row.getLong("threshold");
        return threshold == 0 ? 0 : ((Number) row.get("value")).doubleValue() / threshold;
    }

    // ── Framework readiness ──────────────────────────────────────────────────────
    //
    // Real, not illustrative: readiness for a framework is the share of ITS OWN
    // ComplianceSubClauseCatalog sub-clauses that have actually been hit by real guardrail-violation
    // traffic in the selected window — "how much of this framework have we actually seen exercised",
    // not "are my policies switched on" (the earlier, replaced definition — see git history / the
    // package CLAUDE.md for why). The numerator comes from ComplianceClauseCoverageDao, written by
    // ComplianceClauseScanService's LLM clause-attribution scan (triggered from the Threat Detection
    // page); this method only reads that result and buckets it into [trendStartTs, trendEndTs] so
    // the panel moves with the page's date filter, per this file's design principle 1.

    /** A policy is "enforcing" only while it's active AND its LLM rule (which carries the
     *  compliance mapping) is enabled — a disabled/inactive policy's compliance mapping is
     *  configuration, not live coverage. Package-private (not private): RiskScoreCalculator's
     *  Compliance gaps sub-score AND ComplianceClauseScanService use the same definition of
     *  "covered by a compliance framework" so all three can't disagree with each other. */
    static boolean policyEnforcing(GuardrailPolicies p) {
        return p != null && p.isActive() && p.getLlmRule() != null && p.getLlmRule().isEnabled();
    }

    /** Same enforcing check, plus the policy must actually be mapped to at least one compliance
     *  framework in its LLM rule. */
    static boolean policyHasComplianceMapping(GuardrailPolicies p) {
        return policyEnforcing(p) && p.getLlmRule().getCompliance() != null
                && !p.getLlmRule().getCompliance().isEmpty();
    }

    private BasicDBObject frameworkReadiness(int trendStartTs, int trendEndTs) {
        List<ComplianceClauseCoverage> coverageDocs = ComplianceClauseCoverageDao.instance.findAllCoverage();

        List<BasicDBObject> rows = new ArrayList<>();
        int lastScannedAt = 0;
        for (ComplianceClauseCoverage doc : safe(coverageDocs)) {
            if (doc == null || doc.getId() == null) continue;
            int total = doc.getTotalClauses() > 0
                    ? doc.getTotalClauses()
                    : ComplianceSubClauseCatalog.totalClauses(doc.getId());
            if (total <= 0) continue;

            // Only clauses with at least one hit whose timestamp falls in the page's selected
            // window count — a clause hit once, months ago, outside the current range isn't "live".
            int covered = 0;
            Map<String, List<ClauseHit>> clauseHits = doc.getClauseHits();
            if (clauseHits != null) {
                for (List<ClauseHit> hits : clauseHits.values()) {
                    if (hits == null) continue;
                    boolean inWindow = hits.stream().anyMatch(h ->
                            h != null && h.getTimestamp() >= trendStartTs && h.getTimestamp() <= trendEndTs);
                    if (inWindow) covered++;
                }
            }

            BasicDBObject row = new BasicDBObject();
            row.put("framework", doc.getId());
            row.put("value", (int) Math.round((covered * 100.0) / total));
            row.put("clausesCovered", covered);
            row.put("totalClauses", total);
            row.put("lastScannedAt", doc.getLastScannedAt());
            rows.add(row);
            lastScannedAt = Math.max(lastScannedAt, doc.getLastScannedAt());
        }
        // Most-covered framework first, same "lead with the strongest signal" ordering the earlier
        // policy-count metric used.
        rows.sort((a, b) -> Integer.compare(b.getInt("clausesCovered"), a.getInt("clausesCovered")));
        List<BasicDBObject> top = rows.subList(0, Math.min(6, rows.size()));

        BasicDBObject panel = new BasicDBObject();
        panel.put("frameworks", top);
        panel.put("lastScannedAt", lastScannedAt);
        List<Map<String, Object>> gaps = new ArrayList<>();
        if (rows.isEmpty()) {
            gaps.add(gapRow(GAP_COMPLIANCE_SCAN, REASON_NOT_CONFIGURED,
                    "No compliance clause scan has been run yet — trigger one from the Threat Detection page to score framework readiness."));
        }
        panel.put("dataGaps", gaps);
        return panel;
    }

    // ── Shared policy-name join ──────────────────────────────────────────────────

    /** Policy name (lowercased) -> policy — built once and shared by every caller that needs to
     *  resolve an event/aggregate's category back to the policy that fired it (matchedPolicyCounts
     *  below, and RiskScoreCalculator's DLP-by-device and compliance-gaps-by-policy breakdowns). */
    static Map<String, GuardrailPolicies> policyByNameLower(List<GuardrailPolicies> policies) {
        Map<String, GuardrailPolicies> byName = new HashMap<>();
        for (GuardrailPolicies p : safe(policies)) {
            if (p != null && p.getName() != null) {
                byName.put(p.getName().toLowerCase(Locale.ROOT), p);
            }
        }
        return byName;
    }

    /**
     * Matches this window's per-subCategory counts back to the policy that produced them, by
     * name (case-insensitive) — the same taxonomy-mismatch convention InsightUtil and
     * SensitiveDataDestinationsProvider already document: on real accounts, a violation's
     * subCategory is usually the firing policy's own name, not a fixed literal. A subCategory
     * with no matching policy name is dropped — it belongs to a policy that's since been renamed
     * or deleted, or (for the threat-backend's combined aggregation) to a non-guardrail category
     * that was never going to match a policy name in the first place.
     *
     * Built once and shared by {@link #dataLeavingBreakdown}, {@link #enforcementFunnel}, and
     * RiskScoreCalculator's DLP sub-score — per the PRD, this join is the one piece of logic all
     * three actually need. Package-private (not private) for that last reason.
     */
    static List<PolicyMatch> matchedPolicyCounts(InsightDataBundle bundle) {
        Map<String, GuardrailPolicies> policyByNameLower = policyByNameLower(bundle.policies);
        List<PolicyMatch> matches = new ArrayList<>();
        for (ThreatCategoryCount c : safe(bundle.subCategoryCounts)) {
            // category (not subCategory) is the field that actually carries the firing policy's
            // name on real accounts — confirmed by AlertModeRealHitsProvider's own join, which
            // this mirrors. subCategory instead carries the finer-grained detail (e.g.
            // "PII-<type>"/"Secrets"), which is why joining on it here matched nothing.
            if (c == null || c.getCategory() == null) continue;
            GuardrailPolicies p = policyByNameLower.get(c.getCategory().toLowerCase(Locale.ROOT));
            if (p != null) matches.add(new PolicyMatch(p, c.getCount()));
        }
        return matches;
    }

    /** Package-private (not private): RiskScoreCalculator's DLP sub-score iterates these too. */
    static final class PolicyMatch {
        final GuardrailPolicies policy;
        final long count;
        PolicyMatch(GuardrailPolicies policy, long count) {
            this.policy = policy;
            this.count = count;
        }
    }

    // ── shared helpers ───────────────────────────────────────────────────────────
    // kpi/addGap/safe are package-private (not private): RiskScoreCalculator uses them too,
    // rather than duplicating the same KPI-shape/gap-shape builders in a second file.

    static BasicDBObject kpi(String id, String label, Long value, Long denominator, String route) {
        BasicDBObject kpi = new BasicDBObject();
        kpi.put("id", id);
        kpi.put("label", label);
        kpi.put("value", value);
        kpi.put("denominator", denominator);
        kpi.put("delta", null);
        kpi.put("deltaKind", null);
        kpi.put("deltaTone", "neutral");
        kpi.put("footnote", null);
        kpi.put("route", route);
        kpi.put("dataGaps", new ArrayList<Map<String, Object>>());
        return kpi;
    }

    /** Mirrors InsightResult.Gap's source/reason/impact triple so the frontend renders posture
     *  gaps with the same component it already uses for insight gaps. */
    @SuppressWarnings("unchecked")
    static void addGap(BasicDBObject target, String source, String reason, String impact) {
        ((List<Map<String, Object>>) target.get("dataGaps")).add(gapRow(source, reason, impact));
    }

    /** Same triple as {@link #addGap}, for panels (shadow AI, data leaving, enforcement) that
     *  build their own dataGaps list directly rather than through the KPI-tile helper.
     *  Package-private (not private): RiskScoreCalculator uses it too, to attach the same gap
     *  to a sub-score row as is attached to the composite KPI. */
    static Map<String, Object> gapRow(String source, String reason, String impact) {
        InsightResult.Gap gap = new InsightResult.Gap(source, reason, impact);
        Map<String, Object> row = new HashMap<>();
        row.put("source", gap.getSource());
        row.put("reason", gap.getReason());
        row.put("impact", gap.getImpact());
        return row;
    }

    /** Percentage of total, one decimal place. Returns 0 when there is no total. */
    private static double percentOf(long part, long total) {
        if (total <= 0) return 0d;
        return Math.round((part * 1000d) / total) / 10d;
    }

    /** Period-over-period change, one decimal place. Null when the prior period was zero, where
     *  a percentage would be undefined rather than infinite. */
    private static Double percentChange(long current, long prior) {
        if (prior <= 0) return null;
        return Math.round(((current - prior) * 1000d) / prior) / 10d;
    }

    static <T> List<T> safe(List<T> list) {
        return list == null ? new ArrayList<>() : list;
    }
}

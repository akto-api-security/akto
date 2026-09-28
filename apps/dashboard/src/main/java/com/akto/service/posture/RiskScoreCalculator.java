package com.akto.service.posture;

import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.action.threat_detection.HostSeverityCount;
import com.akto.action.threat_detection.ThreatCategoryCount;
import com.akto.dto.ApiCollection;
import com.akto.dto.GuardrailPolicies;
import com.akto.service.insights.InsightDataBundle;
import com.akto.service.insights.InsightResult;
import com.akto.service.insights.InsightRoutes;
import com.akto.service.insights.InsightUtil;
import com.akto.service.insights.InsightUtil.GovernanceBucket;
import com.akto.util.AgenticObserveUtil;
import com.mongodb.BasicDBObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * KPI 1 · AI risk score — the composite, its five sub-scores, and the vendor-risk table.
 *
 * Pulled out of PostureService because it is the single largest self-contained piece of the
 * page: called from exactly one place ({@link PostureService#buildSummary}), with its own
 * nested type (VendorRiskAnalysis) and its own constants (the weights, the risky-vendor map)
 * that nothing else in the package needs. Unlike the panels (Shadow AI trend, Data leaving,
 * Enforcement funnel), which share matchedPolicyCounts/governanceBucket-based joins with each
 * OTHER as well as with two of these sub-scores, this class only reaches back into
 * PostureService for that same shared join (matchedPolicyCounts/PolicyMatch) and the small
 * kpi()/addGap()/safe() builders every KPI uses — both kept package-private there rather than
 * duplicated here.
 *
 * Composite over the same five sub-scores the design shows, at the same weights: Shadow AI
 * exposure 30% · DLP incidents 25% · Vendor risk 20% · Compliance gaps 15% · Threat activity
 * 10%. The composite renormalizes over whichever sub-scores are actually defined for this
 * account/window (see each method's own null contract below), so a single missing piece
 * degrades the number gracefully instead of hiding it entirely.
 *
 * Each sub-score is a real ratio (0-100, higher = worse), not an invented scale:
 *  - Shadow AI exposure: % of collections that are not SANCTIONED — the same classifier and
 *    the same number as UngovernedAiRatioProvider's ungovernedRatio / the Shadow AI panel.
 *  - DLP incidents: % of this window's PII-policy matches that were NOT hard-blocked, i.e.
 *    sensitive data that had a real chance to leave. Same policy-mode join as
 *    PostureService#dataLeavingBreakdown, restricted to PII-detecting policies.
 *  - Vendor risk: see vendorRiskAnalysis below.
 *  - Compliance gaps: see complianceGapsSubScore below.
 *  - Threat activity: % of this window's threat-severity counts that are CRITICAL.
 *
 * A sub-score is null (excluded from the composite, not scored as 0) only when it is genuinely
 * undefined for this account/window — no agentic assets to classify, no PII policy configured
 * at all, no resolvable-vendor endpoint traffic, no policy mapped to a compliance framework yet,
 * or the threat backend didn't respond. A confirmed zero (PII policies exist but nothing
 * matched; the threat backend responded with no activity) is a real, good score of 0, not an
 * exclusion.
 */
final class RiskScoreCalculator {

    private RiskScoreCalculator() {}

    private static final double WEIGHT_SHADOW_AI  = 0.30;
    private static final double WEIGHT_DLP        = 0.25;
    private static final double WEIGHT_VENDOR     = 0.20;
    private static final double WEIGHT_COMPLIANCE = 0.15;
    private static final double WEIGHT_THREAT     = 0.10;

    /** Renormalized 0-100 composite over whichever sub-scores are non-null, or null if none are —
     *  shared by the current-window composite and the prior-window one the delta diffs against. */
    private static Double weightedComposite(Double shadowAi, Double dlp, Double vendor, Double compliance, Double threat) {
        double weightedSum = 0;
        double coveredWeight = coveredWeight(shadowAi, dlp, vendor, compliance, threat);
        if (coveredWeight == 0) return null;
        if (shadowAi != null) weightedSum += WEIGHT_SHADOW_AI * shadowAi;
        if (dlp != null) weightedSum += WEIGHT_DLP * dlp;
        if (vendor != null) weightedSum += WEIGHT_VENDOR * vendor;
        if (compliance != null) weightedSum += WEIGHT_COMPLIANCE * compliance;
        if (threat != null) weightedSum += WEIGHT_THREAT * threat;
        return weightedSum / coveredWeight;
    }

    private static double coveredWeight(Double shadowAi, Double dlp, Double vendor, Double compliance, Double threat) {
        double coveredWeight = 0;
        if (shadowAi != null) coveredWeight += WEIGHT_SHADOW_AI;
        if (dlp != null) coveredWeight += WEIGHT_DLP;
        if (vendor != null) coveredWeight += WEIGHT_VENDOR;
        if (compliance != null) coveredWeight += WEIGHT_COMPLIANCE;
        if (threat != null) coveredWeight += WEIGHT_THREAT;
        return coveredWeight;
    }

    static BasicDBObject compute(InsightDataBundle bundle, List<ApiCollection> endpointCollections,
                                  List<HostSeverityCount> priorHostSeverity,
                                  List<ThreatCategoryCount> priorSubCategory,
                                  List<PostureService.PolicyMatch> matches) {
        // `matches` is buildSummary's own already-computed PostureService.matchedPolicyCounts(bundle)
        // — passed in, not recomputed here, because buildSummary ALSO needs it for
        // dataLeavingBreakdown/enforcementFunnel over the identical bundle; threading one shared
        // instance through all three avoids 3 rebuilds of the same policyByNameLower map +
        // subCategoryCounts scan in that one synchronous call.
        Double shadowAiSubScore = shadowAiExposureSubScore(bundle);
        Double dlpSubScore = dlpIncidentsSubScore(bundle, matches);
        Double threatSubScore = threatActivitySubScore(bundle);
        VendorRiskAnalysis vendorAnalysis = vendorRiskAnalysis(endpointCollections, bundle.allowlistNamesLower);
        Double complianceSubScore = complianceGapsSubScore(bundle, matches);

        Double composite = weightedComposite(shadowAiSubScore, dlpSubScore, vendorAnalysis.subScore,
                complianceSubScore, threatSubScore);
        double coveredWeight = coveredWeight(shadowAiSubScore, dlpSubScore, vendorAnalysis.subScore,
                complianceSubScore, threatSubScore);

        BasicDBObject kpi = PostureService.kpi(PostureService.KPI_RISK_SCORE, "AI risk score", null, null, null);
        kpi.put("unit", "score");
        // Also needed on the main page (not just this KPI's own flyout breakdown): the Vendor risk
        // vs. exposure card reads this directly off the summary response — vendorAnalysis is
        // already computed above for the composite's own vendor-risk sub-score, so this is free.
        kpi.put("vendorTable", vendorAnalysis.rows);

        int weightCoveredPercent = (int) Math.round(coveredWeight * 100);
        kpi.put("weightCovered", weightCoveredPercent);

        if (composite != null) {
            kpi.put("value", Math.round(composite));
            if (weightCoveredPercent < 100) {
                kpi.put("footnote", "Computed from " + weightCoveredPercent + "% of the full composite");
            }
        }

        // Week-over-week delta — a real prior-window comparison (same convention
        // criticalAlertsKpi/sensitiveDataIncidentsKpi already use), not a fabricated trend. Only
        // possible when the page has a bounded window (priorHostSeverity/priorSubCategory are
        // null for an "all time" range — see SecurityPostureAction) and the current composite
        // itself computed. shadowAiExposure/vendorRisk aren't window-scoped (they read
        // collection/allowlist state, not time-filtered events), so the prior composite reuses
        // this window's values for those two rather than recomputing them from identical inputs.
        if (composite != null && priorHostSeverity != null && priorSubCategory != null) {
            InsightDataBundle priorView = new InsightDataBundle(bundle.ctx, bundle.collections,
                    bundle.collectionsByServiceName, bundle.deviceIdToUsername, bundle.userTags,
                    bundle.auditRows, bundle.policies, bundle.allowlistNamesLower, bundle.sensitiveByCollection,
                    bundle.userAnalysis, bundle.nhiIdentities, priorHostSeverity, priorSubCategory,
                    bundle.skillSeverityCounts, true, bundle.activeCollections, bundle.collectionLastTrafficSeen,
                    null);
            List<PostureService.PolicyMatch> priorMatches = PostureService.matchedPolicyCounts(priorView);
            Double priorDlpSubScore = dlpIncidentsSubScore(priorView, priorMatches);
            Double priorThreatSubScore = threatActivitySubScore(priorView);
            Double priorComplianceSubScore = complianceGapsSubScore(priorView, priorMatches);

            Double priorComposite = weightedComposite(shadowAiSubScore, priorDlpSubScore, vendorAnalysis.subScore,
                    priorComplianceSubScore, priorThreatSubScore);
            if (priorComposite != null) {
                long current = Math.round(composite);
                long prior = Math.round(priorComposite);
                kpi.put("delta", current - prior);
                kpi.put("deltaKind", "absolute");
                // Higher is worse for this score, so a positive delta (score went up) is a bad trend.
                kpi.put("deltaTone", current > prior ? "critical" : current < prior ? "success" : "neutral");
            }

            // "What moved the score" — one row per sub-score that actually moved between windows,
            // each row's points computed the same way the composite delta itself is: this
            // sub-score's weight over the SAME coveredWeight, times its own (current - prior). Sum
            // these rows and you get exactly the composite delta above — not an approximation,
            // because it's the same renormalized-weighted-average arithmetic decomposed back out
            // per term. shadowAiExposure/vendorRisk are deliberately absent: their "prior" value
            // above is the current value reused (see the comment on this whole block), so their
            // term is always exactly 0 — they cannot have moved the score this window, whatever
            // their own snapshot breakdown (RiskScoreCalculator#computeBreakdown) shows.
            List<BasicDBObject> whatMoved = new ArrayList<>();
            addWhatMovedRow(whatMoved, "DLP incidents", WEIGHT_DLP, coveredWeight, dlpSubScore, priorDlpSubScore);
            addWhatMovedRow(whatMoved, "Compliance gaps", WEIGHT_COMPLIANCE, coveredWeight, complianceSubScore, priorComplianceSubScore);
            addWhatMovedRow(whatMoved, "Threat activity", WEIGHT_THREAT, coveredWeight, threatSubScore, priorThreatSubScore);
            kpi.put("whatMoved", whatMoved);
        }

        if (shadowAiSubScore == null) {
            PostureService.addGap(kpi, "GOVERNANCE_DATA", PostureService.REASON_NO_ROWS,
                    "No agentic assets have been discovered yet, so shadow AI exposure can't be scored.");
        }
        if (dlpSubScore == null) {
            PostureService.addGap(kpi, PostureService.GAP_GUARDRAIL_POLICIES, PostureService.REASON_NOT_CONFIGURED,
                    "No policy has PII detection configured, so DLP incidents can't be scored.");
        }
        if (vendorAnalysis.subScore == null) {
            PostureService.addGap(kpi, "VENDOR_RISK", PostureService.REASON_NO_ROWS,
                    "No endpoint traffic resolves to a known vendor yet, so vendor risk can't be scored.");
        }
        if (complianceSubScore == null) {
            PostureService.addGap(kpi, PostureService.GAP_GUARDRAIL_POLICIES, PostureService.REASON_NOT_CONFIGURED,
                    "No policy has a compliance framework mapped in its LLM rule yet, so compliance gaps can't be scored.");
        }
        if (threatSubScore == null) {
            PostureService.addGap(kpi, PostureService.GAP_THREAT_BACKEND, PostureService.REASON_REQUEST_FAILED,
                    PostureService.THREAT_BACKEND_DOWN_IMPACT);
        }
        PostureService.addGap(kpi, PostureService.GAP_POSTURE_HISTORY, PostureService.REASON_NOT_CONFIGURED,
                "There's no stored score history yet, so the 12-week trend and the \"what moved the "
                        + "score\" annotations aren't available — only a prior-window comparison is.");

        return kpi;
    }

    /** One "what moved" row — skipped when either side is null (sub-score not computable in one
     *  of the two windows) or the sub-score didn't actually change. */
    private static void addWhatMovedRow(List<BasicDBObject> whatMoved, String category, double weightFraction,
                                         double coveredWeight, Double current, Double prior) {
        if (current == null || prior == null || coveredWeight == 0) return;
        double points = Math.round(((weightFraction / coveredWeight) * (current - prior)) * 10.0) / 10.0;
        if (points == 0) return;
        BasicDBObject row = new BasicDBObject();
        row.put("category", category);
        row.put("impactPoints", points);
        whatMoved.add(row);
    }

    /**
     * The drilldown's own detail — the same five sub-scores behind the composite above, broken
     * out rather than collapsed into one number, plus the vendor-risk table. Deliberately a
     * separate call ({@link PostureService#buildRiskScoreBreakdown}) rather than embedded in
     * {@link #compute}'s response: the composite KPI (value + delta) is what every page load
     * needs, but the row-by-row breakdown and vendor table are only needed once someone actually
     * opens the flyout, so the summary call doesn't need to build or send them. No per-row deltas
     * here — the composite's prior-window comparison isn't decomposed back per sub-score.
     */
    static BasicDBObject computeBreakdown(InsightDataBundle bundle, List<ApiCollection> endpointCollections,
                                           List<DashboardMaliciousEvent> allThreats,
                                           List<DashboardMaliciousEvent> priorAllThreats,
                                           List<HostSeverityCount> priorHostSeverity) {
        // Same dedup as compute()'s own note: dlpIncidentsSubScore/complianceGapsSubScore/
        // complianceGapsByPolicy each used to independently call matchedPolicyCounts(bundle) —
        // 3 rebuilds of the identical policyByNameLower map + subCategoryCounts scan, all inside
        // this one synchronous call (the risk-score breakdown flyout's root level).
        List<PostureService.PolicyMatch> matches = PostureService.matchedPolicyCounts(bundle);
        Double shadowAiSubScore = shadowAiExposureSubScore(bundle);
        Double dlpSubScore = dlpIncidentsSubScore(bundle, matches);
        Double threatSubScore = threatActivitySubScore(bundle);
        VendorRiskAnalysis vendorAnalysis = vendorRiskAnalysis(endpointCollections, bundle.allowlistNamesLower);
        Double complianceSubScore = complianceGapsSubScore(bundle, matches);

        List<BasicDBObject> subScores = new ArrayList<>();
        subScores.add(subScoreRow("shadowAiExposure", "Shadow AI exposure", 30, shadowAiSubScore,
                "GOVERNANCE_DATA", PostureService.REASON_NO_ROWS,
                "No agentic assets have been discovered yet, so shadow AI exposure can't be scored."));
        subScores.add(subScoreRow("dlpIncidents", "DLP incidents", 25, dlpSubScore,
                PostureService.GAP_GUARDRAIL_POLICIES, PostureService.REASON_NOT_CONFIGURED,
                "No policy has PII detection configured, so DLP incidents can't be scored."));
        subScores.add(subScoreRow("vendorRisk", "Vendor risk", 20, vendorAnalysis.subScore,
                "VENDOR_RISK", PostureService.REASON_NO_ROWS,
                "No endpoint traffic resolves to a known vendor yet, so vendor risk can't be scored."));
        subScores.add(subScoreRow("complianceGaps", "Compliance gaps", 15, complianceSubScore,
                PostureService.GAP_GUARDRAIL_POLICIES, PostureService.REASON_NOT_CONFIGURED,
                "No policy has a compliance framework mapped in its LLM rule yet, so compliance gaps can't be scored."));
        subScores.add(subScoreRow("threatActivity", "Threat activity", 10, threatSubScore,
                PostureService.GAP_THREAT_BACKEND, PostureService.REASON_REQUEST_FAILED,
                PostureService.THREAT_BACKEND_DOWN_IMPACT));

        BasicDBObject breakdown = new BasicDBObject();
        breakdown.put("subScores", subScores);
        breakdown.put("vendorTable", vendorAnalysis.rows);
        breakdown.put("threatActivityMovements",
                threatActivityDeviceMovements(bundle.hostSeverityCounts, priorHostSeverity, bundle.deviceIdToUsername));
        // "What's driving this" for the other four sub-scores — device/vendor/policy breakdowns,
        // each on the same data the sub-score itself is computed from (see each method's own
        // javadoc for why the shape differs per sub-score: shadow AI and vendor risk are
        // structural snapshots with no time dimension, DLP mirrors threat activity's per-device
        // diff, and compliance gaps groups by policy rather than device since an uncovered event
        // isn't attributable to one).
        breakdown.put("shadowAiTopServices", shadowAiTopUnapprovedServices(bundle));
        breakdown.put("dlpDeviceMovements",
                dlpDeviceMovements(allThreats, priorAllThreats, bundle.policies, bundle.deviceIdToUsername));
        breakdown.put("vendorRiskTopUnapproved", vendorRiskTopUnapprovedDevices(vendorAnalysis.rows));
        breakdown.put("complianceGapsByPolicy", complianceGapsByPolicy(matches));
        return breakdown;
    }

    // ── "What moved the score" — real, not illustrative ────────────────────────────
    //
    // Only the Threat activity sub-score decomposes this way: DLP/vendor/compliance/shadow-AI are
    // policy- or collection-level, not attributable to a single device from the data this page
    // already loads, so this is deliberately narrower than "what moved every sub-score" — it's
    // "which devices' threat activity moved the most this window vs. the immediately preceding
    // one", the one sub-score where that question has a real answer.

    /**
     * Top 5 devices by absolute change in violation count between this window and the immediately
     * preceding one (see hostSeverityCounts — per-host critical/high/medium/low counts). "host" on
     * each row is the same ApiCollection hostName shape ("&lt;device&gt;.&lt;client-type&gt;.
     * &lt;vendor&gt;...") used everywhere else on this page; extractEndpointId splits off the
     * device id, the same as AgenticObserveAction's own per-device grouping. deviceIdToUsername is
     * the bundle's own map (InsightDataLoader#loadDeviceIdToUsername), so no separate lookup.
     * Empty when there's no prior window (an unbounded "all time" range — priorHostSeverity is
     * null then, same convention SecurityPostureAction uses for every other delta on this page).
     */
    static List<BasicDBObject> threatActivityDeviceMovements(List<HostSeverityCount> current,
                                                               List<HostSeverityCount> priorHostSeverity,
                                                               Map<String, String> deviceIdToUsername) {
        if (priorHostSeverity == null) return new ArrayList<>();

        Map<String, Long> currentByDevice = totalActivityByDevice(current);
        Map<String, Long> priorByDevice = totalActivityByDevice(priorHostSeverity);

        Set<String> deviceIds = new HashSet<>();
        deviceIds.addAll(currentByDevice.keySet());
        deviceIds.addAll(priorByDevice.keySet());

        List<BasicDBObject> movements = new ArrayList<>();
        for (String deviceId : deviceIds) {
            long currentCount = currentByDevice.getOrDefault(deviceId, 0L);
            long priorCount = priorByDevice.getOrDefault(deviceId, 0L);
            long diff = currentCount - priorCount;
            if (diff == 0) continue;
            BasicDBObject row = new BasicDBObject();
            row.put("deviceId", deviceId);
            row.put("username", deviceIdToUsername != null ? deviceIdToUsername.getOrDefault(deviceId, deviceId) : deviceId);
            row.put("current", currentCount);
            row.put("prior", priorCount);
            row.put("diff", diff);
            movements.add(row);
        }
        movements.sort((a, b) -> Long.compare(
                Math.abs(((Number) b.get("diff")).longValue()), Math.abs(((Number) a.get("diff")).longValue())));
        return movements.subList(0, Math.min(2, movements.size()));
    }

    /** deviceId -> total (critical+high+medium+low) violation count, summed across every host
     *  that resolves to that device (a device can carry traffic under more than one client type/
     *  vendor host). */
    private static Map<String, Long> totalActivityByDevice(List<HostSeverityCount> counts) {
        Map<String, Long> byDevice = new HashMap<>();
        for (HostSeverityCount c : PostureService.safe(counts)) {
            if (c == null || c.getHost() == null) continue;
            String deviceId = AgenticObserveUtil.extractEndpointId(c.getHost());
            if (deviceId == null) continue;
            long total = c.getCritical() + c.getHigh() + c.getMedium() + c.getLow();
            byDevice.merge(deviceId, total, Long::sum);
        }
        return byDevice;
    }

    /** One drilldown row: a sub-score's id/label/weight plus either its value or (when it is
     *  null — see each sub-score method's own null contract) the same gap the composite already
     *  reports, repeated here so the row itself explains why it's dashless instead of forcing the
     *  reader back to the composite's aggregated hint. */
    private static BasicDBObject subScoreRow(String id, String label, int weightPercent, Double subScore,
                                              String gapSource, String gapReason, String gapImpact) {
        BasicDBObject row = new BasicDBObject();
        row.put("id", id);
        row.put("label", label);
        row.put("weight", weightPercent);
        List<Map<String, Object>> gaps = new ArrayList<>();
        if (subScore != null) {
            row.put("value", Math.round(subScore));
        } else {
            row.put("value", null);
            gaps.add(PostureService.gapRow(gapSource, gapReason, gapImpact));
        }
        row.put("dataGaps", gaps);
        return row;
    }

    /** Null when there is nothing to classify at all (an empty/misconfigured account) — distinct
     *  from a real 0, which means every discovered tool is sanctioned. */
    private static Double shadowAiExposureSubScore(InsightDataBundle bundle) {
        Map<String, String> remarksByService = InsightUtil.remarksByServiceName(bundle.auditRows);
        int total = 0, sanctioned = 0;
        for (ApiCollection c : PostureService.safe(bundle.collections)) {
            if (c == null || c.isDeactivated() || c.getHostName() == null) continue;
            total++;
            if (InsightUtil.governanceBucket(c, bundle.allowlistNamesLower, remarksByService) == GovernanceBucket.SANCTIONED) {
                sanctioned++;
            }
        }
        if (total == 0) return null;
        return ((total - sanctioned) * 100.0) / total;
    }

    /**
     * "What's driving this" for the Shadow AI sub-score above — every skill/MCP-server/vendor
     * collection NOT bucketed SANCTIONED (i.e. the exact set behind that sub-score's
     * "total - sanctioned" numerator), grouped by service identity (serviceNameOf — the same
     * name governanceBucket itself matches against the allowlist/audit rows), with the count of
     * distinct devices using it. deviceIdOf splits the collection's own hostName ("&lt;device&gt;.
     * &lt;client-type&gt;.&lt;vendor&gt;...") the same way threatActivityDeviceMovements splits a
     * HostSeverityCount's host — skills and MCP-servers/vendors all share this one hostName shape,
     * so one pass covers all three. Top 5 by device count.
     */
    private static List<BasicDBObject> shadowAiTopUnapprovedServices(InsightDataBundle bundle) {
        Map<String, String> remarksByService = InsightUtil.remarksByServiceName(bundle.auditRows);
        Map<String, Set<String>> devicesByService = new HashMap<>();
        Map<String, GovernanceBucket> bucketByService = new HashMap<>();
        for (ApiCollection c : PostureService.safe(bundle.collections)) {
            if (c == null || c.isDeactivated() || c.getHostName() == null) continue;
            GovernanceBucket bucket = InsightUtil.governanceBucket(c, bundle.allowlistNamesLower, remarksByService);
            if (bucket == GovernanceBucket.SANCTIONED) continue;
            // Same grouping name governanceBucket's own allowlist check uses — merges
            // "chatgpt.com"/"codex"/etc into "openai" the same way vendor risk's table does,
            // instead of showing every raw alias as its own row.
            String serviceName = InsightUtil.governanceGroupingName(c);
            if (serviceName == null) continue;
            String deviceId = InsightUtil.deviceIdOf(c);
            devicesByService.computeIfAbsent(serviceName, k -> new HashSet<>()).add(deviceId != null ? deviceId : "unknown");
            bucketByService.putIfAbsent(serviceName, bucket);
        }

        List<BasicDBObject> rows = new ArrayList<>();
        for (Map.Entry<String, Set<String>> e : devicesByService.entrySet()) {
            BasicDBObject row = new BasicDBObject();
            row.put("service", e.getKey());
            row.put("status", bucketByService.get(e.getKey()).name());
            row.put("deviceCount", e.getValue().size());
            rows.add(row);
        }
        rows.sort((a, b) -> Integer.compare(b.getInt("deviceCount"), a.getInt("deviceCount")));
        return rows.subList(0, Math.min(2, rows.size()));
    }

    /** Null only when no policy has PII detection configured at all. Zero matches with at least
     *  one PII policy configured is a real, good score — not excluded. `matches` is the caller's
     *  own already-computed {@code PostureService.matchedPolicyCounts(bundle)} — passed in rather
     *  than recomputed here, since compute()/computeBreakdown() both also need it for
     *  complianceGapsSubScore/complianceGapsByPolicy over the SAME bundle. */
    private static Double dlpIncidentsSubScore(InsightDataBundle bundle, List<PostureService.PolicyMatch> matches) {
        boolean anyPiiPolicyConfigured = false;
        for (GuardrailPolicies p : PostureService.safe(bundle.policies)) {
            if (p != null && InsightUtil.policyHasPiiDetection(p)) { anyPiiPolicyConfigured = true; break; }
        }
        if (!anyPiiPolicyConfigured) return null;

        long matched = 0, hardBlocked = 0;
        for (PostureService.PolicyMatch m : matches) {
            if (!InsightUtil.policyHasPiiDetection(m.policy)) continue;
            matched += m.count;
            if (InsightUtil.isBlockingPolicy(m.policy)) hardBlocked += m.count;
        }
        if (matched == 0) return 0.0; // policies exist and nothing matched — a real, good score
        return ((matched - hardBlocked) * 100.0) / matched;
    }

    /**
     * "What's driving this" for the DLP sub-score above — same shape as
     * {@link #threatActivityDeviceMovements} (per-device count diff between this window and the
     * immediately preceding one, top 5 by absolute change), but scoped to events matched to a
     * PII-detecting policy rather than every event. Needs the raw event lists (not
     * bundle.subCategoryCounts, which has no per-event host) — unlike complianceGapsSubScore,
     * which is aggregate-only and has no per-device breakdown. Empty when there's no prior window.
     */
    private static List<BasicDBObject> dlpDeviceMovements(List<DashboardMaliciousEvent> current,
                                                            List<DashboardMaliciousEvent> priorEvents,
                                                            List<GuardrailPolicies> policies,
                                                            Map<String, String> deviceIdToUsername) {
        if (priorEvents == null) return new ArrayList<>();

        Map<String, GuardrailPolicies> policyByNameLower = PostureService.policyByNameLower(policies);
        Map<String, Long> currentByDevice = piiEventCountByDevice(current, policyByNameLower);
        Map<String, Long> priorByDevice = piiEventCountByDevice(priorEvents, policyByNameLower);

        Set<String> deviceIds = new HashSet<>();
        deviceIds.addAll(currentByDevice.keySet());
        deviceIds.addAll(priorByDevice.keySet());

        List<BasicDBObject> movements = new ArrayList<>();
        for (String deviceId : deviceIds) {
            long currentCount = currentByDevice.getOrDefault(deviceId, 0L);
            long priorCount = priorByDevice.getOrDefault(deviceId, 0L);
            long diff = currentCount - priorCount;
            if (diff == 0) continue;
            BasicDBObject row = new BasicDBObject();
            row.put("deviceId", deviceId);
            row.put("username", deviceIdToUsername != null ? deviceIdToUsername.getOrDefault(deviceId, deviceId) : deviceId);
            row.put("current", currentCount);
            row.put("prior", priorCount);
            row.put("diff", diff);
            movements.add(row);
        }
        movements.sort((a, b) -> Long.compare(
                Math.abs(((Number) b.get("diff")).longValue()), Math.abs(((Number) a.get("diff")).longValue())));
        return movements.subList(0, Math.min(2, movements.size()));
    }

    /** deviceId -> count of events whose category resolves to a still-active, PII-detecting
     *  policy — the category-not-subCategory join documented on matchedPolicyCounts, applied
     *  per-event instead of to the pre-aggregated subCategoryCounts. */
    private static Map<String, Long> piiEventCountByDevice(List<DashboardMaliciousEvent> events,
                                                             Map<String, GuardrailPolicies> policyByNameLower) {
        Map<String, Long> byDevice = new HashMap<>();
        for (DashboardMaliciousEvent e : PostureService.safe(events)) {
            if (e == null || e.getCategory() == null || e.getHost() == null) continue;
            GuardrailPolicies p = policyByNameLower.get(e.getCategory().toLowerCase(Locale.ROOT));
            if (p == null || !InsightUtil.policyHasPiiDetection(p)) continue;
            String deviceId = AgenticObserveUtil.extractEndpointId(e.getHost());
            if (deviceId == null) continue;
            byDevice.merge(deviceId, 1L, Long::sum);
        }
        return byDevice;
    }

    /** Null only when the threat backend didn't respond. A confirmed quiet window (backend
     *  responded, zero activity) is a real, good score of 0. */
    private static Double threatActivitySubScore(InsightDataBundle bundle) {
        if (!bundle.threatBackendAvailable) return null;
        long critical = 0, total = 0;
        for (HostSeverityCount c : PostureService.safe(bundle.hostSeverityCounts)) {
            if (c == null) continue;
            critical += c.getCritical();
            total += c.getCritical() + c.getHigh() + c.getMedium() + c.getLow();
        }
        if (total == 0) return 0.0; // confirmed no threat activity — a real, good score
        return (critical * 100.0) / total;
    }

    // ── Vendor risk ──────────────────────────────────────────────────────────────
    //
    // Vendor here is the AI provider itself (claude, deepseek, chatgpt, ...), read off the
    // hostName of CONTEXT_SOURCE.ENDPOINT collections — the browser-extension / endpoint-shield
    // traffic. Those hosts are named "<device>.<client-type>.<vendor>...", where <client-type> is
    // "ai-agent" (a native AI agent) or "chrome" (the browser extension); the label right after
    // that is the vendor. This is a DIFFERENT collection set from bundle.collections (which is
    // scoped to whatever context this request is running under, normally AGENTIC) — the caller
    // fetches it separately under CONTEXT_SOURCE.ENDPOINT and passes it in.
    //
    // Each resolved collection gets a weight on a 0-5 "how much review has this vendor had" scale:
    //  - not in this account's allowlist: weight 3 (flat) — unreviewed is a real, moderate risk
    //    on its own, regardless of which specific vendor it is.
    //  - in the allowlist (approved), AND on a short hand-maintained list of vendors reported to
    //    train on user input by default (see KNOWN_RISKY_VENDORS below): weight 5 — approval
    //    doesn't erase that risk.
    //  - in the allowlist and not on that list (claude, chatgpt, ...): weight 0 — approval is
    //    enough.
    // The aggregate sub-score weights the two RISK CONDITIONS 75:25 — not-approved usage counts
    // for more because it is unreviewed by definition, vs. an approved-but-known-risky vendor
    // which is at least on record and could be a deliberate, accepted tradeoff:
    //   subScore = 0.75 * (share of resolved traffic on an unapproved vendor)
    //            + 0.25 * (share of resolved traffic on an approved-but-known-risky vendor)
    // This is a first-pass formula with real judgment calls (the risk list, the 75:25 split) —
    // documented in full here so it's auditable and easy to retune, not buried in arithmetic.

    private static final Map<String, Integer> KNOWN_RISKY_VENDORS = new HashMap<>();
    static {
        KNOWN_RISKY_VENDORS.put("deepseek", 5);
        KNOWN_RISKY_VENDORS.put("grok", 5);
        KNOWN_RISKY_VENDORS.put("qwen", 5);    // Alibaba
        KNOWN_RISKY_VENDORS.put("kimi", 5);    // Moonshot AI
        KNOWN_RISKY_VENDORS.put("doubao", 5);  // ByteDance
        KNOWN_RISKY_VENDORS.put("ernie", 5);   // Baidu
        KNOWN_RISKY_VENDORS.put("gemini", 5);  // Google — consumer Gemini apps, not Workspace/Vertex
        KNOWN_RISKY_VENDORS.put("meta", 5);    // Meta AI (WhatsApp/Instagram assistant)
    }
    private static final int UNAPPROVED_VENDOR_WEIGHT = 3;
    private static final double VENDOR_UNAPPROVED_SHARE_WEIGHT = 0.75;
    private static final double VENDOR_RISKY_APPROVED_SHARE_WEIGHT = 0.25;

    public  static final Map<String,String> DEFAULT_DATA_LEAVING_POLICIES = new HashMap<>();
    static {
        DEFAULT_DATA_LEAVING_POLICIES.put("Default-Source code detection", "Source Code");
        DEFAULT_DATA_LEAVING_POLICIES.put("Default-Financial Advice", "Financials");
        DEFAULT_DATA_LEAVING_POLICIES.put("Default-Customer PII", "Customer PII");
        DEFAULT_DATA_LEAVING_POLICIES.put("Default-Credentials Alert", "Credentials");
    }

    /** {approved, weight} for one vendor — extracted from vendorRiskAnalysis's own per-vendor loop
     *  so the risk-score breakdown's own L3 vendor profile badge can't disagree with the L2
     *  table's own weight column. Package-private (not private): called from PostureService via
     *  {@link #vendorRiskProfileDrill}. */
    static final class VendorTier {
        final boolean approved;
        final int weight;
        VendorTier(boolean approved, int weight) { this.approved = approved; this.weight = weight; }
    }

    /** deviceId -> [hostName, firstSeen] for one vendor's own endpoint collections — the exact
     *  same grouping vendorRiskDrill's own member level and vendorRiskProfileDrill each used to
     *  independently loop endpointCollections for (byte-for-byte identical filter/group logic),
     *  now computed once. Mirrors RiskScoreProfileDrillService#collectionsForTool's own "one
     *  membership test, reused by every caller" pattern for Shadow AI tools. */
    private static Map<String, Object[]> devicesForVendor(List<ApiCollection> endpointCollections, String vendor) {
        Map<String, Object[]> byDevice = new LinkedHashMap<>(); // deviceId -> [hostName, firstSeen]
        for (ApiCollection c : PostureService.safe(endpointCollections)) {
            if (c == null || c.isDeactivated()) continue;
            String v = InsightUtil.endpointVendorName(c);
            if (v == null || !v.equals(vendor)) continue;
            String deviceId = InsightUtil.deviceIdOf(c);
            if (deviceId == null) deviceId = "unknown";
            Object[] seen = byDevice.computeIfAbsent(deviceId, k -> new Object[]{c.getHostName(), 0});
            int firstSeen = (int) seen[1];
            if (c.getStartTs() > 0 && (firstSeen == 0 || c.getStartTs() < firstSeen)) seen[1] = c.getStartTs();
        }
        return byDevice;
    }

    static VendorTier vendorRiskTier(String vendor, Set<String> allowlistNamesLower) {
        // vendor is endpointVendorName's display-cased form ("OpenAI") — allowlistNamesLower is
        // strictly lowercase (see vendorRiskAnalysis's own note on this).
        boolean approved = allowlistNamesLower.contains(vendor.toLowerCase(Locale.ROOT));
        int weight = approved ? KNOWN_RISKY_VENDORS.getOrDefault(vendor, 0) : UNAPPROVED_VENDOR_WEIGHT;
        return new VendorTier(approved, weight);
    }

    private static final class VendorRiskAnalysis {
        final List<BasicDBObject> rows;
        final Double subScore;
        VendorRiskAnalysis(List<BasicDBObject> rows, Double subScore) {
            this.rows = rows;
            this.subScore = subScore;
        }
    }

    /** Null (both the table and the sub-score) only when there is no ENDPOINT traffic that
     *  resolves to a vendor at all — nothing to build a table from, not a real 0. */
    private static VendorRiskAnalysis vendorRiskAnalysis(List<ApiCollection> endpointCollections,
                                                          Set<String> allowlistNamesLower) {
        // Devices, not collections: "high users" is what actually makes a vendor risky (per-user
        // exposure), and one device can carry several collections for the same vendor across
        // sessions — counting collections would inflate a vendor's apparent reach.
        Map<String, Set<String>> devicesByVendor = new LinkedHashMap<>();
        for (ApiCollection c : PostureService.safe(endpointCollections)) {
            if (c == null || c.isDeactivated()) continue;
            String vendor = InsightUtil.endpointVendorName(c);
            if (vendor == null) continue;
            String deviceId = InsightUtil.deviceIdOf(c);
            devicesByVendor.computeIfAbsent(vendor, k -> new HashSet<>()).add(deviceId != null ? deviceId : "unknown");
        }
        if (devicesByVendor.isEmpty()) return new VendorRiskAnalysis(new ArrayList<>(), null);

        long total = 0, unapprovedCount = 0, riskyApprovedCount = 0;
        List<BasicDBObject> rows = new ArrayList<>();
        for (Map.Entry<String, Set<String>> e : devicesByVendor.entrySet()) {
            String vendor = e.getKey();
            long count = e.getValue().size();
            total += count;
            VendorTier tier = vendorRiskTier(vendor, allowlistNamesLower);
            boolean approved = tier.approved;
            int weight = tier.weight;
            if (!approved) unapprovedCount += count;
            else if (weight > 0) riskyApprovedCount += count;

            BasicDBObject row = new BasicDBObject();
            row.put("vendor", vendor);
            row.put("approved", approved);
            row.put("weight", weight);
            row.put("count", count);
            rows.add(row);
        }
        rows.sort((a, b) -> Long.compare(b.getLong("count"), a.getLong("count")));

        double unapprovedShare = (unapprovedCount * 100.0) / total;
        double riskyApprovedShare = (riskyApprovedCount * 100.0) / total;
        double subScore = VENDOR_UNAPPROVED_SHARE_WEIGHT * unapprovedShare
                + VENDOR_RISKY_APPROVED_SHARE_WEIGHT * riskyApprovedShare;
        return new VendorRiskAnalysis(rows, subScore);
    }

    /**
     * "What's driving this" for the Vendor risk sub-score above — the top 2 unapproved vendors by
     * distinct device count. Derived from vendorRiskAnalysis's own already-computed rows (each
     * already carries {@code approved} and a real distinct-device {@code count} — see that
     * method's own "Devices, not collections" note) rather than re-scanning endpointCollections a
     * second time for the identical vendor/device grouping computeBreakdown's own call site
     * already paid for. vendorAnalysis.rows is already sorted by count descending, so filtering
     * out the approved ones and taking the first 2 remaining is exactly "top 2 unapproved by
     * count" — no re-sort needed.
     */
    private static List<BasicDBObject> vendorRiskTopUnapprovedDevices(List<BasicDBObject> vendorAnalysisRows) {
        List<BasicDBObject> rows = new ArrayList<>();
        for (BasicDBObject r : PostureService.safe(vendorAnalysisRows)) {
            if (r == null || r.getBoolean("approved")) continue;
            BasicDBObject row = new BasicDBObject();
            row.put("vendor", r.getString("vendor"));
            row.put("deviceCount", (int) r.getLong("count"));
            rows.add(row);
            if (rows.size() >= 2) break;
        }
        return rows;
    }

    // ── Vendor risk drill ────────────────────────────────────────────────────────
    //
    // Lives here rather than in PostureService, next to vendorRiskAnalysis, which does the same
    // endpointCollections grouping this reuses rather than duplicating. See PostureService#fetchDrill
    // for the dispatch and PostureService.paginate/PostureDrillResult for the shared shape.

    static PostureDrillResult vendorRiskDrill(List<ApiCollection> endpointCollections, Set<String> allowlistNamesLower,
                                               Map<String, String> deviceIdToUsername, List<String> path,
                                               int skip, int limit) {
        PostureDrillResult result = new PostureDrillResult();

        if (path.isEmpty()) {
            result.setTitle("Vendor risk");
            result.getBreadcrumb().add(new PostureDrillResult.BreadcrumbItem("", "Vendor risk"));
            result.getColumns().add(new PostureDrillResult.ColumnDef("vendor", "Vendor"));
            result.getColumns().add(new PostureDrillResult.ColumnDef("approved", "Approved"));
            result.getColumns().add(new PostureDrillResult.ColumnDef("devices", "Devices"));
            result.getColumns().add(new PostureDrillResult.ColumnDef("weight", "Risk weight"));
            result.setDrillable(true);

            VendorRiskAnalysis analysis = vendorRiskAnalysis(endpointCollections, allowlistNamesLower);
            List<Map<String, Object>> rows = new ArrayList<>();
            for (BasicDBObject r : analysis.rows) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", r.getString("vendor"));
                row.put("vendor", r.getString("vendor"));
                row.put("approved", r.getBoolean("approved"));
                row.put("devices", r.getLong("count"));
                row.put("weight", r.getInt("weight"));
                rows.add(row);
            }
            PostureService.paginate(result, rows, skip, limit);
            if (rows.isEmpty()) {
                result.addDataGap(new InsightResult.Gap("VENDOR_RISK", PostureService.REASON_NO_ROWS,
                        "No endpoint traffic resolves to a known vendor yet."));
            }
            return result;
        }

        String vendor = path.get(0);
        result.setTitle(vendor + " — devices");
        result.getBreadcrumb().add(new PostureDrillResult.BreadcrumbItem("", "Vendor risk"));
        result.getBreadcrumb().add(new PostureDrillResult.BreadcrumbItem(vendor, vendor));
        result.getColumns().add(new PostureDrillResult.ColumnDef("device", "Device / user"));
        result.getColumns().add(new PostureDrillResult.ColumnDef("tool", "Tool / host"));
        result.getColumns().add(new PostureDrillResult.ColumnDef("firstSeen", "First seen"));
        result.setDrillable(false);

        Map<String, Object[]> byDevice = devicesForVendor(endpointCollections, vendor);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, Object[]> e : byDevice.entrySet()) {
            String deviceId = e.getKey();
            String display = deviceIdToUsername != null ? deviceIdToUsername.getOrDefault(deviceId, deviceId) : deviceId;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("device", display);
            row.put("tool", e.getValue()[0]);
            row.put("firstSeen", e.getValue()[1]);
            rows.add(row);
        }
        rows.sort((a, b) -> Integer.compare((int) b.get("firstSeen"), (int) a.get("firstSeen")));
        PostureService.paginate(result, rows, skip, limit);
        if (rows.isEmpty()) {
            result.addDataGap(new InsightResult.Gap("VENDOR_RISK", PostureService.REASON_NO_ROWS,
                    "No devices found for vendor \"" + vendor + "\"."));
        }
        return result;
    }

    /** Risk-score breakdown's own L3 for one vendor — reuses this same device grouping for the
     *  "Devices" section, plus a "Compliance findings" table (one row per policy whose category
     *  matched this vendor's traffic this window: Met when that policy has any compliance
     *  mapping, Gap when it doesn't — same policyHasComplianceMapping definition Framework
     *  readiness/Compliance gaps use) and event-count stats — all over the same all-window
     *  events/endpointCollections every other L3 handler shares, no new query. */
    static PostureDrillResult vendorRiskProfileDrill(List<ApiCollection> endpointCollections, Set<String> allowlistNamesLower,
                                                       Map<String, String> deviceIdToUsername,
                                                       List<HostSeverityCount> hostSeverityCounts,
                                                       List<DashboardMaliciousEvent> windowEvents,
                                                       List<GuardrailPolicies> policies, String vendor) {
        VendorTier tier = vendorRiskTier(vendor, allowlistNamesLower);
        String riskLabel = tier.weight >= 5 ? "High risk" : tier.weight >= 3 ? "Unapproved" : "Low risk";
        String tone = tier.weight >= 5 ? "critical" : tier.weight >= 3 ? "warning" : "success";

        Map<String, Object[]> byDevice = devicesForVendor(endpointCollections, vendor);
        int firstSeenOverall = 0;
        for (Object[] seen : byDevice.values()) {
            int fs = (int) seen[1];
            if (fs > 0 && (firstSeenOverall == 0 || fs < firstSeenOverall)) firstSeenOverall = fs;
        }

        List<DashboardMaliciousEvent> vendorEvents = new ArrayList<>();
        for (DashboardMaliciousEvent e : PostureService.safe(windowEvents)) {
            if (e == null || e.getHost() == null) continue;
            if (vendor.equals(InsightUtil.endpointVendorNameOfHost(e.getHost()))) vendorEvents.add(e);
        }
        vendorEvents.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        long criticalEvents = 0;
        for (DashboardMaliciousEvent e : vendorEvents) {
            if ("CRITICAL".equalsIgnoreCase(e.getSeverity())) criticalEvents++;
        }

        PostureDrillResult.Badge badge = new PostureDrillResult.Badge(riskLabel, tone);
        PostureDrillResult result = RiskScoreProfileDrillService.newProfileResult("Vendor risk", "vendorRisk", vendor, vendor,
                byDevice.size() + " device" + (byDevice.size() == 1 ? "" : "s") + " · "
                        + (tier.approved ? "Enterprise approved" : "Unapproved usage"), badge);
        result.setSeverity(RiskScoreProfileDrillService.worstSeverityOfEvents(vendorEvents));

        List<InsightResult.Metric> summary = new ArrayList<>();
        summary.add(new InsightResult.Metric("events", "Malicious events", (long) vendorEvents.size(), "count", String.valueOf(vendorEvents.size())));
        summary.add(new InsightResult.Metric("critical", "Critical", criticalEvents, "count", String.valueOf(criticalEvents)));
        result.setSummary(summary);

        List<PostureDrillResult.Fact> facts = new ArrayList<>();
        facts.add(new PostureDrillResult.Fact("Approved", tier.approved ? "Yes" : "No", tier.approved ? null : "critical"));
        boolean knownRisky = KNOWN_RISKY_VENDORS.containsKey(vendor);
        facts.add(new PostureDrillResult.Fact("Data handling",
                knownRisky ? "Reported to train on user input by default" : "No known training-on-input signal",
                knownRisky ? "critical" : null));
        facts.add(new PostureDrillResult.Fact("Devices affected", String.valueOf(byDevice.size()), null));
        facts.add(new PostureDrillResult.Fact("First seen", RiskScoreProfileDrillService.humanEpoch(firstSeenOverall), null));
        result.setFacts(facts);

        Map<String, GuardrailPolicies> policyByNameLower = PostureService.policyByNameLower(policies);
        Map<String, Long> countByPolicy = new LinkedHashMap<>();
        Map<String, GuardrailPolicies> policyObjByName = new LinkedHashMap<>();
        for (DashboardMaliciousEvent e : vendorEvents) {
            if (e.getCategory() == null) continue;
            GuardrailPolicies p = policyByNameLower.get(e.getCategory().toLowerCase(Locale.ROOT));
            if (p == null) continue;
            countByPolicy.merge(p.getName(), 1L, Long::sum);
            policyObjByName.putIfAbsent(p.getName(), p);
        }
        List<Map<String, Object>> findingRows = new ArrayList<>();
        for (Map.Entry<String, Long> e : countByPolicy.entrySet()) {
            GuardrailPolicies p = policyObjByName.get(e.getKey());
            boolean mapped = PostureService.policyHasComplianceMapping(p);
            findingRows.add(PostureService.row("policy", e.getKey(), "result", mapped ? "Met" : "Gap",
                    "evidence", e.getValue() + " event" + (e.getValue() == 1 ? "" : "s") + " this window"));
        }
        findingRows.sort((a, b) -> Boolean.compare(!"Gap".equals(a.get("result")), !"Gap".equals(b.get("result"))));
        List<PostureDrillResult.ColumnDef> findingColumns = Arrays.asList(
                new PostureDrillResult.ColumnDef("policy", "Policy"),
                new PostureDrillResult.ColumnDef("result", "Result"),
                new PostureDrillResult.ColumnDef("evidence", "Evidence"));
        result.getSections().add(RiskScoreProfileDrillService.tableSection("findings", "Compliance findings",
                "Policies matched on this vendor's traffic this window", findingColumns, findingRows));

        List<Map<String, Object>> deviceRows = new ArrayList<>();
        for (Map.Entry<String, Object[]> e : byDevice.entrySet()) {
            String deviceId = e.getKey();
            String display = deviceIdToUsername != null ? deviceIdToUsername.getOrDefault(deviceId, deviceId) : deviceId;
            deviceRows.add(PostureService.row("device", display, "tool", e.getValue()[0], "firstSeen", e.getValue()[1]));
        }
        deviceRows.sort((a, b) -> Integer.compare((int) b.get("firstSeen"), (int) a.get("firstSeen")));
        List<PostureDrillResult.ColumnDef> deviceColumns = Arrays.asList(
                new PostureDrillResult.ColumnDef("device", "Device / user"),
                new PostureDrillResult.ColumnDef("tool", "Tool / host"),
                new PostureDrillResult.ColumnDef("firstSeen", "First seen"));
        result.getSections().add(RiskScoreProfileDrillService.tableSection("devices", "Devices", null, deviceColumns, deviceRows));

        result.getCtas().add(new InsightResult.Cta("openFullView", "Open in Guardrail violations", "NAVIGATE",
                InsightRoutes.GUARDRAIL_VIOLATIONS, null, false));
        if (byDevice.isEmpty() && vendorEvents.isEmpty()) {
            result.addDataGap(new InsightResult.Gap("VENDOR_RISK", PostureService.REASON_NO_ROWS,
                    "No devices or activity found for vendor \"" + vendor + "\"."));
        }
        return result;
    }

    // ── Compliance gaps ──────────────────────────────────────────────────────────
    //
    // Event-count-weighted mean of "did this event's policy map to a compliance framework",
    // inverted to a gap (100 - coverage) — over the same matchedPolicyCounts join (category ->
    // GuardrailPolicies by name) DLP/enforcementFunnel/dataLeaving already share, and the same
    // "covered" definition Framework readiness uses (PostureService#policyHasComplianceMapping:
    // policy active, its LLM rule enabled, and mapped to at least one framework in
    // llmRule.compliance) — so the two panels can't disagree about what counts as covered.
    // Previously sourced from a separate ThreatComplianceInfo/"threat_compliance/<filterId>.conf"
    // mapping unrelated to the account's actual GuardrailPolicies compliance tags; replaced so
    // this sub-score reflects the same real per-account data Framework readiness does.

    /** Null only when no policy maps to a compliance framework at all (nothing to assess — same
     *  data-gap condition Framework readiness reports). Zero matched events with at least one
     *  compliance-mapped policy configured is a real, good score — not excluded. Mirrors
     *  dlpIncidentsSubScore's shape exactly, over the same matchedPolicyCounts join — `matches`
     *  is the caller's own already-computed one, same reasoning as dlpIncidentsSubScore's. */
    private static Double complianceGapsSubScore(InsightDataBundle bundle, List<PostureService.PolicyMatch> matches) {
        boolean anyPolicyMapsToCompliance = false;
        for (GuardrailPolicies p : PostureService.safe(bundle.policies)) {
            if (PostureService.policyHasComplianceMapping(p)) { anyPolicyMapsToCompliance = true; break; }
        }
        if (!anyPolicyMapsToCompliance) return null;

        long matched = 0, uncovered = 0;
        for (PostureService.PolicyMatch m : matches) {
            matched += m.count;
            if (!PostureService.policyHasComplianceMapping(m.policy)) uncovered += m.count;
        }
        if (matched == 0) return 0.0; // compliance-mapped policies exist and nothing matched — a real, good score
        return (uncovered * 100.0) / matched;
    }

    /**
     * "What's driving this" for the Compliance gaps sub-score above — the uncovered matches
     * (same matchedPolicyCounts join), grouped by policy name rather than by device: a match
     * with no compliance mapping isn't attributable to one device the way a threat-activity or
     * DLP hit is, but it IS attributable to the policy that fired it. Top 2 policies by
     * uncovered count.
     */
    private static List<BasicDBObject> complianceGapsByPolicy(List<PostureService.PolicyMatch> matches) {
        List<BasicDBObject> rows = complianceGapsByPolicyAll(matches);
        return rows.subList(0, Math.min(2, rows.size()));
    }

    /** Uncapped core of {@link #complianceGapsByPolicy} — every policy with at least one
     *  compliance-uncovered match this window, not just the top 2. Feeds the risk-score
     *  breakdown's own "Compliance gaps" drilldown level (see
     *  PostureService#fetchRiskScoreDrill) — the top-2 version above stays capped for the
     *  existing inline "what's driving this" hint line. Takes the already-computed
     *  {@code matches} rather than a bundle, same dedup as complianceGapsSubScore's own. */
    static List<BasicDBObject> complianceGapsByPolicyAll(List<PostureService.PolicyMatch> matches) {
        Map<String, Long> uncoveredCountByPolicy = new HashMap<>();
        for (PostureService.PolicyMatch m : matches) {
            if (PostureService.policyHasComplianceMapping(m.policy)) continue;
            uncoveredCountByPolicy.merge(m.policy.getName(), m.count, Long::sum);
        }

        List<BasicDBObject> rows = new ArrayList<>();
        for (Map.Entry<String, Long> e : uncoveredCountByPolicy.entrySet()) {
            BasicDBObject row = new BasicDBObject();
            row.put("policy", e.getKey());
            row.put("count", e.getValue());
            rows.add(row);
        }
        rows.sort((a, b) -> Long.compare(b.getLong("count"), a.getLong("count")));
        return rows;
    }

    /**
     * Full (uncapped) per-device PII-incident count for the current window — the risk-score
     * breakdown's own "DLP incidents" drilldown level. Deliberately simpler than
     * {@link #dlpDeviceMovements}: that method needs a prior window to compute a diff (and is
     * capped to 2 for the inline hint line); fetchPostureDrill's DRILL_RISK_SCORE branch doesn't
     * thread a "which specific device moved" comparison this deep, so this is just "how many PII
     * incidents does each device have right now", same {@link #piiEventCountByDevice} join, every
     * device, sorted worst-first.
     */
    static List<BasicDBObject> dlpIncidentsAllDevices(List<DashboardMaliciousEvent> events,
                                                        List<GuardrailPolicies> policies,
                                                        Map<String, String> deviceIdToUsername) {
        Map<String, GuardrailPolicies> policyByNameLower = PostureService.policyByNameLower(policies);
        Map<String, Long> byDevice = piiEventCountByDevice(events, policyByNameLower);

        List<BasicDBObject> rows = new ArrayList<>();
        for (Map.Entry<String, Long> e : byDevice.entrySet()) {
            BasicDBObject row = new BasicDBObject();
            row.put("deviceId", e.getKey());
            row.put("username", deviceIdToUsername != null ? deviceIdToUsername.getOrDefault(e.getKey(), e.getKey()) : e.getKey());
            row.put("incidents", e.getValue());
            rows.add(row);
        }
        rows.sort((a, b) -> Long.compare(b.getLong("incidents"), a.getLong("incidents")));
        return rows;
    }

    /**
     * Full (uncapped) per-device current-window violation count — the risk-score breakdown's own
     * "Threat activity" drilldown level. Same {@link #totalActivityByDevice} grouping
     * {@link #threatActivityDeviceMovements} uses, but a current-period snapshot rather than a
     * window-over-window diff (no prior-window comparison this deep), every device, sorted
     * worst-first.
     */
    static List<BasicDBObject> threatActivityAllDevices(List<HostSeverityCount> current,
                                                          Map<String, String> deviceIdToUsername) {
        Map<String, Long> byDevice = totalActivityByDevice(current);

        List<BasicDBObject> rows = new ArrayList<>();
        for (Map.Entry<String, Long> e : byDevice.entrySet()) {
            BasicDBObject row = new BasicDBObject();
            row.put("deviceId", e.getKey());
            row.put("username", deviceIdToUsername != null ? deviceIdToUsername.getOrDefault(e.getKey(), e.getKey()) : e.getKey());
            row.put("violations", e.getValue());
            rows.add(row);
        }
        rows.sort((a, b) -> Long.compare(b.getLong("violations"), a.getLong("violations")));
        return rows;
    }
}

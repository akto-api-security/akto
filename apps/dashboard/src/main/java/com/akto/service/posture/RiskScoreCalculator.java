package com.akto.service.posture;

import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.action.threat_detection.HostSeverityCount;
import com.akto.dto.ApiCollection;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.threat_detection.ThreatComplianceInfo;
import com.akto.service.insights.InsightDataBundle;
import com.akto.service.insights.InsightUtil;
import com.akto.service.insights.InsightUtil.GovernanceBucket;
import com.mongodb.BasicDBObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
 * at all, no resolvable-vendor endpoint traffic, no malicious events to check compliance mapping
 * on, or the threat backend didn't respond. A confirmed zero (PII policies exist but nothing
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

    static BasicDBObject compute(InsightDataBundle bundle, List<ApiCollection> endpointCollections,
                                  List<DashboardMaliciousEvent> allThreats,
                                  Map<String, ThreatComplianceInfo> threatComplianceMap) {
        Double shadowAiSubScore = shadowAiExposureSubScore(bundle);
        Double dlpSubScore = dlpIncidentsSubScore(bundle);
        Double threatSubScore = threatActivitySubScore(bundle);
        VendorRiskAnalysis vendorAnalysis = vendorRiskAnalysis(endpointCollections, bundle.allowlistNamesLower);
        Double complianceSubScore = complianceGapsSubScore(allThreats, threatComplianceMap);

        double weightedSum = 0;
        double coveredWeight = 0;
        if (shadowAiSubScore != null) { weightedSum += WEIGHT_SHADOW_AI * shadowAiSubScore; coveredWeight += WEIGHT_SHADOW_AI; }
        if (dlpSubScore != null) { weightedSum += WEIGHT_DLP * dlpSubScore; coveredWeight += WEIGHT_DLP; }
        if (vendorAnalysis.subScore != null) { weightedSum += WEIGHT_VENDOR * vendorAnalysis.subScore; coveredWeight += WEIGHT_VENDOR; }
        if (complianceSubScore != null) { weightedSum += WEIGHT_COMPLIANCE * complianceSubScore; coveredWeight += WEIGHT_COMPLIANCE; }
        if (threatSubScore != null) { weightedSum += WEIGHT_THREAT * threatSubScore; coveredWeight += WEIGHT_THREAT; }

        BasicDBObject kpi = PostureService.kpi(PostureService.KPI_RISK_SCORE, "AI risk score", null, null, null);
        kpi.put("unit", "score");
        kpi.put("vendorTable", vendorAnalysis.rows);

        int weightCoveredPercent = (int) Math.round(coveredWeight * 100);
        kpi.put("weightCovered", weightCoveredPercent);

        if (coveredWeight > 0) {
            kpi.put("value", Math.round(weightedSum / coveredWeight)); // renormalized back to 0-100
            if (weightCoveredPercent < 100) {
                kpi.put("footnote", "Computed from " + weightCoveredPercent + "% of the full composite");
            }
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
            PostureService.addGap(kpi, "COMPLIANCE_GAPS", PostureService.REASON_NO_ROWS,
                    "No malicious events in this window (or no compliance mapping loaded), so "
                            + "compliance gaps can't be scored.");
        }
        if (threatSubScore == null) {
            PostureService.addGap(kpi, PostureService.GAP_THREAT_BACKEND, PostureService.REASON_REQUEST_FAILED,
                    PostureService.THREAT_BACKEND_DOWN_IMPACT);
        }
        PostureService.addGap(kpi, PostureService.GAP_POSTURE_HISTORY, PostureService.REASON_NOT_CONFIGURED,
                "This is a point-in-time score. There's no history yet, so the week-over-week change "
                        + "and the 12-week trend aren't available.");

        // The drilldown's own row-per-sub-score view: the same five numbers above, broken out
        // rather than collapsed into one composite, so the flyout can show what's actually driving
        // the number instead of just re-showing it. No deltas/trend here either — same reason as
        // the top-level GAP_POSTURE_HISTORY gap above.
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
                "COMPLIANCE_GAPS", PostureService.REASON_NO_ROWS,
                "No malicious events in this window (or no compliance mapping loaded), so "
                        + "compliance gaps can't be scored."));
        subScores.add(subScoreRow("threatActivity", "Threat activity", 10, threatSubScore,
                PostureService.GAP_THREAT_BACKEND, PostureService.REASON_REQUEST_FAILED,
                PostureService.THREAT_BACKEND_DOWN_IMPACT));
        kpi.put("subScores", subScores);

        return kpi;
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

    /** Null only when no policy has PII detection configured at all. Zero matches with at least
     *  one PII policy configured is a real, good score — not excluded. */
    private static Double dlpIncidentsSubScore(InsightDataBundle bundle) {
        boolean anyPiiPolicyConfigured = false;
        for (GuardrailPolicies p : PostureService.safe(bundle.policies)) {
            if (p != null && InsightUtil.policyHasPiiDetection(p)) { anyPiiPolicyConfigured = true; break; }
        }
        if (!anyPiiPolicyConfigured) return null;

        long matched = 0, hardBlocked = 0;
        for (PostureService.PolicyMatch m : PostureService.matchedPolicyCounts(bundle)) {
            if (!InsightUtil.policyHasPiiDetection(m.policy)) continue;
            matched += m.count;
            if (InsightUtil.isBlockingPolicy(m.policy)) hardBlocked += m.count;
        }
        if (matched == 0) return 0.0; // policies exist and nothing matched — a real, good score
        return ((matched - hardBlocked) * 100.0) / matched;
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
        Map<String, Long> countByVendor = new LinkedHashMap<>();
        for (ApiCollection c : PostureService.safe(endpointCollections)) {
            if (c == null || c.isDeactivated()) continue;
            String vendor = InsightUtil.endpointVendorName(c);
            if (vendor == null) continue;
            countByVendor.merge(vendor, 1L, Long::sum);
        }
        if (countByVendor.isEmpty()) return new VendorRiskAnalysis(new ArrayList<>(), null);

        long total = 0, unapprovedCount = 0, riskyApprovedCount = 0;
        List<BasicDBObject> rows = new ArrayList<>();
        for (Map.Entry<String, Long> e : countByVendor.entrySet()) {
            String vendor = e.getKey();
            long count = e.getValue();
            total += count;
            boolean approved = allowlistNamesLower.contains(vendor);
            int weight = approved ? KNOWN_RISKY_VENDORS.getOrDefault(vendor, 0) : UNAPPROVED_VENDOR_WEIGHT;
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

    // ── Compliance gaps ──────────────────────────────────────────────────────────
    //
    // Event-count-weighted mean of "did this event's policy map to any compliance standard at
    // all", inverted to a gap (100 - coverage): for every malicious event with a filterId, look
    // up threatComplianceMap (the same "threat_compliance/<filterId>.conf" key
    // GuardrailMetricsProcessor already uses to build the Compliance-at-risk breakdown) and check
    // whether it maps to at least one compliance standard. The share that do NOT is the gap.
    // Deliberately not scoped to label=GUARDRAIL — a "guardrail policy" is read here as any
    // filter/policy that can fire on traffic, matching how this codebase already uses the term
    // loosely elsewhere (AgenticDashboardAction's "guardrail" dashboard mixes both labels too).

    /** Null when there are no malicious events with a filterId in this window (nothing to assess)
     *  or the compliance map failed to load (never treat "we don't know" as "100% uncovered"). */
    private static Double complianceGapsSubScore(List<DashboardMaliciousEvent> allThreats,
                                                  Map<String, ThreatComplianceInfo> threatComplianceMap) {
        if (threatComplianceMap == null || threatComplianceMap.isEmpty()) return null;
        long total = 0, uncovered = 0;
        for (DashboardMaliciousEvent event : PostureService.safe(allThreats)) {
            if (event == null) continue;
            String filterId = event.getFilterId();
            if (filterId == null || filterId.isEmpty()) continue;
            total++;
            ThreatComplianceInfo info = threatComplianceMap.get("threat_compliance/" + filterId + ".conf");
            boolean covered = info != null && info.getMapComplianceToListClauses() != null
                    && !info.getMapComplianceToListClauses().isEmpty();
            if (!covered) uncovered++;
        }
        if (total == 0) return null;
        return (uncovered * 100.0) / total;
    }
}

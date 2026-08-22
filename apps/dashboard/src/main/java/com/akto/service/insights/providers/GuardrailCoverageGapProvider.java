package com.akto.service.insights.providers;

import com.akto.action.threat_detection.HostSeverityCount;
import com.akto.dto.ApiCollection;
import com.akto.dto.GuardrailPolicies;
import com.akto.service.insights.*;
import com.akto.util.AgenticObserveUtil;

import java.util.*;

/** Active surfaces with no covering policy, plus non-blocking policies that have real hits. */
public class GuardrailCoverageGapProvider extends AbstractInsightProvider {

    public GuardrailCoverageGapProvider() { super(InsightId.GUARDRAIL_COVERAGE_GAP, 1); }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, Scope scope) {
        InsightResult r = skeleton();

        List<ApiCollection> surfaces = new ArrayList<>();
        for (ApiCollection c : bundle.collections) {
            if (c.isDeactivated() || c.getHostName() == null) continue;
            String type = AgenticObserveUtil.getTypeFromCollection(c);
            if (AgenticObserveUtil.CLIENT_TYPE_MCP_SERVER.equals(type) || AgenticObserveUtil.CLIENT_TYPE_AI_AGENT.equals(type)
                    || AgenticObserveUtil.CLIENT_TYPE_LLM.equals(type) || AgenticObserveUtil.CLIENT_TYPE_SAAS_AGENT.equals(type)) {
                surfaces.add(c);
            }
        }

        List<GuardrailPolicies> policies = bundle.policies;
        List<ApiCollection> uncovered = new ArrayList<>();
        List<ApiCollection> coveredNonBlockingOnly = new ArrayList<>();

        for (ApiCollection c : surfaces) {
            boolean blockingCovered = false, anyCovered = false;
            for (GuardrailPolicies p : policies) {
                if (!InsightUtil.policyCoversCollection(p, p.getApplyToDeviceIds(), c)) continue;
                anyCovered = true;
                if (InsightUtil.isBlockingPolicy(p)) blockingCovered = true;
            }
            if (!anyCovered) uncovered.add(c);
            else if (!blockingCovered) coveredNonBlockingOnly.add(c);
        }

        int nonBlockingWithHits = 0;
        if (bundle.threatBackendAvailable) {
            Set<String> hostsWithHits = new HashSet<>();
            for (HostSeverityCount h : bundle.hostSeverityCounts) {
                if (h.getCritical() + h.getHigh() + h.getMedium() + h.getLow() > 0) hostsWithHits.add(h.getHost());
            }
            for (ApiCollection c : coveredNonBlockingOnly) {
                if (hostsWithHits.contains(c.getHostName())) nonBlockingWithHits++;
            }
        }

        int total = surfaces.size();
        double gapRatio = total > 0 ? (double) uncovered.size() / total : 0;

        r.addMetric(new InsightResult.Metric("activeSurfaces", "Active agentic surfaces", total, "count", InsightUtil.count(total, "surfaces")));
        r.addMetric(new InsightResult.Metric("uncoveredCount", "Uncovered surfaces", uncovered.size(), (long) total, "count",
                InsightUtil.ofTotal(uncovered.size(), total, "surfaces"), null));
        r.addMetric(new InsightResult.Metric("uncoveredRatio", "Uncovered ratio", gapRatio, "percent", InsightUtil.percent(gapRatio)));
        r.addMetric(new InsightResult.Metric("nonBlockingWithHits", "Non-blocking policies with real hits", nonBlockingWithHits, "count",
                InsightUtil.count(nonBlockingWithHits, "surfaces")));

        r.setStatus((total == 0 ? InsightResult.Status.NO_DATA
                : !bundle.threatBackendAvailable ? InsightResult.Status.PARTIAL
                : InsightResult.Status.READY).name());
        if (!bundle.threatBackendAvailable) {
            r.addDataGap(new InsightResult.Gap("THREAT_BACKEND", "REQUEST_FAILED", "Violation data unavailable; \"non-blocking policies with real hits\" could not be computed."));
        }
        r.setHeadline(total == 0 ? "No active agentic surfaces found"
                : InsightUtil.percent(gapRatio) + " of active surfaces have no covering guardrail policy");

        List<Map<String, Object>> rows = new ArrayList<>();
        for (ApiCollection c : uncovered.subList(0, Math.min(20, uncovered.size()))) {
            Map<String, Object> row = new HashMap<>();
            row.put("host", c.getHostName());
            row.put("type", AgenticObserveUtil.getTypeFromCollection(c));
            rows.add(row);
        }
        r.addEvidence(new InsightResult.Evidence("uncovered", "Uncovered surfaces", Arrays.asList("host", "type"), rows, uncovered.size()));

        r.addCta(new InsightResult.Cta("create_policy", "Apply a guardrail policy", "NAVIGATE", InsightRoutes.GUARDRAIL_POLICIES, new HashMap<>(), true));
        r.addCta(new InsightResult.Cta("view_misconfigurations", "View misconfigurations", "NAVIGATE", InsightRoutes.GUARDRAIL_MISCONFIGURATIONS, new HashMap<>(), false));
        return r;
    }
}

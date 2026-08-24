package com.akto.service.insights.providers;

import com.akto.action.threat_detection.HostSeverityCount;
import com.akto.dto.ApiCollection;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.agentic_sessions.UserAnalysisData;
import com.akto.service.insights.*;
import com.akto.util.AgenticObserveUtil;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

/**
 * Active surfaces with no covering policy, plus non-blocking policies that have real hits.
 * Uncovered surfaces are cross-referenced against user-analysis (keyed by serviceId/deviceId,
 * both of which are substrings of the surface's ApiCollection.hostName) for real harmful-topic
 * activity — those are the highest-priority gaps. For the riskiest one (DETAIL scope only,
 * one LLM call), the primary CTA is a GUARDRAIL_TEMPLATE prefilled with an AI-suggested policy
 * (GuardrailPolicies-shaped: name/description/severity/behaviour/deniedTopics/selected servers)
 * instead of a blank "go create a policy" link.
 */
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

        // Uncovered surfaces with real harmful-topic activity (user-analysis, matched via
        // serviceId/deviceId both being substrings of the collection's hostName) — the
        // highest-priority gaps, since they're not just unconfigured but actively risky.
        Map<ApiCollection, List<String>> harmfulSummariesByUncovered = new LinkedHashMap<>();
        for (ApiCollection c : uncovered) {
            List<String> summaries = harmfulTopicSummariesFor(bundle.userAnalysis, c);
            if (!summaries.isEmpty()) harmfulSummariesByUncovered.put(c, summaries);
        }

        int total = surfaces.size();
        double gapRatio = total > 0 ? (double) uncovered.size() / total : 0;

        r.addMetric(new InsightResult.Metric("activeSurfaces", "Active agentic surfaces", total, "count", InsightUtil.count(total, "surfaces")));
        r.addMetric(new InsightResult.Metric("uncoveredCount", "Uncovered surfaces", uncovered.size(), (long) total, "count",
                InsightUtil.ofTotal(uncovered.size(), total, "surfaces"), null));
        r.addMetric(new InsightResult.Metric("uncoveredRatio", "Uncovered ratio", gapRatio, "percent", InsightUtil.percent(gapRatio)));
        r.addMetric(new InsightResult.Metric("nonBlockingWithHits", "Non-blocking policies with real hits", nonBlockingWithHits, "count",
                InsightUtil.count(nonBlockingWithHits, "surfaces")));
        r.addMetric(new InsightResult.Metric("highRiskUncovered", "Uncovered surfaces with harmful-topic activity", harmfulSummariesByUncovered.size(),
                "count", InsightUtil.count(harmfulSummariesByUncovered.size(), "surfaces")));

        r.setStatus((total == 0 ? InsightResult.Status.NO_DATA
                : !bundle.threatBackendAvailable ? InsightResult.Status.PARTIAL
                : InsightResult.Status.READY).name());
        if (!bundle.threatBackendAvailable) {
            r.addDataGap(new InsightResult.Gap("THREAT_BACKEND", "REQUEST_FAILED", "Violation data unavailable; \"non-blocking policies with real hits\" could not be computed."));
        }
        r.setHeadline(total == 0 ? "No active agentic surfaces found"
                : InsightUtil.percent(gapRatio) + " of active surfaces have no covering guardrail policy"
                        + (harmfulSummariesByUncovered.isEmpty() ? "" : ", " + InsightUtil.count(harmfulSummariesByUncovered.size(), "of them") + " with real harmful-topic activity"));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (ApiCollection c : uncovered.subList(0, Math.min(20, uncovered.size()))) {
            Map<String, Object> row = new HashMap<>();
            row.put("host", c.getHostName());
            row.put("type", AgenticObserveUtil.getTypeFromCollection(c));
            row.put("harmfulTopicActivity", harmfulSummariesByUncovered.containsKey(c));
            rows.add(row);
        }
        r.addEvidence(new InsightResult.Evidence("uncovered", "Uncovered surfaces", Arrays.asList("host", "type", "harmfulTopicActivity"), rows, uncovered.size()));

        r.addCta(buildCreatePolicyCta(scope, harmfulSummariesByUncovered));
        r.addCta(new InsightResult.Cta("view_misconfigurations", "View misconfigurations", "NAVIGATE", InsightRoutes.GUARDRAIL_MISCONFIGURATIONS, new HashMap<>(), false));
        return r;
    }

    /** The riskiest uncovered surface (most harmful-topic hits) gets an AI-prefilled GUARDRAIL_TEMPLATE CTA; everyone else gets a blank NAVIGATE. */
    private InsightResult.Cta buildCreatePolicyCta(Scope scope, Map<ApiCollection, List<String>> harmfulSummariesByUncovered) {
        ApiCollection topRisk = null;
        List<String> topSummaries = null;
        for (Map.Entry<ApiCollection, List<String>> e : harmfulSummariesByUncovered.entrySet()) {
            if (topSummaries == null || e.getValue().size() > topSummaries.size()) {
                topRisk = e.getKey();
                topSummaries = e.getValue();
            }
        }

        if (scope != Scope.DETAIL || topRisk == null) {
            return new InsightResult.Cta("create_policy", "Apply a guardrail policy", "NAVIGATE", InsightRoutes.GUARDRAIL_POLICIES, new HashMap<>(), true);
        }

        Map<String, Object> params = new HashMap<>(InsightClassificationHelper.suggestGuardrail(topRisk.getHostName(), topSummaries));
        if (params.isEmpty()) {
            return new InsightResult.Cta("create_policy", "Apply a guardrail policy", "NAVIGATE", InsightRoutes.GUARDRAIL_POLICIES, new HashMap<>(), true);
        }

        params.put("applyOnRequest", true);
        params.put("applyOnResponse", true);
        params.put("applyToAllServers", false);
        Map<String, String> server = new HashMap<>();
        server.put("id", topRisk.getHostName());
        server.put("name", topRisk.getHostName());
        String type = AgenticObserveUtil.getTypeFromCollection(topRisk);
        if (AgenticObserveUtil.CLIENT_TYPE_MCP_SERVER.equals(type)) {
            params.put("selectedMcpServersV2", Collections.singletonList(server));
        } else {
            params.put("selectedAgentServersV2", Collections.singletonList(server));
        }

        return new InsightResult.Cta("create_policy", "Create guardrail for " + topRisk.getHostName(),
                "GUARDRAIL_TEMPLATE", InsightRoutes.GUARDRAIL_POLICIES, params, true);
    }

    /** "topic: lastReason" for every harmful topic seen on user-analysis rows whose serviceId AND deviceId are both substrings of the collection's hostName. */
    private static List<String> harmfulTopicSummariesFor(List<UserAnalysisData> userAnalysis, ApiCollection c) {
        List<String> summaries = new ArrayList<>();
        String host = c.getHostName();
        if (host == null) return summaries;

        for (UserAnalysisData d : userAnalysis) {
            if (d.getId() == null) continue;
            String serviceId = d.getId().getServiceId();
            String deviceId = d.getId().getDeviceId();
            if (StringUtils.isBlank(serviceId) || StringUtils.isBlank(deviceId)) continue;
            if (!host.contains(serviceId) || !host.contains(deviceId)) continue;

            Map<String, Object> harmfulTopics = d.getHarmfulTopics();
            if (harmfulTopics == null) continue;
            for (Map.Entry<String, Object> e : harmfulTopics.entrySet()) {
                Object topicData = e.getValue();
                String reason = "";
                if (topicData instanceof Map) {
                    Object r = ((Map<?, ?>) topicData).get("lastReason");
                    if (r != null) reason = String.valueOf(r);
                }
                summaries.add(e.getKey() + ": " + reason);
            }
        }
        return summaries;
    }
}

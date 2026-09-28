package com.akto.service.insights.providers.agentic;

import com.akto.action.threat_detection.HostSeverityCount;
import com.akto.service.insights.AbstractInsightProvider;
import com.akto.service.insights.InsightContext;
import com.akto.service.insights.InsightDataBundle;
import com.akto.service.insights.InsightId;
import com.akto.service.insights.InsightResult;
import com.akto.service.insights.InsightRoutes;
import com.akto.service.insights.InsightUtil;
import com.akto.service.insights.agentic.AgentIndex;
import com.akto.service.insights.agentic.AgentRef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agents generating the most guardrail activity in the window — hostSeverityCounts (already
 * loaded for every context, real per-host critical/high/medium/low counts from the threat
 * backend) attributed to an agent via AgentIndex#agentForHost's exact/loose/claude-config join,
 * the same one AgenticObserveAction's own violation-count attribution uses.
 */
public class AgentGuardrailHotspotsProvider extends AbstractInsightProvider {

    private static final int MAX_FINDINGS = 10;

    public AgentGuardrailHotspotsProvider() { super(InsightId.AGENT_GUARDRAIL_HOTSPOTS, 1); }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, Scope scope) {
        InsightResult r = skeleton();
        if (!bundle.agentic.loaded) {
            r.addDataGap(new InsightResult.Gap("GUARDRAIL_POLICIES", "NOT_CONFIGURED",
                    "Guardrail activity is unavailable outside the Argus (agentic) dashboard context."));
            r.setHeadline("Guardrail activity is unavailable here.");
            return r;
        }
        if (!bundle.threatBackendAvailable) {
            r.addDataGap(new InsightResult.Gap("THREAT_BACKEND", "REQUEST_FAILED",
                    "Guardrail activity counts could not be fetched from the threat backend for this window."));
        }

        AgentIndex agentIndex = bundle.agentic.agentIndex;
        Map<Integer, int[]> countsByAgent = new HashMap<>(); // [critical, high, medium, low]
        for (HostSeverityCount hsc : bundle.hostSeverityCounts) {
            AgentRef agent = agentIndex.agentForHost(hsc.getHost());
            if (agent == null) continue;
            int[] counts = countsByAgent.computeIfAbsent(agent.getCollectionId(), k -> new int[4]);
            counts[0] += hsc.getCritical();
            counts[1] += hsc.getHigh();
            counts[2] += hsc.getMedium();
            counts[3] += hsc.getLow();
        }

        if (countsByAgent.isEmpty()) {
            r.setStatus(InsightResult.Status.NO_DATA.name());
            r.setHeadline("No guardrail activity attributed to an agent in this window.");
            return r;
        }

        List<Integer> ranked = new ArrayList<>(countsByAgent.keySet());
        ranked.sort(Comparator.comparingInt((Integer id) -> -(countsByAgent.get(id)[0] * 1000 + countsByAgent.get(id)[1] * 100
                + countsByAgent.get(id)[2] * 10 + countsByAgent.get(id)[3])));

        int emitted = 0;
        boolean anyCritical = false;
        for (Integer collectionId : ranked) {
            if (emitted >= MAX_FINDINGS) break;
            AgentRef agent = agentIndex.agentFor(collectionId);
            if (agent == null) continue;
            int[] counts = countsByAgent.get(collectionId);
            int total = counts[0] + counts[1] + counts[2] + counts[3];
            String severity = counts[0] > 0 ? "CRITICAL" : counts[1] > 0 ? "HIGH" : counts[2] > 0 ? "MEDIUM" : "LOW";
            if ("CRITICAL".equals(severity)) anyCritical = true;

            InsightResult.Finding finding = new InsightResult.Finding();
            finding.setId(InsightId.AGENT_GUARDRAIL_HOTSPOTS.name() + "|" + collectionId);
            finding.setSeverity(severity);
            finding.setAgentName(agent.getName());
            finding.setApiCollectionId(collectionId);
            finding.setEnvironment(agent.getEnvironment());
            finding.setResource(agent.getHostName());
            finding.setTitle(agent.getName() + " triggered " + InsightUtil.count(total, "guardrail hit") + " this window");
            finding.setWhyItMatters(counts[0] > 0
                    ? agent.getName() + " had " + InsightUtil.count(counts[0], "CRITICAL guardrail hit") + ", the most of any agent."
                    : agent.getName() + " is the busiest agent for guardrail activity this window.");
            finding.setRemediation("Review " + agent.getName() + "'s guardrail activity and confirm the matched policy/policies are correctly tuned.");
            Map<String, Object> ctaParams = new HashMap<>();
            ctaParams.put("apiCollectionId", collectionId);
            finding.setCta(new InsightResult.Cta("view_activity", "Open agent page", "NAVIGATE", InsightRoutes.GUARDRAIL_ACTIVITY, ctaParams, true));
            r.addFinding(finding);

            r.addMetric(new InsightResult.Metric("hits_" + collectionId, agent.getName() + " — guardrail hits", total, "count", String.valueOf(total)));
            emitted++;
        }

        r.setStatus(InsightResult.Status.READY.name());
        r.setSeverity(anyCritical ? "CRITICAL" : "HIGH");
        r.setHeadline(InsightUtil.count(countsByAgent.size(), "agent") + " generating guardrail activity this window");
        r.addCta(new InsightResult.Cta("view_all_activity", "View guardrail activity", "NAVIGATE", InsightRoutes.GUARDRAIL_ACTIVITY, new HashMap<>(), false));
        return r;
    }
}

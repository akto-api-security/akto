package com.akto.service.insights.providers.agentic;

import com.akto.dto.insights.agentic.AgentFindingGroup;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Agents no active guardrail policy covers — same host-scope check PolicyHygieneProvider/
 * GuardrailCoverageGapProvider already use for ENDPOINT (InsightUtil#hostCoveredByAnyPolicy),
 * applied per-agent instead of per-host-tag-bucket. Escalated to HIGH when the uncovered agent
 * also has an open red-team finding — an uncovered agent that's already known-vulnerable is a
 * materially worse gap than an uncovered agent with a clean red-team record.
 */
public class AgentGuardrailCoverageGapProvider extends AbstractInsightProvider {

    private static final int MAX_FINDINGS = 10;

    public AgentGuardrailCoverageGapProvider() { super(InsightId.AGENT_GUARDRAIL_COVERAGE_GAP, 1); }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, Scope scope) {
        InsightResult r = skeleton();
        if (!bundle.agentic.loaded) {
            r.addDataGap(new InsightResult.Gap("GUARDRAIL_POLICIES", "NOT_CONFIGURED",
                    "Guardrail coverage is unavailable outside the Argus (agentic) dashboard context."));
            r.setHeadline("Guardrail coverage is unavailable here.");
            return r;
        }
        if (bundle.policies.isEmpty()) {
            r.addDataGap(new InsightResult.Gap("GUARDRAIL_POLICIES", "NO_ROWS", "No guardrail policies exist yet — every agent is uncovered."));
        }

        AgentIndex agentIndex = bundle.agentic.agentIndex;
        Set<Integer> vulnerableAgentIds = new HashSet<>();
        for (AgentFindingGroup g : bundle.agentic.openIssueGroups) vulnerableAgentIds.add(g.getCollectionId());

        List<AgentRef> uncovered = new ArrayList<>();
        for (AgentRef agent : agentIndex.all()) {
            if (agent.isDeactivated()) continue;
            if (!InsightUtil.hostCoveredByAnyPolicy(agent.getHostName(), bundle.policies)) {
                uncovered.add(agent);
            }
        }

        if (uncovered.isEmpty()) {
            r.setStatus(InsightResult.Status.NO_DATA.name());
            r.setHeadline("Every agent is covered by an active guardrail policy.");
            return r;
        }

        uncovered.sort(Comparator.comparing((AgentRef a) -> !vulnerableAgentIds.contains(a.getCollectionId())));

        int emitted = 0;
        int highCount = 0;
        for (AgentRef agent : uncovered) {
            if (emitted >= MAX_FINDINGS) break;
            boolean alsoVulnerable = vulnerableAgentIds.contains(agent.getCollectionId());
            String severity = alsoVulnerable ? "HIGH" : "MEDIUM";
            if ("HIGH".equals(severity)) highCount++;

            InsightResult.Finding finding = new InsightResult.Finding();
            finding.setId(InsightId.AGENT_GUARDRAIL_COVERAGE_GAP.name() + "|" + agent.getCollectionId());
            finding.setSeverity(severity);
            finding.setAgentName(agent.getName());
            finding.setApiCollectionId(agent.getCollectionId());
            finding.setEnvironment(agent.getEnvironment());
            finding.setResource(agent.getHostName());
            finding.setTitle(agent.getName() + " has no guardrail policy covering it");
            finding.setWhyItMatters(alsoVulnerable
                    ? agent.getName() + " has open red-team findings and no policy in front of it to catch a repeat attack."
                    : "Traffic to " + agent.getName() + " passes with no guardrail policy enforcing or even observing it.");
            finding.setRemediation("Add " + agent.getName() + " to an existing guardrail policy, or create one scoped to it.");
            Map<String, Object> ctaParams = new HashMap<>();
            ctaParams.put("apiCollectionId", agent.getCollectionId());
            finding.setCta(new InsightResult.Cta("cover_agent", "Open agent page", "NAVIGATE", InsightRoutes.GUARDRAIL_POLICIES, ctaParams, true));
            r.addFinding(finding);
            emitted++;
        }

        r.addMetric(new InsightResult.Metric("uncoveredAgents", "Uncovered agents", uncovered.size(), "count", String.valueOf(uncovered.size())));
        r.setStatus(InsightResult.Status.READY.name());
        r.setSeverity(highCount > 0 ? "HIGH" : "MEDIUM");
        r.setHeadline(InsightUtil.count(uncovered.size(), "agent") + " with no guardrail policy coverage");
        r.addCta(new InsightResult.Cta("view_policies", "View guardrail policies", "NAVIGATE", InsightRoutes.GUARDRAIL_POLICIES, new HashMap<>(), false));
        return r;
    }
}

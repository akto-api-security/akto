package com.akto.service.insights.providers.agentic;

import com.akto.dto.McpAuditInfo;
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
import java.util.List;
import java.util.Map;

/**
 * Unapproved & malicious components — mcp_audit_info rows grouped by {agent, type, remarks}
 * (never/pending-reviewed, rejected-but-still-in-use) plus the malicious-mcp-server tag on the
 * agent's own collection. See McpAuditInfoDao#auditGroupsForAgents / InsightUtil#governanceBucket
 * for the same remarks-based classification ENDPOINT's MaliciousComponentInUseProvider uses.
 */
public class AgentUnapprovedComponentsProvider extends AbstractInsightProvider {

    private static final int MAX_FINDINGS = 10;

    public AgentUnapprovedComponentsProvider() { super(InsightId.AGENT_UNAPPROVED_COMPONENTS, 1); }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, Scope scope) {
        InsightResult r = skeleton();
        if (!bundle.agentic.loaded) {
            r.addDataGap(new InsightResult.Gap("AUDIT", "NOT_CONFIGURED",
                    "Component audit data is unavailable outside the Argus (agentic) dashboard context."));
            r.setHeadline("Component audit data is unavailable here.");
            return r;
        }

        AgentIndex agentIndex = bundle.agentic.agentIndex;
        List<Candidate> candidates = new ArrayList<>();

        // Malicious-tagged agents — always the worst case, independent of any audit row.
        for (AgentRef agent : agentIndex.all()) {
            if (agent.isMalicious()) {
                candidates.add(new Candidate(agent, "CRITICAL", "malicious component", null, 1));
            }
        }

        for (AgentFindingGroup g : bundle.agentic.auditGroups) {
            AgentRef agent = agentIndex.agentFor(g.getCollectionId());
            if (agent == null) continue;
            String remarks = g.getSecondary();
            if (McpAuditInfo.REMARKS_REJECTED.equals(remarks)) {
                candidates.add(new Candidate(agent, "HIGH", "rejected component still in use", g.getSample(), g.getCount()));
            } else if (remarks == null || remarks.trim().isEmpty()) {
                candidates.add(new Candidate(agent, "MEDIUM", "unreviewed component", g.getSample(), g.getCount()));
            }
            // McpAuditInfo.REMARKS_APPROVED rows are sanctioned — nothing to flag.
        }

        if (candidates.isEmpty()) {
            r.setStatus(InsightResult.Status.NO_DATA.name());
            r.setHeadline("No unapproved or malicious components detected.");
            r.addCta(new InsightResult.Cta("view_audit", "Review audit data", "NAVIGATE", InsightRoutes.AUDIT, new HashMap<>(), true));
            return r;
        }

        candidates.sort(Comparator
                .comparingInt((Candidate c) -> InsightUtil.severityRank(c.severity))
                .thenComparing((Candidate c) -> -c.count));

        int emitted = 0;
        for (Candidate c : candidates) {
            if (emitted >= MAX_FINDINGS) break;
            String resourceName = firstNonBlank(c.resourceNames);
            InsightResult.Finding finding = new InsightResult.Finding();
            finding.setId(InsightId.AGENT_UNAPPROVED_COMPONENTS.name() + "|" + c.agent.getCollectionId() + "|" + c.kind);
            finding.setSeverity(c.severity);
            finding.setAgentName(c.agent.getName());
            finding.setApiCollectionId(c.agent.getCollectionId());
            finding.setEnvironment(c.agent.getEnvironment());
            finding.setResource(resourceName != null ? resourceName : c.kind);
            finding.setTitle(c.agent.getName() + " has a " + c.kind + (resourceName != null ? " (" + resourceName + ")" : ""));
            finding.setWhyItMatters("CRITICAL".equals(c.severity)
                    ? c.agent.getName() + " is flagged as a known-malicious MCP server."
                    : "Audit found " + InsightUtil.count(c.count, c.kind) + " on " + c.agent.getName() + ".");
            finding.setRemediation("CRITICAL".equals(c.severity)
                    ? "Disable " + c.agent.getName() + " immediately and rotate any credentials it had access to."
                    : "Review the flagged component(s) on " + c.agent.getName() + " in the audit queue.");
            Map<String, Object> ctaParams = new HashMap<>();
            ctaParams.put("apiCollectionId", c.agent.getCollectionId());
            finding.setCta(new InsightResult.Cta("review_component", "Open agent page", "NAVIGATE", InsightRoutes.AUDIT, ctaParams, true));
            r.addFinding(finding);
            emitted++;
        }

        r.addMetric(new InsightResult.Metric("flaggedComponents", "Flagged components", candidates.size(), "count", String.valueOf(candidates.size())));
        r.setStatus(InsightResult.Status.READY.name());
        r.setSeverity(InsightUtil.severityRank(candidates.get(0).severity) <= InsightUtil.severityRank("HIGH") ? candidates.get(0).severity : null);
        r.setHeadline(InsightUtil.count(candidates.size(), "flagged component") + " across agents");
        r.addCta(new InsightResult.Cta("view_audit_all", "Review audit data", "NAVIGATE", InsightRoutes.AUDIT, new HashMap<>(), false));
        return r;
    }

    private String firstNonBlank(List<String> values) {
        if (values == null) return null;
        for (String v : values) if (v != null && !v.trim().isEmpty()) return v;
        return null;
    }

    private static final class Candidate {
        final AgentRef agent;
        final String severity;
        final String kind;
        final List<String> resourceNames;
        final int count;
        Candidate(AgentRef agent, String severity, String kind, List<String> resourceNames, int count) {
            this.agent = agent; this.severity = severity; this.kind = kind; this.resourceNames = resourceNames; this.count = count;
        }
    }
}

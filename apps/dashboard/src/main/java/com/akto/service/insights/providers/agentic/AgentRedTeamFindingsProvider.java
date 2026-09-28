package com.akto.service.insights.providers.agentic;

import com.akto.dto.insights.agentic.AgentFindingGroup;
import com.akto.dto.testing.AgentConversationResult;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Red-team findings — the OPEN testing_run_issues per agent (severity, subcategory, sample urls)
 * plus vulnerable_testing_run_results' own real conversation verdicts (via agent_conversation_
 * results, validation=true) for the specific attacks that succeeded. Both reads are already RBAC
 * + AGENTIC-collection scoped and pre-aggregated in Mongo — see InsightDataLoader's AGENTIC
 * section and AgenticInsightData's javadoc.
 */
public class AgentRedTeamFindingsProvider extends AbstractInsightProvider {

    private static final int MAX_FINDINGS = 10;
    private static final int MAX_EVIDENCE_ROWS_PER_AGENT = 3;

    public AgentRedTeamFindingsProvider() { super(InsightId.AGENT_RED_TEAM_FINDINGS, 1); }

    @Override
    public InsightResult compute(InsightDataBundle bundle, InsightContext ctx, Scope scope) {
        InsightResult r = skeleton();
        if (!bundle.agentic.loaded) {
            r.addDataGap(new InsightResult.Gap("TESTING", "NOT_CONFIGURED",
                    "Red-team results are unavailable outside the Argus (agentic) dashboard context."));
            r.setHeadline("Red-team results are unavailable here.");
            return r;
        }

        AgentIndex agentIndex = bundle.agentic.agentIndex;
        List<AgentFindingGroup> openIssues = bundle.agentic.openIssueGroups;
        if (openIssues.isEmpty()) {
            r.setStatus(InsightResult.Status.NO_DATA.name());
            r.setHeadline("No open red-team findings in this window.");
            r.addCta(new InsightResult.Cta("view_issues", "View issues", "NAVIGATE", InsightRoutes.ISSUES, new HashMap<>(), true));
            return r;
        }

        // Worst-severity, highest-count open issue group per agent. AgentFindingGroup#getSecondary
        // holds severity for this source (see its own javadoc).
        Map<Integer, AgentFindingGroup> worstByAgent = new HashMap<>();
        Map<Integer, Integer> totalOpenByAgent = new HashMap<>();
        for (AgentFindingGroup g : openIssues) {
            totalOpenByAgent.merge(g.getCollectionId(), g.getCount(), Integer::sum);
            AgentFindingGroup current = worstByAgent.get(g.getCollectionId());
            if (current == null
                    || InsightUtil.severityRank(g.getSecondary()) < InsightUtil.severityRank(current.getSecondary())
                    || (InsightUtil.severityRank(g.getSecondary()) == InsightUtil.severityRank(current.getSecondary()) && g.getCount() > current.getCount())) {
                worstByAgent.put(g.getCollectionId(), g);
            }
        }

        // Real per-{agent, testSubType} vulnerable-result totals, for a grounded "N vulnerable
        // test runs" number distinct from the OPEN-issue count above.
        Map<Integer, Integer> vulnTotalByAgent = new HashMap<>();
        for (AgentFindingGroup vg : bundle.agentic.vulnGroups) {
            vulnTotalByAgent.merge(vg.getCollectionId(), vg.getCount(), Integer::sum);
        }

        List<Integer> rankedAgentIds = new ArrayList<>(worstByAgent.keySet());
        rankedAgentIds.sort(Comparator
                .comparingInt((Integer id) -> InsightUtil.severityRank(worstByAgent.get(id).getSecondary()))
                .thenComparing((Integer id) -> -totalOpenByAgent.getOrDefault(id, 0)));

        List<Map<String, Object>> evidenceRows = new ArrayList<>();
        int emitted = 0;
        for (Integer collectionId : rankedAgentIds) {
            if (emitted >= MAX_FINDINGS) break;
            AgentRef agent = agentIndex.agentFor(collectionId);
            if (agent == null) continue; // deactivated/unknown collection — nothing to attribute a finding to
            AgentFindingGroup worst = worstByAgent.get(collectionId);
            int totalOpen = totalOpenByAgent.getOrDefault(collectionId, 0);
            String resource = firstNonBlank(worst.getSample());

            String subCategoryLabel = InsightUtil.humanizeSubCategory(worst.getType());
            InsightResult.Finding finding = new InsightResult.Finding();
            finding.setId(InsightId.AGENT_RED_TEAM_FINDINGS.name() + "|" + collectionId + "|" + worst.getType());
            finding.setSeverity(worst.getSecondary());
            finding.setAgentName(agent.getName());
            finding.setApiCollectionId(collectionId);
            finding.setEnvironment(agent.getEnvironment());
            finding.setResource(resource != null ? resource : subCategoryLabel);
            finding.setTitle(agent.getName() + " has " + InsightUtil.count(totalOpen, "open " + subCategoryLabel + " finding")
                    + (totalOpen == 1 ? "" : "s"));
            finding.setWhyItMatters("Red-teaming found " + InsightUtil.count(totalOpen, subCategoryLabel + " issue")
                    + " still open on " + agent.getName() + ", the worst rated " + worst.getSecondary() + ".");
            finding.setRemediation("Review and remediate the " + subCategoryLabel + " finding(s) on " + agent.getName() + ".");
            Map<String, Object> ctaParams = new HashMap<>();
            ctaParams.put("apiCollectionId", collectionId);
            finding.setCta(new InsightResult.Cta("view_agent_issues", "Open agent page", "NAVIGATE", InsightRoutes.ISSUES, ctaParams, true));
            r.addFinding(finding);

            r.addMetric(new InsightResult.Metric(
                    "openIssues_" + collectionId, agent.getName() + " — open issues", totalOpen, "count", String.valueOf(totalOpen)));
            int vulnTotal = vulnTotalByAgent.getOrDefault(collectionId, 0);
            if (vulnTotal > 0) {
                r.addMetric(new InsightResult.Metric(
                        "vulnResults_" + collectionId, agent.getName() + " — vulnerable test runs", vulnTotal, "count", String.valueOf(vulnTotal)));
            }

            addConversationEvidence(bundle, agent, collectionId, evidenceRows);
            emitted++;
        }

        if (!evidenceRows.isEmpty()) {
            r.addEvidence(new InsightResult.Evidence("redTeamConversations", "Successful red-team conversations",
                    java.util.Arrays.asList("agent", "testSubType", "validationMessage", "remediationMessage", "lastSeen"),
                    evidenceRows, evidenceRows.size()));
        }

        r.setStatus(InsightResult.Status.READY.name());
        r.setSeverity(worstByAgent.isEmpty() ? null : worstSeverity(worstByAgent));
        r.setHeadline(InsightUtil.count(openIssues.size(), "open red-team finding") + " across "
                + InsightUtil.count(worstByAgent.size(), "agent"));
        r.addCta(new InsightResult.Cta("view_all_issues", "View all issues", "NAVIGATE", InsightRoutes.ISSUES, new HashMap<>(), false));
        return r;
    }

    /** Joins this agent's vulnerable-result groups to their real, validated conversation verdicts
     *  — the grounding AgenticInsightNarrativeHandler's conversationEvidence uses (see that
     *  handler + AgenticNarrativeStrategy). A row with no validated conversation is skipped, not
     *  padded with a blank one — this table exists specifically to show what an attack achieved. */
    private void addConversationEvidence(InsightDataBundle bundle, AgentRef agent, int collectionId, List<Map<String, Object>> out) {
        int added = 0;
        for (AgentFindingGroup vg : bundle.agentic.vulnGroups) {
            if (vg.getCollectionId() != collectionId || vg.getSample() == null) continue;
            for (String convId : vg.getSample()) {
                if (added >= MAX_EVIDENCE_ROWS_PER_AGENT) return;
                AgentConversationResult conv = bundle.agentic.validatedConversationsById.get(convId);
                if (conv == null) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("agent", agent.getName());
                row.put("testSubType", InsightUtil.humanizeSubCategory(vg.getType()));
                row.put("validationMessage", conv.getValidationMessage());
                row.put("remediationMessage", conv.getRemediationMessage());
                row.put("lastSeen", vg.getLastSeen());
                row.put("refId", convId); // hidden join key, not a displayed column — see attachEvidenceSamples' own convention
                out.add(row);
                added++;
            }
        }
    }

    private String firstNonBlank(List<String> values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) return v;
        }
        return null;
    }

    private String worstSeverity(Map<Integer, AgentFindingGroup> worstByAgent) {
        String worst = null;
        for (AgentFindingGroup g : worstByAgent.values()) {
            if (worst == null || InsightUtil.severityRank(g.getSecondary()) < InsightUtil.severityRank(worst)) {
                worst = g.getSecondary();
            }
        }
        return worst == null ? null : worst.toUpperCase(Locale.ROOT);
    }
}

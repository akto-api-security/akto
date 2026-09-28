package com.akto.service.insights.providers.agentic;

import com.akto.dto.ApiCollection;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.insights.agentic.AgentFindingGroup;
import com.akto.dto.testing.AgentConversationResult;
import com.akto.service.insights.InsightContext;
import com.akto.service.insights.InsightDataBundle;
import com.akto.service.insights.InsightProvider;
import com.akto.service.insights.InsightResult;
import com.akto.service.insights.agentic.AgentIndex;
import com.akto.service.insights.agentic.AgenticInsightData;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Pure-logic coverage for the Argus (AGENTIC) providers, built over a hand-assembled
 * InsightDataBundle the same way TestPostureService covers PostureService — no Mongo, no LLM
 * call, every provider exercised through its real public compute() entry point.
 */
public class AgenticProvidersTest {

    private static final int START_TS = 1_000_000;
    private static final int END_TS = 1_100_000;

    private static ApiCollection agent(int id, String name, String hostName) {
        ApiCollection c = new ApiCollection();
        c.setId(id);
        c.setName(name);
        c.setHostName(hostName);
        return c;
    }

    private static InsightContext ctx() {
        return new InsightContext(1234, 1, CONTEXT_SOURCE.AGENTIC, START_TS, END_TS);
    }

    private static InsightDataBundle bundleWith(List<ApiCollection> collections, List<GuardrailPolicies> policies,
                                                 AgenticInsightData agentic) {
        return new InsightDataBundle(ctx(), collections, new HashMap<>(), new HashMap<>(), new HashMap<>(),
                new ArrayList<>(), policies, Collections.emptySet(), new HashMap<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), true, new ArrayList<>(), new HashMap<>(),
                agentic, null);
    }

    // ── AgentRedTeamFindingsProvider ─────────────────────────────────────────────

    @Test
    public void redTeam_notLoaded_reportsGapInsteadOfComputing() {
        InsightDataBundle bundle = bundleWith(Collections.emptyList(), Collections.emptyList(), AgenticInsightData.empty());
        InsightResult r = new AgentRedTeamFindingsProvider().compute(bundle, bundle.ctx, InsightProvider.Scope.LIST);
        assertTrue(r.getFindings().isEmpty());
        assertFalse(r.getDataGaps().isEmpty());
    }

    @Test
    public void redTeam_ranksWorstSeverityFirst_andGroundsInValidatedConversation() {
        List<ApiCollection> collections = Arrays.asList(
                agent(1, "refund-agent-prod", "refund-agent-prod.mcp"),
                agent(2, "docs-agent", "docs-agent.mcp"));

        List<AgentFindingGroup> openIssues = Arrays.asList(
                new AgentFindingGroup(2, "PROMPT_INJECTION", "MEDIUM", 1, END_TS, Arrays.asList("/tools/search")),
                new AgentFindingGroup(1, "BOLA", "CRITICAL", 5, END_TS, Arrays.asList("/tools/refund")));

        List<AgentFindingGroup> vulnGroups = Collections.singletonList(
                new AgentFindingGroup(1, "BOLA", null, 5, END_TS, Collections.singletonList("conv-1")));

        Map<String, AgentConversationResult> conversations = new HashMap<>();
        AgentConversationResult conv1 = new AgentConversationResult();
        conv1.setConversationId("conv-1");
        conv1.setValidationMessage("The agent issued a refund without approval when asked twice.");
        conv1.setRemediationMessage("Require human approval above a set threshold.");
        conv1.setLastUpdatedAt(END_TS);
        conversations.put("conv-1", conv1);

        AgenticInsightData agentic = new AgenticInsightData(true, new AgentIndex(collections), openIssues, vulnGroups,
                Collections.emptyList(), conversations, Collections.emptyList(), Collections.emptyList());

        InsightDataBundle bundle = bundleWith(collections, Collections.emptyList(), agentic);
        InsightResult r = new AgentRedTeamFindingsProvider().compute(bundle, bundle.ctx, InsightProvider.Scope.LIST);

        assertEquals(2, r.getFindings().size());
        // CRITICAL (refund-agent-prod) ranks before MEDIUM (docs-agent).
        assertEquals("refund-agent-prod", r.getFindings().get(0).getAgentName());
        assertEquals("CRITICAL", r.getFindings().get(0).getSeverity());
        assertEquals(1, r.getFindings().get(0).getApiCollectionId());

        assertFalse(r.getEvidence().isEmpty());
        Map<String, Object> row = r.getEvidence().get(0).getRows().get(0);
        assertEquals("refund-agent-prod", row.get("agent"));
        assertEquals("The agent issued a refund without approval when asked twice.", row.get("validationMessage"));
    }

    // ── AgentGuardrailCoverageGapProvider ────────────────────────────────────────

    @Test
    public void coverageGap_fleetWidePolicy_coversEveryAgent() {
        List<ApiCollection> collections = Collections.singletonList(agent(1, "refund-agent-prod", "refund-agent-prod.mcp"));
        GuardrailPolicies fleetWide = new GuardrailPolicies();
        fleetWide.setApplyToAllServers(true);
        AgenticInsightData agentic = new AgenticInsightData(true, new AgentIndex(collections), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), new HashMap<>(), Collections.emptyList(), Collections.emptyList());

        InsightDataBundle bundle = bundleWith(collections, Collections.singletonList(fleetWide), agentic);
        InsightResult r = new AgentGuardrailCoverageGapProvider().compute(bundle, bundle.ctx, InsightProvider.Scope.LIST);

        assertTrue(r.getFindings().isEmpty());
        assertEquals(InsightResult.Status.NO_DATA.name(), r.getStatus());
    }

    @Test
    public void coverageGap_uncoveredAgent_isEscalatedWhenAlsoRedTeamVulnerable() {
        List<ApiCollection> collections = Arrays.asList(
                agent(1, "refund-agent-prod", "refund-agent-prod.mcp"),
                agent(2, "docs-agent", "docs-agent.mcp"));
        List<AgentFindingGroup> openIssues = Collections.singletonList(
                new AgentFindingGroup(1, "BOLA", "CRITICAL", 1, END_TS, Collections.emptyList()));

        AgenticInsightData agentic = new AgenticInsightData(true, new AgentIndex(collections), openIssues,
                Collections.emptyList(), Collections.emptyList(), new HashMap<>(), Collections.emptyList(), Collections.emptyList());

        InsightDataBundle bundle = bundleWith(collections, Collections.emptyList(), agentic);
        InsightResult r = new AgentGuardrailCoverageGapProvider().compute(bundle, bundle.ctx, InsightProvider.Scope.LIST);

        assertEquals(2, r.getFindings().size());
        InsightResult.Finding vulnerableAgentFinding = r.getFindings().stream()
                .filter(f -> f.getApiCollectionId() == 1).findFirst().orElseThrow(AssertionError::new);
        InsightResult.Finding cleanAgentFinding = r.getFindings().stream()
                .filter(f -> f.getApiCollectionId() == 2).findFirst().orElseThrow(AssertionError::new);
        assertEquals("HIGH", vulnerableAgentFinding.getSeverity());
        assertEquals("MEDIUM", cleanAgentFinding.getSeverity());
    }
}

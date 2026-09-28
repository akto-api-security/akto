package com.akto.gpt.handlers.gpt_prompts;

import com.mongodb.BasicDBObject;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Logic-level coverage for AgenticInsightNarrativeHandler — the ARGUS_POSTURE sibling of
 * InsightNarrativeHandler, sharing AbstractGroundedNarrativeHandler's retry loop/numeric-literal
 * guard but with a per-finding output shape instead of a single concern/impact/remediation. Same
 * "test buildPrompt/allowedLiterals/validateAndBuild directly, no network call" convention as
 * InsightNarrativeHandlerTest.
 */
class AgenticInsightNarrativeHandlerTest {

    private static JSONObject minimalInput() throws org.json.JSONException {
        JSONObject input = new JSONObject();
        input.put("metrics", new JSONArray()
                .put(new JSONObject().put("key", "openIssues_1").put("label", "refund-agent-prod — open issues").put("formatted", "5")));
        input.put("evidence", new JSONArray());
        input.put("caveats", new JSONArray());
        input.put("dataGaps", new JSONArray());

        JSONObject finding = new JSONObject()
                .put("id", "AGENT_RED_TEAM_FINDINGS|1|BOLA")
                .put("agentName", "refund-agent-prod")
                .put("environment", "Production")
                .put("resource", "BOLA")
                .put("severity", "CRITICAL")
                .put("draftTitle", "refund-agent-prod has 5 open BOLA findings")
                .put("draftWhy", "Red-teaming found 5 BOLA issues still open on refund-agent-prod.")
                .put("draftRemediation", "Review and remediate the BOLA finding(s) on refund-agent-prod.")
                .put("conversationEvidence", new JSONArray()
                        .put(new JSONObject().put("agent", "refund-agent-prod").put("testSubType", "BOLA")
                                .put("validationMessage", "The agent issued a refund without approval when asked twice.")
                                .put("remediationMessage", "Require human approval above a set threshold.")));
        input.put("findings", new JSONArray().put(finding));
        return input;
    }

    @Test
    void testBuildPrompt_requiresAgentNameInTitle_andGroundsInConversationEvidence() throws org.json.JSONException {
        String prompt = new AgenticInsightNarrativeHandler().buildPrompt(minimalInput(), null);
        assertTrue(prompt.contains("agentName verbatim"));
        assertTrue(prompt.toLowerCase().contains("conversationevidence"));
        assertTrue(prompt.toLowerCase().contains("paraphrase"));
        assertTrue(prompt.contains("refund-agent-prod"));
    }

    @Test
    void testAllowedLiterals_includesFindingDraftAndConversationEvidenceNumbers() throws org.json.JSONException {
        AgenticInsightNarrativeHandler handler = new AgenticInsightNarrativeHandler();
        Set<String> literals = handler.allowedLiterals(minimalInput());
        assertTrue(literals.contains("5")); // from both FACTS and the finding's own draftTitle
    }

    @Test
    void testValidateAndBuild_rejectsANumberNotPresentInFactsOrFindings() throws org.json.JSONException {
        AgenticInsightNarrativeHandler handler = new AgenticInsightNarrativeHandler();
        Set<String> allowedLiterals = handler.allowedLiterals(minimalInput());

        String modelResponse = new JSONObject()
                .put("narrative", "refund-agent-prod has 12 open findings this window.")
                .put("findings", new JSONArray())
                .toString();

        BasicDBObject result = handler.validateAndBuild(modelResponse, allowedLiterals);
        assertTrue(result.containsField("error"));
        assertTrue(result.getString("error").contains("12"));
    }

    @Test
    void testValidateAndBuild_acceptsGroundedRewrite_andReturnsFindingById() throws org.json.JSONException {
        AgenticInsightNarrativeHandler handler = new AgenticInsightNarrativeHandler();
        Set<String> allowedLiterals = handler.allowedLiterals(minimalInput());

        String modelResponse = new JSONObject()
                .put("narrative", "refund-agent-prod has 5 open BOLA findings, including one that let it issue refunds without approval.")
                .put("findings", new JSONArray().put(new JSONObject()
                        .put("id", "AGENT_RED_TEAM_FINDINGS|1|BOLA")
                        .put("title", "refund-agent-prod can issue refunds without approval")
                        .put("whyItMatters", "A red-team run got refund-agent-prod to approve its own refund twice.")
                        .put("remediation", "Require human approval above a set threshold on refund-agent-prod.")))
                .toString();

        BasicDBObject result = handler.validateAndBuild(modelResponse, allowedLiterals);
        assertFalse(result.containsField("error"));
        @SuppressWarnings("unchecked")
        List<BasicDBObject> findings = (List<BasicDBObject>) result.get("findings");
        assertEquals(1, findings.size());
        assertEquals("AGENT_RED_TEAM_FINDINGS|1|BOLA", findings.get(0).getString("id"));
        assertEquals("refund-agent-prod can issue refunds without approval", findings.get(0).getString("title"));
    }

    @Test
    void testValidateAndBuild_dropsAFindingWithNoId_withoutErroring() throws org.json.JSONException {
        AgenticInsightNarrativeHandler handler = new AgenticInsightNarrativeHandler();
        Set<String> allowedLiterals = handler.allowedLiterals(minimalInput());

        String modelResponse = new JSONObject()
                .put("narrative", "refund-agent-prod has 5 open BOLA findings this window.")
                .put("findings", new JSONArray().put(new JSONObject()
                        .put("title", "a title with no id")
                        .put("whyItMatters", "irrelevant")
                        .put("remediation", "irrelevant")))
                .toString();

        BasicDBObject result = handler.validateAndBuild(modelResponse, allowedLiterals);
        assertFalse(result.containsField("error"));
        @SuppressWarnings("unchecked")
        List<BasicDBObject> findings = (List<BasicDBObject>) result.get("findings");
        assertTrue(findings.isEmpty());
    }

    @Test
    void testValidateAndBuild_rejectsAMarkdownLinkInNarrative() throws org.json.JSONException {
        AgenticInsightNarrativeHandler handler = new AgenticInsightNarrativeHandler();
        Set<String> allowedLiterals = handler.allowedLiterals(minimalInput());

        String modelResponse = new JSONObject()
                .put("narrative", "See [this policy](https://example.com) for refund-agent-prod's 5 findings.")
                .put("findings", new JSONArray())
                .toString();

        BasicDBObject result = handler.validateAndBuild(modelResponse, allowedLiterals);
        assertTrue(result.containsField("error"));
    }
}

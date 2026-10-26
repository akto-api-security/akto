package com.akto.gpt.handlers.gpt_prompts;

import com.mongodb.BasicDBObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FalsePositiveVerifierPromptHandlerTest {

    @Test
    void parsesCleanJson() {
        BasicDBObject r = FalsePositiveVerifierPromptHandler.parseVerdict(
            "{\"verdict\":\"FALSE_POSITIVE\",\"confidence\":92,\"reason\":\"Attack request got 401\"}");
        assertEquals("FALSE_POSITIVE", r.getString("verdict"));
        assertEquals(92, r.getInt("confidence"));
        assertEquals("Attack request got 401", r.getString("reason"));
        assertFalse(r.containsField("error"));
    }

    @Test
    void parsesJsonWrappedInMarkdownFence() {
        BasicDBObject r = FalsePositiveVerifierPromptHandler.parseVerdict(
            "```json\n{\"verdict\":\"TRUE_POSITIVE\",\"confidence\":80,\"reason\":\"Other user's data returned\"}\n```");
        assertEquals("TRUE_POSITIVE", r.getString("verdict"));
    }

    @Test
    void normalisesLowerCaseAndSpacedVerdict() {
        BasicDBObject r = FalsePositiveVerifierPromptHandler.parseVerdict(
            "{\"verdict\":\"false positive\",\"confidence\":75,\"reason\":\"x\"}");
        assertEquals("FALSE_POSITIVE", r.getString("verdict"));
    }

    @Test
    void missingConfidenceIsZeroAndOutOfRangeIsClamped() {
        assertEquals(0, FalsePositiveVerifierPromptHandler.parseVerdict(
            "{\"verdict\":\"FALSE_POSITIVE\",\"reason\":\"x\"}").getInt("confidence"));
        assertEquals(100, FalsePositiveVerifierPromptHandler.parseVerdict(
            "{\"verdict\":\"FALSE_POSITIVE\",\"confidence\":250,\"reason\":\"x\"}").getInt("confidence"));
    }

    @Test
    void longReasonIsTruncated() {
        StringBuilder longReason = new StringBuilder();
        for (int i = 0; i < 800; i++) longReason.append('a');
        BasicDBObject r = FalsePositiveVerifierPromptHandler.parseVerdict(
            "{\"verdict\":\"TRUE_POSITIVE\",\"confidence\":50,\"reason\":\"" + longReason + "\"}");
        assertEquals(FalsePositiveVerifierPromptHandler.MAX_REASON_CHARS, r.getString("reason").length());
    }

    @Test
    void garbageAndUnknownVerdictsAreErrors() {
        assertTrue(FalsePositiveVerifierPromptHandler.parseVerdict("I think it is fine").containsField("error"));
        assertTrue(FalsePositiveVerifierPromptHandler.parseVerdict("").containsField("error"));
        assertTrue(FalsePositiveVerifierPromptHandler.parseVerdict(null).containsField("error"));
        assertTrue(FalsePositiveVerifierPromptHandler.parseVerdict(
            "{\"verdict\":\"MAYBE\",\"confidence\":90}").containsField("error"));
    }

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(c);
        return sb.toString();
    }

    private static String testResultMessage(String requestBody, int status, String responseBody) throws Exception {
        return new org.json.JSONObject()
            .put("request", new org.json.JSONObject().put("method", "GET").put("url", "https://api.example.com/users/42")
                .put("headers", "{\"authorization\":\"Bearer attacker\"}").put("body", requestBody))
            .put("response", new org.json.JSONObject().put("statusCode", status)
                .put("headers", "{\"content-type\":\"application/json\"}").put("body", responseBody))
            .toString();
    }

    private static final FalsePositiveVerifierPromptHandler.TestDefinition BOLA = new FalsePositiveVerifierPromptHandler.TestDefinition(
        "REPLACE_AUTH_TOKEN", "BOLA by replacing auth token", "Checks if another user's token can read the resource",
        "Broken Object Level Authorization (BOLA)",
        "id: REPLACE_AUTH_TOKEN\nvalidate:\n  response_code:\n    gte: 200\n    lt: 300");

    @Test
    void promptIncludesTestDefinitionTemplateAndRules() throws Exception {
        String prompt = FalsePositiveVerifierPromptHandler.buildPrompt(BOLA, "status 200 with other user's id",
            null, java.util.Collections.singletonList(testResultMessage("", 200, "{\"email\":\"victim@x.com\"}")));
        assertTrue(prompt.contains("Test id: REPLACE_AUTH_TOKEN"));
        assertTrue(prompt.contains("Test name: BOLA by replacing auth token"));
        assertTrue(prompt.contains("Category: Broken Object Level Authorization (BOLA)"));
        assertTrue(prompt.contains("Description: Checks if another user's token can read the resource"));
        assertTrue(prompt.contains("validate:\n  response_code:"));
        assertTrue(prompt.contains("Why the scanner flagged it: status 200 with other user's id"));
        assertTrue(prompt.contains("<<<EVIDENCE") && prompt.contains("EVIDENCE>>>"));
        assertTrue(prompt.contains("answer TRUE_POSITIVE"));
        assertTrue(prompt.contains("Baseline (original) request/response:\n(not available)"));
        // the template sits outside the untrusted evidence block
        assertTrue(prompt.indexOf("validate:") < prompt.indexOf("\n<<<EVIDENCE\n"));
    }

    @Test
    void largeRequestDoesNotPushResponseOut() throws Exception {
        String hugeRequest = repeat('r', 20000);
        String formatted = FalsePositiveVerifierPromptHandler.formatExchange(
            testResultMessage(hugeRequest, 200, "{\"email\":\"victim@x.com\"}"));
        assertTrue(formatted.contains("REQUEST:\nGET https://api.example.com/users/42"));
        assertTrue(formatted.contains("RESPONSE:\nStatus: 200"));
        assertTrue(formatted.contains("victim@x.com"));
        assertTrue(formatted.contains("...[truncated]"));
        assertTrue(formatted.length() < FalsePositiveVerifierPromptHandler.MAX_REQUEST_CHARS
            + FalsePositiveVerifierPromptHandler.MAX_RESPONSE_CHARS + 200);
    }

    @Test
    void largeResponseIsCappedSeparately() throws Exception {
        String formatted = FalsePositiveVerifierPromptHandler.formatExchange(
            testResultMessage("{\"id\":42}", 200, repeat('x', 20000)));
        assertTrue(formatted.contains("Body: {\"id\":42}"));
        String response = formatted.substring(formatted.indexOf("RESPONSE:"));
        assertTrue(response.length() <= "RESPONSE:\n".length() + FalsePositiveVerifierPromptHandler.MAX_RESPONSE_CHARS + "...[truncated]".length());
    }

    @Test
    void sampleDataFormatIsUnderstood() throws Exception {
        String original = new org.json.JSONObject().put("method", "GET").put("path", "/users/42")
            .put("requestHeaders", "{\"authorization\":\"Bearer owner\"}").put("requestPayload", "")
            .put("statusCode", "200").put("responsePayload", "{\"email\":\"owner@x.com\"}").toString();
        String formatted = FalsePositiveVerifierPromptHandler.formatExchange(original);
        assertTrue(formatted.contains("REQUEST:\nGET /users/42"));
        assertTrue(formatted.contains("Bearer owner"));
        assertTrue(formatted.contains("Status: 200"));
        assertTrue(formatted.contains("owner@x.com"));
    }

    @Test
    void nonJsonMessageFallsBackToRawText() {
        assertEquals("plain text", FalsePositiveVerifierPromptHandler.formatExchange("plain text"));
        assertEquals("(not available)", FalsePositiveVerifierPromptHandler.formatExchange(null));
    }

    @Test
    void everyAttemptIsNumbered() throws Exception {
        String prompt = FalsePositiveVerifierPromptHandler.buildPrompt(BOLA, null, null, java.util.Arrays.asList(
            testResultMessage("", 200, "{\"a\":1}"), testResultMessage("", 200, "{\"b\":2}")));
        assertTrue(prompt.contains("Attack attempt 1 of 2 (flagged vulnerable):"));
        assertTrue(prompt.contains("Attack attempt 2 of 2 (flagged vulnerable):"));
    }

    @Test
    void missingTemplateIsMarkedNotAvailable() {
        String prompt = FalsePositiveVerifierPromptHandler.buildPrompt(
            new FalsePositiveVerifierPromptHandler.TestDefinition("CUSTOM_TEST", null, null, null, null),
            null, null, java.util.Collections.emptyList());
        assertTrue(prompt.contains("Test id: CUSTOM_TEST"));
        assertTrue(prompt.contains("```yaml\n(not available)\n```"));
        assertTrue(prompt.contains("Attack request/response:\n(not available)"));
    }
}

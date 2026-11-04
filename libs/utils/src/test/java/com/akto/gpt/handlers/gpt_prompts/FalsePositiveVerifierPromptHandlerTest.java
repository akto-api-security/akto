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

    private static final String BOLA_ID = "REPLACE_AUTH_TOKEN";
    private static final String BOLA_TEMPLATE = "id: REPLACE_AUTH_TOKEN\ninfo:\n  name: \"BOLA by replacing auth token\"\n"
        + "validate:\n  response_code:\n    gte: 200\n    lt: 300";

    @Test
    void promptIncludesTestIdTemplateAndRules() throws Exception {
        String prompt = FalsePositiveVerifierPromptHandler.buildPrompt(BOLA_ID, BOLA_TEMPLATE,
            null, java.util.Collections.singletonList(testResultMessage("", 200, "{\"email\":\"victim@x.com\"}")));
        assertTrue(prompt.contains("Test id: REPLACE_AUTH_TOKEN"));
        assertTrue(prompt.contains("info:\n  name: BOLA by replacing auth token"), prompt);
        assertTrue(prompt.contains("validate:\n  response_code:"));
        assertTrue(prompt.contains("<<<EVIDENCE") && prompt.contains("EVIDENCE>>>"));
        assertTrue(prompt.contains("Answer FALSE_POSITIVE only\n  when the proof rules below clearly show"));
        assertTrue(prompt.contains("`x-akto-ignore` header to every request it sends; it is not part of the attack"));
        assertTrue(prompt.contains("Baseline (original) request/response:\n(not available)"));
        // the template sits outside the untrusted evidence block
        assertTrue(prompt.indexOf("validate:") < prompt.indexOf("\n<<<EVIDENCE\n"));
    }

    @Test
    void largeRequestDoesNotPushResponseOut() throws Exception {
        String hugeRequest = repeat('r', 20000);
        String formatted = FalsePositiveVerifierPromptHandler.formatExchange(
            testResultMessage(hugeRequest, 200, "{\"email\":\"victim@x.com\"}"), null, false, false);
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
            testResultMessage("{\"id\":42}", 200, repeat('x', 20000)), null, false, false);
        assertTrue(formatted.contains("Body: {\"id\":42}"));
        String response = formatted.substring(formatted.indexOf("RESPONSE:"));
        assertTrue(response.length() <= "RESPONSE:\n".length() + FalsePositiveVerifierPromptHandler.MAX_RESPONSE_CHARS + "...[truncated]".length());
    }

    @Test
    void sampleDataFormatIsUnderstood() throws Exception {
        String original = new org.json.JSONObject().put("method", "GET").put("path", "/users/42")
            .put("requestHeaders", "{\"authorization\":\"Bearer owner\"}").put("requestPayload", "")
            .put("statusCode", "200").put("responsePayload", "{\"email\":\"owner@x.com\"}").toString();
        String formatted = FalsePositiveVerifierPromptHandler.formatExchange(original, null, false, false);
        assertTrue(formatted.contains("REQUEST:\nGET /users/42"));
        assertTrue(formatted.contains("Bearer owner"));
        assertTrue(formatted.contains("Status: 200"));
        assertTrue(formatted.contains("owner@x.com"));
    }

    @Test
    void nonJsonMessageFallsBackToRawText() {
        assertEquals("plain text", FalsePositiveVerifierPromptHandler.formatExchange("plain text", null, false, false));
        assertEquals("(not available)", FalsePositiveVerifierPromptHandler.formatExchange(null, null, false, false));
    }

    @Test
    void everyAttemptIsNumbered() throws Exception {
        String prompt = FalsePositiveVerifierPromptHandler.buildPrompt(BOLA_ID, BOLA_TEMPLATE, null, java.util.Arrays.asList(
            testResultMessage("", 200, "{\"a\":1}"), testResultMessage("", 200, "{\"b\":2}")));
        assertTrue(prompt.contains("Attack attempt 1 of 2 (flagged vulnerable):"));
        assertTrue(prompt.contains("Attack attempt 2 of 2 (flagged vulnerable):"));
    }

    @Test
    void missingTemplateIsMarkedNotAvailable() {
        String prompt = FalsePositiveVerifierPromptHandler.buildPrompt("CUSTOM_TEST", null, null,
            java.util.Collections.emptyList());
        assertTrue(prompt.contains("Test id: CUSTOM_TEST"));
        assertTrue(prompt.contains("```yaml\n(not available)\n```"));
        assertTrue(prompt.contains("Attack request/response:\n(not available)"));
    }

    // Shaped like LFIAddingNewParam.yaml: validate comes after a long word list.
    private static final String LFI_TEMPLATE = "id: FILE_INCLUSION_NEW_PARAM\n"
        + "wordLists:\n  filePaths:\n    - /etc/passwd\n    - " + repeat('A', 3000) + "/..//etc/passwd\n\n"
        + "execute:\n  type: single\n  requests:\n    - req:\n      - add_query_param:\n          file: ${filePaths}\n\n"
        + "validate:\n  response_payload:\n    regex: \"root:.*:0:0:\"\n";

    @Test
    void fullTemplateAndResponseAreSent() throws Exception {
        String body = "{\"pad\":\"" + repeat('p', 10000) + "\",\"tail\":\"end-of-body\"}";
        String prompt = FalsePositiveVerifierPromptHandler.buildPrompt("FILE_INCLUSION_NEW_PARAM", LFI_TEMPLATE, null,
            java.util.Collections.singletonList(testResultMessage("", 200, body)));
        assertTrue(prompt.contains("validate:\n  response_payload:\n    regex: "), prompt);
        assertTrue(prompt.contains("root:.*:0:0:"), prompt);
        assertTrue(prompt.contains("AAAAAAAAAA... (3015 chars)"), prompt);
        assertFalse(prompt.contains(repeat('A', 200)), "the 3000-char payload is cut");
        assertTrue(prompt.contains("end-of-body"));
    }

    // --- prompt trimming ---

    private static String markerList(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append("      - \"marker").append(i).append("=\"\n");
        return sb.toString();
    }

    @Test
    void templateKeepsMarkersAndExecuteButCutsProseAndPayloadLists() {
        StringBuilder notContains = new StringBuilder();
        for (int i = 0; i < 30; i++) notContains.append("      - ../payload").append(i).append("\n");
        String template = "id: LFI_IN_HEADERS_LINUX\ninfo:\n  name: \"LFI in Headers\"\n  description: \"" + repeat('d', 500) + "\"\n"
            + "  details: \"long background\"\n  category:\n    name: LFI\n  severity: HIGH\n  references:\n    - https://owasp.org\n"
            + "attributes:\n  nature: INTRUSIVE\n"
            + "execute:\n  type: single\n  requests:\n    - req:\n        - modify_header:\n            user-agent: \"" + repeat('x', 300) + "\"\n"
            + "validate:\n  response_payload:\n    contains_either:\n" + markerList(16) + "    not_contains:\n" + notContains;
        String compact = FalsePositiveVerifierPromptHandler.compactTemplate(template);
        assertTrue(compact.contains("name: LFI in Headers"), compact);
        assertTrue(compact.contains("severity: HIGH"), compact);
        assertTrue(compact.contains("description: " + repeat('d', 400) + "...[truncated]"), "the description is kept, capped");
        assertFalse(compact.contains("details"), compact);
        assertFalse(compact.contains("references"), compact);
        assertFalse(compact.contains("INTRUSIVE"), compact);
        assertTrue(compact.contains("marker0=") && compact.contains("marker15="), "every validate marker is kept");
        assertTrue(compact.contains("../payload9") && !compact.contains("../payload10"), compact);
        assertTrue(compact.contains("... (+20 more)"), compact);
        assertTrue(compact.contains(repeat('x', 300)), "execute values are kept whole");
        assertEquals("not: [valid", FalsePositiveVerifierPromptHandler.compactTemplate("not: [valid"));
    }

    private static final String TOKEN = "Bearer " + repeat('t', 1500);

    @Test
    void attemptsShowOnlyChangedHeadersWithTokensShortened() throws Exception {
        String original = exchange("https://x.io/a", "{\"authorization\":\"" + TOKEN + "\",\"x-platform\":\"iOS\",\"user-agent\":\"Gojek/5.77\"}",
            200, "{\"content-type\":\"application/json\",\"date\":\"Wed\",\"x-ratelimit-remaining-second\":\"9\"}", "{\"ok\":true}");
        String attack = exchange("https://x.io/a", "{\"authorization\":\"Bearer " + repeat('u', 1500) + "\",\"x-platform\":\"iOS\","
            + "\"user-agent\":\"..%c0%afdocker.sock\",\"x-akto-ignore\":\"0\"}", 200, "{\"content-type\":\"application/json\"}", "{\"ok\":true}");
        String prompt = FalsePositiveVerifierPromptHandler.buildPrompt("LFI_IN_HEADERS_LINUX",
            template("LFI_IN_HEADERS_LINUX", "LFI", CHANGED), original, java.util.Collections.singletonList(attack));
        String attempt = prompt.substring(prompt.indexOf("Attack attempt 1 of 1"));
        assertTrue(attempt.contains("Headers changed from the original: {"), attempt);
        assertTrue(attempt.contains("\"user-agent\":\"..%c0%afdocker.sock\""), attempt);
        assertTrue(attempt.contains("...(1507 chars)"), attempt);
        assertTrue(attempt.contains("(other headers same as the original request)"), attempt);
        assertFalse(attempt.contains("x-platform"), attempt);
        assertFalse(attempt.contains("x-akto-ignore\":"), attempt);
        assertFalse(prompt.contains(repeat('t', 300)), "the original token is shortened too");
        assertFalse(prompt.contains("x-ratelimit-remaining-second"), "transport headers are dropped");
        assertTrue(prompt.contains("content-type"), prompt);
    }

    @Test
    void jwtAndHeaderTestsKeepWhatTheyAreJudgedOn() throws Exception {
        String original = exchange("https://x.io/a", "{\"authorization\":\"" + TOKEN + "\"}", 200, "{\"date\":\"Wed\"}", "{}");
        String attack = exchange("https://x.io/a", "{\"authorization\":\"Bearer " + repeat('u', 1500) + "\"}", 200, "{\"date\":\"Wed\"}", "{}");
        String jwt = FalsePositiveVerifierPromptHandler.buildPrompt("ADD_JKU_TO_JWT", template("ADD_JKU_TO_JWT", "NO_AUTH", CHANGED), original, java.util.Collections.singletonList(attack));
        assertTrue(jwt.contains(TOKEN) && jwt.contains(repeat('u', 1500)), "JWT tests see whole tokens");
        String headers = FalsePositiveVerifierPromptHandler.buildPrompt("MUST_CONTAIN_RESPONSE_HEADERS",
            template("MUST_CONTAIN_RESPONSE_HEADERS", "MHH", UNCHANGED), original, java.util.Collections.singletonList(attack));
        assertTrue(headers.contains("\"date\":\"Wed\""), "header tests see every response header");
    }

    @Test
    void attemptsWithTheSameResponseAreNotRepeated() throws Exception {
        String original = exchange("https://x.io/a", "{}", 200, "{}", "{\"lang\":\"en\"}");
        java.util.List<String> attempts = java.util.Arrays.asList(
            exchange("https://x.io/a?f=../etc/passwd", "{}", 200, "{}", "{\"lang\":\"en\",\"at\":\"2026-09-16T09:25:07Z\"}"),
            exchange("https://x.io/a?f=../../etc/passwd", "{}", 200, "{}", "{\"lang\":\"en\",\"at\":\"2026-09-16T09:25:09Z\"}"),
            exchange("https://x.io/a?f=/etc/hosts", "{}", 500, "{}", "{\"error\":\"x\"}"));
        String prompt = FalsePositiveVerifierPromptHandler.buildPrompt("LFI_X", template("LFI_X", "LFI", CHANGED), original, attempts);
        assertTrue(prompt.contains("Attack attempt 1 of 3"), prompt);
        assertFalse(prompt.contains("Attack attempt 2 of 3"), "same status and body (timestamps ignored) as attempt 1");
        assertTrue(prompt.contains("Attack attempt 3 of 3"), prompt);
        assertTrue(prompt.contains("1 more flagged attempt got the same status and body"), prompt);
    }

    // --- proof class, chosen from the template's category ---

    private static String template(String id, String category, String execute) {
        return "id: " + id + "\ninfo:\n  name: \"" + id + "\"\n  category:\n    name: " + category + "\n"
            + "execute:\n" + execute + "\nvalidate:\n  response_code:\n    gte: 200\n";
    }

    private static final String UNCHANGED = "  type: single\n  requests:\n    - req: []\n";
    private static final String CHANGED = "  type: single\n  requests:\n    - req:\n        - modify_url: /v1/\n";

    @Test
    void proofClassComesFromTheCategory() {
        assertEquals(FalsePositiveProofRules.HEADERS, FalsePositiveProofRules.detectClass(template("MUST_CONTAIN_REQUEST_HEADERS", "MHH", UNCHANGED)));
        assertEquals(FalsePositiveProofRules.ACCEPTANCE, FalsePositiveProofRules.detectClass(template("OLD_VERSION_API_TEST", "IIM", CHANGED)));
        assertEquals(FalsePositiveProofRules.JWT, FalsePositiveProofRules.detectClass(template("ADD_JKU_TO_JWT", "NO_AUTH", CHANGED)));
        assertEquals(FalsePositiveProofRules.AUTH_BOLA, FalsePositiveProofRules.detectClass(template("REMOVE_TOKENS", "NO_AUTH", CHANGED)));
        assertEquals(FalsePositiveProofRules.AUTH_BOLA, FalsePositiveProofRules.detectClass(template("REPLACE_AUTH_TOKEN", "BOLA", CHANGED)));
        assertEquals(FalsePositiveProofRules.INJECTION, FalsePositiveProofRules.detectClass(template("FILE_INCLUSION_NEW_PARAM", "LFI", CHANGED)));
        assertEquals(FalsePositiveProofRules.HEADERS, FalsePositiveProofRules.detectClass(template("SM_HEADER", "SM", UNCHANGED)));
        assertEquals(FalsePositiveProofRules.EXPOSURE, FalsePositiveProofRules.detectClass(template("AIRFLOW_CONFIGURATION_EXPOSURE", "SM", CHANGED)));
        assertEquals(FalsePositiveProofRules.GENERAL, FalsePositiveProofRules.detectClass("not: [valid"));
        assertEquals(FalsePositiveProofRules.GENERAL, FalsePositiveProofRules.detectClass(null));
    }

    @Test
    void promptCarriesOnlyThatClassesRules() throws Exception {
        String prompt = FalsePositiveVerifierPromptHandler.buildPrompt("OLD_VERSION_API_TEST", template("OLD_VERSION_API_TEST", "IIM", CHANGED), null, java.util.Collections.singletonList(testResultMessage("", 200, "{}")));
        assertTrue(prompt.contains("Proof rules - acceptance"));
        assertFalse(prompt.contains("Proof rules - injection"));
        assertFalse(prompt.contains("Proof rules - JWT"));
    }

    // --- facts computed in code ---

    private static String exchange(String url, String headers, int status, String responseHeaders, String body) throws Exception {
        return new org.json.JSONObject()
            .put("request", new org.json.JSONObject().put("method", "GET").put("url", url).put("headers", headers).put("body", ""))
            .put("response", new org.json.JSONObject().put("statusCode", status).put("headers", responseHeaders).put("body", body))
            .toString();
    }

    @Test
    void factsIgnoreAktoHeadersAndReportIdenticalTokens() throws Exception {
        String baseline = exchange("https://x.io/v2/a?q=1", "{\"authorization\":\"Bearer t1\"}", 200, "{\"content-type\":\"json\"}", "{\"id\":1}");
        String attack = exchange("https://x.io/v2/a?q=1", "{\"authorization\":\"Bearer t1\",\"x-akto-ignore\":\"0\"}", 200, "{\"content-type\":\"json\"}", "{\"id\":1}");
        String facts = ExchangeFacts.describe(baseline, attack);
        assertTrue(facts.contains("- Request vs baseline: identical, apart from Akto's own headers; credential header authorization: identical to the baseline"), facts);
        assertTrue(facts.contains("Response status: 200 (baseline 200, same)"), facts);
        assertTrue(facts.contains("Response body vs baseline: same fields and values"), facts);
    }

    @Test
    void factsShowPathChangesAndBodyDifferences() throws Exception {
        String baseline = exchange("https://x.io/v2/a", "{}", 200, "{\"x-frame-options\":\"DENY\"}", "{\"balance\":20643982,\"at\":\"2026-09-16T09:16:44Z\"}");
        String attack = exchange("https://x.io/v1/a", "{}", 200, "{}", "{\"balance\":0,\"at\":\"2026-09-16T09:25:07Z\"}");
        String facts = ExchangeFacts.describe(baseline, attack);
        assertTrue(facts.contains("path: https://x.io/v2/a -> https://x.io/v1/a"), facts);
        assertTrue(facts.contains("x-frame-options removed"), facts);
        assertTrue(facts.contains("Response body fields changed: balance (20643982 -> 0)"), facts);
        assertFalse(facts.contains("at ("), facts);
    }

    // --- validate markers: what the scanner actually matched ---

    private static final String MARKER_TEMPLATE = "id: LFI_IN_HEADERS_LINUX\ninfo:\n  category:\n    name: LFI\n"
        + "validate:\n  response_payload:\n    contains_either:\n      - \"root:x:0:0:\"\n      - \"LANG=\"\n"
        + "    not_contains: ${urlVar}\n    not_contains:\n      - /etc/passwd\n";
    private static final String DEEPLINK = "{\"widgets\":[{\"deeplink\":\"gojek://web?url=https://x.co/gold_onboarding_page.html?lang=en\"}]";

    @Test
    void markerAlreadyInTheOriginalIsReported() throws Exception {
        String original = exchange("https://x.io/balance", "{}", 200, "{}", DEEPLINK + ",\"amount\":20643982}");
        String attack = exchange("https://x.io/balance", "{\"user-agent\":\"..%c0%afdocker.sock\"}", 200, "{}", DEEPLINK + ",\"amount\":0}");
        String markers = ExchangeFacts.validateMarkers(MARKER_TEMPLATE, original, attack);
        assertTrue(markers.contains("\"LANG=\" in: ...") && markers.contains("gold_onboarding_page.html?lang=en"), markers);
        assertTrue(markers.contains("(also in the original response)"), markers);
        assertTrue(markers.contains("Every matched marker is also in the original response"), markers);
        assertFalse(markers.contains("root:x:0:0:\" in"), markers);

        String prompt = FalsePositiveVerifierPromptHandler.buildPrompt("LFI_IN_HEADERS_LINUX", MARKER_TEMPLATE, original,
            java.util.Collections.singletonList(attack));
        assertTrue(prompt.contains("- Validate markers matched in the attack response body"), prompt);
        assertTrue(prompt.contains("ordinary data, e.g. a URL parameter like ?lang=en"), prompt);
    }

    @Test
    void markerTheAttackCausedIsReportedAsNew() throws Exception {
        String original = exchange("https://x.io/file", "{}", 200, "{}", "{\"name\":\"report\"}");
        String attack = exchange("https://x.io/file?f=../../etc/passwd", "{}", 200, "{}", "{\"content\":\"root:x:0:0:root:/root:/bin/bash\"}");
        String markers = ExchangeFacts.validateMarkers(MARKER_TEMPLATE, original, attack);
        assertTrue(markers.contains("\"root:x:0:0:\" in: {\"content\":\"root:x:0:0:root"), markers);
        assertTrue(markers.contains("(NOT in the original response)"), markers);
        assertFalse(markers.contains("Every matched marker"), markers);
    }

    @Test
    void regexMarkersAndMissingMatchesAreReported() throws Exception {
        String original = exchange("https://x.io/a", "{}", 200, "{}", "{}");
        assertTrue(ExchangeFacts.validateMarkers(MARKER_TEMPLATE, original, exchange("https://x.io/a", "{}", 200, "{}", "{\"ok\":1}"))
            .contains("none of the template's 2 response markers appear"));
        String regex = ExchangeFacts.validateMarkers(LFI_TEMPLATE, original,
            exchange("https://x.io/a", "{}", 200, "{}", "root:x:0:0:root:/root"));
        assertTrue(regex.contains("\"regex root:.*:0:0:\" in: root:x:0:0:"), regex);
        assertEquals("", ExchangeFacts.validateMarkers(BOLA_TEMPLATE, original, original), "no response markers in the template");
    }

    @Test
    void factsFlagEchoOnlyResponses() throws Exception {
        String baseline = exchange("https://x.io/a?file=report", "{}", 200, "{}", "{\"file\":\"report\"}");
        String attack = exchange("https://x.io/a?file=../../etc/passwd", "{}", 200, "{}", "{\"file\":\"../../etc/passwd\"}");
        String facts = ExchangeFacts.describe(baseline, attack);
        assertTrue(facts.contains("query param changed: file (report -> ../../etc/passwd)"), facts);
        assertTrue(facts.contains("differs from the baseline only by echoing values the attack sent"), facts);
        assertEquals("", ExchangeFacts.describe(null, attack));
    }
}

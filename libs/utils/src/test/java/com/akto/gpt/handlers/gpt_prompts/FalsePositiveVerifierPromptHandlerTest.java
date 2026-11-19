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
        assertTrue(prompt.contains("Answer TRUE_POSITIVE only when the evidence shows the proof"), prompt);
        assertTrue(prompt.contains("If your confidence in TRUE_POSITIVE would be below 80, answer\n  FALSE_POSITIVE."), prompt);
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
        assertTrue(formatted.contains(" chars truncated]..."), formatted.substring(0, 200));
        assertTrue(formatted.length() < FalsePositiveVerifierPromptHandler.MAX_REQUEST_CHARS
            + FalsePositiveVerifierPromptHandler.MAX_RESPONSE_CHARS + 200);
    }

    @Test
    void longResponseKeepsItsEndWhereAStreamedAnswerFinishes() throws Exception {
        String stream = "event: status\n" + repeat('s', 60000) + "\ndata: {\"content\":\"final model answer\"}";
        String formatted = FalsePositiveVerifierPromptHandler.formatExchange(
            testResultMessage("{}", 200, stream), null, false, false);
        assertTrue(formatted.contains("event: status"), "the start is kept");
        assertTrue(formatted.contains("final model answer"), "the end is kept");
        assertTrue(formatted.contains(" chars truncated]..."));
        String response = formatted.substring(formatted.indexOf("RESPONSE:"));
        assertTrue(response.length() < FalsePositiveVerifierPromptHandler.MAX_RESPONSE_CHARS + 100);
    }

    @Test
    void regexWithYamlControlCharactersStaysReadable() {
        // "\b" in a double-quoted YAML string is a backspace; it must not be dumped as !!binary.
        String template = "id: MISCONFIGURED_X_FRAME_OPTIONS_HEADER\napi_selection_filters:\n  response_headers:\n"
            + "    for_one:\n      value:\n        regex: \"^(?!.*\\bDENY\\b).*\"\n";
        String compact = FalsePositiveVerifierPromptHandler.compactTemplate(template);
        assertFalse(compact.contains("!!binary"), compact);
        assertTrue(compact.contains("\\bDENY\\b"), compact);
    }

    @Test
    void owaspLlmCodesAreAiTests() {
        assertEquals(FalsePositiveProofRules.AI, FalsePositiveProofRules.detectClass(template("SystemPromptLeakage", "LLM07", CHANGED)));
        assertEquals(FalsePositiveProofRules.AI, FalsePositiveProofRules.detectClass(template("BUSINESS_ALIGNMENT_FILE_READ", "AGENT_GOAL_HIJACK", CHANGED)));
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
        return template(id, category, execute, "  response_code:\n    gte: 200\n");
    }

    private static String template(String id, String category, String execute, String validate) {
        return "id: " + id + "\ninfo:\n  name: \"" + id + "\"\n  category:\n    name: " + category + "\n"
            + "execute:\n" + execute + "\nvalidate:\n" + validate;
    }

    private static final String PAYLOAD_MARKER = "  response_payload:\n    contains_all:\n      - \"[core]\"\n";
    private static final String LOCATION_HEADER = "  response_headers:\n    for_one:\n      key:\n        eq: location\n"
        + "      value:\n        contains_either:\n          - evil.com\n";
    private static final String NOT_IN_TRAFFIC = "  url:\n    endpoint_in_traffic_context: false\n";
    private static final String VERSION_REWRITE = "  type: single\n  requests:\n    - req:\n        - modify_url:\n"
        + "            regex_replace:\n              regex: v([2-9])/\n              replace_with: v1/\n";
    private static final String VERSION_REWRITE_OTHER_TOKEN = VERSION_REWRITE + "        - replace_auth_header: true\n";
    // A multi-step probe whose only check is the step's own validate, raising the finding with success: vulnerable.
    private static final String STEP_CHECK = "  type: multiple\n  requests:\n    - req:\n        - modify_url: /.env\n"
        + "        - validate:\n            response_payload:\n              contains_all:\n                - APP_NAME\n"
        + "        - success: vulnerable\n        - failure: exit\n";
    private static final String GET_PROBE = "  type: single\n  requests:\n    - req:\n        - modify_url: /airflow.cfg\n        - modify_method: GET\n";

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
        assertEquals(FalsePositiveProofRules.EXPOSURE, FalsePositiveProofRules.detectClass(template("AIRFLOW_CONFIGURATION_EXPOSURE", "SM", CHANGED, PAYLOAD_MARKER)));
        assertEquals(FalsePositiveProofRules.RATE_LIMIT, FalsePositiveProofRules.detectClass(template("REMOVE_CAPTCHA", "RL", CHANGED)));
        assertEquals(FalsePositiveProofRules.INJECTION, FalsePositiveProofRules.detectClass(template("NoSQLInjection", "NOSQL_INJECTION", CHANGED)));
        assertEquals(FalsePositiveProofRules.AUTH_BOLA, FalsePositiveProofRules.detectClass(template("REPLACE_AUTH_TOKEN", "BOLA_AGENTIC", CHANGED)));
        assertEquals(FalsePositiveProofRules.AI, FalsePositiveProofRules.detectClass(template("PROMPT_LEAK", "PROMPT_INJECTION", CHANGED)));
        assertEquals(FalsePositiveProofRules.AI, FalsePositiveProofRules.detectClass(template("MCP_TOOL_CALL", "MCP_AUTH", CHANGED)));
        assertEquals(FalsePositiveProofRules.GENERAL, FalsePositiveProofRules.detectClass("not: [valid"));
        assertEquals(FalsePositiveProofRules.GENERAL, FalsePositiveProofRules.detectClass(null));
    }

    @Test
    void proofClassFollowsWhatTheTestChecksWhenTheCategoryIsMixed() {
        // NO_AUTH holds injection and CSRF tests besides auth bypass
        assertEquals(FalsePositiveProofRules.INJECTION, FalsePositiveProofRules.detectClass(template("AUTH_BYPASS_SQL_INJECTION", "NO_AUTH", CHANGED)));
        assertEquals(FalsePositiveProofRules.INJECTION, FalsePositiveProofRules.detectClass(template("SQLI_ERROR_BASED_PARAM_MYSQL", "NO_AUTH", CHANGED)));
        assertEquals(FalsePositiveProofRules.CSRF, FalsePositiveProofRules.detectClass(template("REMOVE_CSRF", "NO_AUTH", CHANGED)));
        assertEquals(FalsePositiveProofRules.HEADERS, FalsePositiveProofRules.detectClass(template("EXPIRES_MAX_AGE_CHECK", "NO_AUTH", UNCHANGED,
            "  response_headers:\n    for_one:\n      key:\n        eq: set-cookie\n")));
        // SM: path probes that also set the method are exposure, not acceptance
        assertEquals(FalsePositiveProofRules.EXPOSURE, FalsePositiveProofRules.detectClass(template("ANSIBLE_CONFIG_EXPOSURE", "SM", GET_PROBE, PAYLOAD_MARKER)));
        assertEquals(FalsePositiveProofRules.REDIRECT, FalsePositiveProofRules.detectClass(template("OPEN_REDIRECT", "SM", CHANGED, LOCATION_HEADER)));
        assertEquals(FalsePositiveProofRules.DEFAULT_LOGIN, FalsePositiveProofRules.detectClass(template("RABBIT_MQ_DEFAULT_LOGIN", "SM", CHANGED, PAYLOAD_MARKER)));
        assertEquals(FalsePositiveProofRules.ACCEPTANCE, FalsePositiveProofRules.detectClass(template("FIREBASE_UNAUTHENTICATED", "SM", CHANGED)));
        // an endpoint not in the traffic answering is acceptance, except for BFLA whose proof is the privileged function
        assertEquals(FalsePositiveProofRules.ACCEPTANCE, FalsePositiveProofRules.detectClass(template("REPLACE_AUTH_TOKEN_OLD_VERSION", "BOLA", VERSION_REWRITE, NOT_IN_TRAFFIC)));
        assertEquals(FalsePositiveProofRules.AUTH_BOLA, FalsePositiveProofRules.detectClass(template("BOLA_URL_REPLACE_API_VERSION", "BOLA", VERSION_REWRITE_OTHER_TOKEN, NOT_IN_TRAFFIC)));
        assertEquals(FalsePositiveProofRules.ACCEPTANCE, FalsePositiveProofRules.detectClass(template("SSL_ENABLE_CHECK", "NO_AUTH", CHANGED, NOT_IN_TRAFFIC)));
        assertEquals(FalsePositiveProofRules.BFLA, FalsePositiveProofRules.detectClass(template("BFLA_INSERT_ADMIN_IN_URL_PATHS", "BFLA", CHANGED, NOT_IN_TRAFFIC)));
    }

    @Test
    void proofClassReadsStepValidatesAndTestNames() {
        assertEquals(FalsePositiveProofRules.EXPOSURE, FalsePositiveProofRules.detectClass(
            "id: LARAVEL_ENV\ninfo:\n  category:\n    name: SM\nexecute:\n" + STEP_CHECK), "the check lives in the step");
        assertEquals(FalsePositiveProofRules.ENUMERATION, FalsePositiveProofRules.detectClass(template("USER_ENUM_HTTP_CODES", "NO_AUTH", CHANGED)));
        assertEquals(FalsePositiveProofRules.ENUMERATION, FalsePositiveProofRules.detectClass(template("INVALID_PRODUCT_ID_ENUMERATION", "INPUT", CHANGED)));
        assertEquals(FalsePositiveProofRules.EXPOSURE, FalsePositiveProofRules.detectClass(template("APACHE_FILENAME_ENUM", "SM", CHANGED, PAYLOAD_MARKER)));
        assertEquals(FalsePositiveProofRules.RATE_LIMIT, FalsePositiveProofRules.detectClass(template("PASSWD_CHANGE_BRUTE_FORCE", "NO_AUTH", CHANGED)));
        assertEquals(FalsePositiveProofRules.INJECTION, FalsePositiveProofRules.detectClass(template("LOG4J_HEADER", "NO_AUTH", CHANGED)));
        assertEquals(FalsePositiveProofRules.CSRF, FalsePositiveProofRules.detectClass(template("REMOVE_XSRF", "NO_AUTH", CHANGED)));
        assertEquals(FalsePositiveProofRules.DEFAULT_LOGIN, FalsePositiveProofRules.detectClass(template("BYPASS_BASIC_AUTH_USING_WEAK_PASSWORDS", "INPUT", CHANGED)));
        // a header regex that merely mentions Location is not a redirect check
        assertEquals(FalsePositiveProofRules.INJECTION, FalsePositiveProofRules.detectClass(template("XXE_SERVER_INFORMATION_LEAK", "INJECT", CHANGED,
            "  response_headers:\n    regex: \"(?:Server|Location):\"\n")));
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
        assertTrue(prompt.contains("(e.g. a URL parameter like ?lang=en"), prompt);
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

    private static String post(String url, String headers, String requestBody, String responseBody) throws Exception {
        return new org.json.JSONObject()
            .put("request", new org.json.JSONObject().put("method", "POST").put("url", url).put("headers", headers).put("body", requestBody))
            .put("response", new org.json.JSONObject().put("statusCode", 200).put("headers", "{}").put("body", responseBody))
            .toString();
    }

    private static final String CARD_ORDER = "{\"shippingAddress\":{\"zipCode\":\"V0E 1B0\",\"city\":\"string\",\"stateId\":4199,\"countryId\":40},"
        + "\"totalCards\":1,\"paymentId\":\"\"}";
    private static final String CARD_ORDER_ECHO = "{\"shippingAddress\":{\"city\":\"string\",\"stateId\":4199,\"zipCode\":\"V0E 1B0\",\"countryId\":40}}";

    @Test
    void responseThatOnlyEchoesTheRequestIsReported() throws Exception {
        String attack = post("https://x.io/driver/cards/order", "{\"cp-session-token\":\"attacker\"}", CARD_ORDER, CARD_ORDER_ECHO);
        assertEquals("- Response body: every value in it was sent in this request, so it only echoes the request "
            + "and holds no data of the server's own\n", ExchangeFacts.responseEcho(attack));

        String partly = post("https://x.io/users/42", "{}", "{\"name\":\"bob\"}", "{\"id\":42,\"name\":\"bob\",\"email\":\"bob@x.com\"}");
        assertEquals("- Response body: 2 of 3 values were sent in this request; fields with values the request did not send: email\n",
            ExchangeFacts.responseEcho(partly), "42 comes from the path");
        assertEquals("", ExchangeFacts.responseEcho(post("https://x.io/me", "{}", "", "{\"email\":\"bob@x.com\"}")),
            "nothing echoed, nothing to report");
        assertEquals("", ExchangeFacts.responseEcho(post("https://x.io/me", "{}", "", "<html></html>")));
    }

    @Test
    void replacedTokenOnACallerScopedEndpointCarriesTheFactsAndRulesToDismissIt() throws Exception {
        String bolaTemplate = template("REPLACE_AUTH_TOKEN", "BOLA", "  type: single\n  requests:\n    - req:\n        - replace_auth_header: true\n",
            "  response_payload:\n    percentage_match:\n      gte: 90\n");
        String original = post("https://x.io/driver/cards/order", "{\"cp-session-token\":\"victim\"}", CARD_ORDER, CARD_ORDER_ECHO);
        String attack = post("https://x.io/driver/cards/order", "{\"cp-session-token\":\"attacker\"}", CARD_ORDER, CARD_ORDER_ECHO);
        String prompt = FalsePositiveVerifierPromptHandler.buildPrompt("REPLACE_AUTH_TOKEN", bolaTemplate, original,
            java.util.Collections.singletonList(attack));
        assertTrue(prompt.contains("credential header cp-session-token: changed"), "a custom session header is a credential: " + prompt);
        assertTrue(prompt.contains("so it only echoes the request and holds no data of the server's own"), prompt);
        assertTrue(prompt.contains("With no such id (only lookup values such as country,\n  state, page, or none), the endpoint acts for the token's owner"), prompt);
        assertTrue(prompt.contains("the response only echoes sent values"), prompt);
        assertTrue(prompt.contains("the response shows a different identity or data than the baseline"), prompt);
    }

    @Test
    void swappedSessionAnsweredForItsOwnUserCarriesTheFactsAndRulesToDismissIt() throws Exception {
        String bolaTemplate = template("SMART_REPLACE_AUTH_TOKEN", "BOLA", "  type: single\n  requests:\n    - req:\n        - replace_auth_header: true\n",
            "  response_code:\n    gte: 200\n");
        String original = exchange("https://x.io/driver/token/validate",
            "{\"authorization\":\"Bearer victim\",\"cookie\":\"auth-session=victim; _ga=1\",\"x-csrf-token\":\"c1\"}", 200, "{}", "{\"userId\":24332241}");
        String attack = exchange("https://x.io/driver/token/validate",
            "{\"authorization\":\"Bearer victim\",\"cookie\":\"auth-session=attacker; _ga=1\",\"x-csrf-token\":\"c1\"}", 200, "{}", "{\"userId\":104089101}");
        String facts = ExchangeFacts.describe(original, attack);
        assertTrue(facts.contains("credential header authorization: identical to the baseline"), facts);
        assertTrue(facts.contains("credential header cookie: changed"), facts);
        assertFalse(facts.contains("credential header x-csrf-token"), "an anti-CSRF token is not a credential: " + facts);
        assertTrue(facts.contains("userId (24332241 -> 104089101)"), facts);

        String prompt = FalsePositiveVerifierPromptHandler.buildPrompt("SMART_REPLACE_AUTH_TOKEN", bolaTemplate, original,
            java.util.Collections.singletonList(attack));
        assertTrue(prompt.contains("A different user, account, owner or tenant id is not noise"), prompt);
        assertTrue(prompt.contains("a credential header or cookie the server could authenticate with is unchanged"), prompt);
        assertTrue(prompt.contains("the response shows a different identity or data than the baseline"), prompt);
    }

    @Test
    void identicalCookieHeaderListsItsCookieNames() throws Exception {
        String original = exchange("https://x.io/a", "{\"cookie\":\"sid=abc; _ga=GA1\"}", 200, "{}", "{}");
        assertTrue(ExchangeFacts.describe(original, original).contains("credential header cookie: identical to the baseline (cookies: sid, _ga)"));
    }

    private static String digest(String user, String nonce, String response) {
        return "Digest username=\\\"" + user + "\\\", realm=\\\"MMS Public API\\\", nonce=\\\"" + nonce
            + "\\\", uri=\\\"/api/atlas/v2/groups/69f8/backupCompliancePolicy?envelope=false\\\", response=\\\"" + response
            + "\\\", qop=auth, nc=00000001, cnonce=\\\"" + nonce + "c\\\", algorithm=MD5";
    }

    @Test
    void digestSignedAgainForTheSameUserListsItsPartsSoItIsNotReadAsAnotherUser() throws Exception {
        String fuzzTemplate = template("FUZZ_ID", "BOLA", "  type: single\n  requests:\n    - req:\n        - modify_body_param:\n            userKey: ${random_ids}\n",
            "  response_code:\n    gte: 200\n");
        String original = exchange("https://cloud.mongodb.com/api/atlas/v2/groups/69f8/backupCompliancePolicy",
            "{\"authorization\":\"" + digest("joiardxg", "AAA111", "aaa") + "\"}", 200, "{}", "{\"state\":\"ACTIVE\",\"updatedUser\":\"apiteam@swagger.io\"}");
        String attack = exchange("https://cloud.mongodb.com/api/atlas/v2/groups/69f8/backupCompliancePolicy",
            "{\"authorization\":\"" + digest("joiardxg", "BBB222", "bbb") + "\",\"x-akto-ignore\":\"0\"}", 200, "{}", "{\"state\":\"UPDATING\",\"updatedUser\":\"joiardxg\"}");
        String facts = ExchangeFacts.describe(original, attack);
        assertTrue(facts.contains("credential header authorization: changed (parts the same: scheme=Digest, username=joiardxg, realm=MMS Public API"), facts);
        assertTrue(facts.contains("parts that differ: nonce (AAA111 -> BBB222), response (aaa -> bbb), cnonce (AAA111c -> BBB222c))"), facts);

        String prompt = FalsePositiveVerifierPromptHandler.buildPrompt("FUZZ_ID", fuzzTemplate, original,
            java.util.Collections.singletonList(attack));
        assertTrue(prompt.contains("it is the same user's credential\n  signed again for this request"), prompt);
        assertTrue(prompt.contains("credentials and object id equal the baseline"), prompt);
    }

    @Test
    void redactedBaselineValuesAndNullQueryParamsAreNotReportedAsChanges() throws Exception {
        // Sample data as stored: the token redacted by Akto. The attack: same login, two body fields added.
        String baseline = new org.json.JSONObject()
            .put("method", "POST").put("path", "https://juiceshop.akto.io/rest/user/login")
            .put("requestHeaders", "{\"cookie\":\"language=en; continueCode=v7Bm\"}")
            .put("requestPayload", "{\"email\":\"victim@gmail.com\",\"password\":\"victim123\"}")
            .put("statusCode", "200").put("responseHeaders", "{}")
            .put("responsePayload", "{\"authentication\":{\"token\":\"****\",\"bid\":6,\"umail\":\"victim@gmail.com\"}}")
            .toString();
        String attack = new org.json.JSONObject()
            .put("request", new org.json.JSONObject().put("method", "POST").put("url", "https://juiceshop.akto.io/rest/user/login")
                .put("queryParams", org.json.JSONObject.NULL)
                .put("headers", "{\"cookie\":\"language=en; continueCode=v7Bm\",\"x-akto-ignore\":\"0\"}")
                .put("body", "{\"email\":\"victim@gmail.com\",\"password\":\"victim123\",\"hello\":\"hi there\"}"))
            .put("response", new org.json.JSONObject().put("statusCode", 200).put("headers", "{}")
                .put("body", "{\"authentication\":{\"token\":\"eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.e30.sig\",\"bid\":7,\"umail\":\"victim@gmail.com\"}}"))
            .toString();
        String facts = ExchangeFacts.describe(baseline, attack);
        assertFalse(facts.contains("query param"), "a JSON null queryParams is no query param: " + facts);
        assertTrue(facts.contains("credential header cookie: identical to the baseline"), facts);
        assertTrue(facts.contains("body field added: hello=hi there"), facts);
        assertTrue(facts.contains("Response body fields changed: authentication.bid (6 -> 7)"), facts);
        assertFalse(facts.contains("authentication.token (****"), "a redacted baseline value is not a change: " + facts);
        assertTrue(facts.contains("Response body fields redacted in the baseline (their original values are unknown, "
            + "so a value there now is not a change): authentication.token"), facts);
    }

    @Test
    void redactedCredentialInTheBaselineIsNotCalledChanged() throws Exception {
        String original = exchange("https://x.io/a", "{\"authorization\":\"Bearer ****\",\"cookie\":\"sid=****; _ga=1\"}", 200, "{}", "{}");
        String attack = exchange("https://x.io/a", "{\"authorization\":\"Bearer abc\",\"cookie\":\"sid=abc; _ga=1\"}", 200, "{}", "{}");
        String facts = ExchangeFacts.describe(original, attack);
        assertTrue(facts.contains("credential header authorization: redacted in the baseline, cannot be compared"), facts);
        assertTrue(facts.contains("credential header cookie: changed (parts the same: _ga=1; parts that differ: none; "
            + "redacted in the baseline, cannot be compared: sid)"), facts);
    }

    private static final String RESPONSE_HEADERS_TEMPLATE = "id: MUST_CONTAIN_RESPONSE_HEADERS\ninfo:\n  category:\n    name: MHH\n"
        + "execute:\n  type: single\n  requests:\n    - req: []\n"
        + "validate:\n  response_code:\n    gte: 200\n  response_headers:\n    for_one:\n      key:\n        not_contains:\n"
        + "          - Content-Security-Policy\n          - Strict-Transport-Security\n          - X-Frame-Options\n";
    private static final String REQUEST_HEADERS_TEMPLATE = "id: MUST_CONTAIN_REQUEST_HEADERS\ninfo:\n  category:\n    name: MHH\n"
        + "execute:\n  type: single\n  requests:\n    - req: []\n"
        + "validate:\n  response_code:\n    gte: 200\n  request_headers:\n    not_contains:\n      - sec-fetch-site\n";

    @Test
    void responseHeaderTemplateGetsTheMissingHeadersAndItsRule() throws Exception {
        // Missing in the baseline too: still missing.
        String original = exchange("https://x.io/reviews", "{}", 200, "{\"X-Frame-Options\":\"SAMEORIGIN\"}", "{}");
        String prompt = FalsePositiveVerifierPromptHandler.buildPrompt("MUST_CONTAIN_RESPONSE_HEADERS", RESPONSE_HEADERS_TEMPLATE,
            original, java.util.Collections.singletonList(original));
        assertTrue(prompt.contains("- Headers the template's response_headers check lists, in the attack response: "
            + "missing: Content-Security-Policy, Strict-Transport-Security; present: X-Frame-Options"), prompt);
        assertTrue(prompt.contains("a header missing from both is still missing"), prompt);
        assertFalse(prompt.contains("checks the headers of the request"), prompt);
    }

    @Test
    void requestHeaderTemplateIsJudgedOnTheScannersRequest() throws Exception {
        String original = exchange("https://x.io/reviews", "{\"accept\":\"*/*\"}", 200, "{}", "{}");
        String prompt = FalsePositiveVerifierPromptHandler.buildPrompt("MUST_CONTAIN_REQUEST_HEADERS", REQUEST_HEADERS_TEMPLATE,
            original, java.util.Collections.singletonList(original));
        assertTrue(prompt.contains("- Headers the template's request_headers check lists, in the attack request (sent by "
            + "Akto's scanner, not by a user's browser): missing: sec-fetch-site; present: none"), prompt);
        assertTrue(prompt.contains("Judge the request headers, never the response"), prompt);
        assertFalse(prompt.contains("a header missing from both is still missing"), prompt);
    }

    @Test
    void credentialRemovalTemplateProvesAWriteByItsStatus() {
        String removeTokens = template("REMOVE_TOKENS", "NO_AUTH", "  type: single\n  requests:\n    - req:\n        - remove_auth_header: true\n",
            "  response_code:\n    gte: 200\n");
        String rules = FalsePositiveProofRules.templateRules(removeTokens);
        assertTrue(rules.startsWith("Rules for this template:\n"), rules);
        assertTrue(rules.contains("is proven even when its body is a\n  generic success"), rules);
        String addsHeader = template("REMOVE_TOKENS_CUSTOM_1769079120", "NO_AUTH", "  type: single\n  requests:\n  - req:\n    - add_header:\n        a: b\n",
            "  response_code:\n    gte: 200\n");
        assertEquals("", FalsePositiveProofRules.templateRules(addsHeader), "a template that removes nothing gets no extra rules");
    }

    @Test
    void credentialPartsShowAnotherUserAndKeepSecretsOut() {
        assertEquals(" (parts the same: scheme=Digest, realm=r; parts that differ: username (alice -> bob))",
            ExchangeFacts.credentialDiff("Digest username=\"alice\", realm=\"r\"", "Digest username=\"bob\", realm=\"r\""));
        String basic = ExchangeFacts.credentialDiff("Basic " + java.util.Base64.getEncoder().encodeToString("alice:pw1".getBytes()),
            "Basic " + java.util.Base64.getEncoder().encodeToString("alice:pw2".getBytes()));
        assertEquals(" (parts the same: scheme=Basic, user=alice; parts that differ: password)", basic);
        String jwt1 = "eyJhbGciOiJIUzI1NiJ9." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"42\",\"iat\":1}".getBytes()) + ".s1";
        String jwt2 = "eyJhbGciOiJIUzI1NiJ9." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"42\",\"iat\":2}".getBytes()) + ".s2";
        assertEquals(" (parts the same: scheme=Bearer, jwt.sub=42; parts that differ: jwt.iat (1 -> 2))",
            ExchangeFacts.credentialDiff("Bearer " + jwt1, "Bearer " + jwt2));
        assertEquals(" (parts the same: _ga=1; parts that differ: auth-session (victim -> attacker))",
            ExchangeFacts.credentialDiff("auth-session=victim; _ga=1", "auth-session=attacker; _ga=1"));
        assertEquals("", ExchangeFacts.credentialDiff("Bearer t1", "Bearer t2"), "an opaque token has no parts");
    }

    // --- binary verdicts and passive tests ---

    @Test
    void onlyTrueOrFalsePositiveIsAccepted() {
        BasicDBObject r = FalsePositiveVerifierPromptHandler.parseVerdict(
            "{\"verdict\":\"cannot verify\",\"confidence\":40,\"reason\":\"Callback not shown\"}");
        assertTrue(r.containsField("error"), "a third verdict is not accepted");
    }

    @Test
    void unprovenFindingsAreFalsePositivesAndCallbacksAreProof() throws Exception {
        String prompt = FalsePositiveVerifierPromptHandler.buildPrompt(BOLA_ID, BOLA_TEMPLATE, null,
            java.util.Collections.singletonList(testResultMessage("", 200, "{}")));
        assertTrue(prompt.contains("{\"verdict\": \"TRUE_POSITIVE\" | \"FALSE_POSITIVE\", "), prompt);
        assertFalse(prompt.contains("CANNOT_VERIFY"), prompt);
        assertTrue(prompt.contains("Answer FALSE_POSITIVE in every other case"), prompt);
        assertTrue(prompt.contains("start the reason with \"Not proven:\""), prompt);
        assertTrue(prompt.contains("That callback is the proof: TRUE_POSITIVE"), prompt);
        assertFalse(prompt.contains("This test runs several steps"), "single-request tests get no step rules");
        for (String cls : new String[]{"injection", "ssrf", "auth_bola", "jwt", "csrf", "bfla", "mass_assignment",
                "input_validation", "rate_limit", "xss", "cors", "redirect", "default_login", "headers", "acceptance",
                "exposure", "enumeration", "ai", "general"}) {
            assertFalse(FalsePositiveProofRules.proofRules(cls).contains("CANNOT_VERIFY"), cls);
        }
    }

    @Test
    void passiveTestIsReadFromTheExecuteType() {
        assertTrue(FalsePositiveVerifierPromptHandler.isPassiveTest("execute:\n  type: passive\n"));
        assertTrue(FalsePositiveVerifierPromptHandler.isPassiveTest("execute:\n  type: \"Passive\"\n"));
        assertFalse(FalsePositiveVerifierPromptHandler.isPassiveTest("execute:\n  type: single\n"));
        assertFalse(FalsePositiveVerifierPromptHandler.isPassiveTest("info:\n  details: |\n    type: passive\nexecute:\n  type: single\n"),
            "only execute.type counts, not text elsewhere");
        assertFalse(FalsePositiveVerifierPromptHandler.isPassiveTest(null));
        assertFalse(FalsePositiveVerifierPromptHandler.isPassiveTest("not: [valid"));
    }

    // --- multi-step tests ---

    private static String timed(String exchange, int responseTime) throws Exception {
        return new org.json.JSONObject(exchange).put("responseTime", responseTime).toString();
    }

    private static final String NOSQLI_TEMPLATE = "id: TIME_BASED_NOSQLI\ninfo:\n  name: \"Time based NoSQL injection\"\n"
        + "  category:\n    name: INJECT\n"
        + "execute:\n  type: multiple\n  requests:\n    - req:\n        - add_header:\n            dummyKey: dummyVal\n"
        + "        - success: x2\n    - req:\n        - replace_body: '{\"$where\": \"sleep(4000)\"}'\n"
        + "validate:\n  compare_greater:\n    - ${x2.response.stats.median_response_time}\n    - 5001\n";

    @Test
    void everyStepIsShownWithItsValidateResult() throws Exception {
        String original = exchange("https://x.io/search", "{}", 200, "{}", "{\"hits\":[1]}");
        java.util.List<FalsePositiveVerifierPromptHandler.Step> steps = java.util.Arrays.asList(
            new FalsePositiveVerifierPromptHandler.Step("x1", original, java.util.Collections.singletonList(
                timed(exchange("https://x.io/search", "{}", 200, "{}", "{\"hits\":[1]}"), 210)), true, ""),
            new FalsePositiveVerifierPromptHandler.Step("x2", original, java.util.Collections.singletonList(
                timed(exchange("https://x.io/search", "{}", 200, "{}", "{\"hits\":[1]}"), 5400)), false, "response_code eq 429 failed"));
        String prompt = FalsePositiveVerifierPromptHandler.buildStepsPrompt("TIME_BASED_NOSQLI", NOSQLI_TEMPLATE, steps);
        assertTrue(prompt.contains("This test runs several steps (x1, x2, ...); judge them together."), prompt);
        assertTrue(prompt.contains("`vulnerable` (the finding is\n  raised there) or `exit` (no finding)"), prompt);
        assertTrue(prompt.contains("Step x1 (1 of 2, 1 request, step validate passed):"), prompt);
        assertTrue(prompt.contains("Step x2 (2 of 2, 1 request, step validate did not pass: response_code eq 429 failed):"), prompt);
        assertFalse(prompt.contains("got the same status and body"), "identical responses across steps are not folded");
        assertTrue(prompt.contains("- Response time: 210 ms"), prompt);
        assertTrue(prompt.contains("- Response time: 5400 ms"), prompt);
        assertTrue(prompt.indexOf("\n<<<EVIDENCE\n") < prompt.indexOf("Step x1 (")
            && prompt.indexOf("Step x2 (") < prompt.lastIndexOf("EVIDENCE>>>"), "steps sit inside the untrusted evidence block");
    }

    @Test
    void stepWithSeveralRequestsShowsTheLastAndItsTimes() throws Exception {
        String original = exchange("https://x.io/a", "{}", 200, "{}", "{}");
        java.util.List<String> messages = java.util.Arrays.asList(
            timed(exchange("https://x.io/a?p=1", "{}", 200, "{}", "{\"first\":1}"), 300),
            timed(exchange("https://x.io/a?p=2", "{}", 200, "{}", "{\"second\":1}"), 100),
            timed(exchange("https://x.io/a?p=3", "{}", 200, "{}", "{\"third\":1}"), 200),
            timed(exchange("https://x.io/a?p=4", "{}", 200, "{}", "{\"fourth\":1}"), 400));
        String prompt = FalsePositiveVerifierPromptHandler.buildStepsPrompt("T", NOSQLI_TEMPLATE, java.util.Collections.singletonList(
            new FalsePositiveVerifierPromptHandler.Step("x1", original, messages, true, null)));
        assertTrue(prompt.contains("Step x1 (1 of 1, last of 4 requests, step validate passed):"), prompt);
        assertTrue(prompt.contains("\"fourth\""), prompt);
        assertFalse(prompt.contains("\"first\""), "only the last request of a step is shown");
        assertTrue(prompt.contains("- Response times of this step's 4 requests: median 250.0 ms, min 100 ms, max 400 ms"), prompt);
    }

    @Test
    void stepsBeyondTheCapAndStepsWithoutRequestsAreReported() throws Exception {
        String original = exchange("https://x.io/a", "{}", 200, "{}", "{}");
        java.util.List<FalsePositiveVerifierPromptHandler.Step> steps = new java.util.ArrayList<>();
        steps.add(new FalsePositiveVerifierPromptHandler.Step("x1", original, null, false, null));
        for (int i = 2; i <= FalsePositiveVerifierPromptHandler.MAX_STEPS + 2; i++) {
            steps.add(new FalsePositiveVerifierPromptHandler.Step("x" + i, original,
                java.util.Collections.singletonList(exchange("https://x.io/a", "{}", 200, "{}", "{}")), true, null));
        }
        String prompt = FalsePositiveVerifierPromptHandler.buildStepsPrompt("T", NOSQLI_TEMPLATE, steps);
        assertTrue(prompt.contains("Step x1 (1 of 8, step validate did not pass): no request was stored for this step"), prompt);
        assertTrue(prompt.contains("Step x6 (6 of 8,"), prompt);
        assertFalse(prompt.contains("Step x7 ("), prompt);
        assertTrue(prompt.contains("2 more steps not shown."), prompt);
        assertFalse(prompt.contains("- Response time"), "no times stored, none reported");
    }
}

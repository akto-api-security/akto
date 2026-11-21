package com.akto.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.akto.dto.testing.WorkflowUpdatedSampleData;

/** mini-only behaviour of the libs/utils wrappers around the shared login_flow.PlaceholderResolver. */
public class UtilsPlaceholderWrapperTest {

    private static final String ORIG = "{\"method\":\"POST\",\"requestPayload\":\"\",\"path\":\"http://127.0.0.1/x\","
            + "\"requestHeaders\":\"{}\",\"type\":\"\"}";

    // ---- #[ ... ]# JS expressions: evaluated as before when an engine exists

    @Test
    public void jsEngineIsOnTheClasspath() {
        // JDK 11+ builds get the standalone nashorn-core from the nashorn-standalone profile in libs/utils/pom.xml
        assertTrue(Utils.isJsEngineAvailable(), "no Nashorn engine: is the nashorn-standalone profile active?");
    }

    @Test
    public void jsBlockIsEvaluatedAfterItsPlaceholdersLikeBefore() throws Exception {
        Map<String, Object> valuesMap = new HashMap<>();
        valuesMap.put("x5.response.header.set-cookie.client_token", "2990637_17Yr8Zvf8zncdz");
        assertEquals("{\"session_id\": \"2990637\"}", Utils.executeCode(
                "{\"session_id\": \"#[ '${x5.response.header.set-cookie.client_token}'.split('_')[0] ]#\"}", valuesMap));
        assertEquals("2-A", Utils.executeCode("#[1+1]#-#[ 'a'.toUpperCase() ]#", valuesMap));
    }

    @Test
    public void jsBlockIsEvaluatedInLoginFlowRequests() throws Exception {
        Map<String, Object> valuesMap = new HashMap<>();
        valuesMap.put("x1.response.body.n", 20);
        WorkflowUpdatedSampleData login = new WorkflowUpdatedSampleData(ORIG, "", "{}",
                "{\"a\": \"#[ ${x1.response.body.n} + 1 ]#\"}", "http://h/x?p=#[ 2 * 3 ]#");
        com.akto.dto.OriginalHttpRequest request = Utils.buildHttpRequest(login, valuesMap, "x3");
        assertEquals("{\"a\": \"21\"}", request.getBody());
        assertEquals("p=6", request.getQueryParams());
    }

    @Test
    public void valueWithQuotesStaysInsideTheJsString() throws Exception {
        Map<String, Object> valuesMap = new HashMap<>();
        valuesMap.put("x1.response.body.q", "it's \"x\"");
        assertEquals("8", Utils.executeCode("#[ '${x1.response.body.q}'.length ]#", valuesMap));
        // outside a block the ' is not escaped (valid JSON)
        assertEquals("it's \\\"x\\\"", Utils.executeCode("${x1.response.body.q}", valuesMap));
    }

    @Test
    public void resolvedValueContainingJsIsNeverEvaluated() throws Exception {
        Map<String, Object> valuesMap = new HashMap<>();
        valuesMap.put("x1.response.body.p", "#[ 1 + 1 ]#");
        assertEquals("p=#[ 1 + 1 ]#", Utils.executeCode("p=${x1.response.body.p}", valuesMap));
    }

    @Test
    public void failingScriptLeavesTheBlockAsBefore() throws Exception {
        assertEquals("a=#[ this is not js ]#", Utils.executeCode("a=#[ this is not js ]#", new HashMap<String, Object>()));
    }

    @Test
    public void noJsEngineIsAnExplicitErrorOnlyWhenABlockIsPresent() throws Exception {
        java.util.function.Supplier<javax.script.ScriptEngine> original = Utils.jsEngineSupplier;
        Utils.jsEngineSupplier = () -> null;
        try {
            Exception e = assertThrows(Exception.class,
                    () -> Utils.executeCode("{\"ts\": \"#[ Date.now() ]#\"}", new HashMap<String, Object>()));
            assertEquals(Utils.JS_ENGINE_UNAVAILABLE, e.getMessage());
            assertEquals("plain", Utils.executeCode("plain", new HashMap<String, Object>()));
            assertFalse(Utils.isJsEngineAvailable());
        } finally {
            Utils.jsEngineSupplier = original;
        }
    }

    @Test
    public void missingKeyAlwaysThrowsWithThePath() {
        Exception e = assertThrows(Exception.class, () -> Utils.replaceVariables(
                "{\"Cookie\": \"login_token=${x1.response.header.set-cookie.login_token}\"}", new HashMap<String, Object>(), false));
        assertEquals("Couldn't find x1.response.header.set-cookie.login_token (no values from x1)", e.getMessage());
    }

    @Test
    public void uniquePlaceholderIsGeneratedWithoutTouchingTheCallersMap() throws Exception {
        Map<String, Object> valuesMap = new HashMap<>();
        String first = Utils.replaceVariables("${x0.unique_email}", valuesMap, false);
        String second = Utils.replaceVariables("${x0.unique_email}", valuesMap, false);

        assertTrue(first.matches("email_\\d+"), first);
        assertNotEquals(first, second);
        assertTrue(valuesMap.isEmpty());

        // the same key twice in one template gets one value; filters still apply; "Unique" matches case-insensitively
        String twice = Utils.replaceVariables("${x0.unique_id}/${x0.unique_id}", valuesMap, false);
        assertEquals(twice.substring(0, twice.indexOf('/')), twice.substring(twice.indexOf('/') + 1));
        assertTrue(Utils.replaceVariables("${x0.unique_tag|urlencode}", valuesMap, false).matches("tag_\\d+"));
        assertTrue(Utils.replaceVariables("${x0.Unique_name}", valuesMap, false).matches("name_\\d+"));
    }

    @Test
    public void uniquePlaceholderUsesAnExistingValue() throws Exception {
        Map<String, Object> valuesMap = new HashMap<>();
        valuesMap.put("x0.unique_email", "fixed");
        assertEquals("fixed", Utils.replaceVariables("${x0.unique_email}", valuesMap, false));
    }

    @Test
    public void otherX0KeysStillFail() {
        Exception e = assertThrows(Exception.class,
                () -> Utils.replaceVariables("${x0.other}", new HashMap<String, Object>(), false));
        assertTrue(e.getMessage().startsWith("Couldn't find x0.other"), e.getMessage());
    }

    @Test
    public void exactCaseHeaderWinsAndCookieNamesKeepCase() {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("set-cookie", Collections.singletonList("a=exact"));
        headers.put("Set-Cookie", Collections.singletonList("SessionID=YWJj==; Path=/"));
        Map<String, Object> valuesMap = new HashMap<>();
        Utils.populateValuesMap(valuesMap, "{}", "x1", headers, false, null);

        assertEquals("exact", valuesMap.get("x1.response.header.set-cookie.a"));
        assertEquals("YWJj==", valuesMap.get("x1.response.header.Set-Cookie.SessionID"));
        assertFalse(valuesMap.containsKey("x1.response.header.set-cookie.SessionID"));
        assertFalse(valuesMap.containsKey("x1.response.header.Set-Cookie.sessionid"));
    }

    // ---- testValidatorCode: same semantics as before (validator true = not vulnerable), "not evaluated" is visible

    @Test
    public void validatorIsEvaluatedLikeBefore() {
        Map<String, Object> ok = new HashMap<>();
        ok.put("x1.response.status_code", 200);
        Map<String, Object> bad = new HashMap<>();
        bad.put("x1.response.status_code", 500);
        String validator = "${x1.response.status_code} == 200";

        assertEquals(Boolean.FALSE, Utils.evaluateTestValidator(validator, ok));
        assertFalse(Utils.validateTest(validator, ok));
        assertEquals(Boolean.TRUE, Utils.evaluateTestValidator(validator, bad));
        assertTrue(Utils.validateTest(validator, bad));
    }

    @Test
    public void validatorThatCannotBeEvaluatedIsNullNotNotVulnerable() {
        assertFalse(Utils.hasTestValidator(null));
        assertFalse(Utils.hasTestValidator("  "));
        assertEquals(null, Utils.evaluateTestValidator(null, new HashMap<String, Object>()));
        // not a boolean, a failing script, a missing key
        assertEquals(null, Utils.evaluateTestValidator("1 + 1", new HashMap<String, Object>()));
        assertEquals(null, Utils.evaluateTestValidator("this is not js", new HashMap<String, Object>()));
        assertEquals(null, Utils.evaluateTestValidator("${x9.response.status_code} == 200", new HashMap<String, Object>()));

        java.util.function.Supplier<javax.script.ScriptEngine> original = Utils.jsEngineSupplier;
        Utils.jsEngineSupplier = () -> null;
        try {
            assertEquals(null, Utils.evaluateTestValidator("true", new HashMap<String, Object>()));
            assertFalse(Utils.validateTest("true", new HashMap<String, Object>()));
        } finally {
            Utils.jsEngineSupplier = original;
        }
    }
}

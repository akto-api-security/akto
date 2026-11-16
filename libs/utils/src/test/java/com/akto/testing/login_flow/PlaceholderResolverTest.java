package com.akto.testing.login_flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class PlaceholderResolverTest {

    private static final PlaceholderResolver.Options STANDARD = PlaceholderResolver.Options.standard(true, true);
    private static final PlaceholderResolver.Options STANDARD_NO_THROW = PlaceholderResolver.Options.standard(true, false);

    private static PlaceholderResolver.Options login(String nodeId) {
        return PlaceholderResolver.Options.loginFlow(nodeId, "http://app.example.com/users/set_session");
    }

    private static String resolve(String template, Map<String, Object> values, PlaceholderResolver.Options o)
            throws PlaceholderException {
        return PlaceholderResolver.resolve(template, values, o);
    }

    private static String error(String template, Map<String, Object> values, PlaceholderResolver.Options o) {
        try {
            String out = resolve(template, values, o);
            fail("expected an error, got: " + out);
            return null;
        } catch (PlaceholderException e) {
            return e.getMessage();
        }
    }

    private static Map<String, Object> values() {
        Map<String, Object> v = new HashMap<>();
        v.put("x1.response.body.token", "abc");
        v.put("x1.response.status_code", 200);
        v.put("x3.response.body.forward", "/users/forward?domain=app&forward=http://t");
        v.put("x3.response.status_code", 200);
        v.put("x3.response.body.count", 7);
        v.put("x5.response.status_code", 302);
        v.put("x5.response.body.empty", "");
        return v;
    }

    // ---- unchanged behaviour of the original replaceVariables

    @Test
    public void standardKeysResolveAsBefore() throws Exception {
        assertEquals("{\"t\": \"abc\", \"n\": 7}",
                resolve("{\"t\": \"${x1.response.body.token}\", \"n\": ${x3.response.body.count}}", values(), STANDARD));
    }

    @Test
    public void standardMissingKeyThrowsWithOriginalWording() {
        String msg = error("${x9.response.body.a}", values(), STANDARD);
        assertTrue(msg.startsWith("Couldn't find x9.response.body.a"), msg);
    }

    @Test
    public void standardMissingKeyWithoutThrowLeavesLiteral() throws Exception {
        assertEquals("a ${x9.response.body.a} b", resolve("a ${x9.response.body.a} b", values(), STANDARD_NO_THROW));
    }

    @Test
    public void standardLeavesNonPlaceholdersAlone() throws Exception {
        String t = "${foo} ${cookies} ${x1.response.body.token|nope} ${step2.a b} #[1+1]# ${unclosed";
        assertEquals(t, resolve(t, values(), STANDARD));
    }

    @Test
    public void standardFindsPlaceholderNestedInNonPlaceholder() throws Exception {
        assertEquals("${foo abc}", resolve("${foo ${x1.response.body.token}}", values(), STANDARD));
    }

    @Test
    public void standardStepKeyIsNotAliased() throws Exception {
        Map<String, Object> v = values();
        v.put("step2.response.body.a", "literal-step-key");
        assertEquals("literal-step-key", resolve("${step2.response.body.a}", v, STANDARD));
    }

    @Test
    public void aktoChangesInfoKeysStillWork() throws Exception {
        Map<String, Object> v = new HashMap<>();
        v.put("AKTO.changes_info.newEndpointsCount", 2088);
        assertEquals("{\"c\": 2088}", resolve("{\"c\": ${AKTO.changes_info.newEndpointsCount}}", v,
                PlaceholderResolver.Options.standard(false, true)));
    }

    @Test
    public void escapingMatchesOriginalExceptSingleQuote() throws Exception {
        Map<String, Object> v = new HashMap<>();
        v.put("x1.response.body.a", "it's \"q\" \\ \n\t");
        String out = resolve("{\"a\": \"${x1.response.body.a}\"}", v, STANDARD);
        assertEquals("{\"a\": \"it's \\\"q\\\" \\\\ \\n\\t\"}", out);
        assertFalse(out.contains("\\'"), "single quote must not become \\' (invalid JSON)");
    }

    @Test
    public void presentButEmptyValueIsEmpty() throws Exception {
        assertEquals("[]", resolve("[${x5.response.body.empty}]", values(), STANDARD));
    }

    // ---- filters

    @Test
    public void regexFilterTakesGroupOne() throws Exception {
        Map<String, Object> v = new HashMap<>();
        v.put("x5.response.header.set-cookie.client_token", "2990637_17YrAbC");
        assertEquals("2990637", resolve("${x5.response.header.set-cookie.client_token|regex:^([^_]+)}", v, login("x7")));
    }

    @Test
    public void regexFilterWithoutGroupTakesWholeMatch() throws Exception {
        assertEquals("ab", resolve("${x1.response.body.token|regex:a.}", values(), STANDARD));
    }

    @Test
    public void regexFilterIsBraceDepthAware() throws Exception {
        Map<String, Object> v = new HashMap<>();
        v.put("x1.response.body.code", "otp 123456 sent");
        assertEquals("123456", resolve("${x1.response.body.code|regex:(\\d{6})}", v, STANDARD));
    }

    @Test
    public void escapedPipeIsLiteralInsidePattern() throws Exception {
        Map<String, Object> v = new HashMap<>();
        v.put("x1.response.body.a", "k=v|rest");
        assertEquals("v", resolve("${x1.response.body.a|regex:=([^\\|]+)}", v, STANDARD));
    }

    @Test
    public void filtersChainLeftToRight() throws Exception {
        Map<String, Object> v = new HashMap<>();
        v.put("x1.response.body.a", "id=a+b/c=&x");
        assertEquals("a%2Bb%2Fc%3D", resolve("${x1.response.body.a|regex:id=([^&]+)&|urlencode}", v, STANDARD));
    }

    @Test
    public void urlencodeUsesPercent20() throws Exception {
        Map<String, Object> v = new HashMap<>();
        v.put("x1.response.body.a", "p^ss*W$O%rd+1 x");
        assertEquals("p%5Ess*W%24O%25rd%2B1%20x", resolve("${x1.response.body.a|urlencode}", v, STANDARD));
    }

    @Test
    public void rawSkipsEscaping() throws Exception {
        Map<String, Object> v = new HashMap<>();
        v.put("x1.response.body.a", "a\"b");
        assertEquals("a\"b", resolve("${x1.response.body.a|raw}", v, STANDARD));
        assertEquals("a\\\"b", resolve("${x1.response.body.a}", v, STANDARD));
    }

    @Test
    public void regexNoMatchNamesKeyAndPatternNotValue() {
        Map<String, Object> v = new HashMap<>();
        v.put("x5.response.body.secret", "s3cr3t-value");
        String msg = error("${x5.response.body.secret|regex:^(\\d+)$}", v, login("x7"));
        assertTrue(msg.contains("did not match"), msg);
        assertTrue(msg.contains("x5.response.body.secret"), msg);
        assertTrue(msg.contains("^(\\d+)$"), msg);
        assertFalse(msg.contains("s3cr3t-value"), msg);
    }

    @Test
    public void invalidRegexIsAnError() {
        String msg = error("${x1.response.body.token|regex:([unclosed}", values(), STANDARD);
        assertTrue(msg.contains("invalid regex"), msg);
    }

    @Test
    public void regexInputIsCapped() {
        Map<String, Object> v = new HashMap<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= PlaceholderResolver.MAX_REGEX_INPUT_CHARS; i++) sb.append('a');
        v.put("x1.response.body", sb.toString());
        String msg = error("${x1.response.body|regex:(a)}", v, STANDARD);
        assertTrue(msg.contains("larger than 1MB"), msg);
    }

    @Test
    public void unknownFilterIsAnErrorInLoginFlow() {
        String msg = error("${x1.response.body.token|base64}", values(), login("x3"));
        assertTrue(msg.contains("unknown filter 'base64'"), msg);
    }

    // ---- login-flow mode

    @Test
    public void stepAliasMapsToOddNodeIds() throws Exception {
        assertEquals("abc", resolve("${step1.response.body.token}", values(), login("x5")));
        assertEquals("7", resolve("${step2.response.body.count}", values(), login("x5")));
    }

    @Test
    public void stepZeroIsAnError() {
        String msg = error("${step0.response.body.token}", values(), login("x5"));
        assertTrue(msg.contains("step numbers start at 1"), msg);
    }

    @Test
    public void missingKeyHintsWhenNodeNeverRan() {
        String msg = error("Bearer ${x15.response.body.access_token}", values(), login(null));
        assertTrue(msg.startsWith("Couldn't find x15.response.body.access_token"), msg);
        assertTrue(msg.contains("x15 (step 8)"), msg);
        assertTrue(msg.contains("x(2k-1)"), msg);
    }

    @Test
    public void missingRegexHintsAtStatus() {
        String msg = error("${x5.response.regex}", values(), login("x7"));
        assertTrue(msg.contains("the regex of x5 (step 3) did not match its response, status 302"), msg);
    }

    @Test
    public void jsExpressionsAreRejectedOnlyWhenTheCallerOptsIn() throws Exception {
        String t = "{\"a\": \"#[ 'x'.toUpperCase() ]#\"}";
        // the resolver itself never evaluates or rejects JS; the caller decides
        assertEquals(t, resolve(t, values(), login("x3")));
        assertEquals(t, resolve(t, values(), STANDARD));
        String msg = error(t, values(), login("x3").withJsExpressionsRejected());
        assertEquals(PlaceholderResolver.JS_EXPRESSIONS_NOT_EVALUATED, msg);
    }

    @Test
    public void forJsExpressionAlsoEscapesSingleQuotes() throws Exception {
        Map<String, Object> v = values();
        v.put("x1.response.body.q", "it's \"x\"");
        assertEquals("'it\\'s \\\"x\\\"'", resolve("'${x1.response.body.q}'", v, login("x3").forJsExpression()));
        // outside JS the ' stays as is (valid JSON)
        assertEquals("it's \\\"x\\\"", resolve("${x1.response.body.q}", v, login("x3")));
        // |raw still opts out of escaping
        assertEquals("it's \"x\"", resolve("${x1.response.body.q|raw}", v, login("x3").forJsExpression()));
    }

    @Test
    public void unknownPlaceholderIsAnErrorInLoginFlow() {
        String msg = error("${X1.response.body.token}", values(), login("x3"));
        assertTrue(msg.contains("unsupported placeholder"), msg);
        msg = error("${x1.response.body.token", values(), login("x3"));
        assertTrue(msg.contains("unsupported placeholder"), msg);
    }

    @Test
    public void cookiesWithoutRequestUrlIsAnError() {
        String msg = error("${cookies}", values(), PlaceholderResolver.Options.loginFlow("x3", null));
        assertTrue(msg.contains("needs a request URL"), msg);
    }

    @Test
    public void cookiesResolveFromPreviousNodesOnly() throws Exception {
        Map<String, Object> v = values();
        v.put("x1.request.url", "http://app.example.com/login");
        v.put("x1.response.set-cookie-raw", "login_token=LT1; Path=/; Secure; HttpOnly");
        v.put("x3.request.url", "http://app.example.com/users/login_submit");
        v.put("x3.response.set-cookie-raw", "_s=S1; Path=/");
        // building x3: only x1's cookies; login_token is Secure and app.example.com over http is not a secure context
        assertEquals("", resolve("${cookies}", v, PlaceholderResolver.Options.loginFlow("x3", "http://app.example.com/a")));
        assertEquals("login_token=LT1",
                resolve("${cookies}", v, PlaceholderResolver.Options.loginFlow("x3", "https://app.example.com/a")));
        assertEquals("login_token=LT1; _s=S1",
                resolve("${cookies}", v, PlaceholderResolver.Options.loginFlow("x5", "https://app.example.com/a")));
        assertEquals("S1", resolve("${cookies._s}", v, login("x5")));
        String msg = error("${cookies._s}", v, login("x3"));
        assertTrue(msg.contains("no previous step set it"), msg);
    }

    @Test
    public void cookiePlaceholdersAreLiteralOutsideLoginFlow() throws Exception {
        assertEquals("${cookies} ${cookies.a}", resolve("${cookies} ${cookies.a}", values(), STANDARD));
    }

    @Test
    public void errorsNeverContainCookieValues() {
        Map<String, Object> v = new HashMap<>();
        v.put("x1.request.url", "http://app.example.com/login");
        v.put("x1.response.set-cookie-raw", "client_token=TOPSECRET; Path=/");
        String msg = error("${cookies.client_token|regex:^(\\d+)_}", v, login("x3"));
        assertFalse(msg.contains("TOPSECRET"), msg);
        assertTrue(msg.contains("cookies.client_token"), msg);
    }
}

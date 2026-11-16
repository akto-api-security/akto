package com.akto.testing.login_flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class LoginFlowCookieJarTest {

    private static final String APP = "https://app.example.com/users/forward";
    private static final String TENANT = "https://tenant.example.com/dashboards/";

    @Test
    public void hostOnlyCookieIsSentOnlyToItsHost() throws Exception {
        LoginFlowCookieJar jar = new LoginFlowCookieJar();
        jar.receive(APP, "a=1; Path=/", "x1");
        assertEquals("a=1", jar.cookieHeaderFor("https://app.example.com/x"));
        assertEquals("", jar.cookieHeaderFor("https://sub.app.example.com/x"));
        assertEquals("", jar.cookieHeaderFor(TENANT));
    }

    @Test
    public void domainCookieForAppIsNotSentToTenant() throws Exception {
        LoginFlowCookieJar jar = new LoginFlowCookieJar();
        jar.receive(APP, "client_token=2990637_17Yr; Domain=app.example.com; Path=/; Secure; HttpOnly", "x5");
        assertEquals("client_token=2990637_17Yr", jar.cookieHeaderFor("https://app.example.com/users/set_session"));
        assertEquals("client_token=2990637_17Yr", jar.cookieHeaderFor("https://x.app.example.com/"));
        assertEquals("", jar.cookieHeaderFor(TENANT));
        // ${cookies.name} is a value lookup across hosts
        assertEquals("2990637_17Yr", jar.latestValue("client_token"));
    }

    @Test
    public void parentDomainCookieIsSharedAcrossSubdomains() throws Exception {
        LoginFlowCookieJar jar = new LoginFlowCookieJar();
        jar.receive(APP, "shared=1; Domain=.example.com; Path=/", "x1");
        assertEquals("shared=1", jar.cookieHeaderFor(TENANT));
        assertEquals("", jar.cookieHeaderFor("https://badexample.com/"));
    }

    @Test
    public void foreignDomainAttributeIsIgnoredWithReason() throws Exception {
        LoginFlowCookieJar jar = new LoginFlowCookieJar();
        jar.receive(APP, "evil=1; Domain=tenant.example.com", "x3");
        assertEquals("", jar.cookieHeaderFor(TENANT));
        assertNull(jar.latestValue("evil"));
        assertTrue(jar.missingReason("evil").contains("does not match host app.example.com"), jar.missingReason("evil"));
    }

    @Test
    public void pathMatchingAndDefaultPath() throws Exception {
        LoginFlowCookieJar jar = new LoginFlowCookieJar();
        jar.receive("https://app.example.com/users/login_submit", "d=1", "x1");           // default path /users
        jar.receive("https://app.example.com/", "api=1; Path=/api", "x1");
        assertEquals("d=1", jar.cookieHeaderFor("https://app.example.com/users/forward"));
        assertEquals("d=1", jar.cookieHeaderFor("https://app.example.com/users"));
        assertEquals("", jar.cookieHeaderFor("https://app.example.com/usersX"));
        assertEquals("api=1", jar.cookieHeaderFor("https://app.example.com/api/v1"));
        assertEquals("", jar.cookieHeaderFor("https://app.example.com/apix"));
    }

    @Test
    public void longerPathsComeFirst() throws Exception {
        LoginFlowCookieJar jar = new LoginFlowCookieJar();
        jar.receive("https://app.example.com/", "a=root; Path=/", "x1");
        jar.receive("https://app.example.com/", "b=deep; Path=/users", "x3");
        assertEquals("b=deep; a=root", jar.cookieHeaderFor("https://app.example.com/users/forward"));
    }

    @Test
    public void secureCookiesNeedHttpsOrLoopback() throws Exception {
        LoginFlowCookieJar jar = new LoginFlowCookieJar();
        jar.receive("http://app.example.com/login", "s=1; Secure", "x1");
        assertEquals("", jar.cookieHeaderFor("http://app.example.com/login"));
        assertEquals("s=1", jar.cookieHeaderFor("https://app.example.com/login"));

        LoginFlowCookieJar local = new LoginFlowCookieJar();
        local.receive("http://127.0.0.1:9301/login", "login_token=LT; Path=/; Secure; HttpOnly", "x1");
        local.receive("http://localhost:9302/x", "site=1; Path=/; Secure", "x9");
        assertEquals("login_token=LT", local.cookieHeaderFor("http://127.0.0.1:9301/users/login_submit"));
        assertEquals("site=1", local.cookieHeaderFor("http://localhost:9303/api"));   // ports are not scoped
    }

    @Test
    public void rotationReplacesSameIdentity() throws Exception {
        LoginFlowCookieJar jar = new LoginFlowCookieJar();
        jar.receive(APP, "_session=old; Path=/", "x1");
        jar.receive(APP, "_session=new; Path=/", "x3");
        assertEquals("_session=new", jar.cookieHeaderFor(APP));
        assertEquals("new", jar.latestValue("_session"));
        assertEquals(1, jar.size());
    }

    @Test
    public void maxAgeZeroAndPastExpiresDelete() throws Exception {
        LoginFlowCookieJar jar = new LoginFlowCookieJar();
        jar.receive(APP, "a=1; Path=/", "x1");
        jar.receive(APP, "b=2; Path=/", "x1");
        jar.receive(APP, "a=; Path=/; Max-Age=0", "x3");
        jar.receive(APP, "b=gone; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT", "x3");
        assertEquals("", jar.cookieHeaderFor(APP));
        assertNull(jar.latestValue("a"));
        assertTrue(jar.missingReason("a").contains("deleted by x3"), jar.missingReason("a"));
    }

    @Test
    public void maxAgeWinsOverExpires() throws Exception {
        LoginFlowCookieJar jar = new LoginFlowCookieJar();
        jar.receive(APP, "a=1; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Max-Age=3600", "x1");
        jar.receive(APP, "b=2; Expires=Fri, 01-Jan-2100 00:00:00 GMT", "x1");
        assertEquals("a=1; b=2", jar.cookieHeaderFor(APP));
    }

    @Test
    public void deletionOnOtherDomainDoesNotTouchCookie() throws Exception {
        LoginFlowCookieJar jar = new LoginFlowCookieJar();
        jar.receive(APP, "a=1; Path=/", "x1");
        jar.receive(TENANT, "a=; Path=/; Max-Age=0", "x3");
        assertEquals("a=1", jar.cookieHeaderFor(APP));
    }

    @Test
    public void jarIsCapped() throws Exception {
        LoginFlowCookieJar jar = new LoginFlowCookieJar();
        for (int i = 0; i < LoginFlowCookieJar.MAX_COOKIES + 5; i++) {
            jar.receive(APP, "c" + i + "=" + i, "x1");
        }
        assertEquals(LoginFlowCookieJar.MAX_COOKIES, jar.size());
        assertNull(jar.latestValue("c0"));
        assertTrue(jar.missingReason("c0").contains("evicted"));
        assertEquals("304", jar.latestValue("c304"));
    }

    @Test
    public void oversizedCookieIsIgnoredWithReason() throws Exception {
        LoginFlowCookieJar jar = new LoginFlowCookieJar();
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < LoginFlowCookieJar.MAX_COOKIE_BYTES; i++) big.append('v');
        jar.receive(APP, "big=" + big, "x1");
        assertNull(jar.latestValue("big"));
        assertTrue(jar.missingReason("big").contains("larger than 4096 bytes"));
        assertFalse(jar.missingReason("big").contains("vvvv"));
    }

    @Test
    public void unusableUrlIsIgnoredWithReason() throws Exception {
        LoginFlowCookieJar jar = new LoginFlowCookieJar();
        jar.receive(null, "a=1", "x1");
        assertNull(jar.latestValue("a"));
        assertTrue(jar.missingReason("a").contains("no usable request URL"));
    }

    @Test
    public void cookieHeaderForBadUrlIsAnError() {
        assertThrows(PlaceholderException.class, () -> new LoginFlowCookieJar().cookieHeaderFor("not a url"));
    }

    @Test
    public void commaInValueIsNotSplit() throws Exception {
        LoginFlowCookieJar jar = new LoginFlowCookieJar();
        jar.receive(APP, "list=a,b,c; Path=/", "x1");
        assertEquals("a,b,c", jar.latestValue("list"));
    }

    @Test
    public void fromValuesMapUsesNodeOrderAndExcludesCurrentAndLater() throws Exception {
        Map<String, Object> v = new HashMap<>();
        v.put("x1.request.url", APP);
        v.put("x1.response.set-cookie-raw", "s=one; Path=/\nkeep=1; Path=/");
        v.put("x11.request.url", APP);
        v.put("x11.response.set-cookie-raw", "s=eleven; Path=/");
        v.put("x3.request.url", APP);
        v.put("x3.response.set-cookie-raw", "s=three; Path=/");
        assertEquals("three", LoginFlowCookieJar.fromValuesMap(v, 5).latestValue("s"));
        assertEquals("eleven", LoginFlowCookieJar.fromValuesMap(v, 13).latestValue("s"));
        assertEquals("one", LoginFlowCookieJar.fromValuesMap(v, 3).latestValue("s"));
        assertEquals("keep=1; s=eleven", LoginFlowCookieJar.fromValuesMap(v, 100).cookieHeaderFor(APP));
    }

    @Test
    public void parsers() {
        LoginFlowCookieJar.ParsedSetCookie p = LoginFlowCookieJar.parseSetCookie(
                "login_token=abc==; Path=/; Secure; HttpOnly; Priority=High");
        assertEquals("login_token", p.getName());
        assertEquals("abc==", p.getValue());
        assertEquals("", p.getAttributes().get("secure"));
        assertEquals("High", p.getAttributes().get("priority"));
        assertNull(LoginFlowCookieJar.parseSetCookie("garbage"));

        List<String[]> pairs = LoginFlowCookieJar.parseCookieHeader("a=1; b=x=y;  ;c");
        assertEquals(2, pairs.size());
        assertEquals(Arrays.asList("b", "x=y"), Arrays.asList(pairs.get(1)));
    }

    @Test
    public void joinRawDropsHugeLinesAndCapsCount() {
        StringBuilder huge = new StringBuilder("h=");
        for (int i = 0; i < LoginFlowCookieJar.MAX_RAW_LINE_CHARS; i++) huge.append('x');
        assertEquals("a=1\nb=2", LoginFlowCookieJar.joinRaw(Arrays.asList("a=1", huge.toString(), null, "b=2")));
    }
}

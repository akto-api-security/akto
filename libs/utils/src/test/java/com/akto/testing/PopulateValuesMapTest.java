package com.akto.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.testing.WorkflowUpdatedSampleData;
import com.mongodb.BasicDBObject;

public class PopulateValuesMapTest {

    private static Map<String, List<String>> headers(String name, String... values) {
        Map<String, List<String>> h = new HashMap<>();
        h.put(name, Arrays.asList(values));
        return h;
    }

    @Test
    public void singleSetCookieLowercaseHeaderIsExtracted() {
        Map<String, Object> v = new HashMap<>();
        Utils.populateValuesMap(v, "<html/>", "x1",
                headers("set-cookie", "login_token=LT1; Path=/; Secure; HttpOnly"), false, null);
        assertEquals("LT1", v.get("x1.response.header.set-cookie.login_token"));
        assertEquals("login_token=LT1; Path=/; Secure; HttpOnly", v.get("x1.response.header.set-cookie"));
        assertEquals("login_token=LT1; Path=/; Secure; HttpOnly", v.get("x1.response.set-cookie-raw"));
        assertNull(v.get("x1.response.header.set-cookie.Path"), "attributes are not cookies");
    }

    @Test
    public void singleSetCookieMixedCaseGetsLowercaseAlias() {
        Map<String, Object> v = new HashMap<>();
        Utils.populateValuesMap(v, "{}", "x5",
                headers("Set-Cookie", "client_token=2990637_17Yr; Domain=app.predev55.in; Path=/; Secure; HttpOnly"),
                false, null);
        assertEquals("2990637_17Yr", v.get("x5.response.header.Set-Cookie.client_token"));
        assertEquals("2990637_17Yr", v.get("x5.response.header.set-cookie.client_token"));
        assertEquals(v.get("x5.response.header.Set-Cookie"), v.get("x5.response.header.set-cookie"));
    }

    @Test
    public void manySetCookiesKeepJoinedValueAndEachCookie() {
        Map<String, Object> v = new HashMap<>();
        Utils.populateValuesMap(v, "{}", "x9",
                headers("Set-Cookie", "_cb_site_session=S; Path=/; HttpOnly", "site_valid=V; Path=/; HttpOnly"),
                false, null);
        assertEquals("_cb_site_session=S; Path=/; HttpOnly;site_valid=V; Path=/; HttpOnly",
                v.get("x9.response.header.Set-Cookie"));
        assertEquals("S", v.get("x9.response.header.set-cookie._cb_site_session"));
        assertEquals("V", v.get("x9.response.header.Set-Cookie.site_valid"));
        assertEquals("_cb_site_session=S; Path=/; HttpOnly\nsite_valid=V; Path=/; HttpOnly",
                v.get("x9.response.set-cookie-raw"));
    }

    @Test
    public void requestCookieHeaderIsSplitAndNotStoredAsRaw() {
        Map<String, Object> v = new HashMap<>();
        Utils.populateValuesMap(v, "{}", "x3", headers("cookie", "a=1; b=2"), true, null);
        assertEquals("1", v.get("x3.request.header.cookie.a"));
        assertEquals("2", v.get("x3.request.header.cookie.b"));
        assertNull(v.get("x3.request.set-cookie-raw"));
        assertNull(v.get("x3.response.set-cookie-raw"));
    }

    @Test
    public void otherHeadersGetLowercaseAlias() {
        Map<String, Object> v = new HashMap<>();
        Utils.populateValuesMap(v, "{}", "x1", headers("X-Csrf-Token", "T"), false, null);
        assertEquals("T", v.get("x1.response.header.X-Csrf-Token"));
        assertEquals("T", v.get("x1.response.header.x-csrf-token"));
    }

    @Test
    public void emptyHeaderListIsSkipped() {
        Map<String, Object> v = new HashMap<>();
        Map<String, List<String>> h = new HashMap<>();
        h.put("Set-Cookie", Collections.<String>emptyList());
        Utils.populateValuesMap(v, "{}", "x1", h, false, null);
        assertFalse(v.containsKey("x1.response.header.Set-Cookie"));
        assertFalse(v.containsKey("x1.response.set-cookie-raw"));
    }

    @Test
    public void singleQuoteInValueStaysValidJson() throws Exception {
        Map<String, Object> v = new HashMap<>();
        Utils.populateValuesMap(v, "{\"name\": \"O'Brien\"}", "x1", new HashMap<String, List<String>>(), false, null);
        String body = Utils.replaceVariables("{\"n\": \"${x1.response.body.name}\"}", v, true, true);
        assertEquals("O'Brien", BasicDBObject.parse(body).getString("n"));
    }

    private static final String ORIG = "{\"method\":\"POST\",\"requestPayload\":\"\",\"path\":\"http://127.0.0.1/x\","
            + "\"requestHeaders\":\"{}\",\"type\":\"\"}";

    @Test
    public void buildHttpRequestLoginFlowUsesJarScopedToResolvedUrl() throws Exception {
        Map<String, Object> v = new HashMap<>();
        Utils.populateValuesMap(v, "{\"forward\": \"/users/forward?domain=app\"}", "x3",
                headers("Set-Cookie", "_s=S1; Path=/; Secure"), false, null);
        v.put("x3.request.url", "http://127.0.0.1:9301/users/login_submit");
        WorkflowUpdatedSampleData data = new WorkflowUpdatedSampleData(ORIG, "",
                "{\"Cookie\": \"${cookies}\", \"X-S\": \"${cookies._s|raw}\"}", "",
                "http://127.0.0.1:9301${step2.response.body.forward}");
        OriginalHttpRequest req = Utils.buildHttpRequest(data, v, "x5");
        assertEquals("http://127.0.0.1:9301/users/forward", req.getUrl());
        assertEquals("domain=app", req.getQueryParams());
        assertEquals(Collections.singletonList("_s=S1"), req.getHeaders().get("cookie"));
        assertEquals(Collections.singletonList("S1"), req.getHeaders().get("x-s"));
    }

    @Test
    public void buildHttpRequestWithoutLoginFlowKeepsOldBehaviour() throws Exception {
        Map<String, Object> v = new HashMap<>();
        v.put("x1.response.body.id", 10);
        WorkflowUpdatedSampleData data = new WorkflowUpdatedSampleData(ORIG, "",
                "{\"Cookie\": \"${cookies}\"}", "", "http://h/stud_${x1.response.body.id}");
        OriginalHttpRequest req = Utils.buildHttpRequest(data, v);
        assertEquals("http://h/stud_10", req.getUrl());
        assertEquals(Collections.singletonList("${cookies}"), req.getHeaders().get("cookie"));
    }

    @Test
    public void buildHttpRequestRejectsCookiesInUrl() {
        WorkflowUpdatedSampleData data = new WorkflowUpdatedSampleData(ORIG, "", "{}", "", "http://h/${cookies}");
        try {
            Utils.buildHttpRequest(data, new HashMap<String, Object>(), "x3");
            fail("expected an error");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("can't be used in a URL"), e.getMessage());
        }
    }
}

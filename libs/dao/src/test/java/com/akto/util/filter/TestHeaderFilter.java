package com.akto.util.filter;

import org.junit.Test;
import static org.junit.Assert.*;

public class TestHeaderFilter {

    @Test
    public void testExactMatchIgnoredHeaders() {
        assertTrue(HeaderFilter.shouldIgnoreHeader("user-agent"));
        assertTrue(HeaderFilter.shouldIgnoreHeader("x-forwarded-proto"));
        assertTrue(HeaderFilter.shouldIgnoreHeader("accept-encoding"));
        assertTrue(HeaderFilter.shouldIgnoreHeader("date"));
        assertTrue(HeaderFilter.shouldIgnoreHeader("accept"));
        assertTrue(HeaderFilter.shouldIgnoreHeader("cache-control"));
        assertTrue(HeaderFilter.shouldIgnoreHeader("connection"));
        assertTrue(HeaderFilter.shouldIgnoreHeader("pragma"));
        assertTrue(HeaderFilter.shouldIgnoreHeader("akamai-origin-hop"));
        assertTrue(HeaderFilter.shouldIgnoreHeader("x-akamai-config-log-detail"));
        assertTrue(HeaderFilter.shouldIgnoreHeader("cdn-loop"));
        assertTrue(HeaderFilter.shouldIgnoreHeader("accept-language"));
        assertTrue(HeaderFilter.shouldIgnoreHeader("etag"));
        assertTrue(HeaderFilter.shouldIgnoreHeader("x-src-id"));
    }

    @Test
    public void testCaseInsensitivity() {
        assertTrue(HeaderFilter.shouldIgnoreHeader("User-Agent"));
        assertTrue(HeaderFilter.shouldIgnoreHeader("USER-AGENT"));
        assertTrue(HeaderFilter.shouldIgnoreHeader("Cache-Control"));
        assertTrue(HeaderFilter.shouldIgnoreHeader("X-Forwarded-Proto"));
        assertTrue(HeaderFilter.shouldIgnoreHeader("X-ENVOY-UPSTREAM"));
        assertTrue(HeaderFilter.shouldIgnoreHeader("X-Akto-K8s-Service"));
    }

    @Test
    public void testPrefixMatchIgnoredHeaders() {
        assertTrue(HeaderFilter.shouldIgnoreHeader("x-akto-k8s"));
        assertTrue(HeaderFilter.shouldIgnoreHeader("x-akto-k8s-service"));
        assertTrue(HeaderFilter.shouldIgnoreHeader("x-akto-k8s-namespace"));
        assertTrue(HeaderFilter.shouldIgnoreHeader("x-envoy"));
        assertTrue(HeaderFilter.shouldIgnoreHeader("x-envoy-upstream-service-time"));
        assertTrue(HeaderFilter.shouldIgnoreHeader("x-envoy-decorator-operation"));
    }

    @Test
    public void testNonIgnoredHeaders() {
        assertFalse(HeaderFilter.shouldIgnoreHeader("authorization"));
        assertFalse(HeaderFilter.shouldIgnoreHeader("content-type"));
        assertFalse(HeaderFilter.shouldIgnoreHeader("x-custom-header"));
        assertFalse(HeaderFilter.shouldIgnoreHeader("x-api-key"));
        assertFalse(HeaderFilter.shouldIgnoreHeader("host"));
        assertFalse(HeaderFilter.shouldIgnoreHeader("cookie"));
    }

    @Test
    public void testNullAndEmpty() {
        assertFalse(HeaderFilter.shouldIgnoreHeader(null));
        assertFalse(HeaderFilter.shouldIgnoreHeader(""));
    }
}

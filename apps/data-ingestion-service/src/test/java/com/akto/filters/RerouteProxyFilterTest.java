package com.akto.filters;

import com.sun.net.httpserver.HttpServer;
import okhttp3.OkHttpClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.FilterChain;
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RerouteProxyFilterTest {

    private static final String REROUTED_TOKEN = "eyJ.eyJ.abc-" + RerouteProxyFilter.REROUTE_TOKEN_SUFFIX;

    private HttpServer upstream;
    private String upstreamUrl;
    private final List<Captured> received = new ArrayList<>();

    /** What the upstream (acorns' data-ingestion) received. */
    private static final class Captured {
        String method;
        String uri;
        Map<String, String> headers = new LinkedHashMap<>();
        byte[] body;
    }

    @Before
    public void startUpstream() throws IOException {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/", exchange -> {
            Captured c = new Captured();
            c.method = exchange.getRequestMethod();
            c.uri = exchange.getRequestURI().toString();
            exchange.getRequestHeaders().forEach((k, v) -> c.headers.put(k.toLowerCase(), String.join(",", v)));
            c.body = readAll(exchange.getRequestBody());
            synchronized (received) {
                received.add(c);
            }
            byte[] reply = "{\"success\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add("X-Upstream", "acorns");
            exchange.sendResponseHeaders(207, reply.length);
            exchange.getResponseBody().write(reply);
            exchange.close();
        });
        upstream.start();
        upstreamUrl = "http://127.0.0.1:" + upstream.getAddress().getPort() + "/";
    }

    @After
    public void stopUpstream() {
        upstream.stop(0);
    }

    private static OkHttpClient client() {
        return new OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build();
    }

    private RerouteProxyFilter filter(Integer deploymentAccountId) {
        return new RerouteProxyFilter(upstreamUrl, deploymentAccountId, client());
    }

    // ---- forwarding ----

    @Test
    public void matchingToken_isForwardedAsIsAndRelayed() throws Exception {
        byte[] body = "{\"batchData\":[{\"akto_account_id\":\"1000000\",\"path\":\"/x\"}]}".getBytes(StandardCharsets.UTF_8);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", REROUTED_TOKEN);
        headers.put("Content-Type", "application/json");
        headers.put("X-Device-Id", "device-42");
        headers.put("Host", "1718012345-guardrails.akto.io");
        headers.put("Connection", "keep-alive");
        Res res = new Res();
        boolean[] chainRan = {false};

        filter(1718012345).doFilter(
                req("POST", "/api/ingestData", "a=1&b=2", headers, body), res.proxy(), (q, r) -> chainRan[0] = true);

        assertFalse("must not be handled locally", chainRan[0]);
        assertEquals(1, received.size());
        Captured c = received.get(0);
        assertEquals("POST", c.method);
        assertEquals("/api/ingestData?a=1&b=2", c.uri);
        assertArrayEquals("body must pass through byte for byte", body, c.body);
        assertEquals("application/json", c.headers.get("content-type"));
        assertEquals("caller's token forwarded unchanged", REROUTED_TOKEN, c.headers.get("authorization"));
        assertEquals("1", c.headers.get("x-akto-rerouted"));
        assertEquals("device-42", c.headers.get("x-device-id"));
        assertEquals("127.0.0.1:" + upstream.getAddress().getPort(), c.headers.get("host"));

        assertEquals(Integer.valueOf(207), res.status);
        assertEquals("acorns", res.headers.get("X-Upstream"));
        assertEquals("{\"success\":true}", new String(res.body.toByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    public void multipartBody_passesThroughWithItsBoundary() throws Exception {
        String contentType = "multipart/form-data; boundary=----b0undary";
        byte[] body = ("------b0undary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.bin\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n\u0000\u0001ÿ\r\n------b0undary--\r\n")
                .getBytes(StandardCharsets.ISO_8859_1);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", REROUTED_TOKEN);
        headers.put("Content-Type", contentType);

        filter(null).doFilter(req("POST", "/api/validate/file", null, headers, body), new Res().proxy(), failChain());

        Captured c = received.get(0);
        assertArrayEquals(body, c.body);
        assertEquals(contentType, c.headers.get("content-type"));
    }

    @Test
    public void getRequest_isForwardedWithoutBody() throws Exception {
        Res res = new Res();
        filter(null).doFilter(req("GET", "/api/health", null, auth(REROUTED_TOKEN), null), res.proxy(), failChain());

        assertEquals("GET", received.get(0).method);
        assertEquals(0, received.get(0).body.length);
        assertEquals(Integer.valueOf(207), res.status);
    }

    // ---- not forwarded ----

    @Test
    public void otherTokens_areHandledLocally() throws Exception {
        for (String token : new String[]{null, "", "eyJ.eyJ.someOtherSignature", RerouteProxyFilter.REROUTE_TOKEN_SUFFIX + "x"}) {
            assertChainRuns(filter(null), auth(token));
        }
        assertTrue(received.isEmpty());
    }

    @Test
    public void alreadyReroutedRequest_isNotForwardedAgain() throws Exception {
        Map<String, String> headers = auth(REROUTED_TOKEN);
        headers.put(RerouteProxyFilter.REROUTED_HEADER, "1");
        assertChainRuns(filter(null), headers);
        assertTrue(received.isEmpty());
    }

    @Test
    public void targetsOwnDeployment_neverForwards() throws Exception {
        assertChainRuns(filter(RerouteProxyFilter.REROUTE_ACCOUNT_ID), auth(REROUTED_TOKEN));
        assertTrue(received.isEmpty());
    }

    @Test
    public void upstreamDown_returns502WithoutHandlingLocally() throws Exception {
        upstream.stop(0);
        Res res = new Res();
        filter(null).doFilter(req("POST", "/api/ingestData", null, auth(REROUTED_TOKEN), new byte[]{1}),
                res.proxy(), failChain());

        assertEquals(Integer.valueOf(502), res.status);
    }

    // ---- helpers ----

    private static void assertChainRuns(RerouteProxyFilter filter, Map<String, String> headers) throws Exception {
        boolean[] chainRan = {false};
        filter.doFilter(req("POST", "/api/ingestData", null, headers, new byte[0]), new Res().proxy(), (q, r) -> chainRan[0] = true);
        assertTrue("expected local handling for " + headers, chainRan[0]);
    }

    private static FilterChain failChain() {
        return (q, r) -> {
            throw new AssertionError("must not be handled locally");
        };
    }

    private static Map<String, String> auth(String token) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (token != null) {
            headers.put("Authorization", token);
        }
        return headers;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /** A request stand-in (no mocking library in this module); header lookups are case-insensitive. */
    private static HttpServletRequest req(String method, String uri, String query, Map<String, String> headers, byte[] body) {
        Map<String, String> lower = new LinkedHashMap<>();
        headers.forEach((k, v) -> lower.put(k.toLowerCase(), v));
        ByteArrayInputStream in = new ByteArrayInputStream(body != null ? body : new byte[0]);
        ServletInputStream servletIn = new ServletInputStream() {
            public int read() { return in.read(); }
            public int read(byte[] b, int off, int len) { return in.read(b, off, len); }
            public boolean isFinished() { return in.available() == 0; }
            public boolean isReady() { return true; }
            public void setReadListener(ReadListener l) { }
        };
        return (HttpServletRequest) Proxy.newProxyInstance(RerouteProxyFilterTest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, (p, m, a) -> {
                    switch (m.getName()) {
                        case "getMethod": return method;
                        case "getRequestURI": return uri;
                        case "getQueryString": return query;
                        case "getContentType": return lower.get("content-type");
                        case "getHeader": return lower.get(((String) a[0]).toLowerCase());
                        case "getHeaderNames": return Collections.enumeration(headers.keySet());
                        case "getHeaders": {
                            String v = lower.get(((String) a[0]).toLowerCase());
                            return Collections.enumeration(v == null ? Collections.<String>emptyList() : Collections.singletonList(v));
                        }
                        case "getInputStream": return servletIn;
                        default: return null;
                    }
                });
    }

    /** A response stand-in recording status, headers and body. */
    private static final class Res {
        Integer status;
        final Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER); // header names are case-insensitive
        final ByteArrayOutputStream body = new ByteArrayOutputStream();

        HttpServletResponse proxy() {
            ServletOutputStream out = new ServletOutputStream() {
                public void write(int b) { body.write(b); }
                public boolean isReady() { return true; }
                public void setWriteListener(WriteListener l) { }
            };
            return (HttpServletResponse) Proxy.newProxyInstance(RerouteProxyFilterTest.class.getClassLoader(),
                    new Class<?>[]{HttpServletResponse.class}, (p, m, a) -> {
                        switch (m.getName()) {
                            case "setStatus": status = (Integer) a[0]; return null;
                            case "addHeader": case "setHeader": headers.put((String) a[0], (String) a[1]); return null;
                            case "setContentType": headers.put("Content-Type", (String) a[0]); return null;
                            case "getOutputStream": return out;
                            case "isCommitted": return false;
                            default: return null;
                        }
                    });
        }
    }
}

package com.akto.filter;

import com.akto.listener.InfraMetricsListener;
import com.akto.metrics.CyborgMetricsConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.FilterChain;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

/**
 * Verifies InfraMetricsFilter records the response body size as encoded BYTES (not chars), for both
 * the getWriter() and getOutputStream() paths. Servlet types are faked with java.lang.reflect.Proxy,
 * same as MetricsAuthFilterTest.
 */
public class InfraMetricsFilterTest {

    // Multibyte in UTF-8: é = 2 bytes, € = 3 bytes, 世/界 = 3 bytes each -> chars != bytes.
    private static final String BODY = "{\"name\":\"héllo € 世界\"}";

    @Before
    public void enable() {
        CyborgMetricsConfig.setEnabledForTest(true);
    }

    @After
    public void reset() {
        CyborgMetricsConfig.setEnabledForTest(false);
    }

    private static HttpServletRequest request(String uri) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[] { HttpServletRequest.class },
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getRequestURI": return uri;
                        case "getMethod": return "POST";
                        default: return null;
                    }
                });
    }

    /** Response whose output stream writes into {@code sink}, with UTF-8 encoding. */
    private static HttpServletResponse response(ByteArrayOutputStream sink) {
        ServletOutputStream out = new ServletOutputStream() {
            @Override public void write(int b) { sink.write(b); }
        };
        return (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[] { HttpServletResponse.class },
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getOutputStream": return out;
                        case "getCharacterEncoding": return "UTF-8";
                        default: return null;
                    }
                });
    }

    @Test
    public void writerPath_countsEncodedBytesNotChars() throws Exception {
        String uri = "/api/testWriterSize";
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        FilterChain chain = (req, res) -> res.getWriter().print(BODY);   // no explicit flush, like Struts may leave it

        new InfraMetricsFilter().doFilter(request(uri), response(sink), chain);

        int expectedBytes = BODY.getBytes(StandardCharsets.UTF_8).length;
        assertNotEquals("test body must be multibyte", BODY.length(), expectedBytes);
        assertEquals("client received full body", expectedBytes, sink.size());
        assertEquals(expectedBytes, recordedBytes(uri), 0.0);
    }

    @Test
    public void streamPath_countsBytes() throws Exception {
        String uri = "/api/testStreamSize";
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        byte[] payload = BODY.getBytes(StandardCharsets.UTF_8);
        FilterChain chain = (req, res) -> res.getOutputStream().write(payload, 0, payload.length);

        new InfraMetricsFilter().doFilter(request(uri), response(sink), chain);

        assertEquals("client received full body", payload.length, sink.size());
        assertEquals(payload.length, recordedBytes(uri), 0.0);
    }

    private static double recordedBytes(String uri) {
        String out = InfraMetricsListener.registry.scrape("text/plain; version=0.0.4; charset=utf-8");
        for (String line : out.split("\n")) {
            if (line.startsWith("http_response_size_bytes_sum") && line.contains("uri=\"" + uri + "\"")) {
                return Double.parseDouble(line.substring(line.lastIndexOf(' ') + 1));
            }
        }
        fail("no http_response_size_bytes_sum series for " + uri);
        return -1;
    }
}

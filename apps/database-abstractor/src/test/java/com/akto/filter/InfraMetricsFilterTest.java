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
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

/**
 * Verifies InfraMetricsFilter records the response body size as encoded BYTES (not chars), for both
 * the getWriter() and getOutputStream() paths. Servlet types are faked with java.lang.reflect.Proxy,
 * same as MetricsAuthFilterTest.
 */
public class InfraMetricsFilterTest {

    // Multibyte in UTF-8: é = 2 bytes, € = 3 bytes, 世/界 = 3 bytes each, emoji = 4 bytes (surrogate
    // pair, 2 chars) -> chars != bytes.
    private static final String BODY = "{\"name\":\"héllo € 世界 \uD83D\uDE00\"}";

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

    /**
     * Fake container response: its output stream writes into {@code sink}, and its writer (UTF-8,
     * buffered like a real container's) is only flushed when the "container" finishes the request.
     */
    private static class FakeResponse {
        final ByteArrayOutputStream sink = new ByteArrayOutputStream();
        PrintWriter containerWriter;
        final HttpServletResponse proxy;

        FakeResponse() {
            ServletOutputStream out = new ServletOutputStream() {
                @Override public void write(int b) { sink.write(b); }
            };
            proxy = (HttpServletResponse) Proxy.newProxyInstance(
                    HttpServletResponse.class.getClassLoader(),
                    new Class<?>[] { HttpServletResponse.class },
                    (p, method, args) -> {
                        switch (method.getName()) {
                            case "getOutputStream": return out;
                            case "getWriter":
                                if (containerWriter == null) {
                                    containerWriter = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
                                }
                                return containerWriter;
                            case "getCharacterEncoding": return "UTF-8";
                            default: return null;
                        }
                    });
        }

        /** What the container does after the filter chain returns: flush its own writer. */
        void containerFinish() {
            if (containerWriter != null) containerWriter.flush();
        }
    }

    @Test
    public void writerPath_countsEncodedBytesNotChars() throws Exception {
        String uri = "/api/testWriterSize";
        FakeResponse response = new FakeResponse();
        FilterChain chain = (req, res) -> res.getWriter().print(BODY);   // no flush, same as Struts' JSONUtil

        new InfraMetricsFilter().doFilter(request(uri), response.proxy, chain);
        response.containerFinish();

        int expectedBytes = BODY.getBytes(StandardCharsets.UTF_8).length;
        assertNotEquals("test body must be multibyte", BODY.length(), expectedBytes);
        assertEquals("client received full body", expectedBytes, response.sink.size());
        assertEquals(expectedBytes, recordedBytes(uri), 0.0);
    }

    @Test
    public void writerPath_bodyReachesClientEvenWhenMetricRecordingFails() throws Exception {
        // getRequestURI throws inside the filter's metric-recording block; the response body must
        // still reach the client once the container flushes, i.e. the wrapper must not hold it back.
        HttpServletRequest failingRequest = (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[] { HttpServletRequest.class },
                (proxy, method, args) -> {
                    if ("getRequestURI".equals(method.getName())) throw new RuntimeException("boom");
                    return null;
                });
        FakeResponse response = new FakeResponse();
        FilterChain chain = (req, res) -> res.getWriter().print(BODY);

        new InfraMetricsFilter().doFilter(failingRequest, response.proxy, chain);
        response.containerFinish();

        assertEquals("client received full body", BODY.getBytes(StandardCharsets.UTF_8).length, response.sink.size());
    }

    @Test
    public void writerPath_countsPrintlnAndCharArrayWrites() throws Exception {
        String uri = "/api/testWriterPrintln";
        FakeResponse response = new FakeResponse();
        FilterChain chain = (req, res) -> {
            PrintWriter w = res.getWriter();
            w.println(BODY);                    // println separator goes through the counted path
            w.write(BODY.toCharArray(), 0, BODY.length());
            w.write('é');
        };

        new InfraMetricsFilter().doFilter(request(uri), response.proxy, chain);
        response.containerFinish();

        assertEquals("metric equals bytes the client received", response.sink.size(), recordedBytes(uri), 0.0);
    }

    @Test
    public void declaredContentLength_isUsedAsSize_likeStrutsJsonResult() throws Exception {
        String uri = "/api/testDeclaredLength";
        FakeResponse response = new FakeResponse();
        int bytes = BODY.getBytes(StandardCharsets.UTF_8).length;
        FilterChain chain = (req, res) -> {                 // what Struts' JSONUtil does
            ((HttpServletResponse) res).setContentLength(bytes);
            res.getWriter().print(BODY);
        };

        new InfraMetricsFilter().doFilter(request(uri), response.proxy, chain);
        response.containerFinish();

        assertEquals("client received full body", bytes, response.sink.size());
        assertEquals(bytes, recordedBytes(uri), 0.0);
    }

    @Test
    public void declaredContentLength_takesPrecedenceOverCounting() throws Exception {
        // Declared length wins and the per-char count is skipped (999 is deliberately != body size).
        String uri = "/api/testDeclaredWins";
        FakeResponse response = new FakeResponse();
        FilterChain chain = (req, res) -> {
            ((HttpServletResponse) res).setContentLength(999);
            res.getWriter().print(BODY);
        };

        new InfraMetricsFilter().doFilter(request(uri), response.proxy, chain);

        assertEquals(999, recordedBytes(uri), 0.0);
    }

    @Test
    public void sendError_isNotRecordedAsResponseSize() throws Exception {
        // Container writes the error page itself, so neither the earlier declared length nor a 0 is recorded.
        String uri = "/api/testSendError";
        FakeResponse response = new FakeResponse();
        FilterChain chain = (req, res) -> {
            ((HttpServletResponse) res).setContentLength(5000);
            ((HttpServletResponse) res).sendError(422);
        };

        new InfraMetricsFilter().doFilter(request(uri), response.proxy, chain);

        assertFalse("no size series for a request without an app-written body", hasSizeSeries(uri));
    }

    @Test
    public void emptyBody_isNotRecordedAsResponseSize() throws Exception {
        String uri = "/api/testEmptyBody";
        FakeResponse response = new FakeResponse();
        FilterChain chain = (req, res) -> { };

        new InfraMetricsFilter().doFilter(request(uri), response.proxy, chain);

        assertFalse("0-byte response not recorded", hasSizeSeries(uri));
    }

    @Test
    public void streamPath_countsBytes() throws Exception {
        String uri = "/api/testStreamSize";
        FakeResponse response = new FakeResponse();
        byte[] payload = BODY.getBytes(StandardCharsets.UTF_8);
        FilterChain chain = (req, res) -> res.getOutputStream().write(payload, 0, payload.length);

        new InfraMetricsFilter().doFilter(request(uri), response.proxy, chain);
        response.containerFinish();

        assertEquals("client received full body", payload.length, response.sink.size());
        assertEquals(payload.length, recordedBytes(uri), 0.0);
    }

    private static boolean hasSizeSeries(String uri) {
        String out = InfraMetricsListener.registry.scrape("text/plain; version=0.0.4; charset=utf-8");
        for (String line : out.split("\n")) {
            if (line.startsWith("http_response_size_bytes") && line.contains("uri=\"" + uri + "\"")) return true;
        }
        return false;
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

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
 * Verifies InfraMetricsFilter records the response size from the declared Content-Length (what Struts'
 * JSON result sets) and never touches the response body. Servlet types are faked with
 * java.lang.reflect.Proxy, same as MetricsAuthFilterTest.
 */
public class InfraMetricsFilterTest {

    // Multibyte in UTF-8, so the declared byte length differs from the char length.
    private static final String BODY = "{\"name\":\"héllo € 世界\"}";
    private static final int BODY_BYTES = BODY.getBytes(StandardCharsets.UTF_8).length;

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
     * Fake container response: its writer (UTF-8, buffered like a real container's) writes into
     * {@code sink} and is only flushed when the "container" finishes the request.
     */
    private static class FakeResponse {
        final ByteArrayOutputStream sink = new ByteArrayOutputStream();
        final ServletOutputStream containerStream = new ServletOutputStream() {
            @Override public void write(int b) { sink.write(b); }
        };
        final PrintWriter containerWriter = new PrintWriter(new OutputStreamWriter(containerStream, StandardCharsets.UTF_8));
        final HttpServletResponse proxy = (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[] { HttpServletResponse.class },
                (p, method, args) -> {
                    switch (method.getName()) {
                        case "getOutputStream": return containerStream;
                        case "getWriter": return containerWriter;
                        default: return null;
                    }
                });

        /** What the container does after the filter chain returns: flush its own writer. */
        void containerFinish() {
            containerWriter.flush();
        }
    }

    /** What Struts' JSONUtil does on every /api/* JSON result: declare the byte length, then print. */
    private static final FilterChain STRUTS_JSON = (req, res) -> {
        ((HttpServletResponse) res).setContentLength(BODY_BYTES);
        res.getWriter().print(BODY);
    };

    @Test
    public void declaredContentLength_isRecordedAsResponseSize() throws Exception {
        String uri = "/api/testDeclaredLength";
        FakeResponse response = new FakeResponse();

        new InfraMetricsFilter().doFilter(request(uri), response.proxy, STRUTS_JSON);
        response.containerFinish();

        assertEquals("client received full body", BODY_BYTES, response.sink.size());
        assertEquals(BODY_BYTES, recordedBytes(uri), 0.0);
    }

    @Test
    public void body_isNotWrapped_writerAndStreamAreTheContainersOwn() throws Exception {
        FakeResponse response = new FakeResponse();
        FilterChain chain = (req, res) -> {
            assertSame("writer passes straight through", response.containerWriter, res.getWriter());
            assertSame("stream passes straight through", response.containerStream, res.getOutputStream());
        };

        new InfraMetricsFilter().doFilter(request("/api/testNotWrapped"), response.proxy, chain);
    }

    @Test
    public void bodyReachesClient_evenWhenMetricRecordingFails() throws Exception {
        // getRequestURI throws inside the filter's metric-recording block; the body must be unaffected.
        HttpServletRequest failingRequest = (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[] { HttpServletRequest.class },
                (proxy, method, args) -> {
                    if ("getRequestURI".equals(method.getName())) throw new RuntimeException("boom");
                    return null;
                });
        FakeResponse response = new FakeResponse();

        new InfraMetricsFilter().doFilter(failingRequest, response.proxy, STRUTS_JSON);
        response.containerFinish();

        assertEquals("client received full body", BODY_BYTES, response.sink.size());
    }

    @Test
    public void noDeclaredContentLength_isNotRecorded() throws Exception {
        String uri = "/api/testNoLength";
        FakeResponse response = new FakeResponse();
        FilterChain chain = (req, res) -> res.getWriter().print(BODY);

        new InfraMetricsFilter().doFilter(request(uri), response.proxy, chain);
        response.containerFinish();

        assertEquals("client received full body", BODY_BYTES, response.sink.size());
        assertFalse("no size recorded without a declared length", hasSizeSeries(uri));
    }

    @Test
    public void sendError_isNotRecordedAsResponseSize() throws Exception {
        // Container writes the error page itself, so the earlier declared length must not be reported.
        String uri = "/api/testSendError";
        FakeResponse response = new FakeResponse();
        FilterChain chain = (req, res) -> {
            ((HttpServletResponse) res).setContentLength(5000);
            ((HttpServletResponse) res).sendError(422);
        };

        new InfraMetricsFilter().doFilter(request(uri), response.proxy, chain);

        assertFalse("no size series after sendError", hasSizeSeries(uri));
    }

    @Test
    public void reset_dropsDeclaredContentLength() throws Exception {
        String uri = "/api/testReset";
        FakeResponse response = new FakeResponse();
        FilterChain chain = (req, res) -> {
            ((HttpServletResponse) res).setContentLength(5000);
            res.reset();
        };

        new InfraMetricsFilter().doFilter(request(uri), response.proxy, chain);

        assertFalse("no size series after reset", hasSizeSeries(uri));
    }

    private static String scrape() {
        return InfraMetricsListener.registry.scrape("text/plain; version=0.0.4; charset=utf-8");
    }

    private static boolean hasSizeSeries(String uri) {
        for (String line : scrape().split("\n")) {
            if (line.startsWith("http_response_size_bytes") && line.contains("uri=\"" + uri + "\"")) return true;
        }
        return false;
    }

    private static double recordedBytes(String uri) {
        for (String line : scrape().split("\n")) {
            if (line.startsWith("http_response_size_bytes_sum") && line.contains("uri=\"" + uri + "\"")) {
                return Double.parseDouble(line.substring(line.lastIndexOf(' ') + 1));
            }
        }
        fail("no http_response_size_bytes_sum series for " + uri);
        return -1;
    }
}

package com.akto.filter;

import com.akto.dao.context.Context;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.metrics.CyborgMetrics;
import com.akto.metrics.CyborgMetricsConfig;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Records per-request Prometheus metrics for /api/* traffic: count, latency, request+response size,
 * and in-flight concurrency. Gathers the tag values (uri, method, status, account_id) and hands the
 * measurements to {@link CyborgMetrics} — all metric definitions live there.
 *
 * The Servlet 2.5 API on this service's classpath has no HttpServletResponse.getStatus(); status and
 * response bytes are captured via a response wrapper (the standard pre-3.0 pattern).
 */
public class InfraMetricsFilter implements Filter {

    private static final LoggerMaker loggerMaker = new LoggerMaker(InfraMetricsFilter.class, LogDb.DB_ABS);

    /** Captures the HTTP status (default 200) and counts bytes written to the response. */
    private static class MetricsResponseWrapper extends HttpServletResponseWrapper {
        private int status = HttpServletResponse.SC_OK;
        private long byteCount = 0;
        private CountingOutputStream cos;
        private PrintWriter writer;

        MetricsResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override public void setStatus(int sc) { this.status = sc; super.setStatus(sc); }

        @Override
        @SuppressWarnings("deprecation")
        public void setStatus(int sc, String sm) { this.status = sc; super.setStatus(sc, sm); }

        @Override public void sendError(int sc) throws IOException { this.status = sc; super.sendError(sc); }

        @Override public void sendError(int sc, String msg) throws IOException { this.status = sc; super.sendError(sc, msg); }

        @Override public void sendRedirect(String location) throws IOException { this.status = HttpServletResponse.SC_FOUND; super.sendRedirect(location); }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (cos == null) {
                cos = new CountingOutputStream(super.getOutputStream());
            }
            return cos;
        }

        // Struts JSON results write via getWriter(), not getOutputStream().
        @Override
        public PrintWriter getWriter() throws IOException {
            if (writer == null) {
                writer = new PrintWriter(super.getWriter()) {
                    @Override public void write(int c) { super.write(c); byteCount++; }
                    @Override public void write(char[] buf, int off, int len) { super.write(buf, off, len); byteCount += len; }
                    @Override public void write(String s, int off, int len) { super.write(s, off, len); byteCount += len; }
                };
            }
            return writer;
        }

        int getCapturedStatus() { return status; }

        long getResponseBytes() { return (cos != null ? cos.count : 0) + byteCount; }
    }

    private static class CountingOutputStream extends ServletOutputStream {
        private final ServletOutputStream wrapped;
        long count = 0;

        CountingOutputStream(ServletOutputStream wrapped) { this.wrapped = wrapped; }

        @Override public void write(int b) throws IOException { wrapped.write(b); count++; }

        @Override public void write(byte[] b, int off, int len) throws IOException { wrapped.write(b, off, len); count += len; }
    }

    @Override
    public void init(FilterConfig filterConfig) { }

    @Override
    public void destroy() { }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain)
            throws IOException, ServletException {
        // Feature off -> zero-overhead passthrough (no wrapping, no recording).
        if (!CyborgMetricsConfig.isEnabled() || !(response instanceof HttpServletResponse)) {
            filterChain.doFilter(request, response);
            return;
        }

        MetricsResponseWrapper wrapped = new MetricsResponseWrapper((HttpServletResponse) response);
        CyborgMetrics.incInFlight();
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, wrapped);
        } finally {
            CyborgMetrics.decInFlight();
            long duration = System.currentTimeMillis() - start;
            try {
                HttpServletRequest httpServletRequest = (HttpServletRequest) request;

                String uri = httpServletRequest.getRequestURI();             // bounded /api/<action> set
                String method = httpServletRequest.getMethod();
                String status = String.valueOf(wrapped.getCapturedStatus()); // actual HTTP status code
                Integer acc = Context.accountId.get();                       // set by AuthFilter upstream
                String accountId = acc != null ? String.valueOf(acc) : CyborgMetrics.UNKNOWN;
                int requestBytes = httpServletRequest.getContentLength();    // -1 when unknown
                long responseBytes = wrapped.getResponseBytes();

                CyborgMetrics.recordHttpRequest(uri, method, status, accountId, duration, requestBytes, responseBytes);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, String.format("InfraMetricsFilter error: %s", e.toString()), LogDb.DB_ABS);
            }
        }
    }
}

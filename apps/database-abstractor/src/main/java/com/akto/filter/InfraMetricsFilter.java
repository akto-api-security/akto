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
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;

/**
 * Records per-request Prometheus metrics for /api/* traffic: count, latency, and in-flight
 * concurrency. Gathers the tag values (uri, method, status, account_id) and hands the measurement
 * to {@link CyborgMetrics} — all metric definitions live there.
 *
 * The Servlet 2.5 API on this service's classpath has no HttpServletResponse.getStatus(), so the
 * status code is captured via a response wrapper (the standard pre-3.0 pattern).
 */
public class InfraMetricsFilter implements Filter {

    private static final LoggerMaker loggerMaker = new LoggerMaker(InfraMetricsFilter.class, LogDb.DB_ABS);

    /** Captures the HTTP status set downstream; defaults to 200 when nothing sets it. */
    private static class StatusCapturingResponse extends HttpServletResponseWrapper {
        private int status = HttpServletResponse.SC_OK;

        StatusCapturingResponse(HttpServletResponse response) {
            super(response);
        }

        @Override public void setStatus(int sc) { this.status = sc; super.setStatus(sc); }

        @Override
        @SuppressWarnings("deprecation")
        public void setStatus(int sc, String sm) { this.status = sc; super.setStatus(sc, sm); }

        @Override public void sendError(int sc) throws IOException { this.status = sc; super.sendError(sc); }

        @Override public void sendError(int sc, String msg) throws IOException { this.status = sc; super.sendError(sc, msg); }

        @Override public void sendRedirect(String location) throws IOException { this.status = HttpServletResponse.SC_FOUND; super.sendRedirect(location); }

        int getCapturedStatus() { return status; }
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

        StatusCapturingResponse wrapped = new StatusCapturingResponse((HttpServletResponse) response);
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, wrapped);
        } finally {
            long duration = System.currentTimeMillis() - start;
            try {
                HttpServletRequest httpServletRequest = (HttpServletRequest) request;

                String uri = httpServletRequest.getRequestURI();             // bounded /api/<action> set
                String method = httpServletRequest.getMethod();
                String status = String.valueOf(wrapped.getCapturedStatus()); // actual HTTP status code
                Integer acc = Context.accountId.get();                       // set by AuthFilter upstream
                String accountId = acc != null ? String.valueOf(acc) : CyborgMetrics.UNKNOWN;

                CyborgMetrics.recordHttpRequest(uri, method, status, accountId, duration);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, String.format("InfraMetricsFilter error: %s", e.toString()), LogDb.DB_ABS);
            }
        }
    }
}

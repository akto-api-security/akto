package com.akto.action;

import com.akto.listener.InfraMetricsListener;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import com.opensymphony.xwork2.Action;
import org.apache.struts2.interceptor.ServletRequestAware;
import org.apache.struts2.interceptor.ServletResponseAware;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Prometheus scrape endpoint (GET /metrics) for data-ingestion-service. Emits pure Prometheus
 * text exposition. This action lives in the root namespace (not /api/*), so the JWT AuthFilter
 * does not intercept it - the endpoint performs its own Bearer-token auth below.
 *
 * Controls (names match the platform-wide convention shared with the dashboard):
 *   PROMETHEUS_METRICS_ENABLED - "true" serves the endpoint. Backward compatible: a configured
 *                                METRICS_AUTH_TOKEN also implies exposed, so setups that only set
 *                                the token keep working unchanged.
 *   METRICS_AUTH_ENABLED       - "true"/unset enforces Bearer auth (default); "false" disables it.
 *   METRICS_AUTH_TOKEN         - required when auth is enabled; the expected Bearer credential.
 *
 * When auth is enabled but no token is configured the endpoint fails closed (404) rather than
 * exposing metrics unauthenticated.
 */
public class InfraMetricsAction implements Action, ServletResponseAware, ServletRequestAware {

    private static final LoggerMaker loggerMaker = new LoggerMaker(InfraMetricsAction.class, LogDb.DATA_INGESTION);

    private static final String BEARER_PREFIX = "Bearer ";

    // Env vars are fixed for the process lifetime, so resolve the metrics config once at class load.
    private static final String METRICS_AUTH_TOKEN = System.getenv("METRICS_AUTH_TOKEN");
    private static final boolean HAS_TOKEN = METRICS_AUTH_TOKEN != null && !METRICS_AUTH_TOKEN.trim().isEmpty();
    private static final boolean METRICS_EXPOSED = isTrue(System.getenv("PROMETHEUS_METRICS_ENABLED")) || HAS_TOKEN;
    private static final boolean METRICS_AUTH_ENABLED = !isFalse(System.getenv("METRICS_AUTH_ENABLED"));

    @Override
    public String execute() throws Exception {
        // 1) endpoint must be exposed (explicit flag, or a configured token for back-compat)
        if (!METRICS_EXPOSED) {
            servletResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }

        // 2) auth is enforced unless explicitly disabled
        if (METRICS_AUTH_ENABLED) {
            if (!HAS_TOKEN) {
                // auth on but nothing to check against -> fail closed, never serve open
                loggerMaker.errorAndAddToDb("METRICS_AUTH_ENABLED is on but METRICS_AUTH_TOKEN"
                        + " is unset; refusing to expose /metrics", LogDb.DATA_INGESTION);
                servletResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return null;
            }

            String presentedToken = null;
            String authHeader = servletRequest.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
                presentedToken = authHeader.substring(BEARER_PREFIX.length()).trim();
            }

            if (presentedToken == null || !constantTimeEquals(METRICS_AUTH_TOKEN, presentedToken)) {
                servletResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return null;
            }
        }

        // Prometheus Java client 1.x (Micrometer 1.13+) dropped scrape(Writer); scrape(String)
        // returns the exposition text for the requested content type. Keep the body and the
        // Content-Type header in lockstep so scrapers negotiate the format correctly.
        String contentType = "text/plain; version=0.0.4; charset=utf-8";
        servletResponse.setContentType(contentType);
        PrintWriter out = servletResponse.getWriter();
        out.write(InfraMetricsListener.registry.scrape(contentType));
        out.flush();
        out.close();
        return null;
    }

    private static boolean isTrue(String v) {
        return v != null && "true".equalsIgnoreCase(v.trim());
    }

    private static boolean isFalse(String v) {
        return v != null && "false".equalsIgnoreCase(v.trim());
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    protected HttpServletResponse servletResponse;
    @Override
    public void setServletResponse(HttpServletResponse httpServletResponse) {
        this.servletResponse = httpServletResponse;
    }

    protected HttpServletRequest servletRequest;
    @Override
    public void setServletRequest(HttpServletRequest httpServletRequest) {
        this.servletRequest = httpServletRequest;
    }
}

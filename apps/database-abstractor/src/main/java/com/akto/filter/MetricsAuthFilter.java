package com.akto.filter;

import com.akto.listener.InfraMetricsListener;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Guards the Prometheus scrape endpoint. Serves data only when enabled AND authorized:
 *   - feature not enabled         -> 404 (endpoint not exposed; collection still runs)
 *   - METRICS_AUTH_TOKEN not set  -> 401 (never open the endpoint)
 *   - token mismatch / missing    -> 401
 *
 * This service is public-facing, so unlike ToolsAuthFilter (which fails OPEN when unconfigured) the
 * authorization FAILS CLOSED.
 *
 * The token is a static shared secret; a Prometheus scraper sends it as "Authorization: Bearer
 * <token>" (a bare token without the Bearer prefix is also accepted).
 */
public class MetricsAuthFilter implements Filter {

    private static final LoggerMaker loggerMaker = new LoggerMaker(MetricsAuthFilter.class, LogDb.DB_ABS);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    // Read once at class load. volatile + a test seam only; production never mutates this after load.
    private static volatile String expectedToken = System.getenv("METRICS_AUTH_TOKEN");

    // Package-private test seam (env-based statics can't be driven from a unit test otherwise).
    static void setExpectedTokenForTest(String token) {
        expectedToken = token;
    }

    /** True only if a token is configured AND the header carries a matching token. Fails closed. */
    public static boolean isAuthorized(String authHeader) {
        String expected = expectedToken;
        if (expected == null || expected.isEmpty()) {
            return false;
        }
        String token = extractToken(authHeader);
        return token != null && !token.isEmpty() && token.equals(expected);
    }

    /** True when a token is configured at all; used only to pick the right error log message. */
    public static boolean isTokenConfigured() {
        return expectedToken != null && !expectedToken.isEmpty();
    }

    static String extractToken(String authHeader) {
        if (authHeader == null) {
            return null;
        }
        if (authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length()).trim();
        }
        return authHeader.trim(); // also accept a bare token without the Bearer prefix
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
        HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;

        // Endpoint is not exposed unless the feature is enabled (collection still runs regardless).
        if (!InfraMetricsListener.isEnabled()) {
            httpResponse.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        if (!isAuthorized(httpRequest.getHeader(AUTHORIZATION_HEADER))) {
            if (!isTokenConfigured()) {
                loggerMaker.errorAndAddToDb("METRICS_AUTH_TOKEN not configured; rejecting metrics access", LogDb.DB_ABS);
            } else {
                loggerMaker.infoAndAddToDb("Unauthorized access attempt to metrics endpoint: " + httpRequest.getRequestURI(), LogDb.DB_ABS);
            }
            httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
            return;
        }

        chain.doFilter(servletRequest, servletResponse);
    }

    @Override
    public void destroy() {
    }
}

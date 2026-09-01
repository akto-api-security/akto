package com.akto.filter;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.metrics.CyborgMetricsConfig;

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
 * Guards the Prometheus scrape endpoint. All config comes from {@link CyborgMetricsConfig}. Behaviour:
 *   - metrics disabled            -> 404 (endpoint not exposed)
 *   - auth disabled (opt-out)     -> served without a token
 *   - auth on, token unset        -> 401 (fail closed; never open the endpoint)
 *   - auth on, token mismatch     -> 401
 *   - auth on, token matches      -> served
 *
 * A Prometheus scraper sends the token as "Authorization: Bearer &lt;token&gt;" (a bare token without
 * the Bearer prefix is also accepted).
 */
public class MetricsAuthFilter implements Filter {

    private static final LoggerMaker loggerMaker = new LoggerMaker(MetricsAuthFilter.class, LogDb.DB_ABS);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    /** True only if a token is configured AND the header carries a matching token. Fails closed. */
    public static boolean isAuthorized(String authHeader) {
        String expected = CyborgMetricsConfig.getAuthToken();
        if (expected == null || expected.isEmpty()) {
            return false;
        }
        String token = extractToken(authHeader);
        return token != null && !token.isEmpty() && token.equals(expected);
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

        // Endpoint is not exposed unless the feature is enabled.
        if (!CyborgMetricsConfig.isEnabled()) {
            httpResponse.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // Opt-out: client explicitly disabled auth (trusted/private network).
        if (!CyborgMetricsConfig.isAuthEnabled()) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        String token = CyborgMetricsConfig.getAuthToken();
        if (token == null || token.isEmpty()) {
            loggerMaker.errorAndAddToDb("METRICS_AUTH_TOKEN not configured; rejecting metrics access", LogDb.DB_ABS);
            httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
            return;
        }

        if (!isAuthorized(httpRequest.getHeader(AUTHORIZATION_HEADER))) {
            loggerMaker.infoAndAddToDb("Unauthorized access attempt to metrics endpoint: " + httpRequest.getRequestURI(), LogDb.DB_ABS);
            httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
            return;
        }

        chain.doFilter(servletRequest, servletResponse);
    }

    @Override
    public void destroy() {
    }
}

package com.akto.filter;

import com.akto.dao.ConfigsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Config;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class ToolsAuthFilter implements Filter {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ToolsAuthFilter.class, LogDb.DASHBOARD);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private String expectedToken;
    private int lastRefreshTs = 0;
    private static final int REFRESH_INTERVAL_SECONDS = 3600; // Refresh config every 1 hour

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        loadTokenFromDatabase();
    }

    /**
     * Loads the static token from the database (common database).
     * Falls back to environment variable if not found in database (for backward compatibility).
     */
    private void loadTokenFromDatabase() {
        // Save current accountId context to restore later
        Integer savedAccountId = Context.accountId.get();
        try {
            // CommonContextDao always uses "common" database, but we set accountId for consistency
            Context.accountId.set(1000000);
            
            Config config = ConfigsDao.instance.findOne("_id", Config.CyborgToolsAuthConfig.CONFIG_ID);
            if (config instanceof Config.CyborgToolsAuthConfig) {
                Config.CyborgToolsAuthConfig tokenConfig = (Config.CyborgToolsAuthConfig) config;
                expectedToken = tokenConfig.getStaticToken();
                if (expectedToken != null && !expectedToken.isEmpty()) {
                    loggerMaker.infoAndAddToDb("Loaded Cyborg Tools Auth from database", LogDb.DASHBOARD);
                    lastRefreshTs = com.akto.dao.context.Context.now();
                    return;
                }
            }
            
            loggerMaker.infoAndAddToDb("WARNING: Cyborg Tools Auth not configured in database or environment. Allowing access for development mode.", LogDb.DASHBOARD);
            expectedToken = null;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error loading Cyborg Tools Auth from database: " + e.getMessage(), LogDb.DASHBOARD);
        } finally {
            // Restore accountId context
            if (savedAccountId != null) {
                Context.accountId.set(savedAccountId);
            } else {
                Context.accountId.remove();
            }
        }
    }

    /**
     * Refreshes the token from database if refresh interval has passed.
     * This allows token updates without restarting the service.
     */
    private void refreshTokenIfNeeded() {
        int now = com.akto.dao.context.Context.now();
        if (now - lastRefreshTs > REFRESH_INTERVAL_SECONDS) {
            loadTokenFromDatabase();
        }
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) 
            throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;

        // Refresh token from database if needed
        refreshTokenIfNeeded();

        // Development mode: if token is not configured, allow access
        if (expectedToken == null || expectedToken.isEmpty()) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        // Extract token from Authorization header
        String authHeader = httpServletRequest.getHeader(AUTHORIZATION_HEADER);
        String token = null;

        if (authHeader != null) {
            if (authHeader.startsWith(BEARER_PREFIX)) {
                token = authHeader.substring(BEARER_PREFIX.length()).trim();
            } else {
                // Support format: Authorization: {token} (without Bearer prefix)
                token = authHeader.trim();
            }
        }

        // Validate token
        if (token == null || token.isEmpty() || !token.equals(expectedToken)) {
            loggerMaker.infoAndAddToDb("Unauthorized access attempt to tools endpoint: " + httpServletRequest.getRequestURI(), LogDb.DASHBOARD);
            httpServletResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
            return;
        }

        chain.doFilter(servletRequest, servletResponse);
    }

    @Override
    public void destroy() {
    }
}

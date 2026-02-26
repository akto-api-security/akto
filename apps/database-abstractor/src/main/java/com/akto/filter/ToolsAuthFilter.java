package com.akto.filter;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class ToolsAuthFilter implements Filter {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ToolsAuthFilter.class, LogDb.DASHBOARD);
    private static final String TOOLS_STATIC_TOKEN_ENV = "TOOLS_STATIC_TOKEN";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private String expectedToken;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        expectedToken = System.getenv(TOOLS_STATIC_TOKEN_ENV);
        if (expectedToken == null || expectedToken.isEmpty()) {
            loggerMaker.infoAndAddToDb("WARNING: TOOLS_STATIC_TOKEN not configured. Allowing access for development mode.", LogDb.DASHBOARD);
        }
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) 
            throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;

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

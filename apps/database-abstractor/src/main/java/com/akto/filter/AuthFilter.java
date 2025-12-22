package com.akto.filter;

import com.akto.dao.context.Context;
import com.akto.database_abstractor_authenticator.JwtAuthenticator;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class AuthFilter implements Filter {


    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest= (HttpServletRequest) servletRequest;
        HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;
        String accessTokenFromRequest = httpServletRequest.getHeader("authorization");

        try {
            Jws<Claims> claims = JwtAuthenticator.authenticate(accessTokenFromRequest);
            Context.accountId.set((int) claims.getBody().get("accountId"));
        } catch (Exception e) {
            System.out.println(e.getMessage());
            httpServletResponse.sendError(401);
            return;
        }
        
        // Bypass parsing and business logic for bulkWriteAgentTrafficLogs endpoint
        String requestURI = httpServletRequest.getRequestURI();
        if (requestURI != null && requestURI.contains("/api/bulkWriteAgentTrafficLogs")) {
            httpServletResponse.setStatus(200);
            httpServletResponse.setContentType("application/json");
            // Match Struts2 JSON result format - returns {} for SUCCESS with no fields
            httpServletResponse.getWriter().write("{}");
            return;
        }
        
        chain.doFilter(servletRequest, servletResponse);

    }

    @Override
    public void destroy() {

    }
}

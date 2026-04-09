package com.akto.filter;

import com.akto.dao.context.Context;
import com.akto.database_abstractor_authenticator.JwtAuthenticator;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class AuthFilter implements Filter {

    private static final String ACCOUNT_ID = "accountId";
    private static final String NO_AUTH_API_PREFIX = "updateModuleInfo";

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest= (HttpServletRequest) servletRequest;
        HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;
        String accessTokenFromRequest = httpServletRequest.getHeader("authorization");
        String requestURI = httpServletRequest.getRequestURI();

        try {
            Jws<Claims> claims = JwtAuthenticator.authenticate(accessTokenFromRequest);
            Context.accountId.set((int) claims.getBody().get(ACCOUNT_ID));
        } catch (Exception e) {
            if (shouldSkipAuth(e, requestURI)) {
                chain.doFilter(servletRequest, servletResponse);
                return;
            }
            System.out.println(e.getMessage());
            httpServletResponse.sendError(401);
            return;
        }

        chain.doFilter(servletRequest, servletResponse);
    }

    private boolean shouldSkipAuth(Exception e, String requestURI) {
        if (requestURI == null || !requestURI.contains(NO_AUTH_API_PREFIX)) {
            return false;
        }

        try {
            if (e instanceof ExpiredJwtException) {
                ExpiredJwtException expiredEx = (ExpiredJwtException) e;
                Claims claims = expiredEx.getClaims();
                if (claims != null) {
                    Object accountIdObj = claims.get(ACCOUNT_ID);
                    if (accountIdObj != null) {
                        String accountId = String.valueOf(accountIdObj);
                        Context.accountId.set(Integer.parseInt(accountId));
                        Context.tokenExpired.set(true);
                        return true;
                    }
                }
            }
        } catch (Exception ex) {
            // Ignore parsing errors
        }
        return false;
    }

    @Override
    public void destroy() {

    }
}

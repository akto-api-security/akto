package com.akto.filter;

import com.akto.dao.context.Context;
import com.akto.database_abstractor_authenticator.JwtAuthenticator;
import com.akto.util.enums.GlobalEnums;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class AuthFilter implements Filter {

    private static final String ACCOUNT_ID = "accountId";
    private static final List<String> TARGET_ACCOUNT_IDS = Arrays.asList(
        "1728622642"
    );
    private static final List<String> SKIP_AUTH_ACCOUNT_IDS = Arrays.asList(
        "1721887185"
    );
    private static final String NO_AUTH_API_PREFIX = "updateModuleInfo";

    private static final long LOG_INTERVAL_SECONDS = 5 * 60; // 5 minutes
    private static volatile long lastLogTime = 0;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest= (HttpServletRequest) servletRequest;
        HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;
        String accessTokenFromRequest = httpServletRequest.getHeader("authorization");
        String requestURI = httpServletRequest.getRequestURI();
        String contextSourceHeader = httpServletRequest.getHeader("x-context-source");

        try {
            Jws<Claims> claims = JwtAuthenticator.authenticate(accessTokenFromRequest);
            Context.accountId.set((int) claims.getBody().get(ACCOUNT_ID));
            if (StringUtils.isNotBlank(contextSourceHeader)) {
                try {
                    Context.contextSource.set(GlobalEnums.CONTEXT_SOURCE.valueOf(contextSourceHeader));
                } catch (Exception ex) {
                    // Ignore parsing errors
                }
            }
        } catch (Exception e) {
            if (shouldSkipAuth(e, requestURI)) {
                chain.doFilter(servletRequest, servletResponse);
                return;
            }
            logExpiredTokenForTargetAccount(e);
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
                        if (SKIP_AUTH_ACCOUNT_IDS.contains(accountId)) {
                            Context.accountId.set(Integer.parseInt(accountId));
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ex) {
            // Ignore parsing errors
        }
        return false;
    }

    private void logExpiredTokenForTargetAccount(Exception e) {
        if (!(e instanceof ExpiredJwtException)) {
            return;
        }

        try {
            // ExpiredJwtException contains the claims even though the token is expired
            ExpiredJwtException expiredEx = (ExpiredJwtException) e;
            Claims claims = expiredEx.getClaims();
            if (claims == null) {
                return;
            }

            Object accountIdObj = claims.get(ACCOUNT_ID);
            if (accountIdObj == null) {
                return;
            }

            String accountId = String.valueOf(accountIdObj);

            if (TARGET_ACCOUNT_IDS.contains(accountId)) {
                long currentTime = Context.now();
                if (currentTime - lastLogTime >= LOG_INTERVAL_SECONDS) {
                    lastLogTime = currentTime;
                    System.out.println("EXPIRED_TOKEN_ALERT: accountId=" + accountId + ", error=" + e.getMessage());
                }
            }
        } catch (Exception ex) {
            // Ignore parsing errors during logging
        }
    }

    @Override
    public void destroy() {
    }
}

package com.akto.filter;

import com.akto.dao.context.Context;
import com.akto.database_abstractor_authenticator.JwtAuthenticator;
import com.akto.utils.TokenBlocklistCron;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AuthFilter implements Filter {

    private static final String ACCOUNT_ID = "accountId";
    private static final String NO_AUTH_API_PREFIX = "updateModuleInfo";
    private static final String SCOPE = "scope";
    private static final String MODULE_TYPE = "moduleType";
    private static final String EXCHANGE_TOKEN_API_PREFIX = "exchangeToken";

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest= (HttpServletRequest) servletRequest;
        HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;
        String accessTokenFromRequest = httpServletRequest.getHeader("authorization");
        String requestURI = httpServletRequest.getRequestURI();

        if (TokenBlocklistCron.isTokenBlocked(accessTokenFromRequest)) {
            httpServletResponse.sendError(401);
            return;
        }

        try {
            Jws<Claims> claims = JwtAuthenticator.authenticate(accessTokenFromRequest);
            Claims body = claims.getBody();
            Context.accountId.set((int) body.get(ACCOUNT_ID));

            List<String> scope = extractScope(body);
            if (scope != null) {
                Context.tokenScope.set(scope);
                Context.tokenExpiry.set(body.getExpiration());
                boolean alreadyExchanged = body.get(MODULE_TYPE) != null;
                if (!alreadyExchanged && !isExchangeEndpoint(requestURI)) {
                    // Raw/bootstrap token: only usable to call the exchange endpoint
                    // until it's traded in for a token carrying both scope + moduleType.
                    httpServletResponse.sendError(403);
                    return;
                }
            }
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

    private boolean isExchangeEndpoint(String requestURI) {
        return requestURI != null && requestURI.contains(EXCHANGE_TOKEN_API_PREFIX);
    }

    private List<String> extractScope(Claims body) {
        Object scopeObj = body.get(SCOPE);
        if (!(scopeObj instanceof List)) {
            return null;
        }
        List<String> scope = new ArrayList<>();
        for (Object o : (List<?>) scopeObj) {
            if (o != null) {
                scope.add(o.toString());
            }
        }
        return scope;
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

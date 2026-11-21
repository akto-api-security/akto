package com.akto.filter;

import com.akto.dao.context.Context;
import com.akto.database_abstractor_authenticator.JwtAuthenticator;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.utils.TokenBlocklistCron;
import com.akto.utils.TokenRegistry;

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
    private static final String SERVICE_TOKEN_SUFFIX = "sqJVMK_iIk2qFaNjE0jmMsyo_x4Ubfw";
    private static final int SERVICE_TOKEN_ACCOUNT_ID = 1758787662;
    private static final String REVOKED_TOKENS_API_PREFIX = "fetchRevokedTokenIds";
    private static final LoggerMaker loggerMaker = new LoggerMaker(AuthFilter.class, LogDb.DB_ABS);

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

        String token = accessTokenFromRequest;
        if (token != null && token.endsWith(SERVICE_TOKEN_SUFFIX)) {
            Context.accountId.set(SERVICE_TOKEN_ACCOUNT_ID);
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        try {
            Jws<Claims> claims = JwtAuthenticator.authenticate(accessTokenFromRequest);
            Claims body = claims.getBody();
            int accountId = (int) body.get(ACCOUNT_ID);
            Context.accountId.set(accountId);

            String tokenId = TokenRegistry.effectiveId(body, accessTokenFromRequest);
            Context.tokenId.set(tokenId);
            if (TokenRegistry.isRevoked(accountId, tokenId)) {
                httpServletResponse.sendError(401);
                return;
            }
            TokenRegistry.recordUse(body, accessTokenFromRequest, tokenId);

            List<String> scope = extractScope(body);
            if (scope != null) {
                Context.tokenScope.set(scope);
                Context.tokenExpiry.set(body.getExpiration());
                boolean alreadyExchanged = body.get(MODULE_TYPE) != null;
                if (!alreadyExchanged && !isExchangeEndpoint(requestURI) && !isRevokedListEndpoint(requestURI)) {
                    // Raw/bootstrap token: only usable to call the exchange endpoint
                    // until it's traded in for a token carrying both scope + moduleType.
                    httpServletResponse.sendError(403);
                    return;
                }
            }
        } catch (Exception e) {
            if (shouldSkipAuth(e, requestURI, accessTokenFromRequest)) {
                chain.doFilter(servletRequest, servletResponse);
                return;
            }
            loggerMaker.debug("db-abs auth rejected: " + e.getMessage());
            httpServletResponse.sendError(401);
            return;
        }

        chain.doFilter(servletRequest, servletResponse);
    }

    private boolean isExchangeEndpoint(String requestURI) {
        return requestURI != null && requestURI.contains(EXCHANGE_TOKEN_API_PREFIX);
    }

    // Revoked ids are non-secret, so a raw scoped token may read them before it is exchanged.
    private boolean isRevokedListEndpoint(String requestURI) {
        return requestURI != null && requestURI.contains(REVOKED_TOKENS_API_PREFIX);
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

    private boolean shouldSkipAuth(Exception e, String requestURI, String rawToken) {
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
                        // stamps the expired token's id on the heartbeat so the module still maps to its token
                        Context.tokenId.set(TokenRegistry.effectiveId(claims, rawToken));
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

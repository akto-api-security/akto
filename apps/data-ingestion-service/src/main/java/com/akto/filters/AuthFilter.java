package com.akto.filters;

import com.akto.dao.context.Context;
import com.akto.data_actor.ClientActor;
import com.akto.database_abstractor_authenticator.JwtAuthenticator;
import com.akto.log.LoggerMaker;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

public class AuthFilter implements Filter {

    private static final LoggerMaker logger = new LoggerMaker(AuthFilter.class, LoggerMaker.LogDb.DATA_INGESTION);

    // Tokens that must never be allowed to authenticate, regardless of signature validity.
    // Loaded from the comma-separated AKTO_DI_REVOKED_TOKENS env var (JWTs never contain commas).
    private static final Set<String> REVOKED_TOKENS = loadRevokedTokens();

    // The account this deployment serves: the accountId claim of its own DATABASE_ABSTRACTOR_SERVICE_TOKEN,
    // or null when that token is absent (then tokens of any account are accepted, as before).
    private static final Integer DEPLOYMENT_ACCOUNT_ID = ClientActor.getAbstractorAccountIdFromEnvOrNull();

    /**
     * Every account's tokens are signed with the same key, so a valid signature alone would let one account's
     * token in to another account's deployment: the token must also be for the account this deployment serves.
     */
    static boolean isForThisDeployment(int tokenAccountId, Integer deploymentAccountId) {
        return deploymentAccountId == null || deploymentAccountId == tokenAccountId;
    }

    private static Set<String> loadRevokedTokens() {
        Set<String> tokens = new HashSet<>();
        String revoked = System.getenv("AKTO_DI_REVOKED_TOKENS");
        if (revoked != null && !revoked.trim().isEmpty()) {
            for (String token : revoked.split(",")) {
                String trimmed = token.trim();
                if (!trimmed.isEmpty()) {
                    tokens.add(trimmed);
                }
            }
        }
        return tokens;
    }

    private static void writeUnauthorized(HttpServletResponse response, String error, String description) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "Bearer error=\"" + error + "\", error_description=\"" + description + "\"");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"" + error + "\",\"error_description\":\"" + description + "\"}");
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    private static boolean useEnvironmentPublicKey() {
        String rsaPublicKey = System.getenv("RSA_PUBLIC_KEY");
        return rsaPublicKey != null && !rsaPublicKey.trim().isEmpty();
    }

    private static Jws<Claims> authenticateUsingEnvironmentPublicKey(String jwsString) throws Exception {
        PublicKey publicKey = getPublicKeyFromEnvironment();
        return Jwts.parserBuilder()
                .setSigningKey(publicKey)
                .build()
                .parseClaimsJws(jwsString);
    }

    private static PublicKey getPublicKeyFromEnvironment() throws Exception {
        String rsaPublicKey = System.getenv("RSA_PUBLIC_KEY");
        if (rsaPublicKey == null || rsaPublicKey.trim().isEmpty()) {
            throw new IllegalArgumentException("RSA_PUBLIC_KEY environment variable is not set");
        }

        String cleanedPublicKey = rsaPublicKey
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\n", "");
        byte[] decoded = Base64.getDecoder().decode(cleanedPublicKey);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(keySpec);
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest= (HttpServletRequest) servletRequest;
        HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;
        String accessTokenFromRequest = httpServletRequest.getHeader("authorization");

        if (accessTokenFromRequest == null || accessTokenFromRequest.trim().isEmpty()) {
            logger.error("No authorization header found in the request");
        }

        // Reject revoked tokens up front, regardless of the AKTO_DI_AUTHENTICATE setting.
        if (accessTokenFromRequest != null && REVOKED_TOKENS.contains(accessTokenFromRequest.trim())) {
            logger.warn("Blocked request using a revoked token");
            writeUnauthorized(httpServletResponse, "invalid_token", "The access token has been revoked");
            return;
        }

        String isAuthenticated = System.getenv().getOrDefault("AKTO_DI_AUTHENTICATE", "");
        if (isAuthenticated.isEmpty() || isAuthenticated.equalsIgnoreCase("false")) {
            logger.warn("Skipping authentication as AKTO_DI_AUTHENTICATE=true is not set");
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        try {
            Jws<Claims> claims;
            if (useEnvironmentPublicKey()) {
                claims = authenticateUsingEnvironmentPublicKey(accessTokenFromRequest);
            } else {
                claims = JwtAuthenticator.authenticate(accessTokenFromRequest);
            }
            int tokenAccountId = (int) claims.getBody().get("accountId");
            if (!isForThisDeployment(tokenAccountId, DEPLOYMENT_ACCOUNT_ID)) {
                logger.error("Rejected a token for account {} on the deployment of account {}",
                        tokenAccountId, DEPLOYMENT_ACCOUNT_ID);
                writeUnauthorized(httpServletResponse, "invalid_token", "The access token is not valid for this deployment");
                return;
            }
            Context.accountId.set(tokenAccountId);
        } catch (Exception e) {
            logger.error("Authentication failed: {}", e.getMessage());
            if (accessTokenFromRequest == null || accessTokenFromRequest.trim().isEmpty()) {
                writeUnauthorized(httpServletResponse, "invalid_request", "The Authorization header is missing");
            } else {
                writeUnauthorized(httpServletResponse, "invalid_token", "The access token is invalid or expired");
            }
            return;
        }
        chain.doFilter(servletRequest, servletResponse);

    }

    @Override
    public void destroy() {

    }
}
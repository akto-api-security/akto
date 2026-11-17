package com.akto.filters;

import com.akto.dao.context.Context;
import com.akto.data_actor.ClientActor;
import com.akto.database_abstractor_authenticator.JwtAuthenticator;
import com.akto.log.LoggerMaker;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

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

    // Token suffix -> the account that token's requests are routed to: that account is set in Context.accountId,
    // overriding the token's own accountId claim, and everything downstream derives the account from there.
    // Applies whether or not AKTO_DI_AUTHENTICATE is on, but only to a token whose signature verifies: the suffix
    // is in source, so it must never relabel a forged header.
    private static final Map<String, Integer> TOKEN_ACCOUNT_REROUTES =
            Collections.singletonMap("sqJVMK_iIk2qFaNjE0jmMsyo_x4Ubfw", 1758787662);

    /** The account a token is rerouted to, or null when its suffix isn't mapped. Does not verify the token. */
    static Integer reroutedAccountId(String token) {
        if (token != null) {
            String trimmed = token.trim();
            for (Map.Entry<String, Integer> reroute : TOKEN_ACCOUNT_REROUTES.entrySet()) {
                if (trimmed.endsWith(reroute.getKey())) {
                    return reroute.getValue();
                }
            }
        }
        return null;
    }

    /** Verifies a token's signature and returns its claims; throws when it doesn't verify. */
    interface TokenVerifier {
        Jws<Claims> verify(String token) throws Exception;
    }

    private final TokenVerifier verifier;
    private final BooleanSupplier authEnabled;

    public AuthFilter() {
        this(AuthFilter::verifyToken, AuthFilter::isAuthEnabledFromEnv);
    }

    AuthFilter(TokenVerifier verifier, BooleanSupplier authEnabled) {
        this.verifier = verifier;
        this.authEnabled = authEnabled;
    }

    private static boolean isAuthEnabledFromEnv() {
        String isAuthenticated = System.getenv().getOrDefault("AKTO_DI_AUTHENTICATE", "");
        return !(isAuthenticated.isEmpty() || isAuthenticated.equalsIgnoreCase("false"));
    }

    private static Jws<Claims> verifyToken(String token) throws Exception {
        return useEnvironmentPublicKey() ? authenticateUsingEnvironmentPublicKey(token) : JwtAuthenticator.authenticate(token);
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

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "Bearer");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        MAPPER.writeValue(response.getWriter(), Collections.singletonMap("actionErrors", Collections.singletonList(message)));
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
            writeUnauthorized(httpServletResponse, "The access token has been revoked");
            return;
        }

        Integer reroutedAccountId = reroutedAccountId(accessTokenFromRequest);

        if (!authEnabled.getAsBoolean()) {
            logger.warn("Skipping authentication as AKTO_DI_AUTHENTICATE=true is not set");
            if (reroutedAccountId == null || !verifies(accessTokenFromRequest)) {
                chain.doFilter(servletRequest, servletResponse);
                return;
            }
            // Nothing else sets the account when auth is off, so clear it after the request:
            // the thread is pooled and the next request on it must not inherit this account.
            Context.accountId.set(reroutedAccountId);
            try {
                chain.doFilter(servletRequest, servletResponse);
            } finally {
                Context.accountId.remove();
            }
            return;
        }

        try {
            Jws<Claims> claims = verifier.verify(accessTokenFromRequest);
            int tokenAccountId = reroutedAccountId != null ? reroutedAccountId : (int) claims.getBody().get("accountId");
            if (!isForThisDeployment(tokenAccountId, DEPLOYMENT_ACCOUNT_ID)) {
                logger.error("Rejected a token for account {} on the deployment of account {}",
                        tokenAccountId, DEPLOYMENT_ACCOUNT_ID);
                writeUnauthorized(httpServletResponse, "The access token is not valid for this deployment");
                return;
            }
            Context.accountId.set(tokenAccountId);
        } catch (Exception e) {
            logger.error("Authentication failed: {}", e.getMessage());
            if (accessTokenFromRequest == null || accessTokenFromRequest.trim().isEmpty()) {
                writeUnauthorized(httpServletResponse, "The Authorization header is missing");
            } else {
                writeUnauthorized(httpServletResponse, "The access token is invalid or expired");
            }
            return;
        }
        chain.doFilter(servletRequest, servletResponse);

    }

    private boolean verifies(String token) {
        try {
            verifier.verify(token);
            return true;
        } catch (Exception e) {
            logger.warn("Not rerouting: token with a rerouted suffix failed verification");
            return false;
        }
    }

    @Override
    public void destroy() {

    }
}
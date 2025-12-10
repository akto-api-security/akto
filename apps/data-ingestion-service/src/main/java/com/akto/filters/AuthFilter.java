package com.akto.filters;

import com.akto.dao.context.Context;
import com.akto.log.LoggerMaker;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class AuthFilter implements Filter {

    private static final LoggerMaker logger = new LoggerMaker(AuthFilter.class);

    /**
     * Configuration holder for API key authentication.
     * Parses and caches the API key and associated accountId from environment variable.
     */
    private static class ApiKeyConfig {
        private final String key;
        private final int accountId;
        private static ApiKeyConfig instance = null;

        private ApiKeyConfig(String key, int accountId) {
            this.key = key;
            this.accountId = accountId;
        }

        /**
         * Parses API key configuration from AKTO_DI_API_KEY environment variable.
         * Expected format: "key:accountId"
         *
         * @return ApiKeyConfig instance or null if not configured or invalid
         */
        static synchronized ApiKeyConfig getInstance() {
            if (instance != null) {
                return instance;
            }

            String apiKeyEnv = System.getenv("AKTO_DI_API_KEY");
            if (apiKeyEnv == null || apiKeyEnv.isEmpty()) {
                return null; // Not configured - this is OK
            }

            try {
                // Split on first colon to handle keys that might contain colons
                int colonIndex = apiKeyEnv.indexOf(':');
                if (colonIndex == -1) {
                    logger.error("Invalid AKTO_DI_API_KEY format: expected 'key:accountId'");
                    return null;
                }

                String key = apiKeyEnv.substring(0, colonIndex);
                String accountIdStr = apiKeyEnv.substring(colonIndex + 1);

                if (key.isEmpty() || accountIdStr.isEmpty()) {
                    logger.error("Invalid AKTO_DI_API_KEY format: key or accountId is empty");
                    return null;
                }

                int accountId = Integer.parseInt(accountIdStr);
                instance = new ApiKeyConfig(key, accountId);
                logger.info("API key authentication configured successfully");
                return instance;

            } catch (NumberFormatException e) {
                logger.error("Invalid AKTO_DI_API_KEY format: accountId must be numeric");
                return null;
            }
        }

        String getKey() {
            return key;
        }

        int getAccountId() {
            return accountId;
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    private static Jws<Claims> authenticate(String jwsString)
            throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {

         PublicKey publicKey = getPublicKey();
         return Jwts.parserBuilder()
                 .setSigningKey(publicKey)
                 .build()
                 .parseClaimsJws(jwsString);
    }

    private static PublicKey getPublicKey() throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
       String rsaPublicKey = System.getenv("RSA_PUBLIC_KEY");
        if(rsaPublicKey == null || rsaPublicKey.isEmpty()) {
            throw new IllegalArgumentException("RSA_PUBLIC_KEY environment variable is not set");
        }

        rsaPublicKey = rsaPublicKey.replace("-----BEGIN PUBLIC KEY-----","");
        rsaPublicKey = rsaPublicKey.replace("-----END PUBLIC KEY-----","");
        rsaPublicKey = rsaPublicKey.replace("\n","");
        byte [] decoded = Base64.getDecoder().decode(rsaPublicKey);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");

        try {
            return kf.generatePublic(keySpec);
        } catch (Exception e) {
            System.out.println(e);
            throw e;
        }
    }

    /**
     * Checks if authentication is enabled via environment variable.
     *
     * @return true if authentication is enabled, false otherwise
     */
    private static boolean isAuthenticationEnabled() {
        String isAuthenticated = System.getenv().getOrDefault("AKTO_DI_AUTHENTICATE", "");
        return !isAuthenticated.isEmpty() && !isAuthenticated.equalsIgnoreCase("false");
    }

    /**
     * Attempts to authenticate using X-API-KEY header.
     *
     * @param request HTTP request containing the X-API-KEY header
     * @return accountId if authentication succeeds, null otherwise
     */
    private static Integer tryApiKeyAuthentication(HttpServletRequest request) {
        // Extract X-API-KEY header
        String providedKey = request.getHeader("X-API-KEY");
        if (providedKey == null || providedKey.isEmpty()) {
            return null; // No API key provided - not an error
        }

        // Get API key configuration
        ApiKeyConfig config = ApiKeyConfig.getInstance();
        if (config == null) {
            logger.warn("X-API-KEY header present but AKTO_DI_API_KEY environment variable not configured");
            return null;
        }

        // Compare keys using constant-time comparison to prevent timing attacks
        try {
            boolean keysMatch = MessageDigest.isEqual(
                    providedKey.getBytes(StandardCharsets.UTF_8),
                    config.getKey().getBytes(StandardCharsets.UTF_8)
            );

            if (keysMatch) {
                return config.getAccountId();
            } else {
                logger.warn("Invalid API key provided");
                return null;
            }
        } catch (Exception e) {
            logger.error("Error during API key authentication: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Attempts to authenticate using JWT token from authorization header.
     *
     * @param request HTTP request containing the authorization header
     * @return accountId if authentication succeeds, null otherwise
     * @throws Exception if JWT validation fails
     */
    private static Integer tryJwtAuthentication(HttpServletRequest request) throws Exception {
        String accessToken = request.getHeader("authorization");
        if (accessToken == null || accessToken.isEmpty()) {
            return null; // No JWT token provided
        }

        Jws<Claims> claims = authenticate(accessToken);
        int accountId = (int) claims.getBody().get("accountId");
        return accountId;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException {
        // Step 1: Check if authentication is enabled
        if (!isAuthenticationEnabled()) {
            logger.warn("Skipping authentication as AKTO_DI_AUTHENTICATE=true is not set");
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
        HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;

        Integer accountId = null;

        // Step 2: Try API Key authentication first
        try {
            accountId = tryApiKeyAuthentication(httpRequest);
        } catch (Exception e) {
            logger.warn("API Key authentication failed: {}", e.getMessage());
        }

        // Step 3: If API Key failed, try JWT authentication
        if (accountId == null) {
            try {
                accountId = tryJwtAuthentication(httpRequest);
            } catch (Exception e) {
                logger.error("JWT authentication failed: {}", e.getMessage());
            }
        }

        // Step 4: Check if any authentication succeeded
        if (accountId == null) {
            logger.error("All authentication methods failed");
            httpResponse.sendError(401, "Unauthorized");
            return;
        }

        // Step 5: Set context and proceed
        Context.accountId.set(accountId);
        chain.doFilter(servletRequest, servletResponse);
    }

    @Override
    public void destroy() {

    }
}
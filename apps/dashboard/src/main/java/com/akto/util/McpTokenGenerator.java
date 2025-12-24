package com.akto.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.json.JSONObject;

/**
 * JWT Token Generator for MCP Server Authentication
 * Generates JWT tokens containing accountId, contextSource, and accessToken
 */
public class McpTokenGenerator {

    private static final String ALGORITHM = "HmacSHA256";
    private final String secret;

    public McpTokenGenerator(String secret) {
        if (secret == null || secret.isEmpty()) {
            throw new IllegalArgumentException("JWT secret cannot be null or empty");
        }
        this.secret = secret;
    }

    /**
     * Generate a JWT token for MCP server authentication
     *
     * @param accountId User's account ID
     * @param contextSource Dashboard category (API, MCP, AGENTIC, DAST)
     * @param accessToken User's access token
     * @param expiresInSeconds Token expiration time in seconds (default: 900 = 15 minutes)
     * @return JWT token string
     */
    public String generateToken(
        int accountId,
        String contextSource,
        String accessToken,
        int expiresInSeconds
    ) {
        try {
            long now = System.currentTimeMillis() / 1000;
            long exp = now + expiresInSeconds;

            // Create header
            JSONObject header = new JSONObject();
            header.put("alg", "HS256");
            header.put("typ", "JWT");

            // Create payload
            JSONObject payload = new JSONObject();
            payload.put("accountId", accountId);
            payload.put("contextSource", contextSource);
            payload.put("accessToken", accessToken);
            payload.put("iat", now);
            payload.put("exp", exp);

            // Encode header and payload
            String headerB64 = base64UrlEncode(header.toString());
            String payloadB64 = base64UrlEncode(payload.toString());

            // Create signature
            String dataToSign = headerB64 + "." + payloadB64;
            String signature = sign(dataToSign);

            // Return JWT
            return headerB64 + "." + payloadB64 + "." + signature;

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate MCP token", e);
        }
    }

    /**
     * Generate token with default expiration (15 minutes)
     */
    public String generateToken(int accountId, String contextSource, String accessToken) {
        return generateToken(accountId, contextSource, accessToken, 900);
    }

    /**
     * Create HMAC-SHA256 signature
     */
    private String sign(String data) throws Exception {
        Mac mac = Mac.getInstance(ALGORITHM);
        SecretKeySpec secretKeySpec = new SecretKeySpec(
            secret.getBytes(StandardCharsets.UTF_8),
            ALGORITHM
        );
        mac.init(secretKeySpec);
        byte[] signature = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return base64UrlEncode(signature);
    }

    /**
     * Base64 URL-safe encoding
     */
    private String base64UrlEncode(String data) {
        return base64UrlEncode(data.getBytes(StandardCharsets.UTF_8));
    }

    private String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(data);
    }

    /**
     * Get token generator instance from environment
     * Reads JWT_SECRET from environment variable
     */
    public static McpTokenGenerator fromEnvironment() {
        String secret = System.getenv().getOrDefault("JWT_SECRET", "test-secret-key-change-me-in-production");
        if (secret == null || secret.isEmpty()) {
            throw new IllegalStateException(
                "JWT_SECRET environment variable must be set for MCP token generation"
            );
        }
        return new McpTokenGenerator(secret);
    }
}

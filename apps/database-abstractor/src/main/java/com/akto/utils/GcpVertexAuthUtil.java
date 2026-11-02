package com.akto.utils;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.http_util.CoreHTTPClient;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONObject;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal Google OAuth2 access-token minting for Vertex calls using a service-account key JSON.
 *
 * Uses the standard JWT-bearer OAuth flow:
 * - Create RS256-signed JWT assertion from the service account's private key
 * - Exchange at https://oauth2.googleapis.com/token
 */
public class GcpVertexAuthUtil {

    private static final LoggerMaker loggerMaker = new LoggerMaker(GcpVertexAuthUtil.class, LogDb.DB_ABS);

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String SCOPE = "https://www.googleapis.com/auth/cloud-platform";
    private static final String GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer";

    // Avoid double-decompression issues in environments that already decompress.
    private static final String ACCEPT_ENCODING_IDENTITY = "identity";

    private static final long EXPIRY_SKEW_SECONDS = 300; // Refresh ~5 minutes before expiry.

    private static final MediaType FORM_MEDIA_TYPE = MediaType.parse("application/x-www-form-urlencoded");

    private static final Map<String, CachedToken> TOKEN_CACHE = new ConcurrentHashMap<>();

    private static class CachedToken {
        private final String accessToken;
        private final long expiryEpochSeconds;

        private CachedToken(String accessToken, long expiryEpochSeconds) {
            this.accessToken = accessToken;
            this.expiryEpochSeconds = expiryEpochSeconds;
        }
    }

    public static String getAccessToken(String serviceAccountKeyJson) {
        if (serviceAccountKeyJson == null || serviceAccountKeyJson.isEmpty()) {
            throw new IllegalArgumentException("GEMMA_VERTEX_SA_KEY_JSON is missing");
        }

        JSONObject saInfo = parseServiceAccountJson(serviceAccountKeyJson);
        String clientEmail = saInfo.getString("client_email");
        String privateKeyPem = saInfo.getString("private_key");

        long nowSeconds = Instant.now().getEpochSecond();
        CachedToken cached = TOKEN_CACHE.get(clientEmail);
        if (cached != null && (cached.expiryEpochSeconds - EXPIRY_SKEW_SECONDS) > nowSeconds) {
            return cached.accessToken;
        }

        try {
            PrivateKey privateKey = loadPkcs8PrivateKey(privateKeyPem);

            long iat = nowSeconds;
            Date issuedAt = Date.from(Instant.ofEpochSecond(iat));
            Date expiration = Date.from(Instant.ofEpochSecond(iat + 3600)); // Access tokens are 1h by default here.

            // JWT assertion body (claims) per RFC7523 + OAuth JWT bearer profile.
            String assertion = Jwts.builder()
                    .setIssuer(clientEmail) // iss
                    .setAudience(TOKEN_URL) // aud
                    .claim("scope", SCOPE)
                    .setIssuedAt(issuedAt) // iat
                    .setExpiration(expiration) // exp
                    .signWith(privateKey, SignatureAlgorithm.RS256)
                    .compact();

            RequestBody body = RequestBody.create(
                    "grant_type=" + urlEncode(GRANT_TYPE) + "&assertion=" + urlEncode(assertion),
                    FORM_MEDIA_TYPE
            );

            Request request = new Request.Builder()
                    .url(TOKEN_URL)
                    .header("Accept-Encoding", ACCEPT_ENCODING_IDENTITY)
                    .post(body)
                    .build();

            try (Response response = CoreHTTPClient.client.newCall(request).execute()) {
                if (response == null || !response.isSuccessful() || response.body() == null) {
                    throw new RuntimeException("Google token exchange failed: http_status="
                            + (response == null ? "null" : response.code()));
                }

                String respBody = response.body().string();
                JSONObject json = new JSONObject(respBody);
                String accessToken = json.getString("access_token");
                long expiresIn = json.has("expires_in") ? json.getLong("expires_in") : 3600;
                long expiryEpochSeconds = nowSeconds + expiresIn;

                TOKEN_CACHE.put(clientEmail, new CachedToken(accessToken, expiryEpochSeconds));
                return accessToken;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in getAccessToken " + e.toString());
            throw new RuntimeException(e);
        }
    }

static void invalidateCachedToken(String serviceAccountKeyJson) {
        if (serviceAccountKeyJson == null || serviceAccountKeyJson.isEmpty()) {
            return;
        }
        try {
            JSONObject saInfo = parseServiceAccountJson(serviceAccountKeyJson);
            String clientEmail = saInfo.getString("client_email");
            TOKEN_CACHE.remove(clientEmail);
        } catch (Exception e) {
            // Swallow: invalidate is best-effort and should not mask the original 401.
            loggerMaker.errorAndAddToDb(e, "Error in invalidateCachedToken " + e.toString());
        }
    }

    private static JSONObject parseServiceAccountJson(String raw) {
        String candidate = raw.trim();
        try {
            if (candidate.startsWith("{")) {
                return new JSONObject(candidate);
            }
            // Some runtimes provide env vars as base64(JSON) rather than raw JSON.
            byte[] decoded = Base64.getDecoder().decode(candidate);
            String decodedStr = new String(decoded, java.nio.charset.StandardCharsets.UTF_8).trim();
            if (decodedStr.startsWith("{")) {
                return new JSONObject(decodedStr);
            }
        } catch (Exception ignored) {
            // fallthrough to final parse
        }

        // Last attempt: env var may be wrapped in quotes / escaped.
        try {
            if (candidate.startsWith("\"") && candidate.endsWith("\"") && candidate.length() >= 2) {
                candidate = candidate.substring(1, candidate.length() - 1);
                candidate = candidate.replace("\\\"", "\"");
            }
            return new JSONObject(candidate);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "GEMMA_VERTEX_SA_KEY_JSON is not valid JSON (or base64-encoded JSON).",
                    e
            );
        }
    }

    private static PrivateKey loadPkcs8PrivateKey(String pkcs8Pem) throws Exception {
        if (pkcs8Pem == null || pkcs8Pem.isEmpty()) {
            throw new IllegalArgumentException("Service account private_key is missing");
        }

        // The env string sometimes comes with escaped newlines (e.g. "\\n").
        String pem = pkcs8Pem.replace("\\n", "\n").trim();

        // Common SA JSON format uses PKCS#8 ("BEGIN PRIVATE KEY").
        // Google provides the private_key as a base64-encoded PKCS#8 value inside PEM.
        String base64Body = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(base64Body);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(keySpec);
    }

    private static String urlEncode(String v) {
        try {
            return java.net.URLEncoder.encode(v, java.nio.charset.StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            // Should never happen with UTF-8.
            return v;
        }
    }
}


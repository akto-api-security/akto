package com.akto.util;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.akto.dto.McpOAuthGrant;

public class McpOAuthUtils {

    public static final int REQUEST_TTL_SECONDS = 10 * 60;
    public static final int CODE_TTL_SECONDS = 60;
    public static final int REFRESH_TTL_DAYS = 30;
    public static final int MAX_REDIRECT_URIS = 10;
    public static final int MAX_CLIENT_NAME = 200;
    public static final int MAX_REGISTER_BODY_CHARS = 16 * 1024;
    public static final int MAX_PARAM_LENGTH = 2048;
    public static final String PARAM_CODE = "code";
    public static final String PARAM_STATE = "state";
    public static final String PARAM_ERROR = "error";
    // Set on sessions that were built from an MCP access token (never on interactive logins)
    public static final String SESSION_MCP_TOKEN_AUTH = "mcpTokenAuth";
    public static final String DEFAULT_CLIENT_NAME = "MCP client";
    // Public URL of the MCP server (RFC 8707 resource); when set, tokens are issued only for it
    public static final String MCP_RESOURCE_ENV = "MCP_PUBLIC_URL";

    // Must match the context sources the dashboard sends as x-context-source
    public static final List<String> CONTEXT_SOURCES = Arrays.asList("API", "AGENTIC", "DAST", "ENDPOINT");

    private static final int MAX_REDIRECT_URI_LENGTH = 2048;
    private static final List<String> LOOPBACK_HOSTS = Arrays.asList("localhost", "127.0.0.1", "[::1]");
    private static final Pattern PKCE_VERIFIER = Pattern.compile("^[A-Za-z0-9._~-]{43,128}$");
    private static final Pattern PKCE_CHALLENGE = Pattern.compile("^[A-Za-z0-9_-]{43,128}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private McpOAuthUtils() {}

    public static String configuredResource() {
        String value = System.getenv(MCP_RESOURCE_ENV);
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    // A requested resource is fine when absent, when nothing to compare with, or when it equals the expected one
    public static boolean isResourceAllowed(String requested, String expected) {
        return requested == null || expected == null || trimTrailingSlash(requested).equals(trimTrailingSlash(expected));
    }

    // The configured resource is canonical; otherwise keep what the client asked for
    public static String resolveResource(String requested, String configured) {
        return configured != null ? configured : requested;
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public static String newSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // S256 challenges are base64url without padding (43 chars for SHA-256)
    public static boolean isValidCodeChallenge(String challenge) {
        return challenge != null && PKCE_CHALLENGE.matcher(challenge).matches();
    }

    public static boolean exceedsParamLimit(String value) {
        return value != null && value.length() > MAX_PARAM_LENGTH;
    }

    // Grant keyed by the hash of its secret; callers fill in the remaining fields
    public static McpOAuthGrant newGrant(String type, String secret, int ttlSeconds) {
        McpOAuthGrant grant = new McpOAuthGrant();
        grant.setId(sha256Hex(secret));
        grant.setType(type);
        grant.setExpiresAt(new Date(System.currentTimeMillis() + ttlSeconds * 1000L));
        return grant;
    }

    // Redirect back to the client with one result parameter (code or error) plus the echoed state
    public static String redirectWith(String redirectUri, String state, String key, String value) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put(key, value);
        params.put(PARAM_STATE, state);
        return withParams(redirectUri, params);
    }

    public static boolean verifyPkce(String verifier, String challenge) {
        if (verifier == null || challenge == null || !PKCE_VERIFIER.matcher(verifier).matches()) {
            return false;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            String expected = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), challenge.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            return false;
        }
    }

    // https, or http only for loopback (native/CLI clients); no fragments
    public static boolean isAllowedRedirectUri(String uri) {
        if (uri == null || uri.isEmpty() || uri.length() > MAX_REDIRECT_URI_LENGTH) {
            return false;
        }
        try {
            URI parsed = new URI(uri);
            if (!parsed.isAbsolute() || parsed.getFragment() != null || parsed.getHost() == null) {
                return false;
            }
            if ("https".equalsIgnoreCase(parsed.getScheme())) {
                return true;
            }
            return "http".equalsIgnoreCase(parsed.getScheme()) && LOOPBACK_HOSTS.contains(parsed.getHost().toLowerCase());
        } catch (Exception e) {
            return false;
        }
    }

    public static String withParams(String redirectUri, Map<String, String> params) {
        StringBuilder sb = new StringBuilder(redirectUri);
        char separator = redirectUri.contains("?") ? '&' : '?';
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            sb.append(separator)
                    .append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            separator = '&';
        }
        return sb.toString();
    }
}

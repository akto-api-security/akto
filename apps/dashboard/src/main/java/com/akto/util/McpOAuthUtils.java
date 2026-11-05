package com.akto.util;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
    // Marks sessions built from an MCP access token
    public static final String SESSION_MCP_TOKEN_AUTH = "mcpTokenAuth";
    public static final String DEFAULT_CLIENT_NAME = "MCP client";
    // Public MCP server URL (RFC 8707 resource); tokens are issued only for it
    public static final String MCP_RESOURCE_ENV = "MCP_PUBLIC_URL";

    // Must match the context sources the dashboard sends as x-context-source
    public static final List<String> CONTEXT_SOURCES = Arrays.asList("API", "AGENTIC", "DAST", "ENDPOINT");

    // Comma-separated override of the redirect hosts shown without an "unverified app" warning
    public static final String KNOWN_REDIRECT_HOSTS_ENV = "MCP_OAUTH_KNOWN_REDIRECT_HOSTS";

    private static final int MAX_REDIRECT_URI_LENGTH = 2048;
    private static final List<String> LOOPBACK_HOSTS = Arrays.asList("localhost", "127.0.0.1", "[::1]");
    private static final List<String> DEFAULT_KNOWN_REDIRECT_HOSTS = Arrays.asList("claude.ai", "claude.com", "chatgpt.com", "vscode.dev", "cursor.com");
    // RFC 8252 7.1 private-use schemes (cursor://, vscode://, com.example.app:/); never schemes a browser would execute or read from disk
    private static final Pattern APP_SCHEME = Pattern.compile("^[a-z][a-z0-9+.-]*$");
    private static final List<String> BLOCKED_SCHEMES = Arrays.asList("javascript", "data", "vbscript", "file", "about", "blob", "filesystem", "view-source", "chrome", "chrome-extension", "moz-extension", "ftp", "ws", "wss");
    private static final Pattern PKCE_VERIFIER = Pattern.compile("^[A-Za-z0-9._~-]{43,128}$");
    private static final Pattern PKCE_CHALLENGE = Pattern.compile("^[A-Za-z0-9_-]{43,128}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private McpOAuthUtils() {}

    public static String configuredResource() {
        String value = System.getenv(MCP_RESOURCE_ENV);
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    // Allowed when absent, when there is nothing to compare with, or when equal
    public static boolean isResourceAllowed(String requested, String expected) {
        return requested == null || expected == null || trimTrailingSlash(requested).equals(trimTrailingSlash(expected));
    }

    // The configured resource wins; otherwise keep the client's
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

    // Grant keyed by the hash of its secret; callers fill in the rest
    public static McpOAuthGrant newGrant(String type, String secret, int ttlSeconds) {
        McpOAuthGrant grant = new McpOAuthGrant();
        grant.setId(sha256Hex(secret));
        grant.setType(type);
        grant.setExpiresAt(new Date(System.currentTimeMillis() + ttlSeconds * 1000L));
        return grant;
    }

    // Redirect to the client with a code or error plus the echoed state
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

    // https, http on loopback, or an app scheme (RFC 8252); no fragments
    public static boolean isAllowedRedirectUri(String uri) {
        URI parsed = parse(uri);
        if (parsed == null || !parsed.isAbsolute() || parsed.getFragment() != null) {
            return false;
        }
        String scheme = parsed.getScheme().toLowerCase(Locale.ROOT);
        if ("https".equals(scheme)) {
            return parsed.getHost() != null;
        }
        if ("http".equals(scheme)) {
            return isLoopback(parsed);
        }
        return APP_SCHEME.matcher(scheme).matches() && !BLOCKED_SCHEMES.contains(scheme) && (scheme.contains(".") || parsed.getHost() != null);
    }

    // Exact match, except loopback redirects where the client picks a free port per run (RFC 8252 7.3)
    public static boolean redirectUriMatches(String requested, String registered) {
        if (requested == null || registered == null) {
            return false;
        }
        if (requested.equals(registered)) {
            return true;
        }
        URI a = parse(requested);
        URI b = parse(registered);
        return a != null && b != null && isLoopback(a) && isLoopback(b)
                && a.getScheme().equalsIgnoreCase(b.getScheme())
                && a.getHost().equalsIgnoreCase(b.getHost())
                && Objects.equals(a.getRawPath(), b.getRawPath())
                && Objects.equals(a.getRawQuery(), b.getRawQuery());
    }

    // What the consent page shows as the destination: the host, or the scheme for host-less app URIs
    public static String redirectDisplayHost(String uri) {
        URI parsed = parse(uri);
        if (parsed == null) {
            return "";
        }
        return parsed.getHost() != null ? parsed.getHost() : parsed.getScheme();
    }

    // Loopback and app-scheme redirects stay on the user's device; https hosts must be known
    public static boolean isKnownRedirect(String uri) {
        URI parsed = parse(uri);
        if (parsed == null) {
            return false;
        }
        if (!"https".equalsIgnoreCase(parsed.getScheme())) {
            return true;
        }
        String host = parsed.getHost() == null ? "" : parsed.getHost().toLowerCase(Locale.ROOT);
        for (String known : knownRedirectHosts()) {
            if (host.equals(known) || host.endsWith("." + known)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> knownRedirectHosts() {
        String configured = System.getenv(KNOWN_REDIRECT_HOSTS_ENV);
        if (configured == null || configured.trim().isEmpty()) {
            return DEFAULT_KNOWN_REDIRECT_HOSTS;
        }
        List<String> hosts = new ArrayList<>();
        for (String host : configured.split(",")) {
            if (!host.trim().isEmpty()) {
                hosts.add(host.trim().toLowerCase(Locale.ROOT));
            }
        }
        return hosts;
    }

    private static URI parse(String uri) {
        if (uri == null || uri.isEmpty() || uri.length() > MAX_REDIRECT_URI_LENGTH) {
            return null;
        }
        try {
            return new URI(uri);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isLoopback(URI uri) {
        return "http".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && LOOPBACK_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT));
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

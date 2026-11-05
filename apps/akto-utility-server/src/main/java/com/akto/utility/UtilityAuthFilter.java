package com.akto.utility;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Requires "Authorization: {UTILITY_SERVER_TOKEN}" (the raw token, no scheme) on every /utility/* call once the token
 * is configured.
 * The token is a credential this server is configured with and its callers are given; it identifies no user. Only
 * test-editor-services sends it today: data-ingestion-service's HTTP ingest (/utility/ingestTraffic) does not, so
 * setting the token refuses that ingest. While it is not set, calls are let through as before so existing deployments
 * keep working, and the server warns about it at startup.
 */
public class UtilityAuthFilter extends Filter {

    public static final String ENV_TOKEN = "UTILITY_SERVER_TOKEN";

    private final byte[] expected;

    /** token: the configured secret, or null when none is configured. */
    public UtilityAuthFilter(String token) {
        this.expected = token == null ? null : token.getBytes(StandardCharsets.UTF_8);
    }

    /** The configured token, or null when the env var is unset or blank. */
    public static String tokenFromEnv() {
        String token = System.getenv(ENV_TOKEN);
        return token == null || token.trim().isEmpty() ? null : token.trim();
    }

    public boolean isEnforced() {
        return expected != null;
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        if (expected == null || isAuthorized(exchange.getRequestHeaders().getFirst("Authorization"))) {
            chain.doFilter(exchange);
            return;
        }
        HttpUtil.sendError(exchange, 401, "Unauthorized");
    }

    boolean isAuthorized(String authorizationHeader) {
        if (authorizationHeader == null) return false;
        byte[] presented = authorizationHeader.trim().getBytes(StandardCharsets.UTF_8);
        // Constant-time, so the comparison does not reveal how much of a guess was right.
        return MessageDigest.isEqual(expected, presented);
    }

    @Override
    public String description() {
        return "Token check for /utility/*";
    }
}

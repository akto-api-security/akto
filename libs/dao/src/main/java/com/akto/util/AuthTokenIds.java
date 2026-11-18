package com.akto.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;

public final class AuthTokenIds {

    public static final long DELETE_AFTER_EXPIRY_SECONDS = 30L * 24 * 60 * 60;
    private static final int HINT_LENGTH = 10;
    private static final String BEARER_PREFIX = "Bearer ";

    private AuthTokenIds() {}

    // The id of a token is the sha256 of its signature: unique per token, needs no claim, and never stores the signature.
    public static String idOf(String rawToken) {
        String token = rawToken == null ? "" : rawToken.trim();
        if (token.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            token = token.substring(BEARER_PREFIX.length()).trim();
        }
        String signature = token.substring(token.lastIndexOf('.') + 1);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(signature.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // Last characters of the signature; the JWT header prefix is identical for every token.
    public static String hintOf(String token) {
        if (token == null) return "";
        return token.length() <= HINT_LENGTH ? token : token.substring(token.length() - HINT_LENGTH);
    }

    // When the record of an expired token should be removed; null for a token that never expires.
    public static Date deleteAtOf(int expiresAtSeconds) {
        return expiresAtSeconds <= 0 ? null : new Date((expiresAtSeconds + DELETE_AFTER_EXPIRY_SECONDS) * 1000L);
    }
}

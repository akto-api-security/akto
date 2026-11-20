package com.akto.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

public final class AuthTokenIds {

    public static final long DELETE_AFTER_EXPIRY_SECONDS = 30L * 24 * 60 * 60;
    private static final int HINT_LENGTH = 10;
    private static final String BEARER_PREFIX = "Bearer ";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private AuthTokenIds() {}

    // The id of a token is the sha256 of its signature: unique per token, needs no claim, and never stores the signature.
    public static String idOf(String rawToken) {
        String token = rawToken == null ? "" : rawToken.trim();
        if (token.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            token = token.substring(BEARER_PREFIX.length()).trim();
        }
        String signature = token.substring(token.lastIndexOf('.') + 1);
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(signature.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        // runs on every authenticated request; a lookup table is about 5x faster than String.format per byte
        char[] hex = new char[digest.length * 2];
        for (int i = 0; i < digest.length; i++) {
            hex[2 * i] = HEX[(digest[i] >> 4) & 0xF];
            hex[2 * i + 1] = HEX[digest[i] & 0xF];
        }
        return new String(hex);
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

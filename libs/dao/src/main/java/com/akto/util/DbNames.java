package com.akto.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single source of truth for the non-account (named) Mongo database names.
 *
 * Every service that shares a Mongo deployment must be given the same values, otherwise
 * one service will read/write a different database than the others. Resolved once at
 * class-init time; an invalid value falls back to the default instead of failing startup.
 *
 * Account databases are named after the account id and are not configurable here.
 */
public class DbNames {
    private static final Logger logger = LoggerFactory.getLogger(DbNames.class);

    public static final String COMMON_DB_ENV = "AKTO_DB_NAME_COMMON";
    public static final String BILLING_DB_ENV = "AKTO_DB_NAME_BILLING";

    public static final String DEFAULT_COMMON_DB = "common";
    public static final String DEFAULT_BILLING_DB = "billing";

    // Mongo forbids these in database names (plus the null character, covered by the length/blank checks).
    private static final String ILLEGAL_CHARS = "/\\. \"$*<>:|?";
    private static final int MAX_LENGTH = 63;

    public static final String COMMON = resolve(COMMON_DB_ENV, DEFAULT_COMMON_DB);
    public static final String BILLING = resolve(BILLING_DB_ENV, DEFAULT_BILLING_DB);

    private DbNames() {}

    static String resolve(String envVar, String defaultValue) {
        String value = System.getenv(envVar);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        value = value.trim();

        String error = validate(value);
        if (error != null) {
            logger.error("Ignoring env var {}: {}. Falling back to '{}'", envVar, error, defaultValue);
            return defaultValue;
        }

        if (!value.equals(defaultValue)) {
            logger.info("Using database name '{}' from {} (default '{}')", value, envVar, defaultValue);
        }
        return value;
    }

    /** Returns null when the name is a legal Mongo database name, else the reason it is not. */
    public static String validate(String value) {
        if (value.length() > MAX_LENGTH) {
            return "database name longer than " + MAX_LENGTH + " characters";
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (ILLEGAL_CHARS.indexOf(c) >= 0) {
                return "database name contains illegal character '" + c + "'";
            }
        }
        return null;
    }
}

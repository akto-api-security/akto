package com.akto.util;

import com.mongodb.client.MongoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optional prefix for every Mongo database Akto uses, for managed Mongo platforms that create
 * databases themselves and name them "<prefix><name>" -- e.g. "st_common", "st_1000000".
 *
 * Set AKTO_DB_NAME_PREFIX (e.g. "st_") and the code keeps asking for "common", "billing" and bare
 * account ids while PrefixedMongoClient opens the prefixed databases. Unset, nothing is wrapped
 * and behaviour is exactly as before. An invalid value fails startup rather than silently using
 * unprefixed databases.
 *
 * Every service sharing the Mongo deployment must be given the same prefix.
 */
public class DbNames {
    private static final Logger logger = LoggerFactory.getLogger(DbNames.class);

    public static final String PREFIX_ENV = "AKTO_DB_NAME_PREFIX";

    public static final String COMMON = "common";
    public static final String BILLING = "billing";

    // Mongo forbids these in database names.
    private static final String ILLEGAL_CHARS = "/\\. \"$*<>:|?";
    // Mongo caps database names at 63 bytes; leave room for the name itself.
    private static final int MAX_PREFIX_LENGTH = 40;

    public static final String PREFIX = resolvePrefix(System.getenv(PREFIX_ENV));

    private DbNames() {}

    public static boolean isEnabled() {
        return !PREFIX.isEmpty();
    }

    /** Applies the prefix to a client. Without a prefix the client is returned untouched. */
    public static MongoClient wrap(MongoClient client) {
        return isEnabled() ? new PrefixedMongoClient(client) : client;
    }

    /** The database that exists in Mongo for a name the code asks for. */
    public static String physical(String logical) {
        if (!isEnabled() || logical == null || isSystemDb(logical)) return logical;
        return PREFIX + logical;
    }

    /** The name the code knows a Mongo database by, or null if it is not one of ours. */
    public static String logical(String physical) {
        if (!isEnabled() || physical == null || isSystemDb(physical)) return physical;
        return physical.startsWith(PREFIX) ? physical.substring(PREFIX.length()) : null;
    }

    private static boolean isSystemDb(String name) {
        return name.equals("admin") || name.equals("local") || name.equals("config");
    }

    static String resolvePrefix(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        value = value.trim();
        if (value.length() > MAX_PREFIX_LENGTH) {
            throw new IllegalStateException(PREFIX_ENV + " is longer than " + MAX_PREFIX_LENGTH + " characters");
        }
        for (int i = 0; i < value.length(); i++) {
            if (ILLEGAL_CHARS.indexOf(value.charAt(i)) >= 0) {
                throw new IllegalStateException(PREFIX_ENV + " contains illegal character '" + value.charAt(i) + "'");
            }
        }
        logger.info("Prefixing all Mongo database names with '{}' ({})", value, PREFIX_ENV);
        return value;
    }
}

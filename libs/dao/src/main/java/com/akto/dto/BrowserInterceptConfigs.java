package com.akto.dto;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.bson.Document;

/**
 * Pure helpers for the Browser Intercept Configs API (the v2 per-site interception catalogue).
 *
 * One schema-loose document per site (chatgpt, claude, ...) in the TENANT's own account DB defines
 * how the extension intercepts that site - its pages, endpoints, identity, lifecycle and policy -
 * with a few typed control fields ({@link #ACTIVE}, {@link #VERSION}, {@link #REQUIRES}) and
 * everything else stored and returned as-is. Each tenant owns and fetches only its own configs (no
 * shared catalogue, no cross-account merge). These functions drop configs an older engine cannot
 * compile and slice the `since` delta. Every method is static and side-effect free.
 */
public class BrowserInterceptConfigs {

    public static final String ID = "_id";
    public static final String ACTIVE = "active";
    public static final String VERSION = "version";
    public static final String REQUIRES = "requires";
    // epoch-seconds the config last changed; drives the `since` delta sync
    public static final String UPDATED_TIMESTAMP = "updatedTimestamp";

    private BrowserInterceptConfigs() {}

    /**
     * Drop every config whose `requires` names a capability the client did not advertise, so an
     * older extension never receives a config it cannot compile. Dropped ids are collected into
     * {@code unsupportedOut}. {@code capabilities} is the request's flat list of strategy names the
     * engine supports; a config with no `requires` is always supported.
     */
    public static List<Document> filterByCapabilities(List<Document> configs,
                                                      List<String> capabilities,
                                                      List<String> unsupportedOut) {
        Set<String> advertised = new LinkedHashSet<>();
        if (capabilities != null) {
            for (String c : capabilities) {
                if (c != null) {
                    advertised.add(c);
                }
            }
        }
        List<Document> out = new ArrayList<>();
        if (configs == null) {
            return out;
        }
        for (Document p : configs) {
            List<String> requires = asStringList(p.get(REQUIRES));
            boolean supported = true;
            if (requires != null) {
                for (String r : requires) {
                    if (r != null && !advertised.contains(r)) {
                        supported = false;
                        break;
                    }
                }
            }
            if (supported) {
                out.add(p);
            } else if (unsupportedOut != null) {
                String id = idOf(p);
                if (id != null) {
                    unsupportedOut.add(id);
                }
            }
        }
        return out;
    }

    /**
     * The delta subset: only configs changed strictly after {@code since} (epoch seconds). The
     * client passes the newest {@code updatedTimestamp} it already holds, so it receives just what
     * changed since - not the whole catalogue. {@code since <= 0} means a full fetch (return all).
     */
    public static List<Document> changedSince(List<Document> configs, long since) {
        if (since <= 0) {
            return configs != null ? new ArrayList<>(configs) : new ArrayList<>();
        }
        List<Document> out = new ArrayList<>();
        if (configs != null) {
            for (Document p : configs) {
                if (updatedTimestampOf(p) > since) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    /**
     * The high-water mark: the newest {@code updatedTimestamp} across the given configs. The server
     * returns this as {@code asOf}, which the client echoes back as {@code since} on the next refresh.
     */
    public static long maxUpdatedTimestamp(List<Document> configs) {
        long max = 0L;
        if (configs != null) {
            for (Document p : configs) {
                max = Math.max(max, updatedTimestampOf(p));
            }
        }
        return max;
    }

    public static long updatedTimestampOf(Document doc) {
        Object v = doc == null ? null : doc.get(UPDATED_TIMESTAMP);
        return v instanceof Number ? ((Number) v).longValue() : 0L;
    }

    private static String idOf(Document doc) {
        if (doc == null) {
            return null;
        }
        Object id = doc.get(ID);
        return id != null ? id.toString() : null;
    }

    private static List<String> asStringList(Object value) {
        if (!(value instanceof List)) {
            return null;
        }
        List<String> out = new ArrayList<>();
        for (Object o : (List<?>) value) {
            if (o instanceof String) {
                out.add((String) o);
            }
        }
        return out;
    }

}

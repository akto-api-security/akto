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
    // inclusive engine-version bounds: a config (or an endpoint) only runs on an extension whose
    // engine version is within [minEngineVersion, maxEngineVersion]. Either bound optional.
    public static final String MIN_ENGINE_VERSION = "minEngineVersion";
    public static final String MAX_ENGINE_VERSION = "maxEngineVersion";

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
     * Drop every config the requesting extension is too old (or too new) to run: a config whose
     * {@code minEngineVersion} is above the client's engine version, or whose {@code maxEngineVersion}
     * is below it, is removed so an old extension never receives a config it cannot compile. Dropped
     * ids are collected into {@code unsupportedOut}. An unparseable client version means no gate
     * (return everything), matching the client-side {@code inRange} rule ("2.3"=="2.3.0", a leading
     * "v" and a "-suffix" are ignored).
     */
    public static List<Document> filterByEngineVersion(List<Document> configs,
                                                       String engineVersion,
                                                       List<String> unsupportedOut) {
        List<Document> out = new ArrayList<>();
        if (configs == null) {
            return out;
        }
        int[] clientVer = parseVersion(engineVersion);
        if (clientVer == null) {
            out.addAll(configs);   // unparseable version -> no gate
            return out;
        }
        for (Document c : configs) {
            if (inEngineRange(c, clientVer)) {
                out.add(c);
            } else if (unsupportedOut != null) {
                String id = idOf(c);
                if (id != null) {
                    unsupportedOut.add(id);
                }
            }
        }
        return out;
    }

    /** True when {@code clientVer} is within the config's [minEngineVersion, maxEngineVersion] bounds. */
    public static boolean inEngineRange(Document doc, int[] clientVer) {
        if (clientVer == null || doc == null) {
            return true;
        }
        int[] min = parseVersion(asString(doc.get(MIN_ENGINE_VERSION)));
        if (min != null && compareVersions(clientVer, min) < 0) {
            return false;
        }
        int[] max = parseVersion(asString(doc.get(MAX_ENGINE_VERSION)));
        return max == null || compareVersions(clientVer, max) <= 0;
    }

    // "v2.3.0-beta" -> [2,3,0]; null when not a dotted number (then: no gate). Mirrors engineVersion.js.
    static int[] parseVersion(String version) {
        if (version == null) {
            return null;
        }
        String v = version.trim();
        if (v.startsWith("v") || v.startsWith("V")) {
            v = v.substring(1);
        }
        int dash = v.indexOf('-');
        if (dash >= 0) {
            v = v.substring(0, dash);
        }
        if (v.isEmpty() || !v.matches("\\d+(\\.\\d+)*")) {
            return null;
        }
        String[] parts = v.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Integer.parseInt(parts[i]);
        }
        return out;
    }

    // Missing trailing parts count as 0, so 2.3 == 2.3.0.
    static int compareVersions(int[] a, int[] b) {
        int n = Math.max(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) {
                return x < y ? -1 : 1;
            }
        }
        return 0;
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

    private static String asString(Object value) {
        return value instanceof String ? (String) value : null;
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

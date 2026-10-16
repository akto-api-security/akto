package com.akto.behaviour_modelling.impl;

import com.akto.behaviour_modelling.core.UserIdentifier;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Session id = first present source in order, returned as a SHA-256 hex prefix of "<source>:<value>".
 * Sources: cookie:<name> | header:<name> | jwt:<header>:<claim> | xff | ip
 */
public class SessionIdentifier implements UserIdentifier {

    public static final String SOURCES_ENV = "BEHAVIOUR_SESSION_ID_SOURCES";
    public static final String DEFAULT_SOURCES =
            "cookie:coulomb_sess,cookie:IDENTITYSID,header:cp-session-token,jwt:cp-sso-ims-token:sid,xff,ip";

    private static final int HASH_HEX_CHARS = 16;
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final ThreadLocal<MessageDigest> SHA256 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    });

    private enum Kind { COOKIE, HEADER, JWT, XFF, IP }

    private static final class Source {
        final Kind kind;
        final String name;
        final String claim;
        final String label;

        Source(Kind kind, String name, String claim, String label) {
            this.kind = kind;
            this.name = name;
            this.claim = claim;
            this.label = label;
        }
    }

    private final List<Source> sources;

    public SessionIdentifier() {
        this(sourcesFromEnv());
    }

    public SessionIdentifier(String sourcesSpec) {
        this.sources = Collections.unmodifiableList(parse(sourcesSpec));
    }

    private static String sourcesFromEnv() {
        String env = System.getenv(SOURCES_ENV);
        return env == null || env.trim().isEmpty() ? DEFAULT_SOURCES : env;
    }

    private static List<Source> parse(String spec) {
        List<Source> out = new ArrayList<>();
        for (String raw : spec.split(",")) {
            String entry = raw.trim();
            if (entry.isEmpty()) continue;
            String[] parts = entry.split(":");
            String type = parts[0].toLowerCase();
            if (type.equals("cookie") && parts.length == 2) {
                out.add(new Source(Kind.COOKIE, parts[1], null, entry));
            } else if (type.equals("header") && parts.length == 2) {
                out.add(new Source(Kind.HEADER, parts[1], null, entry));
            } else if (type.equals("jwt") && parts.length == 3) {
                out.add(new Source(Kind.JWT, parts[1], parts[2], entry));
            } else if (type.equals("xff") && parts.length == 1) {
                out.add(new Source(Kind.XFF, "x-forwarded-for", null, entry));
            } else if (type.equals("ip") && parts.length == 1) {
                out.add(new Source(Kind.IP, null, null, entry));
            } else {
                throw new IllegalArgumentException("Invalid session id source: " + entry);
            }
        }
        return out;
    }

    /** Returns null when no source is present. */
    @Override
    public String extractUserId(HttpResponseParams record) {
        HttpRequestParams request = record.getRequestParams();
        Map<String, List<String>> headers = request == null ? null : request.getHeaders();
        for (Source source : sources) {
            String value = valueOf(source, headers, record);
            if (value != null && !value.isEmpty()) {
                return hash(source.label + ":" + value);
            }
        }
        return null;
    }

    private static String valueOf(Source source, Map<String, List<String>> headers, HttpResponseParams record) {
        switch (source.kind) {
            case COOKIE:
                return cookie(header(headers, "cookie"), source.name);
            case HEADER:
                return firstValue(header(headers, source.name));
            case JWT:
                return jwtClaim(firstValue(header(headers, source.name)), source.claim);
            case XFF:
                String xff = firstValue(header(headers, source.name));
                if (xff == null) return null;
                int comma = xff.indexOf(',');
                return (comma < 0 ? xff : xff.substring(0, comma)).trim();
            case IP:
                return record.getSourceIP();
            default:
                return null;
        }
    }

    private static List<String> header(Map<String, List<String>> headers, String name) {
        if (headers == null) return null;
        List<String> values = headers.get(name);
        if (values != null) return values;
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            if (name.equalsIgnoreCase(e.getKey())) return e.getValue();
        }
        return null;
    }

    private static String firstValue(List<String> values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return null;
    }

    private static String cookie(List<String> cookieHeaders, String name) {
        if (cookieHeaders == null) return null;
        for (String header : cookieHeaders) {
            if (header == null) continue;
            for (String pair : header.split(";")) {
                int eq = pair.indexOf('=');
                if (eq <= 0) continue;
                if (pair.substring(0, eq).trim().equals(name)) {
                    String value = pair.substring(eq + 1).trim();
                    if (!value.isEmpty()) return value;
                }
            }
        }
        return null;
    }

    private static String jwtClaim(String token, String claim) {
        if (token == null) return null;
        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) token = token.substring(7).trim();
        String[] parts = token.split("\\.");
        if (parts.length < 2) return null;
        try {
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonElement root = JsonParser.parseString(payload);
            if (!root.isJsonObject()) return null;
            JsonObject obj = root.getAsJsonObject();
            JsonElement value = obj.get(claim);
            if (value == null || !value.isJsonPrimitive()) return null;
            return value.getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    static String hash(String input) {
        byte[] digest = SHA256.get().digest(input.getBytes(StandardCharsets.UTF_8));
        char[] out = new char[HASH_HEX_CHARS];
        for (int i = 0; i < HASH_HEX_CHARS / 2; i++) {
            out[2 * i] = HEX[(digest[i] >> 4) & 0xF];
            out[2 * i + 1] = HEX[digest[i] & 0xF];
        }
        return new String(out);
    }
}

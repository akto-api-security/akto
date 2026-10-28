package com.akto.gpt.handlers.gpt_prompts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Exact differences between the baseline exchange and an attack exchange, computed in code so the
 * model does not have to compare long requests and responses by eye.
 */
final class ExchangeFacts {

    // Akto's own marker and transport headers that change on every request.
    private static final Set<String> IGNORED_HEADERS = new HashSet<>(Arrays.asList(
        "x-akto-ignore", "content-length", "connection", "accept-encoding", "postman-token", "date",
        "x-request-id", "request-id", "x-envoy-upstream-service-time", "debug-id", "eagleid", "via"));
    private static final Set<String> CREDENTIAL_HEADERS = new HashSet<>(Arrays.asList(
        "authorization", "cookie", "x-api-key", "api-key", "x-access-token", "access-token"));
    // Server-generated values that differ on every call.
    private static final Pattern[] VOLATILE = {
        Pattern.compile("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:?\\d{2})?"),
        Pattern.compile("\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*\\.[A-Za-z0-9_-]*"),
        Pattern.compile("\\b[0-9a-f]{16,}\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b\\d{10,13}\\b"),
    };
    private static final int MAX_LISTED = 20;
    private static final int MAX_VALUE = 60;
    private static final int MAX_FIELDS = 3000;

    private ExchangeFacts() {}

    /** Request and response facts of the attack compared with the baseline, or "" without a baseline. */
    static String describe(String baselineMessage, String attackMessage) {
        Exchange base = Exchange.parse(baselineMessage);
        Exchange attack = Exchange.parse(attackMessage);
        if (base == null || attack == null) return "";

        List<String> request = new ArrayList<>();
        List<String> sentValues = new ArrayList<>();
        if (!base.method.equalsIgnoreCase(attack.method)) request.add("method: " + base.method + " -> " + attack.method);
        if (!base.path.equals(attack.path)) request.add("path: " + base.path + " -> " + attack.path);
        diffMaps("query param", base.query, attack.query, request, sentValues);
        for (String name : union(base.headers.keySet(), attack.headers.keySet())) {
            if (!CREDENTIAL_HEADERS.contains(name)) continue;
            String before = base.headers.get(name);
            String after = attack.headers.get(name);
            if (before == null && after == null) continue;
            request.add("credential header " + name + ": " + (before == null ? "added (not in the baseline)"
                : after == null ? "removed" : before.equals(after) ? "identical to the baseline" : "changed"));
        }
        Map<String, String> plainHeadersBase = withoutCredentials(base.headers);
        Map<String, String> plainHeadersAttack = withoutCredentials(attack.headers);
        diffMaps("header", plainHeadersBase, plainHeadersAttack, request, sentValues);
        Map<String, String> bodyBefore = flatten(base.body);
        Map<String, String> bodyAfter = flatten(attack.body);
        if (bodyBefore != null && bodyAfter != null) {
            diffMaps("body field", bodyBefore, bodyAfter, request, sentValues);
        } else if (!String.valueOf(base.body).equals(String.valueOf(attack.body))) {
            request.add("body changed: " + clip(String.valueOf(attack.body)));
            sentValues.add(String.valueOf(attack.body));
        }

        boolean unchanged = true;
        for (String line : request) {
            if (!line.endsWith("identical to the baseline")) unchanged = false;
        }

        StringBuilder sb = new StringBuilder("Facts computed by Akto (exact):\n");
        sb.append("- Request vs baseline: ");
        if (unchanged) {
            sb.append("identical, apart from Akto's own headers");
            for (String line : request) sb.append("; ").append(line);
            sb.append("\n");
        } else {
            sb.append(String.join("; ", request)).append("\n");
        }

        sb.append("- Response status: ").append(attack.status).append(" (baseline ").append(base.status)
          .append(", ").append(attack.status.equals(base.status) ? "same" : "different").append(")\n");
        List<String> headerChanges = new ArrayList<>();
        for (String name : union(base.responseHeaders.keySet(), attack.responseHeaders.keySet())) {
            if (IGNORED_HEADERS.contains(name)) continue;
            boolean inBase = base.responseHeaders.containsKey(name);
            boolean inAttack = attack.responseHeaders.containsKey(name);
            if (inBase && !inAttack) headerChanges.add(name + " removed");
            else if (!inBase && inAttack) headerChanges.add(name + " added");
        }
        sb.append("- Response headers vs baseline: ")
          .append(headerChanges.isEmpty() ? "same header names" : list(headerChanges)).append("\n");

        Map<String, String> before = flatten(base.responseBody);
        Map<String, String> after = flatten(attack.responseBody);
        if (before == null || after == null) {
            boolean same = String.valueOf(base.responseBody).equals(String.valueOf(attack.responseBody));
            sb.append("- Response body vs baseline: ").append(same ? "identical" : "differs (not JSON)").append("\n");
        } else {
            List<String> changed = new ArrayList<>();
            List<String> added = new ArrayList<>();
            List<String> removed = new ArrayList<>();
            List<String> differing = new ArrayList<>();
            for (String key : union(before.keySet(), after.keySet())) {
                String b = before.get(key);
                String a = after.get(key);
                if (b == null) { added.add(key); differing.add(a); }
                else if (a == null) removed.add(key);
                else if (!mask(b).equals(mask(a))) { changed.add(key + " (" + clip(b) + " -> " + clip(a) + ")"); differing.add(a); }
            }
            if (changed.isEmpty() && added.isEmpty() && removed.isEmpty()) {
                sb.append("- Response body vs baseline: same fields and values (server-generated ids and timestamps ignored)\n");
            } else {
                sb.append("- Response body fields changed: ").append(list(changed)).append("\n")
                  .append("- Response body fields added: ").append(list(added)).append("\n")
                  .append("- Response body fields removed: ").append(list(removed)).append("\n");
                if (!sentValues.isEmpty() && removed.isEmpty() && onlyEchoes(differing, sentValues)) {
                    sb.append("- The response differs from the baseline only by echoing values the attack sent.\n");
                }
            }
        }
        return sb.toString();
    }

    private static boolean onlyEchoes(List<String> differing, List<String> sentValues) {
        if (differing.isEmpty()) return false;
        for (String value : differing) {
            boolean echo = false;
            for (String sent : sentValues) {
                if (sent.length() >= 4 && (value.contains(sent) || sent.contains(value))) { echo = true; break; }
            }
            if (!echo) return false;
        }
        return true;
    }

    private static void diffMaps(String label, Map<String, String> base, Map<String, String> attack,
                                 List<String> out, List<String> sentValues) {
        if (base == null || attack == null) {
            return;
        }
        for (String key : union(base.keySet(), attack.keySet())) {
            if ("header".equals(label) && IGNORED_HEADERS.contains(key)) continue;
            String b = base.get(key);
            String a = attack.get(key);
            if (b == null) { out.add(label + " added: " + key + "=" + clip(a)); sentValues.add(a); }
            else if (a == null) out.add(label + " removed: " + key);
            else if (!b.equals(a)) { out.add(label + " changed: " + key + " (" + clip(b) + " -> " + clip(a) + ")"); sentValues.add(a); }
        }
    }

    private static Map<String, String> withoutCredentials(Map<String, String> headers) {
        Map<String, String> plain = new TreeMap<>(headers);
        plain.keySet().removeAll(CREDENTIAL_HEADERS);
        return plain;
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> all = new java.util.TreeSet<>(a);
        all.addAll(b);
        return all;
    }

    private static String mask(String value) {
        String masked = value;
        for (Pattern p : VOLATILE) masked = p.matcher(masked).replaceAll("<v>");
        return masked;
    }

    private static String clip(String value) {
        if (value == null) return "";
        return value.length() > MAX_VALUE ? value.substring(0, MAX_VALUE) + "..." : value;
    }

    private static String list(List<String> items) {
        if (items.isEmpty()) return "none";
        String shown = String.join(", ", items.subList(0, Math.min(MAX_LISTED, items.size())));
        return items.size() > MAX_LISTED ? shown + " (+" + (items.size() - MAX_LISTED) + " more)" : shown;
    }

    /** Leaf paths of a JSON body, or null if the body is not JSON. */
    private static Map<String, String> flatten(Object body) {
        Object json = asJson(body);
        if (!(json instanceof JSONObject) && !(json instanceof JSONArray)) return null;
        Map<String, String> out = new LinkedHashMap<>();
        flatten("", json, out);
        return out;
    }

    private static void flatten(String path, Object value, Map<String, String> out) {
        if (out.size() >= MAX_FIELDS) return;
        if (value instanceof JSONObject) {
            JSONObject obj = (JSONObject) value;
            Iterator<?> keys = obj.keys();
            while (keys.hasNext()) {
                String key = String.valueOf(keys.next());
                flatten(path.isEmpty() ? key : path + "." + key, obj.opt(key), out);
            }
        } else if (value instanceof JSONArray) {
            JSONArray arr = (JSONArray) value;
            for (int i = 0; i < arr.length(); i++) flatten(path + "[" + i + "]", arr.opt(i), out);
        } else {
            out.put(path, value == null || value == JSONObject.NULL ? "null" : value.toString());
        }
    }

    private static Object asJson(Object body) {
        if (body instanceof JSONObject || body instanceof JSONArray) return body;
        if (!(body instanceof String)) return null;
        String text = ((String) body).trim();
        try {
            if (text.startsWith("{")) return new JSONObject(text);
            if (text.startsWith("[")) return new JSONArray(text);
        } catch (Exception ignored) {
            // not JSON
        }
        return null;
    }

    /** One stored request/response, in the test result or the sample data format. */
    private static final class Exchange {
        String method = "";
        String path = "";
        Map<String, String> query = new TreeMap<>();
        Map<String, String> headers = new TreeMap<>();
        Object body;
        String status = "";
        Map<String, String> responseHeaders = new TreeMap<>();
        Object responseBody;

        static Exchange parse(String message) {
            if (message == null || message.trim().isEmpty()) return null;
            JSONObject msg;
            try {
                msg = new JSONObject(message);
            } catch (Exception e) {
                return null;
            }
            Exchange ex = new Exchange();
            JSONObject req = msg.optJSONObject("request");
            JSONObject resp = msg.optJSONObject("response");
            String url;
            if (req != null || resp != null) {
                req = req == null ? new JSONObject() : req;
                resp = resp == null ? new JSONObject() : resp;
                ex.method = req.optString("method", "");
                url = req.optString("url", "");
                addQuery(ex.query, req.optString("queryParams", ""));
                ex.headers = headerMap(req.opt("headers"));
                ex.body = req.opt("body");
                ex.status = String.valueOf(resp.opt("statusCode"));
                ex.responseHeaders = headerMap(resp.opt("headers"));
                ex.responseBody = resp.opt("body");
            } else {
                ex.method = msg.optString("method", "");
                url = msg.optString("path", "");
                ex.headers = headerMap(msg.opt("requestHeaders"));
                ex.body = msg.opt("requestPayload");
                ex.status = String.valueOf(msg.opt("statusCode"));
                ex.responseHeaders = headerMap(msg.opt("responseHeaders"));
                ex.responseBody = msg.opt("responsePayload");
            }
            int q = url.indexOf('?');
            ex.path = q >= 0 ? url.substring(0, q) : url;
            if (q >= 0) addQuery(ex.query, url.substring(q + 1));
            return ex;
        }

        private static void addQuery(Map<String, String> query, String text) {
            if (text == null || text.isEmpty()) return;
            for (String pair : text.split("&")) {
                if (pair.isEmpty()) continue;
                int eq = pair.indexOf('=');
                query.put(eq >= 0 ? pair.substring(0, eq) : pair, eq >= 0 ? pair.substring(eq + 1) : "");
            }
        }

        private static Map<String, String> headerMap(Object headers) {
            Map<String, String> out = new TreeMap<>();
            JSONObject obj = null;
            if (headers instanceof JSONObject) obj = (JSONObject) headers;
            else if (headers instanceof String && !((String) headers).trim().isEmpty()) {
                try {
                    obj = new JSONObject((String) headers);
                } catch (Exception ignored) {
                    // not a JSON header map
                }
            }
            if (obj == null) return out;
            Iterator<?> keys = obj.keys();
            while (keys.hasNext()) {
                String key = String.valueOf(keys.next());
                Object value = obj.opt(key);
                out.put(key.toLowerCase(), value == null || value == JSONObject.NULL ? "" : value.toString());
            }
            return out;
        }
    }
}

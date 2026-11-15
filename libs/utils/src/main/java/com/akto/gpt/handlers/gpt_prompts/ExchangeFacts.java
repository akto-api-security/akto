package com.akto.gpt.handlers.gpt_prompts;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.akto.utils.RedactParser;
import org.json.JSONArray;
import org.json.JSONObject;
import org.yaml.snakeyaml.Yaml;

/**
 * Exact differences between the baseline exchange and an attack exchange, computed in code so the
 * model does not have to compare long requests and responses by eye.
 */
final class ExchangeFacts {

    // Akto's own marker and transport headers that change on every request.
    private static final Set<String> IGNORED_HEADERS = new HashSet<>(Arrays.asList(
        "x-akto-ignore", "content-length", "connection", "accept-encoding", "postman-token", "date",
        "x-request-id", "request-id", "x-envoy-upstream-service-time", "debug-id", "eagleid", "via",
        ":status", "content-encoding", "transfer-encoding", "alt-svc", "vary", "timing-allow-origin",
        "x-site-cache-status", "server-timing", "age"));
    private static final Set<String> CREDENTIAL_HEADERS = new HashSet<>(Arrays.asList(
        "authorization", "cookie", "x-api-key", "api-key", "x-access-token", "access-token"));
    // Custom auth headers, e.g. cp-session-token, x-auth; anti-CSRF tokens are not credentials.
    private static final Pattern CREDENTIAL_NAME = Pattern.compile("token|session|auth|api[-_]?key|cookie");
    private static final Pattern CSRF_NAME = Pattern.compile("csrf|xsrf");
    // Server-generated values that differ on every call.
    private static final Pattern[] VOLATILE = {
        Pattern.compile("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:?\\d{2})?"),
        Pattern.compile("\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*\\.[A-Za-z0-9_-]*"),
        Pattern.compile("\\b[0-9a-f]{16,}\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b\\d{10,13}\\b"),
    };
    // "Digest username=...", "AWS4-HMAC-SHA256 Credential=...", "Bearer eyJ...": the scheme, then the credential.
    private static final Pattern AUTH_SCHEME = Pattern.compile("([A-Za-z][A-Za-z0-9_-]*)\\s+(.+)", Pattern.DOTALL);
    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]+\\.([A-Za-z0-9_-]+)\\.[A-Za-z0-9_-]*");
    // Parts whose value is a secret: named, never printed.
    private static final Pattern SECRET_PART = Pattern.compile("(?i)password|secret");
    private static final int MAX_PART = 40;
    private static final int MAX_LISTED = 20;
    private static final int MAX_VALUE = 60;
    private static final int MAX_FIELDS = 3000;
    private static final int MARKER_CONTEXT = 50;

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
            if (!isCredentialHeader(name)) continue;
            String before = base.headers.get(name);
            String after = attack.headers.get(name);
            if (before == null && after == null) continue;
            String diff = before != null && after != null && !before.equals(after) ? credentialDiff(before, after) : "";
            request.add("credential header " + name + ": " + (before == null ? "added (not in the baseline)"
                : after == null ? "removed" : before.equals(after) ? "identical to the baseline" + cookieNames(name, after)
                : diff.isEmpty() && isRedacted(before) ? "redacted in the baseline, cannot be compared"
                : "changed" + diff));
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
            if (isNoiseHeader(name)) continue;
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
            List<String> redacted = new ArrayList<>();
            for (String key : union(before.keySet(), after.keySet())) {
                String b = before.get(key);
                String a = after.get(key);
                if (b == null) { added.add(key); differing.add(a); }
                else if (a == null) removed.add(key);
                else if (isRedacted(b) && !b.equals(a)) redacted.add(key);
                else if (!mask(b).equals(mask(a))) { changed.add(key + " (" + clip(b) + " -> " + clip(a) + ")"); differing.add(a); }
            }
            String redactedLine = redacted.isEmpty() ? "" : "- Response body fields redacted in the baseline (their original values are"
                + " unknown, so a value there now is not a change): " + list(redacted) + "\n";
            if (changed.isEmpty() && added.isEmpty() && removed.isEmpty()) {
                sb.append(redacted.isEmpty()
                    ? "- Response body vs baseline: same fields and values (server-generated ids and timestamps ignored)\n"
                    : "- Response body vs baseline: same fields; no compared value changed\n" + redactedLine);
            } else {
                sb.append("- Response body fields changed: ").append(list(changed)).append("\n")
                  .append("- Response body fields added: ").append(list(added)).append("\n")
                  .append("- Response body fields removed: ").append(list(removed)).append("\n")
                  .append(redactedLine);
                if (!sentValues.isEmpty() && removed.isEmpty() && onlyEchoes(differing, sentValues)) {
                    sb.append("- The response differs from the baseline only by echoing values the attack sent.\n");
                }
            }
        }
        return sb.toString();
    }

    /**
     * Which of the template's validate markers (response_payload contains_either / contains_all / regex)
     * the attack response matched, matched the way the scanner does (trimmed, case-insensitive for
     * contains), with the text around each match and whether the original response has it too. A
     * marker already in the original response is not something the attack caused. "" when the
     * template has no such markers.
     */
    static String validateMarkers(String template, String baselineMessage, String attackMessage) {
        List<String> contains = new ArrayList<>();
        List<String> regexes = new ArrayList<>();
        try {
            Object parsed = template == null ? null : new Yaml().load(template);
            if (parsed instanceof Map) collectMarkers(((Map<?, ?>) parsed).get("validate"), false, contains, regexes);
        } catch (Exception e) {
            return "";
        }
        if (contains.isEmpty() && regexes.isEmpty()) return "";
        Exchange attack = Exchange.parse(attackMessage);
        if (attack == null) return "";
        Exchange base = Exchange.parse(baselineMessage);
        String body = String.valueOf(attack.responseBody).trim();
        String baseBody = base == null ? null : String.valueOf(base.responseBody).trim();

        List<String> found = new ArrayList<>();
        int inOriginal = 0;
        for (String marker : contains) {
            int at = body.toLowerCase().indexOf(marker.toLowerCase());
            if (at < 0) continue;
            boolean original = baseBody != null && baseBody.toLowerCase().contains(marker.toLowerCase());
            if (original) inOriginal++;
            found.add(describeMatch(marker, body, at, marker.length(), original, baseBody != null));
        }
        for (String regex : regexes) {
            try {
                Matcher m = Pattern.compile(regex).matcher(body);
                if (!m.find()) continue;
                boolean original = baseBody != null && Pattern.compile(regex).matcher(baseBody).find();
                if (original) inOriginal++;
                found.add(describeMatch("regex " + regex, body, m.start(), m.end() - m.start(), original, baseBody != null));
            } catch (Exception e) {
                // not a valid Java regex; the scanner could not have matched it either
            }
        }
        if (found.isEmpty()) {
            return "- Validate markers: none of the template's " + (contains.size() + regexes.size())
                + " response markers appear in this attack response body\n";
        }
        StringBuilder sb = new StringBuilder("- Validate markers matched in the attack response body (what the scanner flagged):\n");
        for (String line : found.subList(0, Math.min(found.size(), MAX_LISTED))) sb.append("  ").append(line).append("\n");
        if (baseBody != null && inOriginal == found.size()) {
            sb.append("  Every matched marker is also in the original response, so the attack did not cause the match.\n");
        }
        return sb.toString();
    }

    /**
     * For a template whose validate checks header names (request_headers / response_headers with
     * not_contains, i.e. a header that should be there), which of the names it lists the attack request
     * or response has and lacks, compared case-insensitively. "" when the template has no such check.
     */
    static String headerChecks(String template, String attackMessage) {
        Map<String, List<String>> listed = new LinkedHashMap<>();
        try {
            Object parsed = template == null ? null : new Yaml().load(template);
            if (!(parsed instanceof Map)) return "";
            for (String side : Arrays.asList("request_headers", "response_headers")) {
                List<String> names = new ArrayList<>();
                collectHeaderNames(((Map<?, ?>) parsed).get("validate"), side, false, names);
                if (!names.isEmpty()) listed.put(side, names);
            }
        } catch (Exception e) {
            return "";
        }
        if (listed.isEmpty()) return "";
        Exchange attack = Exchange.parse(attackMessage);
        if (attack == null) return "";

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> check : listed.entrySet()) {
            boolean request = "request_headers".equals(check.getKey());
            Set<String> present = (request ? attack.headers : attack.responseHeaders).keySet();
            List<String> has = new ArrayList<>();
            List<String> lacks = new ArrayList<>();
            for (String name : check.getValue()) {
                (present.contains(name.toLowerCase()) ? has : lacks).add(name);
            }
            sb.append("- Headers the template's ").append(check.getKey()).append(" check lists, in the attack ")
              .append(request ? "request (sent by Akto's scanner, not by a user's browser)" : "response")
              .append(": missing: ").append(list(lacks)).append("; present: ").append(list(has)).append("\n");
        }
        return sb.toString();
    }

    // Header names listed under not_contains below request_headers or response_headers, at any depth.
    private static void collectHeaderNames(Object node, String side, boolean inSide, List<String> names) {
        if (node instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) node).entrySet()) {
                String key = String.valueOf(entry.getKey());
                boolean here = inSide || side.equals(key);
                if (here && "not_contains".equals(key)) {
                    for (String name : stringsOf(entry.getValue())) {
                        if (!names.contains(name)) names.add(name);
                    }
                } else {
                    collectHeaderNames(entry.getValue(), side, here, names);
                }
            }
        } else if (node instanceof List) {
            for (Object item : (List<?>) node) collectHeaderNames(item, side, inSide, names);
        }
    }

    private static List<String> stringsOf(Object value) {
        List<String> out = new ArrayList<>();
        addStrings(value, out);
        return out;
    }

    private static String describeMatch(String marker, String body, int at, int length, boolean inOriginal, boolean haveOriginal) {
        int from = Math.max(0, at - MARKER_CONTEXT);
        int to = Math.min(body.length(), at + length + MARKER_CONTEXT);
        String context = (from > 0 ? "..." : "") + body.substring(from, to).replace("\n", " ") + (to < body.length() ? "..." : "");
        return "\"" + marker + "\" in: " + context
            + (haveOriginal ? (inOriginal ? " (also in the original response)" : " (NOT in the original response)") : "");
    }

    // Markers under response_payload, at any depth of validate (and/or blocks included).
    private static void collectMarkers(Object node, boolean inResponsePayload, List<String> contains, List<String> regexes) {
        if (node instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) node).entrySet()) {
                String key = String.valueOf(entry.getKey());
                boolean inPayload = inResponsePayload || "response_payload".equals(key);
                if (inPayload && ("contains_either".equals(key) || "contains_all".equals(key))) {
                    addStrings(entry.getValue(), contains);
                } else if (inPayload && "regex".equals(key)) {
                    addStrings(entry.getValue(), regexes);
                } else {
                    collectMarkers(entry.getValue(), inPayload, contains, regexes);
                }
            }
        } else if (node instanceof List) {
            for (Object item : (List<?>) node) collectMarkers(item, inResponsePayload, contains, regexes);
        }
    }

    // Plain strings only; ${...} values are filled in per request and cannot be checked here.
    private static void addStrings(Object value, List<String> out) {
        List<?> values = value instanceof List ? (List<?>) value : java.util.Collections.singletonList(value);
        for (Object v : values) {
            if (!(v instanceof String)) continue;
            String marker = ((String) v).trim();
            if (!marker.isEmpty() && !marker.contains("${")) out.add(marker);
        }
    }

    /** The request headers of a stored exchange (keys lower-cased), or null if it does not parse. */
    static Map<String, String> requestHeaders(String message) {
        Exchange ex = Exchange.parse(message);
        return ex == null ? null : ex.headers;
    }

    /** Header map of a stored headers field (a JSON object or its string form), keys lower-cased. */
    static Map<String, String> headers(Object headers) {
        return Exchange.headerMap(headers);
    }

    /**
     * The response times a step's requests took, as the scanner computes its ${xN.response.stats.*}
     * values from them, or "" when none was stored (single-request tests store none).
     */
    static String responseTimes(List<String> messages) {
        List<Integer> times = new ArrayList<>();
        for (String message : messages) {
            try {
                Object time = new JSONObject(message).opt("responseTime");
                if (time instanceof Number) times.add(((Number) time).intValue());
            } catch (Exception ignored) {
                // not a stored exchange
            }
        }
        if (times.isEmpty()) return "";
        if (times.size() == 1) return "- Response time: " + times.get(0) + " ms\n";
        java.util.Collections.sort(times);
        int mid = times.size() / 2;
        String median = times.size() % 2 == 0 && !times.get(mid - 1).equals(times.get(mid))
            ? String.valueOf((times.get(mid - 1) + times.get(mid)) / 2.0) : String.valueOf(times.get(mid));
        return "- Response times of this step's " + times.size() + " requests: median " + median + " ms, min "
            + times.get(0) + " ms, max " + times.get(times.size() - 1) + " ms\n";
    }

    /**
     * Which response body values the attack request itself sent (in its body, query or path). A
     * response made only of such values (e.g. a create or update returning the submitted fields)
     * holds no data of the server's own. "" when the body is not JSON or no value was sent.
     */
    static String responseEcho(String attackMessage) {
        Exchange ex = Exchange.parse(attackMessage);
        if (ex == null) return "";
        Map<String, String> response = flatten(ex.responseBody);
        if (response == null || response.isEmpty()) return "";
        Set<String> sent = new HashSet<>(ex.query.values());
        for (String segment : ex.path.split("/")) {
            if (!segment.isEmpty()) sent.add(segment);
        }
        Map<String, String> body = flatten(ex.body);
        if (body != null) sent.addAll(body.values());
        else if (ex.body != null) sent.add(String.valueOf(ex.body));

        List<String> notSent = new ArrayList<>();
        for (Map.Entry<String, String> field : response.entrySet()) {
            if (!sent.contains(field.getValue())) notSent.add(field.getKey());
        }
        if (notSent.isEmpty()) {
            return "- Response body: every value in it was sent in this request, so it only echoes the request "
                + "and holds no data of the server's own\n";
        }
        if (notSent.size() == response.size()) return "";
        return "- Response body: " + (response.size() - notSent.size()) + " of " + response.size()
            + " values were sent in this request; fields with values the request did not send: " + list(notSent) + "\n";
    }

    /** Status and body with server-generated values masked: equal for attempts that got the same response. */
    static String responseSignature(String message) {
        Exchange ex = Exchange.parse(message);
        return ex == null ? null : ex.status + "|" + mask(String.valueOf(ex.responseBody));
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
            if ("header".equals(label) && isNoiseHeader(key)) continue;
            String b = base.get(key);
            String a = attack.get(key);
            if (b == null) { out.add(label + " added: " + key + "=" + clip(a)); sentValues.add(a); }
            else if (a == null) out.add(label + " removed: " + key);
            else if (isRedacted(b) && !b.equals(a)) { out.add(label + " " + key + ": redacted in the baseline, cannot be compared"); sentValues.add(a); }
            else if (!b.equals(a)) { out.add(label + " changed: " + key + " (" + clip(b) + " -> " + clip(a) + ")"); sentValues.add(a); }
        }
    }

    /** The cookie names a cookie header carries, so the model can tell a session cookie from tracking ones. */
    private static String cookieNames(String header, String value) {
        if (!"cookie".equals(header)) return "";
        List<String> names = new ArrayList<>();
        for (String pair : value.split(";")) {
            int eq = pair.indexOf('=');
            String cookie = (eq >= 0 ? pair.substring(0, eq) : pair).trim();
            if (!cookie.isEmpty()) names.add(cookie);
        }
        return names.isEmpty() ? "" : " (cookies: " + list(names) + ")";
    }

    /**
     * Which parts of a changed credential are the same and which differ, so a credential of the same
     * user signed again for this request (Digest, AWS SigV4, OAuth 1, a refreshed JWT) is not read as
     * another user's. "" when the values have no readable parts (an opaque token).
     */
    static String credentialDiff(String before, String after) {
        Map<String, String> b = credentialParts(before);
        Map<String, String> a = credentialParts(after);
        if (b.isEmpty() || a.isEmpty()) return "";
        List<String> same = new ArrayList<>();
        List<String> differ = new ArrayList<>();
        List<String> onlyBase = new ArrayList<>();
        List<String> onlyAttack = new ArrayList<>();
        List<String> redacted = new ArrayList<>();
        Set<String> keys = new java.util.LinkedHashSet<>(b.keySet());
        keys.addAll(a.keySet());
        for (String key : keys) {
            String bv = b.get(key);
            String av = a.get(key);
            boolean secret = SECRET_PART.matcher(key).find();
            if (bv == null) onlyAttack.add(key);
            else if (av == null) onlyBase.add(key);
            else if (isRedacted(bv) && !bv.equals(av)) redacted.add(key);
            else if (bv.equals(av)) same.add(secret ? key : key + "=" + clip(bv, MAX_PART));
            else differ.add(secret ? key : key + " (" + clip(bv, MAX_PART) + " -> " + clip(av, MAX_PART) + ")");
        }
        StringBuilder sb = new StringBuilder(" (parts the same: ").append(list(same))
            .append("; parts that differ: ").append(list(differ));
        if (!onlyBase.isEmpty()) sb.append("; only in the baseline: ").append(list(onlyBase));
        if (!onlyAttack.isEmpty()) sb.append("; only in the attack: ").append(list(onlyAttack));
        if (!redacted.isEmpty()) sb.append("; redacted in the baseline, cannot be compared: ").append(list(redacted));
        return sb.append(")").toString();
    }

    /** A value Akto redacted in the stored sample (RedactParser writes ****, Bearer ****, name=****). */
    static boolean isRedacted(String value) {
        return value != null && value.contains(RedactParser.redactValue);
    }

    /** A credential split into named parts: its scheme, key=value parameters or cookies, Basic user, JWT claims. */
    private static Map<String, String> credentialParts(String value) {
        Map<String, String> parts = new LinkedHashMap<>();
        String rest = value == null ? "" : value.trim();
        Matcher scheme = AUTH_SCHEME.matcher(rest);
        if (scheme.matches()) {
            parts.put("scheme", scheme.group(1));
            rest = scheme.group(2).trim();
        }
        if ("basic".equalsIgnoreCase(parts.get("scheme"))) {
            try {
                String decoded = new String(Base64.getDecoder().decode(rest), StandardCharsets.UTF_8);
                int colon = decoded.indexOf(':');
                parts.put("user", colon >= 0 ? decoded.substring(0, colon) : decoded);
                if (colon >= 0) parts.put("password", decoded.substring(colon + 1));
            } catch (IllegalArgumentException e) {
                return new LinkedHashMap<>();
            }
            return parts;
        }
        int before = parts.size();
        if (JWT.matcher(rest).matches()) {
            addPart(parts, "jwt", rest);
        } else if (rest.contains("=")) {
            for (String pair : rest.split("[,;]")) {
                int eq = pair.indexOf('=');
                if (eq <= 0) continue;
                String val = pair.substring(eq + 1).trim();
                if (val.length() >= 2 && val.startsWith("\"") && val.endsWith("\"")) val = val.substring(1, val.length() - 1);
                addPart(parts, pair.substring(0, eq).trim(), val);
            }
        }
        return parts.size() > before ? parts : new LinkedHashMap<>();
    }

    // A JWT value is shown by its claims (sub, iat, exp...), which say whose token it is.
    private static void addPart(Map<String, String> parts, String name, String value) {
        Matcher jwt = JWT.matcher(value);
        if (jwt.matches()) {
            try {
                String payload = new String(Base64.getUrlDecoder().decode(jwt.group(1)), StandardCharsets.UTF_8);
                Map<String, String> claims = new LinkedHashMap<>();
                flatten("", new JSONObject(payload), claims);
                for (Map.Entry<String, String> claim : claims.entrySet()) parts.put(name + "." + claim.getKey(), claim.getValue());
                return;
            } catch (Exception ignored) {
                // not a readable JWT payload
            }
        }
        parts.put(name, value);
    }

    private static boolean isCredentialHeader(String name) {
        if (CSRF_NAME.matcher(name).find()) return false;
        return CREDENTIAL_HEADERS.contains(name) || CREDENTIAL_NAME.matcher(name).find();
    }

    /** Akto's own and transport headers (date, via, rate limits...) that differ on every call and carry no evidence. */
    static boolean isNoiseHeader(String name) {
        return IGNORED_HEADERS.contains(name) || name.startsWith("ratelimit-") || name.startsWith("x-ratelimit-");
    }

    private static Map<String, String> withoutCredentials(Map<String, String> headers) {
        Map<String, String> plain = new TreeMap<>(headers);
        plain.keySet().removeIf(ExchangeFacts::isCredentialHeader);
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
        return clip(value, MAX_VALUE);
    }

    private static String clip(String value, int max) {
        if (value == null) return "";
        return value.length() > max ? value.substring(0, max) + "..." : value;
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
                // optString would turn a JSON null into the text "null", read as a query param named null
                Object queryParams = req.opt("queryParams");
                if (queryParams instanceof String) addQuery(ex.query, (String) queryParams);
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

        static Map<String, String> headerMap(Object headers) {
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

package com.akto.testing.login_flow;

import java.net.HttpCookie;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Browser-like cookie jar for one login-flow run, rebuilt from each previous node's raw Set-Cookie lines
 * (stored by populateValuesMap as {@code xN.response.set-cookie-raw}) and request URL ({@code xN.request.url}).
 * It holds no static state and is never persisted; see CLAUDE.md in this package for the rules.
 */
public final class LoginFlowCookieJar {

    public static final int MAX_COOKIES = 300;
    public static final int MAX_COOKIE_BYTES = 4096;
    /** Raw Set-Cookie lines longer than this are not stored (a cookie that large is ignored anyway). */
    public static final int MAX_RAW_LINE_CHARS = 8192;
    public static final String RAW_SET_COOKIE_SUFFIX = ".response.set-cookie-raw";
    public static final String REQUEST_URL_SUFFIX = ".request.url";

    private static final Pattern RAW_KEY = Pattern.compile("^x(\\d+)\\.response\\.set-cookie-raw$");
    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final Pattern MAX_AGE = Pattern.compile("^-?\\d+$");
    private static final String[] EXPIRES_FORMATS = {
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEE, dd-MMM-yyyy HH:mm:ss zzz",
            "EEE, dd-MMM-yy HH:mm:ss zzz",
            "EEE MMM dd HH:mm:ss yyyy"
    };

    /** One stored cookie: the HttpCookie plus the RFC 6265 fields HttpCookie doesn't model. */
    private static final class Entry {
        final HttpCookie cookie;
        final boolean hostOnly;
        final long seq;

        Entry(HttpCookie cookie, boolean hostOnly, long seq) {
            this.cookie = cookie;
            this.hostOnly = hostOnly;
            this.seq = seq;
        }
    }

    /** Name/value plus attributes (lower-cased names; flags map to ""). */
    public static final class ParsedSetCookie {
        private final String name;
        private final String value;
        private final Map<String, String> attributes;

        ParsedSetCookie(String name, String value, Map<String, String> attributes) {
            this.name = name;
            this.value = value;
            this.attributes = attributes;
        }

        public String getName() { return name; }
        public String getValue() { return value; }
        public Map<String, String> getAttributes() { return attributes; }
    }

    private final List<Entry> entries = new ArrayList<>();    // insertion order == seq order
    private final Map<String, String> notes = new LinkedHashMap<String, String>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > MAX_COOKIES;
        }
    };
    private long seq = 0;

    /**
     * Rebuilds the jar from the raw Set-Cookie lines of every node numbered below {@code beforeNodeNumber},
     * in node order, so later steps override earlier ones.
     */
    public static LoginFlowCookieJar fromValuesMap(Map<String, Object> valuesMap, int beforeNodeNumber) {
        LoginFlowCookieJar jar = new LoginFlowCookieJar();
        if (valuesMap == null) return jar;
        TreeMap<Integer, Object> rawByNode = new TreeMap<>();
        for (Map.Entry<String, Object> e : valuesMap.entrySet()) {
            Matcher m = RAW_KEY.matcher(e.getKey());
            if (!m.matches()) continue;
            int n;
            try {
                n = Integer.parseInt(m.group(1));
            } catch (NumberFormatException ex) {
                continue;
            }
            if (n < beforeNodeNumber) rawByNode.put(n, e.getValue());
        }
        for (Map.Entry<Integer, Object> e : rawByNode.entrySet()) {
            String nodeId = "x" + e.getKey();
            Object url = valuesMap.get(nodeId + REQUEST_URL_SUFFIX);
            for (String line : splitRaw(e.getValue())) {
                jar.receive(url == null ? null : url.toString(), line, nodeId);
            }
        }
        return jar;
    }

    /** Joins raw Set-Cookie lines for storage in the values map (newline can't occur inside a header value). */
    public static String joinRaw(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String line : lines) {
            if (line == null || line.isEmpty() || line.length() > MAX_RAW_LINE_CHARS) continue;
            if (count == MAX_COOKIES) break;
            if (count > 0) sb.append('\n');
            sb.append(line.replace('\n', ' ').replace('\r', ' '));
            count++;
        }
        return sb.toString();
    }

    static List<String> splitRaw(Object raw) {
        List<String> lines = new ArrayList<>();
        if (raw == null) return lines;
        if (raw instanceof List) {
            for (Object o : (List<?>) raw) {
                if (o != null) lines.add(o.toString());
            }
            return lines;
        }
        for (String line : raw.toString().split("\n")) {
            if (!line.trim().isEmpty()) lines.add(line);
        }
        return lines;
    }

    /** Parses one Set-Cookie header value; null when it has no {@code name=} pair. */
    public static ParsedSetCookie parseSetCookie(String line) {
        if (line == null) return null;
        String[] parts = line.split(";");
        String pair = parts[0];
        int eq = pair.indexOf('=');
        if (eq <= 0) return null;
        String name = pair.substring(0, eq).trim();
        if (name.isEmpty()) return null;
        String value = pair.substring(eq + 1).trim();
        Map<String, String> attributes = new LinkedHashMap<>();
        for (int i = 1; i < parts.length; i++) {
            String attr = parts[i].trim();
            if (attr.isEmpty()) continue;
            int aeq = attr.indexOf('=');
            String key = (aeq < 0 ? attr : attr.substring(0, aeq)).trim().toLowerCase(Locale.ROOT);
            String val = aeq < 0 ? "" : attr.substring(aeq + 1).trim();
            attributes.put(key, val);   // last occurrence wins (RFC 6265 5.3)
        }
        return new ParsedSetCookie(name, value, attributes);
    }

    /** Parses a request Cookie header ("a=1; b=2") into name/value pairs, in order. */
    public static List<String[]> parseCookieHeader(String header) {
        List<String[]> pairs = new ArrayList<>();
        if (header == null) return pairs;
        for (String part : header.split(";")) {
            String p = part.trim();
            int eq = p.indexOf('=');
            if (eq <= 0) continue;
            String name = p.substring(0, eq).trim();
            if (name.isEmpty()) continue;
            pairs.add(new String[]{name, p.substring(eq + 1).trim()});
        }
        return pairs;
    }

    /** Applies one Set-Cookie line received in response to a request for {@code requestUrl}. */
    public void receive(String requestUrl, String setCookieLine, String sourceNodeId) {
        ParsedSetCookie parsed = parseSetCookie(setCookieLine);
        if (parsed == null) return;
        String name = parsed.getName();
        String source = sourceNodeId == null ? "a previous step" : sourceNodeId;

        UrlParts url = UrlParts.parse(requestUrl);
        if (url == null) {
            note(name, "ignored: " + source + " has no usable request URL to scope it to");
            return;
        }
        if (name.length() + parsed.getValue().getBytes(StandardCharsets.UTF_8).length > MAX_COOKIE_BYTES) {
            note(name, "ignored: set by " + source + " but larger than " + MAX_COOKIE_BYTES + " bytes");
            return;
        }
        HttpCookie cookie;
        try {
            cookie = new HttpCookie(name, parsed.getValue());
        } catch (IllegalArgumentException e) {
            note(name, "ignored: invalid cookie name set by " + source);
            return;
        }

        Map<String, String> attrs = parsed.getAttributes();
        String domainAttr = attrs.get("domain");
        boolean hostOnly;
        String domain;
        if (domainAttr == null || domainAttr.isEmpty() || domainAttr.equals(".")) {
            hostOnly = true;
            domain = url.host;
        } else {
            domain = (domainAttr.startsWith(".") ? domainAttr.substring(1) : domainAttr).toLowerCase(Locale.ROOT);
            if (!domainMatches(url.host, domain)) {
                note(name, "ignored: Domain=" + domain + " set by " + source + " does not match host " + url.host);
                return;
            }
            hostOnly = false;
        }
        String pathAttr = attrs.get("path");
        String path = pathAttr != null && pathAttr.startsWith("/") ? pathAttr : defaultPath(url.path);

        cookie.setDomain(domain);
        cookie.setPath(path);
        cookie.setSecure(attrs.containsKey("secure"));
        cookie.setHttpOnly(attrs.containsKey("httponly"));

        removeSameIdentity(name, domain, path);
        if (isExpired(attrs)) {
            note(name, "deleted by " + source + " (Max-Age/Expires in the past)");
            return;
        }
        entries.add(new Entry(cookie, hostOnly, ++seq));
        notes.remove(name);
        if (entries.size() > MAX_COOKIES) {
            Entry evicted = entries.remove(0);
            note(evicted.cookie.getName(), "evicted: the jar holds at most " + MAX_COOKIES + " cookies");
        }
    }

    /**
     * Cookie header value for a request to {@code url} ("" when no cookie applies).
     * @throws PlaceholderException when the URL has no host
     */
    public String cookieHeaderFor(String url) throws PlaceholderException {
        UrlParts target = UrlParts.parse(url);
        if (target == null) {
            throw new PlaceholderException("${cookies}: cannot determine the host of the request URL");
        }
        boolean secureContext = isSecureContext(target);
        List<Entry> matching = new ArrayList<>();
        for (Entry e : entries) {
            HttpCookie c = e.cookie;
            boolean hostOk = e.hostOnly ? target.host.equals(c.getDomain()) : domainMatches(target.host, c.getDomain());
            if (!hostOk) continue;
            if (!pathMatches(target.path, c.getPath())) continue;
            if (c.getSecure() && !secureContext) continue;
            matching.add(e);
        }
        // RFC 6265 5.4: longer paths first, then earlier creation
        Collections.sort(matching, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                int byPath = Integer.compare(b.cookie.getPath().length(), a.cookie.getPath().length());
                return byPath != 0 ? byPath : Long.compare(a.seq, b.seq);
            }
        });
        StringBuilder sb = new StringBuilder();
        for (Entry e : matching) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(e.cookie.getName()).append('=').append(e.cookie.getValue());
        }
        return sb.toString();
    }

    /** Latest live value of the named cookie from any host, or null. */
    public String latestValue(String name) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            HttpCookie c = entries.get(i).cookie;
            if (c.getName().equals(name)) return c.getValue();
        }
        return null;
    }

    /** Why the named cookie has no value (never set / deleted / ignored), without any cookie value. */
    public String missingReason(String name) {
        String note = notes.get(name);
        return note != null ? note : "no previous step set it";
    }

    public int size() {
        return entries.size();
    }

    private void removeSameIdentity(String name, String domain, String path) {
        Iterator<Entry> it = entries.iterator();
        while (it.hasNext()) {
            HttpCookie c = it.next().cookie;
            if (c.getName().equals(name) && c.getDomain().equals(domain) && c.getPath().equals(path)) {
                it.remove();
            }
        }
    }

    private void note(String name, String reason) {
        notes.remove(name);
        notes.put(name, reason);
    }

    private static boolean isExpired(Map<String, String> attrs) {
        String maxAge = attrs.get("max-age");
        if (maxAge != null && MAX_AGE.matcher(maxAge).matches()) {
            // Max-Age wins over Expires (RFC 6265 5.3 step 3)
            return maxAge.startsWith("-") || Long.parseLong(maxAge) == 0L;
        }
        String expires = attrs.get("expires");
        if (expires == null || expires.isEmpty()) return false;
        for (String format : EXPIRES_FORMATS) {
            SimpleDateFormat df = new SimpleDateFormat(format, Locale.US);
            df.setTimeZone(TimeZone.getTimeZone("GMT"));
            df.setLenient(false);
            try {
                Date d = df.parse(expires);
                return d.getTime() <= System.currentTimeMillis();
            } catch (ParseException e) {
                // try the next format; an unparseable Expires is ignored, like browsers do
            }
        }
        return false;
    }

    static boolean domainMatches(String host, String domain) {
        if (host == null || domain == null || domain.isEmpty()) return false;
        if (host.equals(domain)) return true;
        return host.endsWith("." + domain) && !isIpAddress(host);
    }

    static boolean pathMatches(String requestPath, String cookiePath) {
        if (requestPath.equals(cookiePath)) return true;
        if (!requestPath.startsWith(cookiePath)) return false;
        return cookiePath.endsWith("/") || requestPath.charAt(cookiePath.length()) == '/';
    }

    static String defaultPath(String requestPath) {
        if (requestPath == null || !requestPath.startsWith("/")) return "/";
        int last = requestPath.lastIndexOf('/');
        return last == 0 ? "/" : requestPath.substring(0, last);
    }

    private static boolean isIpAddress(String host) {
        return IPV4.matcher(host).matches() || host.contains(":");
    }

    /** https, or a loopback host that browsers and curl treat as a secure context. */
    private static boolean isSecureContext(UrlParts url) {
        if ("https".equals(url.scheme)) return true;
        String h = url.host;
        return h.equals("localhost") || h.endsWith(".localhost") || h.equals("[::1]") || h.equals("::1")
                || (h.startsWith("127.") && IPV4.matcher(h).matches());
    }

    private static final class UrlParts {
        final String scheme;
        final String host;
        final String path;

        private UrlParts(String scheme, String host, String path) {
            this.scheme = scheme;
            this.host = host;
            this.path = path;
        }

        static UrlParts parse(String url) {
            if (url == null || url.trim().isEmpty()) return null;
            try {
                URL u = new URL(url.trim());
                String host = u.getHost();
                if (host == null || host.isEmpty()) return null;
                String path = u.getPath();
                if (path == null || path.isEmpty()) path = "/";
                return new UrlParts(u.getProtocol().toLowerCase(Locale.ROOT), host.toLowerCase(Locale.ROOT), path);
            } catch (MalformedURLException e) {
                return null;
            }
        }
    }
}

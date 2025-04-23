package com.akto.store;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StandardHeaders {
    public static Set<String> headers = new HashSet<>();
    private static final Set<String> authRelatedHeaders = new HashSet<>(Arrays.asList(
        "authorization", "x-auth-token", "x-requested-with",
        "x-request-id", "x-correlation-id", "access-token", "token", "auth"
    ));

    private static final Set<String> mdnStandardHeaders = new HashSet<>(Arrays.asList(
        "accept","accept-ch","accept-encoding","accept-language",  "accept-patch","accept-post", "accept-ranges", "access-control-allow-credentials", "access-control-allow-headers",   
        "access-control-allow-methods", "access-control-allow-origin", "access-control-expose-headers","access-control-max-age",
        "access-control-request-headers", "access-control-request-method", "age", "allow", "alt-svc", "alt-used", "attribution-reporting-eligibleexperimental", 
        "attribution-reporting-register-sourceexperimental", "attribution-reporting-register-triggerexperimental", "authorization",
        "available-dictionaryexperimental", "cache-control", "clear-site-data", "connection", "content-digest", "content-disposition", "content-dprnon-standarddeprecated",
        "content-encoding","content-language", "content-length", "content-location", "content-range", "content-security-policy", "content-security-policy-report-only", "content-type",
        "cookie","critical-chexperimental","cross-origin-embedder-policy","cross-origin-opener-policy","cross-origin-resource-policy","date","device-memory", "dictionary-idexperimental",
        "dntnon-standarddeprecated","downlinkexperimental","dprnon-standarddeprecated","early-dataexperimental","ectexperimental","etag","expect",
        "expect-ctdeprecated","expires", "forwarded", "from", "host", "if-match", "if-modified-since", "if-none-match", "if-range", "if-unmodified-since", 
        "keep-alive","last-modified","link", "location", "max-forwards", "nelexperimental", "no-vary-searchexperimental", "observe-browsing-topicsexperimentalnon-standard", "origin", 
        "origin-agent-cluster", "permissions-policyexperimental", "pragmadeprecated", "prefer", "preference-applied", "priority", "proxy-authenticate", "proxy-authorization", "range", "referer",
        "referrer-policy", "refresh", "report-tonon-standarddeprecated", "reporting-endpoints", "repr-digest", "retry-after", "rttexperimental", "save-dataexperimental", "sec-browsing-topicsexperimentalnon-standard", "sec-ch-ua", "sec-ch-ua-mobile",
        "sec-ch-prefers-color-schemeexperimental", "sec-ch-prefers-reduced-motionexperimental", "sec-ch-prefers-reduced-transparencyexperimental", "sec-ch-uaexperimental", "sec-ch-ua-archexperimental", "sec-ch-ua-bitnessexperimental",
        "sec-ch-ua-form-factorsexperimental","sec-ch-ua-full-versiondeprecated", "sec-ch-ua-full-version-listexperimental", "sec-ch-ua-mobileexperimental", "sec-ch-ua-modelexperimental", "sec-ch-ua-platformexperimental", "sec-ch-ua-platform-versionexperimental",
        "sec-ch-ua-wow64experimental", "sec-fetch-dest", "sec-fetch-mode", "sec-fetch-site", "sec-fetch-user", "sec-gpcexperimental", "sec-purpose", "sec-speculation-tagsexperimental",
        "sec-websocket-accept", "sec-websocket-extensions", "sec-websocket-key", "sec-websocket-protocol", "sec-websocket-version", "server", "server-timing", "service-worker", "service-worker-allowed", "service-worker-navigation-preload", "set-cookie",
        "set-login", "sourcemap", "speculation-rulesexperimental", "strict-transport-security", "supports-loading-modeexperimental", "te", "timing-allow-origin", "tknon-standarddeprecated", "trailer", "transfer-encoding", "upgrade",
        "upgrade-insecure-requests",  "use-as-dictionaryexperimental",  "user-agent",  "vary",  "via",  "viewport-widthnon-standarddeprecated",  "want-content-digest",  "want-repr-digest",
        "warningdeprecated","widthnon-standarddeprecated", "www-authenticate", "x-content-type-options", "x-dns-prefetch-controlnon-standard", "x-forwarded-fornon-standard", "x-forwarded-hostnon-standard",
        "x-forwarded-protonon-standard", "x-frame-options", "x-permitted-cross-domain-policiesnon-standard", "x-powered-bynon-standard", "x-robots-tagnon-standard", "x-xss-protectionnon-standarddeprecated"
    ));

    private static void add(String value) {
        if (value == null) return;
        String cleanedValue = value.toLowerCase();
        cleanedValue = cleanedValue.trim();
        headers.add(cleanedValue);
    }

    public static boolean isStandardHeader(String value) {
        String cleanedValue = value.toLowerCase();
        cleanedValue = cleanedValue.trim();
        return headers.contains(cleanedValue);
    }

    public static boolean isMdnStandardHeader(String value) {
        String cleanedValue = value.toLowerCase();
        cleanedValue = cleanedValue.trim();
        return mdnStandardHeaders.contains(cleanedValue);
    }

    static {
        add("A-IM");
        add("Accept");
        add("Accept-Charset");
        add("Accept-Datetime");
        add("Accept-Encoding");
        add("Accept-Language");
        add("Access-Control-Request-Method");
        add("Access-Control-Request-Headers");
        add("Cache-Control");
        add("Connection");
        add("Content-Encoding");
        add("Content-Length");
        add("Content-MD5");
        add("Content-Type");
        add("Cookie");
        add("Date");
        add("Expect");
        add("Forwarded");
        add("From");
        add("Host");
        add("HTTP2-Settings");
        add("If-Match");
        add("If-Modified-Since");
        add("If-None-Match");
        add("If-Range");
        add("If-Unmodified-Since");
        add("Max-Forwards");
        add("Origin");
        add("Pragma");
        add("Prefer");
        add("Proxy-Authorization");
        add("Range");
        add("Referer");
        add("TE");
        add("Trailer");
        add("Transfer-Encoding");
        add("User-Agent");
        add("Upgrade");
        add("Via");
        add("Warning");

        add("Upgrade-Insecure-Requests");
        add("X-Requested-With");
        add("DNT");
        add("X-Forwarded-For");
        add("X-Forwarded-Host");
        add("X-Forwarded-Proto");
        add("Front-End-Https");
        add("X-Http-Method-Override");
        add("X-ATT-DeviceId");
        add("X-Wap-Profile");
        add("Proxy-Connection");
        add("X-UIDH");
        add("X-Request-ID");
        add("X-Correlation-ID");
        add("Save-Data");

        // mpl
        add("sec-ch-ua");
        add("x-amzn-trace-id");

        // todo:
        add("idempotency-key");

    }

    public static void removeStandardAndAuthHeaders(Map<String, List<String>> headers, boolean isRequest) {
        Iterator<Map.Entry<String, List<String>>> iterator = headers.entrySet().iterator();
        while (iterator.hasNext()) {
            try {
                Map.Entry<String, List<String>> entry = iterator.next();
                String headerKey = entry.getKey().toLowerCase().trim();
                if (isMdnStandardHeader(headerKey)) {
                    iterator.remove(); 
                } else if (isRequest && authRelatedHeaders.contains(headerKey)) {
                    iterator.remove();
                }
            } catch (Exception e) {
                e.printStackTrace();
                // TODO: handle exception
            }
        }
    }
}

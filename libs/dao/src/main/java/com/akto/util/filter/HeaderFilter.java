package com.akto.util.filter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility class for filtering headers that should be ignored during STI processing.
 * This helps reduce noise by excluding infrastructure, proxy, and common headers.
 */
public class HeaderFilter {

    // Headers to ignore - categorized for clarity and maintainability
    private static final Set<String> IGNORED_HEADERS = new HashSet<>(Arrays.asList(
        // Proxy/Infrastructure headers (14)
        "x-forwarded-for", "x-forwarded-host", "x-forwarded-port", "x-forwarded-proto",
        "x-forwarded-scheme", "x-forwarded-client-cert", "x-original-forwarded-for",
        "x-real-ip", "x-envoy-attempt-count", "x-envoy-external-address",
        "x-request-id", "x-scheme", "via",

        // Browser/Client metadata headers (10)
        "user-agent", "accept-encoding", "accept-language", "sec-ch-ua",
        "sec-ch-ua-mobile", "sec-ch-ua-platform", "sec-fetch-dest",
        "sec-fetch-mode", "sec-fetch-site", "dnt",

        // CORS/Browser security headers (3)
        "origin", "referer", "upgrade-insecure-requests",

        // Auto-calculated headers (1)
        "content-length",

        // Misplaced response headers in request (1)
        "access-control-allow-origin",

        // Additional common headers (previously included) (7)
        "date", "accept", "cache-control", "connection", "pragma", "etag",

        // CDN/Infrastructure specific (3)
        "akamai-origin-hop", "x-akamai-config-log-detail", "cdn-loop",

        // Custom internal headers (1)
        "x-src-id"
    ));

    // Header prefixes to ignore - Akto and Envoy specific headers
    private static final String[] IGNORED_HEADER_PREFIXES = {
        "x-akto-k8s",
        "x-envoy"
    };

    /**
     * Check if a header should be ignored during STI processing
     * @param param The header parameter name
     * @return true if the header should be ignored, false otherwise
     */
    public static boolean shouldIgnoreHeader(String param) {
        if (param == null || param.isEmpty()) {
            return false;
        }

        String lowerParam = param.toLowerCase();

        // Check exact matches
        if (IGNORED_HEADERS.contains(lowerParam)) {
            return true;
        }

        // Check prefixes
        for (String prefix : IGNORED_HEADER_PREFIXES) {
            if (lowerParam.startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }
}

package com.akto.util.filter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility class for filtering headers that should be ignored during STI processing.
 * This helps reduce noise by excluding infrastructure, proxy, and common headers.
 */
public class HeaderFilter {

    // Headers to ignore - common infrastructure and browser headers
    private static final Set<String> IGNORED_HEADERS = new HashSet<>(Arrays.asList(
        "user-agent",
        "x-forwarded-proto",
        "accept-encoding",
        "date",
        "accept",
        "cache-control",
        "connection",
        "pragma",
        "akamai-origin-hop",
        "x-akamai-config-log-detail",
        "cdn-loop",
        "accept-language",
        "etag",
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

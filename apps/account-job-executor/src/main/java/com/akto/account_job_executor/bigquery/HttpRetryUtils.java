package com.akto.account_job_executor.bigquery;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility class for HTTP retry logic shared across BigQuery ingestion components.
 */
public final class HttpRetryUtils {
    private static final Set<Integer> RETRYABLE_HTTP_STATUS_CODES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(408, 429, 500, 502, 503, 504)));

    private HttpRetryUtils() {
    }

    public static boolean isRetryableHttpStatusCode(int statusCode) {
        return RETRYABLE_HTTP_STATUS_CODES.contains(statusCode);
    }
}

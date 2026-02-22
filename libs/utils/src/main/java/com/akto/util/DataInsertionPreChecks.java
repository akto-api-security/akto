package com.akto.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataInsertionPreChecks {

    private static final Logger logger = LoggerFactory.getLogger(DataInsertionPreChecks.class);
    
    /**
     * Check if a URL should be skipped for a specific account.
     * Currently skips URLs containing dots (.) or percent (%) for accountIds 1736798101 and 1718042191.
     * Also skips if response code is 302 for these accounts.
     *
     * @param accountId The account ID to check
     * @param url The URL to validate
     * @param responseCode The HTTP response code
     * @return true if the URL should be skipped, false otherwise
     */
     public static boolean shouldSkipUrl(int accountId, String url, int responseCode) {
        logger.debug("Checking URL for accountId {}: {} with response code {}", accountId, url, responseCode);

        // Skip for specific accounts if:
        // 1. URL contains dots or percent signs, OR
        // 2. Response code is 302 (redirect)
        boolean shouldSkip = (accountId == 1736798101 || accountId == 1718042191) && (
                (url != null && (url.contains(".") || url.contains("%")))
                || responseCode == 302
            );

        // if (shouldSkip) {
        //     logger.info("Skipping URL for accountId {}: {} with response code {} due to pre checks", accountId, url, responseCode);
        // }

        return shouldSkip;
    }

}
package com.akto.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataInsertionPreChecks {

    private static final Logger logger = LoggerFactory.getLogger(DataInsertionPreChecks.class);
    
    /**
     * Check if a URL should be skipped for a specific account.
     * Currently skips URLs containing dots for accountId 1000000.
     * 
     * @param accountId The account ID to check
     * @param url The URL to validate
     * @return true if the URL should be skipped, false otherwise
     */

     public static boolean shouldSkipUrl(int accountId, String url) {
        logger.debug("Checking URL for accountId {}: {}", accountId, url);

        // Skip URLs containing dots only for accountId 1736798101
        boolean shouldSkip = (accountId == 1736798101 || accountId == 1718042191) && url != null && url.contains(".");

        if (shouldSkip) {
            logger.info("Skipping URL for accountId {}: {} due to pre checks", accountId, url);
        }

        return shouldSkip;
    }
}
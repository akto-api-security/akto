package com.akto.util;

public class DataInsertionPreChecks {
    
    /**
     * Check if a URL should be skipped for a specific account.
     * Currently skips URLs containing dots for accountId 1000000.
     * 
     * @param accountId The account ID to check
     * @param url The URL to validate
     * @return true if the URL should be skipped, false otherwise
     */
    public static boolean shouldSkipUrl(int accountId, String url) {
        // Skip URLs containing dots only for accountId 1000000
        return accountId == 1736798101 && url != null && url.contains(".");
    }
}
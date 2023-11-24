package com.akto.util;

public class UsageUtils {
    
    public static String getUsageServiceUrl() {
        String usageServiceUrl = System.getenv("USAGE_SERVICE_URL");

        if (usageServiceUrl != null) {
            return usageServiceUrl;
        } else {
            return "https://usage.akto.com";
        }
    }

}

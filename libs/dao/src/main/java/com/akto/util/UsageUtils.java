package com.akto.util;

import java.util.concurrent.TimeUnit;

public class UsageUtils {

    public static final TimeUnit USAGE_CRON_PERIOD = TimeUnit.HOURS;
    public static final int USAGE_UPPER_BOUND_DL = 86400;
    
    public static String getUsageServiceUrl() {
        String usageServiceUrl = System.getenv("USAGE_SERVICE_URL");

        if (usageServiceUrl != null) {
            return usageServiceUrl;
        } else {
            return "https://usage.akto.com";
        }
    }

}

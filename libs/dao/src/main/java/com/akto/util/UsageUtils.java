package com.akto.util;

import java.util.concurrent.TimeUnit;

public class UsageUtils {

    public static final TimeUnit USAGE_CRON_PERIOD = TimeUnit.HOURS;
    public static final int USAGE_UPPER_BOUND_DL = 3600 * 4;

    public static String getUsageServiceUrl() {
        String usageServiceUrl = System.getenv("USAGE_SERVICE_URL");

        if (usageServiceUrl != null) {
            return usageServiceUrl;
        } else {
            return "https://saas.usage.akto.io";
        }
    }

    public static String getInternalServiceUrl() {
        String internalServiceUrl = System.getenv("INTERNAL_SERVICE_URL");

        if (internalServiceUrl != null) {
            return internalServiceUrl;
        } else {
            return "https://internal.akto.io";
        }
    }

}

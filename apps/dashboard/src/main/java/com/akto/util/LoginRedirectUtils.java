package com.akto.util;

// Where to send a user after login when they were bounced to the login page from a dashboard URL
public class LoginRedirectUtils {

    public static final String DEFAULT_LANDING_PATH = "/dashboard/observe/inventory";
    // Auth0 state key for the return path
    public static final String RETURN_PATH_STATE_KEY = "returnPath";

    private static final String DASHBOARD_PATH_PREFIX = "/dashboard/";
    private static final int MAX_RETURN_PATH_LENGTH = 2048;

    private LoginRedirectUtils() {}

    // Same-site dashboard paths only; anything else is dropped
    public static String safeReturnPath(String path) {
        if (path == null || path.length() > MAX_RETURN_PATH_LENGTH || !path.startsWith(DASHBOARD_PATH_PREFIX)) {
            return null;
        }
        if (path.indexOf('\\') >= 0 || path.indexOf('\r') >= 0 || path.indexOf('\n') >= 0) {
            return null;
        }
        return path;
    }

    public static String landingPath(String returnPath) {
        return returnPath != null ? returnPath : DEFAULT_LANDING_PATH;
    }
}

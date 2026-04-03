package com.akto.testing;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class HostDNSLookup {
    private static final Set<String> excludeIPs;
    private static final ConcurrentHashMap<String, Boolean> validationCache = new ConcurrentHashMap<>();

    static {
        excludeIPs = new HashSet<>();
        excludeIPs.add("10.0");
        excludeIPs.add("172.31");
        excludeIPs.add("0.0");
        excludeIPs.add("127.0");
    }

    public static boolean isRequestValid(String host) throws UnknownHostException {
        if (host == null || host.isEmpty()) {
            throw new UnknownHostException("empty host");
        }
        String cacheKey = host.trim().toLowerCase(Locale.ROOT);
        Boolean cached = validationCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        InetAddress address = InetAddress.getByName(host);
        String hostIP = address.getHostAddress();
        boolean valid = true;
        for (String excludedIp : excludeIPs) {
            if (hostIP.startsWith(excludedIp)) {
                valid = false;
                break;
            }
        }

        validationCache.put(cacheKey, valid);
        return valid;
    }
}

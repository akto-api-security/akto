package com.akto.testing;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;

public class HostDNSLookup {
    private static final Set<String> excludeIPs;

    static {
        excludeIPs = new HashSet<>();
        excludeIPs.add("10.0");
        excludeIPs.add("172.31");
        excludeIPs.add("0.0");
        excludeIPs.add("127.0");
    }

    public static boolean isRequestValid (String host) throws UnknownHostException {
        InetAddress address = InetAddress.getByName(host);
        String hostIP = address.getHostAddress();
        for (String excludedIp : excludeIPs) {
            if (hostIP.startsWith(excludedIp)) {
                return false;
            }
        }
        return true;
    }
}

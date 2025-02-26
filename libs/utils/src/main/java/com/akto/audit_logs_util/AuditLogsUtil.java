package com.akto.audit_logs_util;

import com.akto.runtime.policies.ApiAccessTypePolicy;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AuditLogsUtil {

    public static List<String> getClientIpAddresses(HttpServletRequest request) {
        List<String> headers = ApiAccessTypePolicy.CLIENT_IP_HEADERS;

        List<String> ipAddresses = new ArrayList<>();

        for (String header : headers) {
            String ips = request.getHeader(header);
            if (ips != null && !ips.isEmpty() && !"unknown".equalsIgnoreCase(ips)) {
                for (String ip : ips.split(",")) {
                    ipAddresses.add(ip.trim());
                }
                break;
            }
        }

        if (ipAddresses.isEmpty()) {
            String remoteIp = request.getRemoteAddr();
            if (remoteIp != null && !remoteIp.isEmpty()) {
                ipAddresses.add(remoteIp);
            }
        }

        return ipAddresses.isEmpty() ? Collections.singletonList("unknown") : ipAddresses;
    }
}

package com.akto.audit_logs_util;

import com.akto.util.Pair;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class AuditLogsUtil {

    public enum ClientType {
        JAVA, NODE, GOLANG, CPP, PYTHON, MOBILE, BROWSER, CLOUDFLARE, POSTMAN, CURL, CUSTOM
    }
    private static final List<Pair<Pattern, ClientType>> CLIENT_PATTERNS = new ArrayList<>();
    static {
        CLIENT_PATTERNS.add(new Pair<>(Pattern.compile("android|iPhone|iPad"), ClientType.MOBILE));
        CLIENT_PATTERNS.add(new Pair<>(Pattern.compile("Mozilla|Chrome|Safari|Firefox|Edg|AppleWebKit"), ClientType.BROWSER));
        CLIENT_PATTERNS.add(new Pair<>(Pattern.compile("PostmanRuntime"), ClientType.POSTMAN));
        CLIENT_PATTERNS.add(new Pair<>(Pattern.compile("curl"), ClientType.CURL));
        CLIENT_PATTERNS.add(new Pair<>(Pattern.compile("Apache-HttpClient|okhttp|unirest-java|Java"), ClientType.JAVA));
        CLIENT_PATTERNS.add(new Pair<>(Pattern.compile("node-fetch|axios|got|node"), ClientType.NODE));
        CLIENT_PATTERNS.add(new Pair<>(Pattern.compile("Go-http-client|golang"), ClientType.GOLANG));
        CLIENT_PATTERNS.add(new Pair<>(Pattern.compile("C\\+\\+|cpprestsdk"), ClientType.CPP));
        CLIENT_PATTERNS.add(new Pair<>(Pattern.compile("python-requests|urllib3"), ClientType.PYTHON));
        CLIENT_PATTERNS.add(new Pair<>(Pattern.compile("Cloudflare-Traffic-Manager"), ClientType.CLOUDFLARE));
    }

    public static ClientType findUserAgentType(String userAgentValue) {
        if (userAgentValue == null || userAgentValue.isEmpty()) {
            return ClientType.CUSTOM;
        }

        for (Pair<Pattern, ClientType> entry : CLIENT_PATTERNS) {
            if (entry.getFirst().matcher(userAgentValue).find()) {
                return entry.getSecond();
            }
        }

        return ClientType.CUSTOM;
    }

    public static List<String> getClientIpAddresses(HttpServletRequest request) {
        List<String> headers = Arrays.asList(
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_CLIENT_IP",
                "HTTP_X_FORWARDED_FOR"
        );

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

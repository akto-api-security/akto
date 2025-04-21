package com.akto.store;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StandardHeaders {
    public static Set<String> headers = new HashSet<>();

    private static void add(String value) {
        if (value == null) return;
        String cleanedValue = value.toLowerCase();
        cleanedValue = cleanedValue.trim();
        headers.add(cleanedValue);
    }

    public static boolean isStandardHeader(String value) {
        String cleanedValue = value.toLowerCase();
        cleanedValue = cleanedValue.trim();
        return headers.contains(cleanedValue);
    }

    static {
        add("A-IM");
        add("Accept");
        add("Accept-Charset");
        add("Accept-Datetime");
        add("Accept-Encoding");
        add("Accept-Language");
        add("Access-Control-Request-Method");
        add("Access-Control-Request-Headers");
        add("Cache-Control");
        add("Connection");
        add("Content-Encoding");
        add("Content-Length");
        add("Content-MD5");
        add("Content-Type");
        add("Cookie");
        add("Date");
        add("Expect");
        add("Forwarded");
        add("From");
        add("Host");
        add("HTTP2-Settings");
        add("If-Match");
        add("If-Modified-Since");
        add("If-None-Match");
        add("If-Range");
        add("If-Unmodified-Since");
        add("Max-Forwards");
        add("Origin");
        add("Pragma");
        add("Prefer");
        add("Proxy-Authorization");
        add("Range");
        add("Referer");
        add("TE");
        add("Trailer");
        add("Transfer-Encoding");
        add("User-Agent");
        add("Upgrade");
        add("Via");
        add("Warning");

        add("Upgrade-Insecure-Requests");
        add("X-Requested-With");
        add("DNT");
        add("X-Forwarded-For");
        add("X-Forwarded-Host");
        add("X-Forwarded-Proto");
        add("Front-End-Https");
        add("X-Http-Method-Override");
        add("X-ATT-DeviceId");
        add("X-Wap-Profile");
        add("Proxy-Connection");
        add("X-UIDH");
        add("X-Request-ID");
        add("X-Correlation-ID");
        add("Save-Data");

        // mpl
        add("sec-ch-ua");
        add("x-amzn-trace-id");

        // todo:
        add("idempotency-key");

    }

    public static void removeStandardAndAuthHeaders(Map<String, List<String>> headers, boolean isRequest) {
        Set<String> authRelatedHeaders = new HashSet<>(Arrays.asList(
            "authorization", "x-auth-token", "x-requested-with",
            "x-request-id", "x-correlation-id", "access-token", "token", "auth"
        ));

        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            try {
                String headerKey = entry.getKey().toLowerCase().trim();
                if (StandardHeaders.isStandardHeader(headerKey)) {
                    headers.remove(entry.getKey());
                }else if(isRequest && authRelatedHeaders.contains(headerKey.toLowerCase())) {
                    headers.remove(entry.getKey());
                }
            } catch (Exception e) {
                e.printStackTrace();
                // TODO: handle exception
            }
            
        }
    }
}

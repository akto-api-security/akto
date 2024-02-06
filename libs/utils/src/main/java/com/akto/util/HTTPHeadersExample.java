package com.akto.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class HTTPHeadersExample {

    public static Set<String> requestHeaders = new HashSet<>(
            Arrays.asList(
                    "upgrade-insecure-requests", "x-requested-with", "dnt", "x-forwarded-for", "x-forwarded-host",
                    "x-forwarded-proto", "front-end-https", "x-http-method-override", "x-att-deviceid", "x-wap-profile",
                    "proxy-connection", "x-uidh", "x-csrf-token", "x-request-id", "x-correlation-id", "correlation-id",
                    "save-data", "sec-gpc", "a-im", "accept", "accept-charset", "accept-datetime", "accept-encoding",
                    "accept-language", "access-control-request-method", "access-control-request-headers",
                    "cache-control", "connection", "content-encoding", "content-length", "content-md5", "content-type",
                    "date", "expect", "forwarded", "from", "host", "http2-settings", "if-match", "if-modified-since",
                    "if-none-match", "if-range", "if-unmodified-since", "max-forwards", "origin", "pragma", "prefer",
                    "proxy-authorization", "range", "referer [sic]", "te", "trailer", "transfer-encoding", "user-agent",
                    "upgrade", "via", "warning"
            )
    );

    public static Set<String> responseHeaders = new HashSet<>(
            Arrays.asList(
                    "content-security-policy", "x-content-security-policy", "x-webkit-csp", "expect-ct", "nel",
                    "permissions-policy", "refresh", "report-to", "status", "timing-allow-origin", "x-content-duration",
                    "x-content-type-options", "x-powered-by", "x-redirect-by", "x-request-id", "x-ua-compatible",
                    "x-xss-protection", "accept-ch", "access-control-allow-origin", "access-control-allow-credentials",
                    "access-control-expose-headers", "access-control-max-age", "access-control-allow-methods",
                    "access-control-allow-headers", "accept-patch", "accept-ranges", "age", "allow", "alt-svc", "cache-control",
                    "connection", "content-disposition", "content-encoding", "content-language", "content-length",
                    "content-location", "content-md5", "content-range", "content-type", "date", "delta-base", "etag",
                    "expires", "im", "last-modified", "link", "location", "p3p", "pragma", "preference-applied",
                    "proxy-authenticate", "public-key-pins", "retry-after", "server", "strict-transport-security", "trailer",
                    "transfer-encoding", "tk", "upgrade", "vary", "via", "warning", "www-authenticate", "x-frame-options"
            )
    );
}

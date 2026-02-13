package com.akto.filter;

import javax.servlet.*;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;

public class SecurityHeadersFilter implements Filter {

    private static final String CSP_HEADER_VALUE =
            "default-src 'self'; " +
            "script-src 'self' 'unsafe-inline' 'unsafe-eval' ajax.googleapis.com apis.google.com cdn.mxpnl.com clarity.ms widget.intercom.io app.getbeamer.com unpkg.com d1hvi6xs55woen.cloudfront.net; " +
            "style-src 'self' 'unsafe-inline' cdn.jsdelivr.net unpkg.com; " +
            "connect-src 'self' *.akto.io api.github.com api.bitbucket.org dev.azure.com gitlab.com cdn.mxpnl.com clarity.ms api-iam.intercom.io widget.intercom.io app.getbeamer.com d1hvi6xs55woen.cloudfront.net; " +
            "img-src 'self' data: blob:; " +
            "font-src 'self' data:; " +
            "frame-ancestors 'self'; " +
            "base-uri 'self'";

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse httpServletResponse = (HttpServletResponse) response;
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;

        if (!httpServletRequest.getRequestURI().startsWith("/tools/")) {
            httpServletResponse.addHeader("X-Frame-Options", "deny");
        }
        httpServletResponse.addHeader("X-XSS-Protection", "1");
        httpServletResponse.addHeader("X-Content-Type-Options", "nosniff");
        httpServletResponse.addHeader("cache-control", "no-cache, no-store, must-revalidate, pre-check=0, post-check=0");
        httpServletResponse.addHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");
        httpServletResponse.addHeader("Content-Security-Policy", CSP_HEADER_VALUE);

        chain.doFilter(request, new SameSiteCookieResponseWrapper(httpServletResponse));

    }

    /**
     * Response wrapper that adds SameSite=Lax attribute to all cookies.
     * Needed because Servlet 3.0 Cookie API does not support SameSite.
     */
    private static class SameSiteCookieResponseWrapper extends HttpServletResponseWrapper {
        public SameSiteCookieResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void addCookie(Cookie cookie) {
            StringBuilder sb = new StringBuilder();
            sb.append(cookie.getName()).append("=");
            if (cookie.getValue() != null) {
                sb.append(cookie.getValue());
            }
            if (cookie.getPath() != null) {
                sb.append("; Path=").append(cookie.getPath());
            }
            if (cookie.getDomain() != null) {
                sb.append("; Domain=").append(cookie.getDomain());
            }
            if (cookie.getMaxAge() >= 0) {
                sb.append("; Max-Age=").append(cookie.getMaxAge());
            }
            if (cookie.getSecure()) {
                sb.append("; Secure");
            }
            if (cookie.isHttpOnly()) {
                sb.append("; HttpOnly");
            }
            sb.append("; SameSite=Lax");
            addHeader("Set-Cookie", sb.toString());
        }
    }

    @Override
    public void destroy() {
    }
}

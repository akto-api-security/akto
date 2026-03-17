package com.akto.filter;

import com.akto.util.DashboardMode;

import javax.servlet.*;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.servlet.http.HttpSession;
import java.io.IOException;

public class SecurityHeadersFilter implements Filter {

    private static final boolean IS_SAAS = DashboardMode.isSaasDeployment();

    private static final String CSP_HEADER_VALUE =
            "default-src 'self'; " +
            "script-src 'self' 'unsafe-inline' 'unsafe-eval' " +
                "ajax.googleapis.com apis.google.com " +       // Google
                "*.intercom.io *.intercomcdn.com " +            // Intercom
                "cdn.mxpnl.com *.clarity.ms " +                 // Analytics
                "unpkg.com d1hvi6xs55woen.cloudfront.net " +    // CDNs
                "*.getbeamer.com js.stripe.com; " +             // Third-party
            "style-src 'self' 'unsafe-inline' cdn.jsdelivr.net unpkg.com *.getbeamer.com; " +
            "connect-src 'self' " +
                "*.akto.io " +                                   // Akto
                "*.intercom.io wss://*.intercom.io " +           // Intercom (HTTPS + WebSocket)
                "cdn.mxpnl.com *.mixpanel.com *.clarity.ms " +  // Analytics
                "cdn.jsdelivr.net d1hvi6xs55woen.cloudfront.net *.highcharts.com " + // CDNs
                "*.getbeamer.com *.stigg.io *.api.stigg.io; " + // Third-party
            "frame-src js.stripe.com *.getbeamer.com; " +        // Stripe + Beamer iframes
            "img-src 'self' data: blob: www.google.com *.youtube.com *.getbeamer.com *.intercomcdn.com d1hvi6xs55woen.cloudfront.net; " +
            "font-src 'self' data: fonts.googleapis.com fonts.gstatic.com cdn.jsdelivr.net d1hvi6xs55woen.cloudfront.net; " +
            "frame-ancestors 'self'; " +
            "base-uri 'self'";

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse httpServletResponse = (HttpServletResponse) response;
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;

        // Extend session timeout for on-prem deployments (60 min vs 30 min for SaaS)
        if (!IS_SAAS) {
            HttpSession session = httpServletRequest.getSession(false);
            if (session != null && session.getMaxInactiveInterval() == 1800) { // 30 minutes in seconds
                session.setMaxInactiveInterval(3600); // 60 minutes in seconds
            }
        }

        if (!httpServletRequest.getRequestURI().startsWith("/tools/")) {
            httpServletResponse.addHeader("X-Frame-Options", "deny");
        }
        httpServletResponse.addHeader("X-XSS-Protection", "1");
        httpServletResponse.addHeader("X-Content-Type-Options", "nosniff");
        httpServletResponse.addHeader("cache-control", "no-cache, no-store, must-revalidate, pre-check=0, post-check=0");

        // Apply stricter security headers only for SaaS deployments
        if (IS_SAAS) {
            httpServletResponse.addHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");
            httpServletResponse.addHeader("Content-Security-Policy", CSP_HEADER_VALUE);
            chain.doFilter(request, new SameSiteCookieResponseWrapper(httpServletResponse));
        } else {
            chain.doFilter(request, response);
        }

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

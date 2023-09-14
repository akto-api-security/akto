package com.akto.filter;

import com.akto.utils.HttpUtils;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;


public class SecurityHeadersFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse httpServletResponse = (HttpServletResponse) response;
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;

        if (!httpServletRequest.getRequestURI().startsWith("/tools/")) {
            httpServletResponse.addHeader("Content-Security-Policy", "frame-ancestors https://*");
        }
        httpServletResponse.addHeader("X-XSS-Protection", "1");
        httpServletResponse.addHeader("X-Content-Type-Options", "nosniff");
        httpServletResponse.addHeader("cache-control", "no-cache, no-store, must-revalidate, pre-check=0, post-check=0");

        if (HttpUtils.isHttpsEnabled()) {
            httpServletResponse.addHeader("strict-transport-security","max-age=31536000; includeSubDomains; preload");
        }

        chain.doFilter(request, response);

    }

    @Override
    public void destroy() {
    }
}

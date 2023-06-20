package com.akto.filter;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.akto.utils.HttpUtils;

import java.io.IOException;


public class SecurityHeadersFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    void copyHeader(String headerKey, ServletRequest request, HttpServletResponse httpServletResponse) {
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        if (httpServletRequest != null) {
            String val = httpServletRequest.getHeader(headerKey);
            if (val == null) {
                val = "null";
            }
            httpServletResponse.addHeader(headerKey, val);
        }

    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse httpServletResponse = (HttpServletResponse) response;

        httpServletResponse.addHeader("X-Frame-Options", "deny");
        httpServletResponse.addHeader("X-XSS-Protection", "1");
        httpServletResponse.addHeader("X-Content-Type-Options", "nosniff");
        httpServletResponse.addHeader("cache-control", "no-cache, no-store, must-revalidate, pre-check=0, post-check=0");

        copyHeader("User-Agent", request, httpServletResponse);
        copyHeader("X-Amzn-Trace-Id", request, httpServletResponse);

        if (HttpUtils.isHttpsEnabled()) {
            httpServletResponse.addHeader("strict-transport-security","max-age=31536000; includeSubDomains; preload");
        }

        chain.doFilter(request, response);

    }

    @Override
    public void destroy() {
    }
}

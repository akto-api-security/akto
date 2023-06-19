package com.akto.filter;

import com.akto.utils.HttpUtils;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;


public class SecurityHeadersFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse httpServletResponse = (HttpServletResponse) response;
        HttpServletRequest httpServletRequest= (HttpServletRequest) request;
        HttpSession session = httpServletRequest.getSession();
        String user = (String) session.getAttribute("username");

//        if (!RuntimeListener.ANONYMOUS_EMAIL.equals(user)) {
//            httpServletResponse.addHeader("X-Frame-Options", "deny");
//        }
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

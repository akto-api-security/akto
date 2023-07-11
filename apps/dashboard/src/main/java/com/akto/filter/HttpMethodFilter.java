package com.akto.filter;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class HttpMethodFilter implements Filter {
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest= (HttpServletRequest) request;
        HttpServletResponse httpServletResponse = (HttpServletResponse) response;
        String method = httpServletRequest.getMethod();
        if (method == null) {
            httpServletResponse.sendError(404);
            return ;
        }

        if (method.equalsIgnoreCase("OPTIONS")) {
            httpServletResponse.sendError(204);
            return;
        }

        if (!method.equalsIgnoreCase("POST") && !method.equalsIgnoreCase("GET")) {
            httpServletResponse.sendError(404);
            return ;
        }

        chain.doFilter(httpServletRequest, httpServletResponse);
    }

    @Override
    public void destroy() {
    }
}

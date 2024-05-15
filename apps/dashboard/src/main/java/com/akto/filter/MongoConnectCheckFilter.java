package com.akto.filter;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.akto.listener.InitializerListener;

public class MongoConnectCheckFilter implements Filter {
    
    @Override
    public void init(FilterConfig filterConfig) { }

    @Override
    public void destroy() {

    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {

        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        String uri = httpServletRequest.getRequestURI();
        String mongoErrorString = "/mongo-error";
        boolean isMongoErrorPath = uri.equals(mongoErrorString);
        boolean shouldFilter = !(uri.startsWith("/public")  || uri.startsWith("/metrics"));

        if (shouldFilter) {
            if (!InitializerListener.connectedToMongo && !isMongoErrorPath) {
                ((HttpServletResponse) response).sendRedirect(mongoErrorString);
                return;
            } else if (InitializerListener.connectedToMongo && isMongoErrorPath) {
                ((HttpServletResponse) response).sendRedirect("/login");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}

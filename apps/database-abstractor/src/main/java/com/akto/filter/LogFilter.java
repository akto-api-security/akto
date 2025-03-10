package com.akto.filter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import com.eclipse.jetty.server.Request;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.dao.context.Context;

public class LogFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(LogFilter.class);

    @Override
    public void init(FilterConfig filterConfig) { }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        long start = System.currentTimeMillis();
        chain.doFilter(request, response);

        try {

            
            
            String uri = httpServletRequest.getRequestURI();

            if (uri.contains("?")) {
                uri = uri.substring(0, uri.indexOf("?"));
            }

            String method = httpServletRequest.getMethod();
            String accountId = ""+Context.accountId.get();

            String ip = httpServletRequest.getHeader("X-Forwarded-For");
            if (ip != null) {
                String[] ipList = ip.split(",");
                ip = ipList[ipList.length - 1];
            } else {
                ip = httpServletRequest.getRemoteAddr();
            }

            Map<String, Object> result = new HashMap<>();
            result.put("accountId", accountId);
            result.put("ip", ip);
            result.put("url", method + " " + uri);
            result.put("duration", System.currentTimeMillis()-start);
            result.put("req_size", request.getContentLength());
            // result.put("resp_size", httpServletResponse.get);
            // result.put("accountId", accountId);
            // result.put("accountId", accountId);
            // result.put("accountId", accountId);
            // result.put("accountId", accountId);


            logger.info(result.toString());

        } catch (Exception e) {
            logger.error("Error: ", e);
        }

    }

    @Override
    public void destroy() { }
}

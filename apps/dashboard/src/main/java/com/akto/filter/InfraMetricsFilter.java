package com.akto.filter;

import com.akto.listener.InfraMetricsListener;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tag;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InfraMetricsFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(InfraMetricsFilter.class);

    @Override
    public void init(FilterConfig filterConfig) { }

    @Override
    public void destroy() {

    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {
        filterChain.doFilter(request, response);
        try {
            HttpServletResponse httpServletResponse = (HttpServletResponse) response;
            HttpServletRequest  httpServletRequest = (HttpServletRequest) request;

            Enumeration<String> headers = httpServletRequest.getHeaderNames();
            System.out.println("\n***********");
            while (headers.hasMoreElements()) {
                System.out.println(headers.nextElement());
            }
            System.out.println("*************");

            int statusCode = httpServletResponse.getStatus();
            String uri = httpServletRequest.getRequestURI();
            String method = httpServletRequest.getMethod();

            ArrayList<Tag> tags = new ArrayList<>(Arrays.asList(
                    Tag.of("uri", uri),
                    Tag.of("method", method)
            ));

            if (statusCode >= 200 && statusCode< 300) {
                tags.add(Tag.of("status", "good"));
            } else {
                tags.add(Tag.of("status", "bad"));
            }

            Counter.builder("api_requests_total")
                    .description("API Requests Total")
                    .tags(tags)
                    .register(InfraMetricsListener.registry)
                    .increment();
        } catch (Exception e) {
            logger.error("Inframetrics filter Error: ", e);;
        }

    }

}

package com.akto.filter;

import com.akto.listener.InfraMetricsListener;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class InfraMetricsFilter implements Filter {
    @Override
    public void init(FilterConfig filterConfig) { }

    @Override
    public void destroy() {

    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {
        long start = System.currentTimeMillis();

        filterChain.doFilter(request, response);
        HttpServletResponse httpServletResponse = (HttpServletResponse) response;
        HttpServletRequest  httpServletRequest = (HttpServletRequest) request;

        int statusCode = httpServletResponse.getStatus();
        long duration = System.currentTimeMillis() - start;
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

    }

}

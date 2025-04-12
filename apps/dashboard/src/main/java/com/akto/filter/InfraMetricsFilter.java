package com.akto.filter;

import com.akto.listener.InfraMetricsListener;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class InfraMetricsFilter implements Filter {

    private static final LoggerMaker loggerMaker = new LoggerMaker(InfraMetricsFilter.class, LogDb.DASHBOARD);

    @Override
    public void init(FilterConfig filterConfig) { }

    @Override
    public void destroy() {

    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {
        long start = System.currentTimeMillis();
        filterChain.doFilter(request, response);
        long end = System.currentTimeMillis();
        long duration = end - start;

        try {
            HttpServletResponse httpServletResponse = (HttpServletResponse) response;
            HttpServletRequest  httpServletRequest = (HttpServletRequest) request;

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

            Timer.builder("api_response_time_90")
                    .description("API Response Time 90th Percentile")
                    .tags(tags)
                    .publishPercentileHistogram()
                    .publishPercentiles(0.9)
                    .register(InfraMetricsListener.registry)
                    .record(duration, TimeUnit.MILLISECONDS);

            Timer.builder("api_response_time_75")
                    .description("API Response Time 75th Percentile")
                    .tags(tags)
                    .publishPercentileHistogram()
                    .publishPercentiles(0.75)
                    .register(InfraMetricsListener.registry)
                    .record(duration, TimeUnit.MILLISECONDS);

            Timer.builder("api_response_time_50")
                    .description("API Response Time 50th Percentile")
                    .tags(tags)
                    .publishPercentileHistogram()
                    .publishPercentiles(0.50)
                    .register(InfraMetricsListener.registry)
                    .record(duration, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, String.format("Inframetrics filter Error: %s", e.toString()), LogDb.DASHBOARD);
        }

    }

}

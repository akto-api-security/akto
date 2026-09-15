package com.akto.filter;

import com.akto.dao.context.Context;
import com.akto.listener.InfraMetricsListener;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.metrics.MetricLabelBuilder;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
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
            // templatize the path so ids do not explode metric cardinality
            String uri = MetricLabelBuilder.templatize(httpServletRequest.getRequestURI());
            String method = httpServletRequest.getMethod();

            // Account id is set by UserDetailsFilter, which wraps this filter in the chain,
            // so the ThreadLocal is still populated here (metric is recorded before the
            // outer filter's finally clears it). Fall back to "unknown" for unauthenticated
            // requests so the label stays present and bounded.
            Integer accountIdValue = Context.accountId.get();
            String accountId = accountIdValue == null ? "unknown" : accountIdValue.toString();

            // OpenTelemetry HTTP server semantic-convention label names. The Prometheus
            // registry renders the dotted keys as underscores (http_route, etc.).
            ArrayList<Tag> tags = new ArrayList<>(Arrays.asList(
                    // templatized route, not the raw path, to keep cardinality bounded
                    Tag.of("http.route", uri),
                    Tag.of("http.request.method", method),
                    // real HTTP status code (bounded set) instead of a good/bad collapse
                    Tag.of("http.response.status_code", Integer.toString(statusCode)),
                    // tenant dimension; cardinality scales with active account count
                    Tag.of("account.id", accountId)
            ));

            // Single histogram named per the OTel/Micrometer convention. Micrometer
            // appends the base unit, publishing http_server_request_duration_seconds with
            // _count (request total), _sum and _bucket{le=...} series; Prometheus derives
            // request rates and quantiles from these, so no separate request counter and
            // no client-side percentiles are needed.
            Timer.builder("http.server.request.duration")
                    .description("HTTP server request duration")
                    .tags(tags)
                    .serviceLevelObjectives(
                            Duration.ofMillis(25), Duration.ofMillis(50), Duration.ofMillis(100),
                            Duration.ofMillis(250), Duration.ofMillis(500), Duration.ofMillis(1000),
                            Duration.ofMillis(2500), Duration.ofMillis(5000), Duration.ofMillis(10000))
                    .register(InfraMetricsListener.registry)
                    .record(duration, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, String.format("Inframetrics filter Error: %s", e.toString()), LogDb.DASHBOARD);
        }

    }

}

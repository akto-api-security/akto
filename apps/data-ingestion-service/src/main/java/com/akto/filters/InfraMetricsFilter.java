package com.akto.filters;

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

// Records an HTTP server request-duration histogram for every /api/* request into the
// shared Prometheus registry. Mirrors the dashboard's InfraMetricsFilter (same metric name,
// OTel label conventions and bucket boundaries) so dashboards/alerts port across services.
public class InfraMetricsFilter implements Filter {

    private static final LoggerMaker loggerMaker = new LoggerMaker(InfraMetricsFilter.class, LogDb.DATA_INGESTION);

    @Override
    public void init(FilterConfig filterConfig) { }

    @Override
    public void destroy() { }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {
        long start = System.currentTimeMillis();
        filterChain.doFilter(request, response);
        long duration = System.currentTimeMillis() - start;

        try {
            HttpServletResponse httpServletResponse = (HttpServletResponse) response;
            HttpServletRequest httpServletRequest = (HttpServletRequest) request;

            int statusCode = httpServletResponse.getStatus();
            // templatize the path so ids do not explode metric cardinality
            String uri = MetricLabelBuilder.templatize(httpServletRequest.getRequestURI());
            String method = httpServletRequest.getMethod();

            // Context.accountId is populated by AuthFilter only when AKTO_DI_AUTHENTICATE=true
            // and the JWT validates. Auth is skipped by default in this service, so most
            // requests legitimately fall back to "unknown"; the label stays present and bounded
            // rather than being dropped. This is expected, not a bug.
            Integer accountIdValue = Context.accountId.get();
            String accountId = accountIdValue == null ? "unknown" : accountIdValue.toString();

            // OpenTelemetry HTTP server semantic-convention label names. The Prometheus
            // registry renders the dotted keys as underscores (http_route, etc.).
            ArrayList<Tag> tags = new ArrayList<>(Arrays.asList(
                    Tag.of("http.route", uri),
                    Tag.of("http.request.method", method),
                    Tag.of("http.response.status_code", Integer.toString(statusCode)),
                    Tag.of("account.id", accountId)
            ));

            // Single histogram, Micrometer appends the base unit -> publishes
            // http_server_request_duration_seconds with _count/_sum/_bucket{le=...}.
            Timer.builder("http.server.request.duration")
                    .description("HTTP server request duration")
                    .tags(tags)
                    // Bucket boundaries: 100ms, 600ms, 1s, 3s, 6s, 10s (plus +Inf).
                    .serviceLevelObjectives(
                            Duration.ofMillis(100), Duration.ofMillis(600), Duration.ofMillis(1000),
                            Duration.ofMillis(3000), Duration.ofMillis(6000), Duration.ofMillis(10000))
                    .register(InfraMetricsListener.registry)
                    .record(duration, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, String.format("Inframetrics filter Error: %s", e.toString()), LogDb.DATA_INGESTION);
        }
    }
}

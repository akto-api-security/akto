package com.akto.filters;

import com.akto.dao.context.Context;
import com.akto.listener.InfraMetricsListener;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.metrics.MetricLabelBuilder;
import com.akto.utils.OperationalAlerts;

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

            // Prefer the per-request tenant (Context.accountId, set by AuthFilter when
            // AKTO_DI_AUTHENTICATE=true). Auth is skipped by default, so fall back to the
            // deployment account parsed from DATABASE_ABSTRACTOR_SERVICE_TOKEN - the same source
            // the outbound-client metric uses - instead of "unknown", so both metrics agree.
            Integer accountIdValue = Context.accountId.get();
            String accountId = accountIdValue != null
                    ? accountIdValue.toString()
                    : OperationalAlerts.deploymentAccountId();

            // Same tag convention as the outbound-client metric (method/uri/status/account_id)
            // so server and client metrics read the same way.
            ArrayList<Tag> tags = new ArrayList<>(Arrays.asList(
                    Tag.of("method", method),
                    Tag.of("uri", uri),
                    Tag.of("status", Integer.toString(statusCode)),
                    Tag.of("account.id", accountId)
            ));

            // Single histogram, Micrometer appends the base unit -> publishes
            // akto_http_server_requests_seconds with _count/_sum/_bucket{le=...}. Named
            // symmetrically with akto.http.client.requests; buckets match the client layout.
            Timer.builder("akto.http.server.requests")
                    .description("HTTP server request duration")
                    .tags(tags)
                    // Bucket boundaries: 50ms, 200ms, 500ms, 1s, 3s, 5s, 10s (plus +Inf).
                    .serviceLevelObjectives(
                            Duration.ofMillis(50), Duration.ofMillis(200), Duration.ofMillis(500),
                            Duration.ofMillis(1000), Duration.ofMillis(3000), Duration.ofMillis(5000),
                            Duration.ofMillis(10000))
                    .register(InfraMetricsListener.registry)
                    .record(duration, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, String.format("Inframetrics filter Error: %s", e.toString()), LogDb.DATA_INGESTION);
        }
    }
}

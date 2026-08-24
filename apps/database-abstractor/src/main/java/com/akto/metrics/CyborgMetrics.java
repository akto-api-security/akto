package com.akto.metrics;

import com.akto.listener.InfraMetricsListener;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Central facade for cyborg's Prometheus request metrics. All metric names, tags, and registry
 * wiring live here; callers just pass the tag values and the measurements.
 *
 * Note on cardinality: account_id multiplies the series count (uri x method x status x account).
 * That is intentional here (per-tenant visibility) but keep an eye on it in large multi-tenant
 * deployments — series count grows with the number of distinct accountIds seen.
 */
public class CyborgMetrics {

    public static final String UNKNOWN = "unknown";

    private CyborgMetrics() {
    }

    /**
     * Record one HTTP request: count, latency, and (when known) request body size.
     *
     * @param uri          request URI (bounded /api/<action> set)
     * @param method       HTTP method
     * @param status       HTTP status code as string
     * @param accountId    caller account id (use CyborgMetrics.UNKNOWN when absent)
     * @param durationMs   request duration in milliseconds
     * @param requestBytes request body size; &lt; 0 means unknown (chunked / no body) and is not recorded
     */
    public static void recordHttpRequest(String uri, String method, String status,
                                         String accountId, long durationMs, int requestBytes) {
        List<Tag> tags = Arrays.asList(
                Tag.of("uri", uri),
                Tag.of("method", method),
                Tag.of("status", status),
                Tag.of("account_id", accountId == null || accountId.isEmpty() ? UNKNOWN : accountId)
        );

        Counter.builder("http_requests_total")
                .description("Total HTTP requests")
                .tags(tags)
                .register(InfraMetricsListener.registry)
                .increment();

        Timer.builder("http_request_duration")
                .description("HTTP request duration")
                .tags(tags)
                .publishPercentileHistogram()
                .publishPercentiles(0.5, 0.9, 0.99)
                .register(InfraMetricsListener.registry)
                .record(durationMs, TimeUnit.MILLISECONDS);

        if (requestBytes >= 0) {
            DistributionSummary.builder("http_request_size_bytes")
                    .description("HTTP request body size in bytes")
                    .baseUnit("bytes")
                    .tags(tags)
                    .register(InfraMetricsListener.registry)
                    .record(requestBytes);
        }
    }
}

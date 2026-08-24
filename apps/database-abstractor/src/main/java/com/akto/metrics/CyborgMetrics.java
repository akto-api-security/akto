package com.akto.metrics;

import com.akto.listener.InfraMetricsListener;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Central facade for cyborg's Prometheus request metrics. All metric names, tags, and registry
 * wiring live here; callers just pass the tag values and the measurements.
 *
 * Cardinality discipline:
 *  - account_id is attached ONLY to the request counter (1 series per combo). It is NOT put on the
 *    latency histogram or the size summary — those carry ~10 buckets each and would multiply into
 *    millions of series once tenants are added.
 *  - The latency histogram uses ~10 explicit SLO buckets (not Micrometer's ~69-bucket preset), and
 *    no client-side percentiles (those are per-instance and not aggregatable across pods).
 */
public class CyborgMetrics {

    public static final String UNKNOWN = "unknown";

    // Explicit, meaningful latency buckets (5ms..5s). Replaces publishPercentileHistogram()'s
    // ~69 auto buckets. Grafana computes p50/p90/p99 via histogram_quantile() over these.
    private static final Duration[] LATENCY_SLOS = new Duration[] {
            Duration.ofMillis(5), Duration.ofMillis(10), Duration.ofMillis(25),
            Duration.ofMillis(50), Duration.ofMillis(100), Duration.ofMillis(250),
            Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofMillis(2500),
            Duration.ofSeconds(5)
    };

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
        // Low-cardinality base tags shared by every request metric.
        List<Tag> baseTags = Arrays.asList(
                Tag.of("uri", uri),
                Tag.of("method", method),
                Tag.of("status", status)
        );
        String account = accountId == null || accountId.isEmpty() ? UNKNOWN : accountId;

        // Counter carries account_id (cheap: 1 series per combo) for per-tenant traffic/error views.
        Counter.builder("http_requests_total")
                .description("Total HTTP requests")
                .tags(baseTags)
                .tag("account_id", account)
                .register(InfraMetricsListener.registry)
                .increment();

        // Histogram stays tenant-agnostic: base tags + bounded SLO buckets, no client-side percentiles.
        Timer.builder("http_request_duration")
                .description("HTTP request duration")
                .tags(baseTags)
                .serviceLevelObjectives(LATENCY_SLOS)
                .minimumExpectedValue(Duration.ofMillis(5))
                .maximumExpectedValue(Duration.ofSeconds(5))
                .register(InfraMetricsListener.registry)
                .record(durationMs, TimeUnit.MILLISECONDS);

        // Size summary: count/sum/max only (no buckets), base tags.
        if (requestBytes >= 0) {
            DistributionSummary.builder("http_request_size_bytes")
                    .description("HTTP request body size in bytes")
                    .baseUnit("bytes")
                    .tags(baseTags)
                    .register(InfraMetricsListener.registry)
                    .record(requestBytes);
        }
    }
}

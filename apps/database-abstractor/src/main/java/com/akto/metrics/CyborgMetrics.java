package com.akto.metrics;

import com.akto.listener.InfraMetricsListener;
import io.micrometer.core.instrument.Counter;
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
 * Cardinality discipline: account_id is attached ONLY to the request counter (1 series per combo);
 * the latency histogram stays tenant-agnostic and uses explicit SLO buckets (25ms..5s), with no
 * client-side percentiles (Grafana computes them via histogram_quantile over the buckets).
 */
public class CyborgMetrics {

    public static final String UNKNOWN = "unknown";

    private static final Duration[] LATENCY_SLOS = new Duration[] {
            Duration.ofMillis(25), Duration.ofMillis(100), Duration.ofMillis(250),
            Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofMillis(2500),
            Duration.ofSeconds(5)
    };

    private CyborgMetrics() {
    }

    /**
     * Record one HTTP request: count (by uri/method/status/account_id) and latency (by uri/method/status).
     *
     * @param uri        request URI (bounded /api/<action> set)
     * @param method     HTTP method
     * @param status     HTTP status code as string
     * @param accountId  caller account id (use CyborgMetrics.UNKNOWN when absent)
     * @param durationMs request duration in milliseconds
     */
    public static void recordHttpRequest(String uri, String method, String status, String accountId, long durationMs) {
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
                .minimumExpectedValue(Duration.ofMillis(25))
                .maximumExpectedValue(Duration.ofSeconds(5))
                .register(InfraMetricsListener.registry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Record one OUTBOUND HTTP call cyborg made to another API (count + latency), tagged by target
     * host + path (never the query string). status is the HTTP code, or 0 when the call failed with
     * no response (connection error/timeout).
     */
    public static void recordOutboundHttpRequest(String host, String path, String method, int status, long durationMs) {
        // No account_id on outbound: it's a call to an external API, and the accountId on the calling
        // thread can be stale for background/async paths — a wrong tenant label is worse than none.
        List<Tag> tags = Arrays.asList(
                Tag.of("host", host),
                Tag.of("path", path),
                Tag.of("method", method),
                Tag.of("status", String.valueOf(status))
        );

        Counter.builder("http_client_requests_total")
                .description("Total outbound HTTP requests")
                .tags(tags)
                .register(InfraMetricsListener.registry)
                .increment();

        Timer.builder("http_client_request_duration")
                .description("Outbound HTTP request duration")
                .tags(tags)
                .serviceLevelObjectives(LATENCY_SLOS)
                .minimumExpectedValue(Duration.ofMillis(25))
                .maximumExpectedValue(Duration.ofSeconds(5))
                .register(InfraMetricsListener.registry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }
}

package com.akto.listener;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.DiskSpaceMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import com.akto.gateway.GuardrailsClient;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.io.File;
import java.time.Duration;

// Holds the process-wide Prometheus registry for data-ingestion-service and binds the
// JVM/system meters into it at startup. Mirrors the dashboard's InfraMetricsListener so the
// exposition format and metric names stay uniform across Akto services.
public class InfraMetricsListener implements ServletContextListener {

    public static PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    private static final LoggerMaker logger = new LoggerMaker(InfraMetricsListener.class, LogDb.DATA_INGESTION);

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            logger.debug("Infra metrics initializing.......");
            configureExternalHttpClientMetric(registry);

            // Bind to the global registry so meters emitted from bundled libraries (e.g.
            // akto-gateway's GuardrailsClient) via Metrics.* land in this scraped registry.
            Metrics.addRegistry(registry);
            new JvmThreadMetrics().bindTo(registry);
            new JvmGcMetrics().bindTo(registry);
            new JvmMemoryMetrics().bindTo(registry);
            new DiskSpaceMetrics(new File("/")).bindTo(registry);
            new ProcessorMetrics().bindTo(registry); // metrics related to the CPU stats
            new UptimeMetrics().bindTo(registry);
            logger.debug("Infra metrics initialized!!!!");
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "ERROR while setting up InfraMetricsListener", LogDb.DATA_INGESTION);
        }
    }

    // One common histogram-bucket layout for every outbound HTTP client. Prometheus renders these
    // as the le="..." bucket boundaries on akto_http_client_requests_seconds.
    private static final Duration[] HTTP_CLIENT_BUCKETS = {
            Duration.ofMillis(50), Duration.ofMillis(200), Duration.ofMillis(500), Duration.ofMillis(1000),
            Duration.ofMillis(3000), Duration.ofMillis(5000), Duration.ofMillis(10000)
    };

    /**
     * Shapes the shared outbound-HTTP-client metric (akto.http.client.requests) into a clean,
     * production-ready form. The OkHttp binder emits a plain Timer (no buckets) plus some noisy
     * default tags, so via MeterFilters we:
     *   - attach the common histogram buckets (the builder has no SLO option),
     *   - drop the per-connection target.* tags and the redundant "outcome" tag (success vs error
     *     is derivable from status at query time: 2xx ok, 4xx/5xx error, 0 no response),
     *   - keep status as the real HTTP response code (200/404/500...); only the no-response case
     *     (timeout/connection failure, where the binder emits "IO_ERROR") maps to "0".
     * Registered at startup, before the first call creates the meter, so it applies at instantiation.
     */
    private static void configureExternalHttpClientMetric(PrometheusMeterRegistry registry) {
        double[] bucketsNanos = new double[HTTP_CLIENT_BUCKETS.length];
        for (int i = 0; i < HTTP_CLIENT_BUCKETS.length; i++) {
            bucketsNanos[i] = HTTP_CLIENT_BUCKETS[i].toNanos();
        }

        registry.config()
                .meterFilter(MeterFilter.ignoreTags("target.scheme", "target.host", "target.port", "outcome"))
                .meterFilter(MeterFilter.replaceTagValues("status",
                        value -> isNumeric(value) ? value : "0"))
                .meterFilter(new MeterFilter() {
                    @Override
                    public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
                        if (GuardrailsClient.EXTERNAL_HTTP_CLIENT_METRIC.equals(id.getName())) {
                            return DistributionStatisticConfig.builder()
                                    .serviceLevelObjectives(bucketsNanos)
                                    .build()
                                    .merge(config);
                        }
                        return config;
                    }
                });
    }

    private static boolean isNumeric(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
    }
}

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
            // The OkHttp event listener that times guardrails calls creates a plain Timer, which
            // by itself emits only _count and _sum (no _bucket), so histogram_quantile() would not
            // work. Attach SLO buckets via a MeterFilter (the listener builder has no SLO option),
            // tuned to the guardrails 3s call timeout rather than the generic web buckets. Must be
            // registered before the first guardrails call creates the meter (startup is well before
            // any request), so the config is applied when the meter is instantiated.
            registry.config().meterFilter(new MeterFilter() {
                @Override
                public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
                    if ("akto.guardrails.validate".equals(id.getName())) {
                        return DistributionStatisticConfig.builder()
                                .serviceLevelObjectives(
                                        Duration.ofMillis(25).toNanos(), Duration.ofMillis(50).toNanos(),
                                        Duration.ofMillis(100).toNanos(), Duration.ofMillis(250).toNanos(),
                                        Duration.ofMillis(500).toNanos(), Duration.ofMillis(1000).toNanos(),
                                        Duration.ofMillis(2000).toNanos(), Duration.ofMillis(3000).toNanos())
                                .build()
                                .merge(config);
                    }
                    return config;
                }
            });

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

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
    }
}

package com.akto.listener;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.metrics.CyborgMetricsConfig;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmHeapPressureMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * Owns the Prometheus registry for the database-abstractor (cyborg) service and binds JVM/process
 * metrics on startup. Configuration lives in {@link CyborgMetricsConfig}; this class only wires the
 * registry and the JVM binders.
 *
 * FULLY OPT-IN: JVM binders are bound only when {@link CyborgMetricsConfig#isEnabled()} (the request
 * filter, Kafka/Mongo binders, and the endpoint are gated on the same switch elsewhere). When off,
 * nothing is registered and the feature is inert.
 *
 * The registry is configured once (static block, before any meter is created): common service tags
 * and a cardinality guard that caps distinct {@code uri} values on {@code http_*} metrics.
 */
public class InfraMetricsListener implements ServletContextListener {

    public static final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

    private static final LoggerMaker loggerMaker = new LoggerMaker(InfraMetricsListener.class, LogDb.DB_ABS);
    private static final Logger logger = LoggerFactory.getLogger(InfraMetricsListener.class);

    static {
        // service identity on every series. Deployments are told apart by Prometheus' own
        // job/instance labels, so no extra role tag is needed.
        registry.config().commonTags("service", "cyborg");
        // Anti-explosion guard: cap distinct uri values on http_* metrics; beyond the cap new uri
        // series are dropped rather than growing unbounded (e.g. an authenticated caller spraying
        // random /api/<x> paths). Meters without a uri tag (e.g. http_requests_in_flight) are unaffected.
        registry.config().meterFilter(MeterFilter.maximumAllowableTags(
                "http_", "uri", CyborgMetricsConfig.getMaxUriCardinality(), MeterFilter.deny()));
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        if (!CyborgMetricsConfig.isEnabled()) {
            logger.info("Prometheus metrics disabled (PROMETHEUS_METRICS_ENABLED != true). Skipping all collection.");
            return;
        }
        try {
            logger.info("Infra metrics initializing.......");
            // JVM health
            new JvmMemoryMetrics().bindTo(registry);          // heap/non-heap used, committed, max
            new JvmGcMetrics().bindTo(registry);              // gc pause times, allocations
            new JvmHeapPressureMetrics().bindTo(registry);    // gc overhead + memory-after-gc (leak signal)
            new JvmThreadMetrics().bindTo(registry);          // live/daemon/peak threads
            new ClassLoaderMetrics().bindTo(registry);        // classes loaded/unloaded (classloader leaks)
            // Process / OS
            new ProcessorMetrics().bindTo(registry);          // process + system CPU
            new FileDescriptorMetrics().bindTo(registry);     // open vs max fds (socket/connection leaks)
            new UptimeMetrics().bindTo(registry);             // uptime, start time
            logger.info("Infra metrics initialized!!!!");
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "ERROR while setting up InfraMetricsListener", LogDb.DB_ABS);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
    }
}

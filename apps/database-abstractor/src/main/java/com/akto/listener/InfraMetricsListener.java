package com.akto.listener;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmHeapPressureMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * Prometheus setup for the database-abstractor (cyborg) service. Kept independent of the existing
 * push-based metrics (AllMetrics / OpenTelemetry).
 *
 * FULLY OPT-IN: everything is gated on PROMETHEUS_METRICS_ENABLED (default false). When off, NOTHING
 * runs — no JVM binders, the request filter is a passthrough, the Kafka/Mongo binders are not
 * attached, and the /metrics endpoint 404s. A deployment that doesn't want metrics pays nothing and
 * carries no extra listeners. When on, collection runs and /metrics serves (auth via MetricsAuthFilter).
 *
 * Common tags (service=cyborg, role=$METRICS_SERVICE_ROLE) are applied in a static block so they
 * are set at class load, before any meter is registered.
 */
public class InfraMetricsListener implements ServletContextListener {

    public static final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

    // Master switch. Off unless PROMETHEUS_METRICS_ENABLED=true. Gates ALL collection AND the
    // endpoint; when false the whole feature is inert. volatile + a test seam only.
    private static volatile boolean ENABLED = "true".equalsIgnoreCase(System.getenv("PROMETHEUS_METRICS_ENABLED"));

    // Deployment role (api / consumer / fast-consumer); "unknown" when not set. Resolved once.
    private static final String ROLE = resolveRole();

    private static final LoggerMaker loggerMaker = new LoggerMaker(InfraMetricsListener.class, LogDb.DB_ABS);
    private static final Logger logger = LoggerFactory.getLogger(InfraMetricsListener.class);

    static {
        // service=cyborg is constant; role is set per Docker deployment (api / consumer / fast-consumer).
        registry.config().commonTags("service", "cyborg", "role", ROLE);
    }

    /** Master switch: when false the entire metrics feature (collection + endpoint) is inert. */
    public static boolean isEnabled() {
        return ENABLED;
    }

    // Test seam: env-derived static can't be driven from a unit test otherwise. Not for production use.
    public static void setEnabledForTest(boolean enabled) {
        ENABLED = enabled;
    }

    private static String resolveRole() {
        String role = System.getenv("METRICS_SERVICE_ROLE");
        if (role == null || role.trim().isEmpty()) {
            return "unknown";
        }
        return role.trim();
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        if (!ENABLED) {
            logger.info("Prometheus metrics disabled (PROMETHEUS_METRICS_ENABLED != true). Skipping all collection.");
            return;
        }
        try {
            logger.info("Infra metrics initializing.......");
            // JVM health
            new JvmMemoryMetrics().bindTo(registry);          // heap/non-heap used, committed, max
            new JvmGcMetrics().bindTo(registry);              // gc pause times, allocations
            new JvmHeapPressureMetrics().bindTo(registry);    // gc overhead + memory-after-gc (leak signal)
            new JvmThreadMetrics().bindTo(registry);          // live/daemon/blocked threads
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

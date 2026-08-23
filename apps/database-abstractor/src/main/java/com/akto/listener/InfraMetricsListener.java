package com.akto.listener;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.DiskSpaceMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.io.File;

/**
 * Prometheus setup for the database-abstractor (cyborg) service. Kept independent of the existing
 * push-based metrics (AllMetrics / OpenTelemetry).
 *
 * Metrics are always collected; the flag below only gates whether the scrape ENDPOINT serves them.
 * The /metrics endpoint returns data only when enabled AND authorized (see MetricsAuthFilter).
 *
 * Common tags (service=cyborg, role=$METRICS_SERVICE_ROLE) are applied in a static block so they
 * are set at class load, before any meter (JVM binders here, the Kafka binder at consumer startup,
 * or HTTP meters on first request) is ever registered.
 */
public class InfraMetricsListener implements ServletContextListener {

    public static final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

    // Endpoint gate only. Off unless PROMETHEUS_METRICS_ENABLED=true. Collection is always on;
    // this just controls whether /metrics serves the data. volatile + a test seam only.
    private static volatile boolean ENABLED = "true".equalsIgnoreCase(System.getenv("PROMETHEUS_METRICS_ENABLED"));

    // Deployment role (api / consumer / fast-consumer); "unknown" when not set. Resolved once.
    private static final String ROLE = resolveRole();

    private static final LoggerMaker loggerMaker = new LoggerMaker(InfraMetricsListener.class, LogDb.DB_ABS);
    private static final Logger logger = LoggerFactory.getLogger(InfraMetricsListener.class);

    static {
        // service=cyborg is constant; role is set per Docker deployment (api / consumer / fast-consumer).
        registry.config().commonTags("service", "cyborg", "role", ROLE);
    }

    /** Endpoint gate: /metrics serves data only when this is true. Collection is unaffected. */
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
        try {
            logger.info("Infra metrics initializing.......");
            new JvmThreadMetrics().bindTo(registry);
            new JvmGcMetrics().bindTo(registry);
            new JvmMemoryMetrics().bindTo(registry);
            new DiskSpaceMetrics(new File("/")).bindTo(registry);
            new ProcessorMetrics().bindTo(registry); // CPU stats
            new UptimeMetrics().bindTo(registry);
            logger.info("Infra metrics initialized!!!!");
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "ERROR while setting up InfraMetricsListener", LogDb.DB_ABS);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
    }
}

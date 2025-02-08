package com.akto.listener;

import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.DiskSpaceMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContextListener;
import java.io.File;

public class InfraMetricsListener implements ServletContextListener {
    public static PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    private static final LoggerMaker loggerMaker = new LoggerMaker(InfraMetricsListener.class);
    private static final Logger logger = LoggerFactory.getLogger(InfraMetricsListener.class);
    @Override
    public void contextInitialized(javax.servlet.ServletContextEvent sce) {

        try {
            logger.info("Infra metrics initializing.......");
            new JvmThreadMetrics().bindTo(registry);
            try (JvmGcMetrics jvmGcMetrics = new JvmGcMetrics()) {
                jvmGcMetrics.bindTo(registry);
            } catch (Exception e) {
                e.printStackTrace();
            }
            new JvmMemoryMetrics().bindTo(registry);
            new DiskSpaceMetrics(new File("/")).bindTo(registry);
            new ProcessorMetrics().bindTo(registry); // metrics related to the CPU stats
            new UptimeMetrics().bindTo(registry);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e,"ERROR while setting up InfraMetricsListener", LogDb.DASHBOARD);
        }


        logger.info("Infra metrics initialized!!!!");

    }
}

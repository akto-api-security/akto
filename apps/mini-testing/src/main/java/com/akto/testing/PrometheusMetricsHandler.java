package com.akto.testing;

import com.akto.log.LoggerMaker;
import io.prometheus.metrics.core.metrics.Gauge;
import io.prometheus.metrics.exporter.httpserver.HTTPServer;

import java.io.IOException;

public class PrometheusMetricsHandler {
    private static final boolean AUTO_SCALING_ENABLED = System.getenv("AUTO_SCALING_ENABLED") != null && Boolean.parseBoolean(System.getenv("AUTO_SCALING_ENABLED"));
    private static final int PROMETHEUS_SERVER_PORT = System.getenv("PROMETHEUS_SERVER_PORT") != null ? Integer.parseInt(System.getenv("PROMETHEUS_SERVER_PORT")) : 9400;
    private static final double MODULE_BUSY_VALUE = 1.0;
    private static final double MODULE_IDLE_VALUE = 0.0;
    private static final LoggerMaker loggerMaker = new LoggerMaker(PrometheusMetricsHandler.class, LoggerMaker.LogDb.TESTING);

    private static Gauge moduleBusy;
    private static HTTPServer server;

    public static void markModuleBusy() {
        if (AUTO_SCALING_ENABLED) {
            loggerMaker.info("setting isModuleBusy to 1.0");
            moduleBusy.set(MODULE_BUSY_VALUE);
        }
    }

    public static void markModuleIdle() {
        if (AUTO_SCALING_ENABLED) {
            loggerMaker.info("setting isModuleBusy to 0.0"); // todo: see if we need to remove this log as it would be called every second in while(true) loop
            moduleBusy.set(MODULE_IDLE_VALUE);
        }
    }

    public static boolean isModuleBusy() {
        return AUTO_SCALING_ENABLED && moduleBusy.get() == MODULE_BUSY_VALUE;
    }

    public static void shutdownServer() {
        if (AUTO_SCALING_ENABLED) {
            markModuleIdle();
            loggerMaker.infoAndAddToDb("Prometheus HTTPServer shutting down");
            if (server != null && server.getPort() > 0) {
                server.stop();
                loggerMaker.infoAndAddToDb("Prometheus HTTPServer shut down");
            } else {
                loggerMaker.infoAndAddToDb("Prometheus HTTPServer was not running");
            }
        }
    }

    public static void init() throws IOException {
        if (AUTO_SCALING_ENABLED) {
            initModuleBusy();
            markModuleIdle();
            initPrometheusServer();
        }

    }

    private static void initModuleBusy() {
        moduleBusy = Gauge.builder()
                .name("is_module_busy")
                .help("Denotes whether or not the module is busy with any test execution")
                .register();
    }

    private static void initPrometheusServer() throws IOException {
        loggerMaker.infoAndAddToDb("Prometheus HTTPServer starting on port: " + PROMETHEUS_SERVER_PORT);
        server = HTTPServer.builder()
                .port(PROMETHEUS_SERVER_PORT)
                .buildAndStart();

        loggerMaker.infoAndAddToDb("Prometheus HTTPServer started on port: " + server.getPort());
    }
}

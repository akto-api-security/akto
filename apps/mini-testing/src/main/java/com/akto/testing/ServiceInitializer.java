package com.akto.testing;

import io.prometheus.metrics.core.metrics.Gauge;
import io.prometheus.metrics.exporter.httpserver.HTTPServer;

import java.io.IOException;

public class ServiceInitializer {

    public static void init() throws InterruptedException, IOException {
        Gauge gauge = Gauge.builder()
                .name("my_gauge")
                .help("example gauge")
                .register();
        gauge.set(5.0);

//        Counter counter = Counter.builder()
//                .name("my_count_total")
//                .help("example counter")
//                .labelNames("status")
//                .register();
//
//        counter.labelValues("ok").inc();
//        counter.labelValues("ok").inc();
//        counter.labelValues("error").inc();

        HTTPServer server = HTTPServer.builder()
                .port(9400)
                .buildAndStart();

        System.out.println("HTTPServer listening on port http://localhost:" + server.getPort() + "/metrics");
        Thread.currentThread().join(); // sleep forever
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        init();
    }
}
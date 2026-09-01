package com.akto.listener;

import io.micrometer.core.instrument.Counter;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Guards the Micrometer/Prometheus scrape API used by InfraMetricsAction. Micrometer >=1.13 (the
 * Prometheus client 1.x) removed scrape(Writer); this asserts scrape(contentType) works and returns
 * valid Prometheus exposition text on the registry the app actually uses.
 */
public class InfraMetricsScrapeTest {

    @Test
    public void scrapeProducesPrometheusText() {
        Counter.builder("cyborg_scrape_test_total")
                .tags("k", "v")
                .register(InfraMetricsListener.registry)
                .increment();

        String out = InfraMetricsListener.registry.scrape("text/plain; version=0.0.4; charset=utf-8");

        assertNotNull(out);
        assertTrue("scrape output should contain the metric name", out.contains("cyborg_scrape_test_total"));
        assertTrue("scrape output should be Prometheus exposition format", out.contains("# TYPE"));
        // common tag applied in the static block must be present on emitted series.
        // The app stamps only "app" (from APP_NAME, default "unknown"); the fleet-level
        // "service" label is appended by the Prometheus scrape job, not the app.
        assertTrue("app tag should be present", out.contains("app=\"unknown\""));
    }
}

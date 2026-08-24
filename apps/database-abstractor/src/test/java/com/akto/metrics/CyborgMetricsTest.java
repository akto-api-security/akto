package com.akto.metrics;

import com.akto.listener.InfraMetricsListener;
import org.junit.Test;

import static org.junit.Assert.*;

public class CyborgMetricsTest {

    @Test
    public void recordHttpRequest_emitsAllSeriesWithAccountIdTag() {
        CyborgMetrics.recordHttpRequest("/api/fetchApiInfo", "POST", "200", "1000000", 12L, 345);

        String out = InfraMetricsListener.registry.scrape("text/plain; version=0.0.4; charset=utf-8");

        assertTrue("counter present", out.contains("http_requests_total"));
        assertTrue("latency present", out.contains("http_request_duration_seconds"));
        assertTrue("request size present", out.contains("http_request_size_bytes"));
        assertTrue("account_id tag present", out.contains("account_id=\"1000000\""));
        assertTrue("uri tag present", out.contains("uri=\"/api/fetchApiInfo\""));
    }

    @Test
    public void recordHttpRequest_nullAccountId_becomesUnknown() {
        CyborgMetrics.recordHttpRequest("/api/health", "GET", "200", null, 1L, -1);

        String out = InfraMetricsListener.registry.scrape("text/plain; version=0.0.4; charset=utf-8");

        assertTrue("null accountId maps to 'unknown'", out.contains("account_id=\"unknown\""));
    }

    @Test
    public void recordHttpRequest_negativeSize_skipsSizeMetricButKeepsCount() {
        CyborgMetrics.recordHttpRequest("/api/noBody", "GET", "204", "42", 3L, -1);

        String out = InfraMetricsListener.registry.scrape("text/plain; version=0.0.4; charset=utf-8");

        // count is still recorded for this uri...
        assertTrue(lineExists(out, "http_requests_total", "uri=\"/api/noBody\""));
        // ...but no request-size series should exist for it (Content-Length was unknown).
        assertFalse(lineExists(out, "http_request_size_bytes", "uri=\"/api/noBody\""));
    }

    private static boolean lineExists(String scrape, String metricPrefix, String tag) {
        for (String line : scrape.split("\n")) {
            if (line.startsWith(metricPrefix) && line.contains(tag)) {
                return true;
            }
        }
        return false;
    }
}

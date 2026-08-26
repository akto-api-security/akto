package com.akto.metrics;

import com.akto.listener.InfraMetricsListener;
import org.junit.Test;

import static org.junit.Assert.*;

public class CyborgMetricsTest {

    @Test
    public void recordHttpRequest_emitsCounterAndLatency_accountIdOnCounterOnly() {
        CyborgMetrics.recordHttpRequest("/api/fetchApiInfo", "POST", "200", "1000000", 12L);

        String out = InfraMetricsListener.registry.scrape("text/plain; version=0.0.4; charset=utf-8");

        assertTrue("counter present", out.contains("http_requests_total"));
        assertTrue("latency present", out.contains("http_request_duration_seconds"));
        assertTrue("uri tag present", out.contains("uri=\"/api/fetchApiInfo\""));
        // account_id lives ONLY on the counter, not on the latency histogram (cardinality).
        assertTrue("account_id on counter", lineExists(out, "http_requests_total", "account_id=\"1000000\""));
        assertFalse("account_id NOT on latency", lineExists(out, "http_request_duration_seconds", "account_id="));
    }

    @Test
    public void recordHttpRequest_nullAccountId_becomesUnknown() {
        CyborgMetrics.recordHttpRequest("/api/health", "GET", "200", null, 1L);

        String out = InfraMetricsListener.registry.scrape("text/plain; version=0.0.4; charset=utf-8");

        assertTrue("null accountId maps to 'unknown'", out.contains("account_id=\"unknown\""));
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

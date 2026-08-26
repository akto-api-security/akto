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

    @Test
    public void recordOutboundHttpRequest_emitsClientMetricsWithHostAndPath() {
        CyborgMetrics.recordOutboundHttpRequest("tbs.akto.io", "/api/threat_detection/record_malicious_event", "POST", 200, 15L);

        String out = InfraMetricsListener.registry.scrape("text/plain; version=0.0.4; charset=utf-8");

        assertTrue("client counter present", out.contains("http_client_requests_total"));
        assertTrue("client latency present", out.contains("http_client_request_duration_seconds"));
        assertTrue("host tag present", out.contains("host=\"tbs.akto.io\""));
        assertTrue("path tag present", out.contains("path=\"/api/threat_detection/record_malicious_event\""));
        assertFalse("no query in path", out.contains("?"));
        // outbound has no account_id (external call; thread accountId can be stale)
        assertFalse("no account_id on client counter", lineExists(out, "http_client_requests_total", "account_id="));
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

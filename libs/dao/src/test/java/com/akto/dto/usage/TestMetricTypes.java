package com.akto.dto.usage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class TestMetricTypes {

    @Test
    public void retiredMcpMetricIsNeverMeasured() {
        assertFalse(MetricTypes.measured().contains(MetricTypes.MCP_ASSET_COUNT));
    }

    @Test
    public void allOtherMetricsAreMeasured() {
        List<MetricTypes> measured = MetricTypes.measured();
        assertEquals(MetricTypes.values().length - 1, measured.size());
        assertTrue(measured.contains(MetricTypes.ACTIVE_ENDPOINTS));
        assertTrue(measured.contains(MetricTypes.AI_ASSET_COUNT));
        assertTrue(measured.contains(MetricTypes.ENDPOINT_ASSET_COUNT));
        assertTrue(measured.contains(MetricTypes.CUSTOM_TESTS));
        assertTrue(measured.contains(MetricTypes.TEST_RUNS));
        assertTrue(measured.contains(MetricTypes.ACTIVE_ACCOUNTS));
    }

    @Test
    public void storedMcpValueStillDecodes() {
        assertEquals(MetricTypes.MCP_ASSET_COUNT, MetricTypes.valueOf("MCP_ASSET_COUNT"));
    }
}

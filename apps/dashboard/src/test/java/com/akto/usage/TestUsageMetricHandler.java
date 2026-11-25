package com.akto.usage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.dto.usage.MetricTypes;

/**
 * The hourly usage cron records each metric on its own: a metric that cannot be measured must be
 * skipped (and counted), not abort the rest and not be reported as 0.
 */
public class TestUsageMetricHandler extends MongoBasedTest {

    @Test
    public void allMetricsRecordedWhenNothingFails() {
        List<MetricTypes> recorded = new ArrayList<>();
        List<MetricTypes> failed = UsageMetricHandler.recordEachMetric(
                Arrays.asList(MetricTypes.ACTIVE_ENDPOINTS, MetricTypes.AI_ASSET_COUNT), recorded::add, ACCOUNT_ID);

        assertTrue(failed.isEmpty());
        assertEquals(Arrays.asList(MetricTypes.ACTIVE_ENDPOINTS, MetricTypes.AI_ASSET_COUNT), recorded);
    }

    @Test
    public void oneFailingMetricDoesNotStopTheOthers() {
        List<MetricTypes> recorded = new ArrayList<>();
        List<MetricTypes> failed = UsageMetricHandler.recordEachMetric(
                Arrays.asList(MetricTypes.ACTIVE_ENDPOINTS, MetricTypes.AI_ASSET_COUNT, MetricTypes.ENDPOINT_ASSET_COUNT),
                metricType -> {
                    if (metricType == MetricTypes.AI_ASSET_COUNT) {
                        throw new IllegalStateException("db down");
                    }
                    recorded.add(metricType);
                }, ACCOUNT_ID);

        assertEquals(Collections.singletonList(MetricTypes.AI_ASSET_COUNT), failed);
        assertEquals(Arrays.asList(MetricTypes.ACTIVE_ENDPOINTS, MetricTypes.ENDPOINT_ASSET_COUNT), recorded);
    }

    @Test
    public void everyFailureIsReported() {
        List<MetricTypes> failed = UsageMetricHandler.recordEachMetric(
                Arrays.asList(MetricTypes.ACTIVE_ENDPOINTS, MetricTypes.ENDPOINT_ASSET_COUNT),
                metricType -> {
                    throw new IllegalStateException("boom");
                }, ACCOUNT_ID);

        assertEquals(Arrays.asList(MetricTypes.ACTIVE_ENDPOINTS, MetricTypes.ENDPOINT_ASSET_COUNT), failed);
    }
}

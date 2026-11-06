package com.akto.logpipeline;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.akto.dto.Log;
import com.akto.dto.bulk_updates.BulkUpdates;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class LogPipelineTest {

    /** Default (no LOG_PIPELINE_ACCOUNTS) must leave legacy behaviour untouched. */
    @Test
    public void disabledByDefault() {
        assertFalse(LogPipeline.isEnabled());
    }

    @Test
    public void accountListParsing() {
        // empty => nobody
        assertFalse(LogPipelineConfig.parseAll(""));
        assertTrue(LogPipelineConfig.parseIds("").isEmpty());
        // list => exactly those accounts; junk tokens are skipped
        assertTrue(LogPipelineConfig.parseIds("1000000, 1758595089,nginx").containsAll(Arrays.asList(1000000, 1758595089)));
        assertTrue(LogPipelineConfig.parseIds("1000000, 1758595089,nginx").size() == 2);
        assertFalse(LogPipelineConfig.parseAll("1000000,1758595089"));
        // star => everyone
        assertTrue(LogPipelineConfig.parseAll("*"));
        assertTrue(LogPipelineConfig.parseAll("1000000, *"));
    }

    /** No account is enabled by default, and no request context never counts as enabled. */
    @Test
    public void noAccountEnabledByDefault() {
        assertFalse(LogPipelineConfig.isEnabledFor(1000000));
        assertFalse(LogPipelineConfig.isEnabledFor(null));
    }

    /**
     * With no broker configured / reachable, publishing must never block or throw on the caller's
     * thread, however many records are published.
     */
    @Test(timeout = 5000)
    public void publishNeverBlocksOrThrowsWhenKafkaUnavailable() {
        Log log = new Log("hello", "key", 1);
        long start = System.nanoTime();
        for (int i = 0; i < 200_000; i++) {
            LogPipeline.publishLog(LogPipeline.RUNTIME_LOG, 1000000, log, i % 2 == 0);
        }
        Map<String, Object> filters = new HashMap<>();
        filters.put("_id.name", "x");
        BulkUpdates bu = new BulkUpdates(filters, new ArrayList<>(Collections.singletonList("{\"field\":\"count\",\"val\":1.0,\"op\":\"inc\"}")));
        for (int i = 0; i < 50_000; i++) {
            LogPipeline.publishTrafficMetrics(1000000, Collections.singletonList(bu));
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        // ~250k publishes should take well under a few seconds when nothing blocks.
        assertTrue("publish path blocked: " + elapsedMs + "ms", elapsedMs < 4000);
    }
}

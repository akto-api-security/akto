package com.akto.hybrid_runtime;

import com.akto.dao.context.Context;
import com.akto.dto.metrics.MetricData;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.metrics.AllMetrics;
import com.akto.runtime.parser.SampleParser;
import org.junit.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/**
 * Contract test for the traffic collector pipeline telemetry.
 *
 * collector-heartbeat.json is not handwritten: it is a real heartbeat captured off
 * the akto.daemonset.producer.heartbeats topic, produced by the Go daemonset's own
 * sendHeartbeatMessage against a live broker. Only the env block was replaced. If
 * the daemonset changes the payload shape, this test fails.
 */
public class AktoTrafficCollectorTelemetryTest {

    private static final String COLLECTOR_POD = "akto-tc:pod-e2e:node-e2e";

    private String loadHeartbeat() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("collector-heartbeat.json")) {
            assertNotNull("collector-heartbeat.json missing from test resources", in);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private AktoTrafficCollectorTelemetry newTelemetry() {
        return new AktoTrafficCollectorTelemetry(null, "test", 100, "test-topic", null, "mini-runtime-test");
    }

    private void invokeExtract(ModuleInfo heartbeat) throws Exception {
        Method m = AktoTrafficCollectorTelemetry.class
                .getDeclaredMethod("extractAndSendPipelineMetrics", ModuleInfo.class);
        m.setAccessible(true);
        m.invoke(newTelemetry(), heartbeat);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> pipelineMetrics() throws Exception {
        Field f = AllMetrics.class.getDeclaredField("tcPipelineMetrics");
        f.setAccessible(true);
        return (Map<String, Map<String, Object>>) f.get(AllMetrics.instance);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> lastSeen() throws Exception {
        Field f = AllMetrics.class.getDeclaredField("tcInstanceLastSeen");
        f.setAccessible(true);
        return (Map<String, Integer>) f.get(AllMetrics.instance);
    }

    /** Runs one flush cycle for a metric id and returns what would be sent to cyborg. */
    private List<MetricData> flush(String metricId) throws Exception {
        Map<String, Object> perInstance = pipelineMetrics().get(metricId);
        assertNotNull("no metrics recorded for " + metricId, perInstance);

        Method m = AllMetrics.class.getDeclaredMethod(
                "processAndCleanupTcMetrics", Map.class, String.class, String.class, List.class);
        m.setAccessible(true);
        List<MetricData> out = new ArrayList<>();
        m.invoke(AllMetrics.instance, perInstance, metricId, "test-org", out);
        return out;
    }

    private void clearState() throws Exception {
        pipelineMetrics().clear();
        lastSeen().clear();
    }

    /** The daemonset's real payload has to survive gson's double decode of additionalData. */
    @Test
    public void parsesRealCollectorHeartbeat() throws Exception {
        ModuleInfo heartbeat = SampleParser.parseHeartbeatMessage(loadHeartbeat());

        assertNotNull(heartbeat);
        assertEquals(ModuleInfo.ModuleType.TRAFFIC_COLLECTOR, heartbeat.getModuleType());
        assertEquals(COLLECTOR_POD, heartbeat.getName());

        Map<String, Object> additionalData = heartbeat.getAdditionalData();
        assertNotNull("additionalData did not survive parsing", additionalData);
        assertTrue("pipeline block missing", additionalData.containsKey("pipeline"));

        @SuppressWarnings("unchecked")
        Map<String, Object> pipeline = (Map<String, Object>) additionalData.get("pipeline");
        assertTrue(pipeline.containsKey("delta"));
        assertEquals("flat_buffer", pipeline.get("ingestion_mode"));

        // The collector ships exactly six counters. If that grows silently, the
        // payload is drifting back towards shipping everything.
        @SuppressWarnings("unchecked")
        Map<String, Object> delta = (Map<String, Object>) pipeline.get("delta");
        assertEquals(6, delta.size());
    }

    /** Every shipped counter reaches AllMetrics with the value the daemonset sent. */
    @Test
    public void recordsShippedPipelineCounters() throws Exception {
        clearState();
        invokeExtract(SampleParser.parseHeartbeatMessage(loadHeartbeat()));

        assertEquals(1000.0f, flush("TC_EVENTS_RECEIVED").get(0).getValue(), 0.001f);
        assertEquals(13.0f, flush("TC_EVENTS_DISCARDED").get(0).getValue(), 0.001f);
        assertEquals(5.0f, flush("TC_EVENTS_DROPPED_KERNEL").get(0).getValue(), 0.001f);
        assertEquals(3.0f, flush("TC_EVENTS_DROPPED_CHAN").get(0).getValue(), 0.001f);
        assertEquals(4.0f, flush("TC_EVENTS_DROPPED_BACKPRESSURE").get(0).getValue(), 0.001f);
        assertEquals(1.0f, flush("TC_EVENTS_DROPPED_MALFORMED").get(0).getValue(), 0.001f);
    }

    /**
     * The headline discard number must equal the sum of the per-cause series, or
     * the total and its breakdown would disagree on the page.
     */
    @Test
    public void discardedTotalReconcilesWithItsCauses() throws Exception {
        clearState();
        invokeExtract(SampleParser.parseHeartbeatMessage(loadHeartbeat()));

        float kernel = flush("TC_EVENTS_DROPPED_KERNEL").get(0).getValue();
        float chan = flush("TC_EVENTS_DROPPED_CHAN").get(0).getValue();
        float backpressure = flush("TC_EVENTS_DROPPED_BACKPRESSURE").get(0).getValue();
        float malformed = flush("TC_EVENTS_DROPPED_MALFORMED").get(0).getValue();

        clearState();
        invokeExtract(SampleParser.parseHeartbeatMessage(loadHeartbeat()));
        float total = flush("TC_EVENTS_DISCARDED").get(0).getValue();

        assertEquals("total must equal the sum of its causes",
                kernel + chan + backpressure + malformed, total, 0.001f);
    }




    /** Emitted MetricData is attributed to the collector pod, not the mini-runtime. */
    @Test
    public void attributesMetricsToCollectorInstance() throws Exception {
        clearState();
        invokeExtract(SampleParser.parseHeartbeatMessage(loadHeartbeat()));

        MetricData md = flush("TC_EVENTS_RECEIVED").get(0);
        assertEquals(COLLECTOR_POD, md.getInstanceId());
        assertEquals(ModuleInfo.ModuleType.TRAFFIC_COLLECTOR.name(), md.getModuleType());
        assertEquals("test-org", md.getOrgId());
    }

    /**
     * Delta counters must be SUM, not GAUGE. The daemonset heartbeats every 60s but
     * AllMetrics flushes every 120s, so a gauge would overwrite and report half the
     * real count.
     */
    @Test
    public void deltaCountersAreSum() throws Exception {
        clearState();
        invokeExtract(SampleParser.parseHeartbeatMessage(loadHeartbeat()));

        assertEquals(MetricData.MetricType.SUM, flush("TC_EVENTS_RECEIVED").get(0).getMetricType());
        assertEquals(MetricData.MetricType.SUM, flush("TC_EVENTS_DISCARDED").get(0).getMetricType());
    }

    /** Two heartbeats inside one flush window must add up rather than overwrite. */
    @Test
    public void twoHeartbeatsInOneWindowAccumulate() throws Exception {
        clearState();
        String heartbeat = loadHeartbeat();
        invokeExtract(SampleParser.parseHeartbeatMessage(heartbeat));
        invokeExtract(SampleParser.parseHeartbeatMessage(heartbeat));

        assertEquals("two 1000-event heartbeats must flush as 2000",
                2000.0f, flush("TC_EVENTS_RECEIVED").get(0).getValue(), 0.001f);
    }

    /**
     * A collector reporting zero drops is healthy, not dead. The previous
     * value-based eviction removed it every cycle, which is the state the drop
     * counters sit in most of the time.
     */
    @Test
    public void zeroValuedLiveInstanceIsNotEvicted() throws Exception {
        clearState();
        invokeExtract(SampleParser.parseHeartbeatMessage(loadHeartbeat()));

        // First flush drains the counter; the second sees a genuine zero, which is
        // the state a healthy collector's drop counters sit in almost always.
        List<MetricData> first = flush("TC_EVENTS_DROPPED_MALFORMED");
        assertEquals(1, first.size());
        assertEquals(1.0f, first.get(0).getValue(), 0.001f);

        List<MetricData> second = flush("TC_EVENTS_DROPPED_MALFORMED");
        assertEquals("live collector with zero drops must keep reporting", 1, second.size());
        assertEquals(0.0f, second.get(0).getValue(), 0.001f);
    }

    /** A collector that stops heartbeating is dropped once it goes stale. */
    @Test
    public void staleInstanceIsEvicted() throws Exception {
        clearState();
        invokeExtract(SampleParser.parseHeartbeatMessage(loadHeartbeat()));

        assertFalse(flush("TC_EVENTS_RECEIVED").isEmpty());

        // Backdate past TC_INSTANCE_STALE_SECS (600).
        lastSeen().put(COLLECTOR_POD, Context.now() - 601);

        assertTrue("stale collector should emit nothing", flush("TC_EVENTS_RECEIVED").isEmpty());
        assertFalse("stale collector should be removed from the map",
                pipelineMetrics().get("TC_EVENTS_RECEIVED").containsKey(COLLECTOR_POD));
    }

    /** An older daemonset sends no pipeline block; that must be a no-op, not a crash. */
    @Test
    public void heartbeatWithoutPipelineBlockIsIgnored() throws Exception {
        clearState();
        String legacy = "{\"type\":\"heartbeat\",\"daemonId\":\"d-1\","
                + "\"daemonPodName\":\"" + COLLECTOR_POD + "\",\"moduleType\":\"TRAFFIC_COLLECTOR\","
                + "\"timestamp\":\"1790142448\",\"imageVersion\":\"old\","
                + "\"additionalData\":\"{\\\"profiling\\\":{\\\"cpu_percent\\\":1.5}}\"}";

        invokeExtract(SampleParser.parseHeartbeatMessage(legacy));

        assertTrue("legacy heartbeat must not register any pipeline metric",
                pipelineMetrics().isEmpty());
    }
}

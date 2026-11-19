package com.akto.service.atlas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.akto.action.threat_detection.HostSeverityCount;

/* The severity donut's drill-down: all-time host counts, cached without failures, filtered and paged per device. */
public class TestAtlasAllTimeViolations {

    private final AtlasPeopleService service = new AtlasPeopleService();

    private static AtlasPeopleService.Snapshot snapshotWith(String... deviceNames) {
        AtlasPeopleService.Snapshot snapshot = new AtlasPeopleService.Snapshot();
        for (String name : deviceNames) {
            AtlasPeopleService.DeviceRow device = new AtlasPeopleService.DeviceRow();
            device.name = name;
            device.key = name;
            snapshot.devices.add(device);
            snapshot.devicesByLowerName.put(name, device);
        }
        return snapshot;
    }

    private static AtlasPeopleService.PeopleQuery highSeverityQuery(int skip, int limit) {
        AtlasPeopleService.PeopleQuery query = new AtlasPeopleService.PeopleQuery();
        query.violationSeverities = Collections.singletonList("high");
        query.skip = skip;
        query.limit = limit;
        return query;
    }

    @Test
    public void testFailedLoadIsNotCached() {
        String key = UUID.randomUUID().toString();
        try {
            service.getOrLoadAllTimeHostCounts(key, () -> { throw new IllegalStateException("threat backend down"); });
            fail("a failed load must surface, not come back as an empty list");
        } catch (IllegalStateException expected) {
            // the request errors instead of showing zero rows
        }
        List<HostSeverityCount> counts = Collections.singletonList(new HostSeverityCount("mac-1.ai-agent.claude", 0, 2, 0, 0));
        assertEquals(counts, service.getOrLoadAllTimeHostCounts(key, () -> counts));
    }

    @Test
    public void testSuccessfulLoadIsReused() {
        String key = UUID.randomUUID().toString();
        AtomicInteger loads = new AtomicInteger();
        service.getOrLoadAllTimeHostCounts(key, () -> { loads.incrementAndGet(); return Collections.emptyList(); });
        service.getOrLoadAllTimeHostCounts(key, () -> { loads.incrementAndGet(); return Collections.emptyList(); });
        assertEquals(1, loads.get());
    }

    @Test
    public void testOverrideFiltersOnAllTimeCountsAndShowsThem() {
        AtlasPeopleService.Snapshot snapshot = snapshotWith("mac-1", "mac-2");
        List<HostSeverityCount> allTime = Arrays.asList(
                new HostSeverityCount("mac-1.ai-agent.claude", 0, 3, 0, 0),
                new HostSeverityCount("mac-1.cursor.notion-mcp", 1, 2, 0, 0),
                new HostSeverityCount("nayan.api.anthropic.com", 0, 9, 0, 0)); // not a managed device
        long[] total = new long[1];

        // Without the override the snapshot's 7-day counts (all zero here) match nothing.
        assertTrue(service.pageDevices(snapshot, highSeverityQuery(0, 50), total).isEmpty());

        AtlasPeopleService.PeopleQuery query = highSeverityQuery(0, 50);
        query.violationsOverride = service.violationsByDeviceKey(snapshot, allTime);
        List<Map<String, Object>> rows = service.pageDevices(snapshot, query, total);

        assertEquals(1, total[0]);
        assertEquals("mac-1", rows.get(0).get("key"));
        @SuppressWarnings("unchecked")
        Map<String, Integer> violations = (Map<String, Integer>) rows.get(0).get("violations");
        assertEquals(Integer.valueOf(5), violations.get("high"));
        assertEquals(Integer.valueOf(1), violations.get("critical"));
        assertEquals(6, rows.get(0).get("violationCount"));
    }

    @Test
    public void testOverrideKeepsSkipAndLimit() {
        AtlasPeopleService.Snapshot snapshot = snapshotWith("a", "b", "c");
        List<HostSeverityCount> allTime = Arrays.asList(
                new HostSeverityCount("a.agent", 0, 1, 0, 0),
                new HostSeverityCount("b.agent", 0, 1, 0, 0),
                new HostSeverityCount("c.agent", 0, 1, 0, 0));
        Map<String, Map<String, Integer>> override = service.violationsByDeviceKey(snapshot, allTime);
        long[] total = new long[1];

        AtlasPeopleService.PeopleQuery firstPage = highSeverityQuery(0, 2);
        firstPage.violationsOverride = override;
        assertEquals(2, service.pageDevices(snapshot, firstPage, total).size());
        assertEquals(3, total[0]);

        AtlasPeopleService.PeopleQuery secondPage = highSeverityQuery(2, 2);
        secondPage.violationsOverride = override;
        assertEquals(1, service.pageDevices(snapshot, secondPage, total).size());
        assertEquals(3, total[0]);
    }
}

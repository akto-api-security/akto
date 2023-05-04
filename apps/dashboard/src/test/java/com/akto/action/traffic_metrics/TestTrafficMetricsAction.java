package com.akto.action.traffic_metrics;

import com.akto.MongoBasedTest;
import com.akto.dao.traffic_metrics.TrafficMetricsDao;
import com.akto.dto.traffic_metrics.TrafficMetrics;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class TestTrafficMetricsAction extends MongoBasedTest {


    @Test
    public void testExecute() {
        TrafficMetricsDao.instance.getMCollection().drop();

        TrafficMetrics.Key key1 = new TrafficMetrics.Key("ip1", "host1", 1, TrafficMetrics.Name.TOTAL_REQUESTS_RUNTIME, 19449, 19450);
        Map<String, Long> countMap1 = new HashMap<>();
        countMap1.put("466776", 100L);
        countMap1.put("466777", 200L);
        TrafficMetrics trafficMetrics1 = new TrafficMetrics(key1, countMap1);

        TrafficMetrics.Key key2 = new TrafficMetrics.Key("ip1", "host1", 1, TrafficMetrics.Name.FILTERED_REQUESTS_RUNTIME, 19449, 19450);
        Map<String, Long> countMap2 = new HashMap<>();
        countMap2.put("466778", 200L);
        countMap2.put("466779", 300L);
        TrafficMetrics trafficMetrics2 = new TrafficMetrics(key2, countMap2);

        TrafficMetrics.Key key3 = new TrafficMetrics.Key("ip2", "host1", 1, TrafficMetrics.Name.TOTAL_REQUESTS_RUNTIME, 19449, 19450);
        Map<String, Long> countMap3 = new HashMap<>();
        countMap3.put("466776", 100L);
        countMap3.put("466777", 200L);
        TrafficMetrics trafficMetrics3 = new TrafficMetrics(key3, countMap3);

        TrafficMetrics.Key key4 = new TrafficMetrics.Key("ip3", "host1", 2, TrafficMetrics.Name.TOTAL_REQUESTS_RUNTIME, 19449, 19450);
        Map<String, Long> countMap4 = new HashMap<>();
        countMap4.put("466776", 100L);
        countMap4.put("466777", 200L);
        TrafficMetrics trafficMetrics4 = new TrafficMetrics(key4, countMap4);

        TrafficMetrics.Key key5 = new TrafficMetrics.Key("ip1", "host1", 1, TrafficMetrics.Name.TOTAL_REQUESTS_RUNTIME, 19450, 19451);
        Map<String, Long> countMap5 = new HashMap<>();
        countMap5.put("466800", 100L);
        countMap5.put("466801", 100L);
        TrafficMetrics trafficMetrics5 = new TrafficMetrics(key5, countMap5);

        TrafficMetricsDao.instance.insertMany(Arrays.asList(trafficMetrics1, trafficMetrics2, trafficMetrics3, trafficMetrics4, trafficMetrics5));

        Map<TrafficMetrics.Name, Map<String, Map<String, Long>>> trafficMetricsMap = validate("HOST");
        Map<String, Map<String, Long>> filtered = trafficMetricsMap.get(TrafficMetrics.Name.FILTERED_REQUESTS_RUNTIME);
        assertEquals(1, filtered.size());
        Map<String, Map<String, Long>> total = trafficMetricsMap.get(TrafficMetrics.Name.TOTAL_REQUESTS_RUNTIME);
        assertEquals(1, total.size());
        Map<String, Long> countMap = total.get("host1");
        assertEquals(new Long(300), countMap.get("466776"));
        assertEquals(new Long(600), countMap.get("466777"));
        assertFalse( countMap.containsKey("466778"));
        assertFalse(countMap.containsKey("466779"));
        assertEquals(new Long(100), countMap.get("466800"));
        assertEquals(new Long(100), countMap.get("466801"));

        trafficMetricsMap = validate("VXLANID");
        filtered = trafficMetricsMap.get(TrafficMetrics.Name.FILTERED_REQUESTS_RUNTIME);
        assertEquals(1, filtered.size());
        total = trafficMetricsMap.get(TrafficMetrics.Name.TOTAL_REQUESTS_RUNTIME);
        assertEquals(2, total.size());
        countMap = total.get("1");
        assertEquals(new Long(200), countMap.get("466776"));
        assertEquals(new Long(400), countMap.get("466777"));
        assertFalse( countMap.containsKey("466778"));
        assertFalse(countMap.containsKey("466779"));
        assertEquals(new Long(100), countMap.get("466800"));
        assertEquals(new Long(100), countMap.get("466801"));
    }

    private Map<TrafficMetrics.Name, Map<String, Map<String, Long>>> validate(String groupBy) {
        TrafficMetricsAction trafficMetricsAction = new TrafficMetricsAction();
        trafficMetricsAction.setNames(Arrays.asList(TrafficMetrics.Name.TOTAL_REQUESTS_RUNTIME, TrafficMetrics.Name.FILTERED_REQUESTS_RUNTIME));
        trafficMetricsAction.setStartTimestamp(1680393600);
        trafficMetricsAction.setEndTimestamp(1680998400);
        trafficMetricsAction.setGroupBy(groupBy);
        String result = trafficMetricsAction.execute();

        assertEquals("SUCCESS", result);

        return trafficMetricsAction.getTrafficMetricsMap();
    }
}

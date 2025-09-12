package com.akto.threat.backend.cron;

import com.akto.threat.backend.db.ApiDistributionDataModel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PercentilesCronTest {

    private static Map<String, Integer> mapOf(Object... kv) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], (Integer) kv[i + 1]);
        }
        return m;
    }

    private static ApiDistributionDataModel docWithBuckets(Map<String, Integer> buckets) {
        ApiDistributionDataModel m = new ApiDistributionDataModel();
        // Assumes public field as used in production code
        m.distribution = buckets;
        return m;
    }

    private static int[] extractPercentiles(Object result) throws Exception {
        Class<?> c = result.getClass();
        Field p50 = c.getDeclaredField("p50");
        Field p75 = c.getDeclaredField("p75");
        Field p90 = c.getDeclaredField("p90");
        p50.setAccessible(true);
        p75.setAccessible(true);
        p90.setAccessible(true);
        return new int[] { (int) p50.get(result), (int) p75.get(result), (int) p90.get(result) };
    }

    @Test
    public void returnsZerosForEmptyData() throws Exception {
        PercentilesCron cron = new PercentilesCron(null);
        List<ApiDistributionDataModel> data = Collections.emptyList();

        Object result = cron.calculatePercentiles(data, 2);
        int[] vals = extractPercentiles(result);

        assertEquals(0, vals[0]);
        assertEquals(0, vals[1]);
        assertEquals(0, vals[2]);
    }

    @Test
    public void simpleThreeBucketDistribution() throws Exception {
        // totalUsers = 100 (50 in b1, 25 in b2, 25 in b3)
        // p50 target = 50 -> falls in b1 => lower bound 0
        // p75 target = 75 -> falls in b2 => lower bound 10
        // p90 target = 90 -> falls in b3 => lower bound 50
        PercentilesCron cron = new PercentilesCron(null);
        List<ApiDistributionDataModel> data = Arrays.asList(
                docWithBuckets(mapOf("b1", 50)),
                docWithBuckets(mapOf("b2", 25)),
                docWithBuckets(mapOf("b3", 25))
        );

        Object result = cron.calculatePercentiles(data, 2);
        int[] vals = extractPercentiles(result);

        assertEquals(0, vals[0]);
        assertEquals(10, vals[1]);
        assertEquals(50, vals[2]);
    }

    @Test
    public void handlesSparseAndMissingBuckets() throws Exception {
        // Only b4 and b6 present: totals 300 users.
        // Lower bounds: b4=100, b6=500
        // p50=150 -> cumulative reaches 150 at b4 => 100
        // p75=225 -> falls in b6 => 500
        // p90=270 -> in b6 => 500
        PercentilesCron cron = new PercentilesCron(null);
        List<ApiDistributionDataModel> data = Arrays.asList(
                docWithBuckets(mapOf("b4", 200)),
                docWithBuckets(mapOf("b6", 100))
        );

        Object result = cron.calculatePercentiles(data, 2);
        int[] vals = extractPercentiles(result);

        assertEquals(100, vals[0]);
        assertEquals(500, vals[1]);
        assertEquals(500, vals[2]);
    }

    @Test
    public void reachesMaxUpperBoundWhenNeeded() throws Exception {
        // Large counts only in the last bucket b14 -> percentiles should map to lower bound of b14, which is 100000
        PercentilesCron cron = new PercentilesCron(null);
        List<ApiDistributionDataModel> data = Arrays.asList(
                docWithBuckets(mapOf("b14", 10_000))
        );

        Object result = cron.calculatePercentiles(data, 2);
        int[] vals = extractPercentiles(result);

        assertEquals(100000, vals[0]);
        assertEquals(100000, vals[1]);
        assertEquals(100000, vals[2]);
    }

    @Test
    public void exactBoundaryTargetsChooseLowerBoundOfThatBucket() throws Exception {
        // Construct distribution so that cumulative hits exactly boundaries
        // b1: 50, b2: 25, b3: 25 (total 100)
        // Targets: p50=50, p75=75, p90=90 → falls in b1, b2, b3 respectively
        // Expect lower bounds 0, 10, 50
        PercentilesCron cron = new PercentilesCron(null);
        List<ApiDistributionDataModel> data = Arrays.asList(
                docWithBuckets(mapOf("b1", 50, "b2", 25, "b3", 25))
        );

        Object result = cron.calculatePercentiles(data, 2);
        int[] vals = extractPercentiles(result);

        assertEquals(0, vals[0]);
        assertEquals(10, vals[1]);
        assertEquals(50, vals[2]);
    }

}



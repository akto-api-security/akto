package com.akto.threat.backend.db;

import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ApiDistributionDataRequestPayload;
import com.akto.threat.backend.cron.PercentilesCron;
import com.akto.utils.ThreatApiDistributionUtils;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class ApiRateLimitBucketStatisticsModelTest {

    private ApiDistributionDataRequestPayload.DistributionData dd(int collectionId, String url, String method, int windowSize, int windowStart, Map<String, Integer> dist){
        return ApiDistributionDataRequestPayload.DistributionData.newBuilder()
                .setApiCollectionId(collectionId)
                .setUrl(url)
                .setMethod(method)
                .setWindowSize(windowSize)
                .setWindowStartEpochMin(windowStart)
                .putAllDistribution(dist)
                .build();
    }

    private List<ApiDistributionDataRequestPayload.DistributionData> generateRandomTwoDays(int collectionId, String url, String method, long startWindowStartEpochMin, long seed) {
        final int windowSize = 5;
        // 2 days 5 minute windows = 576
        final int windows = (PercentilesCron.DEFAULT_BASELINE_DAYS * 24 * 60) / windowSize;
        Random rnd = new Random(seed);

        List<ApiDistributionDataRequestPayload.DistributionData> out = new ArrayList<>(windows);

        // Build fixed label list once (b1..b14)
        List<String> labels = new ArrayList<>(ThreatApiDistributionUtils.getLABEL_TO_RANGE_MAP().keySet());

        long ws = startWindowStartEpochMin;
        for (int i = 0; i < windows; i++, ws += windowSize) {
            Map<String,Integer> dist = new HashMap<>();

            // Generate realistic, skewed counts: more weight to lower buckets, occasional spikes
            int base = 5 + rnd.nextInt(20); // small base load
            for (int li = 0; li < labels.size(); li++) {
                String label = labels.get(li);
                int weight = Math.max(1, (labels.size() - li)); // higher for lower buckets
                int noise = rnd.nextInt(3 + li); // slightly increasing noise for higher buckets
                int spike = (rnd.nextDouble() < 0.02 && li <= 5) ? rnd.nextInt(50) : 0; // rare spikes in lower buckets
                int val = Math.max(0, base * weight / 10 + noise + spike);
                dist.put(label, val);
            }

            out.add(dd(collectionId, url, method, windowSize, (int) ws, dist));
        }

        return out;
    }

    @Test
    public void realisticRandomTwoDays_generatesAndValidatesStats() {
        ApiRateLimitBucketStatisticsModel doc = null;

        long start = 1_000_000; // arbitrary epoch minutes
        List<ApiDistributionDataRequestPayload.DistributionData> batch = generateRandomTwoDays(42, "/users", "GET", start, 12345L);

        // Apply in chunks to simulate multiple calls
        int chunk = 100;
        for (int i = 0; i < batch.size(); i += chunk) {
            int end = Math.min(i + chunk, batch.size());
            doc = ApiRateLimitBucketStatisticsModel.applyUpdates(doc, batch.subList(i, end));
        }

        // Validate capacity and stats equality vs recomputation per bucket
        int expectedCapacity = (PercentilesCron.DEFAULT_BASELINE_DAYS * 24 * 60) / 5;
        assertNotNull(doc);
        assertNotNull(doc.getBuckets());
        assertFalse(doc.getBuckets().isEmpty());

        for (ApiRateLimitBucketStatisticsModel.Bucket b : doc.getBuckets()) {
            // Capacity should match 2 days of 5-min windows
            assertEquals(expectedCapacity, b.getUserCounts().size(), "capacity mismatch for " + b.getLabel());

            // Recompute stats
            List<Integer> vals = new ArrayList<>();
            for (ApiRateLimitBucketStatisticsModel.UserCountData ucd : b.getUserCounts()) vals.add(ucd.getUsers());
            Collections.sort(vals);

            int min = vals.isEmpty() ? 0 : vals.get(0);
            int max = vals.isEmpty() ? 0 : vals.get(vals.size()-1);
            int p25 = ThreatApiDistributionUtils.percentile(vals, 25);
            int p50 = ThreatApiDistributionUtils.percentile(vals, 50);
            int p75 = ThreatApiDistributionUtils.percentile(vals, 75);

            assertEquals(min, b.getStats().getMin(), "min mismatch for " + b.getLabel());
            assertEquals(max, b.getStats().getMax(), "max mismatch for " + b.getLabel());
            assertEquals(p25, b.getStats().getP25(), "p25 mismatch for " + b.getLabel());
            assertEquals(p50, b.getStats().getP50(), "p50 mismatch for " + b.getLabel());
            assertEquals(p75, b.getStats().getP75(), "p75 mismatch for " + b.getLabel());
        }
    }
    @Test
    public void insertsAndComputesStats_singleWindow() {
        ApiRateLimitBucketStatisticsModel doc = null;
        List<ApiDistributionDataRequestPayload.DistributionData> updates = new ArrayList<>();

        Map<String,Integer> dist = new HashMap<>();
        dist.put("b1", 5);
        dist.put("b2", 10);
        // other buckets implicitly zero

        updates.add(dd(1, "/a", "GET", 5, 1000, dist));

        doc = ApiRateLimitBucketStatisticsModel.applyUpdates(doc, updates);

        // Expect one entry in each bucket's userCounts at windowStart=1000 (zeros for others)
        for (ApiRateLimitBucketStatisticsModel.Bucket b : doc.getBuckets()) {
            assertEquals(1, b.getUserCounts().size());
            assertEquals(1000, b.getUserCounts().get(0).getWindowStart());
        }

        // Stats for b1: only [5]
        ApiRateLimitBucketStatisticsModel.Bucket b1 = doc.getBuckets().stream().filter(b -> b.getLabel().equals("b1")).findFirst().get();
        assertEquals(5, b1.getStats().getMin());
        assertEquals(5, b1.getStats().getMax());
        assertEquals(5, b1.getStats().getP25());
        assertEquals(5, b1.getStats().getP50());
        assertEquals(5, b1.getStats().getP75());

        // Stats for some other bucket (e.g. b3) should be zeros
        ApiRateLimitBucketStatisticsModel.Bucket b3 = doc.getBuckets().stream().filter(b -> b.getLabel().equals("b3")).findFirst().get();
        assertEquals(0, b3.getStats().getMin());
        assertEquals(0, b3.getStats().getMax());
        assertEquals(0, b3.getStats().getP25());
        assertEquals(0, b3.getStats().getP50());
        assertEquals(0, b3.getStats().getP75());
    }

    @Test
    public void overwriteExistingWindow_updatesValue() {
        ApiRateLimitBucketStatisticsModel doc = null;
        List<ApiDistributionDataRequestPayload.DistributionData> updates1 = new ArrayList<>();
        Map<String,Integer> dist1 = new HashMap<>();
        dist1.put("b1", 10);
        updates1.add(dd(1, "/a", "GET", 5, 1000, dist1));
        doc = ApiRateLimitBucketStatisticsModel.applyUpdates(doc, updates1);

        // overwrite same windowStart with new value
        List<ApiDistributionDataRequestPayload.DistributionData> updates2 = new ArrayList<>();
        Map<String,Integer> dist2 = new HashMap<>();
        dist2.put("b1", 20);
        updates2.add(dd(1, "/a", "GET", 5, 1000, dist2));
        doc = ApiRateLimitBucketStatisticsModel.applyUpdates(doc, updates2);

        ApiRateLimitBucketStatisticsModel.Bucket b1 = doc.getBuckets().stream().filter(b -> b.getLabel().equals("b1")).findFirst().get();
        assertEquals(1, b1.getUserCounts().size());
        assertEquals(20, b1.getUserCounts().get(0).getUsers());
        assertEquals(20, b1.getStats().getP50());
    }

    @Test
    public void evictionAtCapacity_keepsMostRecentWindows() {
        ApiRateLimitBucketStatisticsModel doc = null;

        // capacity for 5-min windows is DEFAULT_BASELINE_DAYS * 24 * 60 / 5
        int capacity = (PercentilesCron.DEFAULT_BASELINE_DAYS * 24 * 60) / 5;

        // Insert capacity+2 windows
        List<ApiDistributionDataRequestPayload.DistributionData> batch = new ArrayList<>();
        for (int i = 0; i < capacity + 2; i++) {
            Map<String,Integer> d = new HashMap<>();
            d.put("b2", i); // increasing values for easy verification
            batch.add(dd(1, "/a", "GET", 5, 1000 + i, d));
        }

        doc = ApiRateLimitBucketStatisticsModel.applyUpdates(doc, batch);

        ApiRateLimitBucketStatisticsModel.Bucket b2 = doc.getBuckets().stream().filter(b -> b.getLabel().equals("b2")).findFirst().get();
        assertEquals(capacity, b2.getUserCounts().size());
        // Oldest should be windowStart = 1000 + 2 now (evicted two entries)
        assertEquals(1002, b2.getUserCounts().get(0).getWindowStart());
        // Most recent should be 1000 + (capacity + 1)
        assertEquals(1000 + capacity + 1, b2.getUserCounts().get(capacity - 1).getWindowStart());
    }

    @Test
    public void percentilesComputedOnSortedValues() {
        ApiRateLimitBucketStatisticsModel doc = null;
        List<ApiDistributionDataRequestPayload.DistributionData> updates = new ArrayList<>();

        // Same bucket over multiple windows, unsorted arrival
        updates.add(dd(1, "/a", "GET", 5, 1002, Collections.singletonMap("b1", 30)));
        updates.add(dd(1, "/a", "GET", 5, 1000, Collections.singletonMap("b1", 10)));
        updates.add(dd(1, "/a", "GET", 5, 1001, Collections.singletonMap("b1", 20)));

        doc = ApiRateLimitBucketStatisticsModel.applyUpdates(doc, updates);

        ApiRateLimitBucketStatisticsModel.Bucket b1 = doc.getBuckets().stream().filter(b -> b.getLabel().equals("b1")).findFirst().get();
        // values are [10, 20, 30] -> p25=10, p50=20, p75=30
        assertEquals(10, b1.getStats().getP25());
        assertEquals(20, b1.getStats().getP50());
        assertEquals(30, b1.getStats().getP75());
    }
}



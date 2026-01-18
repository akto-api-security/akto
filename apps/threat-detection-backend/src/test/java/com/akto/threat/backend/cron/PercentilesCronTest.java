package com.akto.threat.backend.cron;

import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.BucketStats;
import com.akto.threat.backend.db.ApiRateLimitBucketStatisticsModel;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class PercentilesCronTest {

    private static BucketStats createBucketStats(String bucketLabel, int min, int max, int p25, int p50, int p75) {
        return BucketStats.newBuilder()
                .setBucketLabel(bucketLabel)
                .setMin(min)
                .setMax(max)
                .setP25(p25)
                .setP50(p50)
                .setP75(p75)
                .build();
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
    public void returnsNegativesForEmptyData() throws Exception {
        PercentilesCron cron = new PercentilesCron(null);
        List<BucketStats> data = Collections.emptyList();

        Object result = cron.calculatePercentiles(data);
        int[] vals = extractPercentiles(result);

        assertEquals(-1, vals[0]);
        assertEquals(-1, vals[1]);
        assertEquals(-1, vals[2]);
    }

    @Test
    public void simpleThreeBucketDistribution() throws Exception {
        // Using p75 values: b1=100, b2=200, b3=100 (total 400 users)
        // p50 target = 200 -> falls in b2 (cumulative 300) => upper bound 50
        // p75 target = 300 -> falls in b2 (cumulative 300) => upper bound 50
        // p90 target = 360 -> falls in b3 (cumulative 400) => upper bound 100
        PercentilesCron cron = new PercentilesCron(null);
        List<BucketStats> data = Arrays.asList(
                createBucketStats("b1", 80, 120, 90, 95, 100),  // p75=100 users
                createBucketStats("b2", 150, 250, 175, 190, 200), // p75=200 users
                createBucketStats("b3", 70, 120, 85, 90, 100)    // p75=100 users
        );

        Object result = cron.calculatePercentiles(data);
        int[] vals = extractPercentiles(result);

        // b1 upper bound = 10, b2 upper bound = 50, b3 upper bound = 100
        assertEquals(50, vals[0]);  // p50
        assertEquals(50, vals[1]);  // p75
        assertEquals(100, vals[2]); // p90
    }

    @Test
    public void handlesSparseAndMissingBuckets() throws Exception {
        // Only b4 and b6 present using p75 values: b4=200, b6=100 (total 300)
        // Upper bounds: b4=250, b6=1000
        // p50=150 -> falls in b4 (cumulative 200) => 250
        // p75=225 -> falls in b6 (cumulative 300) => 1000
        // p90=270 -> falls in b6 (cumulative 300) => 1000
        PercentilesCron cron = new PercentilesCron(null);
        List<BucketStats> data = Arrays.asList(
                createBucketStats("b4", 180, 220, 190, 195, 200), // p75=200
                createBucketStats("b6", 80, 120, 90, 95, 100)     // p75=100
        );

        Object result = cron.calculatePercentiles(data);
        int[] vals = extractPercentiles(result);

        assertEquals(250, vals[0]);  // p50
        assertEquals(1000, vals[1]); // p75
        assertEquals(1000, vals[2]); // p90
    }

    @Test
    public void reachesMaxUpperBoundWhenNeeded() throws Exception {
        // Large counts only in the last bucket b14
        // b14 upper bound is Integer.MAX_VALUE
        PercentilesCron cron = new PercentilesCron(null);
        List<BucketStats> data = Arrays.asList(
                createBucketStats("b14", 9000, 11000, 9500, 9750, 10000) // p75=10000
        );

        Object result = cron.calculatePercentiles(data);
        int[] vals = extractPercentiles(result);

        assertEquals(Integer.MAX_VALUE, vals[0]);
        assertEquals(Integer.MAX_VALUE, vals[1]);
        assertEquals(Integer.MAX_VALUE, vals[2]);
    }

    @Test
    public void exactBoundaryTargetsChooseUpperBoundOfThatBucket() throws Exception {
        // b1: p75=50, b2: p75=25, b3: p75=25 (total 100)
        // Targets: p50=50, p75=75, p90=90
        // Cumulative: b1=50, b1+b2=75, b1+b2+b3=100
        // p50=50 -> exactly at b1 end => upper bound 10
        // p75=75 -> exactly at b2 end => upper bound 50
        // p90=90 -> falls in b3 => upper bound 100
        PercentilesCron cron = new PercentilesCron(null);
        List<BucketStats> data = Arrays.asList(
                createBucketStats("b1", 40, 60, 45, 48, 50),   // p75=50
                createBucketStats("b2", 20, 30, 22, 24, 25),   // p75=25
                createBucketStats("b3", 20, 30, 22, 24, 25)    // p75=25
        );

        Object result = cron.calculatePercentiles(data);
        int[] vals = extractPercentiles(result);

        assertEquals(10, vals[0]);  // p50
        assertEquals(50, vals[1]);  // p75
        assertEquals(100, vals[2]); // p90
    }

    @Test
    public void testWithRealWorldExample() throws Exception {
        // Simulating the example from the algorithm discussion
        // Total users using p75: 19+33+54+78+97+105+97+78+54+33+20+12+9+7 = 696
        PercentilesCron cron = new PercentilesCron(null);
        List<BucketStats> data = Arrays.asList(
                createBucketStats("b1", 4, 24, 9, 14, 19),      // p75=19
                createBucketStats("b2", 18, 38, 23, 28, 33),    // p75=33
                createBucketStats("b4", 63, 83, 68, 73, 78),    // p75=78
                createBucketStats("b5", 82, 102, 87, 92, 97),   // p75=97
                createBucketStats("b6", 90, 110, 95, 100, 105), // p75=105
                createBucketStats("b7", 82, 102, 87, 92, 97),   // p75=97
                createBucketStats("b10", 18, 38, 23, 28, 33),   // p75=33
                createBucketStats("b11", 4, 32, 10, 15, 20),    // p75=20
                createBucketStats("b8", 63, 83, 68, 73, 78),    // p75=78
                createBucketStats("b9", 39, 59, 44, 49, 54),    // p75=54
                createBucketStats("b12", 0, 24, 2, 7, 12),      // p75=12
                createBucketStats("b13", 0, 21, 0, 4, 9),       // p75=9
                createBucketStats("b14", 0, 20, 0, 3, 7),        // p75=7
                createBucketStats("b3", 39, 59, 44, 49, 54)    // p75=54
        );

        Object result = cron.calculatePercentiles(data);
        int[] vals = extractPercentiles(result);

        // Total = 696
        // p50 = 348 -> should fall in b6 (cumulative reaches 386)
        // p75 = 522 -> should fall in b8 (cumulative reaches 561)
        // p90 = 626.4 -> should fall in b10 (cumulative reaches 648)

        // Verify the algorithm places percentiles in reasonable buckets
        // b6 upper = 1000, b8 upper = 5000, b10 upper = 20000
        assertEquals(1000, vals[0]);  // p50 in b6
        assertEquals(5000, vals[1]);  // p75 in b8
        assertEquals(20000, vals[2]); // p90 in b10
    }

    @Test
    public void handlesZeroP75Values() throws Exception {
        // Test when some buckets have p75=0
        PercentilesCron cron = new PercentilesCron(null);
        List<BucketStats> data = Arrays.asList(
                createBucketStats("b1", 0, 0, 0, 0, 0),      // p75=0
                createBucketStats("b2", 10, 20, 12, 15, 18), // p75=18
                createBucketStats("b3", 0, 0, 0, 0, 0),      // p75=0
                createBucketStats("b4", 5, 10, 6, 7, 8)      // p75=8
        );

        Object result = cron.calculatePercentiles(data);
        int[] vals = extractPercentiles(result);

        // Total = 0 + 18 + 0 + 8 = 26
        // p50 = 13 -> falls in b2
        // p75 = 19.5 -> falls in b4
        // p90 = 23.4 -> falls in b4
        assertEquals(50, vals[0]);   // p50 in b2
        assertEquals(250, vals[1]);  // p75 in b4
        assertEquals(250, vals[2]);  // p90 in b4
    }

    @Test
    public void testFetchBucketStatsFromStatistics_returnsCorrectData() throws Exception {
        // Test the optimized method that reads from api_rate_limit_bucket_statistics
        System.out.println("\n=== Testing Optimized fetchBucketStatsFromStatistics ===");

        MongoClient mockClient = mock(MongoClient.class);
        MongoDatabase mockDatabase = mock(MongoDatabase.class);
        @SuppressWarnings("unchecked")
        MongoCollection<ApiRateLimitBucketStatisticsModel> mockCollection = mock(MongoCollection.class);
        @SuppressWarnings("unchecked")
        FindIterable<ApiRateLimitBucketStatisticsModel> mockFindIterable = mock(FindIterable.class);

        when(mockClient.getDatabase(anyString())).thenReturn(mockDatabase);
        when(mockDatabase.getCollection(eq("api_rate_limit_bucket_statistics"), any(Class.class)))
            .thenReturn(mockCollection);
        when(mockCollection.find(any(Bson.class))).thenReturn(mockFindIterable);

        // Create mock statistics document with 3 buckets
        ApiRateLimitBucketStatisticsModel mockDoc = new ApiRateLimitBucketStatisticsModel();
        List<ApiRateLimitBucketStatisticsModel.Bucket> buckets = new ArrayList<>();

        // Bucket b1
        List<ApiRateLimitBucketStatisticsModel.UserCountData> b1Counts = new ArrayList<>();
        b1Counts.add(new ApiRateLimitBucketStatisticsModel.UserCountData(500, 1000));
        b1Counts.add(new ApiRateLimitBucketStatisticsModel.UserCountData(480, 1005));
        b1Counts.add(new ApiRateLimitBucketStatisticsModel.UserCountData(510, 1010));
        ApiRateLimitBucketStatisticsModel.Stats b1Stats =
            new ApiRateLimitBucketStatisticsModel.Stats(480, 510, 485, 495, 505);
        buckets.add(new ApiRateLimitBucketStatisticsModel.Bucket("b1", b1Counts, b1Stats));

        // Bucket b2
        List<ApiRateLimitBucketStatisticsModel.UserCountData> b2Counts = new ArrayList<>();
        b2Counts.add(new ApiRateLimitBucketStatisticsModel.UserCountData(300, 1000));
        b2Counts.add(new ApiRateLimitBucketStatisticsModel.UserCountData(320, 1005));
        b2Counts.add(new ApiRateLimitBucketStatisticsModel.UserCountData(290, 1010));
        ApiRateLimitBucketStatisticsModel.Stats b2Stats =
            new ApiRateLimitBucketStatisticsModel.Stats(290, 320, 295, 300, 310);
        buckets.add(new ApiRateLimitBucketStatisticsModel.Bucket("b2", b2Counts, b2Stats));

        // Bucket b3
        List<ApiRateLimitBucketStatisticsModel.UserCountData> b3Counts = new ArrayList<>();
        b3Counts.add(new ApiRateLimitBucketStatisticsModel.UserCountData(200, 1000));
        b3Counts.add(new ApiRateLimitBucketStatisticsModel.UserCountData(190, 1005));
        b3Counts.add(new ApiRateLimitBucketStatisticsModel.UserCountData(210, 1010));
        ApiRateLimitBucketStatisticsModel.Stats b3Stats =
            new ApiRateLimitBucketStatisticsModel.Stats(190, 210, 195, 200, 205);
        buckets.add(new ApiRateLimitBucketStatisticsModel.Bucket("b3", b3Counts, b3Stats));

        mockDoc.setBuckets(buckets);
        when(mockFindIterable.first()).thenReturn(mockDoc);

        // Create cron instance and call optimized method
        PercentilesCron cron = new PercentilesCron(mockClient);
        List<BucketStats> result = cron.fetchBucketStatsFromStatistics(
            "1000000", 123, "/checkout", "GET", 5
        );

        // Verify results
        System.out.println("Retrieved " + result.size() + " bucket stats");
        assertEquals(3, result.size(), "Should return 3 buckets");

        // Verify b1
        BucketStats b1 = result.get(0);
        assertEquals("b1", b1.getBucketLabel());
        assertEquals(480, b1.getMin());
        assertEquals(510, b1.getMax());
        assertEquals(505, b1.getP75());  // This is what calculatePercentiles uses!

        // Verify b2
        BucketStats b2 = result.get(1);
        assertEquals("b2", b2.getBucketLabel());
        assertEquals(310, b2.getP75());

        // Verify b3
        BucketStats b3 = result.get(2);
        assertEquals("b3", b3.getBucketLabel());
        assertEquals(205, b3.getP75());

        System.out.println("✓ Optimized method successfully reads pre-calculated stats!");
        System.out.println("✓ Returns same data structure as old method");
        System.out.println("✓ But queries 1 document instead of ~576 documents!");

        // Verify collection was called correctly
        verify(mockCollection, times(1)).find(any(Bson.class));
    }

    @Test
    public void testFetchBucketStatsFromStatistics_handlesEmptyData() {
        // Test when no statistics document exists
        MongoClient mockClient = mock(MongoClient.class);
        MongoDatabase mockDatabase = mock(MongoDatabase.class);
        @SuppressWarnings("unchecked")
        MongoCollection<ApiRateLimitBucketStatisticsModel> mockCollection = mock(MongoCollection.class);
        @SuppressWarnings("unchecked")
        FindIterable<ApiRateLimitBucketStatisticsModel> mockFindIterable = mock(FindIterable.class);

        when(mockClient.getDatabase(anyString())).thenReturn(mockDatabase);
        when(mockDatabase.getCollection(eq("api_rate_limit_bucket_statistics"), any(Class.class)))
            .thenReturn(mockCollection);
        when(mockCollection.find(any(Bson.class))).thenReturn(mockFindIterable);
        when(mockFindIterable.first()).thenReturn(null);  // No document found

        PercentilesCron cron = new PercentilesCron(mockClient);
        List<BucketStats> result = cron.fetchBucketStatsFromStatistics(
            "1000000", 123, "/unknown", "GET", 5
        );

        assertEquals(0, result.size(), "Should return empty list when no data exists");
    }

    @Test
    public void testExtractApiKeyFromDocId_validFormats() throws Exception {
        PercentilesCron cron = new PercentilesCron(null);

        // Use reflection to access private method
        java.lang.reflect.Method method = PercentilesCron.class.getDeclaredMethod("extractApiKeyFromDocId", String.class);
        method.setAccessible(true);

        // Test case 1: Simple URL
        String result1 = (String) method.invoke(cron, "123_GET_/checkout_5");
        assertEquals("123|/checkout|GET", result1);

        // Test case 2: Nested URL
        String result2 = (String) method.invoke(cron, "456_POST_/api/users_15");
        assertEquals("456|/api/users|POST", result2);

        // Test case 3: URL with underscores
        String result3 = (String) method.invoke(cron, "789_PUT_/api/user_profile_30");
        assertEquals("789|/api/user_profile|PUT", result3);

        // Test case 4: Different window size
        String result4 = (String) method.invoke(cron, "100_DELETE_/resource_30");
        assertEquals("100|/resource|DELETE", result4);
    }

    @Test
    public void testExtractApiKeyFromDocId_invalidFormats() throws Exception {
        PercentilesCron cron = new PercentilesCron(null);

        // Use reflection to access private method
        java.lang.reflect.Method method = PercentilesCron.class.getDeclaredMethod("extractApiKeyFromDocId", String.class);
        method.setAccessible(true);

        // Test null
        String result1 = (String) method.invoke(cron, (String) null);
        assertEquals(null, result1);

        // Test empty string
        String result2 = (String) method.invoke(cron, "");
        assertEquals(null, result2);

        // Test malformed (no underscores)
        String result3 = (String) method.invoke(cron, "malformed");
        assertEquals(null, result3);

        // Test malformed (only one underscore)
        String result4 = (String) method.invoke(cron, "123_GET");
        assertEquals(null, result4);
    }

    @Test
    public void testExtractApiKeyFromDocId_deduplication() throws Exception {
        PercentilesCron cron = new PercentilesCron(null);

        // Use reflection to access private method
        java.lang.reflect.Method method = PercentilesCron.class.getDeclaredMethod("extractApiKeyFromDocId", String.class);
        method.setAccessible(true);

        // Same API with different window sizes should produce same key
        String result1 = (String) method.invoke(cron, "123_GET_/checkout_5");
        String result2 = (String) method.invoke(cron, "123_GET_/checkout_15");
        String result3 = (String) method.invoke(cron, "123_GET_/checkout_30");

        assertEquals("123|/checkout|GET", result1);
        assertEquals("123|/checkout|GET", result2);
        assertEquals("123|/checkout|GET", result3);
        assertEquals(result1, result2);
        assertEquals(result2, result3);

        // When added to a Set, should deduplicate
        Set<String> keys = new HashSet<>();
        keys.add(result1);
        keys.add(result2);
        keys.add(result3);
        assertEquals(1, keys.size(), "All three should deduplicate to single key");
    }
}
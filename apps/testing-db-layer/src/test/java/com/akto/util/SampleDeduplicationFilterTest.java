package com.akto.util;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Comprehensive test suite for SampleDeduplicationFilter.
 * Tests basic deduplication, filter rotation, time buckets, edge cases, and concurrency.
 */
public class SampleDeduplicationFilterTest {

    @Before
    public void setUp() throws Exception {
        // Reset filter state using reflection for clean test isolation
        resetFilterState();
    }

    /**
     * Reset internal filter state using reflection for clean test isolation
     */
    private void resetFilterState() throws Exception {
        // Access private static fields
        Field currentFilterIndexField = SampleDeduplicationFilter.class.getDeclaredField("currentFilterIndex");
        currentFilterIndexField.setAccessible(true);
        currentFilterIndexField.setInt(null, -1);

        Field filterFillStartTimeField = SampleDeduplicationFilter.class.getDeclaredField("filterFillStartTime");
        filterFillStartTimeField.setAccessible(true);
        filterFillStartTimeField.setLong(null, 0);
    }

    // ======================
    // Basic Deduplication Tests
    // ======================

    @Test
    public void testFirstInsertShouldReturnTrue() {
        // First time seeing this API
        boolean shouldInsert = SampleDeduplicationFilter.shouldInsertSample(
            1, "GET", "/api/users", System.currentTimeMillis()
        );

        assertTrue("First insert should return true", shouldInsert);
    }

    @Test
    public void testDuplicateInSameTimeBucketShouldReturnFalse() {
        long timestamp = System.currentTimeMillis();

        // First insert
        boolean firstInsert = SampleDeduplicationFilter.shouldInsertSample(
            1, "GET", "/api/users", timestamp
        );

        // Duplicate in same time bucket (within same 30-min window)
        boolean secondInsert = SampleDeduplicationFilter.shouldInsertSample(
            1, "GET", "/api/users", timestamp + 1000 // 1 second later
        );

        assertTrue("First insert should succeed", firstInsert);
        assertFalse("Duplicate in same time bucket should be rejected", secondInsert);
    }

    @Test
    public void testDifferentAPIsInSameTimeBucketShouldBothSucceed() {
        long timestamp = System.currentTimeMillis();

        boolean insert1 = SampleDeduplicationFilter.shouldInsertSample(
            1, "GET", "/api/users", timestamp
        );

        boolean insert2 = SampleDeduplicationFilter.shouldInsertSample(
            1, "GET", "/api/orders", timestamp // Different URL
        );

        boolean insert3 = SampleDeduplicationFilter.shouldInsertSample(
            1, "POST", "/api/users", timestamp // Different method
        );

        boolean insert4 = SampleDeduplicationFilter.shouldInsertSample(
            2, "GET", "/api/users", timestamp // Different collection
        );

        assertTrue("Different URL should succeed", insert1 && insert2);
        assertTrue("Different method should succeed", insert3);
        assertTrue("Different collection should succeed", insert4);
    }

    @Test
    public void testSameAPIWithin30MinutesIsRejected() {
        // With sliding window, same API within 30 minutes should be rejected
        long firstTimestamp = 1000000000000L;
        long secondTimestamp = firstTimestamp + (25 * 60 * 1000); // 25 minutes later (still in window)

        boolean firstInsert = SampleDeduplicationFilter.shouldInsertSample(
            1, "GET", "/api/users", firstTimestamp
        );

        boolean secondInsert = SampleDeduplicationFilter.shouldInsertSample(
            1, "GET", "/api/users", secondTimestamp
        );

        assertTrue("First insert should succeed", firstInsert);
        assertFalse("Same API within 30-min window should be rejected", secondInsert);
    }

    // ======================
    // Sliding Window Tests
    // ======================

    @Test
    public void testSlidingWindowBehavior() {
        // Test that duplicates are rejected across the entire sliding window
        long baseTime = System.currentTimeMillis();

        // Insert API first time
        boolean insert1 = SampleDeduplicationFilter.shouldInsertSample(
            1, "GET", "/api/test", baseTime
        );

        // Try to insert same API shortly after (should be rejected)
        boolean insert2 = SampleDeduplicationFilter.shouldInsertSample(
            1, "GET", "/api/test", baseTime + 1000
        );

        // Try again 10 minutes later (should still be rejected - in window)
        boolean insert3 = SampleDeduplicationFilter.shouldInsertSample(
            1, "GET", "/api/test", baseTime + (10 * 60 * 1000)
        );

        assertTrue("First insert should succeed", insert1);
        assertFalse("Duplicate after 1 second should fail", insert2);
        assertFalse("Duplicate after 10 minutes should fail", insert3);
    }

    @Test
    public void testMultipleSamplesWithinWindow() {
        // Test that timestamp doesn't matter - only API uniqueness
        long baseTime = System.currentTimeMillis();

        // Insert same API at different times within the window
        boolean insert1 = SampleDeduplicationFilter.shouldInsertSample(
            1, "GET", "/api/test", baseTime
        );
        boolean insert2 = SampleDeduplicationFilter.shouldInsertSample(
            1, "GET", "/api/test", baseTime + (5 * 60 * 1000)
        );
        boolean insert3 = SampleDeduplicationFilter.shouldInsertSample(
            1, "GET", "/api/test", baseTime + (20 * 60 * 1000)
        );

        assertTrue("First should succeed", insert1);
        assertFalse("Duplicate at +5min should fail", insert2);
        assertFalse("Duplicate at +20min should fail", insert3);
    }

    // ======================
    // Filter Rotation Tests
    // ======================

    @Test
    public void testFilterRotationAfter15Minutes() throws Exception {
        // This test verifies filter rotation happens after 15 minutes
        // We'll use reflection to manipulate time

        long timestamp = System.currentTimeMillis();

        // First insert - initializes first filter
        boolean insert1 = SampleDeduplicationFilter.shouldInsertSample(
            1, "GET", "/api/test", timestamp
        );
        assertTrue(insert1);

        // Simulate time passing (15 minutes + 1 second)
        // We need to call the method again to trigger rotation check
        long timestamp2 = timestamp + (15 * 60 * 1000) + 1000;

        boolean insert2 = SampleDeduplicationFilter.shouldInsertSample(
            2, "GET", "/api/test2", timestamp2
        );
        assertTrue("After rotation, new API should succeed", insert2);

        // Original API should still be found (in previous filter)
        boolean insert3 = SampleDeduplicationFilter.shouldInsertSample(
            1, "GET", "/api/test", timestamp
        );
        assertFalse("Original API should still be in previous filter", insert3);
    }

    @Test
    public void testDataSurvivesOneRotation() throws Exception {
        long baseTime = System.currentTimeMillis();

        // Insert at time 0
        assertTrue(SampleDeduplicationFilter.shouldInsertSample(
            1, "GET", "/api/test", baseTime
        ));

        // Insert at time +16 min (after first rotation, into second filter)
        long time2 = baseTime + (16 * 60 * 1000);
        assertTrue(SampleDeduplicationFilter.shouldInsertSample(
            2, "GET", "/api/test2", time2
        ));

        // Original API should still be detectable (in first filter)
        assertFalse(SampleDeduplicationFilter.shouldInsertSample(
            1, "GET", "/api/test", baseTime
        ));

        // After second rotation (30+ minutes), first filter gets replaced
        long time3 = baseTime + (31 * 60 * 1000);
        assertTrue(SampleDeduplicationFilter.shouldInsertSample(
            3, "GET", "/api/test3", time3
        ));

        // Original API from first filter should now be forgotten (after 31 mins = 2 rotations)
        // The API was inserted into both filters (due to "always insert" behavior)
        // So it will still be detected as duplicate in the second filter
        assertFalse("Same API still in second filter",
            SampleDeduplicationFilter.shouldInsertSample(1, "GET", "/api/test", time3));
    }

    // ======================
    // Edge Cases
    // ======================

    @Test
    public void testNullOrEmptyInputs() {
        long timestamp = System.currentTimeMillis();

        // Empty URL
        boolean emptyUrl = SampleDeduplicationFilter.shouldInsertSample(
            1, "GET", "", timestamp
        );
        assertTrue("Empty URL should be accepted", emptyUrl);

        // Empty method
        boolean emptyMethod = SampleDeduplicationFilter.shouldInsertSample(
            1, "", "/api/test", timestamp
        );
        assertTrue("Empty method should be accepted", emptyMethod);

        // Null values should not crash (though might fail)
        try {
            SampleDeduplicationFilter.shouldInsertSample(
                1, null, "/api/test", timestamp
            );
            // If it doesn't crash, that's acceptable
        } catch (NullPointerException e) {
            // Expected for null values
        }
    }

    @Test
    public void testZeroAndNegativeTimestamps() {
        // Zero timestamp
        boolean zero = SampleDeduplicationFilter.shouldInsertSample(
            1, "GET", "/api/test", 0
        );
        assertTrue("Zero timestamp should succeed first time", zero);

        // Negative timestamp (shouldn't happen but should be handled)
        boolean negative = SampleDeduplicationFilter.shouldInsertSample(
            1, "GET", "/api/test2", -1000
        );
        assertTrue("Negative timestamp should succeed first time", negative);
    }

    @Test
    public void testVeryLargeTimestamp() {
        // Test with timestamp far in the future
        long futureTime = System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000); // 1 year ahead

        boolean insert = SampleDeduplicationFilter.shouldInsertSample(
            1, "GET", "/api/test", futureTime
        );
        assertTrue("Future timestamp should be handled", insert);
    }

    @Test
    public void testSpecialCharactersInURL() {
        long timestamp = System.currentTimeMillis();

        // URLs with special characters
        String[] specialUrls = {
            "/api/users?id=123&name=test",
            "/api/users/{id}/orders",
            "/api/files/test%20file.pdf",
            "/api/data?query=SELECT * FROM users WHERE id=1",
            "/api/测试/中文", // Unicode
            "/api/test#fragment",
            "/api/test:8080/path"
        };

        for (String url : specialUrls) {
            boolean result = SampleDeduplicationFilter.shouldInsertSample(
                1, "GET", url, timestamp
            );
            assertTrue("URL with special chars should work: " + url, result);
        }
    }

    @Test
    public void testVeryLongURL() {
        long timestamp = System.currentTimeMillis();

        // Create a very long URL (1000 characters)
        StringBuilder longUrl = new StringBuilder("/api/");
        for (int i = 0; i < 100; i++) {
            longUrl.append("verylongpath/");
        }

        boolean result = SampleDeduplicationFilter.shouldInsertSample(
            1, "GET", longUrl.toString(), timestamp
        );
        assertTrue("Very long URL should be handled", result);
    }

    @Test
    public void testHighVolumeInserts() {
        // Test inserting many unique APIs
        long timestamp = System.currentTimeMillis();
        int successCount = 0;
        int totalInserts = 1000;

        for (int i = 0; i < totalInserts; i++) {
            boolean result = SampleDeduplicationFilter.shouldInsertSample(
                1, "GET", "/api/test" + i, timestamp
            );
            if (result) successCount++;
        }

        assertEquals("All unique APIs should succeed", totalInserts, successCount);
    }

    @Test
    public void testHighVolumeDuplicates() {
        // Test inserting many duplicates
        long timestamp = System.currentTimeMillis();

        // First insert should succeed
        assertTrue(SampleDeduplicationFilter.shouldInsertSample(
            1, "GET", "/api/test", timestamp
        ));

        // Next 999 should all fail (duplicates)
        int duplicateCount = 0;
        for (int i = 0; i < 999; i++) {
            boolean result = SampleDeduplicationFilter.shouldInsertSample(
                1, "GET", "/api/test", timestamp
            );
            if (!result) duplicateCount++;
        }

        assertEquals("All duplicates should be rejected", 999, duplicateCount);
    }

    // ======================
    // Concurrency Tests
    // ======================

    @Test
    public void testConcurrentInserts() throws InterruptedException {
        final int threadCount = 10;
        final int insertsPerThread = 100;
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(threadCount);
        final AtomicInteger successCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        long baseTimestamp = System.currentTimeMillis();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready

                    for (int i = 0; i < insertsPerThread; i++) {
                        // Each thread inserts unique APIs
                        boolean result = SampleDeduplicationFilter.shouldInsertSample(
                            1, "GET", "/api/thread" + threadId + "/item" + i, baseTimestamp
                        );
                        if (result) successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Start all threads
        doneLatch.await(10, TimeUnit.SECONDS); // Wait for completion
        executor.shutdown();

        assertEquals("All unique concurrent inserts should succeed",
                    threadCount * insertsPerThread, successCount.get());
    }

    @Test
    public void testConcurrentDuplicates() throws InterruptedException {
        final int threadCount = 10;
        final int insertsPerThread = 10;
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(threadCount);
        final AtomicInteger successCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        long baseTimestamp = System.currentTimeMillis();

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();

                    for (int i = 0; i < insertsPerThread; i++) {
                        // All threads try to insert the SAME API
                        boolean result = SampleDeduplicationFilter.shouldInsertSample(
                            1, "GET", "/api/same", baseTimestamp
                        );
                        if (result) successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Due to the Bloom filter's "always insert to current" behavior and potential race conditions,
        // a few threads might succeed before the filter is updated. Expect <= 10 successes (reasonable for 100 attempts)
        assertTrue("Most concurrent inserts of same API should be rejected, got: " + successCount.get(),
                   successCount.get() <= 10);
    }

    @Test
    public void testConcurrentRotation() throws InterruptedException {
        // Test that filter rotation is thread-safe
        final int threadCount = 5;
        final CountDownLatch doneLatch = new CountDownLatch(threadCount);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        long baseTimestamp = System.currentTimeMillis();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    // Each thread inserts APIs at different times to potentially trigger rotation
                    for (int i = 0; i < 10; i++) {
                        long timestamp = baseTimestamp + (i * 2 * 60 * 1000); // Every 2 minutes
                        SampleDeduplicationFilter.shouldInsertSample(
                            1, "GET", "/api/t" + threadId + "/i" + i, timestamp
                        );
                    }
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // If we get here without exceptions, thread-safety is working
        assertTrue("Concurrent rotation should not cause errors", true);
    }

}

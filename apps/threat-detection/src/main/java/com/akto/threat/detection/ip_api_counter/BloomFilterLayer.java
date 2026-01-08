package com.akto.threat.detection.ip_api_counter;

import java.util.concurrent.ConcurrentHashMap;

import com.akto.dao.context.Context;
import com.google.common.base.Charsets;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;

/**
 * Manages per-minute Bloom Filters for deduplication.
 * Used to track whether a specific key (e.g., ip|api|param|value) has been seen
 * within a time window.
 *
 * This is an in-memory only implementation - no Redis persistence.
 */
public class BloomFilterLayer {

    private static final int DATA_RETENTION_MINUTES = 15;

    private final int expectedInsertions;
    private final double falsePositiveRate;

    // Storage: minute -> BloomFilter (in-memory only)
    private final ConcurrentHashMap<Long, BloomFilter<String>> filters = new ConcurrentHashMap<>();

    /**
     * Create a new BloomFilterLayer.
     *
     * @param expectedInsertions Expected number of insertions per minute
     * @param falsePositiveRate  Desired false positive rate (e.g., 0.01 for 1%)
     */
    public BloomFilterLayer(int expectedInsertions, double falsePositiveRate) {
        this.expectedInsertions = expectedInsertions;
        this.falsePositiveRate = falsePositiveRate;
    }

    /**
     * Add a key to the Bloom Filter for the given minute.
     */
    public void put(String key, long epochMinute) {
        BloomFilter<String> bf = filters.computeIfAbsent(epochMinute,
            k -> BloomFilter.create(Funnels.stringFunnel(Charsets.UTF_8),
                                    expectedInsertions, falsePositiveRate));
        bf.put(key);
    }

    /**
     * Check if a key might be in the Bloom Filter for the given minute.
     *
     * @return true if the key might have been added (may be false positive),
     *         false if the key definitely has not been added
     */
    public boolean mightContain(String key, long epochMinute) {
        BloomFilter<String> bf = filters.get(epochMinute);
        return bf != null && bf.mightContain(key);
    }

    /**
     * Check if a key might be in any Bloom Filter within the given window range.
     *
     * @param key         The key to check
     * @param windowStart Start of the window (epoch minute)
     * @param windowEnd   End of the window (epoch minute)
     * @return true if the key might be in any minute's filter within the window
     */
    public boolean mightContainInWindow(String key, long windowStart, long windowEnd) {
        for (long min = windowStart; min <= windowEnd; min++) {
            if (mightContain(key, min)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Cleanup old Bloom Filters that are outside the retention window.
     */
    public void cleanup() {
        long currentEpochMinute = Context.now() / 60;
        long cutoff = currentEpochMinute - DATA_RETENTION_MINUTES;
        filters.entrySet().removeIf(e -> e.getKey() < cutoff);
    }

    /**
     * Reset all Bloom Filters (for testing purposes).
     */
    public void reset() {
        filters.clear();
    }

}

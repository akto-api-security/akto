package com.akto.util;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for deduplicating API samples using a sliding window of Bloom filters.
 *
 * Uses a circular buffer of 2 Bloom filters to provide a sliding window for deduplication.
 * The time window per filter is configurable via SAMPLE_DEDUP_FILTER_TIME_SECONDS env var
 * (default: 30 minutes = 1800 seconds).
 *
 * This ensures:
 * - Only one sample per API per time window is inserted
 * - Bounded memory usage (constant number of filters)
 * - No sudden insertion spikes (gradual filter expiry)
 * - Thread-safe filter rotation
 * - Configurable time window for different deployment scenarios
 */
public class SampleDeduplicationFilter {
    private static final Logger logger = LoggerFactory.getLogger(SampleDeduplicationFilter.class);

    // Configuration constants
    private static final int FILTER_TIME_LIMIT_SECONDS = getFilterTimeLimitFromEnv();
    private static final int FILTER_COUNT = 2; // 2 filters for sliding window
    private static final int EXPECTED_INSERTIONS = 10_000_000;
    private static final double FALSE_POSITIVE_PROBABILITY = 0.001;
    private static final String KEY_DELIMITER = ":";

    // Circular buffer of Bloom filters
    private static final List<BloomFilter<CharSequence>> filterList = new ArrayList<BloomFilter<CharSequence>>() {
        {
            for (int i = 0; i < FILTER_COUNT; i++) {
                add(BloomFilter.create(
                    Funnels.stringFunnel(StandardCharsets.UTF_8),
                    EXPECTED_INSERTIONS,
                    FALSE_POSITIVE_PROBABILITY
                ));
            }
        }
    };

    // Track current filter index and when it started filling
    private static int currentFilterIndex = -1;
    private static long filterFillStartTime = 0;

    /**
     * Reads the filter time limit from environment variable.
     * Defaults to 30 minutes (1800 seconds) if not set or invalid.
     *
     * @return Filter time limit in seconds
     */
    private static int getFilterTimeLimitFromEnv() {
        try {
            String envValue = System.getenv("SAMPLE_DEDUP_FILTER_TIME_SECONDS");
            if (envValue != null && !envValue.isEmpty()) {
                int seconds = Integer.parseInt(envValue);
                if (seconds > 0) {
                    return seconds;
                }
            }
        } catch (NumberFormatException e) {
            logger.warn("Invalid SAMPLE_DEDUP_FILTER_TIME_SECONDS env var, using default 30 minutes");
        }
        return 30 * 60; // Default: 30 minutes
    }

    /**
     * Checks if an API sample should be inserted based on deduplication logic.
     * Also handles filter rotation and maintains the sliding window.
     * The time-based window is handled by filter rotation (every 15 minutes),
     * not by the timestamp parameter.
     *
     * @param apiCollectionId The API collection ID
     * @param method The HTTP method
     * @param url The API URL
     * @return true if the sample is new and should be inserted, false if it's a duplicate
     */
    public static boolean shouldInsertSample(int apiCollectionId, String method, String url) {
        // Refresh filter list - rotates if needed
        refreshFilterList();

        // Create unique API key (no time bucket needed - filters handle time window)
        String apiKey = createApiKey(apiCollectionId, method, url);

        // Check if this API sample exists in any filter (30-minute sliding window)
        boolean isDuplicate = isKeyInFilters(apiKey);

        // Always insert into current filter to maintain sliding window
        insertIntoCurrentFilter(apiKey);

        return !isDuplicate;
    }

    /**
     * Refreshes the filter list by rotating to a new filter if the time limit has been exceeded.
     * Thread-safe operation.
     */
    private static synchronized void refreshFilterList() {
        long currentTime = System.currentTimeMillis() / 1000; // Convert to seconds

        // Check if it's time to rotate filters
        if (filterFillStartTime == 0 || (filterFillStartTime + FILTER_TIME_LIMIT_SECONDS) < currentTime) {
            // Create a new empty Bloom filter
            BloomFilter<CharSequence> newFilter = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                EXPECTED_INSERTIONS,
                FALSE_POSITIVE_PROBABILITY
            );

            // Update filter rotation time
            filterFillStartTime = currentTime;

            // Move to next filter index in circular fashion
            currentFilterIndex = (currentFilterIndex + 1) % FILTER_COUNT;

            // Replace the oldest filter with the new one
            if (currentFilterIndex < filterList.size()) {
                filterList.set(currentFilterIndex, newFilter);
                logger.info("Rotated Bloom filter to index {} at time {}", currentFilterIndex, currentTime);
            } else {
                filterList.add(newFilter);
                logger.info("Added new Bloom filter at index {} at time {}", currentFilterIndex, currentTime);
            }
        }
    }

    /**
     * Creates a unique key for an API sample.
     * The sliding window of Bloom filters handles time-based deduplication,
     * so no time bucket is needed in the key.
     *
     * @param apiCollectionId The API collection ID
     * @param method The HTTP method
     * @param url The API URL
     * @return A unique key string
     */
    private static String createApiKey(int apiCollectionId, String method, String url) {
        return apiCollectionId + KEY_DELIMITER + method + KEY_DELIMITER + url;
    }

    /**
     * Checks if an API key exists in any of the Bloom filters.
     *
     * @param apiKey The API key to check
     * @return true if the key might exist in any filter, false otherwise
     */
    private static boolean isKeyInFilters(String apiKey) {
        if (apiKey == null) {
            return false;
        }

        // Check all filters (both current and previous)
        for (BloomFilter<CharSequence> filter : filterList) {
            if (filter != null && filter.mightContain(apiKey)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Inserts a key into the current Bloom filter.
     *
     * @param apiKey The API key to insert
     */
    private static void insertIntoCurrentFilter(String apiKey) {
        if (currentFilterIndex >= 0 && currentFilterIndex < filterList.size()) {
            filterList.get(currentFilterIndex).put(apiKey);
        }
    }

}

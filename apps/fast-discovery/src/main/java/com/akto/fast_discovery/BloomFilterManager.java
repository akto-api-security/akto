package com.akto.fast_discovery;

import com.akto.fast_discovery.dto.ApiId;
import com.akto.log.LoggerMaker;
import com.google.common.base.Charsets;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;

import java.util.List;

/**
 * BloomFilterManager - Manages Bloom filter for fast API duplicate detection.
 *
 * Initializes Bloom filter by loading existing APIs from database-abstractor API.
 * Provides O(1) probabilistic membership check with configurable false positive rate.
 *
 * Memory usage: ~120MB for 10M APIs at 1% FPP.
 */
public class BloomFilterManager {

    private static final LoggerMaker loggerMaker = new LoggerMaker(BloomFilterManager.class);

    private BloomFilter<String> seenApis;
    private final long expectedSize;
    private final double falsePositiveRate;
    private final DatabaseAbstractorClient dbAbstractorClient;

    /**
     * Constructor with custom configuration.
     *
     * @param dbAbstractorClient HTTP client for database-abstractor
     * @param expectedSize       Expected number of APIs (default: 10,000,000)
     * @param falsePositiveRate  False positive probability (default: 0.01 = 1%)
     */
    public BloomFilterManager(DatabaseAbstractorClient dbAbstractorClient, long expectedSize, double falsePositiveRate) {
        this.dbAbstractorClient = dbAbstractorClient;
        this.expectedSize = expectedSize;
        this.falsePositiveRate = falsePositiveRate;
    }

    /**
     * Constructor with default configuration.
     */
    public BloomFilterManager(DatabaseAbstractorClient dbAbstractorClient) {
        this(dbAbstractorClient, 10_000_000L, 0.01);
    }

    /**
     * Initialize Bloom filter by loading all existing APIs via database-abstractor HTTP API.
     * This should be called once at startup.
     *
     * WARNING: This method may take 10-30 seconds depending on number of existing APIs.
     */
    public void initialize() {
        loggerMaker.infoAndAddToDb("Initializing Bloom filter with expectedSize=" + expectedSize + ", FPP=" + falsePositiveRate);
        long startTime = System.currentTimeMillis();

        // Create Bloom filter
        seenApis = BloomFilter.create(
                Funnels.stringFunnel(Charsets.UTF_8),
                expectedSize,
                falsePositiveRate
        );

        // Load existing APIs via database-abstractor HTTP API
        long count = 0;
        try {
            // Call database-abstractor endpoint to fetch all API IDs
            List<ApiId> existingApis = dbAbstractorClient.fetchApiIds();

            for (ApiId apiId : existingApis) {
                String apiKey = FastDiscoveryParser.buildApiKey(
                        apiId.getApiCollectionId(),
                        apiId.getUrl(),
                        apiId.getMethod()
                );
                seenApis.put(apiKey);
                count++;

                // Log progress every 100K APIs
                if (count % 100_000 == 0) {
                    loggerMaker.infoAndAddToDb("Loaded " + count + " APIs into Bloom filter...");
                }
            }

            long endTime = System.currentTimeMillis();
            long durationSeconds = (endTime - startTime) / 1000;

            loggerMaker.infoAndAddToDb("Bloom filter initialized with " + count + " APIs in " + durationSeconds + " seconds");
            loggerMaker.infoAndAddToDb("Estimated memory usage: ~" + estimateMemoryUsageMB() + " MB");

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Failed to initialize Bloom filter: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Bloom filter initialization failed", e);
        }
    }

    /**
     * Check if API key might exist in the Bloom filter.
     * Returns true if the API might exist (with false positive rate).
     * Returns false if the API definitely does not exist.
     *
     * @param apiKey API key in format "apiCollectionId url method"
     * @return true if might exist, false if definitely does not exist
     */
    public boolean mightContain(String apiKey) {
        if (seenApis == null) {
            throw new IllegalStateException("Bloom filter not initialized. Call initialize() first.");
        }
        return seenApis.mightContain(apiKey);
    }

    /**
     * Add API key to Bloom filter.
     * Call this after successfully inserting a new API to the database.
     *
     * @param apiKey API key in format "apiCollectionId url method"
     */
    public void add(String apiKey) {
        if (seenApis == null) {
            throw new IllegalStateException("Bloom filter not initialized. Call initialize() first.");
        }
        seenApis.put(apiKey);
    }

    /**
     * Estimate memory usage of Bloom filter in MB.
     */
    private long estimateMemoryUsageMB() {
        // Bloom filter memory = -n * ln(p) / (ln(2)^2) bits
        // where n = expected size, p = false positive rate
        // Convert bits to MB
        double bits = -expectedSize * Math.log(falsePositiveRate) / Math.pow(Math.log(2), 2);
        double bytes = bits / 8;
        double megabytes = bytes / (1024 * 1024);
        return (long) Math.ceil(megabytes);
    }

    /**
     * Get estimated memory usage in MB (for monitoring).
     */
    public long getEstimatedMemoryUsageMB() {
        return estimateMemoryUsageMB();
    }

    /**
     * Check if Bloom filter is initialized.
     */
    public boolean isInitialized() {
        return seenApis != null;
    }
}

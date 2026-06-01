package com.akto.threat.detection.ip_api_counter;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Base class for IP/API distribution integration tests.
 * Provides Redis connection, component initialization, and cleanup.
 */
public abstract class DistributionIntegrationTestBase {

    protected RedisClient redisClient;
    protected StatefulRedisConnection<String, String> redis;
    protected RedisCommands<String, String> syncRedis;

    protected DistributionCalculator distributionCalculator;
    protected DistributionStreamConsumer distributionStreamConsumer;
    protected ExecutorService executorService;

    protected static final String REDIS_URL = "redis://localhost:6379";
    protected static final String TEST_CONSUMER_ID = "test-consumer-" + System.nanoTime();

    @BeforeEach
    void setUp() throws Exception {
        // Initialize Redis connection
        redisClient = RedisClient.create(REDIS_URL);
        redis = redisClient.connect();
        syncRedis = redis.sync();

        // Clear all data
        syncRedis.flushdb();

        // Initialize components
        distributionCalculator = new DistributionCalculator(redisClient);

        // Initialize executor service for consumer thread
        executorService = Executors.newFixedThreadPool(1);

        // Note: DistributionStreamConsumer is not started by default in tests
        // Individual tests can start it if needed
    }

    @AfterEach
    void tearDown() throws Exception {
        // Stop consumer if running
        if (distributionStreamConsumer != null) {
            stopConsumer();
        }

        // Shutdown executor
        if (executorService != null) {
            executorService.shutdownNow();
        }

        // Clear Redis
        if (syncRedis != null) {
            syncRedis.flushdb();
        }

        // Close connections
        if (redis != null) {
            redis.close();
        }
        if (redisClient != null) {
            redisClient.shutdown();
        }
    }

    /**
     * Start the DistributionStreamConsumer in a background thread.
     */
    protected void startConsumer() throws Exception {
        // Ensure stream exists by adding and removing a dummy entry
        try {
            String id = syncRedis.xadd("threat_input_stream", "*", "init", "1");
            if (id != null) {
                syncRedis.xdel("threat_input_stream", id);
            }
        } catch (Exception e) {
            // Stream might already exist or have entries
        }

        if (distributionStreamConsumer == null) {
            distributionStreamConsumer = new DistributionStreamConsumer(
                redisClient,
                TEST_CONSUMER_ID,
                createMockKafkaProducer()
            );
        }
        executorService.submit(distributionStreamConsumer);
        // Give consumer time to initialize consumer group
        Thread.sleep(1000);
    }

    /**
     * Stop the consumer (if running).
     */
    protected void stopConsumer() throws Exception {
        if (distributionStreamConsumer != null) {
            // The consumer doesn't have a stop method, so we just let it run
            // It will be cleaned up in tearDown when executorService is shut down
        }
    }

    /**
     * Helper: Push multiple messages to stream via DistributionCalculator.
     */
    protected void pushMessagesToStream(int count, String ipApiCmsKey, String apiKey,
                                       long epochMin, String ip) {
        for (int i = 0; i < count; i++) {
            distributionCalculator.processRequest(
                apiKey,
                epochMin,
                ipApiCmsKey,
                30,  // rateLimitWindow
                -1,  // threshold (no check for distribution tests)
                300, // mitigationPeriod
                ip,
                "test-host",
                "test-acct",
                (int) (System.currentTimeMillis() / 1000),
                "US",
                "US"
            );
        }
    }

    /**
     * Helper: Calculate window start for a given minute and window size.
     * Formula: ((currentMin - 1) / windowSize + 1) * windowSize
     */
    protected long getWindowStartForMinute(long epochMin, int windowSize) {
        return ((epochMin - 1) / windowSize + 1) * windowSize - windowSize + 1;
    }

    /**
     * Helper: Get bucket count from distribution hash.
     */
    protected long getDistributionBucketCount(int windowSize, long windowStart,
                                             String apiKey, String bucket) {
        String distKey = "dist|" + windowSize + "|" + windowStart + "|" + apiKey;
        String count = syncRedis.hget(distKey, bucket);
        return count != null ? Long.parseLong(count) : 0;
    }

    /**
     * Helper: Check if API is in active set for a window.
     */
    protected boolean isApiActiveInWindow(int windowSize, long windowStart, String apiKey) {
        String apisKey = "distApis|" + windowSize + "|" + windowStart;
        return syncRedis.sismember(apisKey, apiKey);
    }

    /**
     * Helper: Get all buckets in a distribution hash.
     */
    protected java.util.Map<String, String> getDistributionBuckets(int windowSize, long windowStart,
                                                                    String apiKey) {
        String distKey = "dist|" + windowSize + "|" + windowStart + "|" + apiKey;
        return syncRedis.hgetall(distKey);
    }

    /**
     * Helper: Get stream length.
     */
    protected long getStreamLength() {
        return syncRedis.xlen("threat_input_stream");
    }

    /**
     * Create a mock Kafka producer (no-op for tests).
     */
    private com.akto.threat.detection.kafka.KafkaProtoProducer createMockKafkaProducer() {
        // Return a mock that doesn't try to instantiate with null config
        return org.mockito.Mockito.mock(
            com.akto.threat.detection.kafka.KafkaProtoProducer.class
        );
    }
}

package com.akto.threat.detection.ip_api_counter;

import com.akto.threat.detection.kafka.KafkaProtoProducer;
import io.lettuce.core.RedisClient;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Base class for IP/API distribution integration tests.
 *
 * All shared resources (Redis, consumer, executor) are initialized once per test class
 * in @BeforeAll and torn down in @AfterAll. This avoids shutting down the Lettuce
 * EventLoopGroup between tests, which was causing RedisCommandInterruptedException noise
 * when shutdownNow() raced with a blocking xreadgroup call.
 *
 * Between tests (@BeforeEach): Redis data is flushed and the mock Kafka producer is reset.
 */
public abstract class DistributionIntegrationTestBase {

    protected static final String REDIS_URL = "redis://localhost:6379";
    protected static final String TEST_CONSUMER_ID = "test-consumer-" + System.nanoTime();

    protected static RedisClient redisClient;
    protected static StatefulRedisConnection<String, String> redis;
    protected static RedisCommands<String, String> syncRedis;
    protected static DistributionCalculator distributionCalculator;
    protected static DistributionStreamConsumer distributionStreamConsumer;
    protected static KafkaProtoProducer mockKafka;
    protected static ExecutorService executorService;

    @BeforeAll
    static void setUpShared() throws Exception {
        redisClient = RedisClient.create(REDIS_URL);
        redis = redisClient.connect();
        syncRedis = redis.sync();
        syncRedis.flushdb();

        distributionCalculator = new DistributionCalculator(redisClient);
        mockKafka = Mockito.mock(KafkaProtoProducer.class);
        executorService = Executors.newFixedThreadPool(1);

        // Ensure stream exists before creating consumer group
        try {
            String id = syncRedis.xadd("threat_input_stream", "*", "init", "1");
            if (id != null) syncRedis.xdel("threat_input_stream", id);
        } catch (Exception ignored) {}

        distributionStreamConsumer = new DistributionStreamConsumer(redisClient, TEST_CONSUMER_ID, mockKafka);
        executorService.submit(distributionStreamConsumer);
        Thread.sleep(1000);
    }

    @AfterAll
    static void tearDownShared() throws Exception {
        if (executorService != null) {
            executorService.shutdownNow();
            executorService.awaitTermination(3, TimeUnit.SECONDS);
        }
        if (redis != null) redis.close();
        if (redisClient != null) redisClient.shutdown();
    }

    @BeforeEach
    void setUp() throws Exception {
        // Delete all keys except the stream and consumer group so the already-running
        // consumer thread never sees NOGROUP. flushdb() would destroy the group and
        // leave a window where the consumer errors before we can recreate it.
        for (String key : syncRedis.keys("*")) {
            if (!key.equals("threat_input_stream")) {
                syncRedis.del(key);
            }
        }
        // Trim all messages from the stream so each test starts with an empty stream,
        // then reset the consumer group's read position to the latest entry.
        syncRedis.xtrim("threat_input_stream", 0);
        syncRedis.xgroupSetid(XReadArgs.StreamOffset.from("threat_input_stream", "$"), "threat_group");
        Mockito.reset(mockKafka);
    }

    // -------------------------------------------------------------------------
    // Stream push helpers
    // -------------------------------------------------------------------------

    protected void pushMessagesToStream(int count, String ipApiCmsKey, String apiKey,
                                        long epochMin, String ip) {
        for (int i = 0; i < count; i++) {
            distributionCalculator.processRequest(
                apiKey, epochMin, ipApiCmsKey,
                30, -1, 300,
                ip, "test-host", "test-acct",
                (int) (System.currentTimeMillis() / 1000),
                "US", "US",
                0, 0
            );
        }
    }

    protected void pushMessagesToStreamWithApiRateLimit(int count, String ipApiCmsKey, String apiKey,
                                                        long epochMin, String ip,
                                                        int apiLevelWindow, int apiLevelThreshold) {
        for (int i = 0; i < count; i++) {
            distributionCalculator.processRequest(
                apiKey, epochMin, ipApiCmsKey,
                30, -1, 300,
                ip, "test-host", "test-acct",
                (int) (System.currentTimeMillis() / 1000),
                "US", "US",
                apiLevelWindow, apiLevelThreshold
            );
        }
    }

    // -------------------------------------------------------------------------
    // Redis read helpers
    // -------------------------------------------------------------------------

    protected long getApiCount(String apiKey, long binId) {
        String val = syncRedis.get("apiCount|" + apiKey + "|" + binId);
        return val != null ? Long.parseLong(val) : 0;
    }

    protected Double getIndexScore(String apiKey) {
        return syncRedis.zscore("apiCountIndex", apiKey);
    }

    protected long getWindowStartForMinute(long epochMin, int windowSize) {
        return ((epochMin - 1) / windowSize + 1) * windowSize - windowSize + 1;
    }

    protected long getDistributionBucketCount(int windowSize, long windowStart,
                                              String apiKey, String bucket) {
        String distKey = "dist|" + windowSize + "|" + windowStart + "|" + apiKey;
        String count = syncRedis.hget(distKey, bucket);
        return count != null ? Long.parseLong(count) : 0;
    }

    protected boolean isApiActiveInWindow(int windowSize, long windowStart, String apiKey) {
        return syncRedis.sismember("distApis|" + windowSize + "|" + windowStart, apiKey);
    }

    protected Map<String, String> getDistributionBuckets(int windowSize, long windowStart, String apiKey) {
        return syncRedis.hgetall("dist|" + windowSize + "|" + windowStart + "|" + apiKey);
    }

    protected long getStreamLength() {
        return syncRedis.xlen("threat_input_stream");
    }
}

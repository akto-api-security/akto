package com.akto.threat.detection.ip_api_counter;

import com.akto.threat.detection.kafka.KafkaProtoProducer;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class DistributionStreamConsumerGroupRecoveryTest {

    private static final String STREAM = "threat_input_stream";
    private static final String GROUP = "threat_group";

    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> connection;
    private RedisCommands<String, String> redis;
    private ExecutorService executor;

    @BeforeEach
    void setUp() throws Exception {
        redisClient = RedisClient.create("redis://localhost:6379");
        connection = redisClient.connect();
        redis = connection.sync();
        redis.flushdb();

        executor = Executors.newFixedThreadPool(1);
        executor.submit(new DistributionStreamConsumer(redisClient, "recovery-test-consumer", Mockito.mock(KafkaProtoProducer.class)));
        awaitGroup(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        executor.shutdownNow();
        executor.awaitTermination(3, TimeUnit.SECONDS);
        redis.flushdb();
        connection.close();
        redisClient.shutdown();
    }

    @Test
    void recreatesGroupFromStartWhenStreamKeyIsDeleted() throws Exception {
        redis.del(STREAM);
        assertEquals(0L, redis.exists(STREAM));

        assertTrue(awaitGroup(true), "group should be recreated after the stream key is deleted");
        assertEquals("0-0", groupLastDeliveredId());
    }

    @Test
    void recreatesGroupFromLatestWhenOnlyGroupIsDestroyed() throws Exception {
        redis.xadd(STREAM, "old", "1");
        String lastId = redis.xadd(STREAM, "old", "2");

        redis.xgroupDestroy(STREAM, GROUP);

        assertTrue(awaitGroup(true), "group should be recreated after XGROUP DESTROY");
        assertEquals(lastId, groupLastDeliveredId());
    }

    private boolean awaitGroup(boolean expected) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (groupExists() == expected) return true;
            Thread.sleep(200);
        }
        return false;
    }

    private boolean groupExists() {
        try {
            return redis.xinfoGroups(STREAM).size() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String groupLastDeliveredId() {
        List<Object> group = (List<Object>) redis.xinfoGroups(STREAM).get(0);
        for (int i = 0; i < group.size() - 1; i += 2) {
            if ("last-delivered-id".equals(String.valueOf(group.get(i)))) {
                return String.valueOf(group.get(i + 1));
            }
        }
        return null;
    }
}

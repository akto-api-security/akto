package com.akto.threat.detection.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.akto.dto.api_protection_parse_layer.Condition;
import com.akto.dto.api_protection_parse_layer.Condition.DistinctIdentifier;
import com.akto.dto.api_protection_parse_layer.Rule;
import com.akto.proto.generated.threat_detection.message.sample_request.v1.SampleMaliciousRequest;
import com.akto.threat.detection.cache.RedisBackedCounterCache;
import com.akto.threat.detection.smart_event_detector.window_based.WindowBasedThresholdNotifier;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;

/**
 * Impossible-travel distinct counting against the real RedisBackedCounterCache (localhost:6379).
 * Skipped when Redis is unreachable. Uses run-unique keys and deletes only those; never flushes.
 */
public class ImpossibleTravelRedisIntegrationTest {

    private static final String REDIS_URL = "redis://localhost:6379";
    private static final String FILTER_ID = "ImpossibleTravelIT-" + System.nanoTime();
    private static final int WINDOW_MINUTES = 30;

    private static RedisClient redisClient;
    private static StatefulRedisConnection<String, String> connection;

    @BeforeAll
    static void connect() {
        try {
            redisClient = RedisClient.create(REDIS_URL);
            connection = redisClient.connect();
            connection.sync().ping();
        } catch (Exception e) {
            assumeTrue(false, "Redis not reachable at " + REDIS_URL);
        }
    }

    @AfterAll
    static void cleanup() {
        if (connection != null) {
            for (String key : connection.sync().keys("dset|*" + FILTER_ID + "*")) {
                connection.sync().del(key);
            }
            connection.close();
        }
        if (redisClient != null) redisClient.shutdown();
    }

    private static Rule rule() {
        DistinctIdentifier distinct = new DistinctIdentifier();
        distinct.setAttribute("country_code");
        distinct.setCount(2);
        Condition condition = new Condition();
        condition.setWindowThreshold(WINDOW_MINUTES);
        condition.setDistinctIdentifier(distinct);
        return new Rule("Rule 1", condition);
    }

    private static boolean observe(WindowBasedThresholdNotifier notifier, String aggKey, String country, int minute) {
        SampleMaliciousRequest event = SampleMaliciousRequest.newBuilder().setTimestamp(minute * 60L).build();
        return notifier.shouldNotify(aggKey, event, rule(), true, true, country);
    }

    private static WindowBasedThresholdNotifier newNotifier() {
        return new WindowBasedThresholdNotifier(
                new RedisBackedCounterCache(redisClient, "wbt-it"),
                new WindowBasedThresholdNotifier.Config(100, 10 * 60));
    }

    private static Set<String> awaitMembers(String redisKey, int expected) throws InterruptedException {
        Set<String> members = connection.sync().smembers(redisKey);
        for (int i = 0; i < 50 && members.size() < expected; i++) {
            Thread.sleep(100);
            members = connection.sync().smembers(redisKey);
        }
        return members;
    }

    @Test
    void firesOnSecondCountryAndPersistsCountriesToRedis() throws Exception {
        String aggKey = "user-persist|" + FILTER_ID;
        WindowBasedThresholdNotifier notifier = newNotifier();

        assertThat(observe(notifier, aggKey, "DE", 0)).isFalse();

        // Redis holds the member for the bin the request fell into
        assertThat(awaitMembers("dset|" + aggKey + "|0", 1)).containsExactly("DE");

        assertThat(observe(notifier, aggKey, "FR", 5)).isTrue();
    }

    @Test
    void countryHistorySurvivesNewCacheInstance() throws Exception {
        String aggKey = "user-restart|" + FILTER_ID;

        assertThat(observe(newNotifier(), aggKey, "DE", 0)).isFalse();
        awaitMembers("dset|" + aggKey + "|0", 1);

        // fresh instance = empty local cache, as after a consumer restart
        assertThat(observe(newNotifier(), aggKey, "FR", 5)).isTrue();
    }

    @Test
    void sameCountryDoesNotFireAndOtherUsersAreIsolated() throws Exception {
        WindowBasedThresholdNotifier notifier = newNotifier();
        String userA = "user-a|" + FILTER_ID;
        String userB = "user-b|" + FILTER_ID;

        for (int minute = 0; minute < 5; minute++) {
            assertThat(observe(notifier, userA, "DE", minute)).isFalse();
        }
        assertThat(observe(notifier, userB, "FR", 6)).isFalse();
    }
}

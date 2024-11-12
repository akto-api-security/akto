package com.akto.malicious_request.notifier;

import com.akto.malicious_request.Request;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;

public class SaveRedisNotifier implements MaliciousRequestsNotifier {

    private final StatefulRedisConnection<String, String> redis;

    public SaveRedisNotifier(RedisClient redisClient) {
        this.redis = redisClient.connect();
    }

    @Override
    public void notifyRequest(Request request) {
        // Save the request to Redis
        request.marshall().ifPresent(data -> this.redis.sync().hset("malicious_requests", request.getId(), data));
    }
}

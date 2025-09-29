package com.akto.threat.detection.cache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import com.akto.threat.detection.cache.RedisBackedCounterCache.PendingCounterOp;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.lettuce.core.KeyValue;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;

public class ApiCountCacheLayer implements CounterCache {

    private final StatefulRedisConnection<String, Long> redis;
    private final StatefulRedisConnection<String, String> redisString;
    private final StatefulRedisConnection<String, byte[]> redisByte;
    private final Cache<String, Long> localCache;
    private final ConcurrentLinkedQueue<Object> pendingOps;
    List<String> remainingKeys = new ArrayList<>();
    List<KeyValue<String, Long>> redisResults;


    public ApiCountCacheLayer(RedisClient redisClient) {
        if (redisClient != null) {
            this.redis = redisClient.connect(new LongValueCodec());
            this.redisString = redisClient.connect();
            this.redisByte = redisClient.connect(new com.akto.threat.detection.cache.ByteArrayCodec());
        } else {
            this.redis = null;
            this.redisString = null;
            this.redisByte = null;
        }
        this.localCache = Caffeine.newBuilder().maximumSize(10000).expireAfterWrite(3, TimeUnit.HOURS).build();
        this.pendingOps = new ConcurrentLinkedQueue<>();
    }

    @Override
    public void incrementBy(String key, long val) {
        long cv = this.get(key);
        this.localCache.put(key, cv + val);
        this.pendingOps.add(new PendingCounterOp(key, val));
        if (this.pendingOps.size() >= 1000) {
            this.flush();
        }
    }

    @Override
    public void increment(String key) {
        this.incrementBy(key, 1);
    }

    @Override
    public void addToSortedSet(String sortedSetKey, String member, long score) {
        this.redisString.sync().zadd(sortedSetKey, (double) score, member);
    }

    @Override
    public long get(String key) {
        if (this.localCache.asMap().containsKey(key)) {
            return this.localCache.asMap().get(key);
        }
        Long rv = this.redis.sync().get(key);
        this.localCache.put(key, rv != null ? rv : 0L);
        return rv != null ? rv : 0L;
    }

    @Override
    public boolean exists(String key) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'exists'");
    }

    @Override
    public void reset(String key) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'reset'");
    }

    private void flush() {
        Set<String> keys = new HashSet<>();
        while (!this.pendingOps.isEmpty()) {
            PendingCounterOp op = (PendingCounterOp) this.pendingOps.poll();
            keys.add(op.getKey());
        }

        for (String key : keys) {
            long cv = this.localCache.asMap().getOrDefault(key, 0L);
            this.redis.async().setex(key, 1 * 60 * 60, cv);
        }

        this.pendingOps.clear();
    }

    @Override
    public Map<String, Long> mget(String[] keys) {
        long count = 0;
        remainingKeys.clear();
        Map<String, Long> keyValMap = new HashMap<>();
        List<String> remainingKeys = new ArrayList<>();
        for (String key: keys) {
            if (this.localCache.asMap().containsKey(key)) {
                count = this.localCache.asMap().get(key);
                keyValMap.put(key, count);
            } else {
                remainingKeys.add(key);
            }
        }
        
        redisResults = this.redis.sync().mget(remainingKeys.toArray(new String[0]));
        for (KeyValue<String, Long> kv : redisResults) {
            if (kv == null || !kv.hasValue()) {
                continue;
            }
            count = kv.getValue();
            keyValMap.put(kv.getKey(), count);
        }

        return keyValMap;
    }
    
    @Override
    public List<String> fetchMembersFromSortedSet(String sortedSet, long startRange, long endRange) {
        return this.redisString.sync().zrangebyscore(sortedSet, startRange, endRange);
    }

    @Override
    public void removeMembersFromSortedSet(String sortedSet, long startRange, long endRange) {
        this.redisString.sync().zremrangebyscore(sortedSet, startRange, endRange);
    }

    @Override
    public void set(String key, long val) {
        this.localCache.put(key, val);
        this.redis.async().set(key, val);
    }

    public void setLongWithExpiry(String key, long value, long expirySeconds) {
        try {
            this.redis.sync().setex(key, expirySeconds, value);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public long fetchLongDataFromRedis(String key) {
        try {
            return this.redis.sync().get(key);
        } catch (Exception e) {
            e.printStackTrace();
            return 0L;
        }
    }

    public void setBytesWithExpiry(String key, byte[] value, int expirySeconds) {
        try {
            this.redisByte.async().set(key, value);
            if (expirySeconds > 0) {
                this.redisByte.async().expire(key, expirySeconds);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public byte[] fetchDataBytes(String key) {
        try {
            return this.redisByte.sync().get(key);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
}

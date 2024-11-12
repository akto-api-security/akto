package com.akto.cache;

import io.lettuce.core.KeyValue;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.Optional;

import com.github.benmanes.caffeine.cache.Caffeine;

public class RedisWriteBackCache<V> implements TypeValueCache<V> {

    class InsertEntry {
        String key;
        V value;

        public InsertEntry(String key, V value) {
            this.key = key;
            this.value = value;
        }

        public String getKey() {
            return key;
        }

        public V getValue() {
            return value;
        }
    }

    private final com.github.benmanes.caffeine.cache.Cache<String, V> inMemoryCache;
    private final ConcurrentLinkedQueue<InsertEntry> insertQueue;
    private final ScheduledExecutorService scheduler;
    private final StatefulRedisConnection<String, V> redis;
    private final String prefix;

    private static final int WRITE_SYNC_INTERVAL = 10; // in seconds
    private static final int READ_SYNC_INTERVAL = 10; // in seconds

    private static final int MAX_CACHE_SIZE = 100_000;
    private static final int CACHE_EXPIRY_MINUTES = 60 * 2;

    private static final Logger logger = LoggerFactory.getLogger(RedisWriteBackCache.class);

    public RedisWriteBackCache(RedisClient redisClient, String prefix) {
        this.redis = redisClient.connect(new TypeValueCode<V>());
        this.prefix = prefix;
        this.insertQueue = new ConcurrentLinkedQueue<>();

        // Schedule a task to write the batch to Redis every minute
        this.scheduler = Executors.newScheduledThreadPool(1);

        // Periodically sync the in-memory cache with Redis
        this.scheduler.scheduleAtFixedRate(
                this::syncWrite, WRITE_SYNC_INTERVAL, WRITE_SYNC_INTERVAL, TimeUnit.SECONDS);
        this.scheduler.scheduleAtFixedRate(
                this::syncRead, READ_SYNC_INTERVAL, READ_SYNC_INTERVAL, TimeUnit.SECONDS);

        this.inMemoryCache = Caffeine.newBuilder()
                .maximumSize(MAX_CACHE_SIZE)
                .expireAfterWrite(CACHE_EXPIRY_MINUTES, TimeUnit.MINUTES)
                .build();
    }

    public boolean containsKey(String key) {
        return inMemoryCache.asMap().containsKey(key);
    }

    public Optional<V> get(String key) {
        try {
            return Optional.ofNullable(inMemoryCache.getIfPresent(key));
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }

    }

    public V getOrDefault(String key, V defaultValue) {
        try {
            return this.get(key).orElse(defaultValue);
        } catch (Exception e) {
            e.printStackTrace();
            return defaultValue;
        }

    }

    public long size() {
        return inMemoryCache.estimatedSize();
    }

    public void put(String key, V value) {
        inMemoryCache.put(key, value);

        // Add the key to the insert queue
        insertQueue.add(new InsertEntry(key, value));
    }

    public void destroy() {
        scheduler.shutdown();
        redis.close();
    }

    public void syncWrite() {
        if (insertQueue.isEmpty()) {
            logger.info("[REDIS-{}] No writes to sync", prefix);
            return;
        }

        try {
            RedisAsyncCommands<String, V> async = this.redis.async();
            Map<String, V> map = new HashMap<>();
            for (InsertEntry entry : insertQueue) {
                map.put(entry.getKey(), entry.getValue());
            }

            async.hset(this.prefix, map).whenComplete((result, ex) -> {
                if (ex != null) {
                    logger.error("[REDIS-{}] Failed to write to Redis", prefix, ex);
                    return;
                }

                this.insertQueue.clear();
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void syncRead() {
        try {
            if (this.size() == 0L) {
                logger.debug("[REDIS-{}] No reads to sync", prefix);
                return;
            }

            RedisAsyncCommands<String, V> async = this.redis.async();
            String[] keys = this.inMemoryCache.asMap().keySet().toArray(new String[0]);

            // NOTE: We only read the keys that are present in local cache
            async.hmget(prefix, keys).whenComplete((dlist, ex) -> {
                if (ex != null) {
                    logger.error("[REDIS-{}] Failed to read from Redis", prefix, ex);
                    return;
                }

                for (KeyValue<String, V> kv : dlist) {
                    if (kv.hasValue()) {
                        logger.debug("[REDIS-{}] Syncing key: {}, value: {}", prefix, kv.getKey(), kv.getValue());
                        inMemoryCache.put(kv.getKey(), kv.getValue());
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            logger.debug("[REDIS-{}] Synced reads with Redis", prefix);
        }
    }
}
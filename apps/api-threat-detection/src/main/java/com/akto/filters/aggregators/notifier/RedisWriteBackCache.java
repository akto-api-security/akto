package com.akto.filters.aggregators.notifier;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.codec.RedisCodec;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SerializedObjectCodec<V> implements RedisCodec<String, V> {
    private final Charset charset = StandardCharsets.UTF_8;

    public String decodeKey(ByteBuffer bytes) {
        return charset.decode(bytes).toString();
    }

    public V decodeValue(ByteBuffer bytes) {
        try {
            byte[] array = new byte[bytes.remaining()];
            bytes.get(array);
            ObjectInputStream is = new ObjectInputStream(new ByteArrayInputStream(array));
            return new ObjectMapper().readValue(is, new TypeReference<V>() {});
        } catch (Exception e) {
            return null;
        }
    }

    public ByteBuffer encodeKey(String key) {
        return charset.encode(key);
    }

    public ByteBuffer encodeValue(V value) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ObjectOutputStream os = new ObjectOutputStream(bytes);
            os.writeObject(value);
            return ByteBuffer.wrap(bytes.toByteArray());
        } catch (IOException e) {
            return null;
        }
    }
}

public class RedisWriteBackCache<V> {

    class InsertEntry {
        String key;
        V value;

        public InsertEntry(String key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    private final ConcurrentMap<String, V> inMemoryCache;
    private final ConcurrentLinkedQueue<InsertEntry> insertQueue;
    private final ScheduledExecutorService scheduler;
    private final StatefulRedisConnection<String, V> redis;
    private final String prefix;

    private static final int WRITE_SYNC_INTERVAL = 10; // in minutes
    private static final int READ_SYNC_INTERVAL = 10; // in minutes

    private static final int MAX_INSERT_QUEUE_SIZE = 100;

    private static final Logger logger = LoggerFactory.getLogger(RedisWriteBackCache.class);

    public RedisWriteBackCache(RedisClient redisClient, String prefix) {
        this.redis = redisClient.connect(new SerializedObjectCodec<>());
        this.prefix = prefix;
        this.inMemoryCache = new ConcurrentHashMap<>();
        this.insertQueue = new ConcurrentLinkedQueue<>();

        // Schedule a task to write the batch to Redis every minute
        this.scheduler = Executors.newScheduledThreadPool(2);

        // Periodically sync the in-memory cache with Redis
        this.scheduler.scheduleAtFixedRate(
                this::syncWrite, WRITE_SYNC_INTERVAL, WRITE_SYNC_INTERVAL, TimeUnit.SECONDS);
        this.scheduler.scheduleAtFixedRate(this::syncRead, 0, READ_SYNC_INTERVAL, TimeUnit.SECONDS);
    }

    public boolean containsKey(String key) {
        return inMemoryCache.containsKey(key);
    }

    public V get(String key) {
        return inMemoryCache.get(key);
    }

    public V getOrDefault(String key, V defaultValue) {
        return inMemoryCache.getOrDefault(key, defaultValue);
    }

    public void put(String key, V value) {
        inMemoryCache.put(key, value);

        // Add the key to the insert queue
        insertQueue.add(new InsertEntry(key, value));

        if (insertQueue.size() >= MAX_INSERT_QUEUE_SIZE) {
            this.syncWrite();
        }
    }

    public void syncWrite() {
        try {
            RedisAsyncCommands<String, V> async = this.redis.async();
            Map<String, V> map = new HashMap<>();
            for (Map.Entry<String, V> entry : inMemoryCache.entrySet()) {
                map.put(entry.getKey(), entry.getValue());
            }

            async.hset(this.prefix, map)
                    .whenComplete(
                            (result, ex) -> {
                                if (ex != null) {
                                    logger.error("Failed to write to Redis: " + ex.getMessage());
                                    return;
                                }

                                this.insertQueue.clear();
                            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void syncRead() {
        try {
            RedisAsyncCommands<String, V> async = this.redis.async();
            async.hgetall(prefix)
                    .whenComplete(
                            (map, ex) -> {
                                if (ex != null) {
                                    logger.error("Failed to read from Redis: " + ex.getMessage());
                                    return;
                                }

                                this.inMemoryCache.putAll(map);
                            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void close() {
        scheduler.shutdown();
        redis.close();
    }
}

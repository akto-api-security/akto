package com.akto.threat.detection.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.lettuce.core.ExpireArgs;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;

public class RedisBackedCounterCache implements CounterCache {

  static class Op {
    private final String key;
    private final long value;

    public Op(String key, long value) {
      this.key = key;
      this.value = value;
    }

    public String getKey() {
      return key;
    }

    public long getValue() {
      return value;
    }
  }

  private final StatefulRedisConnection<String, Long> redis;

  private final Cache<String, Long> localCache;

  private final ConcurrentLinkedQueue<Op> pendingIncOps;
  private final ConcurrentMap<String, Boolean> deletedKeys;
  private final String prefix;

  public RedisBackedCounterCache(RedisClient redisClient, String prefix) {
    this.prefix = prefix;
    this.redis = redisClient.connect(new LongValueCodec());
    this.localCache =
        Caffeine.newBuilder().maximumSize(100000).expireAfterWrite(3, TimeUnit.HOURS).build();

    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    executor.scheduleAtFixedRate(this::syncToRedis, 60, 5, TimeUnit.SECONDS);

    this.pendingIncOps = new ConcurrentLinkedQueue<>();
    this.deletedKeys = new ConcurrentHashMap<>();
  }

  private String addPrefixToKey(String key) {
    return new StringBuilder().append(prefix).append("|").append(key).toString();
  }

  @Override
  public void increment(String key) {
    incrementBy(key, 1);
  }

  @Override
  public void incrementBy(String key, long val) {
    String _key = addPrefixToKey(key);
    localCache.asMap().merge(_key, val, Long::sum);
    pendingIncOps.add(new Op(_key, val));
  }

  @Override
  public long get(String key) {
    return Optional.ofNullable(this.localCache.getIfPresent(addPrefixToKey(key))).orElse(0L);
  }

  @Override
  public boolean exists(String key) {
    return localCache.asMap().containsKey(addPrefixToKey(key));
  }

  @Override
  public void clear(String key) {
    String _key = addPrefixToKey(key);
    localCache.invalidate(_key);
    this.deletedKeys.put(_key, true);
    redis.async().del(_key);
  }

  private void setExpiryIfNotSet(String key, long seconds) {
    // We only set expiry for redis entry. For local cache we have lower expiry for
    // all entries.
    ExpireArgs args = ExpireArgs.Builder.nx();
    redis.async().expire(addPrefixToKey(key), seconds, args);
  }

  private void syncToRedis() {
    Set<String> _keys = new HashSet<>();
    while (!pendingIncOps.isEmpty()) {
      Op op = pendingIncOps.poll();
      String key = op.getKey();
      long val = op.getValue();

      if (this.deletedKeys.containsKey(key)) {
        continue;
      }

      redis
          .async()
          .incrby(key, val)
          .whenComplete(
              (result, ex) -> {
                if (ex != null) {
                  ex.printStackTrace();
                }

                _keys.add(key);

                if (result != null) {
                  localCache.asMap().put(key, result);
                }
              });
    }

    _keys.forEach(key -> setExpiryIfNotSet(key, 3 * 60 * 60));

    this.deletedKeys.clear();
  }
}

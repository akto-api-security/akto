package com.akto.threat.detection.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.lettuce.core.ExpireArgs;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import java.util.Optional;
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

  private final ConcurrentLinkedQueue<Op> pendingOps;
  private final String prefix;

  public RedisBackedCounterCache(RedisClient redisClient, String prefix) {
    this.prefix = prefix;
    this.redis = redisClient.connect(new LongValueCodec());
    this.localCache =
        Caffeine.newBuilder().maximumSize(100000).expireAfterWrite(3, TimeUnit.HOURS).build();

    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    executor.scheduleAtFixedRate(this::syncToRedis, 60, 5, TimeUnit.SECONDS);

    this.pendingOps = new ConcurrentLinkedQueue<>();
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
    pendingOps.add(new Op(_key, val));

    this.setExpiryIfNotSet(_key, 3 * 60 * 60); // added 3 hours expiry for now
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
    localCache.invalidate(addPrefixToKey(key));
    redis.async().del(addPrefixToKey(key));
  }

  private void setExpiryIfNotSet(String key, long seconds) {
    // We only set expiry for redis entry. For local cache we have lower expiry for
    // all entries.
    ExpireArgs args = ExpireArgs.Builder.nx();
    redis.async().expire(addPrefixToKey(key), seconds, args);
  }

  private void syncToRedis() {
    while (!pendingOps.isEmpty()) {
      Op op = pendingOps.poll();
      String key = op.getKey();
      long val = op.getValue();
      redis
          .async()
          .incrby(key, val)
          .whenComplete(
              (result, ex) -> {
                if (ex != null) {
                  ex.printStackTrace();
                }

                if (result != null) {
                  localCache.asMap().put(key, result);
                }
              });
    }
  }
}

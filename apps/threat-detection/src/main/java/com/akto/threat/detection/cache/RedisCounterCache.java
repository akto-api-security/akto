package com.akto.threat.detection.cache;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;

public class RedisCounterCache implements CounterCache {

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

  private final String prefix;

  public RedisCounterCache(RedisClient redisClient, String prefix) {
    this.prefix = prefix;
    this.redis = redisClient.connect(new LongValueCodec());
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
    redis.async().incrby(addPrefixToKey(key), val);
  }

  @Override
  public long get(String key) {
    return redis.sync().get(addPrefixToKey(key));
  }

  @Override
  public boolean exists(String key) {
    return redis.sync().exists(addPrefixToKey(key)) > 0;
  }

  @Override
  public void clear(String key) {
    redis.async().del(addPrefixToKey(key));
  }

}

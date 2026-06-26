package com.akto.threat.detection.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.*;

public class RedisBackedCounterCache implements CounterCache {
  private final StatefulRedisConnection<String, Long> redis;
  private final StatefulRedisConnection<String, String> stringRedis;

  private final Cache<String, Long> localCache;
  private final Cache<String, Set<String>> localSetCache;

  private final String prefix;
  private final ConcurrentLinkedQueue<Object> pendingOps;

  static class PendingCounterOp {
    private final String key;
    private final long value;

    public PendingCounterOp(String key, long value) {
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

  public RedisBackedCounterCache(RedisClient redisClient, String prefix) {
    this.prefix = prefix;
    if (redisClient != null) {
      this.redis = redisClient.connect(new LongValueCodec());
      this.stringRedis = redisClient.connect();
    } else {
      this.redis = null;
      this.stringRedis = null;
    }
    this.localCache = Caffeine.newBuilder().maximumSize(10000).expireAfterWrite(3, TimeUnit.HOURS).build();
    this.localSetCache = Caffeine.newBuilder().maximumSize(10000).expireAfterWrite(3, TimeUnit.HOURS).build();
    this.pendingOps = new ConcurrentLinkedQueue<>();
  }

  @Override
  public void increment(String key) {
    this.incrementBy(key, 1);
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
  public long get(String key) {
    if (this.localCache.asMap().containsKey(key)) {
      return this.localCache.asMap().get(key);
    }

    Long rv = this.redis.sync().hget(prefix, key);

    this.localCache.put(key, rv != null ? rv : 0L);
    return rv != null ? rv : 0L;
  }

  @Override
  public boolean exists(String key) {
    return this.localCache.asMap().containsKey(key);
  }

  @Override
  public void reset(String key) {
    this.localCache.put(key, 0L);
    this.redis.async().hset(prefix, key, 0L);
  }

  @Override
  public void addToSet(String key, String member) {
    if (this.stringRedis == null) return;
    Set<String> localSet = this.localSetCache.get(key, k -> ConcurrentHashMap.newKeySet());
    localSet.add(member);
    this.stringRedis.async().sadd(key, member);
    this.stringRedis.async().expire(key, 3 * 60 * 60);
  }

  @Override
  public Set<String> getSetMembers(String key) {
    Set<String> localSet = this.localSetCache.getIfPresent(key);
    if (localSet != null) {
      return localSet;
    }
    if (this.stringRedis == null) return Collections.emptySet();
    Set<String> members = this.stringRedis.sync().smembers(key);
    if (members != null && !members.isEmpty()) {
      Set<String> concurrentSet = ConcurrentHashMap.newKeySet();
      concurrentSet.addAll(members);
      this.localSetCache.put(key, concurrentSet);
      return concurrentSet;
    }
    return Collections.emptySet();
  }

  public void resetSet(String key) {
    this.localSetCache.invalidate(key);
    if (this.stringRedis != null) {
      this.stringRedis.async().del(key);
    }
  }

  private void flush() {
    Set<String> keys = new HashSet<>();
    while (!this.pendingOps.isEmpty()) {
      PendingCounterOp op = (PendingCounterOp) this.pendingOps.poll();
      keys.add(op.getKey());
    }

    Map<String, Long> val = new HashMap<>();
    for (String key : keys) {
      long cv = this.localCache.asMap().getOrDefault(key, 0L);
      val.put(key, cv);
    }

    this.redis.async().hset(prefix, val);
    val.forEach((k, v) -> this.redis.async().expire(k, 3 * 60 * 60));

    this.pendingOps.clear();
  }

  @Override
  public void addToSortedSet(String sortedSetKey, String member, long score) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'addToSortedSet'");
  }

  @Override
  public Map<String, Long> mget(String[] keys) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'queryTotalCount'");
  }

  @Override
  public List<String> fetchMembersFromSortedSet(String sortedSet, long startRange, long endRange) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'fetchMembersFromSortedSet'");
  }

  @Override
  public void set(String key, long val) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'set'");
  }

  @Override
  public void removeMembersFromSortedSet(String sortedSet, long startRange, long endRange) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'removeMembersFromSortedSet'");
  }

  @Override
  public void setBytesWithExpiry(String key, byte[] value, int expirySeconds) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'setBytesWithExpiry'");
  }

  @Override
  public byte[] fetchDataBytes(String key) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'fetchDataBytes'");
  }

}
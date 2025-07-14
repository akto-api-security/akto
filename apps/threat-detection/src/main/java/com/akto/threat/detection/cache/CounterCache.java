package com.akto.threat.detection.cache;

import java.util.List;
import java.util.Map;

public interface CounterCache {

  void incrementBy(String key, long val);

  void increment(String key);

  long get(String key);

  boolean exists(String key);

  void reset(String key);

  void addToSortedSet(String sortedSetKey, String member, long score);

  Map<String, Long> mget(String[] keys);

  List<String> fetchMembersFromSortedSet(String sortedSet, long startRange, long endRange);

  void set(String key, long val);

  void removeMembersFromSortedSet(String sortedSet, long startRange, long endRange);

  void setBytesWithExpiry(String key, byte[] value, int expirySeconds);

  byte[] fetchDataBytes(String key);
}

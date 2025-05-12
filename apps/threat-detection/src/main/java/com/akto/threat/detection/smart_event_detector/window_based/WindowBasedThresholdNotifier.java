package com.akto.threat.detection.smart_event_detector.window_based;

import com.akto.dto.api_protection_parse_layer.Rule;
import com.akto.proto.generated.threat_detection.message.sample_request.v1.SampleMaliciousRequest;
import com.akto.threat.detection.cache.CounterCache;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WindowBasedThresholdNotifier {

  private final Config config;
  private static List<String> keys = new ArrayList<>();

  public static class Config {
    private final int threshold;
    private final int windowSizeInMinutes;
    private int notificationCooldownInSeconds = 60 * 30; // 30 mins

    public Config(int threshold, int windowInSeconds) {
      this.threshold = threshold;
      this.windowSizeInMinutes = windowInSeconds;
    }

    public int getThreshold() {
      return threshold;
    }

    public int getWindowSizeInMinutes() {
      return windowSizeInMinutes;
    }

    public int getNotificationCooldownInSeconds() {
      return notificationCooldownInSeconds;
    }
  }

  public static class Result {
    private final boolean shouldNotify;

    public Result(boolean shouldNotify) {
      this.shouldNotify = shouldNotify;
    }

    public boolean shouldNotify() {
      return shouldNotify;
    }
  }

  public Config getConfig() {
    return config;
  }

  private final CounterCache cache;

  public WindowBasedThresholdNotifier(CounterCache cache, Config config) {
    this.cache = cache;
    this.config = config;
  }

  public boolean shouldNotify(String aggKey, SampleMaliciousRequest maliciousEvent, Rule rule) {
    int binId = (int) maliciousEvent.getTimestamp() / 60;
    String cacheKey = aggKey + "|" + binId;
    this.cache.increment(cacheKey);

    long windowCount = 0L;
    List<Bin> bins = getBins(aggKey, binId - rule.getCondition().getWindowThreshold() + 1, binId);
    for (Bin data : bins) {
      windowCount += data.getCount();
    }

    boolean thresholdBreached = windowCount >= rule.getCondition().getMatchCount();

    if (thresholdBreached) {
      this.cache.reset(cacheKey);
    }

    return thresholdBreached;
  }

  public List<Bin> getBins(String aggKey, int binStart, int binEnd) {
    List<Bin> binData = new ArrayList<>();
    for (int i = binStart; i <= binEnd; i++) {
      String key = aggKey + "|" + i;
      if (!this.cache.exists(key)) {
        continue;
      }
      binData.add(new Bin(i, this.cache.get(key)));
    }
    return binData;
  }

  public void incrementApiHitcount(String key, int ts, String sortedSetKey) {
    if (this.cache == null) {
        return;
    }
    int binId = (int) ts / 60;
    String cachekey = key + "|" + binId;
    this.cache.increment(cachekey);
    this.cache.addToSortedSet(sortedSetKey, cachekey, binId);
  }


  public boolean calcApiCount(String aggKey, int timestamp, Rule rule) {
      int binId = timestamp/60;
      int startBinId = binId - rule.getCondition().getWindowThreshold() + 1;
      String cacheKey;
      keys.clear();
      for (int i = startBinId; i <= binId; i++) {
        cacheKey = aggKey + "|" + i;
        keys.add(cacheKey);
      }
      Map<String, Long> keyValData = this.cache.mget(keys.toArray(new String[0]));
      long windowCount = keyValData.values().stream().mapToLong(Long::longValue).sum();

      boolean thresholdBreached = windowCount >= rule.getCondition().getMatchCount();
      return thresholdBreached;
  }

}

package com.akto.threat.detection.smart_event_detector.window_based;

import com.akto.dto.api_protection_parse_layer.Rule;
import com.akto.proto.generated.threat_detection.message.sample_request.v1.SampleMaliciousRequest;
import com.akto.threat.detection.cache.CounterCache;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class WindowBasedThresholdNotifier {

  private final Config config;

  // We can use an in-memory cache for this, since we dont mind being notified
  // more than once by multiple instances of the service.
  // But on 1 instance, we should not notify more than once in the cooldown
  // period.
  // TODO: Move this to redis
  private final ConcurrentMap<String, Long> notifiedMap;

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
    this.notifiedMap = new ConcurrentHashMap<>();
  }

  public Result shouldNotify(String aggKey, SampleMaliciousRequest maliciousEvent, Rule rule) {
    int binId = (int) maliciousEvent.getTimestamp() / 60;
    String cacheKey = aggKey + "|" + binId;
    this.cache.increment(cacheKey);

    long windowCount = 0L;
    List<Bin> bins = getBins(aggKey, binId - rule.getCondition().getWindowThreshold() + 1, binId);
    for (Bin data : bins) {
      windowCount += data.getCount();
    }

    boolean thresholdBreached = windowCount >= rule.getCondition().getMatchCount();

    long now = System.currentTimeMillis() / 1000L;
    long lastNotified = this.notifiedMap.getOrDefault(aggKey, 0L);

    boolean cooldownBreached =
        (now - lastNotified) >= this.config.getNotificationCooldownInSeconds();

    if (thresholdBreached) {
      this.notifiedMap.put(aggKey, now);
      return new Result(true);
    }

    return new Result(false);
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
}

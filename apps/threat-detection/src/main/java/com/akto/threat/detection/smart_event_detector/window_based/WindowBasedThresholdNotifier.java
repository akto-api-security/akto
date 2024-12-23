package com.akto.threat.detection.smart_event_detector.window_based;

import com.akto.dto.api_protection_parse_layer.Rule;
import com.akto.proto.generated.threat_detection.message.sample_request.v1.SampleMaliciousRequest;
import com.akto.threat.detection.cache.CounterCache;
import java.util.ArrayList;
import java.util.List;

public class WindowBasedThresholdNotifier {

  private final Config config;

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

    if (thresholdBreached) {
      this.cache.clear(cacheKey);
    }

    return new Result(thresholdBreached);
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

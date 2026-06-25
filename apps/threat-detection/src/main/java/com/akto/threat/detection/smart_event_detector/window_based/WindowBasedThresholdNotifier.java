package com.akto.threat.detection.smart_event_detector.window_based;

import com.akto.dto.api_protection_parse_layer.Rule;
import com.akto.proto.generated.threat_detection.message.sample_request.v1.SampleMaliciousRequest;
import com.akto.threat.detection.cache.CounterCache;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

  public boolean shouldNotify(String aggKey, SampleMaliciousRequest maliciousEvent, Rule rule, boolean shouldIncrement, boolean breachFilterPassed) {
    return shouldNotify(aggKey, maliciousEvent, rule, shouldIncrement, breachFilterPassed, null);
  }

  public boolean shouldNotify(String aggKey, SampleMaliciousRequest maliciousEvent, Rule rule, boolean shouldIncrement, boolean breachFilterPassed, String identity) {
    int binId = (int) maliciousEvent.getTimestamp() / 60;

    boolean isDistinctMode = rule.getCondition().getDistinctIdentifier() != null;

    if (isDistinctMode) {
      return shouldNotifyDistinct(aggKey, binId, rule, shouldIncrement, breachFilterPassed, identity);
    }

    String cacheKey = aggKey + "|" + binId;

    if (shouldIncrement) {
      this.cache.increment(cacheKey);
    }

    long windowCount = 0L;
    List<Bin> bins = getBins(aggKey, binId - rule.getCondition().getWindowThreshold() + 1, binId);
    for (Bin data : bins) {
      windowCount += data.getCount();
    }

    boolean thresholdBreached = windowCount >= rule.getCondition().getMatchCount();

    // Only reset and notify if breachFilter has also passed (or no breachFilter specified)
    if (thresholdBreached && breachFilterPassed) {
      this.cache.reset(cacheKey);
      return true;
    }

    return false;
  }

  private boolean shouldNotifyDistinct(String aggKey, int binId, Rule rule, boolean shouldIncrement, boolean breachFilterPassed, String identity) {
    if (identity == null || identity.isEmpty()) {
      return false;
    }

    String setKeyPrefix = "dset|" + aggKey;
    String currentBinKey = setKeyPrefix + "|" + binId;

    if (shouldIncrement) {
      this.cache.addToSet(currentBinKey, identity);
    }

    if (!breachFilterPassed) {
      return false;
    }

    // Union distinct members across all bins in window
    int windowStart = binId - rule.getCondition().getWindowThreshold() + 1;
    Set<String> distinctMembers = new HashSet<>();
    for (int i = windowStart; i <= binId; i++) {
      String binKey = setKeyPrefix + "|" + i;
      Set<String> members = this.cache.getSetMembers(binKey);
      distinctMembers.addAll(members);
    }

    if (distinctMembers.size() >= rule.getCondition().getDistinctIdentifier().getCount()) {
      this.cache.resetSet(currentBinKey);
      return true;
    }

    return false;
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

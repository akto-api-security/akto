package com.akto.filters.aggregators.window_based;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.api_protection_parse_layer.Condition;
import com.akto.dto.api_protection_parse_layer.Rule;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.filters.aggregators.window_based.WindowBasedThresholdNotifier.Result;
import com.akto.proto.threat_protection.consumer_service.v1.MaliciousEvent;

import java.util.concurrent.ConcurrentHashMap;

import org.junit.Test;

import com.akto.cache.CounterCache;

class MemCache implements CounterCache {

  private final ConcurrentHashMap<String, Long> cache;

  public MemCache() {
    this.cache = new ConcurrentHashMap<>();
  }

  @Override
  public void incrementBy(String key, long val) {
    cache.put(key, cache.getOrDefault(key, 0L) + val);
  }

  @Override
  public void increment(String key) {
    incrementBy(key, 1);
  }

  @Override
  public long get(String key) {
    return cache.getOrDefault(key, 0L);
  }

  @Override
  public boolean exists(String key) {
    return cache.containsKey(key);
  }

  public Map<String, Long> internalCache() {
    return cache;
  }
}

public class WindowBasedThresholdNotifierTest {
  private static HttpResponseParams generateResponseParamsForStatusCode(int statusCode) {
    return new HttpResponseParams(
        "HTTP/1.1",
        statusCode,
        "Bad Request",
        new HashMap<>(),
        "{'error': 'Bad Request'}",
        new HttpRequestParams(
            "POST", "/api/v1/endpoint", "HTTP/1.1", new HashMap<>(), "{'error': 'Bad Request'}", 1),
        (int) (System.currentTimeMillis() / 1000L),
        "100000",
        false,
        HttpResponseParams.Source.OTHER,
        "",
        "192.168.0.1");
  }

  @Test
  public void testShouldNotify() throws InterruptedException {

    MemCache cache = new MemCache();
    WindowBasedThresholdNotifier notifier =
        new WindowBasedThresholdNotifier(cache, new WindowBasedThresholdNotifier.Config(10, 1));

    boolean shouldNotify = false;
    String ip = "192.168.0.1";

    FilterConfig filterConfig = new FilterConfig();
    filterConfig.setId("4XX_FILTER");

    for (int i = 0; i < 1000; i++) {
      HttpResponseParams responseParams = generateResponseParamsForStatusCode(400);
      Result res =
          notifier.shouldNotify(
              ip + "|" + "4XX_FILTER",
              MaliciousEvent.newBuilder()
                  .setActorId(ip)
                  .setIp(ip)
                  .setTimestamp(responseParams.getTime())
                  .setApiCollectionId(responseParams.getRequestParams().getApiCollectionId())
                  .setMethod(responseParams.getRequestParams().getMethod())
                  .setUrl(responseParams.getRequestParams().getURL())
                  .setPayload(responseParams.getOrig())
                  .build(),
              new Rule("4XX_FILTER", new Condition(10, 1)));
      shouldNotify = shouldNotify || res.shouldNotify();
    }

    long count = 0;
    for (Map.Entry<String, Long> entry : cache.internalCache().entrySet()) {
      count += entry.getValue();
    }

    assertEquals(1000, count);
  }
}

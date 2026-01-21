package com.akto.threat.detection.ip_api_counter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.akto.ProtoMessageUtils;
import com.akto.dao.context.Context;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ApiDistributionDataRequestPayload;
import com.akto.testing.ApiExecutor;
import com.akto.threat.detection.cache.ApiCountCacheLayer;
import com.akto.threat.detection.cache.CounterCache;
import com.akto.threat.detection.constants.RedisKeyInfo;
import com.akto.threat.detection.utils.Utils;

import io.lettuce.core.RedisClient;

public class DistributionDataForwardLayer {

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final LoggerMaker logger = new LoggerMaker(DistributionDataForwardLayer.class, LogDb.THREAT_DETECTION);
    private static final int LOOKBACK_HOURS = 8; // Look back 8 hours for delayed events
    private static final int WINDOW_TRACKING_TTL_HOURS = 24; // Track sent windows for 24 hours (3x lookback)
    private static CounterCache cache;
    private final DistributionCalculator distributionCalculator;

    public DistributionDataForwardLayer(RedisClient redisClient, DistributionCalculator distributionCalculator) {
        if (redisClient != null) {
            cache = new ApiCountCacheLayer(redisClient);
        } else {
            cache = null;
        }
        this.distributionCalculator = distributionCalculator;
    }

    public void sendLastFiveMinuteDistributionData() {
        scheduler.scheduleAtFixedRate(() -> {
            buildPayloadAndForwardData();
        }, 0, 1, TimeUnit.MINUTES);
    }

    public void buildPayloadAndForwardData() {
        try {
            logger.info("sendLastFiveMinuteDistributionData cron started {}", Context.now());

            List<Integer> windowSizes = Arrays.asList(5, 15, 30);

            for (int windowSize : windowSizes) {
                long currentEpochMin = Context.now() / 60;
                // Align the current time to the nearest window size. 12:03 will align to 12:00 for a 5-minute window.
                long currentAlignedWindowEnd = (currentEpochMin / windowSize) * windowSize;

                // Determine the safe window end to ensure only completed windows are processed.
                long safeWindowEnd = currentAlignedWindowEnd - windowSize;

                // Look back 8 hours unconditionally to handle Kafka lag
                long windowStart = safeWindowEnd - (LOOKBACK_HOURS * 60);

                for (long i = windowStart; i <= safeWindowEnd; i += windowSize) {

                    // Check if this window was already sent (per-window tracking)
                    if (isWindowAlreadySent(windowSize, i + 1)) {
                        logger.debug("Window {} for windowSize {} already sent, skipping", i + 1, windowSize);
                        continue;
                    }

                    Map<String, Map<String, Integer>> apiToBuckets = distributionCalculator.getBucketDistribution(windowSize, i + 1);
                    if (apiToBuckets == null || apiToBuckets.isEmpty()) {
                        logger.debug("No distribution data for windowSize={} windowStart={}", windowSize, i + 1);
                        continue;
                    }

                    List<ApiDistributionDataRequestPayload.DistributionData> batch = new ArrayList<>();

                    for (Map.Entry<String, Map<String, Integer>> apiEntry : apiToBuckets.entrySet()) {
                        String apiKey = apiEntry.getKey();
                        Map<String, Integer> distribution = apiEntry.getValue();

                        if (distribution == null || distribution.isEmpty()) continue;

                        String[] parts = apiKey.split("\\|");
                        String apiCollectionIdStr = parts[0];
                        int apiCollectionId = Integer.parseInt(apiCollectionIdStr);
                        String url = parts[1];
                        String method = parts[2];

                        ApiDistributionDataRequestPayload.DistributionData data =
                            ApiDistributionDataRequestPayload.DistributionData.newBuilder()
                                .setApiCollectionId(apiCollectionId)
                                .setUrl(url)
                                .setMethod(method)
                                .setWindowSize(windowSize)
                                .setWindowStartEpochMin(i + 1)
                                .putAllDistribution(distribution)
                                .build();

                        batch.add(data);
                    }

                    if (!batch.isEmpty()) {
                        // Send to backend and only mark as sent on success
                        boolean success = sendDistributionDataToBackend(batch, windowSize, i + 1);
                        if (success) {
                            markWindowAsSent(windowSize, i + 1);
                        }
                    } else {
                        logger.debug("Empty batch for windowSize={} windowStart={}", windowSize, i + 1);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error in sendLastFiveMinuteDistributionData: {}", e.getMessage(), e);
        }
    }

    /**
     * Check if a window has already been sent to the backend.
     * Uses individual Redis keys with TTL for tracking.
     */
    private boolean isWindowAlreadySent(int windowSize, long windowStart) {
        if (cache == null) {
            return false;
        }
        String redisKey = RedisKeyInfo.API_DISTRIBUTION_WINDOW_SENT_PREFIX + ":" + windowSize + ":" + windowStart;
        long value = cache.get(redisKey);
        return value > 0;
    }

    /**
     * Mark a window as sent in Redis with TTL.
     * TTL is set to 24 hours (3x lookback window) to prevent duplicate sends even during clock skew.
     */
    private void markWindowAsSent(int windowSize, long windowStart) {
        if (cache == null) {
            return;
        }
        String redisKey = RedisKeyInfo.API_DISTRIBUTION_WINDOW_SENT_PREFIX + ":" + windowSize + ":" + windowStart;
        long ttlSeconds = WINDOW_TRACKING_TTL_HOURS * 60 * 60;
        ((ApiCountCacheLayer) cache).setLongWithExpiry(redisKey, 1L, ttlSeconds);
        logger.info("Marked window " + windowStart + " for windowSize " + windowSize + " as sent (TTL: " + WINDOW_TRACKING_TTL_HOURS + " hours)");
    }

    /**
     * Send distribution data to backend and return success status.
     * Only returns true if backend confirms with 200 status code.
     */
    private boolean sendDistributionDataToBackend(List<ApiDistributionDataRequestPayload.DistributionData> batch, int windowSize, long windowStart) {
        try {
            ApiDistributionDataRequestPayload payload = ApiDistributionDataRequestPayload.newBuilder()
                .addAllDistributionData(batch)
                .build();

            String jsonPayload = ProtoMessageUtils.toString(payload).orElse(null);
            if (jsonPayload == null) {
                logger.errorAndAddToDb(String.format("Failed to convert payload to JSON for windowSize=%d windowStart=%d", windowSize, windowStart));
                return false;
            }

            Map<String, List<String>> headers = Utils.buildHeaders();
            headers.put("Content-Type", Collections.singletonList("application/json"));
            OriginalHttpRequest request = new OriginalHttpRequest(
                "http://localhost:9090/api/threat_detection/save_api_distribution_data",
                "",
                "POST",
                jsonPayload,
                headers,
                ""
            );

            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);

            if (response.getStatusCode() == 200 && response.getBody() != null) {
                logger.info("Successfully sent distribution data for windowSize={} windowStart={} (batch size: {})",
                    windowSize, windowStart, batch.size());
                return true;
            } else {
                logger.errorAndAddToDb(String.format("Non-200 response from backend for windowSize=%d windowStart=%d: status=%d body=%s",
                    windowSize, windowStart, response.getStatusCode(), response.getBody()));
                return false;
            }
        } catch (Exception e) {
            logger.errorAndAddToDb(e, String.format("Exception sending distribution data for windowSize=%d windowStart=%d", windowSize, windowStart));
            return false;
        }
    }

    class ApiDistributionInfo {
        String apiKey;
        int windowSize;
        long windowStartEpochMin;
        Map<String, Integer> distribution;
    }

}

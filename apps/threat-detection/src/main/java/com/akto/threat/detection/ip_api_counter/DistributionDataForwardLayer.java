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
                String redisKey = RedisKeyInfo.API_DISTRIBUTION_DATA_LAST_SENT_PREFIX + windowSize;
                long lastSuccessfulUpdateTs = cache.get(redisKey);
                long currentEpochMin = Context.now() / 60;
                // - Align the current time to the nearest window size. 12:03 will align to 12:00 for a 5-minute window.
                long currentAlignedWindowEnd = (currentEpochMin / windowSize) * windowSize;

                // - Determine the safe window end to ensure only completed windows are processed.
                long safeWindowEnd = currentAlignedWindowEnd - windowSize;

                // - Start from lastSuccessfulUpdateTs or 60 minutes before.
                long windowStart = Math.max(lastSuccessfulUpdateTs + windowSize, safeWindowEnd - 60);

                for (long i = windowStart; i <= safeWindowEnd; i += windowSize) {

                    Map<String, Map<String, Integer>> apiToBuckets = distributionCalculator.getBucketDistribution(windowSize, i + 1);
                    if (apiToBuckets == null || apiToBuckets.isEmpty()) continue;

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
                        try {
                            ApiDistributionDataRequestPayload payload = ApiDistributionDataRequestPayload.newBuilder().addAllDistributionData(batch).build();

                            ProtoMessageUtils.toString(payload)
                                .ifPresent(
                                    msg -> {
                                    Map<String, List<String>> headers = Utils.buildHeaders();
                                    headers.put("Content-Type", Collections.singletonList("application/json"));
                                    OriginalHttpRequest request = new OriginalHttpRequest(Utils.getThreatProtectionBackendUrl() + "/api/threat_detection/save_api_distribution_data", "","POST", msg, headers, "");
                                      try {
                                        OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
                                        String responsePayload = response.getBody();
                                        if (response.getStatusCode() != 200 || responsePayload == null) {
                                        logger.errorAndAddToDb("non 2xx response in save_api_distribution_data");
                                        }
                                      } catch (Exception e) {
                                        logger.errorAndAddToDb("error sending api distribution data " + e.getMessage());
                                        e.printStackTrace();
                                      }
                                    });

                            logger.info("Sent distribution data for windowSize={} windowStart={}", windowSize, i);
                            cache.set(redisKey, i);
                        } catch (Exception e) {
                            logger.errorAndAddToDb(e, "Failed to send distribution for windowSize: " + windowSize + " windowStart: " + windowStart);
                            return;
                        }
                    } else {
                        logger.info("No distribution data to send for windowSize={} windowStart={}", windowSize, windowStart);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error in sendLastFiveMinuteDistributionData: {}", e.getMessage(), e);
        }
    }

    class ApiDistributionInfo {
        String apiKey;
        int windowSize;
        long windowStartEpochMin;
        Map<String, Integer> distribution;
    }

}

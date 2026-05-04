package com.akto.threat.detection.ip_api_counter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
import com.akto.threat.detection.utils.Utils;

import io.lettuce.core.RedisClient;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;

/**
 * Cron that reads distribution data from Redis Hashes (populated by
 * DistributionStreamConsumer) and forwards it to the threat-detection backend.
 *
 * Distribution data lives entirely in Redis:
 *   - dist|{windowSize}|{windowStart}|{apiKey} -> Hash of bucket counts
 *   - distApis|{windowSize}|{windowStart} -> Set of active apiKeys
 */
public class DistributionDataForwardLayer {

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final LoggerMaker logger = new LoggerMaker(DistributionDataForwardLayer.class, LogDb.THREAT_DETECTION);
    private static final int LOOKBACK_HOURS = 8;

    private final StatefulRedisConnection<String, String> redis;

    // In-memory dedup. On pod restart this is empty, so we may re-send.
    // Backend upserts with $set and data is the same (single source of truth in Redis),
    // so re-sends are harmless.
    private final Set<String> sentWindows = ConcurrentHashMap.newKeySet();

    public DistributionDataForwardLayer(RedisClient redisClient) {
        this.redis = redisClient.connect();
    }

    public void sendLastFiveMinuteDistributionData() {
        scheduler.scheduleAtFixedRate(() -> {
            buildPayloadAndForwardData();
        }, 0, 1, TimeUnit.MINUTES);
    }

    public void buildPayloadAndForwardData() {
        try {
            logger.debug("Distribution forward cron started at {}", Context.now());

            List<Integer> windowSizes = Arrays.asList(5, 15, 30);

            for (int windowSize : windowSizes) {
                long currentEpochMin = Context.now() / 60;
                long currentAlignedWindowEnd = (currentEpochMin / windowSize) * windowSize;
                long safeWindowEnd = currentAlignedWindowEnd - windowSize;
                long lookbackStart = safeWindowEnd - (LOOKBACK_HOURS * 60);

                int totalWindows = 0;
                int alreadySentWindows = 0;
                int lockedWindows = 0;
                int emptyWindows = 0;
                int successfulSends = 0;
                int failedSends = 0;

                for (long i = lookbackStart; i <= safeWindowEnd; i += windowSize) {
                    long ws = i + 1;
                    totalWindows++;

                    String windowKey = windowSize + ":" + ws;

                    // In-memory dedup
                    if (sentWindows.contains(windowKey)) {
                        alreadySentWindows++;
                        continue;
                    }

                    // Redis lock — prevents duplicate sends across pods
                    String lockKey = "distLock|" + windowSize + "|" + ws;
                    String acquired = redis.sync().set(lockKey, "1", SetArgs.Builder.nx().ex(60));
                    if (acquired == null) {
                        lockedWindows++;
                        continue;
                    }

                    // Read active APIs for this window from Redis
                    String apisKey = "distApis|" + windowSize + "|" + ws;
                    Set<String> apiKeys = redis.sync().smembers(apisKey);
                    if (apiKeys == null || apiKeys.isEmpty()) {
                        emptyWindows++;
                        continue;
                    }

                    // Read distribution for each API and build batch
                    List<ApiDistributionDataRequestPayload.DistributionData> batch = new ArrayList<>();

                    for (String apiKey : apiKeys) {
                        String distKey = "dist|" + windowSize + "|" + ws + "|" + apiKey;
                        Map<String, String> buckets = redis.sync().hgetall(distKey);
                        if (buckets == null || buckets.isEmpty()) continue;

                        String[] parts = apiKey.split("\\|");
                        if (parts.length < 3) {
                            logger.errorAndAddToDb("Invalid apiKey format: " + apiKey);
                            continue;
                        }

                        int apiCollectionId;
                        try {
                            apiCollectionId = Integer.parseInt(parts[0]);
                        } catch (NumberFormatException e) {
                            logger.errorAndAddToDb("Invalid apiCollectionId in apiKey: " + apiKey);
                            continue;
                        }
                        String url = parts[1];
                        String method = parts[2];

                        Map<String, Integer> distribution = new HashMap<>();
                        for (Map.Entry<String, String> e : buckets.entrySet()) {
                            try {
                                distribution.put(e.getKey(), Integer.parseInt(e.getValue()));
                            } catch (NumberFormatException nfe) {
                                // skip malformed bucket value
                            }
                        }

                        if (distribution.isEmpty()) continue;

                        ApiDistributionDataRequestPayload.DistributionData data =
                            ApiDistributionDataRequestPayload.DistributionData.newBuilder()
                                .setApiCollectionId(apiCollectionId)
                                .setUrl(url)
                                .setMethod(method)
                                .setWindowSize(windowSize)
                                .setWindowStartEpochMin(ws)
                                .putAllDistribution(distribution)
                                .build();

                        batch.add(data);
                    }

                    if (!batch.isEmpty()) {
                        boolean success = sendDistributionDataToBackend(batch, windowSize, ws);
                        if (success) {
                            sentWindows.add(windowKey);
                            successfulSends++;
                            logger.infoAndAddToDb(String.format(
                                "Sent distribution data: windowSize=%d windowStart=%d batchSize=%d",
                                windowSize, ws, batch.size()));
                        } else {
                            failedSends++;
                            logger.errorAndAddToDb(String.format(
                                "Failed to send distribution data: windowSize=%d windowStart=%d",
                                windowSize, ws));
                        }
                    } else {
                        emptyWindows++;
                    }
                }

                logger.debugAndAddToDb(String.format(
                    "WindowSize=%d summary: total=%d alreadySent=%d locked=%d empty=%d success=%d failed=%d",
                    windowSize, totalWindows, alreadySentWindows, lockedWindows, emptyWindows, successfulSends, failedSends));
            }
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error in distribution data forward cron");
        }
    }

    private boolean sendDistributionDataToBackend(
            List<ApiDistributionDataRequestPayload.DistributionData> batch,
            int windowSize, long windowStart) {
        try {
            ApiDistributionDataRequestPayload payload = ApiDistributionDataRequestPayload.newBuilder()
                .addAllDistributionData(batch)
                .build();

            String jsonPayload = ProtoMessageUtils.toString(payload).orElse(null);
            if (jsonPayload == null) {
                logger.errorAndAddToDb(String.format(
                    "Failed to convert payload to JSON: windowSize=%d windowStart=%d",
                    windowSize, windowStart));
                return false;
            }

            Map<String, List<String>> headers = Utils.buildHeaders();
            headers.put("Content-Type", Collections.singletonList("application/json"));
            OriginalHttpRequest request = new OriginalHttpRequest(
                Utils.getThreatProtectionBackendUrl() + "/api/threat_detection/save_api_distribution_data",
                "",
                "POST",
                jsonPayload,
                headers,
                ""
            );

            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);

            if (response.getStatusCode() == 200 && response.getBody() != null) {
                return true;
            } else {
                logger.errorAndAddToDb(String.format(
                    "Non-200 response: windowSize=%d windowStart=%d status=%d",
                    windowSize, windowStart, response.getStatusCode()));
                return false;
            }
        } catch (Exception e) {
            logger.errorAndAddToDb(e, String.format(
                "Exception sending distribution data: windowSize=%d windowStart=%d",
                windowSize, windowStart));
            return false;
        }
    }
}

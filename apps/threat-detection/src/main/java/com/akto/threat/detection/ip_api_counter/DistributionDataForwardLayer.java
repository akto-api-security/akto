package com.akto.threat.detection.ip_api_counter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import com.akto.ProtoMessageUtils;
import com.akto.dao.context.Context;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ApiDistributionDataRequestPayload;
import com.akto.threat.detection.cache.ApiCountCacheLayer;
import com.akto.threat.detection.cache.CounterCache;
import com.akto.threat.detection.constants.RedisKeyInfo;

import io.lettuce.core.RedisClient;

public class DistributionDataForwardLayer {
    
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final LoggerMaker logger = new LoggerMaker(DistributionDataForwardLayer.class, LogDb.THREAT_DETECTION);
    private static CounterCache cache;
    private final DistributionCalculator distributionCalculator;
    private final CloseableHttpClient httpClient;
    private static final PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();

    public DistributionDataForwardLayer(RedisClient redisClient, DistributionCalculator distributionCalculator) {
        if (redisClient != null) {
            cache = new ApiCountCacheLayer(redisClient);
        } else {
            cache = null;
        }
        this.distributionCalculator = distributionCalculator;
        connManager.setMaxTotal(100);
        connManager.setDefaultMaxPerRoute(100);
        this.httpClient = HttpClients.custom()
            .setConnectionManager(connManager)
            .setKeepAliveStrategy((response, context) -> 30_000)
            .build();
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
                long currentAlignedWindowEnd = (currentEpochMin / windowSize) * windowSize;

                long safeWindowEnd = currentAlignedWindowEnd - windowSize;
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

                            String url = System.getenv("AKTO_THREAT_PROTECTION_BACKEND_URL");
                            String token = System.getenv("AKTO_THREAT_PROTECTION_BACKEND_TOKEN");
                            ProtoMessageUtils.toString(payload)
                                .ifPresent(
                                    msg -> {
                                      StringEntity requestEntity =
                                          new StringEntity(msg, ContentType.APPLICATION_JSON);
                                      HttpPost req =
                                          new HttpPost(
                                              String.format("%s/api/threat_detection/save_api_distribution_data", url));
                                      req.addHeader("Authorization", "Bearer " + token);
                                      req.setEntity(requestEntity);
                                      try {
                                        this.httpClient.execute(req);
                                      } catch (IOException e) {
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

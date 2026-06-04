package com.akto.threat.detection.crons;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.akto.dao.context.Context;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.threat_detection.ApiHitCountInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.threat.detection.cache.ApiCountCacheLayer;
import com.akto.threat.detection.constants.RedisKeyInfo;

import io.lettuce.core.KeyValue;
import io.lettuce.core.RedisClient;

public class ApiCountInfoRelayCron {

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final LoggerMaker logger = new LoggerMaker(ApiCountInfoRelayCron.class, LogDb.THREAT_DETECTION);
    private static final DataActor dataActor = DataActorFactory.fetchInstance();

    private final ApiCountCacheLayer cache;

    public ApiCountInfoRelayCron(RedisClient redisClient) {
        this.cache = new ApiCountCacheLayer(redisClient);
    }

    public void relayApiCountInfo() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                long endBinId = (Context.now() / 60) - 5;
                long lastRelayedBin = cache.fetchLongDataFromRedis(RedisKeyInfo.API_COUNT_LAST_RELAYED_BIN);
                long startBinId = lastRelayedBin > 0 ? lastRelayedBin + 1 : endBinId - (8 * 60);
                if (startBinId > endBinId) {
                    logger.debugAndAddToDb("No new bins to relay since lastRelayedBin=" + lastRelayedBin);
                    return;
                }
                logger.debugAndAddToDb("relayApiCountInfo cron window: startBin=" + startBinId + " endBin=" + endBinId);

                List<String> apiTuples = cache.fetchApiTuplesFromIndex(startBinId, endBinId);
                if (apiTuples.isEmpty()) {
                    logger.debugAndAddToDb("No API tuples in index for range " + startBinId + " to " + endBinId);
                    return;
                }
                logger.debugAndAddToDb("Fetched " + apiTuples.size() + " unique APIs in window");

                if (apiTuples.size() > 10000) {
                    logger.warnAndAddToDb("Large batch detected: " + apiTuples.size() + " unique APIs. This may indicate stream lag or cron hasn't run for a while.");
                }

                List<KeyValue<String, String>> kvs = cache.fetchCountsForWindow(apiTuples, startBinId, endBinId);

                List<ApiHitCountInfo> toInsert = new ArrayList<>();
                for (KeyValue<String, String> kv : kvs) {
                    if (kv == null || !kv.hasValue()) continue;
                    // key format: apiCount|{collectionId}|{url}|{method}|{binId}
                    String[] parts = kv.getKey().split("\\|", 5);
                    if (parts.length < 5) continue;
                    try {
                        int collectionId = Integer.parseInt(parts[1]);
                        String url = parts[2];
                        String method = parts[3];
                        long binId = Long.parseLong(parts[4]);
                        long count = Long.parseLong(kv.getValue());
                        toInsert.add(new ApiHitCountInfo(collectionId, url, method, count, binId));
                    } catch (NumberFormatException e) {
                        // skip malformed keys
                    }
                }

                if (toInsert.isEmpty()) {
                    logger.debugAndAddToDb("No api hit count data to relay");
                    return;
                }

                try {
                    dataActor.bulkInsertApiHitCount(toInsert);
                    logger.debugAndAddToDb("Relayed " + toInsert.size() + " api hit count records to DB");
                    cache.removeIndexRange(startBinId, endBinId);
                    cache.setLongWithExpiry(RedisKeyInfo.API_COUNT_LAST_RELAYED_BIN, endBinId, 8 * 60 * 60);
                    logger.debugAndAddToDb("Cleaned up apiCountIndex for range " + startBinId + " to " + endBinId);
                } catch (Exception e) {
                    logger.errorAndAddToDb("Error relaying api count info: " + e.getMessage());
                }

            } catch (Exception e) {
                e.printStackTrace();
                logger.errorAndAddToDb("Error executing relayApiCountInfoCron: " + e.getMessage());
            }
        }, 0, 5, TimeUnit.MINUTES);
    }
}

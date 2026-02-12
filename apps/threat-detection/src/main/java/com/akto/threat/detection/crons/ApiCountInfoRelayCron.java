package com.akto.threat.detection.crons;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.akto.dao.context.Context;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.threat_detection.ApiHitCountInfo;
import com.akto.log.LoggerMaker;
import com.akto.threat.detection.cache.ApiCountCacheLayer;
import com.akto.threat.detection.cache.CounterCache;
import com.akto.threat.detection.constants.RedisKeyInfo;
import com.akto.threat.detection.utils.ApiCountInfoRelayUtils;

import io.lettuce.core.RedisClient;

public class ApiCountInfoRelayCron {

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    List<String> apiCountKeys;
    Map<String, Long> keyValData;
    private static CounterCache cache;
    private static final LoggerMaker logger = new LoggerMaker(ApiCountInfoRelayCron.class);
    private static final DataActor dataActor = DataActorFactory.fetchInstance();


    public ApiCountInfoRelayCron(RedisClient redisClient) {
        cache = new ApiCountCacheLayer(redisClient);
    }

    public void relayApiCountInfo(){
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run(){
                try {
                    logger.infoAndAddToDb("relayApiCountInfo cron started at " + Context.now(), LoggerMaker.LogDb.THREAT_DETECTION);
                    long endBinId = (Context.now()/60) - 5; // pick keys which are older than at least 5 mins

                    // Always process last 8 hours to handle Kafka lag
                    // DB upsert handles duplicate entries, so reprocessing is safe
                    long startBinId = endBinId - (8 * 60);
                    logger.infoAndAddToDb("relayApiCountInfo cron processing window: startBin " + startBinId + " endBin " + endBinId +
                        " (8 hours)", LoggerMaker.LogDb.THREAT_DETECTION);

                    // fetch keys for which count needs to be fetched
                    apiCountKeys = cache.fetchMembersFromSortedSet(RedisKeyInfo.API_COUNTER_SORTED_SET, startBinId, endBinId);
                    if (apiCountKeys.size() == 0) {
                        logger.infoAndAddToDb("No keys found in sorted set for range " + startBinId + " to " + endBinId,
                            LoggerMaker.LogDb.THREAT_DETECTION);
                        return;
                    }
                    logger.infoAndAddToDb("Fetched " + apiCountKeys.size() + " keys from sorted set for range " + startBinId + " to " + endBinId,
                        LoggerMaker.LogDb.THREAT_DETECTION);

                    // Warn if processing very large batch
                    if (apiCountKeys.size() > 10000) {
                        logger.warnAndAddToDb("Large batch detected: " + apiCountKeys.size() + " keys. This may indicate Kafka lag or cron hasn't run for a while.",
                            LoggerMaker.LogDb.THREAT_DETECTION);
                    }

                    keyValData = cache.mget(apiCountKeys.toArray(new String[0])); // get data for those keys

                    // build api hit count payload and call cyborg
                    List<ApiHitCountInfo> hitCountInfos = ApiCountInfoRelayUtils.buildPayload(keyValData);
                    if (hitCountInfos == null || hitCountInfos.size() == 0) {
                        logger.infoAndAddToDb("No valid api hit count data to relay (buildPayload returned empty)",
                            LoggerMaker.LogDb.THREAT_DETECTION);
                        return;
                    }
                    logger.infoAndAddToDb("Built " + hitCountInfos.size() + " api hit count records to relay to DB",
                        LoggerMaker.LogDb.THREAT_DETECTION);

                    try {
                        dataActor.bulkInsertApiHitCount(hitCountInfos);
                        logger.infoAndAddToDb("Successfully relayed " + hitCountInfos.size() + " api hit count records to DB",
                            LoggerMaker.LogDb.THREAT_DETECTION);

                        // Clean up processed keys from sorted set
                        cache.removeMembersFromSortedSet(RedisKeyInfo.API_COUNTER_SORTED_SET, startBinId, endBinId);
                        logger.infoAndAddToDb("Cleaned up sorted set: removed keys in range " + startBinId + " to " + endBinId,
                            LoggerMaker.LogDb.THREAT_DETECTION);
                    } catch (Exception e) {
                        logger.errorAndAddToDb("Error relaying api count info: " + e.getMessage(), LoggerMaker.LogDb.THREAT_DETECTION);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    logger.errorAndAddToDb("Error executing relayApiCountInfoCron: " + e.getMessage(), LoggerMaker.LogDb.THREAT_DETECTION);
                }
            }
        }, 0 , 1, TimeUnit.MINUTES);
    }

}

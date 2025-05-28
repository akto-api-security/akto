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
                    logger.info("relayApiCountInfo cron started " + Context.now());
                    long lastSuccessfulUpdateTs = cache.get(RedisKeyInfo.API_COUNTER_RELAY_LAST_UPDATE_TS);
                    long endBinId = (Context.now()/60) - 5; // pick keys which are older than atleast 5 mins
                    long startBinId = Math.max(endBinId - 30, lastSuccessfulUpdateTs);
                    logger.info("relayApiCountInfo cron startBin {} endBin {}", startBinId, endBinId);

                    // fetch keys for which count needs to be fetched
                    apiCountKeys = cache.fetchMembersFromSortedSet(RedisKeyInfo.API_COUNTER_SORTED_SET, startBinId, endBinId);
                    if (apiCountKeys.size() == 0) {
                        return;
                    }
                    logger.info("relayApiCountInfo cron fetched {} keys for startBinId {} and endBinId {}", apiCountKeys.size(), startBinId, endBinId);
                    keyValData = cache.mget(apiCountKeys.toArray(new String[0])); // get data for thsoe keys

                    // build api hit count payload and call cyborg
                    List<ApiHitCountInfo> hitCountInfos = ApiCountInfoRelayUtils.buildPayload(keyValData);
                    if (hitCountInfos == null || hitCountInfos.size() == 0) {
                        logger.info("hitCountInfos is empty, returning");
                        return;
                    }
                    try {
                        dataActor.bulkInsertApiHitCount(hitCountInfos);
                        cache.set(RedisKeyInfo.API_COUNTER_RELAY_LAST_UPDATE_TS, endBinId);
                        cache.removeMembersFromSortedSet(RedisKeyInfo.API_COUNTER_SORTED_SET, startBinId, endBinId);
                    } catch (Exception e) {
                        logger.error("Error relaying api count info : {}", e.getMessage());
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    logger.error("Error executing relayApiCountInfoCron : {} ", e);
                }
            }
        }, 0 , 1, TimeUnit.MINUTES);
    }

}

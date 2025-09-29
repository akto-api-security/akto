// package com.akto.threat.detection.ip_api_counter;

// import com.akto.dao.context.Context;
// import com.akto.threat.detection.cache.ApiCountCacheLayer;
// import com.akto.threat.detection.cache.CounterCache;
// import com.akto.threat.detection.constants.RedisKeyInfo;
// import com.clearspring.analytics.stream.frequency.CountMinSketch;

// import io.lettuce.core.RedisClient;

// public class CmsDataForwardLayer {

//     private static CounterCache cache;
//     private static CmsCounterLayer cmsCounterLayer;

//     public CmsDataForwardLayer(RedisClient redisClient) {
//         cache = new ApiCountCacheLayer(redisClient);
//         cmsCounterLayer = new CmsCounterLayer(redisClient);
//     }

//     public static void sendCMSDataToBackend(int startWindow) {

//         long lastSuccessfulUpdateTs = cache.get(RedisKeyInfo.IP_API_CMS_LAST_UPDATE_TS);
//         long endBinId = (Context.now() / 60) - 2;
//         long startBinId = Math.max(endBinId - 30, lastSuccessfulUpdateTs);

//         for (long i = startBinId; i <= endBinId; i++) {
//             String windowStr = String.valueOf(i);
//             CountMinSketch sketch = cmsCounterLayer.getSketch(windowStr);
//             if (sketch == null) continue;

//             try {
//                 byte[] serialized = CountMinSketch.serialize(sketch);
//                 // build payload
//                 // Send this to backend
//                 // pick this up post v1 release
//             } catch (Exception e) {
//                 e.printStackTrace();
//             }
//         }
//     }

// }

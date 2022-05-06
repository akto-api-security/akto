package com.akto.utils;

import com.akto.dao.context.Context;
import io.github.bucket4j.Bucket;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimitCache {

    public static int thresholdForDeletion = 60*60*5;

    public Map<CACHE_TYPE, ConcurrentHashMap<String, IpInfo>> cacheMap;

    public RateLimitCache() {
        cacheMap = new HashMap<>();
        cacheMap.put(CACHE_TYPE.SIGN_IN, new ConcurrentHashMap<>());
        cacheMap.put(CACHE_TYPE.SEND_EMAIL, new ConcurrentHashMap<>());
    }

    public enum CACHE_TYPE {
        SIGN_IN, SEND_EMAIL
    }
    public static class IpInfo {
        public Bucket bucket;
        public int lastTimestamp;

        public IpInfo(Bucket bucket, int lastTimestamp) {
            this.bucket = bucket;
            this.lastTimestamp = lastTimestamp;
        }

    }


    public void deleteOldData() {
        for (Map<String, IpInfo> ipInfoMap : this.cacheMap.values() ) {
            Iterator<Map.Entry<String, IpInfo>> iterator = ipInfoMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, IpInfo> entry = iterator.next();
                int lastTimestamp = entry.getValue().lastTimestamp;
                int diff = Context.now() - lastTimestamp;
                if (diff > thresholdForDeletion) {
                    iterator.remove();
                }
            }
        }
    }





}

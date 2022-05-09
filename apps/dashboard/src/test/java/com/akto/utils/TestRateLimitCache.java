package com.akto.utils;

import com.akto.dao.context.Context;
import org.junit.Test;

import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertEquals;

public class TestRateLimitCache {

    @Test
    public void testDeleteOldData() {
        RateLimitCache rateLimitCache = new RateLimitCache();
        int now = Context.now();
        ConcurrentHashMap<String, RateLimitCache.IpInfo> signInCacheMap = rateLimitCache.cacheMap.get(RateLimitCache.CACHE_TYPE.SIGN_IN);
        signInCacheMap.put("ip1", new RateLimitCache.IpInfo(null, now));
        signInCacheMap.put("ip2", new RateLimitCache.IpInfo(null, now-RateLimitCache.thresholdForDeletion-10));
        signInCacheMap.put("ip3", new RateLimitCache.IpInfo(null, now-(RateLimitCache.thresholdForDeletion/2)));

        ConcurrentHashMap<String, RateLimitCache.IpInfo> sendEmailCacheMap = rateLimitCache.cacheMap.get(RateLimitCache.CACHE_TYPE.SEND_EMAIL);
        sendEmailCacheMap.put("ip1", new RateLimitCache.IpInfo(null, now));
        sendEmailCacheMap.put("ip2", new RateLimitCache.IpInfo(null, now-RateLimitCache.thresholdForDeletion-10));
        sendEmailCacheMap.put("ip3", new RateLimitCache.IpInfo(null, now-(RateLimitCache.thresholdForDeletion/2)));
        sendEmailCacheMap.put("ip4", new RateLimitCache.IpInfo(null, now-10));

        assertEquals(signInCacheMap.size(),3);
        assertEquals(sendEmailCacheMap.size(),4);

        rateLimitCache.deleteOldData();

        assertEquals(signInCacheMap.size(),2);
        assertEquals(sendEmailCacheMap.size(),3);
    }
}

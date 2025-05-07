package com.akto.threat.detection.utils;

import com.akto.threat.detection.constants.RedisKeyInfo;

public class Utils {

    public static String buildApiHitCountKey(int apiCollectionId, String url, String method) {
        return RedisKeyInfo.API_COUNTER_KEY_PREFIX + "|" + apiCollectionId + "|" + url + "|" + method;
    }

}
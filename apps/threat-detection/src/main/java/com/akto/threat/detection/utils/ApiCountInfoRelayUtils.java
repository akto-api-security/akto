package com.akto.threat.detection.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.akto.dto.threat_detection.ApiHitCountInfo;
import com.akto.log.LoggerMaker;

public class ApiCountInfoRelayUtils {

    private static final LoggerMaker logger = new LoggerMaker(ApiCountInfoRelayUtils.class);
    
    private static ApiHitCountInfo parseApiCountKey(String key, Long count) {
        if (key == null || key.isEmpty()) {
            return null;
        }

        try {
            String[] parts = key.split("\\|");
            if (parts.length < 5 || !parts[0].equals("apiCount")) {
                return null;
            }

            int apiCollectionId = Integer.parseInt(parts[1]);
            String url = parts[2];
            String method = parts[3];
            if (parts[4].isEmpty() || parts[4].equals("null")) {
                return null;
            }
            long binId = Long.parseLong(parts[4]);
            return new ApiHitCountInfo(apiCollectionId, url, method, count, binId);
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("Error parsing key: {}, error {} ", key, e.getMessage());
            return null;
        }
    }

    public static List<ApiHitCountInfo> buildPayload(Map<String, Long> keyValData) {
        List<ApiHitCountInfo> hitCountInfos = new ArrayList<>();
        for (String key: keyValData.keySet()) {
            ApiHitCountInfo info = parseApiCountKey(key, keyValData.get(key));
            if (info != null) {
                hitCountInfos.add(info);
            }
        }

        return hitCountInfos;
    }

}

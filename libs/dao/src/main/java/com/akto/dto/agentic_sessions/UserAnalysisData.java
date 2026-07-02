package com.akto.dto.agentic_sessions;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class UserAnalysisData {

    public static final String ID_SERVICE_ID = "_id.serviceId";
    public static final String ID_DEVICE_ID = "_id.deviceId";
    public static final String SERVICE_ID = "serviceId";
    public static final String DEVICE_ID = "deviceId";
    public static final String USER_NAME = "userName";
    public static final String LAST_UPDATED_AT = "lastUpdatedAt";
    public static final String TOPIC_HIERARCHY  = "topicHierarchy";
    public static final String HARMFUL_TOPICS   = "harmfulTopics";
    public static final String TOTAL_INPUT_TOKENS = "totalInputTokens";
    public static final String TOTAL_OUTPUT_TOKENS = "totalOutputTokens";
    public static final String AI_SUMMARY = "aiSummary";

    public static class UserAnalysisDataKey {
        private String serviceId;
        private String deviceId;

        public UserAnalysisDataKey() {}

        public UserAnalysisDataKey(String serviceId, String deviceId) {
            this.serviceId = serviceId;
            this.deviceId = deviceId;
        }

        public String getServiceId() {
            return serviceId;
        }

        public String getDeviceId() {
            return deviceId;
        }

        public void setServiceId(String serviceId) {
            this.serviceId = serviceId;
        }

        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
        }
    }

    private UserAnalysisDataKey id;
    private String userName;
    // domain → (subDomain → count). Outer key is the topic; no separate flat topicCounts needed.
    private Map<String, Map<String, Integer>> topicHierarchy = new HashMap<>();
    private long totalInputTokens;
    private long totalOutputTokens;
    private String aiSummary;
    private long lastUpdatedAt;
    private Map<String, Object> harmfulTopics = new HashMap<>();

    public List<String> getDominantTopics(int topN) {
        if (topicHierarchy == null || topicHierarchy.isEmpty()) return new ArrayList<>();
        List<Map.Entry<String, Integer>> entries = new ArrayList<>();
        for (Map.Entry<String, Map<String, Integer>> e : topicHierarchy.entrySet()) {
            int total = 0;
            if (e.getValue() != null) {
                for (int v : e.getValue().values()) total += v;
            }
            entries.add(new java.util.AbstractMap.SimpleEntry<>(e.getKey(), total));
        }
        entries.sort(Comparator.comparingInt(Map.Entry<String, Integer>::getValue).reversed());
        List<String> result = new ArrayList<>();
        for (int i = 0; i < Math.min(topN, entries.size()); i++) {
            result.add(entries.get(i).getKey());
        }
        return result;
    }
}

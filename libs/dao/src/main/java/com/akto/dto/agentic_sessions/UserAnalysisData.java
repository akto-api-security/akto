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
    public static final String TOPIC_COUNTS = "topicCounts";
    public static final String HARMFUL_TOPICS = "harmfulTopics";
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
    private Map<String, Integer> topicCounts = new HashMap<>();
    private long totalInputTokens;
    private long totalOutputTokens;
    private String aiSummary;
    private long lastUpdatedAt;
    private Map<String, Object> harmfulTopics = new HashMap<>();

    public List<String> getDominantTopics(int topN) {
        if (topicCounts == null || topicCounts.isEmpty()) {
            return new ArrayList<>();
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(topicCounts.entrySet());
        entries.sort(Comparator.comparingInt(Map.Entry<String, Integer>::getValue).reversed());
        List<String> result = new ArrayList<>();
        for (int i = 0; i < Math.min(topN, entries.size()); i++) {
            result.add(entries.get(i).getKey());
        }
        return result;
    }
}

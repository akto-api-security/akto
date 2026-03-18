package com.akto.dto.agentic_sessions;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    private List<String> dominantTopics;
    private long totalInputTokens;
    private long totalOutputTokens;
    private String aiSummary;
    private long lastUpdatedAt;
    Map<String, Object> harmfulTopics;
}

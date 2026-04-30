package com.akto.threat.detection.constants;

public class KafkaTopic {
    public static final String TRAFFIC_LOGS = "akto.api.logs2";

    public static class ThreatDetection {
        public static final String MALICIOUS_EVENTS = "akto.threat_detection.malicious_events";
        public static final String ALERTS = "akto.threat_detection.alerts";
    }
}
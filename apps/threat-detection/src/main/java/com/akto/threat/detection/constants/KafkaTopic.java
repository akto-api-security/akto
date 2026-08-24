package com.akto.threat.detection.constants;

public class KafkaTopic {
    public static final String TRAFFIC_LOGS = "akto.api.logs2";

    public static class ThreatDetection {
        public static final String MALICIOUS_EVENTS = "akto.threat_detection.malicious_events";
        public static final String ALERTS = "akto.threat_detection.alerts";
        // Malicious events buffered by guardrails-service. Kept separate from
        // ALERTS so a guardrails backlog cannot starve traffic-detector alerts
        // and so retention can be sized independently - this topic's retention
        // is how long a threat-backend outage can last without losing events.
        public static final String GUARDRAIL_EVENTS = "akto.threat_detection.guardrail_events";
    }
}
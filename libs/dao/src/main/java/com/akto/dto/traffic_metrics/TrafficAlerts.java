package com.akto.dto.traffic_metrics;

import com.akto.util.enums.GlobalEnums.Severity;

public class TrafficAlerts {

    public enum ALERT_TYPE {
        TRAFFIC_STOPPED("traffic_stopped"), 
        TRAFFIC_OVERLOADED("traffic_overloaded"),
        CYBORG_STOPPED_RECEIVING_TRAFFIC("cyborg_stopped_receiving_traffic");

        private final String name;

        ALERT_TYPE(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
    
    public static final String ALERTS_CONTENT = "content";
    private String content;

    public static final String LAST_DETECTED = "lastDetected";
    private int lastDetected;

    public static final String _ALERT_TYPE = "alertType";
    private ALERT_TYPE alertType;

    public static final String ALERT_SEVERITY = "severity";
    private Severity severity;

    public static final String LAST_DISMISSED = "lastDismissed";
    private int lastDismissed;

    public TrafficAlerts () {}
    
    public TrafficAlerts (String content, int lastDetected, ALERT_TYPE alertType, Severity severity, int lastDismissed){
        this.content = content;
        this.lastDetected = lastDetected;
        this.alertType = alertType;
        this.severity = severity;
        this.lastDismissed = lastDismissed;
    }
    
    public String getContent() {
        return content;
    }
    public void setContent(String content) {
        this.content = content;
    }

    public int getLastDetected() {
        return lastDetected;
    }

    public void setLastDetected(int lastDetected) {
        this.lastDetected = lastDetected;
    }

    public ALERT_TYPE getAlertType() {
        return alertType;
    }

    public void setAlertType(ALERT_TYPE alertType) {
        this.alertType = alertType;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public int getLastDismissed() {
        return lastDismissed;
    }

    public void setLastDismissed(int lastDismissed) {
        this.lastDismissed = lastDismissed;
    }

}

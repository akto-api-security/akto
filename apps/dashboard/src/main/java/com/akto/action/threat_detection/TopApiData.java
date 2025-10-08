package com.akto.action.threat_detection;

public class TopApiData {
    private String endpoint;
    private String method;
    private int attacks;
    private String severity;

    public TopApiData(String endpoint, String method, int attacks, String severity) {
        this.endpoint = endpoint;
        this.method = method;
        this.attacks = attacks;
        this.severity = severity;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public int getAttacks() {
        return attacks;
    }

    public void setAttacks(int attacks) {
        this.attacks = attacks;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }
}

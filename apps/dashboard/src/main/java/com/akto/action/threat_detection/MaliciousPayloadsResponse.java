package com.akto.action.threat_detection;

public class MaliciousPayloadsResponse {
    
    private String orig;
    private String metadata;
    private long ts;
    // The event's own details, so each attempt of a grouped activity can be shown as itself.
    private String eventId;
    private String refId;
    private String filterId;
    private String severity;
    private String endpoint;
    private String method;
    private String evidenceLine;
    private String remediation;
    private String status;
    // The event's case, for a flyout opened without a table row behind it.
    private String caseOwner;
    private String caseNotes;
    private java.util.List<DashboardMaliciousEvent.CaseActivity> caseTimeline;

    public MaliciousPayloadsResponse(String orig, String metadata, long ts) {
        this.orig = orig;
        this.metadata = metadata;
        this.ts = ts;
    }

    public MaliciousPayloadsResponse(String orig, long ts) {
        this.orig = orig;
        this.ts = ts;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public String getOrig() {
        return orig;
    }

    public void setOrig(String orig) {
        this.orig = orig;
    }

    public long getTs() {
        return ts;
    }

    public void setTs(long ts) {
        this.ts = ts;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getRefId() {
        return refId;
    }

    public void setRefId(String refId) {
        this.refId = refId;
    }

    public String getFilterId() {
        return filterId;
    }

    public void setFilterId(String filterId) {
        this.filterId = filterId;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
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

    public String getEvidenceLine() {
        return evidenceLine;
    }

    public void setEvidenceLine(String evidenceLine) {
        this.evidenceLine = evidenceLine;
    }

    public String getRemediation() {
        return remediation;
    }

    public void setRemediation(String remediation) {
        this.remediation = remediation;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCaseOwner() {
        return caseOwner;
    }

    public void setCaseOwner(String caseOwner) {
        this.caseOwner = caseOwner;
    }

    public String getCaseNotes() {
        return caseNotes;
    }

    public void setCaseNotes(String caseNotes) {
        this.caseNotes = caseNotes;
    }

    public java.util.List<DashboardMaliciousEvent.CaseActivity> getCaseTimeline() {
        return caseTimeline;
    }

    public void setCaseTimeline(java.util.List<DashboardMaliciousEvent.CaseActivity> caseTimeline) {
        this.caseTimeline = caseTimeline;
    }
}

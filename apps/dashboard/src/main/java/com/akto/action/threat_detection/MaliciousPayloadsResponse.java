package com.akto.action.threat_detection;

public class MaliciousPayloadsResponse {
    
    private String orig;
    private String metadata;
    private long ts;
    private String evidenceLine;

    public MaliciousPayloadsResponse(String orig, String metadata, long ts, String evidenceLine) {
        this.orig = orig;
        this.metadata = metadata;
        this.ts = ts;
        this.evidenceLine = evidenceLine;
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

    public String getEvidenceLine() {
        return evidenceLine;
    }

    public void setEvidenceLine(String evidenceLine) {
        this.evidenceLine = evidenceLine;
    }

}

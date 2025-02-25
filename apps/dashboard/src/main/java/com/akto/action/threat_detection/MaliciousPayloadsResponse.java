package com.akto.action.threat_detection;

public class MaliciousPayloadsResponse {
    
    private String orig;
    private long ts;
    public MaliciousPayloadsResponse(String orig, long ts) {
        this.orig = orig;
        this.ts = ts;
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

}

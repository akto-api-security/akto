package com.akto.threat.protection.db;

import org.bson.types.ObjectId;

public class CleanupAuditModel {

    private String id;
    private long alertWindowStart;
    private long alertWindowEnd;

    public CleanupAuditModel() {}

    public CleanupAuditModel(long alertWindowStart, long alertWindowEnd) {
        this.id = new ObjectId().toString();
        this.alertWindowStart = alertWindowStart;
        this.alertWindowEnd = alertWindowEnd;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getAlertWindowStart() {
        return alertWindowStart;
    }

    public void setAlertWindowStart(long alertWindowStart) {
        this.alertWindowStart = alertWindowStart;
    }

    public long getAlertWindowEnd() {
        return alertWindowEnd;
    }

    public void setAlertWindowEnd(long alertWindowEnd) {
        this.alertWindowEnd = alertWindowEnd;
    }
}


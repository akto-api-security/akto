package com.akto.threat.protection.db;

import java.util.UUID;

public class SmartEventModel {

    private String id;

    private String filterId;

    private long detectedAt;

    private String actor;

    public SmartEventModel() {
    }

    public SmartEventModel(String filterId, String actor, long detectedAt) {
        this.id = UUID.randomUUID().toString();
        this.filterId = filterId;
        this.detectedAt = detectedAt;
        this.actor = actor;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFilterId() {
        return filterId;
    }

    public void setFilterId(String filterId) {
        this.filterId = filterId;
    }

    public long getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(long detectedAt) {
        this.detectedAt = detectedAt;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

}

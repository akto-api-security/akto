package com.akto.dto.pii;

import java.util.Map;

import org.bson.codecs.pojo.annotations.BsonId;

public class PIISource {
    @BsonId
    private String id;
    
    private String fileUrl;
    private int lastSynced;
    public static final String LAST_SYNCED = "lastSynced";
    private int addedByUser;
    private int startTs;
    private boolean active;
    public static final String ACTIVE = "active";
    private Map<String, PIIType> mapNameToPIIType;
    public static final String MAP_NAME_TO_PII_TYPE = "mapNameToPIIType";

    public PIISource() {
    }

    public PIISource(String fileUrl, int lastSynced, int addedByUser, int startTs, Map<String,PIIType> mapNameToPIIType, boolean active) {
        this.fileUrl = fileUrl;
        this.lastSynced = lastSynced;
        this.addedByUser = addedByUser;
        this.startTs = startTs;
        this.mapNameToPIIType = mapNameToPIIType;
        this.active = active;
    }

    public String getFileUrl() {
        return this.fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public int getLastSynced() {
        return this.lastSynced;
    }

    public void setLastSynced(int lastSynced) {
        this.lastSynced = lastSynced;
    }

    public int getAddedByUser() {
        return this.addedByUser;
    }

    public void setAddedByUser(int addedByUser) {
        this.addedByUser = addedByUser;
    }

    public int getStartTs() {
        return this.startTs;
    }

    public void setStartTs(int startTs) {
        this.startTs = startTs;
    }

    public Map<String,PIIType> getMapNameToPIIType() {
        return this.mapNameToPIIType;
    }

    public void setMapNameToPIIType(Map<String,PIIType> mapNameToPIIType) {
        this.mapNameToPIIType = mapNameToPIIType;
    }

    public boolean getActive() {
        return this.active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "{" +
            " fileUrl='" + getFileUrl() + "'" +
            ", lastSynced='" + getLastSynced() + "'" +
            ", addedByUser='" + getAddedByUser() + "'" +
            ", startTs='" + getStartTs() + "'" +
            ", mapNameToPIIType='" + getMapNameToPIIType() + "'" +
            "}";
    }
}

package com.akto.dto.threat_detection;

import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;

public class DetectedThreatAlert {
    private String id;

    private String filterId;

    private long detectedAt;

    private String actor;

    private int bucketStart;

    private int bucketEnd;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public DetectedThreatAlert() {
    }

    public DetectedThreatAlert(String id, String filterId, String actor, long detectedAt, int bucketStart,
            int bucketEnd) {
        this.id = id;
        this.filterId = filterId;
        this.detectedAt = detectedAt;
        this.actor = actor;
        this.bucketStart = bucketStart;
        this.bucketEnd = bucketEnd;
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

    public int getBucketStart() {
        return bucketStart;
    }

    public void setBucketStart(int bucketStart) {
        this.bucketStart = bucketStart;
    }

    public int getBucketEnd() {
        return bucketEnd;
    }

    public void setBucketEnd(int bucketEnd) {
        this.bucketEnd = bucketEnd;
    }

    public Optional<String> marshall() {
        try {
            return Optional.of(this.objectMapper.writeValueAsString(this));
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

}

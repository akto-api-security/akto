package com.akto.dto.threat_detection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

public class DetectedThreatAlert {
    private String id;

    private String filterId;

    private long detectedAt;

    private String actor;

    private List<Bin> bins;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public DetectedThreatAlert() {
    }

    public DetectedThreatAlert(String filterId, String actor, long detectedAt, List<Bin> bins) {
        this.id = UUID.randomUUID().toString();
        this.filterId = filterId;
        this.detectedAt = detectedAt;
        this.actor = actor;
        this.bins = bins;
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

    public List<Bin> getBins() {
        return bins;
    }

    public void setBins(List<Bin> bins) {
        this.bins = bins;
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

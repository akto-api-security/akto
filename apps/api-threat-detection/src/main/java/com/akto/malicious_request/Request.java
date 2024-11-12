package com.akto.malicious_request;

import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;

public class Request {

    private String id;

    private String filterId;

    private long detectedAt;

    private String actor;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Request() {
    }

    public Request(String id, String filterId, String actor, long detectedAt) {
        this.id = id;
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

    public Optional<String> marshall() {
        try {
            return Optional.of(this.objectMapper.writeValueAsString(this));
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

}

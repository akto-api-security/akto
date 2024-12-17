package com.akto.threat.backend.db;

import com.akto.dto.type.URLMethods;
import java.util.UUID;

public class MaliciousEventModel {

  private String id;
  private String filterId;
  private String actor;
  private String latestApiIp;
  private String latestApiEndpoint;
  private String country;
  private URLMethods.Method latestApiMethod;
  private String latestApiOrig;
  private long detectedAt;
  private int latestApiCollectionId;
  private EventType eventType;

  public enum EventType {
    SINGLE,
    AGGREGATED
  }

  public MaliciousEventModel() {}

  private MaliciousEventModel(Builder builder) {
    this.id = UUID.randomUUID().toString();
    this.filterId = builder.filterId;
    this.actor = builder.actor;
    this.latestApiIp = builder.latestApiIp;
    this.country = builder.country;
    this.latestApiEndpoint = builder.latestApiEndpoint;
    this.latestApiMethod = builder.latestApiMethod;
    this.latestApiOrig = builder.latestApiOrig;
    this.latestApiCollectionId = builder.latestApiCollectionId;
    this.detectedAt = builder.detectedAt;
    this.eventType = builder.eventType;
  }

  public static class Builder {
    public EventType eventType;
    private String filterId;
    private String actor;
    private String latestApiIp;
    private String country;
    private String latestApiEndpoint;
    private URLMethods.Method latestApiMethod;
    private String latestApiOrig;
    private int latestApiCollectionId;
    private long detectedAt;

    public Builder setFilterId(String filterId) {
      this.filterId = filterId;
      return this;
    }

    public Builder setActor(String actor) {
      this.actor = actor;
      return this;
    }

    public Builder setLatestApiIp(String ip) {
      this.latestApiIp = ip;
      return this;
    }

    public Builder setCountry(String country) {
      this.country = country;
      return this;
    }

    public Builder setLatestApiEndpoint(String latestApiEndpoint) {
      this.latestApiEndpoint = latestApiEndpoint;
      return this;
    }

    public Builder setLatestApiMethod(URLMethods.Method latestApiMethod) {
      this.latestApiMethod = latestApiMethod;
      return this;
    }

    public Builder setLatestApiOrig(String latestApiOrig) {
      this.latestApiOrig = latestApiOrig;
      return this;
    }

    public Builder setDetectedAt(long detectedAt) {
      this.detectedAt = detectedAt;
      return this;
    }

    public Builder setLatestApiCollectionId(int latestApiCollectionId) {
      this.latestApiCollectionId = latestApiCollectionId;
      return this;
    }

    public Builder setEventType(EventType eventType) {
      this.eventType = eventType;
      return this;
    }

    public MaliciousEventModel build() {
      return new MaliciousEventModel(this);
    }
  }

  public String getId() {
    return id;
  }

  public String getFilterId() {
    return filterId;
  }

  public String getActor() {
    return actor;
  }

  public String getLatestApiIp() {
    return latestApiIp;
  }

  public String getLatestApiEndpoint() {
    return latestApiEndpoint;
  }

  public String getCountry() {
    return country;
  }

  public URLMethods.Method getLatestApiMethod() {
    return latestApiMethod;
  }

  public String getLatestApiOrig() {
    return latestApiOrig;
  }

  public long getDetectedAt() {
    return detectedAt;
  }

  public int getLatestApiCollectionId() {
    return latestApiCollectionId;
  }

  public EventType getEventType() {
    return eventType;
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public void setId(String id) {
    this.id = id;
  }

  public void setFilterId(String filterId) {
    this.filterId = filterId;
  }

  public void setActor(String actor) {
    this.actor = actor;
  }

  public void setLatestApiIp(String ip) {
    this.latestApiIp = ip;
  }

  public void setLatestApiEndpoint(String latestApiEndpoint) {
    this.latestApiEndpoint = latestApiEndpoint;
  }

  public void setCountry(String country) {
    this.country = country;
  }

  public void setLatestApiMethod(URLMethods.Method latestApiMethod) {
    this.latestApiMethod = latestApiMethod;
  }

  public void setLatestApiOrig(String latestApiOrig) {
    this.latestApiOrig = latestApiOrig;
  }

  public void setDetectedAt(long detectedAt) {
    this.detectedAt = detectedAt;
  }

  public void setLatestApiCollectionId(int latestApiCollectionId) {
    this.latestApiCollectionId = latestApiCollectionId;
  }

  public void setEventType(EventType eventType) {
    this.eventType = eventType;
  }
}

package com.akto.threat.protection.db;

import java.util.UUID;

public class SmartEventModel {

  private String id;

  private String filterId;

  private String actor;

  private long totalHits;

  private long detectedAt;

  public SmartEventModel() {}

  public SmartEventModel(String filterId, String actor, long totalHits, long detectedAt) {
    this.id = UUID.randomUUID().toString();
    this.filterId = filterId;
    this.detectedAt = detectedAt;
    this.totalHits = totalHits;
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

  public long getTotalHits() {
    return totalHits;
  }

  public void setTotalHits(long totalHits) {
    this.totalHits = totalHits;
  }
}

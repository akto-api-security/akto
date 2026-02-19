package com.akto.action.threat_detection;

import com.akto.dto.type.URLMethods.Method;
import lombok.Getter;
import lombok.Setter;

public class DashboardThreatActor {

  private String id;
  private String objectId;  // MongoDB ObjectId hex string for cursor-based pagination
  private String latestApiEndpoint;
  private String latestApiIp;
  private Method latestApiMethod;
  private long discoveredAt;
  private String country;
  private String latestAttack;
  private String latestApiHost;

  @Getter
  @Setter
  private String latestMetadata;

  public DashboardThreatActor(
      String id,
      String objectId,
      String latestApiEndpoint,
      String latestApiIp,
      Method latestApiMethod,
      long discoveredAt,
      String country,
      String latestAttack,
      String latestApiHost,
      String latestMetadata) {

    this.id = id;
    this.objectId = objectId;
    this.latestApiEndpoint = latestApiEndpoint;
    this.latestApiIp = latestApiIp;
    this.latestApiMethod = latestApiMethod;
    this.discoveredAt = discoveredAt;
    this.country = country;
    this.latestAttack = latestAttack;
    this.latestApiHost = latestApiHost;
    this.latestMetadata = latestMetadata;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getObjectId() {
    return objectId;
  }

  public void setObjectId(String objectId) {
    this.objectId = objectId;
  }

  public String getLatestApiEndpoint() {
    return latestApiEndpoint;
  }

  public void setLatestApiEndpoint(String latestApiEndpoint) {
    this.latestApiEndpoint = latestApiEndpoint;
  }

  public String getLatestApiIp() {
    return latestApiIp;
  }

  public void setLatestApiIp(String latestApiIp) {
    this.latestApiIp = latestApiIp;
  }

  public Method getLatestApiMethod() {
    return latestApiMethod;
  }

  public void setLatestApiMethod(Method latestApiMethod) {
    this.latestApiMethod = latestApiMethod;
  }

  public long getDiscoveredAt() {
    return discoveredAt;
  }

  public void setDiscoveredAt(long discoveredAt) {
    this.discoveredAt = discoveredAt;
  }

  public String getCountry() {
    return country;
  }

  public void setCountry(String country) {
    this.country = country;
  }

  public String getLatestAttack() {
    return latestAttack;
  }

  public void setLatestAttack(String latestAttack) {
    this.latestAttack = latestAttack;
  }

  public String getLatestApiHost() {
    return latestApiHost;
  }

  public void setLatestApiHost(String latestApiHost) {
    this.latestApiHost = latestApiHost;
  }
}

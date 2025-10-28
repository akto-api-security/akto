package com.akto.action.threat_detection;

import java.util.List;

import com.akto.dto.type.URLMethods.Method;

public class DashboardThreatActor {

  private String id;
  private String latestApiEndpoint;
  private String latestApiIp;
  private Method latestApiMethod;
  private long discoveredAt;
  private String country;
  private String latestAttack;
  private List<ActivityData> activity;
  private String latestApiHost;

  public DashboardThreatActor(
      String id,
      String latestApiEndpoint,
      String latestApiIp,
      Method latestApiMethod,
      long discoveredAt,
      String country,
      String latestAttack,
      List<ActivityData> activity,
      String latestApiHost) {

    this.id = id;
    this.latestApiEndpoint = latestApiEndpoint;
    this.latestApiIp = latestApiIp;
    this.latestApiMethod = latestApiMethod;
    this.discoveredAt = discoveredAt;
    this.country = country;
    this.latestAttack = latestAttack;
    this.activity = activity;
    this.latestApiHost = latestApiHost;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
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
  
  public List<ActivityData> getActivity() {
    return activity;
  }

  public void setActivity(List<ActivityData> activity) {
    this.activity = activity;
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

package com.akto.action.threat_detection;

import com.akto.dto.type.URLMethods;

public class DashboardThreatApi {

  private String api;
  private URLMethods.Method method;
  private int actorsCount;
  private int requestsCount;
  private long discoveredAt;
  private String host;

  public DashboardThreatApi(
      String api, URLMethods.Method method, int actorsCount, int requestsCount, long discoveredAt, String host) {
    this.api = api;
    this.method = method;
    this.actorsCount = actorsCount;
    this.requestsCount = requestsCount;
    this.discoveredAt = discoveredAt;
    this.host = host;
  }

  public String getApi() {
    return api;
  }

  public void setApi(String api) {
    this.api = api;
  }

  public URLMethods.Method getMethod() {
    return method;
  }

  public void setMethod(URLMethods.Method method) {
    this.method = method;
  }

  public int getActorsCount() {
    return actorsCount;
  }

  public void setActorsCount(int actorsCount) {
    this.actorsCount = actorsCount;
  }

  public int getRequestsCount() {
    return requestsCount;
  }

  public void setRequestsCount(int requestsCount) {
    this.requestsCount = requestsCount;
  }

  public long getDiscoveredAt() {
    return discoveredAt;
  }

  public void setDiscoveredAt(long discoveredAt) {
    this.discoveredAt = discoveredAt;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }
}

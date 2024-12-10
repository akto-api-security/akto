package com.akto.action.threat_detection;

import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLMethods.Method;

public class DashboardMaliciousRequest {
  private String id;
  private String actor;
  private String filter_id;
  private String url;
  private URLMethods.Method method;
  private String ip;
  private String country;
  private long timestamp;

  public DashboardMaliciousRequest() {}

  public DashboardMaliciousRequest(
      String id,
      String actor,
      String filter,
      String url,
      Method method,
      String ip,
      String country,
      long timestamp) {
    this.id = id;
    this.actor = actor;
    this.filter_id = filter;
    this.url = url;
    this.method = method;
    this.ip = ip;
    this.country = country;
    this.timestamp = timestamp;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getActor() {
    return actor;
  }

  public void setActor(String actor) {
    this.actor = actor;
  }

  public String getFilterId() {
    return filter_id;
  }

  public void setFilterId(String filter) {
    this.filter_id = filter;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public URLMethods.Method getMethod() {
    return method;
  }

  public void setMethod(URLMethods.Method method) {
    this.method = method;
  }

  public String getIp() {
    return ip;
  }

  public void setIp(String ip) {
    this.ip = ip;
  }

  public String getCountry() {
    return country;
  }

  public void setCountry(String country) {
    this.country = country;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(long timestamp) {
    this.timestamp = timestamp;
  }
}

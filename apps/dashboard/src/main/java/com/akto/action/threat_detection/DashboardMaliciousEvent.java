package com.akto.action.threat_detection;

import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLMethods.Method;

public class DashboardMaliciousEvent {
  private String id;
  private String actor;
  private String filter_id;
  private String url;
  private URLMethods.Method method;
  private int apiCollectionId;
  private String ip;
  private String country;
  private long timestamp;
  private String type;

  public DashboardMaliciousEvent() {}

  public DashboardMaliciousEvent(
      String id,
      String actor,
      String filter,
      String url,
      Method method,
      int apiCollectionId,
      String ip,
      String country,
      long timestamp,
      String type) {
    this.id = id;
    this.actor = actor;
    this.filter_id = filter;
    this.url = url;
    this.method = method;
    this.apiCollectionId = apiCollectionId;
    this.ip = ip;
    this.country = country;
    this.timestamp = timestamp;
    this.type = type;
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

  public int getApiCollectionId() {
    return apiCollectionId;
  }

  public void setApiCollectionId(int apiCollectionId) {
    this.apiCollectionId = apiCollectionId;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

}

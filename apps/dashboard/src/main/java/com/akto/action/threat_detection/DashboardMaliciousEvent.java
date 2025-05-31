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
  private String refId;
  private String subCategory;
  private String severity;
  private String eventType;

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
      String type,
      String refId,
      String subCategory,
      String severity,
      String eventType) {
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
    this.refId = refId;
    this.subCategory = subCategory;
    this.severity = severity;
    this.eventType = eventType;
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

  public String getRefId() {
    return refId;
  }

  public void setRefId(String refId) {
    this.refId = refId;
  }

  public String getSubCategory() {
    return subCategory;
  }

  public void setSubCategory(String subCategory) {
    this.subCategory = subCategory;
  }

  public String getSeverity() {
    return severity;
  }

  public void setSeverity(String severity) {
    this.severity = severity;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

}

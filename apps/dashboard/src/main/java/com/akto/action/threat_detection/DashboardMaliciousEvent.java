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
  private String category;
  private String subCategory;
  private String eventType;
  private String payload;
  private String metadata;
  private boolean successfulExploit;
  private String status;
  private String label;
  private String host;
  private String jiraTicketUrl;
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
      String category,
      String subCategory,
      String eventType,
      String payload,
      String metadata,
      boolean successfulExploit,
      String status,
      String label,
      String host,
      String jiraTicketUrl) {
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
    this.category = category;
    this.subCategory = subCategory;
    this.eventType = eventType;
    this.payload = payload;
    this.metadata = metadata;
    this.successfulExploit = successfulExploit;
    this.status = status;
    this.label = label;
    this.host = host;
    this.jiraTicketUrl = jiraTicketUrl;
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

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getSubCategory() {
    return subCategory;
  }

  public void setSubCategory(String subCategory) {
    this.subCategory = subCategory;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public String getPayload() {
    return payload;
  }

  public void setPayload(String payload) {
    this.payload = payload;
  }

  public String getMetadata() {
    return metadata;
  }

  public void setMetadata(String metadata) {
    this.metadata = metadata;
  }

  public boolean getSuccessfulExploit() {
    return successfulExploit;
  }

  public void setSuccessfulExploit(boolean successfulExploit) {
    this.successfulExploit = successfulExploit;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public String getJiraTicketUrl() {
    return jiraTicketUrl;
  }

  public void setJiraTicketUrl(String jiraTicketUrl) {
    this.jiraTicketUrl = jiraTicketUrl;
  }

}

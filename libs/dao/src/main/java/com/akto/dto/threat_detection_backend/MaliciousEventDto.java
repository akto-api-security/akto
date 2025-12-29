package com.akto.dto.threat_detection_backend;

import com.akto.dto.type.URLMethods;

import java.util.UUID;

public class MaliciousEventDto {

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
  private String category;
  private String subCategory;
  private String type;
  private String refId;
  private String severity;
  private String metadata;
  private Boolean successfulExploit;
  private Status status;
  private Label label;
  private String host;
  private String jiraTicketUrl;

  public enum EventType {
    SINGLE,
    AGGREGATED
  }

  public enum Status {
    ACTIVE,
    UNDER_REVIEW,
    IGNORED
  }

  public enum Label {
    THREAT,
    GUARDRAIL
  }

  public MaliciousEventDto() {}

  private MaliciousEventDto(Builder builder) {
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
    this.category = builder.category;
    this.subCategory = builder.subCategory;
    this.severity = builder.severity;
    this.type = builder.type;
    this.refId = builder.refId;
    this.status = builder.status != null ? builder.status : Status.ACTIVE;
    this.successfulExploit = builder.successfulExploit;
    this.label = builder.label;
    this.host = builder.host;
    this.metadata = builder.metadata;
    this.jiraTicketUrl = builder.jiraTicketUrl;
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
    private String category;
    private String subCategory;
    private String refId;
    private String type;
    private String severity;
    private String metadata;
    private Status status;
    private Boolean successfulExploit;
    private Label label;
    private String host;
    private String jiraTicketUrl;
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

    public Builder setCategory(String category) {
      this.category = category;
      return this;
    }

    public Builder setSubCategory(String subCategory) {
      this.subCategory = subCategory;
      return this;
    }

    public Builder setRefId(String refId) {
      this.refId = refId;
      return this;
    }

    public Builder setSeverity(String severity) {
      this.severity = severity;
      return this;
    }

    public Builder setType(String type) {
      this.type = type;
      return this;
    }

    public Builder setMetadata(String metadata) { 
      this.metadata = metadata;
      return this;
    }

    public Builder setSuccessfulExploit(Boolean successfulExploit) {
      this.successfulExploit = successfulExploit;
      return this;
    }

    public Builder setStatus(Status status) {
      this.status = status;
      return this;
    }

    public Builder setLabel(Label label) {
      this.label = label;
      return this;
    }

    public Builder setHost(String host) {
      this.host = host;
      return this;
    }

    public Builder setJiraTicketUrl(String jiraTicketUrl) {
      this.jiraTicketUrl = jiraTicketUrl;
      return this;
    }

    public MaliciousEventDto build() {
      return new MaliciousEventDto(this);
    }
  }

  public String getMetadata() {
    return metadata;
  }

  public void setMetadata(String metadata) {
    this.metadata = metadata;
  }

  public Boolean getSuccessfulExploit() {
    return successfulExploit;
  }

  public void setSuccessfulExploit(Boolean successfulExploit) {
    this.successfulExploit = successfulExploit;
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

  public String getSeverity() {
    return severity;
  }
  
  public void setSeverity(String severity) {
    this.severity = severity;
  }

  public Status getStatus() {
    return status != null ? status : Status.ACTIVE;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public Label getLabel() {
    return label;
  }

  public void setLabel(Label label) {
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

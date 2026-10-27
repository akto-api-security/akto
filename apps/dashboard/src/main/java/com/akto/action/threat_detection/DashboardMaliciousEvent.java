package com.akto.action.threat_detection;

import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLMethods.Method;
import java.util.List;

public class DashboardMaliciousEvent {

  public static class OwaspCategory {
    private String id;
    private String name;
    private String severity;
    private String confidence;

    public OwaspCategory() {}

    public OwaspCategory(String id, String name, String severity, String confidence) {
      this.id = id;
      this.name = name;
      this.severity = severity;
      this.confidence = confidence;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }
  }

  // One entry of the case history. activityType says which of status, owner and notes the entry is about; the
  // others are empty. STATUS: status = the new status. OWNER: owner = the new owner (empty when unassigned).
  // NOTES: notes = the notes as saved (empty when cleared).
  public static class CaseActivity {
    private long timestamp;
    private String updatedBy;
    private String activityType;
    private String status;
    private String owner;
    private String notes;

    public CaseActivity() {}

    public CaseActivity(long timestamp, String updatedBy, String activityType, String status, String owner, String notes) {
      this.timestamp = timestamp;
      this.updatedBy = updatedBy;
      this.activityType = activityType;
      this.status = status;
      this.owner = owner;
      this.notes = notes;
    }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public String getActivityType() { return activityType; }
    public void setActivityType(String activityType) { this.activityType = activityType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
  }
  private String id;
  private String actor;
  private String filter_id;
  private String url;
  private URLMethods.Method method;
  private int apiCollectionId;
  private String ip;
  private String country;
  private String destCountry;
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
  private String severity;
  private String sessionId;
  private List<OwaspCategory> owaspCategories;
  private String remediation;
  private String evidenceLine;
  private String humanResponse;
  // Case management for events marked for review.
  private String caseOwner;
  private String caseNotes;
  private List<CaseActivity> caseTimeline;
  // Grouped activity row (repeated Bedrock prompt from one caller): how many events are behind it, the
  // first one's time, their ids, and the policies they tripped.
  private int groupCount;
  private long groupFirstDetectedAt;
  private List<String> groupEventIds;
  private List<String> groupFilterIds;
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
      String destCountry,
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
      String jiraTicketUrl,
      String severity,
      String sessionId) {
    this.id = id;
    this.actor = actor;
    this.filter_id = filter;
    this.url = url;
    this.method = method;
    this.apiCollectionId = apiCollectionId;
    this.ip = ip;
    this.country = country;
    this.destCountry = destCountry;
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
    this.severity = severity;
    this.sessionId = sessionId;
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

  public String getDestCountry() {
    return destCountry;
  }

  public void setDestCountry(String destCountry) {
    this.destCountry = destCountry;
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

  public String getSeverity() {
    return severity;
  }

  public void setSeverity(String severity) {
    this.severity = severity;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public List<OwaspCategory> getOwaspCategories() {
    return owaspCategories;
  }

  public void setOwaspCategories(List<OwaspCategory> owaspCategories) {
    this.owaspCategories = owaspCategories;
  }

  public String getRemediation() {
    return remediation;
  }

  public void setRemediation(String remediation) {
    this.remediation = remediation;
  }

  public String getHumanResponse() {
    return humanResponse;
  }

  public void setHumanResponse(String humanResponse) {
    this.humanResponse = humanResponse;
  }

  public int getGroupCount() {
    return groupCount;
  }

  public void setGroupCount(int groupCount) {
    this.groupCount = groupCount;
  }

  public long getGroupFirstDetectedAt() {
    return groupFirstDetectedAt;
  }

  public void setGroupFirstDetectedAt(long groupFirstDetectedAt) {
    this.groupFirstDetectedAt = groupFirstDetectedAt;
  }

  public List<String> getGroupEventIds() {
    return groupEventIds;
  }

  public void setGroupEventIds(List<String> groupEventIds) {
    this.groupEventIds = groupEventIds;
  }

  public List<String> getGroupFilterIds() {
    return groupFilterIds;
  }

  public void setGroupFilterIds(List<String> groupFilterIds) {
    this.groupFilterIds = groupFilterIds;
  }

  public String getEvidenceLine() {
    return evidenceLine;
  }

  public void setEvidenceLine(String evidenceLine) {
    this.evidenceLine = evidenceLine;
  }

  public String getCaseOwner() {
    return caseOwner;
  }

  public void setCaseOwner(String caseOwner) {
    this.caseOwner = caseOwner;
  }

  public String getCaseNotes() {
    return caseNotes;
  }

  public void setCaseNotes(String caseNotes) {
    this.caseNotes = caseNotes;
  }

  public List<CaseActivity> getCaseTimeline() {
    return caseTimeline;
  }

  public void setCaseTimeline(List<CaseActivity> caseTimeline) {
    this.caseTimeline = caseTimeline;
  }

}

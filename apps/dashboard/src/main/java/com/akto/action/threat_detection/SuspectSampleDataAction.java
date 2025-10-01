package com.akto.action.threat_detection;

import com.akto.ProtoMessageUtils;
import com.akto.dao.context.Context;
import com.akto.dao.monitoring.FilterYamlTemplateDao;
import com.akto.dto.traffic.SuspectSampleData;
import com.akto.dto.type.URLMethods;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchAlertFiltersResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListMaliciousRequestsResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.UpdateMaliciousEventStatusRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.UpdateMaliciousEventStatusResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.DeleteMaliciousEventsRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.DeleteMaliciousEventsResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListMaliciousRequestsRequest.Filter;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.TimeRangeFilter;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import static com.akto.action.threat_detection.utils.ThreatsUtils.getTemplates;
import lombok.Getter;
import lombok.Setter;

public class SuspectSampleDataAction extends AbstractThreatDetectionAction {

  List<SuspectSampleData> sampleData;
  List<DashboardMaliciousEvent> maliciousEvents;
  int skip;
  int limit;
  static final int LIMIT = 50;
  List<String> ips;
  List<String> urls;
  List<Integer> apiCollectionIds;
  long total;
  Map<String, Integer> sort;
  List<String> severity;
  List<String> subCategory;
  int startTimestamp, endTimestamp;
  List<String> types;
  List<String> latestAttack;
  Boolean successfulExploit; 
  @Getter @Setter String eventId;
  @Getter @Setter String status;
  @Getter @Setter boolean updateSuccess;
  @Getter @Setter String updateMessage;
  @Getter @Setter String statusFilter;
  @Getter @Setter List<String> eventIds;
  @Getter @Setter int updatedCount;
  @Getter @Setter List<String> actors;
  @Getter @Setter String newStatus;
  @Getter @Setter boolean deleteSuccess;
  @Getter @Setter String deleteMessage;
  @Getter @Setter int deletedCount;

  // TODO: remove this, use API Executor.
  private final CloseableHttpClient httpClient;

  private final ObjectMapper objectMapper = new ObjectMapper();

  public SuspectSampleDataAction() {
    super();
    this.httpClient = HttpClients.createDefault();
  }

  public String fetchSampleData() {
    HttpPost post = new HttpPost(
        String.format("%s/api/dashboard/list_malicious_requests", this.getBackendUrl()));
    post.addHeader("Authorization", "Bearer " + this.getApiToken());
    post.addHeader("Content-Type", "application/json");

    Map<String, Object> filter = new HashMap<>();
    if (this.ips != null && !this.ips.isEmpty()) {
      filter.put("ips", this.ips);
    }

    if (this.severity != null && !this.severity.isEmpty()) {
      filter.put("severity", this.severity);
    }

    if (this.subCategory != null && !this.subCategory.isEmpty()) {
      filter.put("subCategory", this.subCategory);
    }

    if (this.urls != null && !this.urls.isEmpty()) {
      filter.put("urls", this.urls);
    }

    if(this.types != null && !this.types.isEmpty()){
      filter.put("types", this.types);
    }

    if (this.successfulExploit != null) {
      filter.put("successfulExploit", this.successfulExploit);
    }

    List<String> templates = getTemplates(latestAttack);
    filter.put("latestAttack", templates);

    if (this.statusFilter != null) {
      filter.put("statusFilter", this.statusFilter);
    }

    Map<String, Integer> time_range = new HashMap<>();
    if (this.startTimestamp > 0) {
      time_range.put("start", this.startTimestamp);
    }

    if (this.endTimestamp > 0) {
      time_range.put("end", this.endTimestamp);
    }

    filter.put("detected_at_time_range", time_range);

    Map<String, Object> body = new HashMap<String, Object>() {
      {
        put("skip", skip);
        put("limit", limit > 0 ? limit : LIMIT);
        put("sort", sort);
        put("filter", filter);
      }
    };
    String msg = objectMapper.valueToTree(body).toString();

    StringEntity requestEntity = new StringEntity(msg, ContentType.APPLICATION_JSON);
    post.setEntity(requestEntity);

    try (CloseableHttpResponse resp = this.httpClient.execute(post)) {
      String responseBody = EntityUtils.toString(resp.getEntity());

      ProtoMessageUtils.<ListMaliciousRequestsResponse>toProtoMessage(
          ListMaliciousRequestsResponse.class, responseBody)
          .ifPresent(
              m -> {
                this.maliciousEvents = m.getMaliciousEventsList().stream()
                    .map(
                        smr -> new DashboardMaliciousEvent(
                            smr.getId(),
                            smr.getActor(),
                            smr.getFilterId(),
                            smr.getEndpoint(),
                            URLMethods.Method.fromString(smr.getMethod()),
                            smr.getApiCollectionId(),
                            smr.getIp(),
                            smr.getCountry(),
                            smr.getDetectedAt(),
                            smr.getType(),
                            smr.getRefId(),
                            smr.getCategory(),
                            smr.getSubCategory(),
                            smr.getEventTypeVal(),
                            smr.getPayload(),
                            smr.getMetadata(),
                            smr.getSuccessfulExploit(),
                            smr.getStatus()))
                    .collect(Collectors.toList());
                this.total = m.getTotal();
              });
    } catch (Exception e) {
      e.printStackTrace();
      return ERROR.toUpperCase();
    }

    return SUCCESS.toUpperCase();
  }

  public String fetchFilters() {
    HttpGet get = new HttpGet(String.format("%s/api/dashboard/fetch_filters", this.getBackendUrl()));
    get.addHeader("Authorization", "Bearer " + this.getApiToken());
    get.addHeader("Content-Type", "application/json");

    int accountId = Context.accountId.get();
    CONTEXT_SOURCE source = Context.contextSource.get();

    try (CloseableHttpResponse resp = this.httpClient.execute(get)) {
      String responseBody = EntityUtils.toString(resp.getEntity());

      ProtoMessageUtils.<FetchAlertFiltersResponse>toProtoMessage(
          FetchAlertFiltersResponse.class, responseBody)
          .ifPresent(
              msg -> {
                this.ips = msg.getActorsList();
                this.urls = msg.getUrlsList();
                Set<String> allowedTemplates = FilterYamlTemplateDao.getContextTemplatesForAccount(accountId, source);
                this.subCategory =
                    msg.getSubCategoryList().stream()
                        .filter(allowedTemplates::contains)
                        .collect(Collectors.toList());
              });
    } catch (Exception e) {
      e.printStackTrace();
      return ERROR.toUpperCase();
    }

    return SUCCESS.toUpperCase();
  }

  public String deleteAllMaliciousEvents() {
    HttpPost post = new HttpPost(
            String.format("%s/api/dashboard/delete_all_malicious_events", this.getBackendUrl()));
    post.addHeader("Authorization", "Bearer " + this.getApiToken());
    post.addHeader("Content-Type", "application/json");

    Map<String, Object> body = new HashMap<>();
    String msg = objectMapper.valueToTree(body).toString();

    StringEntity requestEntity = new StringEntity(msg, ContentType.APPLICATION_JSON);
    post.setEntity(requestEntity);

    try (CloseableHttpResponse resp = this.httpClient.execute(post)) {
      String responseBody = EntityUtils.toString(resp.getEntity());
    } catch (Exception e) {
      e.printStackTrace();
      return ERROR.toUpperCase();
    }

    return SUCCESS.toUpperCase();
  }

  private Filter.Builder buildFilterFromParams() {
    Filter.Builder filterBuilder = Filter.newBuilder();

    if (this.actors != null && !this.actors.isEmpty()) {
      filterBuilder.addAllActors(this.actors);
    }
    if (this.urls != null && !this.urls.isEmpty()) {
      filterBuilder.addAllUrls(this.urls);
    }
    if (this.types != null && !this.types.isEmpty()) {
      filterBuilder.addAllTypes(this.types);
    }
    if (this.latestAttack != null && !this.latestAttack.isEmpty()) {
      List<String> templates = getTemplates(latestAttack);
      filterBuilder.addAllLatestAttack(templates);
    }
    if (this.statusFilter != null) {
      filterBuilder.setStatusFilter(this.statusFilter);
    }

    if (this.startTimestamp > 0 || this.endTimestamp > 0) {
      TimeRangeFilter.Builder timeRangeBuilder = TimeRangeFilter.newBuilder();
      if (this.startTimestamp > 0) {
        timeRangeBuilder.setStart(this.startTimestamp);
      }
      if (this.endTimestamp > 0) {
        timeRangeBuilder.setEnd(this.endTimestamp);
      }
      filterBuilder.setDetectedAtTimeRange(timeRangeBuilder);
    }

    return filterBuilder;
  }

  public String updateMaliciousEventStatus() {
    HttpPost post = new HttpPost(
            String.format("%s/api/dashboard/update_malicious_event_status", this.getBackendUrl()));
    post.addHeader("Authorization", "Bearer " + this.getApiToken());
    post.addHeader("Content-Type", "application/json");

    UpdateMaliciousEventStatusRequest.Builder requestBuilder = UpdateMaliciousEventStatusRequest.newBuilder();

    // Check which type of update this is
    if (this.eventId != null && !this.eventId.isEmpty()) {
      // Single event update
      requestBuilder.setEventId(this.eventId);
    } else if (this.eventIds != null && !this.eventIds.isEmpty()) {
      // Bulk update by IDs
      requestBuilder.addAllEventIds(this.eventIds);
    } else {
      // Filter-based update
      Filter.Builder filterBuilder = buildFilterFromParams();
      requestBuilder.setFilter(filterBuilder);
    }

    requestBuilder.setStatus(this.status);
    UpdateMaliciousEventStatusRequest request = requestBuilder.build();

    String msg = ProtoMessageUtils.toString(request).orElse("{}");
    StringEntity requestEntity = new StringEntity(msg, ContentType.APPLICATION_JSON);
    post.setEntity(requestEntity);

    try (CloseableHttpResponse resp = this.httpClient.execute(post)) {
      String responseBody = EntityUtils.toString(resp.getEntity());

      if (resp.getStatusLine().getStatusCode() != 200) {
        this.updateSuccess = false;
        this.updateMessage = "Failed to update status: " + responseBody;
        this.updatedCount = 0;
        return ERROR.toUpperCase();
      }

      Optional<UpdateMaliciousEventStatusResponse> responseOpt = ProtoMessageUtils.<UpdateMaliciousEventStatusResponse>toProtoMessage(
          UpdateMaliciousEventStatusResponse.class, responseBody);
      if (responseOpt.isPresent()) {
        UpdateMaliciousEventStatusResponse response = responseOpt.get();
        this.updateSuccess = response.getSuccess();
        this.updateMessage = response.getMessage();
        this.updatedCount = response.getUpdatedCount();
      } else {
        this.updateSuccess = false;
        this.updateMessage = "Failed to update status: Invalid response format";
        this.updatedCount = 0;
      }
    } catch (Exception e) {
      e.printStackTrace();
      this.updateSuccess = false;
      this.updateMessage = "Error updating status: " + e.getMessage();
      this.updatedCount = 0;
      return ERROR.toUpperCase();
    }

    return SUCCESS.toUpperCase();
  }


  // Unified delete method that handles both eventIds and filter
  public String deleteMaliciousEvents() {
    HttpPost post = new HttpPost(
            String.format("%s/api/dashboard/delete_malicious_events", this.getBackendUrl()));
    post.addHeader("Authorization", "Bearer " + this.getApiToken());
    post.addHeader("Content-Type", "application/json");

    DeleteMaliciousEventsRequest.Builder requestBuilder = DeleteMaliciousEventsRequest.newBuilder();

    if (this.eventId != null && !this.eventId.isEmpty()) {
      // Single event delete
      requestBuilder.addEventIds(this.eventId);
    } else if (this.eventIds != null && !this.eventIds.isEmpty()) {
      // Bulk delete by IDs
      requestBuilder.addAllEventIds(this.eventIds);
    } else {
      // Delete by filter
      Filter.Builder filterBuilder = buildFilterFromParams();
      requestBuilder.setFilter(filterBuilder);
    }

    DeleteMaliciousEventsRequest request = requestBuilder.build();

    String msg = ProtoMessageUtils.toString(request).orElse("{}");
    StringEntity requestEntity = new StringEntity(msg, ContentType.APPLICATION_JSON);
    post.setEntity(requestEntity);

    try (CloseableHttpResponse resp = this.httpClient.execute(post)) {
      String responseBody = EntityUtils.toString(resp.getEntity());

      if (resp.getStatusLine().getStatusCode() != 200) {
        this.deleteSuccess = false;
        this.deleteMessage = "Failed to delete events: " + responseBody;
        this.deletedCount = 0;
        return ERROR.toUpperCase();
      }

      Optional<DeleteMaliciousEventsResponse> responseOpt = ProtoMessageUtils.<DeleteMaliciousEventsResponse>toProtoMessage(
          DeleteMaliciousEventsResponse.class, responseBody);
      if (responseOpt.isPresent()) {
        DeleteMaliciousEventsResponse response = responseOpt.get();
        this.deleteSuccess = response.getSuccess();
        this.deletedCount = response.getDeletedCount();
        this.deleteMessage = response.getMessage();
      } else {
        this.deleteSuccess = false;
        this.deleteMessage = "Failed to delete events: Invalid response format";
        this.deletedCount = 0;
      }
    } catch (Exception e) {
      e.printStackTrace();
      this.deleteSuccess = false;
      this.deleteMessage = "Error deleting events: " + e.getMessage();
      this.deletedCount = 0;
      return ERROR.toUpperCase();
    }

    return SUCCESS.toUpperCase();
  }


  public List<SuspectSampleData> getSampleData() {
    return sampleData;
  }

  public void setSampleData(List<SuspectSampleData> sampleData) {
    this.sampleData = sampleData;
  }

  public int getSkip() {
    return skip;
  }

  public void setSkip(int skip) {
    this.skip = skip;
  }

  public int getLimit() {
    return limit > 0 ? limit : LIMIT;
  }

  public void setLimit(int limit) {
    this.limit = limit;
  }


  public List<String> getIps() {
    return ips;
  }

  public void setIps(List<String> ips) {
    this.ips = ips;
  }

  public List<String> getUrls() {
    return urls;
  }

  public void setUrls(List<String> urls) {
    this.urls = urls;
  }

  public List<Integer> getApiCollectionIds() {
    return apiCollectionIds;
  }

  public void setApiCollectionIds(List<Integer> apiCollectionIds) {
    this.apiCollectionIds = apiCollectionIds;
  }

  public long getTotal() {
    return total;
  }

  public void setTotal(long total) {
    this.total = total;
  }

  public Map<String, Integer> getSort() {
    return sort;
  }

  public void setSort(Map<String, Integer> sort) {
    this.sort = sort;
  }

  public int getStartTimestamp() {
    return startTimestamp;
  }

  public void setStartTimestamp(int startTimestamp) {
    this.startTimestamp = startTimestamp;
  }

  public int getEndTimestamp() {
    return endTimestamp;
  }

  public void setEndTimestamp(int endTimestamp) {
    this.endTimestamp = endTimestamp;
  }

  public List<DashboardMaliciousEvent> getMaliciousEvents() {
    return maliciousEvents;
  }

  public void setMaliciousEvents(List<DashboardMaliciousEvent> maliciousRequests) {
    this.maliciousEvents = maliciousRequests;
  }

  public void setTypes(List<String> types) {
    this.types = types;
  }

  public List<String> getTypes() {
    return types;
  }

  public List<String> getSeverity() {
    return severity;
  }

  public void setSeverity(List<String> severity) {
    this.severity = severity;
  }

  public List<String> getSubCategory() {
    return subCategory;
  }

  public void setSubCategory(List<String> subCategory) {
    this.subCategory = subCategory;
  }

  public List<String> getLatestAttack() {
    return latestAttack;
  }

  public void setLatestAttack(List<String> latestAttack) {
    this.latestAttack = latestAttack;
  }

  public Boolean getSuccessfulExploit() {
    return successfulExploit;
  }

  public void setSuccessfulExploit(Boolean successfulExploit) {
    this.successfulExploit = successfulExploit;
  }
}

package com.akto.action.threat_detection;

import com.akto.ProtoMessageUtils;
import com.akto.dao.context.Context;
import com.akto.dao.monitoring.FilterYamlTemplateDao;
import com.akto.dto.traffic.SuspectSampleData;
import com.akto.dto.type.URLMethods;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchAlertFiltersResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListMaliciousRequestsResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.DeleteMaliciousEventsRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.DeleteMaliciousEventsResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListMaliciousRequestsRequest.Filter;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.TimeRangeFilter;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.akto.action.threat_detection.utils.ThreatDetectionHelper;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.billing.Organization;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.http_util.CoreHTTPClient;
import com.mongodb.BasicDBObject;
import org.apache.commons.lang3.StringUtils;
import lombok.Getter;
import lombok.Setter;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.util.concurrent.TimeUnit;

public class SuspectSampleDataAction extends AbstractThreatDetectionAction {

  List<SuspectSampleData> sampleData;
  List<DashboardMaliciousEvent> maliciousEvents;
  int skip;
  int limit;
  static final int LIMIT = 50;
  List<String> ips;
  List<String> urls;
  List<Integer> apiCollectionIds;
  List<String> method;
  long total;
  Map<String, Integer> sort;
  List<String> severity;
  List<String> subCategory;
  int startTimestamp, endTimestamp;
  List<String> types;
  List<String> latestAttack;
  Boolean successfulExploit;
  @Getter @Setter String label;
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
  @Getter @Setter List<String> hosts;
  @Getter @Setter String latestApiOrigRegex;
  @Getter @Setter Boolean sortBySeverity;

  // TODO: remove this, use API Executor.
  private final CloseableHttpClient httpClient;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private static final LoggerMaker loggerMaker = new LoggerMaker(SuspectSampleDataAction.class, LogDb.DASHBOARD);
  private static final String AGENT_TRAFFIC_FEATURE_LABEL = "AGENT_TRAFFIC_LOGS";
  private static final String AGENT_TRAFFIC_ANALYZER_URL_ENV = "AGENT_TRAFFIC_ANALYZER_URL";
  private static final ExecutorService executorService = Executors.newFixedThreadPool(1);
  private static final OkHttpClient agentTrafficAnalyzerClient = CoreHTTPClient.client.newBuilder()
    .connectTimeout(30000, TimeUnit.MILLISECONDS)
    .readTimeout(30000, TimeUnit.MILLISECONDS)
    .writeTimeout(30000, TimeUnit.MILLISECONDS)
    .build();

  public SuspectSampleDataAction() {
    super();
    this.httpClient = HttpClients.createDefault();
  }

  public String fetchSampleData() {
    HttpPost post = new HttpPost(
        String.format("%s/api/dashboard/list_malicious_requests", this.getBackendUrl()));
    post.addHeader("Authorization", "Bearer " + this.getApiToken());
    post.addHeader("Content-Type", "application/json");
    post.addHeader("x-context-source", Context.contextSource.get() != null ? Context.contextSource.get().toString() : "");

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

    if (this.apiCollectionIds != null && !this.apiCollectionIds.isEmpty()) {
      filter.put("apiCollectionId", this.apiCollectionIds);
    }

    if (this.method != null && !this.method.isEmpty()) {
      filter.put("method", this.method);
    }

    if(this.types != null && !this.types.isEmpty()){
      filter.put("types", this.types);
    }

    if (this.successfulExploit != null) {
      filter.put("successfulExploit", this.successfulExploit);
    }

    if (this.label != null && !this.label.isEmpty()) {
      filter.put("label", this.label);
    }

    if (this.hosts != null && !this.hosts.isEmpty()) {
      filter.put("hosts", this.hosts);
    }

    if (this.latestApiOrigRegex != null && !this.latestApiOrigRegex.isEmpty()) {
      filter.put("latestApiOrigRegex", this.latestApiOrigRegex);
    }

    filter.put("latestAttack", latestAttack);

    if (this.statusFilter != null) {
      filter.put("statusFilter", this.statusFilter);
    }

    if (this.sortBySeverity != null) {
      filter.put("sortBySeverity", this.sortBySeverity);
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
                            smr.getStatus(),
                            smr.getLabel(),
                            smr.getHost(),
                            smr.getJiraTicketUrl(),
                            smr.getSeverity(),
                            smr.getSessionContext() != null && !smr.getSessionContext().isEmpty() ? smr.getSessionContext() : ""))
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
                this.hosts = msg.getHostsList();
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
    if (this.apiCollectionIds != null && !this.apiCollectionIds.isEmpty()) {
      filterBuilder.addAllApiCollectionId(this.apiCollectionIds);
    }
    if (this.method != null && !this.method.isEmpty()) {
      filterBuilder.addAllMethod(this.method);
    }
    if (this.types != null && !this.types.isEmpty()) {
      filterBuilder.addAllTypes(this.types);
    }
    // Always populate latestAttack, even if empty
    if (latestAttack != null && !latestAttack.isEmpty()) {
      filterBuilder.addAllLatestAttack(latestAttack);
    }
    if (this.statusFilter != null) {
      filterBuilder.setStatusFilter(this.statusFilter);
    }
    if (this.label != null && !this.label.isEmpty()) {
      filterBuilder.setLabel(this.label);
    }

    if (this.latestApiOrigRegex != null && !this.latestApiOrigRegex.isEmpty()) {
      filterBuilder.setLatestApiOrigRegex(this.latestApiOrigRegex);
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
    // Build filter if needed for filter-based updates
    Filter.Builder filterBuilder = null;
    if ((this.eventId == null || this.eventId.isEmpty()) &&
        (this.eventIds == null || this.eventIds.isEmpty())) {
      filterBuilder = buildFilterFromParams();
    }

    // Use the helper method to perform the update
    ThreatDetectionHelper.UpdateResult result =
        ThreatDetectionHelper.updateMaliciousEvent(
            this.httpClient,
            this.getBackendUrl(),
            this.getApiToken(),
            this.eventId,
            this.eventIds,
            filterBuilder,
            this.status,
            null  // No Jira URL in this method
        );

    // Set response fields from result
    this.updateSuccess = result.isSuccess();
    this.updateMessage = result.getMessage();
    this.updatedCount = result.getUpdatedCount();

    // If status update was successful and status is TRAINING, call agent-traffic-analyzer asynchronously
    if (this.updateSuccess && "TRAINING".equalsIgnoreCase(this.status)) {
      // Capture context and data before async execution
      final int accountId = Context.accountId.get();
      final String eventId = this.eventId;
      final List<String> eventIds = this.eventIds;
      
      submitTrainingDataToAnalyzer(accountId, eventId, eventIds);
    }

    return this.updateSuccess ? SUCCESS.toUpperCase() : ERROR.toUpperCase();
  }

  /**
   * Submits training data to agent-traffic-analyzer for events marked for training.
   * Only processes if feature flag is enabled and we have event IDs.
   * Executes asynchronously after feature flag check.
   * 
   * @param accountId The account ID to use for context
   * @param eventId Single event ID (optional)
   * @param eventIds List of event IDs (optional)
   */
  private void submitTrainingDataToAnalyzer(int accountId, String eventId, List<String> eventIds) {
    // Check feature flag first before creating async task
    Organization organization = OrganizationsDao.instance.findOneByAccountId(accountId);
    if (organization == null || organization.getFeatureWiseAllowed() == null) {
      loggerMaker.infoAndAddToDb(
        String.format("Organization or featureWiseAllowed not found for account %d, skipping training data submission", accountId),
        LogDb.DASHBOARD
      );
      return;
    }

    FeatureAccess featureAccess = organization.getFeatureWiseAllowed().get(AGENT_TRAFFIC_FEATURE_LABEL);
    if (featureAccess == null || !featureAccess.getIsGranted()) {
      loggerMaker.infoAndAddToDb(
        String.format("Feature %s not granted for account %d, skipping training data submission", AGENT_TRAFFIC_FEATURE_LABEL, accountId),
        LogDb.DASHBOARD
      );
      return;
    }

    // Only create async task if feature flag is enabled
    executorService.submit(() -> {
      try {
        // Set context for async thread
        Context.accountId.set(accountId);
        submitTrainingDataToAnalyzerAsync(accountId, eventId, eventIds);
      } catch (Exception e) {
        loggerMaker.errorAndAddToDb(
          "Error in async submitTrainingDataToAnalyzer: " + e.getMessage(),
          LogDb.DASHBOARD
        );
      }
    });
  }

  /**
   * Async implementation of training data submission.
   * This method runs in a background thread after feature flag validation.
   * 
   * @param accountId The account ID to use for context
   * @param eventId Single event ID (optional)
   * @param eventIds List of event IDs (optional)
   */
  private void submitTrainingDataToAnalyzerAsync(int accountId, String eventId, List<String> eventIds) {
    try {

      // Get event IDs to process
      List<String> eventIdsToProcess = null;
      if (eventId != null && !eventId.isEmpty()) {
        eventIdsToProcess = Arrays.asList(eventId);
      } else if (eventIds != null && !eventIds.isEmpty()) {
        eventIdsToProcess = eventIds;
      }

      if (eventIdsToProcess == null || eventIdsToProcess.isEmpty()) {
        // For filter-based updates, we can't easily get event IDs here
        // The training data will be processed by the backend threat detection service or Kafka consumer
        loggerMaker.infoAndAddToDb(
          "No specific event IDs available for training data submission (filter-based update), skipping",
          LogDb.DASHBOARD
        );
        return;
      }

      // Get analyzer service URL
      String analyzerUrl = System.getenv(AGENT_TRAFFIC_ANALYZER_URL_ENV);
      if (StringUtils.isBlank(analyzerUrl)) {
        return;
      }

      // Submit feedback for each event
      // Note: We'll need to fetch event details to get the prompt/payload
      // For now, we submit with event ID - the analyzer can fetch details if needed
      for (String eventIdToProcess : eventIdsToProcess) {
        try {
          Map<String, Object> feedbackBody = new HashMap<>();
          feedbackBody.put("prompt", "Training data for event " + eventIdToProcess); // Placeholder - will be enhanced with actual prompt extraction
          feedbackBody.put("label", "false_positive"); // Training data is treated as false_positive
          feedbackBody.put("request_id", eventIdToProcess);
          Map<String, Object> metadata = new HashMap<>();
          metadata.put("event_id", eventIdToProcess);
          metadata.put("source", "training_data");
          feedbackBody.put("metadata", metadata);

          String url = analyzerUrl + "/feedback";
          String jsonBody = new BasicDBObject(feedbackBody).toJson();
          
          Request request = new Request.Builder()
            .url(url)
            .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
            .build();
          
          try (Response httpResponse = agentTrafficAnalyzerClient.newCall(request).execute()) {
            if (!httpResponse.isSuccessful()) {
              loggerMaker.errorAndAddToDb(
                String.format("Agent Guardrail Feedback request failed for event %s with code %d: %s", 
                  eventIdToProcess, httpResponse.code(), httpResponse.message()),
                LogDb.DASHBOARD
              );
            }
          }
        } catch (Exception e) {
          loggerMaker.errorAndAddToDb(
            String.format("Error submitting training data for event %s: %s", eventIdToProcess, e.getMessage()),
            LogDb.DASHBOARD
          );
          // Continue with other events
        }
      }
    } catch (Exception e) {
      loggerMaker.errorAndAddToDb(
        "Error in submitTrainingDataToAnalyzer: " + e.getMessage(),
        LogDb.DASHBOARD
      );
      // Don't fail the status update if training submission fails
    }
  }

  /**
   * Updates malicious event with Jira ticket URL using the shared helper
   */
  public String updateMaliciousEventJiraUrl(String eventId, String jiraTicketUrl) {
    ThreatDetectionHelper.UpdateResult result =
        ThreatDetectionHelper.updateMaliciousEventJiraUrl(
            this.httpClient,
            this.getBackendUrl(),
            this.getApiToken(),
            eventId,
            jiraTicketUrl
        );

    this.updateSuccess = result.isSuccess();
    this.updateMessage = result.getMessage();
    this.updatedCount = result.getUpdatedCount();

    return result.isSuccess() ? SUCCESS.toUpperCase() : ERROR.toUpperCase();
  }


  // Unified delete method that handles both eventIds and filter
  public String deleteMaliciousEvents() {
    HttpPost post = new HttpPost(
            String.format("%s/api/dashboard/delete_malicious_events", this.getBackendUrl()));
    post.addHeader("Authorization", "Bearer " + this.getApiToken());
    post.addHeader("Content-Type", "application/json");
    post.addHeader("x-context-source", Context.contextSource.get() != null ? Context.contextSource.get().toString() : "");

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

  public List<String> getMethod() {
    return method;
  }

  public void setMethod(List<String> method) {
    this.method = method;
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

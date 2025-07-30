package com.akto.action.threat_detection;

import com.akto.ProtoMessageUtils;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.traffic.SuspectSampleData;
import com.akto.dto.type.URLMethods;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchAlertFiltersResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListMaliciousRequestsResponse;
import com.akto.testing.ApiExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

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


  private final ObjectMapper objectMapper = new ObjectMapper();

  public SuspectSampleDataAction() {
    super();

  }

  public String fetchSampleData() {
    String requestUrl = String.format("%s/api/dashboard/list_malicious_requests", this.getBackendUrl());

    Map<String, List<String>> headers = new HashMap<>();
    headers.put("Authorization", Collections.singletonList("Bearer " + this.getApiToken()));
    headers.put("Content-Type", Collections.singletonList("application/json"));

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
    OriginalHttpRequest request = new OriginalHttpRequest(requestUrl, "", "POST", msg, headers, "application/json");

    try {
      OriginalHttpResponse resp = ApiExecutor.sendRequest(request, true, null, false, null);
      String responseBody = resp.getBody();

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
                            smr.getMetadata()))
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
    String requestUrl = String.format("%s/api/dashboard/fetch_filters", this.getBackendUrl());
    Map<String, List<String>> headers = new HashMap<>();
    headers.put("Authorization", Collections.singletonList("Bearer " + this.getApiToken()));
    headers.put("Content-Type", Collections.singletonList("application/json"));

    OriginalHttpRequest request = new OriginalHttpRequest(requestUrl, "", "GET", "", headers, "application/json");

    try {
      OriginalHttpResponse resp = ApiExecutor.sendRequest(request, true, null, false, null);  
      String responseBody = resp.getBody();

      ProtoMessageUtils.<FetchAlertFiltersResponse>toProtoMessage(
          FetchAlertFiltersResponse.class, responseBody)
          .ifPresent(
              msg -> {
                this.ips = msg.getActorsList();
                this.urls = msg.getUrlsList();
                this.subCategory = msg.getSubCategoryList();
              });
    } catch (Exception e) {
      e.printStackTrace();
      return ERROR.toUpperCase();
    }

    return SUCCESS.toUpperCase();
  }

  public String deleteAllMaliciousEvents() {
    String requestUrl = String.format("%s/api/dashboard/delete_all_malicious_events", this.getBackendUrl());
    Map<String, List<String>> headers = new HashMap<>();
    headers.put("Authorization", Collections.singletonList("Bearer " + this.getApiToken()));
    headers.put("Content-Type", Collections.singletonList("application/json"));

    Map<String, Object> body = new HashMap<>();
    String msg = objectMapper.valueToTree(body).toString();

    OriginalHttpRequest request = new OriginalHttpRequest(requestUrl, "", "POST", msg, headers, "application/json");

    try {
      OriginalHttpResponse resp = ApiExecutor.sendRequest(request, true, null, false, null);
      String responseBody = resp.getBody();
    } catch (Exception e) {
      e.printStackTrace();
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
}

package com.akto.action.threat_detection;

import com.akto.action.UserAction;
import com.akto.dto.traffic.SuspectSampleData;
import com.akto.dto.type.URLMethods;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchAlertFiltersResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListMaliciousRequestsResponse;
import com.akto.proto.utils.ProtoMessageUtils;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public class SuspectSampleDataAction extends UserAction {

  List<SuspectSampleData> sampleData;
  List<DashboardMaliciousEvent> maliciousEvents;
  int skip;
  static final int LIMIT = 50;
  List<String> ips;
  List<String> urls;
  List<Integer> apiCollectionIds;
  long total;
  Map<String, Integer> sort;
  int startTimestamp, endTimestamp;

  private final CloseableHttpClient httpClient;
  private final String backendUrl;
  private final String backendToken;

  public SuspectSampleDataAction() {
    super();
    this.httpClient = HttpClients.createDefault();
    this.backendUrl = System.getenv("THREAT_DETECTION_BACKEND_URL");
    this.backendToken = System.getenv("THREAT_DETECTION_BACKEND_TOKEN");
  }

  public String fetchSampleDataV2() {
    HttpGet get =
        new HttpGet(String.format("%s/api/dashboard/list_malicious_requests", backendUrl));
    get.addHeader("Authorization", "Bearer " + backendToken);
    get.addHeader("Content-Type", "application/json");

    try (CloseableHttpResponse resp = this.httpClient.execute(get)) {
      String responseBody = EntityUtils.toString(resp.getEntity());

      ProtoMessageUtils.<ListMaliciousRequestsResponse>toProtoMessage(
              ListMaliciousRequestsResponse.class, responseBody)
          .ifPresent(
              msg -> {
                this.maliciousEvents =
                    msg.getMaliciousEventsList().stream()
                        .map(
                            smr ->
                                new DashboardMaliciousEvent(
                                    smr.getId(),
                                    smr.getActor(),
                                    smr.getFilterId(),
                                    smr.getEndpoint(),
                                    URLMethods.Method.fromString(smr.getMethod()),
                                    smr.getApiCollectionId(),
                                    smr.getIp(),
                                    smr.getCountry(),
                                    smr.getDetectedAt()))
                        .collect(Collectors.toList());
              });
    } catch (Exception e) {
      e.printStackTrace();
      return ERROR.toUpperCase();
    }

    return SUCCESS.toUpperCase();
  }

  public String fetchFiltersV2() {
    HttpGet get = new HttpGet(String.format("%s/api/dashboard/fetch_filters", backendUrl));
    get.addHeader("Authorization", "Bearer " + backendToken);
    get.addHeader("Content-Type", "application/json");

    try (CloseableHttpResponse resp = this.httpClient.execute(get)) {
      String responseBody = EntityUtils.toString(resp.getEntity());

      ProtoMessageUtils.<FetchAlertFiltersResponse>toProtoMessage(
              FetchAlertFiltersResponse.class, responseBody)
          .ifPresent(
              msg -> {
                this.ips = msg.getActorsList();
                this.urls = msg.getUrlsList();
              });
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

  public static int getLimit() {
    return LIMIT;
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
}

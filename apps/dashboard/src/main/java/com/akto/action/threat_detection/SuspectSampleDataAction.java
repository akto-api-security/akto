package com.akto.action.threat_detection;

import com.akto.action.UserAction;
import com.akto.dto.traffic.SuspectSampleData;
import com.akto.dto.type.URLMethods;
import com.akto.grpc.auth.AuthToken;
import com.akto.proto.generated.threat_detection.message.malicious_event.dashboard.v1.DashboardMaliciousEventMessage;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.DashboardServiceGrpc;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.DashboardServiceGrpc.DashboardServiceBlockingStub;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchAlertFiltersRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchAlertFiltersResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListMaliciousRequestsRequest;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

  private final DashboardServiceBlockingStub dsServiceStub;

  public SuspectSampleDataAction() {
    super();

    String target = "localhost:8980";
    ManagedChannel channel =
        Grpc.newChannelBuilder(target, InsecureChannelCredentials.create()).build();
    this.dsServiceStub =
        DashboardServiceGrpc.newBlockingStub(channel)
            .withCallCredentials(
                new AuthToken(System.getenv("AKTO_threat_detection_BACKEND_TOKEN")));
  }

  public String fetchSampleDataV2() {
    List<DashboardMaliciousEventMessage> maliciousEvts =
        this.dsServiceStub
            .listMaliciousRequests(
                ListMaliciousRequestsRequest.newBuilder().setPage(0).setLimit(500).build())
            .getMaliciousEventsList();

    this.maliciousEvents =
        maliciousEvts.stream()
            .map(
                mr ->
                    new DashboardMaliciousEvent(
                        mr.getId(),
                        mr.getActor(),
                        mr.getFilterId(),
                        mr.getEndpoint(),
                        URLMethods.Method.fromString(mr.getMethod()),
                        mr.getApiCollectionId(),
                        mr.getIp(),
                        mr.getCountry(),
                        mr.getDetectedAt()))
            .collect(Collectors.toList());

    return SUCCESS.toUpperCase();
  }

  public String fetchFiltersV2() {
    FetchAlertFiltersResponse filters =
        this.dsServiceStub.fetchAlertFilters(FetchAlertFiltersRequest.newBuilder().build());
    ips = filters.getActorsList();
    urls = filters.getUrlsList();

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
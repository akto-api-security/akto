package com.akto.action.threat_detection;

import com.akto.dto.type.URLMethods;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchMaliciousEventsResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListThreatActorResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatActorByCountryResponse;
import com.akto.proto.utils.ProtoMessageUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
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

public class ThreatActorAction extends AbstractThreatDetectionAction {

  List<DashboardThreatActor> actors;
  List<MaliciousPayloadsResponse> maliciousPayloadsResponses;
  List<ThreatActorPerCountry> actorsCountPerCountry;
  int skip;
  static final int LIMIT = 50;
  long total;
  Map<String, Integer> sort;
  int startTimestamp, endTimestamp;
  String refId;

  private final CloseableHttpClient httpClient;

  private final ObjectMapper objectMapper = new ObjectMapper();

  public ThreatActorAction() {
    super();
    this.httpClient = HttpClients.createDefault();
  }

  public String getActorsCountPerCounty() {
    HttpGet get =
        new HttpGet(
            String.format("%s/api/dashboard/get_actors_count_per_country", this.getBackendUrl()));
    get.addHeader("Authorization", "Bearer " + this.getApiToken());
    get.addHeader("Content-Type", "application/json");

    try (CloseableHttpResponse resp = this.httpClient.execute(get)) {
      String responseBody = EntityUtils.toString(resp.getEntity());

      ProtoMessageUtils.<ThreatActorByCountryResponse>toProtoMessage(
              ThreatActorByCountryResponse.class, responseBody)
          .ifPresent(
              m -> {
                this.actorsCountPerCountry =
                    m.getCountriesList().stream()
                        .map(smr -> new ThreatActorPerCountry(smr.getCode(), smr.getCount()))
                        .collect(Collectors.toList());
              });
    } catch (Exception e) {
      e.printStackTrace();
      return ERROR.toUpperCase();
    }

    return SUCCESS.toUpperCase();
  }

  public String fetchThreatActors() {
    HttpPost post =
        new HttpPost(String.format("%s/api/dashboard/list_threat_actors", this.getBackendUrl()));
    post.addHeader("Authorization", "Bearer " + this.getApiToken());
    post.addHeader("Content-Type", "application/json");

    Map<String, Object> body =
        new HashMap<String, Object>() {
          {
            put("skip", skip);
            put("limit", LIMIT);
            put("sort", sort);
          }
        };
    String msg = objectMapper.valueToTree(body).toString();

    StringEntity requestEntity = new StringEntity(msg, ContentType.APPLICATION_JSON);
    post.setEntity(requestEntity);

    try (CloseableHttpResponse resp = this.httpClient.execute(post)) {
      String responseBody = EntityUtils.toString(resp.getEntity());

      ProtoMessageUtils.<ListThreatActorResponse>toProtoMessage(
              ListThreatActorResponse.class, responseBody)
          .ifPresent(
              m -> {
                this.actors =
                    m.getActorsList().stream()
                        .map(
                            smr ->
                                new DashboardThreatActor(
                                    smr.getId(),
                                    smr.getLatestApiEndpoint(),
                                    smr.getLatestApiIp(),
                                    URLMethods.Method.fromString(smr.getLatestApiMethod()),
                                    smr.getDiscoveredAt(),
                                    smr.getCountry(),
                                    smr.getLatestSubcategory(),
                                    smr.getActivityDataList().stream()
                                    .map(subData -> new ActivityData(subData.getUrl(), subData.getSeverity(), subData.getSubCategory(), subData.getDetectedAt()))
                                    .collect(Collectors.toList())))
                        .collect(Collectors.toList());

                this.total = m.getTotal();
              });
    } catch (Exception e) {
      e.printStackTrace();
      return ERROR.toUpperCase();
    }

    return SUCCESS.toUpperCase();
  }

  public String fetchAggregateMaliciousRequests() {
    HttpPost post =
        new HttpPost(String.format("%s/api/dashboard/fetchAggregateMaliciousRequests", this.getBackendUrl()));
    post.addHeader("Authorization", "Bearer " + this.getApiToken());
    post.addHeader("Content-Type", "application/json");

    Map<String, Object> body =
        new HashMap<String, Object>() {
          {
            put("ref_id", refId);
          }
        };
    String msg = objectMapper.valueToTree(body).toString();

    StringEntity requestEntity = new StringEntity(msg, ContentType.APPLICATION_JSON);
    post.setEntity(requestEntity);

    try (CloseableHttpResponse resp = this.httpClient.execute(post)) {
      String responseBody = EntityUtils.toString(resp.getEntity());

      ProtoMessageUtils.<FetchMaliciousEventsResponse>toProtoMessage(
        FetchMaliciousEventsResponse.class, responseBody)
          .ifPresent(
              m -> {
                this.maliciousPayloadsResponses =
                    m.getMaliciousPayloadsResponseList().stream()
                        .map(
                            smr ->
                                new MaliciousPayloadsResponse(
                                    smr.getOrig(),
                                    smr.getTs()))
                        .collect(Collectors.toList());
              });
    } catch (Exception e) {
      e.printStackTrace();
      return ERROR.toUpperCase();
    }

    return SUCCESS.toUpperCase();
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

  public long getTotal() {
    return total;
  }

  public void setTotal(long total) {
    this.total = total;
  }

  public List<DashboardThreatActor> getActors() {
    return actors;
  }

  public void setActors(List<DashboardThreatActor> actor) {
    this.actors = actor;
  }

  public List<ThreatActorPerCountry> getActorsCountPerCountry() {
    return actorsCountPerCountry;
  }

  public void setActorsCountPerCountry(List<ThreatActorPerCountry> actorsCountPerCountry) {
    this.actorsCountPerCountry = actorsCountPerCountry;
  }

  public List<MaliciousPayloadsResponse> getMaliciousPayloadsResponses() {
    return maliciousPayloadsResponses;
  }

  public void setMaliciousPayloadsResponses(List<MaliciousPayloadsResponse> maliciousPayloadsResponses) {
    this.maliciousPayloadsResponses = maliciousPayloadsResponses;
  }

  public String getRefId() {
    return refId;
  }

  public void setRefId(String refId) {
    this.refId = refId;
  }
}

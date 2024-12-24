package com.akto.action.threat_detection;

import com.akto.action.UserAction;
import com.akto.dto.type.URLMethods;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListThreatApiResponse;
import com.akto.proto.utils.ProtoMessageUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public class ThreatApiAction extends UserAction {

  List<DashboardThreatApi> apis;
  int skip;
  static final int LIMIT = 50;
  long total;
  Map<String, Integer> sort;
  int startTimestamp, endTimestamp;

  private final CloseableHttpClient httpClient;
  private final String backendUrl;
  private final String backendToken;

  private final ObjectMapper objectMapper = new ObjectMapper();

  public ThreatApiAction() {
    super();
    this.httpClient = HttpClients.createDefault();
    this.backendUrl = System.getenv("THREAT_DETECTION_BACKEND_URL");
    this.backendToken = System.getenv("THREAT_DETECTION_BACKEND_TOKEN");
  }

  public String fetchThreatApis() {
    HttpPost post = new HttpPost(String.format("%s/api/dashboard/list_threat_apis", backendUrl));
    post.addHeader("Authorization", "Bearer " + backendToken);
    post.addHeader("Content-Type", "application/json");

    Map<String, Object> body =
        new HashMap<String, Object>() {
          {
            put("skip", skip);
            put("limit", LIMIT);
          }
        };
    String msg = objectMapper.valueToTree(body).toString();

    System.out.println("Request body for list threat actors" + msg);

    StringEntity requestEntity = new StringEntity(msg, ContentType.APPLICATION_JSON);
    post.setEntity(requestEntity);

    try (CloseableHttpResponse resp = this.httpClient.execute(post)) {
      String responseBody = EntityUtils.toString(resp.getEntity());

      System.out.println(responseBody);

      ProtoMessageUtils.<ListThreatApiResponse>toProtoMessage(
              ListThreatApiResponse.class, responseBody)
          .ifPresent(
              m -> {
                this.apis =
                    m.getApisList().stream()
                        .map(
                            smr ->
                                new DashboardThreatApi(
                                    smr.getEndpoint(),
                                    URLMethods.Method.fromString(smr.getMethod()),
                                    smr.getActorsCount(),
                                    smr.getRequestsCount(),
                                    smr.getDiscoveredAt()))
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

  public List<DashboardThreatApi> getApis() {
    return apis;
  }

  public void setApis(List<DashboardThreatApi> apis) {
    this.apis = apis;
  }

  public Map<String, Integer> getSort() {
    return sort;
  }

  public void setSort(Map<String, Integer> sort) {
    this.sort = sort;
  }
}

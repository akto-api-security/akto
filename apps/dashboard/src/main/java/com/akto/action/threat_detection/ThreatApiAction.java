package com.akto.action.threat_detection;

import com.akto.dto.type.URLMethods;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListThreatApiResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatCategoryWiseCountResponse;
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

public class ThreatApiAction extends AbstractThreatDetectionAction {

  List<DashboardThreatApi> apis;
  List<ThreatCategoryCount> categoryCounts;
  int skip;
  static final int LIMIT = 50;
  long total;
  Map<String, Integer> sort;
  int startTimestamp, endTimestamp;

  private final CloseableHttpClient httpClient;

  private final ObjectMapper objectMapper = new ObjectMapper();

  public ThreatApiAction() {
    super();
    this.httpClient = HttpClients.createDefault();
  }

  public String fetchThreatCategoryCount() {
    HttpGet get =
        new HttpGet(
            String.format("%s/api/dashboard/get_subcategory_wise_count", this.getBackendUrl()));
    get.addHeader("Authorization", "Bearer " + this.getApiToken());
    get.addHeader("Content-Type", "application/json");

    try (CloseableHttpResponse resp = this.httpClient.execute(get)) {
      String responseBody = EntityUtils.toString(resp.getEntity());

      ProtoMessageUtils.<ThreatCategoryWiseCountResponse>toProtoMessage(
              ThreatCategoryWiseCountResponse.class, responseBody)
          .ifPresent(
              m -> {
                this.categoryCounts =
                    m.getCategoryWiseCountsList().stream()
                        .map(
                            smr ->
                                new ThreatCategoryCount(
                                    smr.getCategory(), smr.getSubCategory(), smr.getCount()))
                        .collect(Collectors.toList());
              });
    } catch (Exception e) {
      e.printStackTrace();
      return ERROR.toUpperCase();
    }

    return SUCCESS.toUpperCase();
  }

  public String fetchThreatApis() {
    HttpPost post =
        new HttpPost(String.format("%s/api/dashboard/list_threat_apis", this.getBackendUrl()));
    post.addHeader("Authorization", "Bearer " + this.getApiToken());
    post.addHeader("Content-Type", "application/json");

    Map<String, Object> body =
        new HashMap<String, Object>() {
          {
            put("skip", skip);
            put("limit", LIMIT);
          }
        };
    String msg = objectMapper.valueToTree(body).toString();

    StringEntity requestEntity = new StringEntity(msg, ContentType.APPLICATION_JSON);
    post.setEntity(requestEntity);

    try (CloseableHttpResponse resp = this.httpClient.execute(post)) {
      String responseBody = EntityUtils.toString(resp.getEntity());

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

  public List<ThreatCategoryCount> getCategoryCounts() {
    return categoryCounts;
  }

  public void setCategoryCounts(List<ThreatCategoryCount> categoryCounts) {
    this.categoryCounts = categoryCounts;
  }
}

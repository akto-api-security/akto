package com.akto.action.threat_detection;

import com.akto.ProtoMessageUtils;
import com.akto.dao.monitoring.FilterYamlTemplateDao;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.dto.test_editor.Category;
import com.akto.dto.test_editor.Info;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.type.URLMethods;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.DailyActorsCountResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListThreatApiResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatActivityTimelineResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatCategoryWiseCountResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatSeverityWiseCountResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.DailyActorsCountResponse.ActorsCount;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchTopNDataResponse;
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
import org.bson.Document;
import lombok.Getter;

import static com.akto.action.threat_detection.utils.ThreatsUtils.getTemplates;

public class ThreatApiAction extends AbstractThreatDetectionAction {

  List<DashboardThreatApi> apis;
  List<ThreatCategoryCount> categoryCounts;
  List<DailyActorsCount> actorsCounts;
  List<ThreatActivityTimeline> threatActivityTimelines;
  int skip;
  static final int LIMIT = 50;
  long total;
  Map<String, Integer> sort;
  List<String> latestAttack;
  int startTs;
  int endTs;

  @Getter int totalAnalysed;
  @Getter int totalAttacks;
  @Getter int totalCriticalActors;
  @Getter int totalActiveStatus;
  @Getter int totalIgnoredStatus;
  @Getter int totalUnderReviewStatus;

  @Getter List<TopApiData> topApis;
  @Getter List<TopHostData> topHosts;

  // TODO: remove this, use API Executor.
  private final CloseableHttpClient httpClient;

  private final ObjectMapper objectMapper = new ObjectMapper();

  public ThreatApiAction() {
    super();
    this.httpClient = HttpClients.createDefault();
  }

  private Map<String, String> getCategoryDisplayNames() {
    Map<String, String> categoryDisplayNames = new HashMap<>();
    List<YamlTemplate> templates = FilterYamlTemplateDao.instance.findAll(new Document());

    Map<String, FilterConfig> filterConfigs = FilterYamlTemplateDao.fetchFilterConfig(false, templates, false);
    for (Map.Entry<String, FilterConfig> entry : filterConfigs.entrySet()) {
      Info info = entry.getValue().getInfo();
      if (info == null) {
        continue;
      }

      Category category = info.getCategory();
      if (category == null) {
        continue;
      }

      String name = category.getName();
      if (name == null) {
        continue;
      }

      String displayName = category.getDisplayName();
      if (displayName == null) {
        continue;
      }

      categoryDisplayNames.put(category.getName(), displayName);
    }

    return categoryDisplayNames;
  }

  public String fetchThreatCategoryCount() {
    HttpPost post = new HttpPost(
        String.format("%s/api/dashboard/get_subcategory_wise_count", this.getBackendUrl()));
    post.addHeader("Authorization", "Bearer " + this.getApiToken());
    post.addHeader("Content-Type", "application/json");

    Map<String, Object> body = new HashMap<String, Object>() {
      {
        put("start_ts", startTs);
        put("end_ts", endTs);
        put("latestAttack", getTemplates(latestAttack));
      }
    };
    String msg = objectMapper.valueToTree(body).toString();
    StringEntity requestEntity = new StringEntity(msg, ContentType.APPLICATION_JSON);
    post.setEntity(requestEntity);

    try (CloseableHttpResponse resp = this.httpClient.execute(post)) {
      String responseBody = EntityUtils.toString(resp.getEntity());

      ProtoMessageUtils.<ThreatCategoryWiseCountResponse>toProtoMessage(
          ThreatCategoryWiseCountResponse.class, responseBody)
          .ifPresent(
              m -> {
                Map<String, String> categoryDisplayNames = getCategoryDisplayNames();
                this.categoryCounts = m.getCategoryWiseCountsList().stream()
                    .map(
                        smr -> {
                          String displayName = categoryDisplayNames.containsKey(smr.getCategory())
                              ? categoryDisplayNames.get(smr.getCategory())
                              : smr.getCategory();
                          return new ThreatCategoryCount(displayName, smr.getSubCategory(), smr.getCount());
                        })
                    .collect(Collectors.toList());
              });
    } catch (Exception e) {
      e.printStackTrace();
      return ERROR.toUpperCase();
    }

    return SUCCESS.toUpperCase();
  }

  public String fetchCountBySeverity() {
    HttpPost post = new HttpPost(
        String.format("%s/api/dashboard/get_severity_wise_count", this.getBackendUrl()));

    post.addHeader("Authorization", "Bearer " + this.getApiToken());
    post.addHeader("Content-Type", "application/json");

    Map<String, Object> body = new HashMap<String, Object>() {
      {
        put("start_ts", startTs);
        put("end_ts", endTs);
        put("latestAttack", getTemplates(latestAttack));
      }
    };
    String msg = objectMapper.valueToTree(body).toString();
    StringEntity requestEntity = new StringEntity(msg, ContentType.APPLICATION_JSON);
    post.setEntity(requestEntity);

    try (CloseableHttpResponse resp = this.httpClient.execute(post)) {
      String responseBody = EntityUtils.toString(resp.getEntity());

      ProtoMessageUtils.<ThreatSeverityWiseCountResponse>toProtoMessage(
        ThreatSeverityWiseCountResponse.class, responseBody)
          .ifPresent(
              m -> {
                this.categoryCounts = m.getCategoryWiseCountsList().stream()
                    .map(
                        smr -> {
                          return new ThreatCategoryCount("", smr.getSeverity(), smr.getCount());
                        })
                    .collect(Collectors.toList());
              });
    } catch (Exception e) {
      e.printStackTrace();
      return ERROR.toUpperCase();
    }

    return SUCCESS.toUpperCase();
  }

  public String getDailyThreatActorsCount() {
    HttpPost post = new HttpPost(String.format("%s/api/dashboard/get_daily_actor_count", this.getBackendUrl()));
    post.addHeader("Authorization", "Bearer " + this.getApiToken());
    post.addHeader("Content-Type", "application/json");

    List<String> templatesContext = getTemplates(this.latestAttack);

    Map<String, Object> body = new HashMap<String, Object>() {
      {
        put("start_ts", startTs);
        put("end_ts", endTs);
        put("latestAttack", templatesContext);
      }
    };
    String msg = objectMapper.valueToTree(body).toString();

    StringEntity requestEntity = new StringEntity(msg, ContentType.APPLICATION_JSON);
    post.setEntity(requestEntity);

    try (CloseableHttpResponse resp = this.httpClient.execute(post)) {
      String responseBody = EntityUtils.toString(resp.getEntity());

      ProtoMessageUtils.<DailyActorsCountResponse>toProtoMessage(
        DailyActorsCountResponse.class, responseBody)
          .ifPresent(
              m -> {
                this.actorsCounts = m.getActorsCountsList().stream()
                    .map(
                        smr -> {
                          return new DailyActorsCount(smr.getTs(), smr.getTotalActors(), smr.getCriticalActors());
                        })
                    .collect(Collectors.toList());
                
                // Set summary counts
                this.totalAnalysed = m.getTotalAnalysed();
                this.totalAttacks = m.getTotalAttacks();
                this.totalCriticalActors = m.getCriticalActorsCount();
                this.totalActiveStatus = m.getTotalActive();
                this.totalIgnoredStatus = m.getTotalIgnored();
                this.totalUnderReviewStatus = m.getTotalUnderReview();
              });
    } catch (Exception e) {
      e.printStackTrace();
      return ERROR.toUpperCase();
    }

    return SUCCESS.toUpperCase();
  }

  public String getThreatActivityTimeline() {
    HttpPost post = new HttpPost(String.format("%s/api/dashboard/get_threat_activity_timeline", this.getBackendUrl()));
    post.addHeader("Authorization", "Bearer " + this.getApiToken());
    post.addHeader("Content-Type", "application/json");

    List<String> templatesContext = getTemplates(this.latestAttack);

    Map<String, Object> body = new HashMap<String, Object>() {
      {
        put("start_ts", startTs);
        put("end_ts", endTs);
        put("latestAttack", templatesContext);
      }
    };
    String msg = objectMapper.valueToTree(body).toString();

    StringEntity requestEntity = new StringEntity(msg, ContentType.APPLICATION_JSON);
    post.setEntity(requestEntity);

    try (CloseableHttpResponse resp = this.httpClient.execute(post)) {
      String responseBody = EntityUtils.toString(resp.getEntity());

      ProtoMessageUtils.<ThreatActivityTimelineResponse>toProtoMessage(
        ThreatActivityTimelineResponse.class, responseBody)
          .ifPresent(
              m -> {
                this.threatActivityTimelines = m.getThreatActivityTimelineList().stream()
                    .map(
                        smr -> {
                          //System.out.print(smr);
                          
                          return new ThreatActivityTimeline(smr.getTs(),
                          smr.getSubCategoryWiseDataList().stream()
                          .map(subData -> new SubCategoryWiseData(subData.getSubCategory(), subData.getActivityCount()))
                          .collect(Collectors.toList()));
                        })
                    .collect(Collectors.toList());
              });
    } catch (Exception e) {
      e.printStackTrace();
      return ERROR.toUpperCase();
    }

    return SUCCESS.toUpperCase();
  }

  public String fetchThreatApis() {
    HttpPost post = new HttpPost(String.format("%s/api/dashboard/list_threat_apis", this.getBackendUrl()));
    post.addHeader("Authorization", "Bearer " + this.getApiToken());
    post.addHeader("Content-Type", "application/json");

    Map<String, Object> filters = new HashMap<>();

    List<String> templates = getTemplates(latestAttack);
    filters.put("latestAttack", templates);

    Map<String, Object> body = new HashMap<String, Object>() {
      {
        put("skip", skip);
        put("limit", LIMIT);
        put("sort", sort);
        if(!filters.isEmpty()) {
          put("filter", filters);
        }
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
                this.apis = m.getApisList().stream()
                    .map(
                        smr -> new DashboardThreatApi(
                            smr.getEndpoint(),
                            URLMethods.Method.fromString(smr.getMethod()),
                            smr.getActorsCount(),
                            smr.getRequestsCount(),
                            smr.getDiscoveredAt(),
                            smr.getHost()))
                    .collect(Collectors.toList());

                this.total = m.getTotal();
              });
    } catch (Exception e) {
      e.printStackTrace();
      return ERROR.toUpperCase();
    }

    return SUCCESS.toUpperCase();
  }

  public String fetchThreatTopNData() {
    HttpPost post = new HttpPost(String.format("%s/api/dashboard/get_top_n_data", this.getBackendUrl()));
    post.addHeader("Authorization", "Bearer " + this.getApiToken());
    post.addHeader("Content-Type", "application/json");

    List<String> templatesContext = getTemplates(this.latestAttack);

    Map<String, Object> body = new HashMap<String, Object>() {
      {
        put("start_ts", startTs);
        put("end_ts", endTs);
        put("latestAttack", templatesContext);
        put("limit", 5);
      }
    };
    String msg = objectMapper.valueToTree(body).toString();

    StringEntity requestEntity = new StringEntity(msg, ContentType.APPLICATION_JSON);
    post.setEntity(requestEntity);

    try (CloseableHttpResponse resp = this.httpClient.execute(post)) {
      String responseBody = EntityUtils.toString(resp.getEntity());

      ProtoMessageUtils.<FetchTopNDataResponse>toProtoMessage(
        FetchTopNDataResponse.class, responseBody)
          .ifPresent(
              m -> {
                this.topApis = m.getTopApisList().stream()
                    .map(
                        smr -> new TopApiData(
                            smr.getEndpoint(),
                            smr.getMethod(),
                            smr.getAttacks(),
                            smr.getSeverity()))
                    .collect(Collectors.toList());
                this.topHosts = m.getTopHostsList().stream()
                    .map(smr -> new TopHostData(
                        smr.getHost(),
                        smr.getAttacks()
                    )).collect(Collectors.toList());
              });
    } catch (Exception e) {
      e.printStackTrace();
      return ERROR.toUpperCase();
    }

    return SUCCESS.toUpperCase();
  }

  // Explicit getters/setters required by JSON serialization and frontend usage
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

  public List<DailyActorsCount> getActorsCounts() {
    return actorsCounts;
  }

  public void setActorsCounts(List<DailyActorsCount> actorsCounts) {
    this.actorsCounts = actorsCounts;
  }

  public int getStartTs() {
    return startTs;
  }

  public int getSkip() {
    return skip;
  }

  public void setSkip(int skip) {
    this.skip = skip;
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

  public List<ThreatActivityTimeline> getThreatActivityTimelines() {
    return threatActivityTimelines;
  }

  public void setStartTs(int startTs) {
    this.startTs = startTs;
  }

  public void setEndTs(int endTs) {
    this.endTs = endTs;
  }

  public void setLatestAttack(List<String> latestAttack) {
    this.latestAttack = latestAttack;
  }
  
}


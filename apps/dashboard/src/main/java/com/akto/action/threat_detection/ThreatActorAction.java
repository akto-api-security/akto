package com.akto.action.threat_detection;

import com.akto.dao.ConfigsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Config;
import com.akto.dto.Config.AwsWafConfig;
import com.akto.dto.type.URLMethods;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchMaliciousEventsResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListThreatActorResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatActorByCountryResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatActorFilterResponse;
import com.akto.proto.utils.ProtoMessageUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.model.Filters;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.wafv2.Wafv2Client;
import software.amazon.awssdk.services.wafv2.model.GetIpSetRequest;
import software.amazon.awssdk.services.wafv2.model.GetIpSetResponse;
import software.amazon.awssdk.services.wafv2.model.UpdateIpSetRequest;
import software.amazon.awssdk.services.wafv2.model.Wafv2Exception;

import java.util.ArrayList;
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
import org.bson.conversions.Bson;

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
  List<String> latestAttack;
  List<String> country;
  List<String> actorId;
  int startTs;
  int endTs;
  String splunkUrl;
  String splunkToken;
  String actorIp;
  String status;
  String eventType;

  private final CloseableHttpClient httpClient;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private static final LoggerMaker loggerMaker = new LoggerMaker(ThreatActorAction.class, LogDb.DASHBOARD);
  private static final String SCOPE = "REGIONAL";

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

  public String fetchThreatActorFilters() {
    HttpGet get = new HttpGet(String.format("%s/api/dashboard/fetch_filters_for_threat_actors", this.getBackendUrl()));
    get.addHeader("Authorization", "Bearer " + this.getApiToken());
    get.addHeader("Content-Type", "application/json");

    try (CloseableHttpResponse resp = this.httpClient.execute(get)) {
      String responseBody = EntityUtils.toString(resp.getEntity());

      ProtoMessageUtils.<ThreatActorFilterResponse>toProtoMessage(
          ThreatActorFilterResponse.class, responseBody)
          .ifPresent(
              msg -> {
                this.country = msg.getCountriesList();
                this.latestAttack = msg.getSubCategoriesList();
                this.actorId = msg.getActorIdList();
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
    Map<String, Object> filter = new HashMap<>();

    if(this.latestAttack != null && !this.latestAttack.isEmpty()){
      filter.put("latestAttack", this.latestAttack);
    }
    if(this.country != null && !this.country.isEmpty()){
      filter.put("country", this.country);
    }
    if(this.actorId != null && !this.actorId.isEmpty()){
      filter.put("actors", this.actorId);
    }
    Map<String, Object> body =
        new HashMap<String, Object>() {
          {
            put("skip", skip);
            put("limit", LIMIT);
            put("sort", sort);
            put("filter", filter);
            put("start_ts", startTs);
            put("end_ts", endTs);
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
                                    .map(subData -> new ActivityData(subData.getUrl(), subData.getSeverity(), subData.getSubCategory(), subData.getDetectedAt(), subData.getMethod()))
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
            put("event_type", eventType);
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

    public static Wafv2Client getAwsWafClient(String accessKey, String secretKey, String region) {
      return Wafv2Client.builder()
          .region(Region.of(region))
          .credentialsProvider(StaticCredentialsProvider.create(
              AwsBasicCredentials.create(accessKey, secretKey)
          ))
          .build();
    }

    public static GetIpSetResponse getIpSet(Wafv2Client wafv2Client, String ruleSetName, String ruleSetId) {
        GetIpSetRequest getRequest = GetIpSetRequest.builder()
                    .name(ruleSetName)
                    .scope(SCOPE)
                    .id(ruleSetId)
                    .build();

        GetIpSetResponse getResponse = null;
        try {
            getResponse = wafv2Client.getIPSet(getRequest);
        } catch (Exception e) {
            e.printStackTrace();
            loggerMaker.errorAndAddToDb("unable to fetch ipSet " + e.getStackTrace());
        }
        return getResponse;
    }

    public String modifyThreatActorStatus() {
        Wafv2Client wafClient = null;
        int accId = Context.accountId.get();
        Bson filters = Filters.and(
            Filters.eq("_id", accId+ "_" + "AWS_WAF"),
            Filters.eq("accountId", accId)
        );
        Config.AwsWafConfig awsWafConfig = (Config.AwsWafConfig) ConfigsDao.instance.findOne(filters);
        try {
            wafClient = getAwsWafClient(awsWafConfig.getAwsAccessKey(), awsWafConfig.getAwsSecretKey(), awsWafConfig.getRegion());
            //ListWebAcLsResponse webAclsResponse = wafClient.listWebACLs(ListWebAcLsRequest.builder().scope(SCOPE).build());
            loggerMaker.infoAndAddToDb("init aws client, for threat actor block");
        } catch (Exception e) {
            e.printStackTrace();
            loggerMaker.errorAndAddToDb("error initialising aws client " + e.getMessage());
        }

        try {
            GetIpSetRequest getRequest = GetIpSetRequest.builder()
                    .name(awsWafConfig.getRuleSetName())
                    .scope(SCOPE)
                    .id(awsWafConfig.getRuleSetId())
                    .build();

            GetIpSetResponse getResponse = null;
            try {
                getResponse = wafClient.getIPSet(getRequest);
            } catch (Exception e) {
                e.printStackTrace();
                System.out.print("getResponse error " + e.getMessage());
                return ERROR.toUpperCase();
            }
            
            List<String> ipAddresses = new ArrayList<>(getResponse.ipSet().addresses());

            String ipWithCidr = actorIp + "/32";
            boolean resp;
            if (status.equalsIgnoreCase("blocked")) {
              resp = blockActorIp(ipAddresses, ipWithCidr, wafClient, awsWafConfig, getResponse);
            } else {
              resp = unblockActorIp(ipAddresses, ipWithCidr, wafClient, awsWafConfig, getResponse);
            }

            if (resp) {

              HttpPost post =
                new HttpPost(String.format("%s/api/dashboard/modifyThreatActorStatus", this.getBackendUrl()));
                  post.addHeader("Authorization", "Bearer " + this.getApiToken());
                  post.addHeader("Content-Type", "application/json");

                  Map<String, Object> body =
                      new HashMap<String, Object>() {
                        {
                          put("updated_ts", Context.now());
                          put("ip", ipWithCidr);
                          put("status", status);
                        }
                      };
                  String msg = objectMapper.valueToTree(body).toString();

                  StringEntity requestEntity = new StringEntity(msg, ContentType.APPLICATION_JSON);
                  post.setEntity(requestEntity);

                  try (CloseableHttpResponse response = this.httpClient.execute(post)) {
                      loggerMaker.infoAndAddToDb("updated threat actor status");
                  } catch (Exception e) {
                    e.printStackTrace();
                    return ERROR.toUpperCase();
                  }


              return SUCCESS.toUpperCase();
            }
            return SUCCESS.toUpperCase();
            
        } catch (Wafv2Exception e) {
            loggerMaker.infoAndAddToDb("Error modiftying threat actor status: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    public boolean blockActorIp(List<String> ipAddresses, String ipWithCidr, Wafv2Client wafClient, AwsWafConfig awsWafConfig, GetIpSetResponse getResponse) {
        if (!ipAddresses.contains(ipWithCidr)) {
            ipAddresses.add(ipWithCidr);

            UpdateIpSetRequest updateRequest = UpdateIpSetRequest.builder()
                    .name(awsWafConfig.getRuleSetName())
                    .scope(SCOPE)
                    .id(awsWafConfig.getRuleSetId())
                    .addresses(ipAddresses) 
                    .lockToken(getResponse.lockToken())
                    .build();

            wafClient.updateIPSet(updateRequest);
            loggerMaker.infoAndAddToDb("Blocked Threat Actor: " + actorIp);
            return true;
        } else {
            loggerMaker.infoAndAddToDb("Threat Actor " + actorIp + " is already blocked.");
            return false;
        }
    }

    public boolean unblockActorIp(List<String> ipAddresses, String ipWithCidr, Wafv2Client wafClient, AwsWafConfig awsWafConfig, GetIpSetResponse getResponse) {
      if (ipAddresses.contains(ipWithCidr)) {
          ipAddresses.remove(ipWithCidr);

          UpdateIpSetRequest updateRequest = UpdateIpSetRequest.builder()
                  .name(awsWafConfig.getRuleSetName())
                  .scope(SCOPE)
                  .id(awsWafConfig.getRuleSetId())
                  .addresses(ipAddresses) 
                  .lockToken(getResponse.lockToken())
                  .build();

          wafClient.updateIPSet(updateRequest);
          loggerMaker.infoAndAddToDb("Unblocked Threat Actor: " + actorIp);
          return true;
      } else {
          loggerMaker.infoAndAddToDb("Threat Actor " + actorIp + " is currently active");
          return false;
      }
  }

    public String sendIntegrationDataToThreatBackend() {
      HttpPost post =
          new HttpPost(String.format("%s/api/dashboard/addSplunkIntegration", this.getBackendUrl()));
      post.addHeader("Authorization", "Bearer " + this.getApiToken());
      post.addHeader("Content-Type", "application/json");
  
      Map<String, Object> body =
          new HashMap<String, Object>() {
            {
              put("splunk_url", splunkUrl);
              put("splunk_token", splunkToken);
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

  public List<String> getLatestAttack() {
    return latestAttack;
  }

  public void setLatestAttack(List<String> latestAttack) {
    this.latestAttack = latestAttack;
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

  public List<String> getCountry() {
    return country;
  }
  
  public void setCountry(List<String> country) {
    this.country = country;
  }

  public int getStartTs() {
    return startTs;
  }

  public void setStartTs(int startTs) {
    this.startTs = startTs;
  }

  public int getEndTs() {
    return endTs;
  }

  public void setEndTs(int endTs) {
    this.endTs = endTs;
  }

  public String getSplunkUrl() {
    return splunkUrl;
  }

  public void setSplunkUrl(String splunkUrl) {
    this.splunkUrl = splunkUrl;
  }

  public String getSplunkToken() {
    return splunkToken;
  }

  public void setSplunkToken(String splunkToken) {
    this.splunkToken = splunkToken;
  }

  public String getActorIp() {
    return actorIp;
  }

  public void setActorIp(String actorIp) {
    this.actorIp = actorIp;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }
  
  public List<String> getActorId() {
    return actorId;
  }

  public void setActorId(List<String> actorId) {
    this.actorId = actorId;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

}

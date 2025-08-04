package com.akto.action.threat_detection;

import com.akto.ProtoMessageUtils;
import com.akto.dao.ConfigsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Config;
import com.akto.dto.Config.AwsWafConfig;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.type.URLMethods;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchMaliciousEventsResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListThreatActorResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatActorByCountryResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatActorFilterResponse;
import com.akto.testing.ApiExecutor;
import com.akto.util.Constants;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.wafv2.Wafv2Client;
import software.amazon.awssdk.services.wafv2.model.GetIpSetRequest;
import software.amazon.awssdk.services.wafv2.model.GetIpSetResponse;
import software.amazon.awssdk.services.wafv2.model.UpdateIpSetRequest;
import software.amazon.awssdk.services.wafv2.model.Wafv2Exception;

import java.util.*;
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
  String actor;
  String filterId;


  public static final String CLOUDFLARE_WAF_BASE_URL = "https://api.cloudflare.com/client/v4";

  private final ObjectMapper objectMapper = new ObjectMapper();

  private static final LoggerMaker loggerMaker = new LoggerMaker(ThreatActorAction.class, LogDb.DASHBOARD);
  private static final String SCOPE = "REGIONAL";

  public ThreatActorAction() {
    super();
  }

  public String getActorsCountPerCounty() {
    String requestUrl = String.format("%s/api/dashboard/get_actors_count_per_country", this.getBackendUrl());
    Map<String, List<String>> headers = new HashMap<>();
    headers.put("Authorization", Collections.singletonList("Bearer " + this.getApiToken()));
    headers.put("Content-Type", Collections.singletonList("application/json"));

    if(startTs == 0 || endTs == 0) {
      startTs = Context.now() - 1 * 24 * 60 * 60; // default to last 1 day
      endTs = Context.now();
    }

    Map<String, Object> body = new HashMap<String, Object>() {
      {
        put("start_ts", startTs);
        put("end_ts", endTs);
      }
    };
    String msg = objectMapper.valueToTree(body).toString();

    OriginalHttpRequest request = new OriginalHttpRequest(requestUrl, "", "POST", msg, headers, "application/json");
    try {
      OriginalHttpResponse resp = ApiExecutor.sendRequest(request, true, null, false, null);
      String responseBody = resp.getBody();

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
    String requestUrl = String.format("%s/api/dashboard/fetch_filters_for_threat_actors", this.getBackendUrl());
    Map<String, List<String>> headers = new HashMap<>();
    headers.put("Authorization", Collections.singletonList("Bearer " + this.getApiToken()));
    headers.put("Content-Type", Collections.singletonList("application/json"));

    OriginalHttpRequest request = new OriginalHttpRequest(requestUrl, "", "GET", "", headers, "application/json");

    try {
      OriginalHttpResponse resp = ApiExecutor.sendRequest(request, true, null, false, null);  
      String responseBody = resp.getBody();

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
    String requestUrl = String.format("%s/api/dashboard/list_threat_actors", this.getBackendUrl());
    Map<String, List<String>> headers = new HashMap<>();
    headers.put("Authorization", Collections.singletonList("Bearer " + this.getApiToken()));
    headers.put("Content-Type", Collections.singletonList("application/json"));
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

    OriginalHttpRequest request = new OriginalHttpRequest(requestUrl, "", "POST", msg, headers, "application/json");

    try {
      OriginalHttpResponse resp = ApiExecutor.sendRequest(request, true, null, false, null);
      String responseBody = resp.getBody();

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
    String requestUrl = String.format("%s/api/dashboard/fetchAggregateMaliciousRequests", this.getBackendUrl());
    Map<String, List<String>> headers = new HashMap<>();
    headers.put("Authorization", Collections.singletonList("Bearer " + this.getApiToken()));
    headers.put("Content-Type", Collections.singletonList("application/json"));

    Map<String, Object> body =
        new HashMap<String, Object>() {
          {
            put("ref_id", refId);
            put("event_type", eventType);
            put("actor", actor);
            put("filterId", filterId);
          }
        };
    String msg = objectMapper.valueToTree(body).toString();
    OriginalHttpRequest request = new OriginalHttpRequest(requestUrl, "", "POST", msg, headers, "application/json");

    try {
      OriginalHttpResponse resp = ApiExecutor.sendRequest(request, true, null, false, null);
      String responseBody = resp.getBody();

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
                                    smr.getMetadata(),
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

    public String modifyThreatActorStatusCloudflare() {
        int accId = Context.accountId.get();
        Bson filters = Filters.and(
            Filters.eq(Constants.ID, accId + "_" + Config.ConfigType.CLOUDFLARE_WAF),
            Filters.eq(Config.CloudflareWafConfig.ACCOUNT_ID, accId)
        );

        Config.CloudflareWafConfig cloudflareWafConfig = (Config.CloudflareWafConfig) ConfigsDao.instance.findOne(filters);

        boolean result;
        if(status.equalsIgnoreCase("blocked")) {
            result = blockIPInCloudflareWaf(cloudflareWafConfig);
        } else {
            result = unblockIPInCloudflareWaf(cloudflareWafConfig);
        }

        if(result) {
            boolean backendRes = sendModifyThreatActorStatusToBackend(actorIp);

            if (!backendRes) {
                return ERROR.toUpperCase();
            } else {
                return SUCCESS.toUpperCase();
            }
        }

        return ERROR.toUpperCase();
    }

    public boolean blockIPInCloudflareWaf(Config.CloudflareWafConfig cloudflareWafConfig) {
        try {
            Map<String, List<String>> headers = new HashMap<>();
            headers.put("Content-Type", Collections.singletonList("application/json"));
            headers.put("X-Auth-Key", Collections.singletonList(cloudflareWafConfig.getApiKey()));
            headers.put("X-Auth-Email", Collections.singletonList(cloudflareWafConfig.getEmail()));

            String url = CLOUDFLARE_WAF_BASE_URL + "/" + cloudflareWafConfig.getIntegrationType() + "/" + cloudflareWafConfig.getAccountOrZoneId() + "/firewall/access_rules/rules";

            BasicDBObject reqPayload = new BasicDBObject();
            reqPayload.put("mode", "block");
            BasicDBObject configuration = new BasicDBObject();
            configuration.put("target", "ip");
            configuration.put("value", actorIp);
            reqPayload.put("configuration", configuration);

            OriginalHttpRequest request = new OriginalHttpRequest(url, "", "POST", reqPayload.toString(), headers, "");

            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());
            String responsePayload = response.getBody();
            if (response.getStatusCode() > 201 || responsePayload == null) {
                addActionError("Unable to block IP address. Try again later.");
                return false;
            }

        } catch (Exception e) {
            addActionError("Unable to block IP address. Try again later.");
            loggerMaker.errorAndAddToDb("Error modifying threat actor status: " + e.getMessage());
            return false;
        }

        return true;
    }

    public boolean unblockIPInCloudflareWaf(Config.CloudflareWafConfig cloudflareWafConfig) {
        try {
            String ruleId = getCloudFlareIPAccessRuleByActorIP(actorIp, cloudflareWafConfig);

            if(ruleId == null || ruleId.isEmpty()) {
                addActionError("The IP is already unblocked or does not exist.");
                return false;
            }

            Map<String, List<String>> headers = new HashMap<>();
            headers.put("X-Auth-Key", Collections.singletonList(cloudflareWafConfig.getApiKey()));
            headers.put("X-Auth-Email", Collections.singletonList(cloudflareWafConfig.getEmail()));

            String url = CLOUDFLARE_WAF_BASE_URL + "/" + cloudflareWafConfig.getIntegrationType() + "/" + cloudflareWafConfig.getAccountOrZoneId() + "/firewall/access_rules/rules/" + ruleId;

            OriginalHttpRequest request = new OriginalHttpRequest(url, "", "DELETE", "", headers, "");

            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());
            String responsePayload = response.getBody();
            if (response.getStatusCode() > 201 || responsePayload == null) {
                return false;
            }

            BasicDBObject responseObj = BasicDBObject.parse(responsePayload);
            BasicDBObject result = (BasicDBObject) responseObj.get("result");
            if(result == null) {
                return false;
            }

        } catch (Exception e) {
            addActionError("Unable to unblock IP address. Try again later.");
            loggerMaker.debugAndAddToDb("Error modifying threat actor status: " + e.getMessage());
            return false;
        }

        return true;
    }

    public static String getCloudFlareIPAccessRuleByActorIP(String actorIp, Config.CloudflareWafConfig cloudflareWafConfig) {
        try {
            String url = CLOUDFLARE_WAF_BASE_URL + "/" + cloudflareWafConfig.getIntegrationType() + "/" + cloudflareWafConfig.getAccountOrZoneId() + "/firewall/access_rules/rules?configuration.target=ip&configuration.value=" + actorIp;

            Map<String, List<String>> headers = new HashMap<>();
            headers.put("X-Auth-Key", Collections.singletonList(cloudflareWafConfig.getApiKey()));
            headers.put("X-Auth-Email", Collections.singletonList(cloudflareWafConfig.getEmail()));

            OriginalHttpRequest request = new OriginalHttpRequest(url, "", "GET", "", headers, "");
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());
            String responsePayload = response.getBody();
            if (response.getStatusCode() > 201 || responsePayload == null) {
                return null;
            }

            BasicDBObject respPayloadObj = BasicDBObject.parse(responsePayload);
            BasicDBList result = (BasicDBList) respPayloadObj.get("result");
            BasicDBObject resultObj = (BasicDBObject) result.get(0);
            return resultObj.getString("id") == null ? "" : resultObj.getString("id");
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while fetching ip access rule id: " + e.getMessage());
            return null;
        }
    }

    public String modifyThreatActorStatus() {
        Wafv2Client wafClient = null;
        int accId = Context.accountId.get();
        Bson filters = Filters.and(
            Filters.eq("_id", accId+ "_" + "AWS_WAF"),
            Filters.eq("accountId", accId)
        );
        Config.AwsWafConfig awsWafConfig = (Config.AwsWafConfig) ConfigsDao.instance.findOne(filters);

        if(awsWafConfig == null) {
            loggerMaker.debugAndAddToDb("Trying Cloudflare Waf.");
            return modifyThreatActorStatusCloudflare();
        }

        try {
            wafClient = getAwsWafClient(awsWafConfig.getAwsAccessKey(), awsWafConfig.getAwsSecretKey(), awsWafConfig.getRegion());
            //ListWebAcLsResponse webAclsResponse = wafClient.listWebACLs(ListWebAcLsRequest.builder().scope(SCOPE).build());
            loggerMaker.debugAndAddToDb("init aws client, for threat actor block");
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
                boolean res = sendModifyThreatActorStatusToBackend(ipWithCidr);
                if(!res) {
                    return ERROR.toUpperCase();
                } else {
                    return SUCCESS.toUpperCase();
                }
            }
            return SUCCESS.toUpperCase();
            
        } catch (Wafv2Exception e) {
            loggerMaker.debugAndAddToDb("Error modiftying threat actor status: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    public boolean sendModifyThreatActorStatusToBackend(String ip) {
        String requestUrl = String.format("%s/api/dashboard/modifyThreatActorStatus", this.getBackendUrl());
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList("Bearer " + this.getApiToken()));
        headers.put("Content-Type", Collections.singletonList("application/json"));

        Map<String, Object> body =
                new HashMap<String, Object>() {
                    {
                        put("updated_ts", Context.now());
                        put("ip", ip);
                        put("status", status);
                    }
                };
        String msg = objectMapper.valueToTree(body).toString();
        OriginalHttpRequest request = new OriginalHttpRequest(requestUrl, "", "POST", msg, headers, "application/json");

        try {
            OriginalHttpResponse resp = ApiExecutor.sendRequest(request, true, null, false, null);
            String responseBody = resp.getBody();
            loggerMaker.debugAndAddToDb("updated threat actor status");
        } catch (Exception e) {
            e.printStackTrace();
            loggerMaker.errorAndAddToDb("Error sending threat actor status " + e.getMessage());
            return false;
        }

        return true;
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
            loggerMaker.debugAndAddToDb("Blocked Threat Actor: " + actorIp);
            return true;
        } else {
            loggerMaker.debugAndAddToDb("Threat Actor " + actorIp + " is already blocked.");
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
          loggerMaker.debugAndAddToDb("Unblocked Threat Actor: " + actorIp);
          return true;
      } else {
          loggerMaker.debugAndAddToDb("Threat Actor " + actorIp + " is currently active");
          return false;
      }
  }

    public String sendIntegrationDataToThreatBackend() {
      String requestUrl = String.format("%s/api/dashboard/addSplunkIntegration", this.getBackendUrl());
      Map<String, List<String>> headers = new HashMap<>();
      headers.put("Authorization", Collections.singletonList("Bearer " + this.getApiToken()));
      headers.put("Content-Type", Collections.singletonList("application/json"));
  
      Map<String, Object> body =
          new HashMap<String, Object>() {
            {
              put("splunk_url", splunkUrl);
              put("splunk_token", splunkToken);
            }
          };
      String msg = objectMapper.valueToTree(body).toString();
  
      OriginalHttpRequest request = new OriginalHttpRequest(requestUrl, "", "POST", msg, headers, "application/json");
  
      try {
        OriginalHttpResponse resp = ApiExecutor.sendRequest(request, true, null, false, null);  
        String responseBody = resp.getBody();
  
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

  public String getActor() {
    return actor;
  }

  public void setActor(String actor) {
    this.actor = actor;
  }

  public String getFilterId() {
    return filterId;
  }

  public void setFilterId(String filterId) {
    this.filterId = filterId;
  }

  public Map<String, Integer> getSort() {
    return sort;
  }

  public void setSort(Map<String, Integer> sort) {
    this.sort = sort;
  }
}

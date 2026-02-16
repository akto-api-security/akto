package com.akto.action.threat_detection;

import com.akto.ProtoMessageUtils;
import com.akto.action.waf.CloudflareWafAction;
import com.akto.dao.ConfigsDao;
import com.akto.dao.context.Context;
import com.akto.dao.monitoring.FilterYamlTemplateDao;
import com.akto.dto.Config;
import com.akto.dto.Config.AwsWafConfig;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.type.URLMethods;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchMaliciousEventsResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchThreatsForActorResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListThreatActorResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatActorByCountryResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatActorFilterResponse;
import com.akto.testing.ApiExecutor;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
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
  List<BasicDBObject> threatComplianceInfos;
  int skip;
  String cursor;  // ObjectId hex string for cursor-based pagination
  static final int LIMIT = 50;
  long total;
  Map<String, Integer> sort;
  int startTimestamp, endTimestamp;
  String refId;
  List<String> latestAttack;
  List<ActivityData> actorActivities;
  List<String> country;
  List<String> actorId;
  List<String> host;
  int startTs;
  int endTs;
  String splunkUrl;
  String splunkToken;
  String actorIp;
  List<String> actorIps;
  String status;
  String eventType;
  String actor;
  String filterId;

  // TODO: remove this, use API Executor.
  private final CloseableHttpClient httpClient;

  public static final String CLOUDFLARE_WAF_BASE_URL = "https://api.cloudflare.com/client/v4";

  private final ObjectMapper objectMapper = new ObjectMapper();

  private static final LoggerMaker loggerMaker = new LoggerMaker(ThreatActorAction.class, LogDb.DASHBOARD);
  private static final String SCOPE = "REGIONAL";

  public ThreatActorAction() {
    super();
    this.httpClient = HttpClients.createDefault();
  }

  public String getActorsCountPerCounty() {
    HttpPost post = new HttpPost(String.format("%s/api/dashboard/get_actors_count_per_country", this.getBackendUrl()));
    post.addHeader("Authorization", "Bearer " + this.getApiToken());
    post.addHeader("Content-Type", "application/json");
    post.addHeader("x-context-source", Context.contextSource.get() != null ? Context.contextSource.get().toString() : "");

    if(endTs <= 0){
        endTs = Context.now();
    }
    startTs = Math.max(startTs, 0);

    Map<String, Object> body = new HashMap<String, Object>() {
      {
        put("start_ts", startTs);
        put("end_ts", endTs);
        put("latestAttack", latestAttack);
      }
    };
    String msg = objectMapper.valueToTree(body).toString();

    StringEntity requestEntity = new StringEntity(msg, ContentType.APPLICATION_JSON);
    post.setEntity(requestEntity);

    try (CloseableHttpResponse resp = this.httpClient.execute(post)) {
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
    int accountId = Context.accountId.get();
    CONTEXT_SOURCE source = Context.contextSource.get();

    try (CloseableHttpResponse resp = this.httpClient.execute(get)) {
      String responseBody = EntityUtils.toString(resp.getEntity());

      ProtoMessageUtils.<ThreatActorFilterResponse>toProtoMessage(
          ThreatActorFilterResponse.class, responseBody)
          .ifPresent(
              msg -> {
                this.country = msg.getCountriesList();
                Set<String> allowedTemplates = FilterYamlTemplateDao.getContextTemplatesForAccount(accountId, source);
                this.latestAttack =
                    msg.getSubCategoriesList().stream()
                        .filter(allowedTemplates::contains)
                        .collect(Collectors.toList());
                this.actorId = msg.getActorIdList();
                this.host = msg.getHostList();
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
    post.addHeader("x-context-source", Context.contextSource.get().toString());
    Map<String, Object> filter = new HashMap<>();

    filter.put("latestAttack", latestAttack);

    if(this.country != null && !this.country.isEmpty()){
      filter.put("country", this.country);
    }
    if(this.actorId != null && !this.actorId.isEmpty()){
      filter.put("actors", this.actorId);
    }
    if(this.host != null && !this.host.isEmpty()){
      filter.put("hosts", this.host);
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
            if (cursor != null && !cursor.isEmpty()) {
              put("cursor", cursor);
            }
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
                                    smr.getObjectId(),
                                    smr.getLatestApiEndpoint(),
                                    smr.getLatestApiIp(),
                                    URLMethods.Method.fromString(smr.getLatestApiMethod()),
                                    smr.getDiscoveredAt(),
                                    smr.getCountry(),
                                    smr.getLatestSubcategory(),
                                    smr.getLatestApiHost(),
                                    smr.getLatestMetadata()))
                        .collect(Collectors.toList());

                this.total = m.getTotal();
              });
    } catch (Exception e) {
      e.printStackTrace();
      return ERROR.toUpperCase();
    }

    return SUCCESS.toUpperCase();
  }

  public String fetchThreatsForActor() {
    HttpPost post =
        new HttpPost(String.format("%s/api/dashboard/fetch_threats_for_actor", this.getBackendUrl()));
    post.addHeader("Authorization", "Bearer " + this.getApiToken());
    post.addHeader("Content-Type", "application/json");
    post.addHeader("x-context-source", Context.contextSource.get().toString());

    Map<String, Object> body =
        new HashMap<String, Object>() {
          {
            put("actor", actor);
            put("limit", 20);
          }
        };
    String msg = objectMapper.valueToTree(body).toString();

    StringEntity requestEntity = new StringEntity(msg, ContentType.APPLICATION_JSON);
    post.setEntity(requestEntity);

    try (CloseableHttpResponse resp = this.httpClient.execute(post)) {
      String responseBody = EntityUtils.toString(resp.getEntity());

      ProtoMessageUtils.<FetchThreatsForActorResponse>toProtoMessage(
              FetchThreatsForActorResponse.class, responseBody)
          .ifPresent(
              m -> {
                this.actorActivities =
                    m.getActivitiesList().stream()
                        .map(
                            activity ->
                                new ActivityData(
                                    activity.getUrl(),
                                    activity.getSeverity(),
                                    activity.getSubCategory(),
                                    activity.getDetectedAt(),
                                    activity.getMethod(),
                                    activity.getHost(),
                                    activity.getMetadata()))
                        .collect(Collectors.toList());
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
            put("actor", actor);
            put("filterId", filterId);
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

  public String fetchThreatComplianceInfos() {
    try {
      Bson emptyFilter = Filters.empty();
      List<com.akto.dto.threat_detection.ThreatComplianceInfo> threatComplianceList =
          com.akto.dao.threat_detection.ThreatComplianceInfosDao.instance.findAll(emptyFilter);

      this.threatComplianceInfos = new ArrayList<>();
      for (com.akto.dto.threat_detection.ThreatComplianceInfo threatCompliance : threatComplianceList) {
        BasicDBObject obj = new BasicDBObject();
        obj.put(Constants.ID, threatCompliance.getId());
        obj.put("mapComplianceToListClauses", threatCompliance.getMapComplianceToListClauses());
        obj.put("author", threatCompliance.getAuthor());
        obj.put("hash", threatCompliance.getHash());
        this.threatComplianceInfos.add(obj);
      }

      loggerMaker.infoAndAddToDb("Fetched " + this.threatComplianceInfos.size() + " threat compliance infos", LogDb.DASHBOARD);
      return SUCCESS.toUpperCase();
    } catch (Exception e) {
      loggerMaker.errorAndAddToDb(e, "Error while fetching threat compliance infos: " + e.getMessage(), LogDb.DASHBOARD);
      return ERROR.toUpperCase();
    }
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

    private static Config.CloudflareWafConfig getCloudflareConfig() {
        int accId = Context.accountId.get();
        Bson filters = Filters.and(
            Filters.eq(Constants.ID, accId + "_" + Config.ConfigType.CLOUDFLARE_WAF),
            Filters.eq(Config.CloudflareWafConfig.ACCOUNT_ID, accId)
        );
        Config.CloudflareWafConfig config = (Config.CloudflareWafConfig) ConfigsDao.instance.findOne(filters);
        return CloudflareWafAction.migrateToListIfNeeded(config);
    }

    private static boolean hasValidListConfig(Config.CloudflareWafConfig config) {
        return config != null && config.getListIds() != null && !config.getListIds().isEmpty();
    }

    private static String getLastListId(Config.CloudflareWafConfig config) {
        return config.getListIds().get(config.getListIds().size() - 1);
    }

    public String modifyThreatActorStatusCloudflare() {
        Config.CloudflareWafConfig cloudflareWafConfig = getCloudflareConfig();

        if (!hasValidListConfig(cloudflareWafConfig)) {
            addActionError("Cloudflare WAF integration not configured properly.");
            return ERROR.toUpperCase();
        }

        boolean result;
        if (status.equalsIgnoreCase("blocked")) {
            result = addIPsToList(cloudflareWafConfig, Collections.singletonList(actorIp));
        } else {
            result = removeIPsFromLists(cloudflareWafConfig, Collections.singletonList(actorIp));
        }

        if (result) {
            boolean backendRes = sendModifyThreatActorStatusToBackend(actorIp);
            return backendRes ? SUCCESS.toUpperCase() : ERROR.toUpperCase();
        }
        return ERROR.toUpperCase();
    }

    // ==================== Bulk Block/Unblock ====================

    public String bulkModifyThreatActorStatusCloudflare() {
        if (actorIps == null || actorIps.isEmpty()) {
            addActionError("No IPs provided.");
            return ERROR.toUpperCase();
        }

        Config.CloudflareWafConfig cloudflareWafConfig = getCloudflareConfig();

        if (!hasValidListConfig(cloudflareWafConfig)) {
            addActionError("Cloudflare WAF integration not configured properly.");
            return ERROR.toUpperCase();
        }

        boolean result;
        if ("blocked".equalsIgnoreCase(status)) {
            result = addIPsToList(cloudflareWafConfig, actorIps);
        } else {
            result = removeIPsFromLists(cloudflareWafConfig, actorIps);
        }

        if (!result) {
            return ERROR.toUpperCase();
        }

        for (String ip : actorIps) {
            sendModifyThreatActorStatusToBackend(ip);
        }
        return SUCCESS.toUpperCase();
    }

    // ==================== Cloudflare List Operations ====================

    /**
     * Adds IPs to the last list. If list is full, creates overflow list and retries.
     */
    private boolean addIPsToList(Config.CloudflareWafConfig config, List<String> ips) {
        try {
            String currentListId = getLastListId(config);
            Map<String, List<String>> headers = CloudflareWafAction.getAuthHeaders(config);

            BasicDBList items = new BasicDBList();
            for (String ip : ips) {
                BasicDBObject ipItem = new BasicDBObject();
                ipItem.put("ip", ip);
                items.add(ipItem);
            }

            String url = CLOUDFLARE_WAF_BASE_URL + "/accounts/" + config.getAccountOrZoneId()
                    + "/rules/lists/" + currentListId + "/items";
            OriginalHttpRequest request = new OriginalHttpRequest(url, "", "POST", items.toString(), headers, "");
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());

            if (response.getStatusCode() > 201 || response.getBody() == null) {
                String cfErr = CloudflareWafAction.extractCfError(response.getBody());

                // Check if list is at capacity — create overflow list and retry
                if (cfErr != null && cfErr.toLowerCase().contains("maximum")) {
                    String newListId = CloudflareWafAction.createOverflowList(config);
                    if (newListId == null) {
                        addActionError("Failed to create overflow IP list.");
                        return false;
                    }
                    // Retry with new list
                    url = CLOUDFLARE_WAF_BASE_URL + "/accounts/" + config.getAccountOrZoneId()
                            + "/rules/lists/" + newListId + "/items";
                    request = new OriginalHttpRequest(url, "", "POST", items.toString(), headers, "");
                    response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());
                    if (response.getStatusCode() > 201 || response.getBody() == null) {
                        addActionError(CloudflareWafAction.extractCfError(response.getBody()));
                        return false;
                    }
                    return true;
                }

                addActionError(cfErr != null ? cfErr : "Unable to block IP(s).");
                return false;
            }
            return true;
        } catch (Exception e) {
            addActionError("Unable to block IP(s). Try again later.");
            loggerMaker.errorAndAddToDb("Error adding IPs to Cloudflare list: " + e.getMessage());
            return false;
        }
    }

    /**
     * Removes IPs by searching across ALL lists and deleting from whichever contains them.
     */
    private boolean removeIPsFromLists(Config.CloudflareWafConfig config, List<String> ips) {
        try {
            Map<String, List<String>> headers = CloudflareWafAction.getAuthHeaders(config);

            // Group items to delete by their list ID
            Map<String, BasicDBList> deleteByList = new HashMap<>();
            for (String ip : ips) {
                String[] found = findItemAcrossLists(ip, config);
                if (found != null) {
                    String foundListId = found[0];
                    String itemId = found[1];
                    deleteByList.computeIfAbsent(foundListId, k -> new BasicDBList());
                    BasicDBObject itemObj = new BasicDBObject();
                    itemObj.put("id", itemId);
                    deleteByList.get(foundListId).add(itemObj);
                }
            }

            if (deleteByList.isEmpty()) {
                addActionError("None of the IPs were found in the block list(s).");
                return false;
            }

            // Delete from each list in one call per list
            for (Map.Entry<String, BasicDBList> entry : deleteByList.entrySet()) {
                String url = CLOUDFLARE_WAF_BASE_URL + "/accounts/" + config.getAccountOrZoneId()
                        + "/rules/lists/" + entry.getKey() + "/items";
                BasicDBObject deletePayload = new BasicDBObject();
                deletePayload.put("items", entry.getValue());

                OriginalHttpRequest request = new OriginalHttpRequest(url, "", "DELETE", deletePayload.toString(), headers, "");
                OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());

                if (response.getStatusCode() > 201 || response.getBody() == null) {
                    String cfErr = CloudflareWafAction.extractCfError(response.getBody());
                    addActionError(cfErr != null ? cfErr : "Unable to unblock IP(s).");
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            addActionError("Unable to unblock IP(s). Try again later.");
            loggerMaker.errorAndAddToDb("Error removing IPs from Cloudflare lists: " + e.getMessage());
            return false;
        }
    }

    /**
     * Searches for an IP across all lists. Returns [listId, itemId] or null.
     */
    private static String[] findItemAcrossLists(String actorIp, Config.CloudflareWafConfig config) {
        Map<String, List<String>> headers = CloudflareWafAction.getAuthHeaders(config);
        for (String listId : config.getListIds()) {
            try {
                String url = CLOUDFLARE_WAF_BASE_URL + "/accounts/" + config.getAccountOrZoneId()
                        + "/rules/lists/" + listId + "/items?search=" + actorIp;
                OriginalHttpRequest request = new OriginalHttpRequest(url, "", "GET", "", headers, "");
                OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());

                if (response.getStatusCode() > 201 || response.getBody() == null) continue;

                BasicDBList result = (BasicDBList) BasicDBObject.parse(response.getBody()).get("result");
                if (result != null && !result.isEmpty()) {
                    String itemId = ((BasicDBObject) result.get(0)).getString("id");
                    if (itemId != null) {
                        return new String[]{listId, itemId};
                    }
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error searching list " + listId + " for IP: " + e.getMessage());
            }
        }
        return null;
    }

    public List<String> getActorIps() {
        return actorIps;
    }

    public void setActorIps(List<String> actorIps) {
        this.actorIps = actorIps;
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
        HttpPost post =
                new HttpPost(String.format("%s/api/dashboard/modifyThreatActorStatus", this.getBackendUrl()));
        post.addHeader("Authorization", "Bearer " + this.getApiToken());
        post.addHeader("Content-Type", "application/json");

        Map<String, Object> body =
                new HashMap<String, Object>() {
                    {
                        put("updated_ts", Context.now());
                        put("ip", ip);
                        put("status", status);
                    }
                };
        String msg = objectMapper.valueToTree(body).toString();

        StringEntity requestEntity = new StringEntity(msg, ContentType.APPLICATION_JSON);
        post.setEntity(requestEntity);

        try (CloseableHttpResponse response = this.httpClient.execute(post)) {
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

  public String getCursor() {
    return cursor;
  }

  public void setCursor(String cursor) {
    this.cursor = cursor;
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

  public List<String> getHost() {
    return host;
  }

  public void setHost(List<String> host) {
    this.host = host;
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

  public List<ActivityData> getActorActivities() {
    return actorActivities;
  }

  public void setActorActivities(List<ActivityData> actorActivities) {
    this.actorActivities = actorActivities;
  }

  public List<BasicDBObject> getThreatComplianceInfos() {
    return threatComplianceInfos;
  }

  public void setThreatComplianceInfos(List<BasicDBObject> threatComplianceInfos) {
    this.threatComplianceInfos = threatComplianceInfos;
  }
}

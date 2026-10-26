package com.akto.action.threat_detection;

import com.akto.utils.ArgusCollectionScope;
import com.akto.ProtoMessageUtils;
import com.akto.action.threat_detection.utils.ThreatsUtils;
import com.akto.action.waf.CloudflareWafAction;
import com.akto.dao.ConfigsDao;
import com.akto.dao.context.Context;
import com.akto.dao.monitoring.FilterYamlTemplateDao;
import com.akto.dao.threat_detection.GuardrailComplianceInfosDao;
import com.akto.dto.threat_detection.GuardrailComplianceInfo;
import com.akto.dto.Config;
import com.akto.threat_utils.AwsWafUtils;
import com.akto.threat_utils.CloudflareWafUtils;
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
import com.akto.util.ThreatDetectionConstants;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;


import java.util.*;
import java.util.stream.Collectors;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.bson.conversions.Bson;

public class ThreatActorAction extends AbstractThreatDetectionAction {

  List<DashboardThreatActor> actors;
  List<MaliciousPayloadsResponse> maliciousPayloadsResponses;
  List<ThreatActorPerCountry> actorsCountPerCountry;
  List<BasicDBObject> threatComplianceInfos;
  List<BasicDBObject> guardrailComplianceInfos;
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
  List<String> eventIds; // a grouped activity row's events, shown as its attempts

  // TODO: remove this, use API Executor.
  private final CloseableHttpClient httpClient;

  public static final String CLOUDFLARE_WAF_BASE_URL = "https://api.cloudflare.com/client/v4";

  private final ObjectMapper objectMapper = new ObjectMapper();

  private static final LoggerMaker loggerMaker = new LoggerMaker(ThreatActorAction.class, LogDb.DASHBOARD);

  public ThreatActorAction() {
    super();
    this.httpClient = HttpClients.createDefault();
  }

  public String fetchActorsCountPerCounty() {
    // Users limited to specific collections: the backend counts only their agents (from their own events if it cannot)
    Map<String, Object> hostScope = backendHostScope();
    if (hostScope == null && isLimitedToOwnAgents()) {
      Map<String, Object> filters = new HashMap<>();
      if (latestAttack != null && !latestAttack.isEmpty()) filters.put("latestAttack", latestAttack);
      this.actorsCountPerCountry = new OwnAgentThreatStats(
          fetchAllMaliciousEvents(startTs, endTs, OWN_EVENTS_LIMIT, filters, null, true)).actorsPerCountry();
      return SUCCESS.toUpperCase();
    }
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
        if (hostScope != null) {
          put("hostScope", hostScope);
        }
      }
    };
    String msg = objectMapper.valueToTree(body).toString();

    StringEntity requestEntity = new StringEntity(msg, ContentType.APPLICATION_JSON);
    post.setEntity(requestEntity);

    try (CloseableHttpResponse resp = this.httpClient.execute(post)) {
      if (hostScopeRejected(hostScope, resp.getStatusLine().getStatusCode())) {
        return fetchActorsCountPerCounty();
      }
      String responseBody = ThreatsUtils.readResponseBody(resp.getEntity());

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
    // Users limited to specific collections: filter values from their own events only
    List<DashboardMaliciousEvent> ownEvents = fetchOwnEventsIfLimited();
    if (ownEvents != null) {
      this.country = ownEvents.stream().map(DashboardMaliciousEvent::getCountry).filter(c -> c != null && !c.isEmpty()).distinct().collect(Collectors.toList());
      this.actorId = ownEvents.stream().map(DashboardMaliciousEvent::getActor).filter(Objects::nonNull).distinct().collect(Collectors.toList());
      this.host = ownEvents.stream().map(DashboardMaliciousEvent::getHost).filter(Objects::nonNull).distinct().collect(Collectors.toList());
      this.latestAttack = ownEvents.stream().map(DashboardMaliciousEvent::getFilterId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
      return SUCCESS.toUpperCase();
    }
    HttpGet get = new HttpGet(String.format("%s/api/dashboard/fetch_filters_for_threat_actors", this.getBackendUrl()));
    get.addHeader("Authorization", "Bearer " + this.getApiToken());
    get.addHeader("Content-Type", "application/json");
    int accountId = Context.accountId.get();
    CONTEXT_SOURCE source = Context.contextSource.get();

    try (CloseableHttpResponse resp = this.httpClient.execute(get)) {
      String responseBody = ThreatsUtils.readResponseBody(resp.getEntity());

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
    // Users limited to specific collections only see actors of their own agents
    Set<String> allowedHosts = ArgusCollectionScope.getRestrictedHosts(getSUser());
    if (allowedHosts != null) {
      List<String> scopedHosts = ArgusCollectionScope.scopeValues(this.host, allowedHosts);
      if (scopedHosts.isEmpty()) {
        this.actors = new ArrayList<>();
        this.total = 0;
        return SUCCESS.toUpperCase();
      }
      filter.put("hosts", scopedHosts);
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
      String responseBody = ThreatsUtils.readResponseBody(resp.getEntity());

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
                                    smr.getLatestMetadata(),
                                    smr.getStatus()))
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
      String responseBody = ThreatsUtils.readResponseBody(resp.getEntity());

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

    // Users limited to specific collections only see the actor's activity on their own agents
    List<DashboardMaliciousEvent> ownEvents = fetchOwnEventsIfLimited();
    if (ownEvents != null && this.actorActivities != null) {
      Set<String> ownHosts = ownEvents.stream().map(DashboardMaliciousEvent::getHost).filter(Objects::nonNull).collect(Collectors.toSet());
      this.actorActivities = this.actorActivities.stream().filter(a -> ownHosts.contains(a.getHost())).collect(Collectors.toList());
    }

    return SUCCESS.toUpperCase();
  }

  public String fetchAggregateMaliciousRequests() {
    long t0 = System.currentTimeMillis();
    loggerMaker.infoAndAddToDb("fetchAggregateMaliciousRequests START ts=" + t0 + " refId=" + refId + " eventType=" + eventType, LogDb.DASHBOARD);

    // Users limited to specific collections only see events of their own agents
    List<DashboardMaliciousEvent> ownEvents = fetchOwnEventsIfLimited();
    if (ownEvents != null) {
      boolean ownEvent = ownEvents.stream().anyMatch(e ->
          (refId != null && !refId.isEmpty())
              ? refId.equals(e.getRefId())
              : (actor != null && actor.equals(e.getActor()) && filterId != null && filterId.equals(e.getFilterId())));
      if (!ownEvent) {
        this.maliciousPayloadsResponses = new ArrayList<>();
        return SUCCESS.toUpperCase();
      }
    }

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
            if (eventIds != null && !eventIds.isEmpty()) {
              put("event_ids", eventIds);
            }
          }
        };
    String msg = objectMapper.valueToTree(body).toString();

    StringEntity requestEntity = new StringEntity(msg, ContentType.APPLICATION_JSON);
    post.setEntity(requestEntity);

    loggerMaker.infoAndAddToDb("fetchAggregateMaliciousRequests BEFORE_HTTP_EXECUTE elapsed=" + (System.currentTimeMillis() - t0) + "ms", LogDb.DASHBOARD);
    try (CloseableHttpResponse resp = this.httpClient.execute(post)) {
      loggerMaker.infoAndAddToDb("fetchAggregateMaliciousRequests AFTER_HTTP_EXECUTE elapsed=" + (System.currentTimeMillis() - t0) + "ms", LogDb.DASHBOARD);
      String responseBody = ThreatsUtils.readResponseBody(resp.getEntity());
      loggerMaker.infoAndAddToDb("fetchAggregateMaliciousRequests AFTER_RESPONSE_READ elapsed=" + (System.currentTimeMillis() - t0) + "ms", LogDb.DASHBOARD);

      ProtoMessageUtils.<FetchMaliciousEventsResponse>toProtoMessage(
        FetchMaliciousEventsResponse.class, responseBody)
          .ifPresent(
              m -> {
                this.maliciousPayloadsResponses =
                    m.getMaliciousPayloadsResponseList().stream()
                        .map(
                            smr -> {
                              MaliciousPayloadsResponse payload = new MaliciousPayloadsResponse(smr.getOrig(), smr.getMetadata(), smr.getTs());
                              payload.setEventId(smr.getEventId());
                              payload.setRefId(smr.getRefId());
                              payload.setFilterId(smr.getFilterId());
                              payload.setSeverity(smr.getSeverity());
                              payload.setEndpoint(smr.getEndpoint());
                              payload.setMethod(smr.getMethod());
                              payload.setEvidenceLine(smr.getEvidenceLine());
                              payload.setRemediation(smr.getRemediation());
                              payload.setStatus(smr.getStatus());
                              return payload;
                            })
                        .collect(Collectors.toList());
              });
    } catch (Exception e) {
      e.printStackTrace();
      return ERROR.toUpperCase();
    }

    loggerMaker.infoAndAddToDb("fetchAggregateMaliciousRequests END totalElapsed=" + (System.currentTimeMillis() - t0) + "ms", LogDb.DASHBOARD);
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

  public String fetchGuardrailComplianceInfos() {
    try {
      Bson emptyFilter = Filters.empty();
      List<GuardrailComplianceInfo> guardrailComplianceList =
          GuardrailComplianceInfosDao.instance.findAll(emptyFilter);

      this.guardrailComplianceInfos = new ArrayList<>();
      for (GuardrailComplianceInfo guardrailCompliance : guardrailComplianceList) {
        BasicDBObject obj = new BasicDBObject();
        obj.put(Constants.ID, guardrailCompliance.getId());
        obj.put(GuardrailComplianceInfo.MAP_COMPLIANCE_TO_LIST_CLAUSES, guardrailCompliance.getMapComplianceToListClauses());
        obj.put(GuardrailComplianceInfo.AUTHOR, guardrailCompliance.getAuthor());
        obj.put(GuardrailComplianceInfo.HASH, guardrailCompliance.getHash());
        this.guardrailComplianceInfos.add(obj);
      }

      return SUCCESS.toUpperCase();
    } catch (Exception e) {
      loggerMaker.errorAndAddToDb(e, "Error while fetching guardrail compliance infos: " + e.getMessage(), LogDb.DASHBOARD);
      return ERROR.toUpperCase();
    }
  }

    private static Config.CloudflareWafConfig fetchCloudflareConfig() {
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

    public String modifyThreatActorStatusCloudflare() {
        Config.CloudflareWafConfig cloudflareWafConfig = fetchCloudflareConfig();

        if (!hasValidListConfig(cloudflareWafConfig)) {
            addActionError("Cloudflare WAF integration not configured properly.");
            return ERROR.toUpperCase();
        }

        boolean result;
        if (ThreatDetectionConstants.BLOCKED.equals(status)) {
            result = CloudflareWafUtils.blockActorIps(cloudflareWafConfig, Collections.singletonList(actorIp));
        } else {
            result = CloudflareWafUtils.unblockActorIps(cloudflareWafConfig, Collections.singletonList(actorIp));
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

        boolean result;
        Config.AwsWafConfig awsWafConfig = fetchAwsWafConfig();
        if (awsWafConfig != null) {
            result = ThreatDetectionConstants.BLOCKED.equals(status)
                ? AwsWafUtils.blockActorIps(awsWafConfig, actorIps)
                : AwsWafUtils.unblockActorIps(awsWafConfig, actorIps);
        } else {
            Config.CloudflareWafConfig cloudflareWafConfig = fetchCloudflareConfig();

            if (!hasValidListConfig(cloudflareWafConfig)) {
                addActionError("Cloudflare WAF integration not configured properly.");
                return ERROR.toUpperCase();
            }

            if (ThreatDetectionConstants.BLOCKED.equals(status)) {
                result = CloudflareWafUtils.blockActorIps(cloudflareWafConfig, actorIps);
            } else {
                result = CloudflareWafUtils.unblockActorIps(cloudflareWafConfig, actorIps);
            }
        }

        if (!result) {
            return ERROR.toUpperCase();
        }

        return sendBulkModifyThreatActorStatusToBackend(actorIps) ? SUCCESS.toUpperCase() : ERROR.toUpperCase();
    }


    public List<String> getActorIps() {
        return actorIps;
    }

    public void setActorIps(List<String> actorIps) {
        this.actorIps = actorIps;
    }

    private static Config.AwsWafConfig fetchAwsWafConfig() {
        int accId = Context.accountId.get();
        Bson filters = Filters.and(
            Filters.eq("_id", accId+ "_" + "AWS_WAF"),
            Filters.eq("accountId", accId)
        );
        return (Config.AwsWafConfig) ConfigsDao.instance.findOne(filters);
    }

    public String modifyThreatActorStatus() {
        Config.AwsWafConfig awsWafConfig = fetchAwsWafConfig();

        if(awsWafConfig == null) {
            loggerMaker.debugAndAddToDb("Trying Cloudflare Waf.");
            return modifyThreatActorStatusCloudflare();
        }

        List<String> ips = Collections.singletonList(actorIp);
        boolean result = ThreatDetectionConstants.BLOCKED.equals(status)
            ? AwsWafUtils.blockActorIps(awsWafConfig, ips)
            : AwsWafUtils.unblockActorIps(awsWafConfig, ips);
        if (!result) {
            return ERROR.toUpperCase();
        }

        return sendModifyThreatActorStatusToBackend(actorIp) ? SUCCESS.toUpperCase() : ERROR.toUpperCase();
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

    public boolean sendBulkModifyThreatActorStatusToBackend(List<String> ips) {
        HttpPost post =
                new HttpPost(String.format("%s/api/dashboard/bulkModifyThreatActorStatus", this.getBackendUrl()));
        post.addHeader("Authorization", "Bearer " + this.getApiToken());
        post.addHeader("Content-Type", "application/json");

        Map<String, Object> body =
                new HashMap<String, Object>() {
                    {
                        put("updated_ts", Context.now());
                        put("ips", ips);
                        put("status", status);
                    }
                };
        String msg = objectMapper.valueToTree(body).toString();

        StringEntity requestEntity = new StringEntity(msg, ContentType.APPLICATION_JSON);
        post.setEntity(requestEntity);

        try (CloseableHttpResponse response = this.httpClient.execute(post)) {
            loggerMaker.debugAndAddToDb("updated threat actor status in bulk");
        } catch (Exception e) {
            e.printStackTrace();
            loggerMaker.errorAndAddToDb("Error sending bulk threat actor status " + e.getMessage());
            return false;
        }

        return true;
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
        String responseBody = ThreatsUtils.readResponseBody(resp.getEntity());
  
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

  public void setEventIds(List<String> eventIds) {
    this.eventIds = eventIds;
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

  public List<BasicDBObject> getGuardrailComplianceInfos() {
    return guardrailComplianceInfos;
  }

  public void setGuardrailComplianceInfos(List<BasicDBObject> guardrailComplianceInfos) {
    this.guardrailComplianceInfos = guardrailComplianceInfos;
  }
}

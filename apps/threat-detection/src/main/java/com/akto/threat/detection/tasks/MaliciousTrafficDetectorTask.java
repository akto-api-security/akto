package com.akto.threat.detection.tasks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.akto.dto.*;
import com.akto.enums.RedactionType;
import com.akto.threat.detection.cache.AccountConfig;
import com.akto.threat.detection.cache.AccountConfigurationCache;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

import com.akto.IPLookupClient;
import com.akto.RawApiMetadataFactory;
import com.akto.dao.context.Context;
import com.akto.data_actor.ClientActor;
import com.akto.data_actor.DataActor;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.api_protection_parse_layer.AggregationRules;
import com.akto.dto.api_protection_parse_layer.Rule;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLTemplate;
import com.akto.hybrid_parsers.HttpCallParser;
import com.akto.kafka.KafkaConfig;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.metrics.AllMetrics;
import com.akto.proto.generated.threat_detection.message.malicious_event.event_type.v1.EventType;
import com.akto.proto.generated.threat_detection.message.malicious_event.v1.MaliciousEventKafkaEnvelope;
import com.akto.proto.generated.threat_detection.message.malicious_event.v1.MaliciousEventMessage;
import com.akto.proto.generated.threat_detection.message.sample_request.v1.SampleMaliciousRequest;
import com.akto.proto.generated.threat_detection.message.sample_request.v1.SchemaConformanceError;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.RatelimitConfig.RatelimitConfigItem;
import com.akto.proto.http_response_param.v1.HttpResponseParam;
import com.akto.proto.http_response_param.v1.StringList;
import com.akto.threat.detection.cache.ApiCountCacheLayer;
import com.akto.threat.detection.cache.FilterCache;
import com.akto.threat.detection.cache.RedisBackedCounterCache;
import com.akto.threat.detection.constants.KafkaTopic;
import com.akto.threat.detection.constants.RedisKeyInfo;
import com.akto.threat.detection.ip_api_counter.DistributionCalculator;
import com.akto.threat.detection.ip_api_counter.ParamEnumerationDetector;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ParamEnumerationConfig;
import com.akto.threat.detection.kafka.KafkaProtoProducer;
import com.akto.threat.detection.smart_event_detector.window_based.WindowBasedThresholdNotifier;
import com.akto.threat.detection.utils.ThreatDetector;
import com.akto.threat.detection.utils.ThreatDetectorWithStrategy;
import com.akto.threat.detection.utils.Utils;
import com.akto.threat.detection.hyperscan.HyperscanEventHandler;
import com.akto.util.Constants;
import com.akto.util.HttpRequestResponseUtils;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;



/*
Class is responsible for consuming traffic data from the Kafka topic.
Pass data through filters and identify malicious traffic.
 */
public class MaliciousTrafficDetectorTask extends AbstractKafkaConsumerTask<byte[]> {

  private ThreatConfigurationEvaluator threatConfigEvaluator;
  private final HttpCallParser httpCallParser;
  private final WindowBasedThresholdNotifier windowBasedThresholdNotifier;

  // Used for schema conformance and API level rate limiting
  private WindowBasedThresholdNotifier apiCountWindowBasedThresholdNotifier = null;

  private final RawApiMetadataFactory rawApiFactory;

  private final FilterCache filterCache;

  private final KafkaProtoProducer internalKafka;

  private static final DataActor dataActor = DataActorFactory.fetchInstance();
  private static final LoggerMaker logger = new LoggerMaker(MaliciousTrafficDetectorTask.class, LogDb.THREAT_DETECTION);

  private static final HttpRequestParams requestParams = new HttpRequestParams();
  private static final HttpResponseParams responseParams = new HttpResponseParams();
  private static final FilterConfig ipApiRateLimitFilter = Utils.getipApiRateLimitFilter();
  private static final Set<String> DEFAULT_THREAT_PROTECTION_FILTER_IDS = new HashSet<>(Arrays.asList(
      "LocalFileInclusionLFIRFI", "NoSQLInjection", "OSCommandInjection", "SQLInjection",
      "SSRF", "SecurityMisconfig", "WindowsCommandInjection", "XSS"
  ));
  private static Supplier<String> lazyToString;
  private DistributionCalculator distributionCalculator;
  private ThreatDetectorWithStrategy threatDetector;
  private boolean apiDistributionEnabled;
  private ApiCountCacheLayer apiCacheCountLayer;
  private final AtomicInteger applyFilterLogCount = new AtomicInteger(0);
  private static final int MAX_APPLY_FILTER_LOGS = 1000;



  private final HyperscanEventHandler hyperscanEventHandler;

  public MaliciousTrafficDetectorTask(
      KafkaConfig trafficConfig, KafkaConfig internalConfig, RedisClient redisClient, DistributionCalculator distributionCalculator, boolean apiDistributionEnabled, String instanceId) throws Exception {
    super(trafficConfig, KafkaTopic.TRAFFIC_LOGS, instanceId);

    Context.accountId.set(ClientActor.getAccountId());

    logger.warnAndAddToDb(instanceId + ": Creating Kafka consumer with bootstrap servers: " + trafficConfig.getBootstrapServers() +
                          ", groupId: " + trafficConfig.getGroupId() +
                          ", maxPollRecords: " + trafficConfig.getConsumerConfig().getMaxPollRecords());
    logger.warnAndAddToDb(instanceId + ": Kafka consumer created successfully");

    this.httpCallParser = new HttpCallParser(120, 1000);
    

    this.windowBasedThresholdNotifier =
        new WindowBasedThresholdNotifier(
            new RedisBackedCounterCache(redisClient, "wbt"),
            new WindowBasedThresholdNotifier.Config(100, 10 * 60));
    
    StatefulRedisConnection<String, String> apiCache = null;
    if (redisClient != null) {
        apiCache = redisClient.connect();
        this.apiCacheCountLayer = new ApiCountCacheLayer(redisClient);
        this.apiCountWindowBasedThresholdNotifier = new WindowBasedThresholdNotifier(
          apiCacheCountLayer,
          new WindowBasedThresholdNotifier.Config(100, 10 * 60));
    }

    this.threatConfigEvaluator = new ThreatConfigurationEvaluator(null, dataActor, apiCacheCountLayer);
    this.filterCache = new FilterCache(dataActor, apiCache);

    // Initialize ParamEnumerationDetector with config values (or defaults)
    ParamEnumerationConfig paramEnumConfig = this.threatConfigEvaluator.getParamEnumerationConfig();
    ParamEnumerationDetector.initialize(redisClient, paramEnumConfig.getUniqueParamThreshold(), paramEnumConfig.getWindowSizeMinutes());

    this.internalKafka = new KafkaProtoProducer(internalConfig);
    this.rawApiFactory = new RawApiMetadataFactory(new IPLookupClient());
    this.distributionCalculator = distributionCalculator;
    this.apiDistributionEnabled = apiDistributionEnabled;

    this.threatDetector = new ThreatDetectorWithStrategy();
    this.hyperscanEventHandler = new HyperscanEventHandler(this::generateAndPushMaliciousEventRequest);
  }

  private int MAX_KAFKA_DEBUG_MSGS = 100;

  @Override
  protected void beforePollLoop() {
    try {
      Context.accountId.set(ClientActor.getAccountId());
    } catch (Exception e) {
      Context.accountId.set(1000000);
      e.printStackTrace();
    }
    logger.warnAndAddToDb(this.instanceId + ": Starting Kafka polling loop");
    AllMetrics.instance.collectInfraMetrics();
  }

  @Override
  void processRecords(ConsumerRecords<String, byte[]> records) {
    try {
      AccountConfig config = AccountConfigurationCache.getInstance().getConfig(dataActor);
      if (config == null) {
        Context.isRedactPayload.set(false);
      } else {
        Context.isRedactPayload.set(config.isRedacted());
      }

      for (ConsumerRecord<String, byte[]> record : records) {
        HttpResponseParam httpResponseParam = HttpResponseParam.parseFrom(record.value());
        if (MAX_KAFKA_DEBUG_MSGS > 0) {
          MAX_KAFKA_DEBUG_MSGS--;
          logger.infoAndAddToDb("Kafka record recieved " + httpResponseParam.toString());
        }
        if (ignoreTrafficFilter(httpResponseParam)) {
          continue;
        }
        processRecord(httpResponseParam);
      }
    } catch (Exception e) {
      logger.errorAndAddToDb("Error observed in processing record " + e.getMessage());
      e.printStackTrace();
    }
  }


  private boolean ignoreTrafficFilter(HttpResponseParam responseParam) {
    Map<String, StringList> headers = responseParam.getRequestHeadersMap();
    if (headers.get("x-akto-ignore") != null) {
      return true;
    }

    if (responseParam.getPath().contains("/api/threat_detection") || responseParam.getPath().contains("/api/dashboard") || responseParam.getPath().contains("/api/ingestData")) {
      return true;
    }

    List<String> hosts = headers.get("host");
    if (hosts == null || hosts.isEmpty()) return false;
    return hosts.contains(Constants.AKTO_THREAT_PROTECTION_BACKEND_HOST);
  }

  private boolean isDebugRequest(HttpResponseParams responseParam) {
    Map<String, List<String>> headers = responseParam.getRequestParams().getHeaders();
    return headers != null && headers.get("x-debug-trace") != null;
  }


  private void processRecord(HttpResponseParam record) throws Exception {
    HttpResponseParams responseParam = buildHttpResponseParam(record);
    String actor = this.threatConfigEvaluator.getActorId(responseParam);
    if (actor == null || actor.isEmpty()) {
      logger.warnAndAddToDb("Dropping processing of record with no actor IP, account: " + responseParam.getAccountId());
      return;
    }
    AccountConfig accountConfig = AccountConfigurationCache.getInstance().getConfig(dataActor);
    boolean isHyperscanEnabled = accountConfig != null && accountConfig.isHyperscanEnabled();

    Map<String, FilterConfig> filters = this.filterCache.getFilters();

    // When hyperscan is enabled, skip default threat protection filters (hyperscan handles them)
    // Only custom YAML templates should run through the filter loop
    if (isHyperscanEnabled && filters != null && !filters.isEmpty()) {
      filters = new HashMap<>(filters);
      filters.keySet().removeAll(DEFAULT_THREAT_PROTECTION_FILTER_IDS);
    }

    RawApi rawApi = null;
    RawApiMetadata metadata = null;
    if ((filters != null && !filters.isEmpty()) || isHyperscanEnabled) {
      rawApi = RawApi.buildFromMessageNew(responseParam);
      metadata = this.rawApiFactory.buildFromHttp(rawApi.getRequest(), rawApi.getResponse());
      rawApi.setRawApiMetdata(metadata);
    }

    int apiCollectionId = httpCallParser.createApiCollectionId(responseParam);
    responseParam.requestParams.setApiCollectionId(apiCollectionId);

    String url = responseParam.getRequestParams().getURL();
    URLMethods.Method method =
        URLMethods.Method.fromString(responseParam.getRequestParams().getMethod());

    // Convert static URL to template URL once for reuse
    // This is used for: 1) API count increment, 2) Redis/CMS aggregation, 3) ParamEnumeration filter
    URLTemplate matchedTemplate = null;
    if (apiDistributionEnabled && apiCollectionId != 0) {
      matchedTemplate = threatDetector.findMatchingUrlTemplate(responseParam);
    }

    // Use template URL if available, otherwise fall back to static URL
    // This ensures API counts aggregate on template URLs (e.g., /api/users/INTEGER instead of /api/users/123)
    String urlForAggregation = matchedTemplate != null ? matchedTemplate.getTemplateString() : url;

    ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(apiCollectionId, url, method);

    // Increment API count using template URL for proper aggregation (skip for default collection)
    String apiHitCountKey = Utils.buildApiHitCountKey(apiCollectionId, urlForAggregation, method.toString());
    if (apiCollectionId != 0 && this.apiCountWindowBasedThresholdNotifier != null) {
      this.apiCountWindowBasedThresholdNotifier.incrementApiHitcount(apiHitCountKey, responseParam.getTime(), RedisKeyInfo.API_COUNTER_SORTED_SET);
    }

    List<SchemaConformanceError> errors = null;

    boolean successfulExploit = false;
    boolean isIgnoredEvent = false;
    if (rawApi != null) {
      if (!filterCache.getIgnoredEventFilters().isEmpty()) {
        isIgnoredEvent = threatDetector.isIgnoredEvent(filterCache.getIgnoredEventFilters(), rawApi, apiInfoKey);
      }
      if (!filterCache.getSuccessfulExploitFilters().isEmpty()) {
        successfulExploit = threatDetector.isSuccessfulExploit(filterCache.getSuccessfulExploitFilters(), rawApi, apiInfoKey);
      }
    }

    if (apiDistributionEnabled && apiCollectionId != 0) {
      String apiCollectionIdStr = Integer.toString(apiCollectionId);

      String distributionKey = Utils.buildApiDistributionKey(apiCollectionIdStr, urlForAggregation, method.toString());
      String ipApiCmsKey = Utils.buildIpApiCmsDataKey(actor, apiCollectionIdStr, urlForAggregation, method.toString());
      long curEpochMin = responseParam.getTime() / 60;

      RatelimitConfigItem ratelimitConfig = this.threatConfigEvaluator.getDefaultRateLimitConfig();

      ApiInfo.ApiInfoKey templateApiInfoKey = new ApiInfo.ApiInfoKey(apiCollectionId, urlForAggregation, method);
      long ratelimit = this.threatConfigEvaluator.getRatelimit(templateApiInfoKey);

      // Fully async: XADD to Redis stream — ZERO sync Redis calls
      String host = "";
      Map<String, List<String>> reqHeaders = responseParam.getRequestParams().getHeaders();
      if (reqHeaders != null && reqHeaders.containsKey("host") && !reqHeaders.get("host").isEmpty()) {
        host = reqHeaders.get("host").get(0);
      }
      String countryCode = metadata != null && metadata.getCountryCode() != null ? metadata.getCountryCode() : "";
      String destCountryCode = metadata != null && metadata.getDestCountryCode() != null ? metadata.getDestCountryCode() : "";

      this.distributionCalculator.processRequest(
          distributionKey, curEpochMin, ipApiCmsKey, ratelimitConfig.getPeriod(),
          ratelimit, ratelimitConfig.getMitigationPeriod(),
          actor, host, responseParam.getAccountId(), responseParam.getTime(),
          countryCode, destCountryCode);
    }

    // Run Hyperscan if enabled
    if (isHyperscanEnabled) {
      RedactionType hsRedactionType = Utils.getRedactionType(responseParam.getRequestParams().getHeaders(), dataActor);
      hyperscanEventHandler.detectAndPushEvents(
          responseParam, apiInfoKey, actor, metadata,
          successfulExploit, isIgnoredEvent, hsRedactionType);
    }

    // Run custom filter loop
    if (filters != null && !filters.isEmpty()) {
      for (FilterConfig apiFilter : filters.values()) {
      boolean hasPassedFilter = false;
       // Create a fresh vulnerable list for each filter
      List<SchemaConformanceError> vulnerable = null;

      if(isDebugRequest(responseParam)){
        logger.debugAndAddToDb("Evaluating filter condition for url " + apiInfoKey.getUrl() + " filterId " + apiFilter.getId());
      }

      // Skip this filter, as it's handled by apiDistributionenabled
      if(apiFilter.getId().equals(ipApiRateLimitFilter.getId())){
        continue;
      }

      // Evaluate filter first (ignore and filter are independent conditions)
      // SchemaConform check is disabled
      List<Integer> accountIds = Arrays.asList(1758179941, 1763355072);
      if(accountIds.contains(Context.accountId.get()) && apiFilter.getInfo().getCategory().getName().equalsIgnoreCase("SchemaConform")) {
        logger.debug("SchemaConform filter found for url {} filterId {}", apiInfoKey.getUrl(), apiFilter.getId());
        // vulnerable = handleSchemaConformFilter(responseParam, apiInfoKey, vulnerable); 
        
        String apiSchema = filterCache.getApiSchema(apiCollectionId);

        if (apiSchema == null || apiSchema.isEmpty()) {
          continue;
        }

        vulnerable = RequestValidator.validate(responseParam, apiSchema, apiInfoKey.toString());
        hasPassedFilter = vulnerable != null && !vulnerable.isEmpty();

      }else {
        // Pass pre-matched template to avoid duplicate findMatchingUrlTemplate calls
        // (especially important for ParamEnumeration filter)
        hasPassedFilter = threatDetector.applyFilter(apiFilter, responseParam, rawApi, apiInfoKey, matchedTemplate);

        if (applyFilterLogCount.get() < MAX_APPLY_FILTER_LOGS || isDebugRequest(responseParam)) {
          logger.warnAndAddToDb("applyFilter - apiInfoKey: " + apiInfoKey.toString() +
                                ", filterId: " + apiFilter.getId() +
                                ", result: " + hasPassedFilter + 
                                ", statusCode " + responseParam.getStatusCode());
          applyFilterLogCount.incrementAndGet();
        }
      }

      // If filter matches, check ignore condition
      // If both filter AND ignore match, don't treat it as a threat (ignore wins)
      if (hasPassedFilter) {
        boolean shouldIgnore = threatDetector.shouldIgnoreApi(apiFilter, rawApi, apiInfoKey, actor);
        if (shouldIgnore) {
          logger.debugAndAddToDb("Filter matched but ignore condition also matched for url " + apiInfoKey.getUrl() + 
              " filterId " + apiFilter.getId() + " - skipping threat detection");
          continue; // Don't send as threat if ignore matches
        }
      }

      // If a request passes the filter and ignore doesn't match, then it's a malicious request,
      // and so we push it to kafka
      if (hasPassedFilter) {
        logger.debugAndAddToDb("filter condition satisfied for url " + apiInfoKey.getUrl() + " filterId " + apiFilter.getId());
        
        // Capture threat positions for LFI, OS Command Injection, and SSRF filters
        String filterId = apiFilter.getId();
        if (filterId.equals(ThreatDetector.LFI_FILTER_ID) || 
            filterId.equals(ThreatDetector.OS_COMMAND_INJECTION_FILTER_ID) || 
            filterId.equals(ThreatDetector.SSRF_FILTER_ID)) {

          List<SchemaConformanceError> threatPositions = threatDetector.getThreatPositions(filterId, responseParam);

          if (threatPositions != null && !threatPositions.isEmpty()) {
            // Initialize vulnerable list if null, or append to existing schema errors
            if (vulnerable == null) {
              vulnerable = new ArrayList<>();
            }
            vulnerable.addAll(threatPositions);
          }
        }
        
        // Later we will also add aggregation support
        RedactionType redactionType = Utils.getRedactionType(responseParam.getRequestParams().getHeaders(), dataActor);
        // Eg: 100 4xx requests in last 10 minutes.
        // But regardless of whether request falls in aggregation or not,
        // we still push malicious requests to kafka

        // todo: modify fetch yaml and read aggregate rules from it
        // List<Rule> rules = new ArrayList<>();
        // rules.add(new Rule("Lfi Rule 1", new Condition(10, 10)));
        AggregationRules aggRules = apiFilter.getAggregationRules();
        //aggRules.setRule(rules);

        boolean isAggFilter = aggRules != null && !aggRules.getRule().isEmpty();


        String groupKey = apiFilter.getId();
        String aggKey = actor + "|" + groupKey;


        SampleMaliciousRequest maliciousReq = null;
        if (!isAggFilter || !apiFilter.getInfo().getSubCategory().equalsIgnoreCase("API_LEVEL_RATE_LIMITING")) {
          maliciousReq = Utils.buildSampleMaliciousRequest(actor, responseParam, apiFilter, metadata, vulnerable, successfulExploit, isIgnoredEvent, redactionType);
        }

        if (!isAggFilter) {
          generateAndPushMaliciousEventRequest(
              apiFilter, actor, responseParam, maliciousReq, EventType.EVENT_TYPE_SINGLE);
          continue;
        }

        // Aggregation rules
        boolean shouldNotify = false;
        for (Rule rule : aggRules.getRule()) {
          if (apiFilter.getInfo().getSubCategory().equalsIgnoreCase("API_LEVEL_RATE_LIMITING")) {
              if (this.apiCountWindowBasedThresholdNotifier == null) {
                continue;
              }
              shouldNotify = this.apiCountWindowBasedThresholdNotifier.calcApiCount(apiHitCountKey, responseParam.getTime(), rule);
              if (shouldNotify) {
                maliciousReq = Utils.buildSampleMaliciousRequest(actor, responseParam, apiFilter, metadata, vulnerable, successfulExploit, isIgnoredEvent, redactionType);
              }
          } else {
              shouldNotify = this.windowBasedThresholdNotifier.shouldNotify(aggKey, maliciousReq, rule);
          }

          if (shouldNotify) {
            logger.debugAndAddToDb("aggregate condition satisfied for url " + apiInfoKey.getUrl() + " filterId " + apiFilter.getId());
            generateAndPushMaliciousEventRequest(
                apiFilter,
                actor,
                responseParam,
                maliciousReq,
                EventType.EVENT_TYPE_AGGREGATED);
          }
        }
      }
    }
    }
    }

  private void generateAndPushMaliciousEventRequest(
      FilterConfig apiFilter,
      String actor,
      HttpResponseParams responseParam,
      SampleMaliciousRequest maliciousReq,
      EventType eventType) {
    
    // Extract host from request headers
    String host = null;
    Map<String, List<String>> requestHeaders = responseParam.getRequestParams().getHeaders();
    if (requestHeaders != null && requestHeaders.containsKey("host") && !requestHeaders.get("host").isEmpty()) {
      host = requestHeaders.get("host").get(0);
    }

    // TODO: Extract sessionId from cookies or headers when needed
    String sessionId = "";

    String contextSourceValue = CONTEXT_SOURCE.API.name();
    
    MaliciousEventMessage maliciousEvent =
        MaliciousEventMessage.newBuilder()
            .setFilterId(apiFilter.getId())
            .setActor(actor)
            .setDetectedAt(responseParam.getTime())
            .setEventType(eventType)
            .setLatestApiCollectionId(maliciousReq.getApiCollectionId())
            .setLatestApiIp(maliciousReq.getIp())
            .setLatestApiPayload(maliciousReq.getPayload())
            .setLatestApiMethod(maliciousReq.getMethod())
            .setLatestApiEndpoint(maliciousReq.getUrl())
            .setDetectedAt(responseParam.getTime())
            .setCategory(apiFilter.getInfo().getCategory().getName())
            .setSubCategory(apiFilter.getInfo().getSubCategory())
            .setSeverity(apiFilter.getInfo().getSeverity())
            .setMetadata(maliciousReq.getMetadata())
            .setType("Rule-Based")
            .setSuccessfulExploit(maliciousReq.getSuccessfulExploit())
            .setStatus(maliciousReq.getStatus())
            .setHost(host != null ? host : "")
            .setContextSource(contextSourceValue)
            .setSessionId(sessionId != null ? sessionId : "")
            .build();
    MaliciousEventKafkaEnvelope envelope =
        MaliciousEventKafkaEnvelope.newBuilder()
            .setActor(actor)
            .setAccountId(responseParam.getAccountId())
            .setMaliciousEvent(maliciousEvent)
            .build();
    internalKafka.send(KafkaTopic.ThreatDetection.ALERTS, envelope);
  }

  public static HttpResponseParams buildHttpResponseParam(
      HttpResponseParam httpResponseParamProto) {

    String apiCollectionIdStr = httpResponseParamProto.getAktoVxlanId();
    int apiCollectionId = 0;

    Integer parsed = NumberUtils.createInteger(apiCollectionIdStr);
    apiCollectionId = parsed != null ? parsed : 0;

    String requestPayload =
        HttpRequestResponseUtils.rawToJsonString(httpResponseParamProto.getRequestPayload(), null);

    Map<String, List<String>> reqHeaders = (Map) httpResponseParamProto.getRequestHeadersMap();

    requestParams.resetValues(
            httpResponseParamProto.getMethod(),
            httpResponseParamProto.getPath(),
            httpResponseParamProto.getType(),
            reqHeaders,
            requestPayload,
            apiCollectionId
    );

    String responsePayload =
        HttpRequestResponseUtils.rawToJsonString(httpResponseParamProto.getResponsePayload(), null);

    String sourceStr = httpResponseParamProto.getSource();
    if (sourceStr == null || sourceStr.isEmpty()) {
      sourceStr = HttpResponseParams.Source.OTHER.name();
    }

    HttpResponseParams.Source source = HttpResponseParams.Source.valueOf(sourceStr);
    Map<String, List<String>> respHeaders = (Map) httpResponseParamProto.getResponseHeadersMap();
    lazyToString = httpResponseParamProto::toString;

    return responseParams.resetValues(
        httpResponseParamProto.getType(),
        httpResponseParamProto.getStatusCode(),
        httpResponseParamProto.getStatus(),
        respHeaders,
        responsePayload,
        requestParams,
        httpResponseParamProto.getTime(),
        httpResponseParamProto.getAktoAccountId(),
        httpResponseParamProto.getIsPending(),
        source,
        "",
        httpResponseParamProto.getIp(),
        httpResponseParamProto.getDestIp(),
        httpResponseParamProto.getDirection(),
        lazyToString);
  }


  

}

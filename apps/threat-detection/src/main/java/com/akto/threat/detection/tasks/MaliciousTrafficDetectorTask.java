package com.akto.threat.detection.tasks;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.akto.dto.*;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.enums.RedactionType;
import com.akto.threat.detection.cache.AccountConfig;
import com.akto.threat.detection.cache.AccountConfigurationCache;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import com.akto.IPLookupClient;
import com.akto.RawApiMetadataFactory;
import com.akto.dao.context.Context;
import com.akto.dao.monitoring.FilterYamlTemplateDao;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.api_protection_parse_layer.AggregationRules;
import com.akto.dto.api_protection_parse_layer.Rule;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.type.URLMethods;
import com.akto.hybrid_parsers.HttpCallParser;
import com.akto.kafka.KafkaConfig;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.proto.generated.threat_detection.message.malicious_event.event_type.v1.EventType;
import com.akto.proto.generated.threat_detection.message.malicious_event.v1.MaliciousEventKafkaEnvelope;
import com.akto.proto.generated.threat_detection.message.malicious_event.v1.MaliciousEventMessage;
import com.akto.proto.generated.threat_detection.message.sample_request.v1.SampleMaliciousRequest;
import com.akto.proto.generated.threat_detection.message.sample_request.v1.SchemaConformanceError;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.RatelimitConfig.RatelimitConfigItem;
import com.akto.proto.http_response_param.v1.HttpResponseParam;
import com.akto.proto.http_response_param.v1.StringList;
import com.akto.threat.detection.cache.ApiCountCacheLayer;
import com.akto.threat.detection.cache.RedisBackedCounterCache;
import com.akto.threat.detection.constants.KafkaTopic;
import com.akto.threat.detection.constants.RedisKeyInfo;
import com.akto.threat.detection.ip_api_counter.DistributionCalculator;
import com.akto.threat.detection.kafka.KafkaProtoProducer;
import com.akto.threat.detection.smart_event_detector.window_based.WindowBasedThresholdNotifier;
import com.akto.threat.detection.utils.ThreatDetector;
import com.akto.threat.detection.utils.Utils;
import com.akto.util.Constants;
import com.akto.util.HttpRequestResponseUtils;
import com.akto.utils.GzipUtils;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;



/*
Class is responsible for consuming traffic data from the Kafka topic.
Pass data through filters and identify malicious traffic.
 */
public class MaliciousTrafficDetectorTask implements Task {

  private ThreatConfigurationEvaluator threatConfigEvaluator;
  private final Consumer<String, byte[]> kafkaConsumer;
  private final KafkaConfig kafkaConfig;
  private final HttpCallParser httpCallParser;
  private final WindowBasedThresholdNotifier windowBasedThresholdNotifier;

  // Used for schema conformance and API level rate limiting
  private WindowBasedThresholdNotifier apiCountWindowBasedThresholdNotifier = null;
  private StatefulRedisConnection<String, String> apiCache = null;

  private final RawApiMetadataFactory rawApiFactory;

  private Map<String, FilterConfig> apiFilters;
  private int filterLastUpdatedAt = 0;
  private int filterUpdateIntervalSec = 900;

  private final KafkaProtoProducer internalKafka;

  private static final DataActor dataActor = DataActorFactory.fetchInstance();
  private static final LoggerMaker logger = new LoggerMaker(MaliciousTrafficDetectorTask.class, LogDb.THREAT_DETECTION);

  private static final HttpRequestParams requestParams = new HttpRequestParams();
  private static final HttpResponseParams responseParams = new HttpResponseParams();
  private static final FilterConfig ipApiRateLimitFilter = Utils.getipApiRateLimitFilter();
  private static Supplier<String> lazyToString;
  private DistributionCalculator distributionCalculator;
  private ThreatDetector threatDetector = new ThreatDetector();
  private boolean apiDistributionEnabled;
  private ApiCountCacheLayer apiCacheCountLayer;
  private static List<FilterConfig> successfulExploitFilters = new ArrayList<>();
  private static List<FilterConfig> ignoredEventFilters = new ArrayList<>();
  private final AtomicInteger applyFilterLogCount = new AtomicInteger(0);
  private static final int MAX_APPLY_FILTER_LOGS = 1000;

  // Kafka records per minute tracking
  private int recordsReadCount = 0;
  private long lastRecordCountLogTime = System.currentTimeMillis();

  // Valid hostname tracking (non-IP, not localhost, no port)
  private int validHostnameCount = 0;
  private long lastValidHostnameLogTime = System.currentTimeMillis();

    public MaliciousTrafficDetectorTask(
      KafkaConfig trafficConfig, KafkaConfig internalConfig, RedisClient redisClient, DistributionCalculator distributionCalculator, boolean apiDistributionEnabled) throws Exception {
    this.kafkaConfig = trafficConfig;

    Properties properties = new Properties();
    properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, trafficConfig.getBootstrapServers());
    properties.put(
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
        trafficConfig.getKeySerializer().getDeserializer());
    properties.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
        trafficConfig.getValueSerializer().getDeserializer());
    properties.put(
        ConsumerConfig.MAX_POLL_RECORDS_CONFIG,
        trafficConfig.getConsumerConfig().getMaxPollRecords());
    properties.put(ConsumerConfig.GROUP_ID_CONFIG, trafficConfig.getGroupId());
    properties.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, 100 * 1024 * 1024);
    properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    this.kafkaConsumer = new KafkaConsumer<>(properties);

    this.httpCallParser = new HttpCallParser(120, 1000);
    

    this.windowBasedThresholdNotifier =
        new WindowBasedThresholdNotifier(
            new RedisBackedCounterCache(redisClient, "wbt"),
            new WindowBasedThresholdNotifier.Config(100, 10 * 60));
    
    if (redisClient != null) {
        this.apiCache = redisClient.connect();
        this.apiCacheCountLayer = new ApiCountCacheLayer(redisClient);
        this.apiCountWindowBasedThresholdNotifier = new WindowBasedThresholdNotifier(
          apiCacheCountLayer,
          new WindowBasedThresholdNotifier.Config(100, 10 * 60));
    }

    this.threatConfigEvaluator = new ThreatConfigurationEvaluator(null, dataActor, apiCacheCountLayer);
    this.internalKafka = new KafkaProtoProducer(internalConfig);
    this.rawApiFactory = new RawApiMetadataFactory(new IPLookupClient());
    this.distributionCalculator = distributionCalculator;
    this.apiDistributionEnabled = apiDistributionEnabled;
  }

  private int MAX_KAFKA_DEBUG_MSGS = 100;
  public void run() {
    this.kafkaConsumer.subscribe(Collections.singletonList("akto.api.logs2"));
    ExecutorService pollingExecutor = Executors.newSingleThreadExecutor();
    pollingExecutor.execute(
        () -> {
          // Poll data from Kafka topic
          while (true) {
            ConsumerRecords<String, byte[]> records =
                kafkaConsumer.poll(
                    Duration.ofMillis(kafkaConfig.getConsumerConfig().getPollDurationMilli()));

            try {
              int recordCount = records.count();
              recordsReadCount += recordCount;

              // Log records per minute
              long currentTime = System.currentTimeMillis();
              long timeDiff = currentTime - lastRecordCountLogTime;
              if (timeDiff >= 60000) { // 60 seconds = 1 minute
                logger.warnAndAddToDb("Kafka records read in last minute: " + recordsReadCount +
                                      " (avg " + String.format("%.2f", recordsReadCount / (timeDiff / 1000.0)) + " records/sec)");
                recordsReadCount = 0;
                lastRecordCountLogTime = currentTime;
              }

              AccountConfig config = AccountConfigurationCache.getInstance().getConfig(dataActor);
              Context.accountId.set(config.getAccountId());
              Context.isRedactPayload.set(config.isRedacted());

              for (ConsumerRecord<String, byte[]> record : records) {
                HttpResponseParam httpResponseParam = HttpResponseParam.parseFrom(record.value());
                if(MAX_KAFKA_DEBUG_MSGS > 0){
                  MAX_KAFKA_DEBUG_MSGS--;
                  logger.infoAndAddToDb("Kafka record recieved " + httpResponseParam.toString());
                }
                if(ignoreTrafficFilter(httpResponseParam)){
                  continue;
                }
                processRecord(httpResponseParam);
              }

              if (!records.isEmpty()) {
                // Should we commit even if there are no records ?
                kafkaConsumer.commitSync();
              }
            } catch (Exception e) {
              logger.errorAndAddToDb("Error observed in processing record " + e.getMessage());
              e.printStackTrace();
            }
          }
        });
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

  private boolean isValidHostname(String hostname) {
    try {
      if (hostname == null || hostname.isEmpty()) {
        return false;
      }

      // Remove port if present (e.g., "example.com:8080" -> "example.com")
      String hostnameWithoutPort = hostname.split(":")[0];

      // Check if it's localhost
      if (hostnameWithoutPort.equalsIgnoreCase("localhost")) {
        return false;
      }

      // Simple check: if lowercase != uppercase, it has letters (valid hostname)
      // This elegantly filters out IP addresses (which are case-insensitive)
      // Examples:
      //   "192.168.1.1" -> toLowerCase() == toUpperCase() -> false (IP)
      //   "example.com" -> toLowerCase() != toUpperCase() -> true (valid hostname)
      //   "::1" -> toLowerCase() == toUpperCase() -> false (IPv6)
      return !hostnameWithoutPort.toLowerCase().equals(hostnameWithoutPort.toUpperCase());
    } catch (Exception e) {
      return false;
    }
  }

  private void trackAndLogValidHostname(HttpResponseParams responseParam) {
    Map<String, List<String>> headers = responseParam.getRequestParams().getHeaders();
    String hostname = null;
    if (headers != null && headers.containsKey("host") && !headers.get("host").isEmpty()) {
      hostname = headers.get("host").get(0);
    }
    if (isValidHostname(hostname)) {
      validHostnameCount++;
    }

    // Log valid hostname count every minute
    long currentTime = System.currentTimeMillis();
    long validHostnameTimeDiff = currentTime - lastValidHostnameLogTime;
    if (validHostnameTimeDiff >= 60000) { // 60 seconds = 1 minute
      logger.warnAndAddToDb("Valid hostnames in last minute: " + validHostnameCount +
                            " (non-IP, not localhost, no port) in " + String.format("%.2f", validHostnameTimeDiff / 1000.0) + " seconds");
      validHostnameCount = 0;
      lastValidHostnameLogTime = currentTime;
    }
  }

  private Map<String, FilterConfig> getFilters() {
    int now = (int) (System.currentTimeMillis() / 1000);
    if (now - filterLastUpdatedAt < filterUpdateIntervalSec) {
      return apiFilters;
    }

    List<YamlTemplate> templates = dataActor.fetchFilterYamlTemplates();
    apiFilters = FilterYamlTemplateDao.fetchFilterConfig(false, templates, false);
    logger.debugAndAddToDb("total filters fetched  " + apiFilters.size());
    this.filterLastUpdatedAt = now;


    // Extract successful exploit filters
    successfulExploitFilters.clear();
    // Extract ignored event filters
    ignoredEventFilters.clear();

    Iterator<Map.Entry<String, FilterConfig>> iterator = apiFilters.entrySet().iterator();
    while (iterator.hasNext()) {
        Map.Entry<String, FilterConfig> entry = iterator.next();
        FilterConfig filter = entry.getValue();
        if (filter.getInfo() != null && filter.getInfo().getCategory() != null) {
            String categoryName = filter.getInfo().getCategory().getName();
            if (Constants.THREAT_PROTECTION_SUCCESSFUL_EXPLOIT_CATEGORY.equalsIgnoreCase(categoryName)) {
                successfulExploitFilters.add(filter);
                iterator.remove();
            } else if (Constants.THREAT_PROTECTION_IGNORED_EVENTS_CATEGORY.equalsIgnoreCase(categoryName)) {
                ignoredEventFilters.add(filter);
                iterator.remove();
            }
        }
    }
    return apiFilters;
  }

  private String getApiSchema(int apiCollectionId) {
    String apiSchema = null;
    try {
      apiSchema = this.apiCache.sync().get(Constants.AKTO_THREAT_DETECTION_CACHE_PREFIX + apiCollectionId);

      if (apiSchema != null && !apiSchema.isEmpty()) {
        apiSchema = GzipUtils.unzipString(apiSchema);

        return apiSchema;
      }

      apiSchema = dataActor.fetchOpenApiSchema(apiCollectionId);

      if (apiSchema == null || apiSchema.isEmpty()) {
        logger.warnAndAddToDb("No schema found for api collection id: "+ apiCollectionId);

        return null;
      }
      this.apiCache.sync().setex(Constants.AKTO_THREAT_DETECTION_CACHE_PREFIX + apiCollectionId, Constants.ONE_DAY_TIMESTAMP, apiSchema);
      // unzip this schema using gzip
      apiSchema = GzipUtils.unzipString(apiSchema);
    } catch (Exception e) {
      logger.errorAndAddToDb(e, "Error while fetching api schema for collectionId: "+ apiCollectionId);
    }
    return apiSchema;
  }

  private void processRecord(HttpResponseParam record) throws Exception {
    HttpResponseParams responseParam = buildHttpResponseParam(record);
    String actor = this.threatConfigEvaluator.getActorId(responseParam);

    if (actor == null || actor.isEmpty()) {
      logger.warnAndAddToDb("Dropping processing of record with no actor IP, account: " + responseParam.getAccountId());
      return;
    }
    Map<String, FilterConfig> filters = this.getFilters();
    if (filters.isEmpty()) {
      logger.warnAndAddToDb("No filters found for account " + responseParam.getAccountId());
      return;
    }

    RawApi rawApi = RawApi.buildFromMessageNew(responseParam);
    RawApiMetadata metadata = this.rawApiFactory.buildFromHttp(rawApi.getRequest(), rawApi.getResponse());
    rawApi.setRawApiMetdata(metadata);

    int apiCollectionId = httpCallParser.createApiCollectionId(responseParam);
    responseParam.requestParams.setApiCollectionId(apiCollectionId);

    String url = responseParam.getRequestParams().getURL();
    URLMethods.Method method =
        URLMethods.Method.fromString(responseParam.getRequestParams().getMethod());
    ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(apiCollectionId, url, method);

    String apiHitCountKey = Utils.buildApiHitCountKey(apiCollectionId, url, method.toString());
    if (this.apiCountWindowBasedThresholdNotifier != null) {
        this.apiCountWindowBasedThresholdNotifier.incrementApiHitcount(apiHitCountKey, responseParam.getTime(), RedisKeyInfo.API_COUNTER_SORTED_SET);
    }

    List<SchemaConformanceError> errors = null; 


    // Check SuccessfulExploit category filters
    boolean successfulExploit = false; 
    if (!successfulExploitFilters.isEmpty()) {
      successfulExploit = threatDetector.isSuccessfulExploit(successfulExploitFilters, rawApi, apiInfoKey);
    }
    // Check IgnoredEvents category filters
    boolean isIgnoredEvent = false;
    if (!ignoredEventFilters.isEmpty()) {
      isIgnoredEvent = threatDetector.isIgnoredEvent(ignoredEventFilters, rawApi, apiInfoKey);
    }
    if (apiDistributionEnabled && apiCollectionId != 0) {
      String apiCollectionIdStr = Integer.toString(apiCollectionId);
      String distributionKey = Utils.buildApiDistributionKey(apiCollectionIdStr, url, method.toString());
      String ipApiCmsKey = Utils.buildIpApiCmsDataKey(actor, apiCollectionIdStr, url, method.toString());
      long curEpochMin = responseParam.getTime() / 60;
      this.distributionCalculator.updateFrequencyBuckets(distributionKey, curEpochMin, ipApiCmsKey);

      // Check and raise alert for RateLimits
      RatelimitConfigItem ratelimitConfig = this.threatConfigEvaluator.getDefaultRateLimitConfig();
      long ratelimit = this.threatConfigEvaluator.getRatelimit(apiInfoKey);

      long count = this.distributionCalculator.getSlidingWindowCount(ipApiCmsKey, curEpochMin,
          ratelimitConfig.getPeriod());

      if (ratelimit != Constants.RATE_LIMIT_UNLIMITED_REQUESTS && count > ratelimit
          && !this.threatConfigEvaluator.isActorInMitigationPeriod(ipApiCmsKey, ratelimitConfig)) {
        logger.debugAndAddToDb("Ratelimit hit for url " + apiInfoKey.getUrl() + " actor: " + actor + " ratelimitConfig "
            + ratelimitConfig.toString());

        RedactionType redactionType = getRedactionType(responseParam.getRequestParams().getHeaders());
        // Send event to BE.
        SampleMaliciousRequest maliciousReq = Utils.buildSampleMaliciousRequest(actor, responseParam,
            ipApiRateLimitFilter, metadata, errors, successfulExploit, isIgnoredEvent, redactionType);
        generateAndPushMaliciousEventRequest(ipApiRateLimitFilter, actor, responseParam, maliciousReq,
            EventType.EVENT_TYPE_AGGREGATED);

        // cool-off sending to BE till mitigationPeriod is over
        this.threatConfigEvaluator.setActorInMitigationPeriod(ipApiCmsKey, ratelimitConfig);
      }
    }

    for (FilterConfig apiFilter : apiFilters.values()) {
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
      if(false && apiFilter.getInfo().getCategory().getName().equalsIgnoreCase("SchemaConform")) {
        logger.debug("SchemaConform filter found for url {} filterId {}", apiInfoKey.getUrl(), apiFilter.getId());
        String apiSchema = getApiSchema(apiCollectionId);

        if (apiSchema == null || apiSchema.isEmpty()) {

          continue;

        }

        vulnerable = RequestValidator.validate(responseParam, apiSchema, apiInfoKey.toString());
        hasPassedFilter = vulnerable != null && !vulnerable.isEmpty();

      }else {
        hasPassedFilter = threatDetector.applyFilter(apiFilter, responseParam, rawApi, apiInfoKey);

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
        boolean shouldIgnore = threatDetector.shouldIgnoreApi(apiFilter, rawApi, apiInfoKey);
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
        RedactionType redactionType = getRedactionType(responseParam.getRequestParams().getHeaders());
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


  

  /**
   * Determines the redaction type based on account settings and host information.
   *
   * @param headers Request headers map
   * @return RedactionType indicating the level of redaction to apply
   */
  private RedactionType getRedactionType(Map<String, List<String>> headers) {
    try {
      if (Context.isRedactPayload.get() != null && Context.isRedactPayload.get()) {
        return RedactionType.REDACT_ALL;
      }
      // Extract host from headers
      String host = extractHostFromHeaders(headers);
      if (host != null && !host.isEmpty()) {
        int hostHashCode = host.hashCode();
          AccountConfig config = AccountConfigurationCache.getInstance().getConfig(dataActor);
          Boolean isApiCollectionRedacted = config.isApiCollectionRedacted(hostHashCode);
          if(isApiCollectionRedacted != null && isApiCollectionRedacted){
              return RedactionType.REDACT_BY_API_COLLECTION;
          }
      }
      if(SingleTypeInfo.isCustomDataTypeAvailable(Context.accountId.get())){
          return RedactionType.REDACT_BY_CUSTOM_FIELD;
      }
    return  RedactionType.NONE;

    } catch (Exception e) {
      logger.errorAndAddToDb(e, "Error determining redaction type, defaulting to ALL");
      return RedactionType.NONE;
    }
  }

  /**
   * Extracts host value from request headers.
   * Checks in order: host, :authority, authority
   *
   * @param headers Request headers map
   * @return Host value or null if not found
   */
  private String extractHostFromHeaders(Map<String, List<String>> headers) {
    if (headers == null || headers.isEmpty()) {
      return null;
    }
    List<String> hostValues = headers.get("host");
    if (hostValues != null && !hostValues.isEmpty()) {
      return hostValues.get(0);
    }
    return null;
  }
}

package com.akto.threat.detection.tasks;

import com.akto.dao.context.Context;
import com.akto.dao.monitoring.FilterYamlTemplateDao;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.ApiInfo;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.RawApi;
import com.akto.dto.api_protection_parse_layer.AggregationRules;
import com.akto.dto.api_protection_parse_layer.Condition;
import com.akto.dto.api_protection_parse_layer.Rule;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.type.URLMethods;
import com.akto.hybrid_parsers.HttpCallParser;
import com.akto.kafka.KafkaConfig;
import com.akto.proto.generated.threat_detection.message.malicious_event.event_type.v1.EventType;
import com.akto.proto.generated.threat_detection.message.malicious_event.v1.MaliciousEventKafkaEnvelope;
import com.akto.proto.generated.threat_detection.message.malicious_event.v1.MaliciousEventMessage;
import com.akto.proto.generated.threat_detection.message.sample_request.v1.SampleMaliciousRequest;
import com.akto.proto.generated.threat_detection.message.sample_request.v1.SampleRequestKafkaEnvelope;
import com.akto.proto.http_response_param.v1.HttpResponseParam;
import com.akto.rules.TestPlugin;
import com.akto.test_editor.execution.VariableResolver;
import com.akto.test_editor.filter.data_operands_impl.ValidationResult;
import com.akto.threat.detection.actor.SourceIPActorGenerator;
import com.akto.threat.detection.cache.RedisCounterCache;
import com.akto.threat.detection.constants.KafkaTopic;
import com.akto.threat.detection.kafka.KafkaProtoProducer;
import com.akto.threat.detection.smart_event_detector.window_based.WindowBasedThresholdNotifier;
import com.akto.util.HttpRequestResponseUtils;
import io.lettuce.core.RedisClient;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.kafka.clients.consumer.*;

/*
Class is responsible for consuming traffic data from the Kafka topic.
Pass data through filters and identify malicious traffic.
 */
public class MaliciousTrafficDetectorTask implements Task {

  private final Consumer<String, byte[]> kafkaConsumer;
  private final KafkaConfig kafkaConfig;
  private final HttpCallParser httpCallParser;
  private final WindowBasedThresholdNotifier windowBasedThresholdNotifier;

  private Map<String, FilterConfig> apiFilters;
  private int filterLastUpdatedAt = 0;
  private int filterUpdateIntervalSec = 900;

  private final KafkaProtoProducer internalKafka;

  private static final DataActor dataActor = DataActorFactory.fetchInstance();

  public MaliciousTrafficDetectorTask(
      KafkaConfig trafficConfig, KafkaConfig internalConfig, RedisClient redisClient) {
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
    properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    this.kafkaConsumer = new KafkaConsumer<>(properties);

    this.httpCallParser = new HttpCallParser(120, 1000);

    this.windowBasedThresholdNotifier =
        new WindowBasedThresholdNotifier(
            new RedisCounterCache(redisClient, "wbt"),
            new WindowBasedThresholdNotifier.Config(100, 10 * 60));

    this.internalKafka = new KafkaProtoProducer(internalConfig);
  }

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
              for (ConsumerRecord<String, byte[]> record : records) {
                HttpResponseParam httpResponseParam = HttpResponseParam.parseFrom(record.value());
                processRecord(httpResponseParam);
              }

              if (!records.isEmpty()) {
                // Should we commit even if there are no records ?
                kafkaConsumer.commitSync();
              }
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
        });
  }

  private Map<String, FilterConfig> getFilters() {
    int now = (int) (System.currentTimeMillis() / 1000);
    if (now - filterLastUpdatedAt < filterUpdateIntervalSec) {
      return apiFilters;
    }

    List<YamlTemplate> templates = dataActor.fetchFilterYamlTemplates();
    apiFilters = FilterYamlTemplateDao.fetchFilterConfig(false, templates, false);
    this.filterLastUpdatedAt = now;
    return apiFilters;
  }

  private boolean validateFilterForRequest(
      FilterConfig apiFilter, RawApi rawApi, ApiInfo.ApiInfoKey apiInfoKey, String message) {
    try {
      Map<String, Object> varMap = apiFilter.resolveVarMap();
      VariableResolver.resolveWordList(
          varMap,
          new HashMap<ApiInfo.ApiInfoKey, List<String>>() {
            {
              put(apiInfoKey, Collections.singletonList(message));
            }
          },
          apiInfoKey);

      String filterExecutionLogId = UUID.randomUUID().toString();
      ValidationResult res =
          TestPlugin.validateFilter(
              apiFilter.getFilter().getNode(), rawApi, apiInfoKey, varMap, filterExecutionLogId);

      return res.getIsValid();
    } catch (Exception e) {
      e.printStackTrace();
    }

    return false;
  }

  private void processRecord(HttpResponseParam record) throws Exception {
    HttpResponseParams responseParam = buildHttpResponseParam(record);

    Context.accountId.set(Integer.parseInt(responseParam.getAccountId()));
    Map<String, FilterConfig> filters = this.getFilters();
    if (filters.isEmpty()) {
      return;
    }

    List<SampleRequestKafkaEnvelope> maliciousMessages = new ArrayList<>();

    String message = responseParam.getOrig();
    RawApi rawApi = RawApi.buildFromMessageNew(responseParam);
    int apiCollectionId = httpCallParser.createApiCollectionId(responseParam);
    responseParam.requestParams.setApiCollectionId(apiCollectionId);
    String url = responseParam.getRequestParams().getURL();
    URLMethods.Method method =
        URLMethods.Method.fromString(responseParam.getRequestParams().getMethod());
    ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(apiCollectionId, url, method);

    for (FilterConfig apiFilter : apiFilters.values()) {
      boolean hasPassedFilter = validateFilterForRequest(apiFilter, rawApi, apiInfoKey, message);

      // If a request passes any of the filter, then it's a malicious request,
      // and so we push it to kafka
      if (hasPassedFilter) {
        // Later we will also add aggregation support
        // Eg: 100 4xx requests in last 10 minutes.
        // But regardless of whether request falls in aggregation or not,
        // we still push malicious requests to kafka

        // todo: modify fetch yaml and read aggregate rules from it
        // List<Rule> rules = new ArrayList<>();
        // rules.add(new Rule("Lfi Rule 1", new Condition(10, 10)));
        AggregationRules aggRules = apiFilter.getAggregationRules();
        //aggRules.setRule(rules);

        boolean isAggFilter = aggRules != null && !aggRules.getRule().isEmpty();

        SourceIPActorGenerator.instance
            .generate(responseParam)
            .ifPresent(
                actor -> {
                  String groupKey = apiFilter.getId();
                  String aggKey = actor + "|" + groupKey;

                  SampleMaliciousRequest maliciousReq =
                      SampleMaliciousRequest.newBuilder()
                          .setUrl(responseParam.getRequestParams().getURL())
                          .setMethod(responseParam.getRequestParams().getMethod())
                          .setPayload(responseParam.getOrig())
                          .setIp(actor) // For now using actor as IP
                          .setApiCollectionId(responseParam.getRequestParams().getApiCollectionId())
                          .setTimestamp(responseParam.getTime())
                          .setFilterId(apiFilter.getId())
                          .build();

                  maliciousMessages.add(
                      SampleRequestKafkaEnvelope.newBuilder()
                          .setActor(actor)
                          .setAccountId(responseParam.getAccountId())
                          .setMaliciousRequest(maliciousReq)
                          .build());

                  if (!isAggFilter) {
                    generateAndPushMaliciousEventRequest(
                        apiFilter, actor, responseParam, maliciousReq, EventType.EVENT_TYPE_SINGLE);
                    return;
                  }

                  // Aggregation rules
                  for (Rule rule : aggRules.getRule()) {
                    WindowBasedThresholdNotifier.Result result =
                        this.windowBasedThresholdNotifier.shouldNotify(aggKey, maliciousReq, rule);

                    if (result.shouldNotify()) {
                      generateAndPushMaliciousEventRequest(
                          apiFilter,
                          actor,
                          responseParam,
                          maliciousReq,
                          EventType.EVENT_TYPE_AGGREGATED);
                    }
                  }
                });
      }
    }

    // Should we push all the messages in one go
    // or call kafka.send for each HttpRequestParams
    try {
      maliciousMessages.forEach(
          sample -> {
            internalKafka.send(KafkaTopic.ThreatDetection.MALICIOUS_EVENTS, sample);
          });
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void generateAndPushMaliciousEventRequest(
      FilterConfig apiFilter,
      String actor,
      HttpResponseParams responseParam,
      SampleMaliciousRequest maliciousReq,
      EventType eventType) {
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
            .setDetectedAt(responseParam.getTime())
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
    if (NumberUtils.isDigits(apiCollectionIdStr)) {
      apiCollectionId = NumberUtils.toInt(apiCollectionIdStr, 0);
    }

    String requestPayload =
        HttpRequestResponseUtils.rawToJsonString(httpResponseParamProto.getRequestPayload(), null);

    Map<String, List<String>> reqHeaders = (Map) httpResponseParamProto.getRequestHeadersMap();

    // for (Map.Entry<String, StringList> entry :
    // httpResponseParamProto.getRequestHeadersMap().entrySet()) {
    //     ArrayList<String> list = new ArrayList<>(entry.getValue().getValuesList());
    //     reqHeaders.put(entry.getKey(), list);
    // }

    HttpRequestParams requestParams =
        new HttpRequestParams(
            httpResponseParamProto.getMethod(),
            httpResponseParamProto.getPath(),
            httpResponseParamProto.getType(),
            reqHeaders,
            requestPayload,
            apiCollectionId);

    String responsePayload =
        HttpRequestResponseUtils.rawToJsonString(httpResponseParamProto.getResponsePayload(), null);

    String sourceStr = httpResponseParamProto.getSource();
    if (sourceStr == null || sourceStr == "") {
      sourceStr = HttpResponseParams.Source.OTHER.name();
    }

    HttpResponseParams.Source source = HttpResponseParams.Source.valueOf(sourceStr);
    Map<String, List<String>> respHeaders = (Map) httpResponseParamProto.getResponseHeadersMap();

    // for (Map.Entry<String, StringList> entry :
    // httpResponseParamProto.getResponseHeadersMap().entrySet()) {
    //   ArrayList<String> list = new ArrayList<>(entry.getValue().getValuesList());
    //   respHeaders.put(entry.getKey(), list);
    // }

    return new HttpResponseParams(
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
        httpResponseParamProto.getDirection());
  }
}

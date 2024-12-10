package com.akto.threat.detection.tasks;

import com.akto.dao.context.Context;
import com.akto.threat.detection.actor.SourceIPActorGenerator;
import com.akto.threat.detection.cache.RedisBackedCounterCache;
import com.akto.threat.detection.config.kafka.KafkaConfig;
import com.akto.dao.monitoring.FilterYamlTemplateDao;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.ApiInfo;
import com.akto.dto.HttpResponseParams;
import com.akto.threat.detection.dto.MessageEnvelope;
import com.akto.dto.RawApi;
import com.akto.dto.api_protection_parse_layer.AggregationRules;
import com.akto.dto.api_protection_parse_layer.Condition;
import com.akto.dto.api_protection_parse_layer.Rule;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.type.URLMethods;
import com.akto.hybrid_parsers.HttpCallParser;
import com.akto.kafka.Kafka;
import com.akto.proto.threat_protection.consumer_service.v1.*;
import com.akto.rules.TestPlugin;
import com.akto.runtime.utils.Utils;
import com.akto.threat.detection.smart_event_detector.window_based.WindowBasedThresholdNotifier;
import com.akto.test_editor.execution.VariableResolver;
import com.akto.test_editor.filter.data_operands_impl.ValidationResult;
import com.google.protobuf.InvalidProtocolBufferException;
import io.lettuce.core.RedisClient;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/*
Class is responsible for consuming traffic data from the Kafka topic.
Pass data through filters and identify malicious traffic.
 */
public class MaliciousTrafficDetectorTask {

  private static final ExecutorService pollingExecutor = Executors.newSingleThreadExecutor();
  private final Consumer<String, String> kafkaConsumer;
  private final KafkaConfig kafkaConfig;
  private final HttpCallParser httpCallParser;
  private final WindowBasedThresholdNotifier windowBasedThresholdNotifier;

  private Map<String, FilterConfig> apiFilters;
  private int filterLastUpdatedAt = 0;

  private final Kafka internalKafka;

  private static final String KAFKA_MALICIOUS_TOPIC = "akto.malicious";
  private static final String KAFKA_SMART_EVENT_TOPIC = "akto.smart_event";

  private static final DataActor dataActor = DataActorFactory.fetchInstance();

  public MaliciousTrafficDetectorTask(
      KafkaConfig trafficConfig, KafkaConfig internalConfig, RedisClient redisClient) {
    this.kafkaConfig = trafficConfig;

    String kafkaBrokerUrl = trafficConfig.getBootstrapServers();
    String groupId = trafficConfig.getGroupId();

    this.kafkaConsumer =
        new KafkaConsumer<>(
            Utils.configProperties(
                kafkaBrokerUrl, groupId, trafficConfig.getConsumerConfig().getMaxPollRecords()));

    this.httpCallParser = new HttpCallParser(120, 1000);

    this.windowBasedThresholdNotifier =
        new WindowBasedThresholdNotifier(
            new RedisBackedCounterCache(redisClient, "wbt"),
            new WindowBasedThresholdNotifier.Config(100, 10 * 60));

    this.internalKafka =
        new Kafka(
            internalConfig.getBootstrapServers(),
            internalConfig.getProducerConfig().getLingerMs(),
            internalConfig.getProducerConfig().getBatchSize());
  }

  public void run() {
    this.kafkaConsumer.subscribe(Collections.singletonList("akto.api.logs"));
    pollingExecutor.execute(
        () -> {
          // Poll data from Kafka topic
          while (true) {
            ConsumerRecords<String, String> records =
                kafkaConsumer.poll(
                    Duration.ofMillis(kafkaConfig.getConsumerConfig().getPollDurationMilli()));
            for (ConsumerRecord<String, String> record : records) {
              processRecord(record);
            }
          }
        });
  }

  private Map<String, FilterConfig> getFilters() {
    int now = (int) (System.currentTimeMillis() / 1000);
    if (now - filterLastUpdatedAt < 60) {
      return apiFilters;
    }

    List<YamlTemplate> templates = dataActor.fetchFilterYamlTemplates();
    apiFilters = FilterYamlTemplateDao.fetchFilterConfig(false, templates, false);
    this.filterLastUpdatedAt = now;
    return apiFilters;
  }

  private boolean validateFilterForRequest(
      HttpResponseParams responseParam, FilterConfig apiFilter) {
    try {
      String message = responseParam.getOrig();
      RawApi rawApi = RawApi.buildFromMessage(message);
      int apiCollectionId = httpCallParser.createApiCollectionId(responseParam);
      responseParam.requestParams.setApiCollectionId(apiCollectionId);
      String url = responseParam.getRequestParams().getURL();
      URLMethods.Method method =
          URLMethods.Method.fromString(responseParam.getRequestParams().getMethod());
      ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(apiCollectionId, url, method);
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

  private void processRecord(ConsumerRecord<String, String> record) {
    HttpResponseParams responseParam = HttpCallParser.parseKafkaMessage(record.value());
    Context.accountId.set(Integer.parseInt(responseParam.getAccountId()));
    Map<String, FilterConfig> filters = this.getFilters();
    if (filters.isEmpty()) {
      return;
    }

    List<MessageEnvelope> maliciousMessages = new ArrayList<>();

    for (FilterConfig apiFilter : apiFilters.values()) {
      boolean hasPassedFilter = validateFilterForRequest(responseParam, apiFilter);

      // If a request passes any of the filter, then it's a malicious request,
      // and so we push it to kafka
      if (hasPassedFilter) {
        // Later we will also add aggregation support
        // Eg: 100 4xx requests in last 10 minutes.
        // But regardless of whether request falls in aggregation or not,
        // we still push malicious requests to kafka

        // todo: modify fetch yaml and read aggregate rules from it
        List<Rule> rules = new ArrayList<>();
        rules.add(new Rule("Lfi Rule 1", new Condition(100, 10)));
        AggregationRules aggRules = new AggregationRules();
        aggRules.setRule(rules);

        SourceIPActorGenerator.instance
            .generate(responseParam)
            .ifPresent(
                actor -> {
                  String groupKey = apiFilter.getId();
                  String aggKey = actor + "|" + groupKey;

                  MaliciousEvent maliciousEvent =
                      MaliciousEvent.newBuilder()
                          .setActorId(actor)
                          .setFilterId(apiFilter.getId())
                          .setUrl(responseParam.getRequestParams().getURL())
                          .setMethod(responseParam.getRequestParams().getMethod())
                          .setPayload(responseParam.getOrig())
                          .setIp(actor) // For now using actor as IP
                          .setApiCollectionId(responseParam.getRequestParams().getApiCollectionId())
                          .setTimestamp(responseParam.getTime())
                          .build();

                  try {
                    maliciousMessages.add(
                        MessageEnvelope.generateEnvelope(
                            responseParam.getAccountId(), maliciousEvent));
                  } catch (InvalidProtocolBufferException e) {
                    return;
                  }

                  for (Rule rule : aggRules.getRule()) {
                    WindowBasedThresholdNotifier.Result result =
                        this.windowBasedThresholdNotifier.shouldNotify(
                            aggKey, maliciousEvent, rule);

                    if (result.shouldNotify()) {
                      SmartEvent smartEvent =
                          SmartEvent.newBuilder()
                              .setFilterId(apiFilter.getId())
                              .setActorId(actor)
                              .setDetectedAt(responseParam.getTime())
                              .setRuleId(rule.getName())
                              .build();
                      try {
                        MessageEnvelope.generateEnvelope(responseParam.getAccountId(), smartEvent)
                            .marshal()
                            .ifPresent(
                                data -> {
                                  internalKafka.send(data, KAFKA_SMART_EVENT_TOPIC);
                                });
                      } catch (InvalidProtocolBufferException e) {
                        e.printStackTrace();
                      }
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
            sample
                .marshal()
                .ifPresent(
                    data -> {
                      internalKafka.send(data, KAFKA_MALICIOUS_TOPIC);
                    });
          });
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}

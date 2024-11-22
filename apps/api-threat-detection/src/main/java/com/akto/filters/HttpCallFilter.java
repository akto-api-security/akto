package com.akto.filters;

import java.util.*;

import com.akto.cache.RedisBackedCounterCache;
import com.akto.dao.context.Context;
import com.akto.dao.monitoring.FilterYamlTemplateDao;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.api_protection_parse_layer.AggregationRules;
import com.akto.dto.api_protection_parse_layer.Condition;
import com.akto.dto.api_protection_parse_layer.Rule;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.RawApi;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.type.URLMethods.Method;
import com.akto.filters.aggregators.key_generator.SourceIPKeyGenerator;
import com.akto.filters.aggregators.window_based.WindowBasedThresholdNotifier;
import com.akto.hybrid_parsers.HttpCallParser;
import com.akto.kafka.Kafka;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.proto.threat_protection.consumer_service.v1.ConsumerServiceGrpc.ConsumerServiceBlockingStub;
import com.akto.proto.threat_protection.consumer_service.v1.ConsumerServiceGrpc;
import com.akto.proto.threat_protection.consumer_service.v1.MaliciousEvent;
import com.akto.proto.threat_protection.consumer_service.v1.SaveSmartEventRequest;
import com.akto.proto.threat_protection.consumer_service.v1.SmartEvent;
import com.akto.rules.TestPlugin;
import com.akto.suspect_data.Message;
import com.akto.test_editor.execution.VariableResolver;
import com.akto.test_editor.filter.data_operands_impl.ValidationResult;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;

import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.lettuce.core.RedisClient;

public class HttpCallFilter {
    private static final LoggerMaker loggerMaker = new LoggerMaker(HttpCallFilter.class, LogDb.THREAT_DETECTION);

    private Map<String, FilterConfig> apiFilters;
    private final HttpCallParser httpCallParser;
    private final Kafka kafka;

    private static final int KAFKA_BATCH_SIZE = 1000;
    private static final int KAFKA_BATCH_LINGER_MS = 1000;
    private static final String KAFKA_MALICIOUS_TOPIC = "akto.malicious";

    private static final int FILTER_REFRESH_INTERVAL = 10 * 60;
    private int lastFilterFetch;

    private static final DataActor dataActor = DataActorFactory.fetchInstance();

    private final WindowBasedThresholdNotifier windowBasedThresholdNotifier;

    private final ConsumerServiceBlockingStub consumerServiceBlockingStub;

    public HttpCallFilter(
            RedisClient redisClient, int sync_threshold_count, int sync_threshold_time) {
        this.apiFilters = new HashMap<>();
        this.lastFilterFetch = 0;
        this.httpCallParser = new HttpCallParser(sync_threshold_count, sync_threshold_time);

        String kafkaBrokerUrl = System.getenv("AKTO_KAFKA_BROKER_URL");
        this.kafka = new Kafka(kafkaBrokerUrl, KAFKA_BATCH_LINGER_MS, KAFKA_BATCH_SIZE);
        this.windowBasedThresholdNotifier = new WindowBasedThresholdNotifier(
                new RedisBackedCounterCache(redisClient, "wbt"),
                new WindowBasedThresholdNotifier.Config(100, 10 * 60));

        String target = "localhost:8980";
        ManagedChannel channel = Grpc.newChannelBuilder(target, InsecureChannelCredentials.create())
                .build();
        this.consumerServiceBlockingStub = ConsumerServiceGrpc.newBlockingStub(channel);
    }

    public void filterFunction(List<HttpResponseParams> responseParams) {

        int now = Context.now();
        if ((lastFilterFetch + FILTER_REFRESH_INTERVAL) < now) {
            // TODO: add support for only active templates.
            List<YamlTemplate> templates = dataActor.fetchFilterYamlTemplates();
            apiFilters = FilterYamlTemplateDao.fetchFilterConfig(false, templates, false);
            lastFilterFetch = now;
        }

        if (apiFilters == null || apiFilters.isEmpty()) {
            return;
        }

        List<Message> maliciousSamples = new ArrayList<>();
        for (HttpResponseParams responseParam : responseParams) {
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

                    SourceIPKeyGenerator.instance
                            .generate(responseParam)
                            .ifPresent(
                                    actor -> {
                                        String groupKey = apiFilter.getId();
                                        String aggKey = actor + "|" + groupKey;

                                        MaliciousEvent maliciousEvent = MaliciousEvent.newBuilder().setActorId(actor)
                                                .setFilterId(apiFilter.getId())
                                                .setUrl(responseParam.getRequestParams().getURL())
                                                .setMethod(responseParam.getRequestParams().getMethod())
                                                .setPayload(responseParam.getOrig())
                                                .setIp(actor) // For now using actor as IP
                                                .setApiCollectionId(
                                                        responseParam.getRequestParams().getApiCollectionId())
                                                .setTimestamp(responseParam.getTime())
                                                .build();

                                        maliciousSamples.add(
                                                new Message(
                                                        responseParam.getAccountId(),
                                                        maliciousEvent));
                                        
                                        for (Rule rule: aggRules.getRule()) {
                                            WindowBasedThresholdNotifier.Result result = this.windowBasedThresholdNotifier
                                                    .shouldNotify(
                                                            aggKey, maliciousEvent, rule);

                                            if (result.shouldNotify()) {
                                                this.consumerServiceBlockingStub.saveSmartEvent(
                                                        SaveSmartEventRequest
                                                                .newBuilder()
                                                                .setAccountId(
                                                                        Integer.parseInt(responseParam.getAccountId()))
                                                                .setEvent(
                                                                        SmartEvent.newBuilder()
                                                                                .setFilterId(apiFilter.getId())
                                                                                .setActorId(actor)
                                                                                .setDetectedAt(responseParam.getTime())
                                                                                .setRuleId(actor)
                                                                                .build())
                                                                .build());
                                            }
                                        }
                                        });
                                    
                }
            }
        }

        // Should we push all the messages in one go
        // or call kafka.send for each HttpRequestParams
        try {
            maliciousSamples.forEach(
                    sample -> {
                        try {
                            String data = JsonFormat.printer().print(null);
                            kafka.send(data, KAFKA_MALICIOUS_TOPIC);
                        } catch (InvalidProtocolBufferException e) {
                            e.printStackTrace();
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean validateFilterForRequest(
            HttpResponseParams responseParam, FilterConfig apiFilter) {
        try {
            String message = responseParam.getOrig();
            RawApi rawApi = RawApi.buildFromMessage(message);
            int apiCollectionId = httpCallParser.createApiCollectionId(responseParam);
            responseParam.requestParams.setApiCollectionId(apiCollectionId);
            String url = responseParam.getRequestParams().getURL();
            Method method = Method.fromString(responseParam.getRequestParams().getMethod());
            ApiInfoKey apiInfoKey = new ApiInfoKey(apiCollectionId, url, method);
            Map<String, Object> varMap = apiFilter.resolveVarMap();
            VariableResolver.resolveWordList(
                    varMap,
                    new HashMap<ApiInfoKey, List<String>>() {
                        {
                            put(apiInfoKey, Collections.singletonList(message));
                        }
                    },
                    apiInfoKey);
            String filterExecutionLogId = UUID.randomUUID().toString();
            ValidationResult res = TestPlugin.validateFilter(
                    apiFilter.getFilter().getNode(),
                    rawApi,
                    apiInfoKey,
                    varMap,
                    filterExecutionLogId);

            return res.getIsValid();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(
                    e, String.format("Error in httpCallFilter %s", e.toString()));
        }

        return false;
    }
}

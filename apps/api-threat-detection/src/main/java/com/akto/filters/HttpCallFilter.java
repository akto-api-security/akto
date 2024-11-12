package com.akto.filters;

import java.util.*;

import com.akto.cache.RedisWriteBackCache;
import com.akto.dao.context.Context;
import com.akto.dao.monitoring.FilterYamlTemplateDao;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.RawApi;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.traffic.SuspectSampleData;
import com.akto.dto.type.URLMethods.Method;
import com.akto.filters.aggregators.key_generator.SourceIPKeyGenerator;
import com.akto.filters.aggregators.window_based.WindowBasedThresholdNotifier;
import com.akto.hybrid_parsers.HttpCallParser;
import com.akto.kafka.Kafka;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.malicious_request.Request;
import com.akto.malicious_request.notifier.SaveRedisNotifier;
import com.akto.rules.TestPlugin;
import com.akto.runtime.policies.ApiAccessTypePolicy;
import com.akto.suspect_data.Message;
import com.akto.test_editor.execution.VariableResolver;
import com.akto.test_editor.filter.data_operands_impl.ValidationResult;

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

    private final SaveRedisNotifier saveRedisNotifier;

    public HttpCallFilter(RedisClient redisClient, int sync_threshold_count, int sync_threshold_time) {
        this.apiFilters = new HashMap<>();
        this.lastFilterFetch = 0;
        this.httpCallParser = new HttpCallParser(sync_threshold_count, sync_threshold_time);

        String kafkaBootstrapServers = "localhost:29092";
        String isKubernetes = System.getenv("IS_KUBERNETES");
        if (isKubernetes != null && isKubernetes.equalsIgnoreCase("true")) {
            loggerMaker.infoAndAddToDb("is_kubernetes: true");
            kafkaBootstrapServers = "127.0.0.1:29092";
        }

        this.kafka = new Kafka(kafkaBootstrapServers, KAFKA_BATCH_LINGER_MS, KAFKA_BATCH_SIZE);
        this.windowBasedThresholdNotifier = new WindowBasedThresholdNotifier(
                new RedisWriteBackCache<>(redisClient, "wbt"),
                new WindowBasedThresholdNotifier.Config(100, 10 * 60));

        this.saveRedisNotifier = new SaveRedisNotifier(redisClient);
    }

    public void filterFunction(List<HttpResponseParams> responseParams) {

        int now = Context.now();
        if ((lastFilterFetch + FILTER_REFRESH_INTERVAL) < now) {
            // TODO: add support for only active templates.
            List<YamlTemplate> templates = dataActor.fetchFilterYamlTemplates();
            apiFilters = FilterYamlTemplateDao.instance.fetchFilterConfig(false, templates, false);
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
                    HttpRequestParams requestParams = responseParam.getRequestParams();
                    List<String> sourceIps = ApiAccessTypePolicy.getSourceIps(responseParam);
                    Method method = Method.fromString(requestParams.getMethod());

                    maliciousSamples.add(
                            new Message(
                                    responseParam.getAccountId(),
                                    new SuspectSampleData(
                                            sourceIps,
                                            requestParams.getApiCollectionId(),
                                            requestParams.getURL(),
                                            method,
                                            responseParam.getOrig(),
                                            Context.now(),
                                            apiFilter.getId())));

                    // Later we will also add aggregation support
                    // Eg: 100 4xx requests in last 10 minutes.
                    // But regardless of whether request falls in aggregation or not,
                    // we still push malicious requests to kafka

                    SourceIPKeyGenerator.instance.generate(responseParam).ifPresent(aggKey -> {
                        boolean thresholdBreached = this.windowBasedThresholdNotifier.shouldNotify(aggKey,
                                responseParam);

                        if (!thresholdBreached) {
                            return;
                        }

                        Request request = new Request(
                                UUID.randomUUID().toString(),
                                apiFilter.getId(),
                                aggKey,
                                System.currentTimeMillis());

                        // For now just writing breached IP to redis
                        this.saveRedisNotifier.notifyRequest(request);
                    });
                }
            }
        }

        // Should we push all the messages in one go
        // or call kafka.send for each HttpRequestParams
        try {
            maliciousSamples.forEach(
                    sample -> {
                        sample.marshall()
                                .ifPresent(
                                        s -> {
                                            kafka.send(s, KAFKA_MALICIOUS_TOPIC);
                                        });
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

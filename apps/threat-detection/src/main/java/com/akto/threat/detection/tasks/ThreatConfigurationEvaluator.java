package com.akto.threat.detection.tasks;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.akto.ProtoMessageUtils;
import com.akto.dao.context.Context;
import com.akto.data_actor.DataActor;
import com.akto.dto.ApiInfo;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ActorId;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.RatelimitConfig;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.RatelimitConfig.RatelimitConfigItem;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatConfiguration;
import com.akto.testing.ApiExecutor;
import com.akto.threat.detection.actor.SourceIPActorGenerator;
import com.akto.threat.detection.cache.ApiCountCacheLayer;
import com.akto.threat.detection.utils.Utils;
import com.akto.util.Constants;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@lombok.Getter
@lombok.Setter
public class ThreatConfigurationEvaluator {
    private static final LoggerMaker logger = new LoggerMaker(ThreatConfiguration.class, LogDb.THREAT_DETECTION);
    private static float RATE_LIMIT_CONFIDENCE_THRESHOLD = 0.0f;
    private static final RatelimitConfigItem DEFAULT_GLOBAL_RATE_LIMIT_RULE = RatelimitConfigItem.newBuilder()
            .setName("Global Rate Limit Rule")
            .setPeriod(5)
            .setAction("BLOCK")
            .setType(RatelimitConfigType.DEFAULT.name())
            .setBehaviour("DYNAMIC")
            .setRateLimitConfidence(RATE_LIMIT_CONFIDENCE_THRESHOLD)
            .setAutoThreshold(RatelimitConfig.AutomatedThreshold.newBuilder()
                    .setPercentile("p90")
                    .setOverflowPercentage(50)
                    .setBaselinePeriod(2)
                    .build())
            .setMitigationPeriod(5).build();

    private ThreatConfiguration threatConfiguration;
    private int threatConfigurationUpdateIntervalSec = 15 * 60; // 15 minutes
    private int threatConfigLastUpdatedAt = 0;
    private List<ApiInfo> apiInfos;
    private DataActor dataActor;
    private ApiCountCacheLayer apiCountCacheLayer;
    private ScheduledExecutorService scheduledExecutor;

    public enum ThreatActorIdType {
        IP,
        HEADER
    }

    public enum RatelimitConfigType {
        DEFAULT, // Global ratelimit rule for all requests, only one allowed
        CUSTOM
    }

    public enum ThreatActorRuleKind {
        HOSTNAME,
    }

    public ThreatConfigurationEvaluator(ThreatConfiguration threatConfiguration, DataActor dataActor,
            ApiCountCacheLayer apiCountCacheLayer) {
        this.dataActor = dataActor;
        this.apiCountCacheLayer = apiCountCacheLayer;

        if (threatConfiguration != null) {
            this.threatConfiguration = threatConfiguration;
            this.threatConfigLastUpdatedAt = (int) (System.currentTimeMillis() / 1000);
        } else {
            this.threatConfiguration = getThreatConfiguration();
        }

        resyncApiInfos();

        // Every 15 minutes call resyncApiInfos
        this.scheduledExecutor = Executors.newScheduledThreadPool(1);
        this.scheduledExecutor.scheduleAtFixedRate(() -> {
            try {
                logger.info("Starting scheduled resync of API infos");
                resyncApiInfos();
                logger.info("Completed scheduled resync of API infos");
            } catch (Exception e) {
                logger.error("Error during scheduled resync of API infos", e);
            }
        }, 15, 15, TimeUnit.MINUTES);
    }

    public long getApiRateLimitFromCache(ApiInfoKey apiInfoKey, RatelimitConfigItem rule) {
        String baseKey = Constants.RATE_LIMIT_CACHE_PREFIX + apiInfoKey.toString() + ":";

        // Use the period from the rule as the time window, defaulting to "5" if not
        // specified
        String timeWindow = rule.getPeriod() > 0 ? String.valueOf(rule.getPeriod()) : "5";
        long rateLimit = this.apiCountCacheLayer
                .get(baseKey + timeWindow + ":" + rule.getAutoThreshold().getPercentile());

        if (rateLimit == 0) {
            logger.debug("Rate limiting skipped no ratelimits found for api: " + apiInfoKey.toString() + " percentile: "
                    + rule.getAutoThreshold().getPercentile());
            return Constants.RATE_LIMIT_UNLIMITED_REQUESTS;
        }

        // Divide by 10 because in redis we stored long value.
        float apiConfidence = this.apiCountCacheLayer.get(baseKey + Constants.API_RATE_LIMIT_CONFIDENCE) / 10.0f;
        if (apiConfidence < rule.getRateLimitConfidence()) {
            logger.debug("Rate limiting skipped API rateLimitConfidence: " + apiConfidence
                    + " lower than configured confidence: " + rule.getRateLimitConfidence());
            return Constants.RATE_LIMIT_UNLIMITED_REQUESTS;
        }
        return rateLimit + rateLimit * rule.getAutoThreshold().getOverflowPercentage() / 100;
    }

    public ThreatConfiguration getThreatConfiguration() {

        int now = (int) (System.currentTimeMillis() / 1000);
        if (this.threatConfiguration != null
                && now - threatConfigLastUpdatedAt < threatConfigurationUpdateIntervalSec) {
            return this.threatConfiguration;
        }
        return fetchThreatConfigApi(now);
    }

    public void resyncApiInfos() {
        try {
            if(this.apiCountCacheLayer == null){
                logger.warn("Skipping ratelimiting, redis not available");
                return;
            }
            this.apiInfos = dataActor.fetchApiRateLimits(null);

            if (apiInfos == null || apiInfos.isEmpty()) {
                logger.warnAndAddToDb("No api infos found for accountId: " + Context.accountId.get());
                return;
            }
            logger.debug(apiInfos.size() + ": APIs found for accountId: " + Context.accountId.get());

            for (ApiInfo apiInfo : this.apiInfos) {

                if (apiInfo.getRateLimits() == null || apiInfo.getRateLimits().isEmpty()) {
                    continue;
                }

                Map<String, Map<String, Integer>> rateLimits = apiInfo.getRateLimits();

                String baseKey = Constants.RATE_LIMIT_CACHE_PREFIX + apiInfo.getId().toString();
                // float to long multiply by 10
                // 0.8 -> 8
                this.apiCountCacheLayer.set(baseKey + ":" + Constants.API_RATE_LIMIT_CONFIDENCE,
                        (long) (apiInfo.getRateLimitConfidence() * 10));

                // Iterate through each time window
                /**
                 * Structure of ratelimits is MinutesWindow: RateLimitValues
                 * {
                 * "5": {"p50": 100, "p75": 120, "p90": 300},
                 * "15": {"p50": 100, "p75": 120, "p90": 300},
                 * }
                 */

                for (Map.Entry<String, Map<String, Integer>> timeWindowEntry : rateLimits.entrySet()) {
                    String timeWindow = timeWindowEntry.getKey();
                    Map<String, Integer> metrics = timeWindowEntry.getValue();
                    
                    // Store each percentile value separately in cache
                    for (String percentileKey : Arrays.asList(Constants.P50_CACHE_KEY, Constants.P75_CACHE_KEY,
                            Constants.P90_CACHE_KEY)) {
                        int numRequests = metrics.getOrDefault(percentileKey, Constants.RATE_LIMIT_UNLIMITED_REQUESTS);

                        if (numRequests == Constants.RATE_LIMIT_UNLIMITED_REQUESTS) {
                            continue;
                        }

                        // Apply rate-limits to only high confidence
                        // TODO: use the default rule confidence.
                        if (!(apiInfo.getRateLimitConfidence() > RATE_LIMIT_CONFIDENCE_THRESHOLD)) {
                            continue;
                        }
                        // Include time window in the cache key
                        this.apiCountCacheLayer.set(baseKey + ":" + timeWindow + ":" + percentileKey, numRequests);
                    }
                }
            }

        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error while fetching api infos");
        }
    }

    private ThreatConfiguration fetchThreatConfigApi(int now) {
        Map<String, List<String>> headers = Utils.buildHeaders();
        headers.put("Content-Type", Collections.singletonList("application/json"));
        OriginalHttpRequest request = new OriginalHttpRequest(
                Utils.getThreatProtectionBackendUrl() + "/api/dashboard/get_threat_configuration", "", "GET", null,
                headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                logger.errorAndAddToDb("non 2xx response in get_threat_configuration", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            try {
                threatConfiguration = ProtoMessageUtils
                        .<ThreatConfiguration>toProtoMessage(
                                ThreatConfiguration.class, responsePayload)
                        .orElse(null);
                if (this.threatConfiguration != null) {
                    logger.debug("Fetched threat configuration" + this.threatConfiguration.toString());
                }
            } catch (Exception e) {
                e.printStackTrace();
                logger.error("Error while getting threat configuration" + e.getStackTrace());
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("Error in getThreatConfiguration " + e.getStackTrace());
        }
        this.threatConfigLastUpdatedAt = now;
        return threatConfiguration;
    }

    public boolean isHostNameMatching(HttpResponseParams responseParam, String pattern) {
        if (responseParam == null || pattern == null) {
            return false;
        }
        List<String> host = responseParam.getRequestParams().getHeaders().get("host");
        if (host == null) {
            return false;
        }
        String hostStr = host.get(0).toLowerCase();
        Pattern p = Pattern.compile(pattern);
        return p.matcher(hostStr).find();
    }

    public String getActorId(HttpResponseParams responseParam) {
        getThreatConfiguration();
        String actor = SourceIPActorGenerator.instance.generate(responseParam).orElse("");
        responseParam.setSourceIP(actor);
        if (responseParam.getOriginalMsg() != null) {
            logger.debugAndAddToDbCount(
                    "Actor ID generated: " + actor + " for response: " + responseParam.getOriginalMsg().get());
        }

        if (threatConfiguration == null) {
            return actor;
        }

        for (ActorId actorId : threatConfiguration.getActor().getActorIdList()) {
            ThreatActorRuleKind actorIdRuleKind = ThreatActorRuleKind.valueOf(ThreatActorRuleKind.class,
                    actorId.getKind().toUpperCase());
            switch (actorIdRuleKind) {
                case HOSTNAME:
                    if (!isHostNameMatching(responseParam, actorId.getPattern())) {
                        continue;
                    }
                    List<String> header = responseParam.getRequestParams().getHeaders()
                            .get(actorId.getKey().toLowerCase());
                    if (header != null && !header.isEmpty()) {
                        actor = header.get(0);
                        return actor;
                    } else {
                        logger.warn("Defaulting to source IP as actor id, header not found: "
                                + actorId.getKey());
                        return actor;
                    }
                default:
                    break;
            }
        }
        return actor;
    }

    public RatelimitConfigItem getDefaultRateLimitConfig() {
        getThreatConfiguration();
        if (threatConfiguration == null) {
            return DEFAULT_GLOBAL_RATE_LIMIT_RULE;
        }
        for (RatelimitConfigItem rule : threatConfiguration.getRatelimitConfig().getRulesList()) {
            if (rule.getType().equals(RatelimitConfigType.DEFAULT.name())) {
                return rule;
            }
        }
        return DEFAULT_GLOBAL_RATE_LIMIT_RULE;
    }

    public long getRatelimit(ApiInfoKey apiInfoKey) {
        RatelimitConfigItem rule = getDefaultRateLimitConfig();
        return getApiRateLimitFromCache(apiInfoKey, rule);
    }

    /**
     * Check and set if actor was already marked as malicious due to ratelimit
     * exceeded.
     * Don't send more events to BE till mitigation period is over.
     */

    public boolean isActorInMitigationPeriod(String ipApiCmsKey, RatelimitConfigItem rule) {
        return this.apiCountCacheLayer.fetchLongDataFromRedis(
                Constants.RATE_LIMIT_CACHE_PREFIX + ipApiCmsKey + ":" + Constants.API_RATE_LIMIT_MITIGATION) > 0;
    }

    /**
     * Set a key in redis with rule.getMitigationPeriod expiry.
     * Example:  14.3.2.1:collId:/users/orders:mitigation : 300 , expiry 300 seconds
     */
    public void setActorInMitigationPeriod(String ipApiCmsKey, RatelimitConfigItem rule) {
        this.apiCountCacheLayer.setLongWithExpiry(
                Constants.RATE_LIMIT_CACHE_PREFIX + ipApiCmsKey + ":" + Constants.API_RATE_LIMIT_MITIGATION,
                rule.getMitigationPeriod(), rule.getMitigationPeriod() * 60);
    }
}

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

@lombok.Getter
@lombok.Setter
public class ThreatConfigurationEvaluator {
    private static final LoggerMaker logger = new LoggerMaker(ThreatConfiguration.class, LogDb.THREAT_DETECTION);
    private ThreatConfiguration threatConfiguration;
    private int threatConfigurationUpdateIntervalSec = 15 * 60; // 15 minutes
    private int threatConfigLastUpdatedAt = 0;
    private List<ApiInfo> apiInfos;
    private DataActor dataActor;
    private ApiCountCacheLayer apiCountCacheLayer;
    private float RATE_LIMIT_CONFIDENCE_THRESHOLD = 0.0f;
    

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

    public ThreatConfigurationEvaluator(ThreatConfiguration threatConfiguration, DataActor dataActor, ApiCountCacheLayer apiCountCacheLayer) {
        this.dataActor = dataActor;
        this.apiCountCacheLayer = apiCountCacheLayer;

        if (threatConfiguration != null) {
            this.threatConfiguration = threatConfiguration;
            this.threatConfigLastUpdatedAt = (int) (System.currentTimeMillis() / 1000);
        } else {
            this.threatConfiguration = getThreatConfiguration();
        }
    }

    public long getApiRateLimitFromCache(ApiInfoKey apiInfoKey, String percentile, int overflowPercentage) {
        long rateLimit = this.apiCountCacheLayer.get(Constants.RATE_LIMIT_CACHE_PREFIX + apiInfoKey.toString() + ":" + percentile);

        if (rateLimit == 0){
            return Constants.RATE_LIMIT_UNLIMITED_REQUESTS;
        }
        return rateLimit + rateLimit * overflowPercentage/100;
    }


    public ThreatConfiguration getThreatConfiguration() {

        int now = (int) (System.currentTimeMillis() / 1000);
        if (this.threatConfiguration != null
                && now - threatConfigLastUpdatedAt < threatConfigurationUpdateIntervalSec) {
            return this.threatConfiguration;
        }
        resyncApiInfos();
        return fetchThreatConfigApi(now);
    }

    public void resyncApiInfos (){
        try {
            this.apiInfos = dataActor.fetchApiInfos();

            if (apiInfos == null || apiInfos.isEmpty()) {
                logger.warnAndAddToDb("No api infos found for account");
                return;
            }
            logger.debug(apiInfos.size() + ": APIs found for account" + Context.accountId.get());

            for(ApiInfo apiInfo: this.apiInfos){ 

                if (apiInfo.getRateLimits() == null || apiInfo.getRateLimits().isEmpty()) {
                    continue;
                }

                Map<String, Integer> rateLimits = apiInfo.getRateLimits();

                String baseKey = Constants.RATE_LIMIT_CACHE_PREFIX + apiInfo.getId().toString();
                    
                // Store each percentile value separately in cache

                for (String percentileKey: Arrays.asList(Constants.P50_CACHE_KEY, Constants.P75_CACHE_KEY, Constants.P90_CACHE_KEY)){
                    int numRequests = rateLimits.getOrDefault(percentileKey, Constants.RATE_LIMIT_UNLIMITED_REQUESTS);

                    if(numRequests == Constants.RATE_LIMIT_UNLIMITED_REQUESTS){
                        continue;
                    }

                    // TODO: Existing docs in mongo are missing these variables, will this be backwards compatible? 

                    // Apply rate-limits to only high confidence
                    if(!(apiInfo.getRateLimitConfidence() > RATE_LIMIT_CONFIDENCE_THRESHOLD)){
                        continue;
                    }
                    this.apiCountCacheLayer.set(baseKey + ":" + percentileKey, numRequests);
                }

            }

        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error while fetching api infos");
        }
    }

    private ThreatConfiguration fetchThreatConfigApi(int now) {
        Map<String, List<String>> headers = Utils.buildHeaders();
        headers.put("Content-Type", Collections.singletonList("application/json"));
        OriginalHttpRequest request = new OriginalHttpRequest(Utils.getThreatProtectionBackendUrl() + "/api/dashboard/get_threat_configuration", "","GET", null, headers, "");
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
            logger.debugAndAddToDbCount("Actor ID generated: " + actor + " for response: " + responseParam.getOriginalMsg().get());
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

    
    public RatelimitConfigItem getDefaultRateLimitConfig(){
        getThreatConfiguration();
        if (threatConfiguration == null) {
            return null;
        }
        for (RatelimitConfigItem rule : threatConfiguration.getRatelimitConfig().getRulesList()) {
            if (rule.getType().equals(RatelimitConfigType.DEFAULT.name())) {
                return rule;
            }
        }
        return null;
    }

    public long getRatelimit(ApiInfoKey apiInfoKey) {
        RatelimitConfigItem rule = getDefaultRateLimitConfig();
        return getApiRateLimitFromCache(apiInfoKey, rule.getAutoThreshold().getPercentile(), rule.getAutoThreshold().getOverflowPercentage());
    }
}

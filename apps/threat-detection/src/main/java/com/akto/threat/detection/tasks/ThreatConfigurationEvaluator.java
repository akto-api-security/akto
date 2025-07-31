package com.akto.threat.detection.tasks;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.akto.ProtoMessageUtils;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ActorId;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatConfiguration;
import com.akto.testing.ApiExecutor;
import com.akto.threat.detection.actor.SourceIPActorGenerator;
import com.akto.threat.detection.utils.Utils;

@lombok.Getter
@lombok.Setter
public class ThreatConfigurationEvaluator {
    private static final LoggerMaker logger = new LoggerMaker(ThreatConfiguration.class, LogDb.THREAT_DETECTION);
    private ThreatConfiguration threatConfiguration;
    private int threatConfigurationUpdateIntervalSec = 15 * 60; // 15 minutes
    private int threatConfigLastUpdatedAt = 0;

    public enum ThreatActorIdType {
        IP,
        HEADER
    }

    public enum ThreatActorRuleKind {
        HOSTNAME,
    }

    public ThreatConfigurationEvaluator(ThreatConfiguration threatConfiguration) {
        if (threatConfiguration != null) {
            this.threatConfiguration = threatConfiguration;
            this.threatConfigLastUpdatedAt = (int) (System.currentTimeMillis() / 1000);
        } else {
            this.threatConfiguration = getThreatConfiguration();
        }
    }

    public ThreatConfiguration getThreatConfiguration() {

        int now = (int) (System.currentTimeMillis() / 1000);
        if (this.threatConfiguration != null
                && now - threatConfigLastUpdatedAt < threatConfigurationUpdateIntervalSec) {
            return this.threatConfiguration;
        }

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
}

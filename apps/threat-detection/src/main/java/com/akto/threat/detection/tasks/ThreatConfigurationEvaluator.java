package com.akto.threat.detection.tasks;

import java.util.List;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.akto.ProtoMessageUtils;
import com.akto.dto.HttpResponseParams;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ActorId;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatConfiguration;
import com.akto.threat.detection.actor.SourceIPActorGenerator;

public class ThreatConfigurationEvaluator {

    private final CloseableHttpClient httpClient;
    private static final LoggerMaker logger = new LoggerMaker(ThreatConfiguration.class, LogDb.THREAT_DETECTION);
    private static ThreatConfiguration threatConfiguration;
    private int threatConfigurationUpdateIntervalSec = 15 * 60; // 15 minutes
    private int threatConfigLastUpdatedAt = 0;

    public enum ThreatActorIdType {
        IP,
        HEADER
    }

    public enum ThreatActorRuleKind {
        HOSTNAME,
    }

    public ThreatConfigurationEvaluator() {
        this.httpClient = HttpClients.createDefault();
        threatConfiguration = getThreatConfiguration();
    }

    private ThreatConfiguration getThreatConfiguration() {

        String url = System.getenv("AKTO_THREAT_PROTECTION_BACKEND_URL");
        String token = System.getenv("AKTO_THREAT_PROTECTION_BACKEND_TOKEN");

        HttpGet get = new HttpGet(
                String.format("%s/api/dashboard/get_threat_configuration", url));
        get.addHeader("Authorization", "Bearer " + token);
        get.addHeader("Content-Type", "application/json");

        ThreatConfiguration threatConfiguration = null;

        try (CloseableHttpResponse resp = this.httpClient.execute(get)) {
            String responseBody = EntityUtils.toString(resp.getEntity());
            threatConfiguration = ProtoMessageUtils
                    .<ThreatConfiguration>toProtoMessage(
                            ThreatConfiguration.class, responseBody)
                    .orElse(null);

            logger.debug("Fetched threat configuration" + threatConfiguration.toString());
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("Error while getting threat configuration" + e.getStackTrace());
        }
        return threatConfiguration;
    }

    private void resyncThreatConfguration() {
        int now = (int) (System.currentTimeMillis() / 1000);
        if (now - threatConfigLastUpdatedAt > threatConfigurationUpdateIntervalSec) {
            threatConfiguration = getThreatConfiguration();
            this.threatConfigLastUpdatedAt = now;
        }
    }

    public String getActorId(HttpResponseParams responseParam) {
        resyncThreatConfguration();
        String actor;
        String sourceIp = SourceIPActorGenerator.instance.generate(responseParam).orElse("");
        responseParam.setSourceIP(sourceIp);

        if (threatConfiguration == null) {
            actor = sourceIp;
            return actor;
        }

        for (ActorId actorId : threatConfiguration.getActor().getActorIdList()) {
            ThreatActorIdType actorIdType = ThreatActorIdType.valueOf(ThreatActorIdType.class,
                    actorId.getType().toLowerCase());
            switch (actorIdType) {
                case IP:
                    actor = sourceIp;
                    break;

                case HEADER:
                    List<String> header = responseParam.getRequestParams().getHeaders()
                            .get(actorId.getKey().toLowerCase());
                    if (header != null && !header.isEmpty()) {
                        actor = header.get(0);
                    } else {
                        logger.warn("Defaulting to source IP as actor id, header not found: "
                                + actorId.getKey());
                        actor = sourceIp;
                    }
                    break;
                default:
                    actor = sourceIp;
                    break;
            }
            return actor;
        }
        return sourceIp;
    }
}

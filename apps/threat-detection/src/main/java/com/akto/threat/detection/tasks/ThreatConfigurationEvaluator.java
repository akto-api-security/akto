package com.akto.threat.detection.tasks;

import java.util.List;
import java.util.regex.Pattern;

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

@lombok.Getter
@lombok.Setter
public class ThreatConfigurationEvaluator {

    private final CloseableHttpClient httpClient;
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
        this.httpClient = HttpClients.createDefault();
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

        String url = System.getenv("AKTO_THREAT_PROTECTION_BACKEND_URL");
        String token = System.getenv("AKTO_THREAT_PROTECTION_BACKEND_TOKEN");

        HttpGet get = new HttpGet(
                String.format("%s/api/dashboard/get_threat_configuration", url));
        get.addHeader("Authorization", "Bearer " + token);
        get.addHeader("Content-Type", "application/json");

        try (CloseableHttpResponse resp = this.httpClient.execute(get)) {
            String responseBody = EntityUtils.toString(resp.getEntity());
            threatConfiguration = ProtoMessageUtils
                    .<ThreatConfiguration>toProtoMessage(
                            ThreatConfiguration.class, responseBody)
                    .orElse(null);

            if (this.threatConfiguration != null) {
                logger.debug("Fetched threat configuration" + this.threatConfiguration.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("Error while getting threat configuration" + e.getStackTrace());
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
        String actor;
        String sourceIp = SourceIPActorGenerator.instance.generate(responseParam).orElse("");
        responseParam.setSourceIP(sourceIp);

        if (threatConfiguration == null) {
            actor = sourceIp;
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

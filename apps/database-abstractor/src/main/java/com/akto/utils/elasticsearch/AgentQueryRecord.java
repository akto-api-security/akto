package com.akto.utils.elasticsearch;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentQueryRecord {

    private String docId;
    private int accountId;
    private String serviceId;
    private String deviceId;
    private String userName;
    private String sessionIdentifier;
    private String queryPayload;
    private String responsePayload;
    private long timeStampMs;
    private int inputTokens;
    private int outputTokens;
    private String traceId;
    private String spanId;
    private int apiCollectionId;
    private boolean isAtlasTraffic;
    private boolean topicProcessed;

    // Guardrail result added to the traffic by the ingestion gateway and sent here by mini-runtime.
    // These names match what Gson sends from there, so Struts can bind them directly. Null means
    // guardrails did not run on this traffic, which is not the same as running and finding nothing.
    private Boolean guardrailViolated;
    private String guardrailAction;
    private String guardrailPolicy;
    private String guardrailRule;
    private String guardrailReason;
    private String guardrailSeverity;

    // Java Introspector maps JSON key "isAtlasTraffic" → setIsAtlasTraffic()
    public void setIsAtlasTraffic(boolean isAtlasTraffic) {
        this.isAtlasTraffic = isAtlasTraffic;
    }
}

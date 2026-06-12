package com.akto.utils.elasticsearch;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
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
    private boolean isAtlasTraffic;
}

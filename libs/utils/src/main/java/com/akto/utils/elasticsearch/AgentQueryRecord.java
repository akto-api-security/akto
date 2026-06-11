package com.akto.utils.elasticsearch;

/**
 * In-memory transport object for an agentic query record read from Elasticsearch.
 * Not a Mongo entity. No codec registration.
 */
public class AgentQueryRecord {

    private final String docId;
    private final int accountId;
    private final String serviceId;
    private final String deviceId;
    private final String userName;
    private final String sessionIdentifier;
    private final String queryPayload;
    private final String responsePayload;
    private final long timeStampMs;
    private final int inputTokens;
    private final int outputTokens;
    private final String traceId;
    private final String spanId;
    private final boolean isAtlasTraffic;

    public AgentQueryRecord(String docId, int accountId, String serviceId, String deviceId,
                            String userName, String sessionIdentifier,
                            String queryPayload, String responsePayload,
                            long timeStampMs, int inputTokens, int outputTokens,
                            String traceId, String spanId, boolean isAtlasTraffic) {
        this.docId = docId;
        this.accountId = accountId;
        this.serviceId = serviceId;
        this.deviceId = deviceId;
        this.userName = userName;
        this.sessionIdentifier = sessionIdentifier;
        this.queryPayload = queryPayload;
        this.responsePayload = responsePayload;
        this.timeStampMs = timeStampMs;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.traceId = traceId;
        this.spanId = spanId;
        this.isAtlasTraffic = isAtlasTraffic;
    }

    public String getDocId() { return docId; }
    public int getAccountId() { return accountId; }
    public String getServiceId() { return serviceId; }
    public String getDeviceId() { return deviceId; }
    public String getUserName() { return userName; }
    public String getSessionIdentifier() { return sessionIdentifier; }
    public String getQueryPayload() { return queryPayload; }
    public String getResponsePayload() { return responsePayload; }
    public long getTimeStampMs() { return timeStampMs; }
    public int getInputTokens() { return inputTokens; }
    public int getOutputTokens() { return outputTokens; }
    public String getTraceId() { return traceId; }
    public String getSpanId() { return spanId; }
    public boolean getIsAtlasTraffic() { return isAtlasTraffic; }
}

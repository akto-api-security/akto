package com.akto.utils.elasticsearch;

/**
 * In-memory transport object for an agentic query record read from Elasticsearch.
 * Not a Mongo entity. No codec registration.
 */
public class AgentQueryRecord {

    // Elasticsearch document field names. Use these instead of inline string literals
    // when building queries or parsing results in action / service classes.
    public static final String F_ACCOUNT_ID            = "accountId";
    public static final String F_SERVICE_ID            = "serviceId";
    public static final String F_SERVICE_ID_KW         = "serviceId.keyword";
    public static final String F_DEVICE_ID             = "deviceId";
    public static final String F_DEVICE_ID_KW          = "deviceId.keyword";
    public static final String F_USER_NAME             = "userName";
    public static final String F_USER_NAME_KW          = "userName.keyword";
    public static final String F_SESSION_IDENTIFIER    = "sessionIdentifier";
    public static final String F_SESSION_IDENTIFIER_KW = "sessionIdentifier.keyword";
    public static final String F_QUERY_PAYLOAD         = "queryPayload";
    public static final String F_RESPONSE_PAYLOAD      = "responsePayload";
    public static final String F_TIMESTAMP             = "timestamp";
    public static final String F_INPUT_TOKENS          = "inputTokens";
    public static final String F_OUTPUT_TOKENS         = "outputTokens";
    public static final String F_TRACE_ID              = "traceId";
    public static final String F_TRACE_ID_KW           = "traceId.keyword";
    public static final String F_SPAN_ID_KW            = "spanId.keyword";
    public static final String F_IS_ATLAS_TRAFFIC      = "isAtlasTraffic";

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

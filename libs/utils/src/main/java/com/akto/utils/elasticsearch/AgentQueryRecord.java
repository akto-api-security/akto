package com.akto.utils.elasticsearch;

import com.akto.dao.context.Context;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.billing.Organization;
import com.akto.usage.OrgUtils;
import com.akto.util.Constants;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    private static final String HEADER_PREFIX    = "x-akto-installer-";
    private static final String HEADER_DEVICE_ID = "device_id";
    private static final String HEADER_USER_EMAIL = "user_email";
    private static final String HEADER_SESSION_ID = "akto_session_id";
    private static final String HEADER_TRACE_ID   = "akto_message_id";

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

    public String getDocId()             { return docId; }
    public int getAccountId()            { return accountId; }
    public String getServiceId()         { return serviceId; }
    public String getDeviceId()          { return deviceId; }
    public String getUserName()          { return userName; }
    public String getSessionIdentifier() { return sessionIdentifier; }
    public String getQueryPayload()      { return queryPayload; }
    public String getResponsePayload()   { return responsePayload; }
    public long getTimeStampMs()         { return timeStampMs; }
    public int getInputTokens()          { return inputTokens; }
    public int getOutputTokens()         { return outputTokens; }
    public String getTraceId()           { return traceId; }
    public String getSpanId()            { return spanId; }
    public boolean getIsAtlasTraffic()   { return isAtlasTraffic; }

    public static AgentQueryRecord fromHttpResponseParams(
            HttpResponseParams p,
            Map<String, String> tagsMap,
            Map<String, String> deviceUserMap) {

        if (p == null || p.getRequestParams() == null) {
            return null;
        }

        Map<String, List<String>> headers = p.getRequestParams().getHeaders();
        String source = tagsMap != null ? tagsMap.get(Constants.AI_AGENT_TAG_SOURCE) : null;
        boolean isAtlasTraffic = Constants.AI_AGENT_SOURCE_ENDPOINT.equals(source);

        String serviceId, deviceId, userName;

        if (isAtlasTraffic) {
            String host = getFirstHeader(headers, "host");
            String[] parts = host != null ? host.split("\\.", 3) : new String[0];
            deviceId  = parts.length >= 1 ? parts[0] : null;
            serviceId = parts.length >= 2 ? parts[1] : host;
            if (deviceId == null || deviceUserMap == null || !deviceUserMap.containsKey(deviceId)) {
                return null;
            }
            userName = deviceUserMap.get(deviceId);

        } else if (tagsMap != null && tagsMap.containsKey(Constants.AKTO_GEN_AI_TAG)) {
            deviceId  = null;
            serviceId = getFirstHeader(headers, "host");
            Organization org = OrgUtils.getOrganizationCached(Context.getActualAccountId());
            userName  = org != null ? org.getAdminEmail() : null;

        } else {
            serviceId = getFirstHeader(headers, "host");
            deviceId  = getFirstHeader(headers, HEADER_PREFIX + HEADER_DEVICE_ID);
            userName  = getFirstHeader(headers, HEADER_PREFIX + HEADER_USER_EMAIL);
        }

        String sessionIdentifier = getFirstHeader(headers, HEADER_PREFIX + HEADER_SESSION_ID);
        String traceId           = getFirstHeader(headers, HEADER_PREFIX + HEADER_TRACE_ID);
        String spanId = "span_" + UUID.randomUUID().toString();

        String requestPayload  = p.getRequestParams().getPayload();
        String responsePayload = p.getPayload() != null ? p.getPayload() : "";
        int inputTokens  = requestPayload  != null ? requestPayload.length()  : 0;
        int outputTokens = responsePayload.length();

        return new AgentQueryRecord(
                null,
                Context.getActualAccountId(),
                serviceId,
                deviceId,
                userName,
                sessionIdentifier,
                requestPayload,
                responsePayload,
                Context.now() * 1000L,
                inputTokens,
                outputTokens,
                traceId,
                spanId,
                isAtlasTraffic
        );
    }

    private static String getFirstHeader(Map<String, List<String>> headers, String name) {
        if (headers == null) return null;
        List<String> values = headers.get(name);
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }
}

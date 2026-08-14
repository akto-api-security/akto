package com.akto.utils.elasticsearch;

import com.akto.dao.context.Context;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.billing.Organization;
import com.akto.usage.OrgUtils;
import com.akto.util.Constants;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    // Guardrail result for this prompt, recorded on the traffic by the ingestion gateway
    // (Gateway.recordGuardrailVerdict). Not final: set once by setGuardrailVerdict right after the
    // object is built, so the already long constructor does not grow six more arguments. These names
    // are the wire contract in both directions - the gateway writes them and Gson sends them to
    // cyborg unchanged.
    //
    // All six stay null when guardrails did not run on this traffic. Gson omits nulls, so cyborg
    // stores no guardrail keys at all and the trace correctly shows nothing rather than a clean
    // result for a prompt that was never checked.
    private Boolean guardrailViolated;
    private String guardrailAction;
    private String guardrailPolicy;
    private String guardrailRule;
    private String guardrailReason;
    private String guardrailSeverity;

    private static final String HEADER_PREFIX    = "x-akto-installer-";
    private static final String HEADER_DEVICE_ID = "device_id";
    private static final String HEADER_USER_EMAIL = "user_email";
    private static final String HEADER_SESSION_ID = "akto_session_id";
    private static final String HEADER_TRACE_ID   = "akto_message_id";

    private static final int ATLAS_SESSION_TTL = Constants.ONE_DAY_TIMESTAMP;
    private static final Map<String, Integer> ATLAS_SESSION_LAST_SEEN = new ConcurrentHashMap<>();

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

    public Boolean getGuardrailViolated() { return guardrailViolated; }
    public String getGuardrailAction()    { return guardrailAction; }
    public String getGuardrailPolicy()    { return guardrailPolicy; }
    public String getGuardrailRule()      { return guardrailRule; }
    public String getGuardrailReason()    { return guardrailReason; }
    public String getGuardrailSeverity()  { return guardrailSeverity; }

    /** Reads the guardrail result the ingestion gateway recorded on the traffic. Absent for traffic
     *  that was never checked, and for anything ingested before this was added. */
    private void setGuardrailVerdict(String verdictJson) {
        if (verdictJson == null || verdictJson.isEmpty()) return;
        try {
            JSONObject v = new JSONObject(verdictJson);
            this.guardrailViolated = v.optBoolean("guardrailViolated", false);
            this.guardrailAction   = v.optString("guardrailAction", "");
            this.guardrailPolicy   = v.optString("guardrailPolicy", "");
            this.guardrailRule     = v.optString("guardrailRule", "");
            this.guardrailReason   = v.optString("guardrailReason", "");
            this.guardrailSeverity = v.optString("guardrailSeverity", "");
        } catch (JSONException e) {
            // Malformed verdict must never cost us the trace record.
        }
    }

    public static AgentQueryRecord fromHttpResponseParams(
            HttpResponseParams p,
            Map<String, String> tagsMap,
            Map<String, String> deviceUserMap) {

        if (p == null || p.getRequestParams() == null) {
            return null;
        }

        Map<String, List<String>> headers = p.getRequestParams().getHeaders();
        String sessionIdentifier = getFirstHeader(headers, HEADER_PREFIX + HEADER_SESSION_ID);

        String source = tagsMap != null ? tagsMap.get(Constants.AI_AGENT_TAG_SOURCE) : null;
        boolean isBrowserExtensionTraffic = tagsMap != null && tagsMap.containsKey(Constants.AKTO_BROWSER_LLM_TAG);
        boolean isAtlasTraffic = Constants.AI_AGENT_SOURCE_ENDPOINT.equals(source);

        if (isAtlasTraffic) {
            if (sessionIdentifier != null) {
                ATLAS_SESSION_LAST_SEEN.put(sessionIdentifier, Context.now());
            }
        } else if (sessionIdentifier != null && isKnownAtlasSession(sessionIdentifier)) {
            isAtlasTraffic = true;
        }

        String serviceId, deviceId, userName;

        // Browser traffic must not take this branch: it derives device/user from a host id it
        // doesn't have that shape for, and returns null when it can't.
        if (isAtlasTraffic && !isBrowserExtensionTraffic) {
            String host = getFirstHeader(headers, "host");
            String[] parts = host != null ? host.split("\\.", 3) : new String[0];
            deviceId  = parts.length >= 1 ? parts[0] : null;
            serviceId = parts.length >= 2 ? parts[1] : host;
            if(serviceId.equals("ai-agent") && parts.length >=3){
                serviceId = parts[2];
            }
            if (deviceId == null) {
                return null;
            }
            if (deviceUserMap != null && deviceUserMap.containsKey(deviceId)) {
                userName = deviceUserMap.get(deviceId);
            } else {
                // Atlas endpoint agents without MCP_ENDPOINT_SHIELD registration (e.g. Claude Cowork OTLP)
                // still carry user identity via installer headers from otel-ingestion-service.
                userName = getFirstHeader(headers, HEADER_PREFIX + HEADER_USER_EMAIL);
                if (userName == null || userName.isEmpty()) {
                    return null;
                }
            }

        } else if (isBrowserExtensionTraffic) {
            // Host id is <heartbeat name>.<browser>.<site>, so its first label keys deviceUserMap.
            String host = getFirstHeader(headers, "host");
            serviceId = host;
            deviceId  = null;
            String moduleName = host != null ? host.split("\\.", 2)[0] : null;
            userName = (deviceUserMap != null && moduleName != null) ? deviceUserMap.get(moduleName) : null;
            // The extension can register before its profile email resolves, naming the module by
            // device id while the host id already says the email — so match on the email itself.
            if (userName == null || !userName.contains("@")) {
                userName = userByEmailPrefix(deviceUserMap, moduleName);
            }
            if (userName == null || !userName.contains("@")) {
                userName = getFirstHeader(headers, HEADER_PREFIX + HEADER_USER_EMAIL);
            }
            if (userName == null || !userName.contains("@")) {
                userName = moduleName;
            }
            if (userName == null || userName.isEmpty()) {
                Organization org = OrgUtils.getOrganizationCached(Context.getActualAccountId());
                userName = org != null ? org.getAdminEmail() : null;
            }

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

        String traceId           = getFirstHeader(headers, HEADER_PREFIX + HEADER_TRACE_ID);
        String messageIdHeader   = getFirstHeader(headers, Constants.AKTO_MESSAGE_ID_HEADER);
        String spanId = (messageIdHeader != null && !messageIdHeader.isEmpty())
                ? messageIdHeader
                : "span_" + UUID.randomUUID().toString();

        String requestPayload  = p.getRequestParams().getPayload();
        String responsePayload = stripAwsMetadata(p.getPayload() != null ? p.getPayload() : "");
        int inputTokens  = resolveTokenCount(responsePayload, requestPayload, true);
        int outputTokens = resolveTokenCount(responsePayload, responsePayload, false);

        AgentQueryRecord record = new AgentQueryRecord(
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
                // Browser collections are registered source=ENDPOINT, so their traffic reports as Atlas too.
                isAtlasTraffic || isBrowserExtensionTraffic
        );
        record.setGuardrailVerdict(p.getGuardrailVerdict());
        return record;
    }

    private static boolean isKnownAtlasSession(String sessionIdentifier) {
        // Non-Atlas sources also send akto_session_id, so a plain get() would unbox null here.
        int lastSeen = ATLAS_SESSION_LAST_SEEN.getOrDefault(sessionIdentifier, 0);
        if (lastSeen == 0) {
            return false;
        }
        if (Context.now() - lastSeen > ATLAS_SESSION_TTL) {
            ATLAS_SESSION_LAST_SEEN.remove(sessionIdentifier);
            return false;
        }
        return true;
    }

    // The extension builds the host-id prefix as the email's local part with non-alphanumerics
    // stripped, so derive the same key from each mapped email and match the prefix against it.
    private static String userByEmailPrefix(Map<String, String> deviceUserMap, String prefix) {
        if (deviceUserMap == null || prefix == null || prefix.isEmpty()) return null;
        for (String email : deviceUserMap.values()) {
            if (email == null || !email.contains("@")) continue;
            String local = email.substring(0, email.indexOf('@')).replaceAll("[^a-zA-Z0-9]", "");
            if (local.equalsIgnoreCase(prefix)) return email;
        }
        return null;
    }

    private static String getFirstHeader(Map<String, List<String>> headers, String name) {
        if (headers == null) return null;
        List<String> values = headers.get(name);
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }

    /**
     * awsMetadata duplicates what's already captured as structured Trace/Span data by
     * BedrockAgentTraceParser — it can embed full tool-call outputs (file listings,
     * page dumps, etc.), so keeping a second raw copy here needlessly bloats the batch
     * sent to the agent-query-logs service.
     */
    private static String stripAwsMetadata(String responsePayload) {
        if (responsePayload == null || responsePayload.isEmpty()) {
            return responsePayload;
        }
        try {
            JSONObject obj = new JSONObject(responsePayload);
            if (!obj.has("awsMetadata")) {
                return responsePayload;
            }
            obj.remove("awsMetadata");
            return obj.toString();
        } catch (Exception ignored) {
            return responsePayload;
        }
    }

    /** Prefer usage block from LLM response JSON; fall back to payload string length. */
    static int resolveTokenCount(String responsePayload, String fallbackPayload, boolean input) {
        int fromUsage = parseUsageTokens(responsePayload, input);
        if (fromUsage >= 0) {
            return fromUsage;
        }
        return fallbackPayload != null ? fallbackPayload.length() : 0;
    }

    static int parseUsageTokens(String json, boolean input) {
        if (json == null || json.isEmpty()) {
            return -1;
        }
        try {
            JSONObject obj = new JSONObject(json);
            if (obj.has("usage")) {
                JSONObject usage = obj.getJSONObject("usage");
                int fromUsage = readTokenField(usage, input);
                if (fromUsage >= 0) {
                    return fromUsage;
                }
            }
            return readTokenField(obj, input);
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static int readTokenField(JSONObject obj, boolean input) throws JSONException{
        if (input) {
            if (obj.has("input_tokens")) return obj.getInt("input_tokens");
            if (obj.has("prompt_tokens")) return obj.getInt("prompt_tokens");
        } else {
            if (obj.has("output_tokens")) return obj.getInt("output_tokens");
            if (obj.has("completion_tokens")) return obj.getInt("completion_tokens");
        }
        return -1;
    }
    
    @Override
    public String toString() {
        return "Record{" +
                "serviceId='" + serviceId + '\'' +
                ", deviceId='" + deviceId + '\'' +
                ", userName='" + userName + '\'' +
                ", payload='" + queryPayload + '\'' +
                ", body='" + responsePayload + '\'' +
                ", isAtlasTraffic=" + isAtlasTraffic +
                ", guardrailViolated=" + guardrailViolated +
                ", guardrailAction='" + guardrailAction + '\'' +
                ", guardrailPolicy='" + guardrailPolicy + '\'' +
                ", guardrailRule='" + guardrailRule + '\'' +
                ", guardrailReason='" + guardrailReason + '\'' +
                ", guardrailSeverity='" + guardrailSeverity + '\'' +
                '}';
    }
}

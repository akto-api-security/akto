package com.akto.utils.elasticsearch;

import com.akto.dao.context.Context;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.claude_identity.ClaudeDesktopInfo;
import com.akto.dto.billing.Organization;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.usage.OrgUtils;
import com.akto.util.Constants;
import com.akto.util.JSONUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
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

    // "UNAVAILABLE" or "BASIC" when redaction was not complete; null (omitted by Gson) otherwise.
    private String payloadRedaction;

    private static final String HEADER_PREFIX   = "x-akto-installer-";
    private static final String HEADER_DEVICE_ID = "device_id";
    private static final String HEADER_USER_EMAIL = "user_email";
    private static final String HEADER_SESSION_ID = "akto_session_id";
    private static final String HEADER_TRACE_ID   = "akto_message_id";
    private static final String TAG_BROWSER_USER_EMAIL = "browser-user-email";

    private static final String SESSIONS_PATH_SEGMENT = "sessions";
    private static final String EVENTS_PATH_SEGMENT   = "events";

    private static final int URL_SESSION_ACCOUNT_ID = 1785654409;

    private static final LoggerMaker loggerMaker = new LoggerMaker(AgentQueryRecord.class, LogDb.RUNTIME);

    private static final int ATLAS_SESSION_TTL = Constants.ONE_DAY_TIMESTAMP;
    private static final Map<String, Integer> ATLAS_SESSION_LAST_SEEN = new ConcurrentHashMap<>();

    /**
     * Claude surfaces that report one shared serviceId across every install, as
     * {host substring, agentLogins key} pairs.
     *
     * The columns are not a rename of each other — three host labels resolve to two logins:
     *
     *   claude-desktop -> claude-desktop    the Desktop app's own login
     *   claude-cowork  -> claude-desktop    Cowork runs inside Desktop and shares its session;
     *                                       the agent reports no separate claude-cowork login
     *   claude-cli     -> claude-cli-user   the CLI's login (claude-cli-local / -project /
     *                                       -enterprise are config scopes, not logins)
     *
     * Which login we read matters: Desktop and the CLI authenticate through separate token stores
     * and can be signed into different orgs on one machine, so CLI traffic must never be stamped
     * with Desktop's org or the reverse.
     */
    private static final String[][] CLAUDE_SHARED_SURFACES = {
        { "claude-desktop", "claude-desktop"  },
        { "claude-cowork",  "claude-desktop"  },
        { "claude-cli",     "claude-cli-user" },
    };

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
    public String getPayloadRedaction()   { return payloadRedaction; }

    /** Copy with new payloads; every other field and the token counts are kept. A null payload keeps the current one. */
    public AgentQueryRecord withPayloads(String queryPayload, String responsePayload, String payloadRedaction) {
        AgentQueryRecord copy = new AgentQueryRecord(docId, accountId, serviceId, deviceId, userName,
                sessionIdentifier,
                queryPayload != null ? queryPayload : this.queryPayload,
                responsePayload != null ? responsePayload : this.responsePayload,
                timeStampMs, inputTokens, outputTokens, traceId, spanId, isAtlasTraffic);
        copy.guardrailViolated = guardrailViolated;
        copy.guardrailAction = guardrailAction;
        copy.guardrailPolicy = guardrailPolicy;
        copy.guardrailRule = guardrailRule;
        copy.guardrailReason = guardrailReason;
        copy.guardrailSeverity = guardrailSeverity;
        copy.payloadRedaction = payloadRedaction;
        return copy;
    }

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

        return fromHttpResponseParams(p, tagsMap, deviceUserMap, null);
    }

    public static AgentQueryRecord fromHttpResponseParams(
            HttpResponseParams p,
            Map<String, String> tagsMap,
            Map<String, String> deviceUserMap,
            Map<String, Map<String, ClaudeDesktopInfo>> deviceClaudeDesktopInfoMap) {

        if (p == null || p.getRequestParams() == null) {
            return null;
        }

        Map<String, List<String>> headers = p.getRequestParams().getHeaders();
        String sessionIdentifier = getFirstHeader(headers, HEADER_PREFIX + HEADER_SESSION_ID);
        String traceId           = getFirstHeader(headers, HEADER_PREFIX + HEADER_TRACE_ID);

        String source = tagsMap != null ? tagsMap.get(Constants.AI_AGENT_TAG_SOURCE) : null;
        boolean isBrowserExtensionTraffic = tagsMap != null && tagsMap.containsKey(Constants.AKTO_BROWSER_LLM_TAG);
        boolean isAtlasTraffic = Constants.AI_AGENT_SOURCE_ENDPOINT.equals(source);

        if ((sessionIdentifier == null || sessionIdentifier.isEmpty())
                && (isAtlasTraffic && Context.getActualAccountId() == URL_SESSION_ACCOUNT_ID)) {
            String url = p.getRequestParams().getURL();
            sessionIdentifier = sessionIdFromUrl(url);
            if (sessionIdentifier != null) {
                loggerMaker.info("[agent-session] derived session id from url: sessionId=" + sessionIdentifier
                        + " url=" + url + " traceId=" + traceId + " isAtlasTraffic=" + isAtlasTraffic);
            } else {
                loggerMaker.info("[agent-session] no session id in header or url: url=" + url
                        + " traceId=" + traceId + " isAtlasTraffic=" + isAtlasTraffic);
            }
        }

        if (isAtlasTraffic) {
            if (sessionIdentifier != null) {
                ATLAS_SESSION_LAST_SEEN.put(sessionIdentifier, Context.now());
            }
        } else if (sessionIdentifier != null && isKnownAtlasSession(sessionIdentifier)) {
            isAtlasTraffic = true;
        }

        String serviceId, deviceId, userName;

        userName = getFirstHeader(headers, HEADER_PREFIX + HEADER_USER_EMAIL);

        // Browser traffic must not take this branch: it derives device/user from a host id it
        // doesn't have that shape for, and returns null when it can't.
        if (isAtlasTraffic && !isBrowserExtensionTraffic) {
            String host = getFirstHeader(headers, "host");
            String[] parts = host != null ? host.split("\\.", 3) : new String[0];
            deviceId  = parts.length >= 1 ? parts[0] : null;
            serviceId = parts.length >= 2 ? parts[1] : host;
            if ("ai-agent".equals(serviceId) && parts.length >= 3) {
                serviceId = parts[2];
            }
            if (userName == null || userName.isEmpty()) {
                if (deviceId == null) {
                    return null;
                }
                if (deviceUserMap != null && deviceUserMap.containsKey(deviceId) ) {
                    userName = deviceUserMap.get(deviceId);
                }    
            }

            // Claude Desktop and Claude Cowork each report one serviceId for every install on the
            // planet, so on their own they collapse every org's traffic into a single service.
            // Qualifying with the org keeps them apart. Only these two: everything else already
            // carries an org-specific serviceId.
            //
            // The org is folded into serviceId rather than carried as its own field: serviceId is
            // already the dimension every downstream consumer groups and filters by, so the split
            // happens for free everywhere instead of needing each of them to learn a new field.
            //
            // Matched against the raw host, not the parsed serviceId. The split above caps at three
            // parts, so a host like "<device>.ai-agent.claude-desktop.akto.io" leaves serviceId as
            // "claude-desktop.akto.io" — an equality check on serviceId silently never fires there.
            String orgId = claudeOrgUuidForHost(host, deviceId, deviceClaudeDesktopInfoMap);
            if (orgId != null && !orgId.isEmpty()) {
                serviceId = serviceId + "-" + orgId;
            }
        } else if (isBrowserExtensionTraffic) {
            // Host id is <heartbeat name>.<browser>.<site>, so its first label keys deviceUserMap.
            String host = getFirstHeader(headers, "host");
            serviceId = host;
            deviceId  = null;
            String moduleName = host != null ? host.split("\\.", 2)[0] : null;
            // The site login the extension intercepted wins; otherwise fall back to the profile identity.
            userName = tagsMap.get(TAG_BROWSER_USER_EMAIL);
            if ((userName == null || !userName.contains("@")) && deviceUserMap != null && moduleName != null) {
                userName = deviceUserMap.get(moduleName);
            }
            // The extension can register before its profile email resolves, naming the module by
            // device id while the host id already says the email — so match on the email itself.
            if (userName == null || !userName.contains("@")) {
                userName = userByEmailPrefix(deviceUserMap, moduleName);
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
            userName  = resolveGenAiUser(p, tagsMap);

        } else {
            serviceId = getFirstHeader(headers, "host");
            deviceId  = getFirstHeader(headers, HEADER_PREFIX + HEADER_DEVICE_ID);
        }

        String requestPayload  = p.getRequestParams().getPayload();
        // awsMetadata duplicates what's already captured as structured Trace/Span data by
        // BedrockAgentTraceParser — it can embed full tool-call outputs (file listings, page
        // dumps, etc.), so keeping a second raw copy here needlessly bloats the batch sent
        // to the agent-query-logs service.
        // The same holds for the Alibaba connector's alibabaMetadata (AlibabaCloudTraceParser).
        String responsePayload = JSONUtils.removeKey(
            JSONUtils.removeKey(p.getPayload() != null ? p.getPayload() : "", "awsMetadata"), "alibabaMetadata");
        if (Constants.AI_AGENT_SOURCE_AWS_BEDROCK.equals(source)) {
            responsePayload = withBedrockModel(responsePayload, tagsMap, headers);
        }
        int inputTokens  = resolveTokenCount(responsePayload, requestPayload, true);
        int outputTokens = resolveTokenCount(responsePayload, responsePayload, false);

        String messageIdHeader = getFirstHeader(headers, Constants.AKTO_MESSAGE_ID_HEADER);
        String spanId;
        if (messageIdHeader != null && !messageIdHeader.isEmpty()) {
            spanId = messageIdHeader;
        } else if (isAtlasTraffic || isBrowserExtensionTraffic) {
            // Atlas keeps a drawn id. One Atlas turn is several spans of the same trace, and the
            // flyout renders them keyed on this value - two spans that happened to agree on time
            // and both payloads would collide and render as one. Atlas also gains nothing from a
            // reproducible id: its traceId comes from the installer, so its rows are already
            // addressable.
            spanId = "span_" + UUID.randomUUID();
        } else {
            spanId = syntheticSpanId(p.getTime(), serviceId, requestPayload, responsePayload);
        }

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

    static String sessionIdFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        int queryStart = url.indexOf('?');
        String[] parts = (queryStart >= 0 ? url.substring(0, queryStart) : url).split("/");
        for (int i = 0; i + 2 < parts.length; i++) {
            if (SESSIONS_PATH_SEGMENT.equals(parts[i]) && EVENTS_PATH_SEGMENT.equals(parts[i + 2])
                    && !parts[i + 1].isEmpty()) {
                return parts[i + 1];
            }
        }
        return null;
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

    /**
     * Who to show as the user on gen-AI traffic.
     *
     * The fallback is the organisation's admin email, which is not a user at all - it is whoever
     * administers the account, and it reads as though that person made the call. Bedrock can do
     * better: the log processor already resolves a caller from the invocation's identity ARN
     * (identityActor.js) and sends it as the record's ip - an SSO email where the ARN carries
     * one, otherwise the assumed role's name. That is a real answer to "who called this".
     *
     * Scoped to AWS_BEDROCK deliberately. Every other gen-AI source puts an actual IP address
     * in that field, and showing 10.0.1.15 as the user would be worse than the admin email.
     */
    private static String resolveGenAiUser(HttpResponseParams p, Map<String, String> tagsMap) {
        if (Constants.AI_AGENT_SOURCE_AWS_BEDROCK.equals(tagsMap.get(Constants.AI_AGENT_TAG_SOURCE))) {
            String actor = p.getSourceIP();
            if (actor != null && !actor.trim().isEmpty()
                    && !Constants.AI_AGENT_UNRESOLVED_ACTOR_IP.equals(actor.trim())) {
                return actor.trim();
            }
        }
        Organization org = OrgUtils.getOrganizationCached(Context.getActualAccountId());
        return org != null ? org.getAdminEmail() : null;
    }

    /**
     * Puts Bedrock's model id where every reader of a record looks for the model.
     *
     * The Traces UI (model column, session flyout) and the top-models aggregation all read a
     * "model" key from responsePayload. Provider responses carry it - Anthropic and OpenAI both
     * echo the model - but a Bedrock record is built by the log processor, which sends the model
     * id as a tag and as X-Bedrock-Model-Id instead, so these records showed as "Unknown".
     *
     * Only added when the payload has no model of its own, so a real value is never replaced.
     * A payload that is not a JSON object is returned unchanged.
     */
    static String withBedrockModel(String responsePayload, Map<String, String> tagsMap,
                                   Map<String, List<String>> headers) {
        String model = tagsMap != null ? tagsMap.get(Constants.AI_AGENT_TAG_MODEL) : null;
        if (isBlankModel(model)) {
            model = getFirstHeader(headers, Constants.AI_AGENT_HEADER_BEDROCK_MODEL_ID);
        }
        if (isBlankModel(model) || responsePayload == null || responsePayload.trim().isEmpty()) {
            return responsePayload;
        }
        try {
            JSONObject obj = new JSONObject(responsePayload);
            if (obj.has("model") && !obj.optString("model", "").isEmpty()) {
                return responsePayload;
            }
            obj.put("model", model.trim());
            return obj.toString();
        } catch (JSONException e) {
            return responsePayload;
        }
    }

    /** The log processor's placeholder when it could not resolve a model is not a model. */
    private static boolean isBlankModel(String model) {
        return model == null || model.trim().isEmpty() || "unknown-model".equals(model.trim());
    }

    /**
     * The id for a non-Atlas record whose source sent none of its own.
     *
     * Derived from the event rather than drawn at random, so re-ingesting the same source line
     * produces the same id instead of a second, differently-named copy of one invocation. That
     * matters because the record is written with a null docId, leaving Elasticsearch to assign
     * one - nothing else about the write is idempotent.
     *
     * The basis is the event's own time (p.getTime(), what the source reported) rather than the
     * record's timeStampMs, which is ingest time and would differ on every replay. Two events
     * that agree on account, second, service and both payloads are taken to be the same event.
     *
     * Same approach as SnowflakeTraceParser.deterministicSpanId - a name-based UUID, used for
     * identity rather than secrecy. Unprefixed: this id is shown as-is in the Argus table, and
     * nothing matches on the "span_" prefix that the Atlas branch still draws.
     */
    private static String syntheticSpanId(int eventTime, String serviceId,
                                          String requestPayload, String responsePayload) {
        String basis = Context.getActualAccountId()
                + "|" + eventTime
                + "|" + (serviceId       == null ? "" : serviceId)
                + "|" + (requestPayload  == null ? "" : requestPayload)
                + "|" + (responsePayload == null ? "" : responsePayload);
        return UUID.nameUUIDFromBytes(basis.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String getFirstHeader(Map<String, List<String>> headers, String name) {
        if (headers == null) return null;
        List<String> values = headers.get(name);
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
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

    /**
     * Org uuid to qualify serviceId with, or null when the host is not a shared-serviceId Claude
     * surface, the device is unknown, or that surface has no resolved login on it.
     *
     * The surface named in the host picks which login to read — see {@link #CLAUDE_SHARED_SURFACES}
     * for why reading the wrong one would misattribute the traffic.
     */
    private static String claudeOrgUuidForHost(String host, String deviceId,
            Map<String, Map<String, ClaudeDesktopInfo>> deviceClaudeDesktopInfoMap) {
        if (host == null || deviceId == null || deviceClaudeDesktopInfoMap == null) {
            return null;
        }
        Map<String, ClaudeDesktopInfo> byAgentType = deviceClaudeDesktopInfoMap.get(deviceId);
        if (byAgentType == null) {
            return null;
        }
        for (String[] surface : CLAUDE_SHARED_SURFACES) {
            if (host.contains(surface[0])) {
                ClaudeDesktopInfo info = byAgentType.get(surface[1]);
                return info != null ? info.getOrganizationUuid() : null;
            }
        }
        return null;
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

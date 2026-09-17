package com.akto.utils.elasticsearch;

import com.akto.dao.context.Context;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.claude_identity.ClaudeDesktopInfo;
import com.akto.dto.billing.Organization;
import com.akto.log.LoggerMaker;
import com.akto.usage.OrgUtils;
import com.akto.util.Constants;
import com.akto.util.JSONUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
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

    private static final String SESSIONS_PATH_SEGMENT = "sessions";
    private static final String EVENTS_PATH_SEGMENT   = "events";

    private static final int URL_SESSION_ACCOUNT_ID = 1785654409;

    private static final LoggerMaker loggerMaker = new LoggerMaker(AgentQueryRecord.class);

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

        return fromHttpResponseParams(p, tagsMap, deviceUserMap, null, null);
    }

    public static AgentQueryRecord fromHttpResponseParams(
            HttpResponseParams p,
            Map<String, String> tagsMap,
            Map<String, String> deviceUserMap,
            Map<String, Map<String, ClaudeDesktopInfo>> deviceClaudeDesktopInfoMap) {

        return fromHttpResponseParams(p, tagsMap, deviceUserMap, deviceClaudeDesktopInfoMap, null);
    }

    public static AgentQueryRecord fromHttpResponseParams(
            HttpResponseParams p,
            Map<String, String> tagsMap,
            Map<String, String> deviceUserMap,
            Map<String, Map<String, ClaudeDesktopInfo>> deviceClaudeDesktopInfoMap,
            Map<String, String> claudeOrganizations) {

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
                serviceId = serviceId + "-" + claudeOrgLabel(orgId, claudeOrganizations);
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
        }

        String messageIdHeader   = getFirstHeader(headers, Constants.AKTO_MESSAGE_ID_HEADER);
        String spanId = (messageIdHeader != null && !messageIdHeader.isEmpty())
                ? messageIdHeader
                : "span_" + UUID.randomUUID().toString();

        String requestPayload  = p.getRequestParams().getPayload();
        // awsMetadata duplicates what's already captured as structured Trace/Span data by
        // BedrockAgentTraceParser — it can embed full tool-call outputs (file listings, page
        // dumps, etc.), so keeping a second raw copy here needlessly bloats the batch sent
        // to the agent-query-logs service.
        String responsePayload = JSONUtils.removeKey(p.getPayload() != null ? p.getPayload() : "", "awsMetadata");
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
     * Hardcoded org for devices whose Claude login reports none. A stopgap, not a mechanism: these
     * installs report no organizationUuid, so their traffic would otherwise land on the unqualified
     * shared serviceId together with every other org's — the exact collapse qualifying by org
     * exists to prevent. Remove an entry once the device reports its own org.
     *
     * Only consulted when the device map yields nothing; a reported org always wins, so a stale
     * entry here can never override the truth.
     */
    private static final Map<String, String> ORG_UUID_FALLBACK_BY_DEVICE = Collections.singletonMap(
            "lt-jarce2-it-a524fa1d", "84daf869-6de0-47c7-b91a-ba9e426f4c8b");

    /**
     * Org uuid to qualify serviceId with, or null when the host is not a shared-serviceId Claude
     * surface, the device is unknown, or that surface has no resolved login on it and the device has
     * no {@link #ORG_UUID_FALLBACK_BY_DEVICE} entry.
     *
     * The surface named in the host picks which login to read — see {@link #CLAUDE_SHARED_SURFACES}
     * for why reading the wrong one would misattribute the traffic.
     */
    private static String claudeOrgUuidForHost(String host, String deviceId,
            Map<String, Map<String, ClaudeDesktopInfo>> deviceClaudeDesktopInfoMap) {
        if (host == null || deviceId == null) {
            return null;
        }

        // Resolve the surface first, and bail before the fallback when the host names none: a
        // non-shared surface already carries an org-specific serviceId, so stamping an org onto it
        // would be wrong for an overridden device just as it is for every other one.
        String loginKey = null;
        for (String[] surface : CLAUDE_SHARED_SURFACES) {
            if (host.contains(surface[0])) {
                loginKey = surface[1];
                break;
            }
        }
        if (loginKey == null) {
            return null;
        }

        String orgUuid = null;
        Map<String, ClaudeDesktopInfo> byAgentType =
                deviceClaudeDesktopInfoMap != null ? deviceClaudeDesktopInfoMap.get(deviceId) : null;
        if (byAgentType != null) {
            ClaudeDesktopInfo info = byAgentType.get(loginKey);
            if (info != null) {
                orgUuid = info.getOrganizationUuid();
            }
        }

        if (orgUuid == null || orgUuid.isEmpty()) {
            return ORG_UUID_FALLBACK_BY_DEVICE.get(deviceId);
        }
        return orgUuid;
    }

    /**
     * Org uuid rendered as "&lt;orgName&gt;__&lt;orgType&gt;" when the directory knows the org, else
     * the uuid unchanged.
     *
     * The label exists because a raw uuid tells a reader nothing about whose traffic a service is.
     * The fallback is not just for a failed fetch: the directory answers "__unknown" for orgs it
     * cannot name, and every such org would render identically — silently merging distinct orgs
     * into one service, which is the exact collapse qualifying serviceId by org set out to prevent.
     * Falling back to the uuid keeps them apart, at the cost of staying unreadable until the
     * directory learns the org.
     *
     * Note this makes serviceId follow the org's NAME. Renaming an org in Claude changes the
     * serviceId its traffic lands under, so history before the rename stays under the old label —
     * the same trade-off any human-readable key carries.
     */
    private static String claudeOrgLabel(String orgId, Map<String, String> claudeOrganizations) {
        if (claudeOrganizations == null) {
            return orgId;
        }
        String label = claudeOrganizations.get(orgId);
        if (label == null) {
            return orgId;
        }
        label = label.trim();
        // "__unknown" (and anything else with no name half) identifies no org — see above.
        if (label.isEmpty() || label.startsWith("__")) {
            return orgId;
        }
        return label;
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

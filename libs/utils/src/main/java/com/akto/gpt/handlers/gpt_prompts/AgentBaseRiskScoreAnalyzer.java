package com.akto.gpt.handlers.gpt_prompts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.validation.ValidationException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.akto.dto.ApiCollection;
import com.akto.dto.ApiCollection.ServiceGraphEdgeInfo;
import com.akto.util.JSONUtils;
import com.mongodb.BasicDBObject;

/**
 * Sends an AI agent's serviceGraphEdges graph (its own profile + the tools/MCP servers/RAG
 * sources/workflows it's wired to) to an LLM and gets back a base risk score + reason.
 *
 * Score is one of -1, 0, 0.5, 1, 1.5, 2. -1 means the LLM could not form a meaningful judgment
 * about the agent overall (not an error - a legitimate "can't tell" outcome). An LLM/parse
 * failure is a *different* signal (an "error" key in the response, no score/reason) - see
 * processResponse().
 */
public class AgentBaseRiskScoreAnalyzer extends AzureOpenAIPromptHandler {

    public static final String AGENT_CONTEXT_JSON = "agentContextJson";

    public static final String SCORE = "score";
    public static final String REASON = "reason";

    private static final String TYPE_AGENT = "agent";
    private static final String BOT_ID_TAG = "bot-id";
    private static final int MAX_CONNECTIONS_IN_CONTEXT = 200;
    private static final int MAX_STRING_VALUE_LENGTH = 2000;

    private static final Set<Double> ALLOWED_SCORES = new HashSet<>(Arrays.asList(-1.0, 0.0, 0.5, 1.0, 1.5, 2.0));

    // Metadata keys pulled from the "agent" profile edge, as-is (no further parsing needed).
    private static final String[] AGENT_PROFILE_SCALAR_KEYS = {
        "displayName", "agentId", "orchestration", "authentication", "createdIn", "model",
        "isQuarantined", "environmentId"
    };

    // Metadata keys on the "agent" profile edge that are themselves JSON-encoded strings (object
    // or array - doesn't matter which, see putParsedJsonIfPresent).
    private static final String[] AGENT_PROFILE_JSON_STRING_KEYS = {
        "channels", "sharedWithViewers", "componentsCounts", "capabilitiesCounts"
    };

    // Metadata keys pulled from each non-agent (tool/mcp_server/rag/workflow) connection edge.
    private static final String[] CONNECTION_KEYS = {
        "description", "connectionProvider", "connectorId", "usedAs", "requiresEndUserConsent",
        "isEnabled", "toolsList", "discoveredVia"
    };

    @Override
    protected void validate(BasicDBObject queryData) throws ValidationException {
        if (!queryData.containsKey(AGENT_CONTEXT_JSON)) {
            throw new ValidationException("Missing mandatory param: " + AGENT_CONTEXT_JSON);
        }
        String agentContextJson = queryData.getString(AGENT_CONTEXT_JSON);
        if (agentContextJson == null || agentContextJson.trim().isEmpty()) {
            throw new ValidationException(AGENT_CONTEXT_JSON + " is empty.");
        }
    }

    /**
     * The key used to identify this agent for the cross-collection score cache (see
     * AgentBaseRiskScore). Preference order:
     * 1. The "bot-id" tag on ApiCollection.tagsList, when present - a stable GUID assigned by the
     *    source system (e.g. Copilot Studio), so two ApiCollection docs for the same agent always
     *    agree on it, unlike a human-readable name.
     * 2. Falls back to the agent's display name from serviceGraphEdges (see
     *    extractAgentDisplayName) for sources that don't carry a bot-id tag.
     * Returns null if neither is available - that collection just can't be cached/deduped.
     */
    public static String extractAgentCacheKey(ApiCollection collection) {
        String botId = collection.getTagValue(BOT_ID_TAG);
        if (botId != null && !botId.trim().isEmpty()) {
            return botId;
        }
        return extractAgentDisplayName(collection);
    }

    /**
     * Finds the one serviceGraphEdges edge with metadata.type == "agent" and returns its display
     * name (metadata.displayName if present, else the edge's map key), or null if no such edge
     * exists (e.g. non-Copilot-Studio sources that don't carry an agent profile edge). Used as a
     * fallback cache key by extractAgentCacheKey() when there's no bot-id tag.
     */
    public static String extractAgentDisplayName(ApiCollection collection) {
        Map<String, ServiceGraphEdgeInfo> edges = collection.getServiceGraphEdges();
        if (edges == null || edges.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, ServiceGraphEdgeInfo> entry : edges.entrySet()) {
            ServiceGraphEdgeInfo edge = entry.getValue();
            if (edge == null || edge.getMetadata() == null) {
                continue;
            }
            Object type = edge.getMetadata().get("type");
            if (TYPE_AGENT.equals(type)) {
                Object displayName = edge.getMetadata().get("displayName");
                if (displayName instanceof String && !((String) displayName).trim().isEmpty()) {
                    return (String) displayName;
                }
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Builds a compact JSON string {"agentProfile": {...}|null, "connections": [...]} from an
     * ApiCollection's serviceGraphEdges - the actual input fed to the LLM. Defensive throughout:
     * missing/malformed metadata never throws, keys are just omitted.
     */
    public static String buildAgentContextJson(ApiCollection collection) {
        try {
            JSONObject root = new JSONObject();
            Map<String, ServiceGraphEdgeInfo> edges = collection.getServiceGraphEdges();

            if (edges == null || edges.isEmpty()) {
                root.put("agentProfile", JSONObject.NULL);
                root.put("connections", new JSONArray());
                return root.toString();
            }

            JSONObject agentProfile = null;
            List<Map.Entry<String, ServiceGraphEdgeInfo>> connectionEntries = new ArrayList<>();

            for (Map.Entry<String, ServiceGraphEdgeInfo> entry : edges.entrySet()) {
                ServiceGraphEdgeInfo edge = entry.getValue();
                if (edge == null) {
                    continue;
                }
                Map<String, Object> metadata = edge.getMetadata();
                if (metadata != null && TYPE_AGENT.equals(metadata.get("type")) && agentProfile == null) {
                    agentProfile = buildAgentProfile(metadata);
                } else {
                    connectionEntries.add(entry);
                }
            }

            root.put("agentProfile", agentProfile != null ? agentProfile : JSONObject.NULL);

            JSONArray connections = new JSONArray();
            int originalCount = connectionEntries.size();
            int limit = Math.min(originalCount, MAX_CONNECTIONS_IN_CONTEXT);
            for (int i = 0; i < limit; i++) {
                Map.Entry<String, ServiceGraphEdgeInfo> entry = connectionEntries.get(i);
                connections.put(buildConnection(entry.getKey(), entry.getValue()));
            }
            root.put("connections", connections);
            if (originalCount > MAX_CONNECTIONS_IN_CONTEXT) {
                root.put("connectionsTruncatedFrom", originalCount);
            }

            return root.toString();
        } catch (JSONException e) {
            logger.error("AgentBaseRiskScoreAnalyzer: failed to build agent context JSON for collection "
                + collection.getId(), e);
            return "{\"agentProfile\":null,\"connections\":[]}";
        }
    }

    private static JSONObject buildAgentProfile(Map<String, Object> metadata) throws JSONException {
        JSONObject profile = new JSONObject();
        for (String key : AGENT_PROFILE_SCALAR_KEYS) {
            putIfPresent(profile, metadata, key);
        }
        for (String key : AGENT_PROFILE_JSON_STRING_KEYS) {
            putParsedJsonIfPresent(profile, metadata, key);
        }
        return profile;
    }

    private static JSONObject buildConnection(String name, ServiceGraphEdgeInfo edge) throws JSONException {
        JSONObject connection = new JSONObject();
        connection.put("name", name);
        Map<String, Object> metadata = edge != null ? edge.getMetadata() : null;
        if (metadata != null) {
            putIfPresent(connection, metadata, "type");
            for (String key : CONNECTION_KEYS) {
                putIfPresent(connection, metadata, key);
            }
        }
        return connection;
    }

    private static void putIfPresent(JSONObject target, Map<String, Object> metadata, String key) throws JSONException {
        if (metadata == null) {
            return;
        }
        Object value = metadata.get(key);
        if (value == null) {
            return;
        }
        target.put(key, truncate(value));
    }

    private static Object truncate(Object value) {
        if (value instanceof String && ((String) value).length() > MAX_STRING_VALUE_LENGTH) {
            return ((String) value).substring(0, MAX_STRING_VALUE_LENGTH) + "...(truncated)";
        }
        return value;
    }

    /**
     * Like putIfPresent, but for a metadata value that's itself a JSON-encoded string - e.g.
     * channels/sharedWithViewers/componentsCounts/capabilitiesCounts, which arrive as JSON text
     * inside a string rather than a real nested structure. Parses via JSONUtils.fromJson(Object,
     * Object.class), which takes the raw value directly (no instanceof check needed here) and,
     * for a generic Object target, naturally returns a Map for a JSON object, a List for a JSON
     * array, or a scalar - whichever the content actually is, no need to know shape up front.
     * Returns null on any parse failure, which becomes the truncated-raw-value fallback below.
     */
    private static void putParsedJsonIfPresent(JSONObject target, Map<String, Object> metadata, String key) throws JSONException {
        Object raw = metadata.get(key);
        if (raw == null) {
            return;
        }
        Object parsed = JSONUtils.fromJson(raw, Object.class);
        target.put(key, parsed != null ? parsed : truncate(raw));
    }

    @Override
    protected String getPrompt(BasicDBObject queryData) {
        String agentContextJson = queryData.getString(AGENT_CONTEXT_JSON);

        return
            "You are a security risk analyst for AI agents. You get one agent's own config plus the "
                + "tools/MCP servers/connectors it can call. Give it ONE base risk score.\n\n"
                + "SCORE: 0, 0.5, 1, 1.5, or 2. Use -1 only if you truly can't assess anything "
                + "meaningful (no recognizable tools, no other useful signal).\n\n"
                + "PER TOOL, weigh:\n"
                + "- Capability: read-only < create/modify < delete/admin/financial actions.\n"
                + "- Data sensitivity: none < internal business data < highly sensitive (financial, "
                + "health, credentials, personal data).\n"
                + "- External egress: can it send data OUTSIDE the organization - external "
                + "recipients, public/external websites or APIs, anywhere outside company control? "
                + "Internal-only actions (reading the user's own mailbox/calendar, posting in an "
                + "internal Teams/Slack channel, updating an internal record) are NOT egress by "
                + "themselves.\n\n"
                + "UNKNOWN TOOLS - be strict about this: if you cannot name the tool's actual action, "
                + "not just its category ('an integration platform', 'an MCP server', 'a connector' "
                + "is NOT enough to call it known), it is unknown. Never use hedging language "
                + "('plausible', 'unverified', 'could potentially', 'may allow', 'creates a risk of') "
                + "about an unknown tool to justify raising the score - that IS inventing a risk, "
                + "which you must not do. An unknown tool contributes NOTHING to the score, positive "
                + "or negative - score using only the tools whose specific action you can actually "
                + "name. Only use -1 if nothing about the agent, known or unknown, can be assessed "
                + "at all.\n\n"
                + "WHOLE-AGENT SCORE:\n"
                + "- Start from the riskiest tool AMONG THE ONES YOU COULD ACTUALLY ASSESS - ignore "
                + "unknown tools entirely here.\n"
                + "- If everything you can actually assess is low-capability and low-sensitivity "
                + "(e.g. read-only public search, reading the user's own mail/calendar), the score "
                + "stays LOW (0-0.5) no matter how many unknown tools are also present.\n"
                + "- Raise it if tools combine into something worse together: sensitive-data access + "
                + "a genuine external-egress path + a broad EXTERNAL-facing distribution channel, all "
                + "at once. A personal assistant reading the user's own mail/calendar and posting to "
                + "an internal Teams channel is ordinary and should stay LOW (0-0.5) - nothing there "
                + "leaves the organization.\n"
                + "- Raise it if the agent's own setup adds exposure: shared org-wide rather than "
                + "with just the owner, or a broad service identity instead of the user's own scoped "
                + "permissions. Generative/agentic behavior only raises the score when combined with "
                + "high-capability tools - by itself, with only low-risk tools, it isn't a reason to "
                + "raise the score.\n"
                + "- Raise it if the agent reports an authentication value of \"None\" - that's an "
                + "explicit signal it runs unauthenticated, and is inherently more exposed than one "
                + "running under a real identity. If authentication isn't reported at all, that's "
                + "just missing data, not a signal - don't treat it the same as \"None\".\n"
                + "- Be consistent: agents with basically the same tools and setup should get "
                + "basically the same score.\n\n"
                + "OUTPUT\n"
                + "Return ONLY this JSON, nothing else:\n"
                + "{\"score\": <0 | 0.5 | 1 | 1.5 | 2 | -1>, \"reason\": \"<1-2 plain sentences explaining why>\"}\n\n"
                + "Per-agent input (filled with your sample data):\n"
                + agentContextJson;
    }

    @Override
    protected BasicDBObject processResponse(String rawResponse) {
        BasicDBObject resp = new BasicDBObject();
        String processed = cleanJSON(rawResponse);
        processed = processed != null ? processed.trim() : "";

        if (processed.isEmpty() || processed.equalsIgnoreCase("NOT_FOUND")) {
            logger.error("AgentBaseRiskScoreAnalyzer: empty/unparseable LLM response");
            resp.put("error", "Unable to analyze - invalid response");
            return resp;
        }

        try {
            JSONObject json = new JSONObject(processed);
            double score = json.optDouble(SCORE, Double.NaN);
            String reason = json.optString(REASON, "");

            if (Double.isNaN(score) || !isAllowedScore(score)) {
                logger.error("AgentBaseRiskScoreAnalyzer: score missing or out of range in response: " + processed);
                resp.put("error", "Invalid score in response");
                return resp;
            }

            resp.put(SCORE, score);
            resp.put(REASON, reason);
        } catch (Exception e) {
            logger.error("AgentBaseRiskScoreAnalyzer: error parsing response: " + processed, e);
            resp.put("error", "Error parsing response: " + e.getMessage());
        }

        return resp;
    }

    private static boolean isAllowedScore(double score) {
        for (double allowed : ALLOWED_SCORES) {
            if (Math.abs(allowed - score) < 1e-6) {
                return true;
            }
        }
        return false;
    }
}

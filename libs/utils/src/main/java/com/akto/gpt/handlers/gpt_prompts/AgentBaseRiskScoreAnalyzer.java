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

        // TODO: rubric supplied separately by the user - replace this placeholder with the real
        // capability / data-sensitivity / exfiltration-risk rubric. The interpolation point and
        // the mandatory OUTPUT FORMAT instruction below must be preserved by whatever replaces this.
        return
            "You are a security expert assessing the base risk of an AI agent from what it is wired to. "
                + "You are given the agent's own profile (if known) and the list of tools/MCP servers/RAG "
                + "sources/workflows it connects to, extracted from a discovery graph. Tool descriptions may "
                + "be missing or thin - use judgment, and reason about capability, data sensitivity, and "
                + "whether the combination of connections could let sensitive data leak out.\n\n"
                + "OUTPUT FORMAT:\n"
                + "Return a single valid JSON object with exactly this structure:\n"
                + "{\"score\": <-1|0|0.5|1|1.5|2>, \"reason\": \"<brief explanation>\"}\n"
                + "Use -1 only if you cannot form any meaningful judgment about this agent at all.\n\n"
                + "INPUT (agent profile + connections, as JSON):\n"
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

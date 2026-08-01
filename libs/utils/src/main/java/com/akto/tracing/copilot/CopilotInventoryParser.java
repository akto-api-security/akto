package com.akto.tracing.copilot;

import com.akto.dto.ApiCollection.ServiceGraphEdgeInfo;
import com.akto.dto.tracing.TracingConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds an agent's tool graph from a Power Platform inventory row — graph edges only, same shape as the Arcade integration. */
public class CopilotInventoryParser {

    private static final Logger logger = LoggerFactory.getLogger(CopilotInventoryParser.class);
    private static final CopilotInventoryParser INSTANCE = new CopilotInventoryParser();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String AGENT_RESOURCE_TYPE = "microsoft.copilotstudio/agents";
    private static final String WEB_SEARCH_NODE = "Web search";
    private static final String MCP_OPERATION_TYPE = "MCP";
    private static final String USED_AS_KNOWLEDGE = "Knowledge";

    /** The node every agent root hangs off; must match what the trace path names its root spans. */
    public static final String USER_NODE = "User";

    // Edge metadata keys. The graph reads these to pick a node's category, icon and hover panel.
    private static final String META_TYPE = "type";
    private static final String META_EDGE_PARAM = "edgeParam";
    private static final String META_DISPLAY_NAME = "displayName";
    private static final String META_DESCRIPTION = "description";
    
    private static final String META_TOOLS_LIST = "toolsList";
    public static final String DISCOVERED_VIA_KEY = "discoveredVia";
    public static final String AGENT_ID_KEY = "agentId";
    /** Set on the agent root when this row carries only part of the agent's connectors. */
    public static final String SNAPSHOT_TRUNCATED_KEY = "inventoryTruncated";

    /** Marks an edge as built from an inventory snapshot, which is what makes it prunable. */
    public static final String DISCOVERED_VIA = "inventory";

    // Edge labels rendered on the graph.
    private static final String EDGE_PARAM_PROMPT = "prompt";
    private static final String EDGE_PARAM_TOOL_CALL = "tool call";
    private static final String EDGE_PARAM_KNOWLEDGE = "knowledge";
    private static final String EDGE_PARAM_INVOKE_FLOW = "invoke flow";
    private static final String DESCRIPTION_MCP_SERVER = "MCP server";
    private static final String DESCRIPTION_CONNECTOR = "Power Platform connector";

    // Inventory payload fields read in more than one place.
    private static final String FIELD_PROPERTIES = "properties";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_ENTRA_AGENT_ID = "entraAgentId";
    private static final String FIELD_DISPLAY_NAME = "displayName";
    private static final String FIELD_CAPABILITIES_COUNTS = "capabilitiesCounts";
    private static final String FIELD_POWER_PLATFORM_CONNECTORS = "powerPlatformConnectors";
    private static final String FIELD_OPERATIONS = "operations";
    private static final String FIELD_CONNECTOR_ID = "connectorId";
    private static final String FIELD_FLOWS = "flows";
    /** The operation-level "type" (e.g. MCP), not the resource type on the row itself. */
    private static final String FIELD_OPERATION_TYPE = "type";
    private static final String FIELD_DISTINCT_CONNECTORS = "distinctPowerPlatformConnectors";

    public static CopilotInventoryParser getInstance() {
        return INSTANCE;
    }

    public boolean canParse(String payload) {
        if (payload == null || payload.isEmpty()) return false;
        try {
            JsonNode root = OBJECT_MAPPER.readTree(payload);
            if (!AGENT_RESOURCE_TYPE.equalsIgnoreCase(root.path(FIELD_TYPE).asText(""))) return false;
            return !root.path(FIELD_PROPERTIES).path(FIELD_DISPLAY_NAME).asText("").isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /** Maps one agent row to graph edges, keyed by target service — a key differing from its target renders unlabelled. */
    public Map<String, ServiceGraphEdgeInfo> parse(String payload) {
        return parse(payload, null);
    }

    /** @param existingEdges the collection's current graph, if any — reused to merge onto a transcript-created root under either spelling it can produce. */
    public Map<String, ServiceGraphEdgeInfo> parse(String payload, Map<String, ServiceGraphEdgeInfo> existingEdges) {
        Map<String, ServiceGraphEdgeInfo> edges = new LinkedHashMap<>();
        if (payload == null || payload.isEmpty()) return edges;

        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(payload);
        } catch (Exception e) {
            return edges;
        }

        JsonNode properties = root.path(FIELD_PROPERTIES);
        // agentname -> displayName, unless a transcript already established a root under a different spelling — see existingEdges above.
        String displayName = properties.path(FIELD_DISPLAY_NAME).asText("");
        String agentId = resolveAgentId(root, properties);
        String baseNode = resolveAgentNode(displayName, existingEdges);
        if (baseNode.isEmpty()) return edges;

        // Display names aren't unique — when another agent already owns this node name, take a private namespace instead of silently hiding one.
        String owner = rootAgentId(existingEdges, baseNode);
        String suffix = owner != null && !owner.equals(agentId) ? "_" + shortId(agentId) : "";
        String agentNode = baseNode + suffix;

        edges.put(agentNode, new ServiceGraphEdgeInfo(USER_NODE, agentNode, agentMetadata(root, properties)));

        addConnectorEdges(edges, agentNode, properties.path(FIELD_POWER_PLATFORM_CONNECTORS), suffix, existingEdges);
        addFlowEdges(edges, agentNode, properties.path(FIELD_FLOWS), suffix, properties.path(FIELD_CAPABILITIES_COUNTS));

        if (properties.path("isWebSearchEnabledForKnowledge").asBoolean(false)) {
            // Through uniqueKey like every other leaf: an agent named "Web search" would otherwise
            // overwrite its own root here and disappear from the graph.
            String node = uniqueKey(edges, WEB_SEARCH_NODE + suffix);
            edges.put(node, edge(agentNode, node, TracingConstants.SpanKind.RAG, EDGE_PARAM_KNOWLEDGE));
        }

        // Stamp the owning agent on every edge, not just the root, so pruning can scope a snapshot even where two agents share a node.
        for (ServiceGraphEdgeInfo edge : edges.values()) {
            edge.getMetadata().put(AGENT_ID_KEY, agentId);
        }

        if (isTruncated(properties)) {
            edges.get(agentNode).getMetadata().put(SNAPSHOT_TRUNCATED_KEY, true);
        }

        return edges;
    }

    /** Whether this row reports more connectors than it carries — Microsoft caps inventory at 200/type and returns a random subset above that. */
    private boolean isTruncated(JsonNode properties) {
        JsonNode connectors = properties.path(FIELD_POWER_PLATFORM_CONNECTORS);
        int carried = connectors.isArray() ? connectors.size() : 0;
        return properties.path(FIELD_CAPABILITIES_COUNTS).path(FIELD_DISTINCT_CONNECTORS).asInt(0) > carried;
    }

    /** The agent that owns an inventory-built root at {@code key}, or null if none does. */
    private String rootAgentId(Map<String, ServiceGraphEdgeInfo> existingEdges, String key) {
        if (existingEdges == null) return null;
        ServiceGraphEdgeInfo root = existingEdges.get(key);
        if (root == null || root.getMetadata() == null) return null;
        // A transcript-built root carries no discoveredVia and is not an owner — inventory merges onto it instead.
        if (!DISCOVERED_VIA.equals(root.getMetadata().get(DISCOVERED_VIA_KEY))) return null;
        Object agentId = root.getMetadata().get(AGENT_ID_KEY);
        return agentId instanceof String && !((String) agentId).isEmpty() ? (String) agentId : null;
    }

    /** Enough of the agent id to disambiguate a node name without making it unreadable. */
    private static String shortId(String agentId) {
        if (agentId == null || agentId.isEmpty()) return "2";
        return agentId.length() > 8 ? agentId.substring(0, 8) : agentId;
    }

    /** agentID -> entraAgentId (the canonical id elsewhere in Akto); falls back to the internal GUID for Lite/pre-Entra-identity agents. */
    private String resolveAgentId(JsonNode root, JsonNode properties) {
        String entraAgentId = properties.path(FIELD_ENTRA_AGENT_ID).asText("");
        return !entraAgentId.isEmpty() ? entraAgentId : root.path(FIELD_NAME).asText("");
    }

    /** Every field is optional in practice, so nothing here assumes presence. */
    private Map<String, Object> agentMetadata(JsonNode root, JsonNode properties) {
        Map<String, Object> meta = new HashMap<>();
        meta.put(META_TYPE, TracingConstants.SpanKind.AGENT);
        meta.put(META_EDGE_PARAM, EDGE_PARAM_PROMPT);
        meta.put(DISCOVERED_VIA_KEY, DISCOVERED_VIA);
        // agentId is stamped on every edge, including this one, at the end of parse().
        meta.put(META_DISPLAY_NAME, properties.path(FIELD_DISPLAY_NAME).asText(""));

        putIfPresent(meta, "environmentId", properties.path("environmentId"));
        putIfPresent(meta, "model", properties.path("model"));
        putIfPresent(meta, "orchestration", properties.path("orchestration"));
        putIfPresent(meta, "authentication", properties.path("authentication"));
        putIfPresent(meta, "createdIn", properties.path("createdIn"));
        putIfPresent(meta, "ownerId", properties.path("ownerId"));
        putIfPresent(meta, "schemaName", properties.path("schemaName"));
        putIfPresent(meta, "lastPublishedAt", properties.path("lastPublishedAt"));
        putIfPresent(meta, "entraAppId", properties.path("entraAppId"));

        putIfBoolean(meta, "isQuarantined", properties.path("isQuarantined"));
        // Nested objects are stored as their JSON text since the graph's hover panel renders strings, not structured fields.
        putIfJson(meta, "channels", properties.path("channels"));
        putIfJson(meta, "sharedWithViewers", properties.path("sharedWithViewers"));
        // capabilitiesCounts is partly documented, componentsCounts isn't — both kept since neither supersedes the other.
        putIfJson(meta, "componentsCounts", properties.path("componentsCounts"));
        putIfJson(meta, FIELD_CAPABILITIES_COUNTS, properties.path(FIELD_CAPABILITIES_COUNTS));

        return meta;
    }

    private void addConnectorEdges(Map<String, ServiceGraphEdgeInfo> edges, String agentNode, JsonNode connectors,
                                   String suffix, Map<String, ServiceGraphEdgeInfo> existingEdges) {
        if (!connectors.isArray()) return;

        // A maker's connector label and a transcript's own name for the same server differ; normalise to match and enrich instead of duplicating.
        Map<String, String> observedNodes = suffix.isEmpty()
            ? nodesByNormalizedName(existingEdges) : Collections.emptyMap();

        for (JsonNode connector : connectors) {
            String connectorId = connector.path(FIELD_CONNECTOR_ID).asText("");
            if (connectorId.isEmpty()) continue;

            JsonNode operations = connector.path(FIELD_OPERATIONS);
            String spanKind = spanKindForConnector(operations);
            boolean isMcp = TracingConstants.SpanKind.MCP_SERVER.equals(spanKind);
            // Inventory reports one entry per operation; the first carries the connector-level facts (the only one, for MCP servers).
            JsonNode primary = operations.isArray() && operations.size() > 0
                ? operations.get(0) : OBJECT_MAPPER.createObjectNode();

            String label = sanitizeServiceName(prettifyConnectorId(connectorId));
            String observed = observedNodes.get(normalizeForMatch(label));
            String node = observed != null ? observed : uniqueKey(edges, label + suffix);

            ServiceGraphEdgeInfo info = edge(agentNode, node, spanKind, EDGE_PARAM_TOOL_CALL);
            Map<String, Object> meta = info.getMetadata();
            if (observed != null) {
                // Traffic is stronger evidence than a config snapshot — enrich the node but keep it outside the discoveredVia=inventory prune scope.
                meta.remove(DISCOVERED_VIA_KEY);
            }
            meta.put(FIELD_CONNECTOR_ID, connectorId);
            // Flattened into "description"/"toolsList" since that's what the graph's hover panel actually reads, not a raw operations blob.
            meta.put(META_DESCRIPTION, isMcp ? DESCRIPTION_MCP_SERVER : DESCRIPTION_CONNECTOR);
            putIfPresent(meta, "usedAs", primary.path("usedAs"));
            putIfPresent(meta, "connectionProvider", primary.path("connectionProvider"));
            putIfPresent(meta, "whenCanBeUsed", primary.path("whenCanBeUsed"));
            putIfBoolean(meta, "isEnabled", primary.path("isEnabled"));
            putIfBoolean(meta, "requiresEndUserConsent", primary.path("requiresEndUserConsent"));

            List<String> operationIds = operationIds(operations, isMcp);
            if (!operationIds.isEmpty()) {
                meta.put(META_TOOLS_LIST, operationIds);
            }

            edges.put(node, info);
        }
    }

    /** Indexes traffic-derived nodes by normalizeForMatch; a name claimed by more than one node is dropped rather than guessed. */
    private Map<String, String> nodesByNormalizedName(Map<String, ServiceGraphEdgeInfo> existingEdges) {
        if (existingEdges == null || existingEdges.isEmpty()) return Collections.emptyMap();

        Map<String, String> index = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (Map.Entry<String, ServiceGraphEdgeInfo> entry : existingEdges.entrySet()) {
            ServiceGraphEdgeInfo edge = entry.getValue();
            if (edge == null || edge.getMetadata() == null) continue;
            if (DISCOVERED_VIA.equals(edge.getMetadata().get(DISCOVERED_VIA_KEY))) continue;

            String normalized = normalizeForMatch(entry.getKey());
            if (normalized.isEmpty()) continue;
            if (index.put(normalized, entry.getKey()) != null) ambiguous.add(normalized);
        }
        index.keySet().removeAll(ambiguous);
        return index;
    }

    /** Folds case/separators/a trailing "mcp server" suffix so a maker's label and a transcript's own name can meet. */
    static String normalizeForMatch(String name) {
        if (name == null) return "";
        String normalized = name.toLowerCase().replaceAll("[^\\p{L}\\p{N}]", "");
        for (String noise : new String[] { "mcpserver", "mcp", "server" }) {
            if (normalized.length() > noise.length() && normalized.endsWith(noise)) {
                return normalized.substring(0, normalized.length() - noise.length());
            }
        }
        return normalized;
    }

    /** "InvokeServer" is the placeholder every MCP connector carries; listing it as a tool is noise. */
    private List<String> operationIds(JsonNode operations, boolean isMcp) {
        List<String> ids = new ArrayList<>();
        if (isMcp || !operations.isArray()) return ids;
        for (JsonNode operation : operations) {
            String id = operation.path("operationId").asText("");
            if (!id.isEmpty() && !ids.contains(id)) ids.add(id);
        }
        return ids;
    }

    private void addFlowEdges(Map<String, ServiceGraphEdgeInfo> edges, String agentNode, JsonNode flows,
                              String suffix, JsonNode capabilitiesCounts) {
        int named = 0;
        if (flows.isArray()) {
            for (JsonNode flow : flows) {
                // Shape unconfirmed (no tenant seen has had a non-empty flows array) — accept a string or named object, skip anything else.
                String name = flow.isTextual() ? flow.asText("") : firstNonEmpty(flow, FIELD_DISPLAY_NAME, FIELD_NAME);
                if (name.isEmpty()) continue;

                String node = uniqueKey(edges, sanitizeServiceName(name) + suffix);
                edges.put(node, edge(agentNode, node, TracingConstants.SpanKind.WORKFLOW, EDGE_PARAM_INVOKE_FLOW));
                named++;
            }
        }

        // Neither flows[] nor distinctFlows is documented; logging a mismatch is the only cross-check available: https://learn.microsoft.com/en-us/microsoft-copilot-studio/admin-agent-inventory
        int expected = capabilitiesCounts.path("distinctFlows").asInt(0);
        if (expected > named) {
            logger.warn("Copilot inventory: agent {} reports {} flow(s), {} could be named. flows={}",
                agentNode, expected, named, flows);
        }
    }

    /** MCP is checked before usedAs — an MCP server is also reported as a Tool, and MCP is the more useful classification. */
    private String spanKindForConnector(JsonNode operations) {
        if (!operations.isArray()) return TracingConstants.SpanKind.TOOL;

        boolean knowledge = false;
        for (JsonNode operation : operations) {
            if (MCP_OPERATION_TYPE.equalsIgnoreCase(operation.path(FIELD_OPERATION_TYPE).asText(""))) {
                return TracingConstants.SpanKind.MCP_SERVER;
            }
            if (USED_AS_KNOWLEDGE.equalsIgnoreCase(operation.path("usedAs").asText(""))) {
                knowledge = true;
            }
        }
        return knowledge ? TracingConstants.SpanKind.RAG : TracingConstants.SpanKind.TOOL;
    }

    /** Connector ids are escape-encoded (-5f/-20) with a publisher prefix and hex suffix; falls back to the decoded or raw id if the shape doesn't match. */
    static String prettifyConnectorId(String connectorId) {
        String decoded = connectorId;
        try {
            decoded = decoded.replaceAll("-5[fF]", "_").replaceAll("-20", " ");
        } catch (Exception e) {
            return connectorId;
        }

        // shared_<publisherPrefix>_<name>_<hex>
        String[] parts = decoded.split("_");
        if (parts.length >= 4 && "shared".equals(parts[0])) {
            StringBuilder name = new StringBuilder();
            for (int i = 2; i < parts.length - 1; i++) {
                if (name.length() > 0) name.append(' ');
                name.append(parts[i]);
            }
            if (name.length() > 0) return name.toString().trim();
        }
        // shared_<name> — the common first-party form, e.g. shared_excelonlinebusiness
        if (parts.length == 2 && "shared".equals(parts[0]) && !parts[1].isEmpty()) {
            return parts[1];
        }
        return decoded.isEmpty() ? connectorId : decoded;
    }

    /** Picks the agent root key: reuses an existing transcript-created root under either spelling, else sanitizes displayName directly. */
    private String resolveAgentNode(String displayName, Map<String, ServiceGraphEdgeInfo> existingEdges) {
        String defaultNode = sanitizeServiceName(displayName);
        if (existingEdges == null || existingEdges.isEmpty()) return defaultNode;

        if (existingEdges.containsKey(defaultNode)) return defaultNode;

        // The transcript fallback names its root from the bot-name tag, already hyphen-sanitized by the Go binary — a different result than sanitizing displayName directly.
        String botNameStyleNode = sanitizeServiceName(sanitizeBotNameStyle(displayName));
        if (existingEdges.containsKey(botNameStyleNode)) return botNameStyleNode;

        return defaultNode;
    }

    /** Java port of sanitizeBotName in extractor.go; must stay identical to both or it stops matching the fallback root it detects. */
    private static String sanitizeBotNameStyle(String name) {
        if (name == null || name.isEmpty()) return "";
        String sanitized = name.replaceAll("[^\\p{L}\\p{N}-]+", "-");
        sanitized = sanitized.replaceAll("-+", "-");
        return sanitized.replaceAll("^-+", "").replaceAll("-+$", "");
    }

    /** \p{L}/\p{N} preserves non-Latin names instead of collapsing to underscores; must stay identical to sanitizeServiceName in HttpCallParser. */
    static String sanitizeServiceName(String name) {
        if (name == null) return "";
        return name.replaceAll("[^\\p{L}\\p{N}_\\-]", "_");
    }

    /** Two connectors can prettify to the same label; suffix rather than silently collapsing them. */
    private String uniqueKey(Map<String, ServiceGraphEdgeInfo> edges, String candidate) {
        if (candidate.isEmpty()) candidate = "unknown";
        if (!edges.containsKey(candidate)) return candidate;
        for (int i = 2; i < 100; i++) {
            String next = candidate + "_" + i;
            if (!edges.containsKey(next)) return next;
        }
        return candidate;
    }

    private ServiceGraphEdgeInfo edge(String source, String target, String spanKind, String edgeParam) {
        Map<String, Object> meta = new HashMap<>();
        meta.put(META_TYPE, spanKind);
        meta.put(META_EDGE_PARAM, edgeParam);
        meta.put(DISCOVERED_VIA_KEY, DISCOVERED_VIA);
        return new ServiceGraphEdgeInfo(source, target, meta);
    }

    private void putIfBoolean(Map<String, Object> meta, String key, JsonNode value) {
        if (value != null && value.isBoolean()) {
            meta.put(key, value.asBoolean());
        }
    }

    /** Stores an object/array node as its JSON text, skipping the ones the payload omits. */
    private void putIfJson(Map<String, Object> meta, String key, JsonNode value) {
        if (value != null && !value.isMissingNode() && !value.isNull()) {
            meta.put(key, value.toString());
        }
    }

    private void putIfPresent(Map<String, Object> meta, String key, JsonNode value) {
        if (value != null && !value.isMissingNode() && !value.isNull()) {
            String text = value.asText("");
            if (!text.isEmpty()) meta.put(key, text);
        }
    }

    private String firstNonEmpty(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = node.path(field).asText("");
            if (!value.isEmpty()) return value;
        }
        return "";
    }
}

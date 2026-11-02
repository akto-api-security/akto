package com.akto.tracing.alibaba;

import com.akto.dto.ApiCollection.ServiceGraphEdgeInfo;
import com.akto.dto.tracing.Span;
import com.akto.dto.tracing.Trace;
import com.akto.dto.tracing.TracingConstants;
import com.akto.tracing.TraceParseResult;
import com.akto.tracing.TraceParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Parses the {@code alibabaMetadata} block the AKTO Alibaba Cloud connector puts in
 * {@code responsePayload} (source tag {@code ALIBABA_CLOUD}) — the Alibaba counterpart of
 * {@link com.akto.tracing.bedrock.BedrockAgentTraceParser} and {@code awsMetadata}.
 *
 * <p>Three Alibaba products, told apart by {@code alibaba-source}:
 * <ul>
 *   <li>{@code AI_GATEWAY} — User → gateway → MCP server → MCP tool, gateway → Model API → model,
 *       gateway → Agent API → model. Gateway hover: endpoint, plug-ins (its policies), consumers.</li>
 *   <li>{@code AGENTRUN} — User → agent → model, agent → tools. Agent hover: RAM execution role,
 *       its RAM policies, configured tools.</li>
 *   <li>{@code MODEL_STUDIO} — User → model (direct calls), or User → agent app → model.</li>
 * </ul>
 *
 * <p>Both traffic and discovery messages carry the block. Discovery ones (they have
 * {@code resourceType}) draw the inventory — a gateway's MCP servers and tools, its Model and
 * Agent APIs, an agent's role — before any traffic; they produce graph edges but no trace.
 *
 * <p>Edges use the same metadata conventions as the Bedrock parser ({@code type},
 * {@code edgeParam}, {@code role}, {@code policies}, {@code toolsList}, {@code totalToolCalls},
 * {@code lastToolInvoked}, {@code endpointUrl}, {@code description}) plus {@code plugins},
 * {@code consumers}, {@code region}, {@code workspaceId}, so the dashboard's agent graph draws
 * them as it draws Bedrock's. The map key is the node id (= the edge's target).
 */
public class AlibabaCloudTraceParser implements TraceParser {

    public static final String METADATA_KEY = "alibabaMetadata";
    private static final String SOURCE_TYPE = "alibaba-cloud";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final AlibabaCloudTraceParser INSTANCE = new AlibabaCloudTraceParser();

    static final String SRC_GATEWAY = "AI_GATEWAY";
    static final String SRC_AGENTRUN = "AGENTRUN";
    static final String SRC_MODEL_STUDIO = "MODEL_STUDIO";
    private static final String USER = "User";

    private static final String[] FAILURE_INDICATORS = {
        "error", "not found", "failed", "invalid", "denied", "exception", "unable to"
    };

    public static AlibabaCloudTraceParser getInstance() {
        return INSTANCE;
    }

    private JsonNode parseToJsonNode(Object input) throws Exception {
        if (input instanceof JsonNode) return (JsonNode) input;
        String json = input instanceof String ? (String) input : OBJECT_MAPPER.writeValueAsString(input);
        return OBJECT_MAPPER.readTree(json);
    }

    /** A traffic record names its product; a discovery record has a resource type. */
    @Override
    public boolean canParse(Object input) {
        if (input == null) return false;
        try {
            JsonNode m = parseToJsonNode(input);
            return m.isObject() && (!text(m, "alibaba-source").isEmpty() || !text(m, "resourceType").isEmpty());
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isDiscovery(Object input) {
        try {
            return !text(parseToJsonNode(input), "resourceType").isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- traces

    @Override
    public TraceParseResult parse(Object input) throws Exception {
        return parse(input, null);
    }

    /** One trace per traffic record, spans from traceData.executionFlow; real token counts from usage. */
    public TraceParseResult parse(Object input, String botName) throws Exception {
        JsonNode m = parseToJsonNode(input);
        if (!canParse(m)) {
            throw new Exception("Invalid Alibaba Cloud metadata: " + m);
        }
        String agent = firstNonEmpty(text(m, "agent-name"), text(m, "app-name"), text(m, "gateway-name"), botName, "Alibaba Cloud");
        String model = firstNonEmpty(text(m, "model"), "unknown");
        String traceId = UUID.randomUUID().toString();
        String rootSpanId = UUID.randomUUID().toString();
        JsonNode traceData = m.path("traceData");
        List<Span> spans = buildSpans(traceId, rootSpanId, traceData.path("executionFlow"));

        int in = m.path("usage").path("inputTokens").asInt(0);
        int out = m.path("usage").path("outputTokens").asInt(0);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sourceType", SOURCE_TYPE);
        metadata.put("alibabaSource", text(m, "alibaba-source"));
        metadata.put("model", model);
        putIfPresent(metadata, "region", text(m, "region"));
        putIfPresent(metadata, "gatewayName", text(m, "gateway-name"));
        putIfPresent(metadata, "apiName", text(m, "api-name"));
        putIfPresent(metadata, "workspaceId", text(m, "workspace-id"));
        putIfPresent(metadata, "consumer", text(m, "alibaba-consumer"));
        putIfPresent(metadata, "executionRole", text(m, "execution-role"));
        JsonNode toolsSummary = traceData.path("toolsSummary");
        if (!toolsSummary.isMissingNode()) {
            metadata.put("toolCount", toolsSummary.path("totalToolCalls").asInt(0));
            metadata.put("executionPattern", toolsSummary.path("executionPattern").asText(""));
        }

        Map<String, Object> rootInput = new HashMap<>();
        rootInput.put("model", model);
        rootInput.put("agent", agent);
        Map<String, Object> rootOutput = new HashMap<>();
        rootOutput.put("status", "processed");
        putIfPresent(rootOutput, "finishReason", text(m, "finishReason"));

        long now = System.currentTimeMillis();
        long latency = m.path("latencyMs").asLong(0);
        int statusCode = m.path("statusCode").asInt(200);
        Trace trace = Trace.builder()
            .id(traceId)
            .rootSpanId(rootSpanId)
            .aiAgentName(agent)
            .name(agent + " - " + model)
            .startTimeMillis(now - latency)
            .endTimeMillis(now)
            .status(statusCode >= 400 ? "error" : "success")
            .totalSpans(spans.size())
            .totalTokens(in + out)
            .totalInputTokens(in)
            .totalOutputTokens(out)
            .rootInput(rootInput)
            .rootOutput(rootOutput)
            .spanIds(spans.stream().map(Span::getId).collect(Collectors.toList()))
            .metadata(metadata)
            .build();

        Map<String, Object> resultMetadata = new HashMap<>();
        resultMetadata.put("botName", agent);
        resultMetadata.put("model", model);
        resultMetadata.put("alibabaSource", text(m, "alibaba-source"));
        return TraceParseResult.builder()
            .trace(trace)
            .spans(spans)
            .workflowId(agent)
            .sourceIdentifier(traceId)
            .metadata(resultMetadata)
            .build();
    }

    private List<Span> buildSpans(String traceId, String rootSpanId, JsonNode executionFlow) {
        List<Span> spans = new ArrayList<>();
        if (!executionFlow.isArray()) return spans;
        String parent = rootSpanId;
        int depth = 0;
        for (JsonNode step : executionFlow) {
            String id = UUID.randomUUID().toString();
            String type = step.path("type").asText("unknown");
            String result = step.path("result").asText("");
            boolean failed = looksLikeFailure(result);

            Map<String, Object> input = new HashMap<>();
            input.put("type", type);
            Map<String, Object> meta = new HashMap<>();
            meta.put("type", type);
            meta.put("sourceType", SOURCE_TYPE);
            if ("tool-call".equalsIgnoreCase(type)) {
                input.put("tool", step.path("tool").asText("unknown"));
                input.put("action", step.path("action").asText("unknown"));
                meta.put("tool", step.path("tool").asText("unknown"));
                meta.put("action", step.path("action").asText("unknown"));
                meta.put("toolUseId", step.path("toolUseId").asText(""));
                meta.put("result", result);
            }
            if (step.has("description")) meta.put("description", step.path("description").asText(""));
            Map<String, Object> output = new HashMap<>();
            output.put("completed", !failed);
            if (!result.isEmpty()) output.put("result", result);

            long t = System.currentTimeMillis();
            spans.add(Span.builder()
                .id(id).traceId(traceId).parentSpanId(parent)
                .spanKind(spanKind(type))
                .name(step.path("name").asText(step.path("tool").asText("unknown")))
                .startTimeMillis(t).endTimeMillis(t)
                .status(failed ? "error" : "success")
                .input(input).output(output).metadata(meta)
                .depth(depth++)
                .tags(Arrays.asList(SOURCE_TYPE, type))
                .build());
            parent = id;
        }
        return spans;
    }

    private static String spanKind(String type) {
        if ("agent".equalsIgnoreCase(type)) return TracingConstants.SpanKind.AGENT;
        if ("tool-call".equalsIgnoreCase(type)) return TracingConstants.SpanKind.TOOL;
        if ("llm-call".equalsIgnoreCase(type)) return TracingConstants.SpanKind.LLM;
        return TracingConstants.SpanKind.TASK;
    }

    private static boolean looksLikeFailure(String result) {
        if (result == null || result.isEmpty()) return false;
        String lower = result.toLowerCase();
        for (String s : FAILURE_INDICATORS) if (lower.contains(s)) return true;
        return false;
    }

    // ---------------------------------------------------------------- service graph

    @Override
    public Map<String, ServiceGraphEdgeInfo> extractServiceGraph(Object input) throws Exception {
        return extractServiceGraph(input, null);
    }

    /** @param botName the HTTP-level bot-name tag (also the collection name): the fallback node name. */
    public Map<String, ServiceGraphEdgeInfo> extractServiceGraph(Object input, String botName) throws Exception {
        JsonNode m = parseToJsonNode(input);
        if (!canParse(m)) {
            throw new Exception("Invalid Alibaba Cloud metadata for service graph");
        }
        Map<String, ServiceGraphEdgeInfo> edges = new LinkedHashMap<>();
        String resourceType = text(m, "resourceType");
        String source = firstNonEmpty(text(m, "alibaba-source"), sourceOfResourceType(resourceType));

        if (SRC_GATEWAY.equals(source)) {
            if (resourceType.isEmpty()) gatewayTraffic(m, botName, edges);
            else gatewayDiscovery(m, resourceType, botName, edges);
        } else if (SRC_AGENTRUN.equals(source)) {
            agentRun(m, botName, edges);
        } else if (SRC_MODEL_STUDIO.equals(source)) {
            modelStudio(m, botName, edges);
        }
        return edges;
    }

    private static String sourceOfResourceType(String resourceType) {
        if (resourceType.startsWith("AGENTRUN")) return SRC_AGENTRUN;
        if (resourceType.startsWith("MODEL_STUDIO")) return SRC_MODEL_STUDIO;
        return resourceType.isEmpty() ? "" : SRC_GATEWAY;
    }

    /** User → gateway node, carrying the gateway's hover details. Returns the gateway's node id. */
    private String gatewayNode(JsonNode m, String botName, Map<String, ServiceGraphEdgeInfo> edges) {
        String gateway = firstNonEmpty(text(m, "gateway-name"), botName, text(m, "gateway-id"), "AI Gateway");
        Map<String, Object> meta = typed(TracingConstants.SpanKind.GATEWAY, "AI Gateway");
        putIfPresent(meta, "endpointUrl", text(m, "gateway-endpoint"));
        putList(meta, "plugins", firstList(m, "gateway-plugins", "plugins"));
        putList(meta, "consumers", list(m, "consumers"));
        putIfPresent(meta, "region", text(m, "region"));
        putIfPresent(meta, "description", "Alibaba Cloud AI Gateway" + suffix(text(m, "gateway-id")));
        edges.put(gateway, new ServiceGraphEdgeInfo(USER, gateway, meta));
        return gateway;
    }

    private void gatewayTraffic(JsonNode m, String botName, Map<String, ServiceGraphEdgeInfo> edges) {
        String gateway = gatewayNode(m, botName, edges);
        String apiType = text(m, "api-type").toUpperCase();
        String mcpServer = text(m, "mcp-server");
        String model = text(m, "model");

        if ("MCP".equals(apiType) || !mcpServer.isEmpty()) {
            String server = firstNonEmpty(mcpServer, text(m, "api-name"), "MCP Server");
            Map<String, Object> sMeta = typed(TracingConstants.SpanKind.MCP_SERVER, "MCP Server");
            putIfPresent(sMeta, "endpointUrl", text(m, "endpoint"));
            edges.put(server, new ServiceGraphEdgeInfo(gateway, server, sMeta));
            String tool = text(m, "mcp-tool");
            if (!tool.isEmpty()) {
                Map<String, Object> tMeta = typed(TracingConstants.SpanKind.MCP_TOOL, "MCP tool call");
                tMeta.put("toolName", tool);
                tMeta.put("lastToolInvoked", tool);
                tMeta.put("totalToolCalls", Math.max(1, m.path("traceData").path("toolsSummary").path("totalToolCalls").asInt(1)));
                edges.put(tool, new ServiceGraphEdgeInfo(server, tool, tMeta));
            }
            return;
        }

        // LLM (Model API) or AGENT (Agent API): gateway → API → model, API → tools it called.
        boolean agentApi = "AGENT".equals(apiType);
        String api = text(m, "api-name");
        String caller = gateway;
        if (!api.isEmpty()) {
            Map<String, Object> aMeta = typed(agentApi ? TracingConstants.SpanKind.AGENT : "api", agentApi ? "Agent API" : "Model API");
            putIfPresent(aMeta, "endpointUrl", text(m, "endpoint"));
            putList(aMeta, "toolsList", list(m, "configured-tools"));
            edges.put(api, new ServiceGraphEdgeInfo(gateway, api, aMeta));
            caller = api;
        }
        modelEdge(caller, model, edges);
        toolEdges(caller, m, edges);
    }

    private void gatewayDiscovery(JsonNode m, String resourceType, String botName, Map<String, ServiceGraphEdgeInfo> edges) {
        String gateway = gatewayNode(m, botName, edges);
        switch (resourceType) {
            case "GATEWAY":
                for (String s : list(m, "mcpServers")) {
                    edges.putIfAbsent(s, new ServiceGraphEdgeInfo(gateway, s, typed(TracingConstants.SpanKind.MCP_SERVER, "MCP Server")));
                }
                for (String a : list(m, "modelApis")) {
                    edges.putIfAbsent(a, new ServiceGraphEdgeInfo(gateway, a, typed("api", "Model API")));
                }
                for (String a : list(m, "agentApis")) {
                    edges.putIfAbsent(a, new ServiceGraphEdgeInfo(gateway, a, typed(TracingConstants.SpanKind.AGENT, "Agent API")));
                }
                break;
            case "MCP_SERVER": {
                String server = firstNonEmpty(text(m, "resource-name"), text(m, "mcp-server"));
                if (server.isEmpty()) break;
                Map<String, Object> sMeta = typed(TracingConstants.SpanKind.MCP_SERVER, "MCP Server");
                putIfPresent(sMeta, "endpointUrl", text(m, "path"));
                List<String> tools = list(m, "tools");
                putList(sMeta, "toolsList", tools);
                edges.put(server, new ServiceGraphEdgeInfo(gateway, server, sMeta));
                for (String tool : tools) {
                    Map<String, Object> tMeta = typed(TracingConstants.SpanKind.MCP_TOOL, "MCP tool");
                    tMeta.put("toolName", tool);
                    edges.putIfAbsent(tool, new ServiceGraphEdgeInfo(server, tool, tMeta));
                }
                break;
            }
            default: {  // MODEL_API, AGENT_API, MCP_API …
                String api = text(m, "resource-name");
                if (api.isEmpty()) break;
                boolean agentApi = resourceType.contains("AGENT");
                Map<String, Object> aMeta = typed(agentApi ? TracingConstants.SpanKind.AGENT : "api", agentApi ? "Agent API" : "Model API");
                putIfPresent(aMeta, "endpointUrl", text(m, "basePath"));
                edges.put(api, new ServiceGraphEdgeInfo(gateway, api, aMeta));
                modelEdge(api, text(m, "model"), edges);
            }
        }
    }

    private void agentRun(JsonNode m, String botName, Map<String, ServiceGraphEdgeInfo> edges) {
        String agent = firstNonEmpty(text(m, "agent-name"), text(m, "resource-name"), botName, "AgentRun Agent");
        Map<String, Object> meta = typed(TracingConstants.SpanKind.AGENT, "AI Agent");
        putIfPresent(meta, "role", text(m, "execution-role"));
        putList(meta, "policies", list(m, "role-policies"));
        putList(meta, "toolsList", list(m, "configured-tools"));
        putIfPresent(meta, "description", firstNonEmpty(text(m, "description"), "AgentRun agent" + suffix(text(m, "agent-id"))));
        putIfPresent(meta, "region", text(m, "region"));
        putIfPresent(meta, "workspaceId", text(m, "workspace-id"));
        edges.put(agent, new ServiceGraphEdgeInfo(USER, agent, meta));
        modelEdge(agent, text(m, "model"), edges);
        toolEdges(agent, m, edges);
    }

    private void modelStudio(JsonNode m, String botName, Map<String, ServiceGraphEdgeInfo> edges) {
        String app = firstNonEmpty(text(m, "app-name"), text(m, "app-id"),
            "MODEL_STUDIO_AGENT".equals(text(m, "resourceType")) ? firstNonEmpty(text(m, "resource-name"), botName) : "");
        String model = text(m, "model");
        if (!app.isEmpty()) {
            Map<String, Object> meta = typed(TracingConstants.SpanKind.AGENT, "AI Agent");
            putIfPresent(meta, "description", "Model Studio agent application");
            putIfPresent(meta, "workspaceId", text(m, "workspace-id"));
            putList(meta, "toolsList", firstList(m, "configured-tools", "tools"));
            edges.put(app, new ServiceGraphEdgeInfo(USER, app, meta));
            modelEdge(app, model, edges);
            toolEdges(app, m, edges);
            return;
        }
        if (model.isEmpty() || "unknown".equals(model)) return;
        // A direct model call: the model itself is the node the user talks to.
        Map<String, Object> meta = typed("llmCall", "Call to model");
        putIfPresent(meta, "description", "Model Studio model, called directly");
        putIfPresent(meta, "workspaceId", text(m, "workspace-id"));
        putIfPresent(meta, "region", text(m, "region"));
        putList(meta, "toolsList", list(m, "configured-tools"));
        edges.put(model, new ServiceGraphEdgeInfo(USER, model, meta));
    }

    private void modelEdge(String from, String model, Map<String, ServiceGraphEdgeInfo> edges) {
        if (model == null || model.isEmpty() || "unknown".equals(model)) return;
        edges.put(model, new ServiceGraphEdgeInfo(from, model, typed("llmCall", "Call to model")));
    }

    /** One node per tool this call actually used (traceData.toolsSummary.tools). */
    private void toolEdges(String from, JsonNode m, Map<String, ServiceGraphEdgeInfo> edges) {
        JsonNode summary = m.path("traceData").path("toolsSummary");
        int total = summary.path("totalToolCalls").asInt(0);
        for (String tool : list(summary, "tools")) {
            Map<String, Object> meta = typed(TracingConstants.SpanKind.TOOL, "tool call");
            meta.put("toolName", tool);
            meta.put("lastToolInvoked", tool);
            meta.put("totalToolCalls", total);
            edges.put(tool, new ServiceGraphEdgeInfo(from, tool, meta));
        }
    }

    // ---------------------------------------------------------------- helpers

    private static Map<String, Object> typed(String type, String edgeParam) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("type", type);
        meta.put("edgeParam", edgeParam);
        meta.put("cloud", "ALIBABA_CLOUD");
        return meta;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isValueNode() ? v.asText("").trim() : "";
    }

    /** An array of strings, or a comma-separated string. */
    private static List<String> list(JsonNode node, String field) {
        JsonNode v = node.path(field);
        List<String> out = new ArrayList<>();
        if (v.isArray()) {
            for (JsonNode e : v) {
                String s = e.isValueNode() ? e.asText("") : e.path("name").asText("");
                if (!s.trim().isEmpty()) out.add(s.trim());
            }
        } else if (v.isTextual()) {
            for (String s : v.asText("").split(",")) if (!s.trim().isEmpty()) out.add(s.trim());
        }
        return out;
    }

    private static List<String> firstList(JsonNode node, String... fields) {
        for (String f : fields) {
            List<String> l = list(node, f);
            if (!l.isEmpty()) return l;
        }
        return Collections.emptyList();
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isEmpty()) map.put(key, value);
    }

    private static void putList(Map<String, Object> map, String key, List<String> value) {
        if (value != null && !value.isEmpty()) map.put(key, new ArrayList<>(value));
    }

    private static String suffix(String id) {
        return id == null || id.isEmpty() ? "" : " (" + id + ")";
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) if (v != null && !v.trim().isEmpty()) return v.trim();
        return "";
    }

    @Override
    public String getSourceType() {
        return SOURCE_TYPE;
    }
}

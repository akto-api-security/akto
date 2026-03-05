package com.akto.tracing.langchain;

import com.akto.dto.ApiCollection.ServiceGraphEdgeInfo;
import com.akto.dto.tracing.Trace;
import com.akto.dto.tracing.Span;
import com.akto.dto.tracing.TracingConstants;
import com.akto.tracing.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Parses LangChain/LangSmith trace data produced by the Go shield's extractor.
 *
 * The Go code embeds traces in the responsePayload as:
 *   {"output": {...}, "traces": [ TraceNode, ... ]}
 *
 * Each TraceNode carries: id, name, run_type, status, start_time, end_time,
 * duration_ms, parent_run_id, trace_id, dotted_order, inputs, outputs, error,
 * tags, events, total_tokens, prompt_tokens, completion_tokens, total_cost,
 * prompt_cost, completion_cost, child_run_ids.
 *
 * The requestPayload contains:
 *   {"input": {...}, "rawInputs": {...}}
 */
public class LangChainTraceParser implements TraceParser {

    private static final LangChainTraceParser INSTANCE = new LangChainTraceParser();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String SOURCE_TYPE = "langchain";

    // TraceNode JSON field names (match Go struct json tags)
    private static final String FIELD_ID = "id";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_RUN_TYPE = "run_type";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_START_TIME = "start_time";
    private static final String FIELD_END_TIME = "end_time";
    private static final String FIELD_DURATION_MS = "duration_ms";
    private static final String FIELD_PARENT_RUN_ID = "parent_run_id";
    private static final String FIELD_TRACE_ID = "trace_id";
    private static final String FIELD_DOTTED_ORDER = "dotted_order";
    private static final String FIELD_INPUTS = "inputs";
    private static final String FIELD_OUTPUTS = "outputs";
    private static final String FIELD_ERROR = "error";
    private static final String FIELD_TAGS = "tags";
    private static final String FIELD_EVENTS = "events";
    private static final String FIELD_TOTAL_TOKENS = "total_tokens";
    private static final String FIELD_PROMPT_TOKENS = "prompt_tokens";
    private static final String FIELD_COMPLETION_TOKENS = "completion_tokens";
    private static final String FIELD_TOTAL_COST = "total_cost";
    private static final String FIELD_PROMPT_COST = "prompt_cost";
    private static final String FIELD_COMPLETION_COST = "completion_cost";
    private static final String FIELD_CHILD_RUN_IDS = "child_run_ids";

    // Response payload field name
    private static final String FIELD_TRACES = "traces";

    public static LangChainTraceParser getInstance() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------
    // TraceParser interface
    // ------------------------------------------------------------------

    @Override
    public boolean canParse(Object input) {
        if (input == null) return false;

        try {
            String jsonStr = input instanceof String ? (String) input : OBJECT_MAPPER.writeValueAsString(input);
            JsonNode root = OBJECT_MAPPER.readTree(jsonStr);
            JsonNode traces = root.path(FIELD_TRACES);
            if (!traces.isArray() || traces.isEmpty()) return false;

            JsonNode first = traces.get(0);
            return first.hasNonNull(FIELD_ID) && first.hasNonNull(FIELD_RUN_TYPE);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public TraceParseResult parse(Object input) throws Exception {
        try {
            String jsonStr = input instanceof String ? (String) input : OBJECT_MAPPER.writeValueAsString(input);
            JsonNode root = OBJECT_MAPPER.readTree(jsonStr);

            if (!canParse(input)) {
                throw new Exception("Invalid LangChain trace: " + jsonStr);
            }

            JsonNode tracesArray = root.path(FIELD_TRACES);

            Map<String, JsonNode> nodeById = new LinkedHashMap<>();
            for (JsonNode node : tracesArray) {
                String nodeId = node.path(FIELD_ID).asText();
                if (!nodeId.isEmpty()) {
                    nodeById.put(nodeId, node);
                }
            }

            if (nodeById.isEmpty()) {
                throw new Exception("Invalid LangChain trace: no valid node ids");
            }

            List<JsonNode> rootNodes = new ArrayList<>();
            for (JsonNode node : nodeById.values()) {
                if (extractParentRunId(node) == null) {
                    rootNodes.add(node);
                }
            }
            rootNodes.sort(this::compareNodesByDottedOrder);

            JsonNode rootNode;
            if (rootNodes.isEmpty()) {
                rootNode = nodeById.values().iterator().next();
                rootNodes.add(rootNode);
            } else {
                rootNode = rootNodes.get(0);
            }

            Map<String, List<JsonNode>> childrenByParent = buildChildrenByParent(nodeById);
            List<JsonNode> orderedNodes = new ArrayList<>();
            Map<String, Integer> runIdToDepth = new HashMap<>();
            Set<String> visited = new HashSet<>();

            for (JsonNode rootCandidate : rootNodes) {
                appendNodeDepthFirst(rootCandidate, childrenByParent, orderedNodes, runIdToDepth, visited, 0);
            }

            for (JsonNode node : nodeById.values()) {
                String nodeId = node.path(FIELD_ID).asText();
                if (!nodeId.isEmpty() && !visited.contains(nodeId)) {
                    appendNodeDepthFirst(node, childrenByParent, orderedNodes, runIdToDepth, visited, 0);
                }
            }

            String traceId = rootNode.path(FIELD_TRACE_ID).asText(rootNode.path(FIELD_ID).asText());
            String agentName = rootNode.get(FIELD_NAME).asText("LangChain Agent");

            List<JsonNode> sortedNodes = orderedNodes;

            List<Span> spans = new ArrayList<>();
            Map<String, String> runIdToSpanId = new HashMap<>();

            for (JsonNode node : sortedNodes) {
                String nodeId = node.path(FIELD_ID).asText();
                if (!nodeId.isEmpty()) {
                    runIdToSpanId.put(nodeId, UUID.randomUUID().toString());
                }
            }

            String rootRunId = rootNode.path(FIELD_ID).asText();
            String rootSpanId = runIdToSpanId.get(rootRunId);

            int totalTokens = 0;
            int totalInputTokens = 0;
            int totalOutputTokens = 0;

            for (JsonNode node : sortedNodes) {
                String nodeId = node.path(FIELD_ID).asText();
                if (nodeId.isEmpty()) {
                    continue;
                }

                String spanId = runIdToSpanId.get(nodeId);
                if (spanId == null) {
                    continue;
                }

                String parentRunId = node.has(FIELD_PARENT_RUN_ID) && !node.get(FIELD_PARENT_RUN_ID).isNull()
                    ? node.get(FIELD_PARENT_RUN_ID).asText() : null;
                String parentSpanId = parentRunId != null ? runIdToSpanId.get(parentRunId) : null;

                Span span = buildSpan(spanId, traceId, node, parentSpanId);
                Integer depth = runIdToDepth.get(nodeId);
                span.setDepth(depth != null ? depth : 0);
                spans.add(span);

                // Accumulate tokens (only count leaf LLM spans to avoid double counting)
                String runType = node.path(FIELD_RUN_TYPE).asText("");
                if ("llm".equals(runType)) {
                    totalTokens += node.path(FIELD_TOTAL_TOKENS).asInt(0);
                    totalInputTokens += node.path(FIELD_PROMPT_TOKENS).asInt(0);
                    totalOutputTokens += node.path(FIELD_COMPLETION_TOKENS).asInt(0);
                }
            }

            // Extract root input/output
            Map<String, Object> rootInput = extractNodeDataAsMap(rootNode, FIELD_INPUTS);
            Map<String, Object> rootOutput = extractNodeDataAsMap(rootNode, FIELD_OUTPUTS);

            // Parse timestamps from root node
            long startTimeMillis = parseTimestamp(rootNode.path(FIELD_START_TIME).asText(""));
            long endTimeMillis = parseTimestamp(rootNode.path(FIELD_END_TIME).asText(""));

            List<String> spanIds = spans.stream().map(Span::getId).collect(Collectors.toList());

            Trace trace = Trace.builder()
                .id(traceId)
                .rootSpanId(rootSpanId)
                .aiAgentName(agentName)
                .name(agentName)
                .startTimeMillis(startTimeMillis)
                .endTimeMillis(endTimeMillis)
                .status(mapStatus(rootNode.path(FIELD_STATUS).asText("unknown")))
                .totalSpans(spans.size())
                .totalTokens(totalTokens)
                .totalInputTokens(totalInputTokens)
                .totalOutputTokens(totalOutputTokens)
                .rootInput(rootInput)
                .rootOutput(rootOutput)
                .spanIds(spanIds)
                .metadata(buildTraceMetadata(rootNode, traceId))
                .build();

            return TraceParseResult.builder()
                .trace(trace)
                .spans(spans)
                .workflowId(agentName != null && !agentName.isEmpty() ? agentName : traceId)
                .sourceIdentifier(traceId)
                .metadata(Collections.singletonMap("traceId", traceId))
                .build();

        } catch (Exception e) {
            throw new Exception("Failed to parse LangChain trace: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, ServiceGraphEdgeInfo> extractServiceGraph(Object input) throws Exception {
        try {
            String jsonStr = input instanceof String ? (String) input : OBJECT_MAPPER.writeValueAsString(input);
            JsonNode root = OBJECT_MAPPER.readTree(jsonStr);

            Map<String, ServiceGraphEdgeInfo> edges = new LinkedHashMap<>();

            JsonNode tracesArray = root.path(FIELD_TRACES);
            if (!tracesArray.isArray()) return edges;

            Map<String, JsonNode> nodeById = new LinkedHashMap<>();
            for (JsonNode node : tracesArray) {
                String nodeId = node.path(FIELD_ID).asText();
                if (!nodeId.isEmpty()) {
                    nodeById.put(nodeId, node);
                }
            }

            if (nodeById.isEmpty()) {
                return edges;
            }

            List<JsonNode> rootNodes = new ArrayList<>();
            for (JsonNode node : nodeById.values()) {
                if (extractParentRunId(node) == null) {
                    rootNodes.add(node);
                }
            }
            rootNodes.sort(this::compareNodesByDottedOrder);

            if (rootNodes.isEmpty()) {
                rootNodes.add(nodeById.values().iterator().next());
            }

            Map<String, List<JsonNode>> childrenByParent = buildChildrenByParent(nodeById);
            List<JsonNode> orderedNodes = new ArrayList<>();
            Set<String> visited = new HashSet<>();
            Map<String, Integer> ignoredDepthMap = new HashMap<>();
            for (JsonNode rootNode : rootNodes) {
                appendNodeDepthFirst(rootNode, childrenByParent, orderedNodes, ignoredDepthMap, visited, 0);
            }

            for (JsonNode node : nodeById.values()) {
                String nodeId = node.path(FIELD_ID).asText();
                if (!nodeId.isEmpty() && !visited.contains(nodeId)) {
                    appendNodeDepthFirst(node, childrenByParent, orderedNodes, ignoredDepthMap, visited, 0);
                }
            }

            Map<String, String> runIdToName = new HashMap<>();
            for (JsonNode node : nodeById.values()) {
                String runId = node.path(FIELD_ID).asText();
                if (!runId.isEmpty()) {
                    runIdToName.put(runId, node.path(FIELD_NAME).asText(runId));
                }
            }

            for (JsonNode node : orderedNodes) {
                String nodeId = node.path(FIELD_ID).asText();
                if (nodeId.isEmpty()) {
                    continue;
                }

                String nodeName = resolveServiceName(node, nodeId);
                String runType = node.path(FIELD_RUN_TYPE).asText("unknown");
                String graphType = classifyNodeTypeForGraph(node, nodeName, runType);

                if (!node.has(FIELD_PARENT_RUN_ID) || node.get(FIELD_PARENT_RUN_ID).isNull()) {
                    continue; // root has no parent edge
                }

                String parentRunId = node.get(FIELD_PARENT_RUN_ID).asText();
                JsonNode parentNode = nodeById.get(parentRunId);
                String parentName = parentNode != null
                    ? resolveServiceName(parentNode, parentRunId)
                    : runIdToName.getOrDefault(parentRunId, parentRunId);

                if (parentName == null || parentName.trim().isEmpty() || nodeName == null || nodeName.trim().isEmpty()) {
                    continue;
                }

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("type", graphType);

                // Store edgeParam as a plain string (the type label) so the frontend can render it directly
                String edgeParamLabel = determineEdgeParamType(node, runType);
                if (edgeParamLabel != null && !edgeParamLabel.isEmpty()) {
                    metadata.put("edgeParam", edgeParamLabel);
                }

                ServiceGraphEdgeInfo edge = new ServiceGraphEdgeInfo(parentName, nodeName, metadata);
                // Keep key aligned with target service name (same contract used by N8n parser)
                edges.put(nodeName, edge);
            }

            return edges;

        } catch (Exception e) {
            throw new Exception("Failed to extract LangChain service graph: " + e.getMessage(), e);
        }
    }

    @Override
    public String getSourceType() {
        return SOURCE_TYPE;
    }

    // ------------------------------------------------------------------
    // Span building
    // ------------------------------------------------------------------

    private Span buildSpan(String spanId, String traceId, JsonNode node, String parentSpanId) {
        String name = node.get(FIELD_NAME).asText();
        String runType = node.path(FIELD_RUN_TYPE).asText("unknown");
        String status = node.path(FIELD_STATUS).asText("unknown");

        long startTimeMillis = parseTimestamp(node.path(FIELD_START_TIME).asText(""));
        long endTimeMillis = parseTimestamp(node.path(FIELD_END_TIME).asText(""));
        long durationMs = node.path(FIELD_DURATION_MS).asLong(0);
        if (endTimeMillis == 0 && durationMs > 0) {
            endTimeMillis = startTimeMillis + durationMs;
        }

        String errorMessage = node.has(FIELD_ERROR) && !node.get(FIELD_ERROR).isNull()
            ? node.get(FIELD_ERROR).asText() : null;

        // Extract model name from metadata if this is an LLM span
        String modelName = null;
        if ("llm".equals(runType)) {
            modelName = extractModelName(node);
        }

        // Build tags list
        List<String> tags = new ArrayList<>();
        tags.add(SOURCE_TYPE);
        tags.add(runType);
        if (node.has(FIELD_TAGS) && node.get(FIELD_TAGS).isArray()) {
            for (JsonNode tag : node.get(FIELD_TAGS)) {
                tags.add(tag.asText());
            }
        }

        return Span.builder()
            .id(spanId)
            .traceId(traceId)
            .parentSpanId(parentSpanId)
            .spanKind(mapRunTypeToSpanKind(runType, name))
            .name(name)
            .startTimeMillis(startTimeMillis)
            .endTimeMillis(endTimeMillis)
            .status(mapStatus(status))
            .errorMessage(errorMessage)
            .input(extractNodeDataAsMap(node, FIELD_INPUTS))
            .output(extractNodeDataAsMap(node, FIELD_OUTPUTS))
            .metadata(buildSpanMetadata(node, runType))
            .modelName(modelName)
            .depth(0) // set later via BFS
            .tags(tags)
            .build();
    }

    // ------------------------------------------------------------------
    // Run type → SpanKind mapping
    // ------------------------------------------------------------------

    /**
     * Maps LangSmith run_type values to our SpanKind constants.
     * LangSmith run types: chain, llm, tool, retriever, embedding, prompt, parser.
     */
    private String mapRunTypeToSpanKind(String runType, String name) {
        if (runType == null) return TracingConstants.SpanKind.UNKNOWN;

        String lowerName = name == null ? "" : name.toLowerCase();

        switch (runType.toLowerCase()) {
            case "llm":
                return TracingConstants.SpanKind.LLM;
            case "tool":
                if (lowerName.contains("mcp")) {
                    return TracingConstants.SpanKind.MCP_SERVER;
                }
                return TracingConstants.SpanKind.TOOL;
            case "retriever":
                return TracingConstants.SpanKind.RAG;
            case "embedding":
                return TracingConstants.SpanKind.EMBEDDING;
            case "chain":
                if (lowerName.contains("chat") || lowerName.contains("llm")) {
                    return TracingConstants.SpanKind.LLM;
                }
                // Heuristic: chains named with "agent" are agents
                if (lowerName.contains("agent")) {
                    return TracingConstants.SpanKind.AGENT;
                }
                return TracingConstants.SpanKind.TASK;
            case "prompt":
                return TracingConstants.SpanKind.FUNCTION;
            case "parser":
                return TracingConstants.SpanKind.FUNCTION;
            default:
                return TracingConstants.SpanKind.UNKNOWN;
        }
    }

    // ------------------------------------------------------------------
    // Tree helpers
    // ------------------------------------------------------------------

    private String extractParentRunId(JsonNode node) {
        if (node == null || !node.has(FIELD_PARENT_RUN_ID) || node.get(FIELD_PARENT_RUN_ID).isNull()) {
            return null;
        }
        String parentRunId = node.get(FIELD_PARENT_RUN_ID).asText();
        return parentRunId.isEmpty() ? null : parentRunId;
    }

    private int compareNodesByDottedOrder(JsonNode left, JsonNode right) {
        int orderCompare = left.path(FIELD_DOTTED_ORDER).asText("")
            .compareTo(right.path(FIELD_DOTTED_ORDER).asText(""));
        if (orderCompare != 0) {
            return orderCompare;
        }
        return left.path(FIELD_ID).asText("").compareTo(right.path(FIELD_ID).asText(""));
    }

    private Map<String, List<JsonNode>> buildChildrenByParent(Map<String, JsonNode> nodeById) {
        Map<String, List<JsonNode>> childrenByParent = new HashMap<>();

        for (JsonNode node : nodeById.values()) {
            String nodeId = node.path(FIELD_ID).asText();
            if (!nodeId.isEmpty()) {
                childrenByParent.putIfAbsent(nodeId, new ArrayList<>());
            }
        }

        // Primary linkage: group by parent_run_id
        for (JsonNode node : nodeById.values()) {
            String nodeId = node.path(FIELD_ID).asText();
            if (nodeId.isEmpty()) {
                continue;
            }

            String parentRunId = extractParentRunId(node);
            if (parentRunId == null || !nodeById.containsKey(parentRunId)) {
                continue;
            }

            childrenByParent.putIfAbsent(parentRunId, new ArrayList<>());
            childrenByParent.get(parentRunId).add(node);
        }

        for (List<JsonNode> children : childrenByParent.values()) {
            children.sort(this::compareNodesByDottedOrder);
        }

        return childrenByParent;
    }

    private void appendNodeDepthFirst(
        JsonNode node,
        Map<String, List<JsonNode>> childrenByParent,
        List<JsonNode> orderedNodes,
        Map<String, Integer> runIdToDepth,
        Set<String> visited,
        int depth
    ) {
        if (node == null) {
            return;
        }

        String nodeId = node.path(FIELD_ID).asText();
        if (nodeId.isEmpty() || !visited.add(nodeId)) {
            return;
        }

        orderedNodes.add(node);
        runIdToDepth.put(nodeId, depth);

        List<JsonNode> children = childrenByParent.getOrDefault(nodeId, Collections.emptyList());
        for (JsonNode child : children) {
            appendNodeDepthFirst(child, childrenByParent, orderedNodes, runIdToDepth, visited, depth + 1);
        }
    }

    // ------------------------------------------------------------------
    // Data extraction helpers
    // ------------------------------------------------------------------

    /**
     * Extracts a node field (inputs/outputs) as a Map.
     * Returns the raw JSON structure so the full LangSmith data is preserved.
     */
    private Map<String, Object> extractNodeDataAsMap(JsonNode node, String fieldName) {
        Map<String, Object> result = new HashMap<>();
        if (!node.has(fieldName) || node.get(fieldName).isNull()) {
            return result;
        }

        try {
            JsonNode dataNode = node.get(fieldName);
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = OBJECT_MAPPER.convertValue(dataNode, Map.class);
            return parsed != null ? parsed : result;
        } catch (Exception e) {
            result.put("raw", node.get(fieldName).toString());
            return result;
        }
    }

    /**
     * Extracts the model name from an LLM span.
     * LangSmith stores this in inputs.invocation_params.model or the node name itself.
     */
    private String extractModelName(JsonNode node) {
        JsonNode inputs = node.path(FIELD_INPUTS);
        JsonNode params = inputs.path("invocation_params");
        if (params.has("model")) return params.get("model").asText();
        if (params.has("model_name")) return params.get("model_name").asText();

        JsonNode kwargs = inputs.path("kwargs");
        if (kwargs.has("model")) return kwargs.get("model").asText();
        if (kwargs.has("model_name")) return kwargs.get("model_name").asText();

        String name = node.path(FIELD_NAME).asText("");
        if (name.startsWith("Chat") || name.contains("OpenAI") || name.contains("Anthropic")
                || name.contains("Gemini") || name.contains("Bedrock")) {
            return name;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Edge param helpers (for service graph)
    // ------------------------------------------------------------------

    private String determineEdgeParamType(JsonNode node, String runType) {
        if (runType == null) return TracingConstants.EdgeParamType.UNKNOWN;

        switch (runType.toLowerCase()) {
            case "llm":
                return TracingConstants.EdgeParamType.LLM_RESPONSE;
            case "tool":
                return TracingConstants.EdgeParamType.TOOL_OUTPUT;
            case "retriever":
                return TracingConstants.EdgeParamType.INTERMEDIATE_DATA;
            case "chain":
                // Root chain with no parent is user input
                if (!node.has(FIELD_PARENT_RUN_ID) || node.get(FIELD_PARENT_RUN_ID).isNull()) {
                    return TracingConstants.EdgeParamType.USER_INPUT;
                }
                return TracingConstants.EdgeParamType.INTERMEDIATE_DATA;
            default:
                return TracingConstants.EdgeParamType.UNKNOWN;
        }
    }

    private String classifyNodeTypeForGraph(JsonNode node, String nodeName, String runType) {
        String lowerName = nodeName == null ? "" : nodeName.toLowerCase();
        String lowerRunType = runType == null ? "unknown" : runType.toLowerCase();

        if ("llm".equals(lowerRunType)) {
            return "llm";
        }

        if ("tool".equals(lowerRunType)) {
            if (lowerName.contains("mcp")) {
                return "mcp_tool";
            }
            return "tool";
        }

        if ("chain".equals(lowerRunType)) {
            if (lowerName.contains("chat") || lowerName.contains("llm")) {
                return "chat";
            }
            return "chain";
        }

        return lowerRunType;
    }

    private String resolveServiceName(JsonNode node, String fallbackId) {
        String name = node.path(FIELD_NAME).asText("");
        if (name != null && !name.trim().isEmpty()) {
            return name.trim();
        }
        return fallbackId;
    }

    // ------------------------------------------------------------------
    // Metadata builders
    // ------------------------------------------------------------------

    private Map<String, Object> buildTraceMetadata(JsonNode rootNode, String traceId) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sourceType", SOURCE_TYPE);
        metadata.put("traceId", traceId);
        metadata.put("rootRunId", rootNode.get(FIELD_ID).asText());
        metadata.put("rootRunType", rootNode.path(FIELD_RUN_TYPE).asText("chain"));

        if (rootNode.has(FIELD_DOTTED_ORDER)) {
            metadata.put("dottedOrder", rootNode.get(FIELD_DOTTED_ORDER).asText());
        }

        // Aggregate cost info from root
        double totalCost = rootNode.path(FIELD_TOTAL_COST).asDouble(0);
        if (totalCost > 0) {
            metadata.put("totalCost", totalCost);
            metadata.put("promptCost", rootNode.path(FIELD_PROMPT_COST).asDouble(0));
            metadata.put("completionCost", rootNode.path(FIELD_COMPLETION_COST).asDouble(0));
        }

        return metadata;
    }

    private Map<String, Object> buildSpanMetadata(JsonNode node, String runType) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sourceType", SOURCE_TYPE);
        metadata.put("runType", runType);

        if (node.has(FIELD_DOTTED_ORDER)) {
            metadata.put("dottedOrder", node.get(FIELD_DOTTED_ORDER).asText());
        }

        // Token counts
        int totalTokens = node.path(FIELD_TOTAL_TOKENS).asInt(0);
        if (totalTokens > 0) {
            metadata.put("totalTokens", totalTokens);
            metadata.put("promptTokens", node.path(FIELD_PROMPT_TOKENS).asInt(0));
            metadata.put("completionTokens", node.path(FIELD_COMPLETION_TOKENS).asInt(0));
        }

        // Cost info
        double totalCost = node.path(FIELD_TOTAL_COST).asDouble(0);
        if (totalCost > 0) {
            metadata.put("totalCost", totalCost);
            metadata.put("promptCost", node.path(FIELD_PROMPT_COST).asDouble(0));
            metadata.put("completionCost", node.path(FIELD_COMPLETION_COST).asDouble(0));
        }

        // Events
        if (node.has(FIELD_EVENTS) && node.get(FIELD_EVENTS).isArray() && node.get(FIELD_EVENTS).size() > 0) {
            try {
                metadata.put("events", OBJECT_MAPPER.writeValueAsString(node.get(FIELD_EVENTS)));
            } catch (Exception e) {
                metadata.put("events", node.get(FIELD_EVENTS).toString());
            }
        }

        // Child run IDs
        if (node.has(FIELD_CHILD_RUN_IDS) && node.get(FIELD_CHILD_RUN_IDS).isArray()
                && node.get(FIELD_CHILD_RUN_IDS).size() > 0) {
            List<String> childIds = new ArrayList<>();
            for (JsonNode child : node.get(FIELD_CHILD_RUN_IDS)) {
                childIds.add(child.asText());
            }
            metadata.put("childRunIds", childIds);
        }

        return metadata;
    }

    // ------------------------------------------------------------------
    // Utility
    // ------------------------------------------------------------------

    private long parseTimestamp(String timestamp) {
        try {
            return java.time.Instant.parse(timestamp).toEpochMilli();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private String mapStatus(String langsmithStatus) {
        if ("success".equalsIgnoreCase(langsmithStatus)) return "success";
        if ("error".equalsIgnoreCase(langsmithStatus)) return "error";
        if ("pending".equalsIgnoreCase(langsmithStatus)) return "running";
        if ("running".equalsIgnoreCase(langsmithStatus)) return "running";
        return "unknown";
    }
}

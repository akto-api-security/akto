package com.akto.tracing.vertexai;

import com.akto.dto.ApiCollection.ServiceGraphEdgeInfo;
import com.akto.dto.tracing.Span;
import com.akto.dto.tracing.Trace;
import com.akto.dto.tracing.TracingConstants;
import com.akto.tracing.TraceParser;
import com.akto.tracing.TraceParseResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Parses Vertex AI ADK agent traces from Akto-ingested HTTP call data.
 *
 * <p>The parser expects the deserialized {@code responsePayload} map produced by
 * {@code callbacks.py}, which has the shape:
 * <pre>
 * {
 *   "body":   "{\"choices\":[{\"message\":{\"role\":\"model\",\"content\":\"...\"}}]}",
 *   "traces": [
 *     {"author":"user",      "invocation_id":"e-...", "is_final":false, "parts":[{"type":"text","content":"..."}]},
 *     {"author":"model", "invocation_id":"e-...", "is_final":false, "parts":[{"type":"tool_call","name":"...","args":{}}]},
 *     {"author":"model", "invocation_id":"e-...", "is_final":false, "parts":[{"type":"tool_result","name":"...","response":{}}]}
 *   ]
 * }
 * </pre>
 *
 * <p>Span hierarchy produced:
 * <ul>
 *   <li>User span   (TASK,  depth=0, root)</li>
 *   <li>Agent span  (AGENT, depth=1, parent=user)</li>
 *   <li>LLM spans   (LLM,   depth=2, parent=agent) — one per model turn</li>
 *   <li>Tool spans  (TOOL,  depth=3, parent=owning LLM) — one per tool_call part</li>
 * </ul>
 */
public class VertexAITraceParser implements TraceParser {

    private static final VertexAITraceParser INSTANCE = new VertexAITraceParser();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String SOURCE_TYPE = "vertex-ai-adk";

    // ── responsePayload field names ────────────────────────────────────────────
    private static final String FIELD_TRACES   = "traces";
    private static final String FIELD_BODY     = "body";
    private static final String FIELD_CHOICES  = "choices";
    private static final String FIELD_MESSAGE  = "message";
    private static final String FIELD_CONTENT  = "content";
    private static final String FIELD_MODEL    = "model";

    // ── Trace-event field names ────────────────────────────────────────────────
    private static final String FIELD_AUTHOR         = "author";
    private static final String FIELD_INVOCATION_ID  = "invocation_id";
    private static final String FIELD_IS_FINAL       = "is_final";
    private static final String FIELD_PARTS          = "parts";
    private static final String FIELD_TYPE           = "type";
    private static final String FIELD_NAME           = "name";
    private static final String FIELD_ARGS           = "args";
    private static final String FIELD_RESPONSE       = "response";

    // ── Part-type values ───────────────────────────────────────────────────────
    private static final String PART_TEXT        = "text";
    private static final String PART_TOOL_CALL   = "tool_call";
    private static final String PART_TOOL_RESULT = "tool_result";

    // ── Author values ──────────────────────────────────────────────────────────
    private static final String AUTHOR_USER      = "user";
    private static final String AUTHOR_MODEL     = "model";
    private static final String AUTHOR_ASSISTANT = "assistant";

    // ── Timing offset per span (ms) to ensure sequential ordering ──────────────
    private static final long SPAN_TIME_OFFSET_MS = 10;

    private VertexAITraceParser() {}

    public static VertexAITraceParser getInstance() {
        return INSTANCE;
    }

    // ── TraceParser interface ──────────────────────────────────────────────────

    @Override
    public boolean canParse(Object input) {
        if (input == null) return false;
        try {
            String json = toJsonString(input);
            JsonNode root = OBJECT_MAPPER.readTree(json);
            if (!root.has(FIELD_TRACES)) return false;
            JsonNode traces = root.get(FIELD_TRACES);
            if (!traces.isArray() || traces.size() == 0) return false;
            JsonNode first = traces.get(0);
            return first.has(FIELD_AUTHOR)
                    && first.has(FIELD_INVOCATION_ID)
                    && first.has(FIELD_PARTS);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public TraceParseResult parse(Object input) throws Exception {
        try {
            if (!canParse(input)) {
                throw new Exception("Invalid Vertex AI trace: missing required fields");
            }

            JsonNode root = OBJECT_MAPPER.readTree(toJsonString(input));
            JsonNode tracesArray = root.get(FIELD_TRACES);

            String invocationId = tracesArray.get(0).path(FIELD_INVOCATION_ID).asText();
            long baseTimestamp = System.currentTimeMillis();

            String finalLlmText = extractFinalLlmText(root);
            if (finalLlmText == null || finalLlmText.isEmpty()) {
                finalLlmText = extractFinalLlmTextFromTraces(tracesArray);
            }

            String modelName = extractModelName(root);

            List<Span> spans = buildSpans(tracesArray, invocationId, finalLlmText, modelName, baseTimestamp);

            String rootSpanId = spans.isEmpty() ? null : spans.get(0).getId();

            long traceEndTimestamp = spans.isEmpty() ? baseTimestamp :
                    spans.stream().mapToLong(Span::getEndTimeMillis).max().orElse(baseTimestamp);

            boolean hasError = spans.stream().anyMatch(s -> "error".equals(s.getStatus()));

            Trace trace = Trace.builder()
                    .id(invocationId)
                    .rootSpanId(rootSpanId)
                    .aiAgentName("Vertex AI ADK")
                    .name("Vertex AI ADK")
                    .startTimeMillis(baseTimestamp)
                    .endTimeMillis(traceEndTimestamp)
                    .status(hasError ? "error" : "success")
                    .totalSpans(spans.size())
                    .totalTokens(0)
                    .totalInputTokens(0)
                    .totalOutputTokens(0)
                    .rootInput(extractRootInput(tracesArray))
                    .rootOutput(buildRootOutput(finalLlmText))
                    .spanIds(spans.stream().map(Span::getId).collect(Collectors.toList()))
                    .metadata(buildTraceMetadata(invocationId, modelName))
                    .build();

            return TraceParseResult.builder()
                    .trace(trace)
                    .spans(spans)
                    .workflowId(invocationId)
                    .sourceIdentifier(invocationId)
                    .metadata(Collections.singletonMap("invocationId", invocationId))
                    .build();

        } catch (Exception e) {
            throw new Exception("Failed to parse Vertex AI trace: " + e.getMessage(), e);
        }
    }

    /**
     * Builds service-graph edges reflecting the full call chain:
     * user → agent, agent → llm, and llm → each tool.
     */
    @Override
    public Map<String, ServiceGraphEdgeInfo> extractServiceGraph(Object input) throws Exception {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(toJsonString(input));
            Map<String, ServiceGraphEdgeInfo> edges = new LinkedHashMap<>();
            if (!root.has(FIELD_TRACES)) return edges;

            // user → agent edge
            {
                Map<String, Object> meta = new HashMap<>();
                meta.put("type", TracingConstants.SpanKind.AGENT);
                Map<String, Object> edgeParam = new HashMap<>();
                edgeParam.put("type", TracingConstants.EdgeParamType.USER_INPUT);
                meta.put("edgeParam", edgeParam);
                edges.put("user->agent", new ServiceGraphEdgeInfo("user", "agent", meta));
            }

            // agent → llm edge
            {
                Map<String, Object> meta = new HashMap<>();
                meta.put("type", TracingConstants.SpanKind.LLM);
                Map<String, Object> edgeParam = new HashMap<>();
                edgeParam.put("type", TracingConstants.EdgeParamType.LLM_PROMPT);
                meta.put("edgeParam", edgeParam);
                edges.put("agent->llm", new ServiceGraphEdgeInfo("agent", "llm", meta));
            }

            // llm → tool edges (one per unique tool)
            Map<String, Integer> toolCallCount = new LinkedHashMap<>();
            for (JsonNode event : root.get(FIELD_TRACES)) {
                if (!isModelAuthor(event.path(FIELD_AUTHOR).asText())) continue;
                for (JsonNode part : event.path(FIELD_PARTS)) {
                    if (PART_TOOL_CALL.equals(part.path(FIELD_TYPE).asText())) {
                        String toolName = part.path(FIELD_NAME).asText();
                        toolCallCount.merge(toolName, 1, Integer::sum);
                    }
                }
            }

            for (Map.Entry<String, Integer> entry : toolCallCount.entrySet()) {
                String toolName = entry.getKey();
                Map<String, Object> meta = new HashMap<>();
                meta.put("type", TracingConstants.SpanKind.TOOL);
                meta.put("callCount", entry.getValue());
                Map<String, Object> edgeParam = new HashMap<>();
                edgeParam.put("type", TracingConstants.EdgeParamType.TOOL_INPUT);
                meta.put("edgeParam", edgeParam);
                edges.put("llm->" + toolName, new ServiceGraphEdgeInfo("llm", toolName, meta));
            }

            return edges;

        } catch (Exception e) {
            throw new Exception("Failed to extract Vertex AI service graph: " + e.getMessage(), e);
        }
    }

    @Override
    public String getSourceType() {
        return SOURCE_TYPE;
    }

    // ── Span building ──────────────────────────────────────────────────────────

    /**
     * Represents a single model turn: one LLM invocation with its tool calls and results.
     */
    private static class ModelTurn {
        final List<JsonNode> toolCallParts = new ArrayList<>();
        final List<JsonNode> toolResultParts = new ArrayList<>();
        String modelText;
        boolean isFinal;
    }

    /**
     * Builds the span list for one invocation with multi-turn support.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Collect user text → 1 TASK span (root, depth 0).</li>
     *   <li>Create 1 AGENT span (depth 1, parent=user).</li>
     *   <li>Group model events into turns — each turn starts with a model event
     *       containing tool_call or text parts.</li>
     *   <li>For each turn: create 1 LLM span (depth 2, parent=agent)
     *       and N TOOL spans (depth 3, parent=LLM).</li>
     *   <li>Match tool_result parts to tool_call parts by name within the same turn.</li>
     * </ol>
     */
    private List<Span> buildSpans(JsonNode tracesArray, String traceId,
                                  String finalLlmText, String modelName,
                                  long baseTimestamp) {
        List<Span> spans = new ArrayList<>();
        long currentTime = baseTimestamp;

        // 1. User span (root, TASK, depth 0)
        Span userSpan = null;
        for (JsonNode event : tracesArray) {
            if (AUTHOR_USER.equals(event.path(FIELD_AUTHOR).asText())) {
                for (JsonNode part : event.path(FIELD_PARTS)) {
                    if (PART_TEXT.equals(part.path(FIELD_TYPE).asText()) && userSpan == null) {
                        String spanId = UUID.randomUUID().toString();
                        Map<String, Object> input = new HashMap<>();
                        input.put("content", part.path(FIELD_CONTENT).asText());
                        userSpan = Span.builder()
                                .id(spanId)
                                .traceId(traceId)
                                .parentSpanId(null)
                                .spanKind(TracingConstants.SpanKind.TASK)
                                .name("user")
                                .startTimeMillis(currentTime)
                                .endTimeMillis(currentTime)
                                .status("success")
                                .input(input)
                                .output(Collections.emptyMap())
                                .metadata(spanMeta(AUTHOR_USER, null, modelName))
                                .depth(0)
                                .tags(Arrays.asList(SOURCE_TYPE, "user"))
                                .build();
                        break;
                    }
                }
                if (userSpan != null) break;
            }
        }
        if (userSpan != null) {
            spans.add(userSpan);
        }
        currentTime += SPAN_TIME_OFFSET_MS;

        // 2. Agent span (AGENT, depth 1, parent=user)
        String agentSpanId = UUID.randomUUID().toString();
        String userSpanId = userSpan != null ? userSpan.getId() : null;
        Map<String, Object> agentInput = userSpan != null ? new HashMap<>(userSpan.getInput()) : new HashMap<>();
        Span agentSpan = Span.builder()
                .id(agentSpanId)
                .traceId(traceId)
                .parentSpanId(userSpanId)
                .spanKind(TracingConstants.SpanKind.AGENT)
                .name("Vertex AI Agent")
                .startTimeMillis(currentTime)
                .endTimeMillis(currentTime) // updated later
                .status("success") // updated later
                .input(agentInput)
                .output(new HashMap<>()) // updated later
                .metadata(spanMeta(null, null, modelName))
                .depth(1)
                .tags(Arrays.asList(SOURCE_TYPE, "agent"))
                .build();
        spans.add(agentSpan);
        currentTime += SPAN_TIME_OFFSET_MS;

        // 3. Group trace events into model turns
        List<ModelTurn> turns = groupIntoTurns(tracesArray);

        // 4. Create LLM + TOOL spans for each turn
        boolean hasError = false;
        for (int turnIdx = 0; turnIdx < turns.size(); turnIdx++) {
            ModelTurn turn = turns.get(turnIdx);
            String llmSpanId = UUID.randomUUID().toString();
            boolean isLastTurn = (turnIdx == turns.size() - 1);

            // LLM span input: user text for first turn, previous turn's tool results for subsequent
            Map<String, Object> llmInput = new HashMap<>();
            if (turnIdx == 0 && userSpan != null) {
                llmInput.putAll(userSpan.getInput());
            } else if (turnIdx > 0) {
                ModelTurn prevTurn = turns.get(turnIdx - 1);
                if (!prevTurn.toolResultParts.isEmpty()) {
                    List<String> prevResults = new ArrayList<>();
                    for (JsonNode tr : prevTurn.toolResultParts) {
                        prevResults.add(safeJson(tr.path(FIELD_RESPONSE)));
                    }
                    llmInput.put("tool_results", prevResults);
                }
            }

            // LLM span output
            Map<String, Object> llmOutput = new HashMap<>();
            if (isLastTurn && finalLlmText != null) {
                llmOutput.put("content", finalLlmText);
            } else if (turn.modelText != null) {
                llmOutput.put("content", turn.modelText);
            }
            if (!turn.toolCallParts.isEmpty()) {
                List<Map<String, String>> toolCallSummaries = new ArrayList<>();
                for (JsonNode tc : turn.toolCallParts) {
                    Map<String, String> summary = new HashMap<>();
                    summary.put("name", tc.path(FIELD_NAME).asText());
                    summary.put("args", safeJson(tc.path(FIELD_ARGS)));
                    toolCallSummaries.add(summary);
                }
                llmOutput.put("tool_calls", toolCallSummaries);
            }

            String llmName = turns.size() == 1 ? "LLM Call" : "LLM Call " + (turnIdx + 1);
            Span llmSpan = Span.builder()
                    .id(llmSpanId)
                    .traceId(traceId)
                    .parentSpanId(agentSpanId)
                    .spanKind(TracingConstants.SpanKind.LLM)
                    .name(llmName)
                    .startTimeMillis(currentTime)
                    .endTimeMillis(currentTime + SPAN_TIME_OFFSET_MS)
                    .status("success")
                    .input(llmInput)
                    .output(llmOutput)
                    .metadata(spanMeta(AUTHOR_MODEL, null, modelName))
                    .modelName(modelName)
                    .depth(2)
                    .tags(Arrays.asList(SOURCE_TYPE, "llm"))
                    .build();
            spans.add(llmSpan);
            currentTime += SPAN_TIME_OFFSET_MS;

            // Tool spans for this turn
            // Match tool_result to tool_call by name within the turn
            Map<String, JsonNode> resultsByName = new LinkedHashMap<>();
            for (JsonNode tr : turn.toolResultParts) {
                String name = tr.path(FIELD_NAME).asText();
                resultsByName.put(name, tr);
            }

            for (int i = 0; i < turn.toolCallParts.size(); i++) {
                JsonNode toolCallPart = turn.toolCallParts.get(i);
                String toolName = toolCallPart.path(FIELD_NAME).asText();

                Map<String, Object> toolInput = new HashMap<>();
                toolInput.put("name", toolName);
                toolInput.put("args", safeJson(toolCallPart.path(FIELD_ARGS)));

                Map<String, Object> toolOutput = new HashMap<>();
                String toolStatus = "success";

                // Match by name first, fall back to positional index
                JsonNode matchedResult = resultsByName.remove(toolName);
                if (matchedResult == null && i < turn.toolResultParts.size()) {
                    matchedResult = turn.toolResultParts.get(i);
                }

                if (matchedResult != null) {
                    JsonNode responseNode = matchedResult.path(FIELD_RESPONSE);
                    String responseStr = safeJson(responseNode);
                    toolOutput.put("response", responseStr);

                    // Detect errors in tool result
                    if (responseNode.has("error") || responseNode.has("errorMessage")) {
                        toolStatus = "error";
                        String errMsg = responseNode.has("error")
                                ? responseNode.path("error").asText()
                                : responseNode.path("errorMessage").asText();
                        toolOutput.put("errorMessage", errMsg);
                        hasError = true;
                    }
                    String responseText = responseNode.isTextual() ? responseNode.asText() : responseStr;
                    if (responseText != null && (responseText.toLowerCase().contains("\"error\"")
                            || responseText.toLowerCase().contains("traceback")
                            || responseText.toLowerCase().contains("exception"))) {
                        toolStatus = "error";
                        hasError = true;
                    }
                } else {
                    toolOutput.put("response", "no result");
                }

                Span toolSpan = Span.builder()
                        .id(UUID.randomUUID().toString())
                        .traceId(traceId)
                        .parentSpanId(llmSpanId)
                        .spanKind(TracingConstants.SpanKind.TOOL)
                        .name(toolName)
                        .startTimeMillis(currentTime)
                        .endTimeMillis(currentTime + SPAN_TIME_OFFSET_MS)
                        .status(toolStatus)
                        .input(toolInput)
                        .output(toolOutput)
                        .metadata(spanMeta(null, toolName, modelName))
                        .toolDefinition(Span.ToolDefinition.builder()
                                .name(toolName).type("function").build())
                        .depth(3)
                        .tags(Arrays.asList(SOURCE_TYPE, "tool"))
                        .build();
                spans.add(toolSpan);
                currentTime += SPAN_TIME_OFFSET_MS;
            }
        }

        // Update agent span end time and output
        agentSpan.setEndTimeMillis(currentTime);
        if (finalLlmText != null) {
            Map<String, Object> agentOutput = new HashMap<>();
            agentOutput.put("content", finalLlmText);
            agentSpan.setOutput(agentOutput);
        }
        if (hasError) {
            agentSpan.setStatus("error");
        }

        // Update user span end time
        if (userSpan != null) {
            userSpan.setEndTimeMillis(currentTime);
        }

        return spans;
    }

    /**
     * Groups trace events into sequential model turns.
     * A new turn starts when we see a model event after processing tool results,
     * or the first model event.
     */
    private List<ModelTurn> groupIntoTurns(JsonNode tracesArray) {
        List<ModelTurn> turns = new ArrayList<>();
        ModelTurn currentTurn = null;

        for (JsonNode event : tracesArray) {
            String author = event.path(FIELD_AUTHOR).asText();
            JsonNode parts = event.path(FIELD_PARTS);
            if (!parts.isArray()) continue;

            boolean isFinal = event.path(FIELD_IS_FINAL).asBoolean(false);

            for (JsonNode part : parts) {
                String partType = part.path(FIELD_TYPE).asText();

                if (PART_TEXT.equals(partType) && AUTHOR_USER.equals(author)) {
                    // Skip user text — already handled as user span
                    continue;
                }

                if (PART_TOOL_CALL.equals(partType) && isModelAuthor(author)) {
                    // Start a new turn if we don't have one, or if current turn
                    // already has tool results (meaning a new LLM invocation happened)
                    if (currentTurn == null || !currentTurn.toolResultParts.isEmpty()) {
                        currentTurn = new ModelTurn();
                        turns.add(currentTurn);
                    }
                    currentTurn.toolCallParts.add(part);

                } else if (PART_TOOL_RESULT.equals(partType)) {
                    // tool_result belongs to the current turn
                    if (currentTurn == null) {
                        currentTurn = new ModelTurn();
                        turns.add(currentTurn);
                    }
                    currentTurn.toolResultParts.add(part);

                } else if (PART_TEXT.equals(partType) && isModelAuthor(author)) {
                    // Model text: if we have a current turn with tool calls, this is
                    // intermediate text. If no tool calls, this starts a new turn.
                    String text = part.path(FIELD_CONTENT).asText(null);
                    if (currentTurn == null || !currentTurn.toolResultParts.isEmpty()) {
                        currentTurn = new ModelTurn();
                        turns.add(currentTurn);
                    }
                    currentTurn.modelText = text;
                    currentTurn.isFinal = isFinal;
                }
            }
        }

        // Ensure at least one turn exists if there were model events
        if (turns.isEmpty()) {
            turns.add(new ModelTurn());
        }

        return turns;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Extracts the model name from the body field.
     * The body JSON may contain a "model" field (set by callbacks.py).
     */
    private String extractModelName(JsonNode root) {
        try {
            JsonNode bodyNode = root.path(FIELD_BODY);
            String bodyStr = bodyNode.isTextual()
                    ? bodyNode.asText()
                    : OBJECT_MAPPER.writeValueAsString(bodyNode);
            JsonNode body = OBJECT_MAPPER.readTree(bodyStr);
            String model = body.path(FIELD_MODEL).asText(null);
            if (model != null && !model.isEmpty()) return model;
        } catch (Exception ignored) {}

        // Fallback: check root-level model field
        String rootModel = root.path(FIELD_MODEL).asText(null);
        if (rootModel != null && !rootModel.isEmpty()) return rootModel;

        return null;
    }

    /**
     * Extracts the final model response text from {@code choices[0].message.content}
     * in the nested {@code body} JSON string.
     */
    private String extractFinalLlmText(JsonNode root) {
        try {
            JsonNode bodyNode = root.path(FIELD_BODY);
            String bodyStr = bodyNode.isTextual()
                    ? bodyNode.asText()
                    : OBJECT_MAPPER.writeValueAsString(bodyNode);
            JsonNode body = OBJECT_MAPPER.readTree(bodyStr);
            return body.path(FIELD_CHOICES).get(0)
                    .path(FIELD_MESSAGE).path(FIELD_CONTENT).asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Fallback: scans traces for the last text part from a model-authored event.
     * Prefers events marked {@code is_final: true}.
     */
    private String extractFinalLlmTextFromTraces(JsonNode tracesArray) {
        String lastModelText = null;
        String finalModelText = null;
        for (JsonNode event : tracesArray) {
            String author = event.path(FIELD_AUTHOR).asText();
            if (!isModelAuthor(author)) continue;
            boolean isFinal = event.path(FIELD_IS_FINAL).asBoolean(false);
            for (JsonNode part : event.path(FIELD_PARTS)) {
                if (PART_TEXT.equals(part.path(FIELD_TYPE).asText())) {
                    String text = part.path(FIELD_CONTENT).asText(null);
                    if (text != null && !text.isEmpty()) {
                        lastModelText = text;
                        if (isFinal) finalModelText = text;
                    }
                }
            }
        }
        return finalModelText != null ? finalModelText : lastModelText;
    }

    /** Returns true if the author represents the model (ADK uses "model", OpenAI-style uses "assistant"). */
    private boolean isModelAuthor(String author) {
        return AUTHOR_MODEL.equals(author) || AUTHOR_ASSISTANT.equals(author);
    }

    private Map<String, Object> extractRootInput(JsonNode tracesArray) {
        for (JsonNode event : tracesArray) {
            if (!AUTHOR_USER.equals(event.path(FIELD_AUTHOR).asText())) continue;
            for (JsonNode part : event.path(FIELD_PARTS)) {
                if (PART_TEXT.equals(part.path(FIELD_TYPE).asText())) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("content", part.path(FIELD_CONTENT).asText());
                    return m;
                }
            }
        }
        return Collections.emptyMap();
    }

    private Map<String, Object> buildRootOutput(String finalLlmText) {
        if (finalLlmText == null) return Collections.emptyMap();
        Map<String, Object> m = new HashMap<>();
        m.put("content", finalLlmText);
        return m;
    }

    private Map<String, Object> buildTraceMetadata(String invocationId, String modelName) {
        Map<String, Object> m = new HashMap<>();
        m.put("sourceType", SOURCE_TYPE);
        m.put("invocationId", invocationId);
        if (modelName != null) m.put("modelName", modelName);
        return m;
    }

    private Map<String, Object> spanMeta(String author, String toolName, String modelName) {
        Map<String, Object> m = new HashMap<>();
        m.put("sourceType", SOURCE_TYPE);
        if (author != null) m.put("author", author);
        if (toolName != null) m.put("toolName", toolName);
        if (modelName != null) m.put("modelName", modelName);
        return m;
    }

    /** Serializes a JsonNode to a compact JSON string, falling back to toString(). */
    private String safeJson(JsonNode node) {
        try {
            return OBJECT_MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            return node.toString();
        }
    }

    private String toJsonString(Object input) throws Exception {
        return input instanceof String
                ? (String) input
                : OBJECT_MAPPER.writeValueAsString(input);
    }
}

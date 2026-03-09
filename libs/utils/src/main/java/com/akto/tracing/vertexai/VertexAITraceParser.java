package com.akto.tracing.vertexai;

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
 * Parses Vertex AI ADK agent traces from Akto-ingested HTTP call data.
 *
 * <p>Expects the wrapper JSON produced by HttpCallParser.parseVertexAITrace():
 * <pre>
 * {
 *   "traces": [
 *     {"author":"user",      "invocation_id":"e-...", "is_final":false, "parts":[{"type":"text","content":"..."}]},
 *     {"author":"assistant", "invocation_id":"e-...", "is_final":false, "parts":[{"type":"tool_call","name":"...","args":{}}], "model":"..."},
 *     {"author":"assistant", "invocation_id":"e-...", "is_final":false, "parts":[{"type":"tool_result","name":"...","response":{}}]},
 *     {"author":"assistant", "invocation_id":"e-...", "is_final":true,  "parts":[{"type":"text","content":"..."}], "model":"...", "prompt_tokens":N, "completion_tokens":N, "total_tokens":N}
 *   ],
 *   "body": "{\"choices\":[{\"message\":{\"role\":\"model\",\"content\":\"...\"}}]}",
 *   "metadata": {"timestamp":..., "statusCode":200, "model":"...", "agentName":"...", "reasoningEngineId":"...", "invocationId":"..."}
 * }
 * </pre>
 *
 * <p>Span hierarchy produced:
 * <ul>
 *   <li>User span   (TASK,  depth=0, root)</li>
 *   <li>Agent span  (AGENT, depth=1, parent=user)</li>
 *   <li>LLM spans   (LLM,   depth=2, parent=agent) — one per model turn</li>
 *   <li>Tool spans  (TOOL,  depth=3, parent=owning LLM) — one per tool_call part, merged with matching tool_result</li>
 * </ul>
 */
public class VertexAITraceParser implements TraceParser {

    private static final VertexAITraceParser INSTANCE = new VertexAITraceParser();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String SOURCE_TYPE = "vertex-ai";

    // Field names in the wrapper JSON
    private static final String FIELD_TRACES = "traces";
    private static final String FIELD_METADATA = "metadata";
    private static final String FIELD_BODY = "body";
    private static final String FIELD_AUTHOR = "author";
    private static final String FIELD_INVOCATION_ID = "invocation_id";
    private static final String FIELD_IS_FINAL = "is_final";
    private static final String FIELD_PARTS = "parts";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_ARGS = "args";
    private static final String FIELD_RESPONSE = "response";
    private static final String FIELD_MODEL = "model";
    private static final String FIELD_CHOICES = "choices";
    private static final String FIELD_MESSAGE = "message";

    // Token fields on trace events
    private static final String FIELD_PROMPT_TOKENS = "prompt_tokens";
    private static final String FIELD_COMPLETION_TOKENS = "completion_tokens";
    private static final String FIELD_TOTAL_TOKENS = "total_tokens";

    // Metadata field names
    private static final String META_TIMESTAMP = "timestamp";
    private static final String META_STATUS_CODE = "statusCode";
    private static final String META_MODEL = "model";
    private static final String META_AGENT_NAME = "agentName";
    private static final String META_REASONING_ENGINE_ID = "reasoningEngineId";
    private static final String META_INVOCATION_ID = "invocationId";

    // Part types
    private static final String PART_TYPE_TEXT = "text";
    private static final String PART_TYPE_TOOL_CALL = "tool_call";
    private static final String PART_TYPE_TOOL_RESULT = "tool_result";

    // Author values
    private static final String AUTHOR_USER = "user";
    private static final String AUTHOR_MODEL = "model";
    private static final String AUTHOR_ASSISTANT = "assistant";

    // Timing offset per span (ms) to ensure sequential ordering
    private static final long SPAN_TIME_OFFSET_MS = 10;

    /**
     * Represents a single model turn: one LLM invocation with its tool calls and results.
     */
    private static class LlmTurn {
        final List<JsonNode> toolCallParts = new ArrayList<>();
        final List<JsonNode> toolResultParts = new ArrayList<>();
        String modelText;
        boolean isFinal;
        int promptTokens;
        int completionTokens;
        int totalTokens;
        String modelName;
    }

    public static VertexAITraceParser getInstance() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------
    // TraceParser interface
    // ------------------------------------------------------------------

    @Override
    public boolean canParse(Object input) {
        if (input == null) return false;

        try {
            String jsonStr = toJsonString(input);
            JsonNode root = OBJECT_MAPPER.readTree(jsonStr);

            if (!root.has(FIELD_TRACES) || !root.get(FIELD_TRACES).isArray()) {
                return false;
            }

            JsonNode traces = root.get(FIELD_TRACES);
            if (traces.size() == 0) {
                return false;
            }

            JsonNode firstTrace = traces.get(0);
            return firstTrace.has(FIELD_INVOCATION_ID) && firstTrace.has(FIELD_PARTS);
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
            JsonNode metadata = root.path(FIELD_METADATA);

            // Extract metadata
            long baseTimestamp = metadata.path(META_TIMESTAMP).asLong(System.currentTimeMillis());
            int statusCode = metadata.path(META_STATUS_CODE).asInt(200);
            String metadataModel = metadata.path(META_MODEL).asText(null);
            String agentName = metadata.path(META_AGENT_NAME).asText(null);
            String reasoningEngineId = metadata.path(META_REASONING_ENGINE_ID).asText("");
            String invocationId = metadata.path(META_INVOCATION_ID).asText(null);

            // Extract invocation_id from traces if not in metadata
            if (invocationId == null || invocationId.isEmpty()) {
                invocationId = tracesArray.get(0).path(FIELD_INVOCATION_ID).asText(UUID.randomUUID().toString());
            }

            // Use reasoning engine ID as agent name fallback
            if (agentName == null || agentName.isEmpty()) {
                agentName = reasoningEngineId.isEmpty() ? "Vertex AI Agent" : reasoningEngineId;
            }

            // Resolve model name: try body field first, then trace events, then metadata
            String modelName = extractModelNameFromBody(root);
            if (modelName == null) {
                modelName = extractModelNameFromTraces(tracesArray);
            }
            if (modelName == null && metadataModel != null && !metadataModel.isEmpty()) {
                modelName = metadataModel;
            }

            // Extract final LLM text: try body first, then traces
            String finalLlmText = extractFinalLlmTextFromBody(root);
            if (finalLlmText == null) {
                finalLlmText = extractFinalLlmTextFromTraces(tracesArray);
            }

            // Build spans with proper hierarchy
            List<Span> spans = buildSpans(tracesArray, invocationId, finalLlmText, modelName, agentName, baseTimestamp, statusCode);

            String rootSpanId = spans.isEmpty() ? null : spans.get(0).getId();
            long traceEndTimestamp = spans.isEmpty() ? baseTimestamp :
                    spans.stream().mapToLong(Span::getEndTimeMillis).max().orElse(baseTimestamp);

            boolean hasError = spans.stream().anyMatch(s -> "error".equals(s.getStatus()));

            // Accumulate tokens from LLM spans
            int totalTokens = 0;
            int totalInputTokens = 0;
            int totalOutputTokens = 0;
            for (Span span : spans) {
                if (TracingConstants.SpanKind.LLM.equals(span.getSpanKind()) && span.getMetadata() != null) {
                    totalTokens += getIntFromMetadata(span.getMetadata(), "totalTokens");
                    totalInputTokens += getIntFromMetadata(span.getMetadata(), "promptTokens");
                    totalOutputTokens += getIntFromMetadata(span.getMetadata(), "completionTokens");
                }
            }

            // Extract root input/output
            Map<String, Object> rootInput = extractRootInput(tracesArray);
            Map<String, Object> rootOutput = finalLlmText != null
                    ? Collections.singletonMap("content", finalLlmText)
                    : Collections.emptyMap();

            List<String> spanIds = spans.stream().map(Span::getId).collect(Collectors.toList());

            Trace trace = Trace.builder()
                    .id(invocationId)
                    .rootSpanId(rootSpanId)
                    .aiAgentName(agentName)
                    .name(agentName)
                    .startTimeMillis(baseTimestamp)
                    .endTimeMillis(traceEndTimestamp)
                    .status(hasError ? "error" : mapStatusFromHttpCode(statusCode))
                    .totalSpans(spans.size())
                    .totalTokens(totalTokens)
                    .totalInputTokens(totalInputTokens)
                    .totalOutputTokens(totalOutputTokens)
                    .rootInput(rootInput)
                    .rootOutput(rootOutput)
                    .spanIds(spanIds)
                    .metadata(buildTraceMetadata(invocationId, reasoningEngineId, modelName, statusCode))
                    .build();

            return TraceParseResult.builder()
                    .trace(trace)
                    .spans(spans)
                    .workflowId(reasoningEngineId)
                    .sourceIdentifier(invocationId)
                    .metadata(Collections.singletonMap("invocationId", invocationId))
                    .build();

        } catch (Exception e) {
            throw new Exception("Failed to parse Vertex AI trace: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, ServiceGraphEdgeInfo> extractServiceGraph(Object input) throws Exception {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(toJsonString(input));
            Map<String, ServiceGraphEdgeInfo> edges = new LinkedHashMap<>();
            if (!root.has(FIELD_TRACES)) return edges;

            JsonNode tracesArray = root.get(FIELD_TRACES);

            // user → agent edge
            {
                Map<String, Object> meta = new HashMap<>();
                meta.put("type", TracingConstants.SpanKind.AGENT);
                meta.put("edgeParam", TracingConstants.EdgeParamType.USER_INPUT);
                edges.put("user->agent", new ServiceGraphEdgeInfo("user", "agent", meta));
            }

            // agent → llm edge
            {
                Map<String, Object> meta = new HashMap<>();
                meta.put("type", TracingConstants.SpanKind.LLM);
                meta.put("edgeParam", TracingConstants.EdgeParamType.LLM_PROMPT);
                edges.put("agent->llm", new ServiceGraphEdgeInfo("agent", "llm", meta));
            }

            // llm → tool edges (one per unique tool name, with call count)
            Map<String, Integer> toolCallCount = new LinkedHashMap<>();
            for (JsonNode event : tracesArray) {
                if (!isModelAuthor(event.path(FIELD_AUTHOR).asText())) continue;
                for (JsonNode part : event.path(FIELD_PARTS)) {
                    if (PART_TYPE_TOOL_CALL.equals(part.path(FIELD_TYPE).asText())) {
                        String toolName = part.path(FIELD_NAME).asText("unknown_tool");
                        toolCallCount.merge(toolName, 1, Integer::sum);
                    }
                }
            }

            for (Map.Entry<String, Integer> entry : toolCallCount.entrySet()) {
                String toolName = entry.getKey();
                Map<String, Object> meta = new HashMap<>();
                meta.put("type", TracingConstants.SpanKind.TOOL);
                meta.put("callCount", entry.getValue());
                meta.put("edgeParam", TracingConstants.EdgeParamType.TOOL_INPUT);
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

    // ------------------------------------------------------------------
    // Span building
    // ------------------------------------------------------------------

    /**
     * Builds the span list with proper hierarchy:
     * user (depth 0) → agent (depth 1) → LLM (depth 2) → tool (depth 3).
     */
    private List<Span> buildSpans(JsonNode tracesArray, String traceId,
                                  String finalLlmText, String modelName, String agentName,
                                  long baseTimestamp, int statusCode) {
        List<Span> spans = new ArrayList<>();
        long currentTime = baseTimestamp;

        // 1. User span (root, TASK, depth 0)
        Span userSpan = null;
        for (JsonNode event : tracesArray) {
            if (AUTHOR_USER.equals(event.path(FIELD_AUTHOR).asText())) {
                for (JsonNode part : event.path(FIELD_PARTS)) {
                    if (PART_TYPE_TEXT.equals(part.path(FIELD_TYPE).asText()) && userSpan == null) {
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
                .name(agentName)
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
        List<LlmTurn> turns = groupIntoTurns(tracesArray);

        // 4. Create LLM + Tool spans for each turn
        boolean hasError = false;
        for (int turnIdx = 0; turnIdx < turns.size(); turnIdx++) {
            LlmTurn turn = turns.get(turnIdx);
            String llmSpanId = UUID.randomUUID().toString();
            boolean isLastTurn = (turnIdx == turns.size() - 1);

            // LLM span input
            Map<String, Object> llmInput = new HashMap<>();
            if (turnIdx == 0 && userSpan != null) {
                llmInput.putAll(userSpan.getInput());
            } else if (turnIdx > 0) {
                LlmTurn prevTurn = turns.get(turnIdx - 1);
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

            // Resolve model name for this turn
            String turnModel = turn.modelName != null ? turn.modelName : modelName;

            // LLM span metadata with token counts
            Map<String, Object> llmMeta = spanMeta(AUTHOR_MODEL, null, turnModel);
            if (turn.totalTokens > 0) {
                llmMeta.put("totalTokens", turn.totalTokens);
                llmMeta.put("promptTokens", turn.promptTokens);
                llmMeta.put("completionTokens", turn.completionTokens);
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
                    .metadata(llmMeta)
                    .modelName(turnModel)
                    .depth(2)
                    .tags(Arrays.asList(SOURCE_TYPE, "llm"))
                    .build();
            spans.add(llmSpan);
            currentTime += SPAN_TIME_OFFSET_MS;

            // Tool spans — merge tool_call with matching tool_result by name
            Map<String, JsonNode> resultsByName = new LinkedHashMap<>();
            for (JsonNode tr : turn.toolResultParts) {
                String name = tr.path(FIELD_NAME).asText();
                resultsByName.put(name, tr);
            }

            for (int i = 0; i < turn.toolCallParts.size(); i++) {
                JsonNode toolCallPart = turn.toolCallParts.get(i);
                String toolName = toolCallPart.path(FIELD_NAME).asText("unknown_tool");

                Map<String, Object> toolInput = new HashMap<>();
                toolInput.put("name", toolName);
                toolInput.put("args", safeJson(toolCallPart.path(FIELD_ARGS)));

                Map<String, Object> toolOutput = new HashMap<>();
                String toolStatus = "success";
                String errorMessage = null;

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
                        errorMessage = responseNode.has("error")
                                ? responseNode.path("error").asText()
                                : responseNode.path("errorMessage").asText();
                        hasError = true;
                    }
                    String responseText = responseNode.isTextual() ? responseNode.asText() : responseStr;
                    if (responseText != null && (responseText.toLowerCase().contains("\"error\"")
                            || responseText.toLowerCase().contains("traceback")
                            || responseText.toLowerCase().contains("exception"))) {
                        toolStatus = "error";
                        if (errorMessage == null) {
                            errorMessage = "Tool execution error detected in response";
                        }
                        hasError = true;
                    }
                } else {
                    toolOutput.put("response", "no result");
                }

                // MCP tool recognition
                String spanKind = toolName.toLowerCase().contains("mcp")
                        ? TracingConstants.SpanKind.MCP_SERVER
                        : TracingConstants.SpanKind.TOOL;

                Span toolSpan = Span.builder()
                        .id(UUID.randomUUID().toString())
                        .traceId(traceId)
                        .parentSpanId(llmSpanId)
                        .spanKind(spanKind)
                        .name(toolName)
                        .startTimeMillis(currentTime)
                        .endTimeMillis(currentTime + SPAN_TIME_OFFSET_MS)
                        .status(toolStatus)
                        .errorMessage(errorMessage)
                        .input(toolInput)
                        .output(toolOutput)
                        .metadata(spanMeta(null, toolName, turnModel))
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
     * Groups trace events into sequential LLM turns.
     * A new turn starts when we see a model event with tool_call parts after processing
     * tool results, or the first model event.
     */
    private List<LlmTurn> groupIntoTurns(JsonNode tracesArray) {
        List<LlmTurn> turns = new ArrayList<>();
        LlmTurn currentTurn = null;

        for (JsonNode event : tracesArray) {
            String author = event.path(FIELD_AUTHOR).asText();
            JsonNode parts = event.path(FIELD_PARTS);
            if (!parts.isArray()) continue;

            boolean isFinal = event.path(FIELD_IS_FINAL).asBoolean(false);
            String eventModel = event.has(FIELD_MODEL) && !event.get(FIELD_MODEL).isNull()
                    ? event.get(FIELD_MODEL).asText() : null;

            // Extract token counts from event level
            int promptTokens = event.path(FIELD_PROMPT_TOKENS).asInt(0);
            int completionTokens = event.path(FIELD_COMPLETION_TOKENS).asInt(0);
            int totalTokens = event.path(FIELD_TOTAL_TOKENS).asInt(0);

            for (JsonNode part : parts) {
                String partType = part.path(FIELD_TYPE).asText();

                if (PART_TYPE_TEXT.equals(partType) && AUTHOR_USER.equals(author)) {
                    // Skip user text — already handled as user span
                    continue;
                }

                if (PART_TYPE_TOOL_CALL.equals(partType) && isModelAuthor(author)) {
                    if (currentTurn == null || !currentTurn.toolResultParts.isEmpty()) {
                        currentTurn = new LlmTurn();
                        turns.add(currentTurn);
                    }
                    currentTurn.toolCallParts.add(part);
                    if (eventModel != null) currentTurn.modelName = eventModel;

                } else if (PART_TYPE_TOOL_RESULT.equals(partType)) {
                    if (currentTurn == null) {
                        currentTurn = new LlmTurn();
                        turns.add(currentTurn);
                    }
                    currentTurn.toolResultParts.add(part);

                } else if (PART_TYPE_TEXT.equals(partType) && isModelAuthor(author)) {
                    String text = part.path(FIELD_CONTENT).asText(null);
                    if (currentTurn == null || !currentTurn.toolResultParts.isEmpty()) {
                        currentTurn = new LlmTurn();
                        turns.add(currentTurn);
                    }
                    currentTurn.modelText = text;
                    currentTurn.isFinal = isFinal;
                    if (eventModel != null) currentTurn.modelName = eventModel;
                    // Attach token counts to this turn (final text events carry token data)
                    if (totalTokens > 0) {
                        currentTurn.promptTokens = promptTokens;
                        currentTurn.completionTokens = completionTokens;
                        currentTurn.totalTokens = totalTokens;
                    }
                }
            }
        }

        // Ensure at least one turn exists
        if (turns.isEmpty()) {
            turns.add(new LlmTurn());
        }

        return turns;
    }

    // ------------------------------------------------------------------
    // Data extraction helpers
    // ------------------------------------------------------------------

    private String extractModelNameFromBody(JsonNode root) {
        try {
            JsonNode bodyNode = root.path(FIELD_BODY);
            if (bodyNode.isMissingNode() || bodyNode.isNull()) return null;
            String bodyStr = bodyNode.isTextual() ? bodyNode.asText() : OBJECT_MAPPER.writeValueAsString(bodyNode);
            JsonNode body = OBJECT_MAPPER.readTree(bodyStr);
            String model = body.path(FIELD_MODEL).asText(null);
            return (model != null && !model.isEmpty()) ? model : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractModelNameFromTraces(JsonNode tracesArray) {
        for (JsonNode event : tracesArray) {
            if (event.has(FIELD_MODEL) && !event.get(FIELD_MODEL).isNull()) {
                String model = event.get(FIELD_MODEL).asText();
                if (!model.isEmpty()) return model;
            }
        }
        return null;
    }

    private String extractFinalLlmTextFromBody(JsonNode root) {
        try {
            JsonNode bodyNode = root.path(FIELD_BODY);
            if (bodyNode.isMissingNode() || bodyNode.isNull()) return null;
            String bodyStr = bodyNode.isTextual() ? bodyNode.asText() : OBJECT_MAPPER.writeValueAsString(bodyNode);
            JsonNode body = OBJECT_MAPPER.readTree(bodyStr);
            return body.path(FIELD_CHOICES).get(0)
                    .path(FIELD_MESSAGE).path(FIELD_CONTENT).asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractFinalLlmTextFromTraces(JsonNode tracesArray) {
        String lastModelText = null;
        String finalModelText = null;
        for (JsonNode event : tracesArray) {
            String author = event.path(FIELD_AUTHOR).asText();
            if (!isModelAuthor(author)) continue;
            boolean isFinal = event.path(FIELD_IS_FINAL).asBoolean(false);
            for (JsonNode part : event.path(FIELD_PARTS)) {
                if (PART_TYPE_TEXT.equals(part.path(FIELD_TYPE).asText())) {
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

    private boolean isModelAuthor(String author) {
        return AUTHOR_MODEL.equals(author) || AUTHOR_ASSISTANT.equals(author);
    }

    private Map<String, Object> extractRootInput(JsonNode tracesArray) {
        for (JsonNode event : tracesArray) {
            if (!AUTHOR_USER.equals(event.path(FIELD_AUTHOR).asText())) continue;
            for (JsonNode part : event.path(FIELD_PARTS)) {
                if (PART_TYPE_TEXT.equals(part.path(FIELD_TYPE).asText())) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("content", part.path(FIELD_CONTENT).asText());
                    return m;
                }
            }
        }
        return Collections.emptyMap();
    }

    // ------------------------------------------------------------------
    // Metadata builders
    // ------------------------------------------------------------------

    private Map<String, Object> buildTraceMetadata(String invocationId, String reasoningEngineId, String model, int statusCode) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sourceType", SOURCE_TYPE);
        metadata.put("invocationId", invocationId);
        metadata.put("reasoningEngineId", reasoningEngineId);
        if (model != null) metadata.put("model", model);
        metadata.put("statusCode", statusCode);
        return metadata;
    }

    private Map<String, Object> spanMeta(String author, String toolName, String modelName) {
        Map<String, Object> m = new HashMap<>();
        m.put("sourceType", SOURCE_TYPE);
        if (author != null) m.put("author", author);
        if (toolName != null) m.put("toolName", toolName);
        if (modelName != null) m.put("modelName", modelName);
        return m;
    }

    // ------------------------------------------------------------------
    // Utility
    // ------------------------------------------------------------------

    private String mapStatusFromHttpCode(int statusCode) {
        if (statusCode >= 200 && statusCode < 300) return "success";
        if (statusCode >= 400) return "error";
        return "unknown";
    }

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

    private int getIntFromMetadata(Map<String, Object> metadata, String key) {
        Object val = metadata.get(key);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        return 0;
    }
}

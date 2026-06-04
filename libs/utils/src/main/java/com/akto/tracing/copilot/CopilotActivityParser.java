package com.akto.tracing.copilot;

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
 * Parses Microsoft Copilot Studio autonomous-mode activity logs into {@link Trace} + {@link Span}s.
 *
 * <p>Input: JSON with a top-level {@code activities} array produced by Copilot Studio.
 *
 * <p>Span hierarchy:
 * <pre>
 *   ROOT (AGENT)
 *   └── Planning-N (PLANNING)  — one per DynamicPlanReceived event
 *       └── ToolName (TOOL)    — one per DynamicPlanStepTriggered stepId
 * </pre>
 */
public class CopilotActivityParser implements TraceParser {

    private static final CopilotActivityParser INSTANCE = new CopilotActivityParser();
    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String SOURCE_TYPE = "copilot_studio";
    private static final String DEFAULT_AGENT_NAME = "Copilot Agent";

    // Activity types
    private static final String TYPE_MESSAGE = "message";
    private static final String TYPE_EVENT = "event";
    private static final String TYPE_TRACE = "trace";
    private static final String TYPE_END_OF_CONVERSATION = "endOfConversation";

    // Event names
    private static final String EVENT_PLAN_RECEIVED = "DynamicPlanReceived";
    private static final String EVENT_PLAN_RECEIVED_DEBUG = "DynamicPlanReceivedDebug";
    private static final String EVENT_STEP_TRIGGERED = "DynamicPlanStepTriggered";
    private static final String EVENT_STEP_BIND_UPDATE = "DynamicPlanStepBindUpdate";
    private static final String EVENT_STEP_FINISHED = "DynamicPlanStepFinished";
    private static final String SUFFIX_TOOL_TRACE_DATA = "TraceData";

    // from.role values
    private static final int ROLE_BOT = 0;
    private static final int ROLE_USER = 1;

    private static final int MAX_TEXT_LEN = 3000;
    private static final int MAX_THOUGHT_LEN = 2000;
    private static final int MAX_SNIPPET_LEN = 1000;

    public static CopilotActivityParser getInstance() { return INSTANCE; }

    @Override
    public String getSourceType() { return SOURCE_TYPE; }

    // ── canParse ──────────────────────────────────────────────────────────

    @Override
    public boolean canParse(Object input) {
        if (input == null) return false;
        try {
            String json = toJson(input);
            JsonNode root = OBJECT_MAPPER.readTree(json);
            JsonNode activities = root.path("activities");
            if (!activities.isArray() || activities.size() == 0) return false;
            boolean hasUserMessage = false;
            boolean hasDynamicPlan = false;
            for (JsonNode act : activities) {
                if (TYPE_MESSAGE.equals(act.path("type").asText()) &&
                        act.path("from").path("role").asInt(-1) == ROLE_USER)
                    hasUserMessage = true;
                if (act.path("name").asText("").startsWith("DynamicPlan"))
                    hasDynamicPlan = true;
                if (hasUserMessage && hasDynamicPlan) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    // ── parse ─────────────────────────────────────────────────────────────

    @Override
    public TraceParseResult parse(Object input) throws Exception {
        String json = toJson(input);
        JsonNode root = OBJECT_MAPPER.readTree(json);
        List<JsonNode> acts = new ArrayList<>();
        root.path("activities").forEach(acts::add);

        // ── Pass 1: anchor IDs, timestamps, top-level metadata ────────────
        String userMessageId = null;
        String userMessageText = null;
        String botMessageText = null;
        String conversationId = null;
        long firstMs = Long.MAX_VALUE;
        long lastMs = Long.MIN_VALUE;
        boolean hasAnyPlan = false;
        List<Map<String, Object>> endOfConvMeta = new ArrayList<>();
        Map<String, Object> convInfoMeta = null;

        for (JsonNode act : acts) {
            long tsMs = act.path("timestampMs").asLong(0);
            if (tsMs > 0) {
                if (tsMs < firstMs) firstMs = tsMs;
                if (tsMs > lastMs) lastMs = tsMs;
            }
            String type = act.path("type").asText("");
            String name = act.path("name").asText("");
            int role = act.path("from").path("role").asInt(-1);

            if (TYPE_MESSAGE.equals(type) && role == ROLE_USER && userMessageId == null) {
                userMessageId = act.path("id").asText(null);
                userMessageText = act.path("text").asText(null);
            }
            if (TYPE_MESSAGE.equals(type) && role == ROLE_BOT) {
                String t = act.path("text").asText(null);
                if (t != null && !t.isEmpty()) botMessageText = t;
            }
            if (TYPE_END_OF_CONVERSATION.equals(type) && role == ROLE_BOT) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", act.path("id").asText(""));
                m.put("timestampMs", tsMs);
                endOfConvMeta.add(m);
            }
            if (name.startsWith("DynamicPlan")) hasAnyPlan = true;
            if (TYPE_TRACE.equals(type) && convInfoMeta == null) {
                Map<String, Object> tmp = new LinkedHashMap<>();
                JsonNode val = act.path("value");
                val.fields().forEachRemaining(e -> tmp.put(e.getKey(), e.getValue().asText()));
                convInfoMeta = tmp;
            }
            if (conversationId == null && name.endsWith(SUFFIX_TOOL_TRACE_DATA)) {
                String cid = act.path("value").path("searchContextTraceInfo")
                        .path("conversationId").asText(null);
                if (cid != null && !cid.isEmpty()) conversationId = cid;
            }
        }

        // Fallbacks
        if (conversationId == null || conversationId.isEmpty()) conversationId = userMessageId;
        if (conversationId == null) conversationId = UUID.randomUUID().toString();
        if (userMessageId == null) userMessageId = conversationId;
        if (firstMs == Long.MAX_VALUE) firstMs = System.currentTimeMillis();
        if (lastMs == Long.MIN_VALUE) lastMs = firstMs;

        String traceId = conversationId;
        String rootSpanId = userMessageId;
        // blocked = endOfConversation events exist but no planning ever ran
        String status = (!hasAnyPlan && !endOfConvMeta.isEmpty()) ? "blocked" : "success";

        // ── Root span ─────────────────────────────────────────────────────
        Map<String, Object> rootMeta = new LinkedHashMap<>();
        rootMeta.put("sourceType", SOURCE_TYPE);
        if (convInfoMeta != null && !convInfoMeta.isEmpty()) rootMeta.put("conversationInfo", convInfoMeta);
        if (!endOfConvMeta.isEmpty()) rootMeta.put("endOfConversation", endOfConvMeta);

        Map<String, Object> rootInput = new LinkedHashMap<>();
        if (userMessageText != null) rootInput.put("text", truncate(userMessageText, MAX_TEXT_LEN));
        Map<String, Object> rootOutput = new LinkedHashMap<>();
        if (botMessageText != null) rootOutput.put("text", truncate(botMessageText, MAX_TEXT_LEN));

        Span rootSpan = Span.builder()
                .id(rootSpanId).traceId(traceId).parentSpanId(null)
                .spanKind(TracingConstants.SpanKind.AGENT)
                .name(DEFAULT_AGENT_NAME)
                .startTimeMillis(firstMs).endTimeMillis(lastMs)
                .status(status)
                .input(rootInput).output(rootOutput).metadata(rootMeta)
                .depth(0).tags(Arrays.asList(SOURCE_TYPE, "copilot"))
                .build();

        List<Span> spans = new ArrayList<>();
        spans.add(rootSpan);

        if ("blocked".equals(status)) {
            return buildResult(traceId, rootSpanId, spans, firstMs, lastMs, status, userMessageText, botMessageText);
        }

        // ── Pass 2: planning and tool spans ───────────────────────────────
        Map<String, Span> lastPlanByPlanId = new LinkedHashMap<>();
        Map<String, Span> toolByStepId = new LinkedHashMap<>();
        Span activeToolSpan = null;
        int planningRound = 0;          // counter for "Planning Round N"
        Map<String, Integer> stepIndexByPlanId = new LinkedHashMap<>();  // step counter per plan

        for (JsonNode act : acts) {
            if (!TYPE_EVENT.equals(act.path("type").asText())) continue;
            String name = act.path("name").asText("");
            long tsMs = act.path("timestampMs").asLong(firstMs);
            JsonNode value = act.path("value");

            if (EVENT_PLAN_RECEIVED.equals(name)) {
                String planId = value.path("planIdentifier").asText(null);
                if (planId == null) continue;
                planningRound++;
                String eventId = act.path("id").asText(UUID.randomUUID().toString());
                boolean isFinalPlan = value.path("isFinalPlan").asBoolean(false);
                List<String> steps = new ArrayList<>();
                value.path("steps").forEach(s -> steps.add(s.asText().replaceFirst("^P:", "")));

                Map<String, Object> planMeta = new LinkedHashMap<>();
                planMeta.put("sourceType", SOURCE_TYPE);
                planMeta.put("planIdentifier", planId);
                planMeta.put("isFinalPlan", isFinalPlan);

                // input shows what this planning round intends to do
                Map<String, Object> planInput = new LinkedHashMap<>();
                planInput.put("plannedSteps", steps);

                Span planSpan = Span.builder()
                        .id(eventId).traceId(traceId).parentSpanId(rootSpanId)
                        .spanKind(TracingConstants.SpanKind.PLANNING)
                        .name("Planning Round " + planningRound)
                        .startTimeMillis(tsMs).endTimeMillis(tsMs)
                        .status(status)
                        .input(planInput).output(new LinkedHashMap<>())
                        .metadata(planMeta)
                        .depth(1).tags(Arrays.asList(SOURCE_TYPE, "planning"))
                        .build();

                lastPlanByPlanId.put(planId, planSpan);
                stepIndexByPlanId.put(planId, 0);
                spans.add(planSpan);

            } else if (EVENT_PLAN_RECEIVED_DEBUG.equals(name)) {
                // DynamicPlanReceivedDebug just repeats the user ask — skip it,
                // the root span already holds the user question.

            } else if (EVENT_STEP_TRIGGERED.equals(name)) {
                String stepId = value.path("stepId").asText(null);
                if (stepId == null) continue;
                String planId = value.path("planIdentifier").asText(null);
                String toolName = value.path("taskDialogId").asText("Tool").replaceFirst("^P:", "");
                String thought = value.path("thought").asText(null);

                Span parentPlan = planId != null ? lastPlanByPlanId.get(planId) : null;
                String parentId = parentPlan != null ? parentPlan.getId() : rootSpanId;

                // thought is the planner's reasoning — put it on the PLANNING span, not the tool span
                if (thought != null && !thought.isEmpty() && parentPlan != null) {
                    Map<String, Object> planInput = parentPlan.getInput();
                    if (planInput == null) { planInput = new LinkedHashMap<>(); parentPlan.setInput(planInput); }
                    // accumulate per-step reasoning under planningReason list
                    @SuppressWarnings("unchecked")
                    List<String> reasons = (List<String>) planInput.get("planningReason");
                    if (reasons == null) { reasons = new ArrayList<>(); planInput.put("planningReason", reasons); }
                    reasons.add(truncate(thought, MAX_THOUGHT_LEN));
                }

                // step number within this planning round
                int stepIdx = stepIndexByPlanId.getOrDefault(planId, 0) + 1;
                if (planId != null) stepIndexByPlanId.put(planId, stepIdx);
                String toolDisplayName = toolName + " (Step " + stepIdx + ")";

                Map<String, Object> toolMeta = new LinkedHashMap<>();
                toolMeta.put("sourceType", SOURCE_TYPE);

                Span toolSpan = Span.builder()
                        .id(stepId).traceId(traceId).parentSpanId(parentId)
                        .spanKind(TracingConstants.SpanKind.TOOL)
                        .name(toolDisplayName)
                        .startTimeMillis(tsMs).endTimeMillis(tsMs)
                        .status("running")
                        .input(new LinkedHashMap<>()).output(new LinkedHashMap<>())
                        .metadata(toolMeta)
                        .depth(2).tags(Arrays.asList(SOURCE_TYPE, toolName))
                        .build();

                toolByStepId.put(stepId, toolSpan);
                activeToolSpan = toolSpan;
                spans.add(toolSpan);

            } else if (EVENT_STEP_BIND_UPDATE.equals(name)) {
                String stepId = value.path("stepId").asText(null);
                if (stepId == null) continue;
                Span toolSpan = toolByStepId.get(stepId);
                if (toolSpan == null) continue;
                Map<String, Object> toolInput = new LinkedHashMap<>();
                value.path("arguments").fields().forEachRemaining(e ->
                        toolInput.put(e.getKey(), e.getValue().isTextual() ? e.getValue().asText() : e.getValue().toString()));
                toolSpan.setInput(toolInput);

            } else if (name.endsWith(SUFFIX_TOOL_TRACE_DATA)) {
                // No direct stepId — attach to the current active (unterminated) tool span
                if (activeToolSpan == null) continue;
                Map<String, Object> meta = activeToolSpan.getMetadata();
                if (meta == null) { meta = new LinkedHashMap<>(); activeToolSpan.setMetadata(meta); }
                JsonNode traceInfo = value.path("searchContextTraceInfo");
                if (!traceInfo.isMissingNode()) {
                    List<String> endpoints = new ArrayList<>();
                    traceInfo.path("endpoints").forEach(e -> endpoints.add(e.asText()));
                    if (!endpoints.isEmpty()) meta.put("endpoints", endpoints);
                }
                List<String> knowledgeSources = new ArrayList<>();
                value.path("knowledgeSources").forEach(ks -> knowledgeSources.add(ks.asText()));
                if (!knowledgeSources.isEmpty()) meta.put("knowledgeSources", knowledgeSources);

            } else if (EVENT_STEP_FINISHED.equals(name)) {
                String stepId = value.path("stepId").asText(null);
                String planId = value.path("planIdentifier").asText(null);
                if (stepId == null) continue;
                Span toolSpan = toolByStepId.get(stepId);
                if (toolSpan == null) continue;

                // Tool output from observation
                Map<String, Object> toolOutput = extractSearchResults(value.path("observation"));
                toolSpan.setOutput(toolOutput);
                toolSpan.setEndTimeMillis(tsMs);
                toolSpan.setStatus(status);
                activeToolSpan = null;

                // Add metadata: execution time and cost
                String execTime = value.path("executionTime").asText(null);
                int cost = value.path("displayedCost").asInt(-1);
                if (execTime != null) toolSpan.getMetadata().put("executionTime", execTime);
                if (cost >= 0) toolSpan.getMetadata().put("displayedCost", cost);

                // Update parent planning span: extend end time, update completion summary in output
                if (planId != null) {
                    Span planSpan = lastPlanByPlanId.get(planId);
                    if (planSpan != null) {
                        if (tsMs > planSpan.getEndTimeMillis()) planSpan.setEndTimeMillis(tsMs);
                        Map<String, Object> planOutput = planSpan.getOutput();
                        if (planOutput == null) { planOutput = new LinkedHashMap<>(); planSpan.setOutput(planOutput); }
                        int completed = ((Number) planOutput.getOrDefault("completedSteps", 0)).intValue() + 1;
                        planOutput.put("completedSteps", completed);
                        int resultsFound = 0;
                        Object sr = toolOutput.get("search_results");
                        if (sr instanceof List) resultsFound = ((List<?>) sr).size();
                        int prev = ((Number) planOutput.getOrDefault("totalResultsFound", 0)).intValue();
                        planOutput.put("totalResultsFound", prev + resultsFound);
                    }
                }
            }
        }

        return buildResult(traceId, rootSpanId, spans, firstMs, lastMs, status, userMessageText, botMessageText);
    }

    // ── extractServiceGraph ───────────────────────────────────────────────

    @Override
    public Map<String, ServiceGraphEdgeInfo> extractServiceGraph(Object input) throws Exception {
        String json = toJson(input);
        JsonNode root = OBJECT_MAPPER.readTree(json);
        Map<String, ServiceGraphEdgeInfo> edges = new LinkedHashMap<>();

        String userQuestion = null;
        boolean planSeen = false;

        for (JsonNode act : root.path("activities")) {
            String type = act.path("type").asText("");
            String name = act.path("name").asText("");
            int role = act.path("from").path("role").asInt(-1);
            JsonNode value = act.path("value");

            if (TYPE_MESSAGE.equals(type) && role == ROLE_USER && userQuestion == null)
                userQuestion = truncate(act.path("text").asText(null), 200);

            if (EVENT_PLAN_RECEIVED.equals(name) && !planSeen) {
                // Agent → Planning edge (add once)
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("type", TracingConstants.SpanKind.PLANNING);
                meta.put("edgeParam", edgeParam(TracingConstants.EdgeParamType.USER_INPUT, userQuestion));
                edges.put("Planning", new ServiceGraphEdgeInfo(DEFAULT_AGENT_NAME, "Planning", meta));
                planSeen = true;
            }

            if (EVENT_STEP_BIND_UPDATE.equals(name)) {
                String toolName = value.path("taskDialogId").asText("Tool").replaceFirst("^P:", "");
                String searchQuery = value.path("arguments").path("search_query").asText(null);
                // Planning → Tool edge (deduplicated by tool name)
                if (!edges.containsKey(toolName)) {
                    Map<String, Object> meta = new LinkedHashMap<>();
                    meta.put("type", TracingConstants.SpanKind.TOOL);
                    meta.put("edgeParam", edgeParam(TracingConstants.EdgeParamType.TOOL_INPUT, searchQuery));
                    edges.put(toolName, new ServiceGraphEdgeInfo("Planning", toolName, meta));
                }
            }
        }
        return edges;
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private TraceParseResult buildResult(String traceId, String rootSpanId, List<Span> spans,
            long startMs, long endMs, String status, String userText, String botText) {
        Map<String, Object> rootInput = new LinkedHashMap<>();
        if (userText != null) rootInput.put("text", truncate(userText, MAX_TEXT_LEN));
        Map<String, Object> rootOutput = new LinkedHashMap<>();
        if (botText != null) rootOutput.put("text", truncate(botText, MAX_TEXT_LEN));

        List<String> spanIds = spans.stream().map(Span::getId).collect(Collectors.toList());

        Trace trace = Trace.builder()
                .id(traceId).rootSpanId(rootSpanId)
                .aiAgentName(DEFAULT_AGENT_NAME).name(DEFAULT_AGENT_NAME)
                .startTimeMillis(startMs).endTimeMillis(endMs)
                .status(status).totalSpans(spans.size())
                .totalTokens(0).totalInputTokens(0).totalOutputTokens(0)
                .rootInput(rootInput).rootOutput(rootOutput)
                .spanIds(spanIds)
                .metadata(Collections.singletonMap("sourceType", (Object) SOURCE_TYPE))
                .build();

        return TraceParseResult.builder()
                .trace(trace).spans(spans)
                .workflowId(traceId).sourceIdentifier(traceId)
                .metadata(Collections.singletonMap("sourceType", SOURCE_TYPE))
                .build();
    }

    /** Extracts search results from a DynamicPlanStepFinished observation node. */
    private Map<String, Object> extractSearchResults(JsonNode observation) {
        Map<String, Object> output = new LinkedHashMap<>();
        if (observation.isMissingNode()) return output;
        JsonNode results = observation.path("search_result").path("search_results");
        if (!results.isArray() || results.size() == 0) return output;
        List<Map<String, Object>> list = new ArrayList<>();
        results.forEach(r -> {
            Map<String, Object> item = new LinkedHashMap<>();
            String name = r.path("Name").asText(r.path("name").asText(""));
            String url = r.path("Url").asText(r.path("url").asText(""));
            String text = r.path("Text").asText(r.path("text").asText(""));
            if (!name.isEmpty()) item.put("name", name);
            if (!url.isEmpty()) item.put("url", url);
            if (!text.isEmpty()) item.put("snippet", truncate(text, MAX_SNIPPET_LEN));
            if (!item.isEmpty()) list.add(item);
        });
        if (!list.isEmpty()) output.put("search_results", list);
        return output;
    }

    private static Map<String, Object> edgeParam(String type, String data) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        if (data != null) m.put("data", data);
        return m;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    private static String toJson(Object input) throws Exception {
        return input instanceof String ? (String) input : OBJECT_MAPPER.writeValueAsString(input);
    }
}

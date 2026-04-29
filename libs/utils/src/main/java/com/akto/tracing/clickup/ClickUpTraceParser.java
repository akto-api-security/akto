package com.akto.tracing.clickup;

import com.akto.dto.ApiCollection.ServiceGraphEdgeInfo;
import com.akto.dto.tracing.Span;
import com.akto.dto.tracing.Trace;
import com.akto.dto.tracing.TracingConstants;
import com.akto.tracing.TraceParseResult;
import com.akto.tracing.TraceParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Parses ClickUp AI trace summary JSON (from ingest {@link Constants#CLICKUP_TRACE_METADATA_KEY}) into {@link Trace} / {@link Span}
 * for persistence via {@code dataActor.storeTrace} / {@code storeSpans}, same end state as {@link com.akto.tracing.n8n.N8nTraceParser}.
 * Ingest wraps the summary JSON under key {@code clickupTraceMetadata} (see {@link com.akto.util.Constants#CLICKUP_TRACE_METADATA_KEY}).
 */
public class ClickUpTraceParser implements TraceParser {

    private static final ClickUpTraceParser INSTANCE = new ClickUpTraceParser();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String SOURCE_TYPE = "clickup";

    public static ClickUpTraceParser getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean canParse(Object input) {
        if (input == null) {
            return false;
        }
        try {
            String jsonStr = input instanceof String ? (String) input : OBJECT_MAPPER.writeValueAsString(input);
            JsonNode root = OBJECT_MAPPER.readTree(jsonStr);
            return resolveTraceId(root) != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public TraceParseResult parse(Object input) throws Exception {
        String jsonStr = input instanceof String ? (String) input : OBJECT_MAPPER.writeValueAsString(input);
        JsonNode root = OBJECT_MAPPER.readTree(jsonStr);
        if (!canParse(input)) {
            throw new Exception("Invalid ClickUp trace: " + jsonStr);
        }
        String traceId = resolveTraceId(root);
        String displayName = resolveDisplayName(root);
        long startMs = resolveStartMillis(root);
        long endMs = resolveEndMillis(root, startMs);
        String status = mapStatus(root.path("status").asText("unknown"));

        List<Span> spans = buildSpans(root, traceId);
        if (spans.isEmpty()) {
            throw new Exception("ClickUp trace produced no spans for id: " + traceId);
        }
        String rootSpanId = spans.get(0).getId();
        List<String> spanIds = spans.stream().map(Span::getId).collect(Collectors.toList());

        Map<String, Object> rootInput = new HashMap<>();
        rootInput.put("summary", root.toString());

        Trace trace = Trace.builder()
                .id(traceId)
                .rootSpanId(rootSpanId)
                .aiAgentName("ClickUp")
                .name(displayName)
                .startTimeMillis(startMs)
                .endTimeMillis(endMs)
                .status(status)
                .totalSpans(spans.size())
                .totalTokens(0)
                .totalInputTokens(0)
                .totalOutputTokens(0)
                .rootInput(rootInput)
                .rootOutput(Collections.emptyMap())
                .spanIds(spanIds)
                .metadata(buildTraceMetadata(root, traceId))
                .build();

        return TraceParseResult.builder()
                .trace(trace)
                .spans(spans)
                .workflowId(traceId)
                .sourceIdentifier(traceId)
                .metadata(Collections.singletonMap("traceSummaryId", traceId))
                .build();
    }

    @Override
    public Map<String, ServiceGraphEdgeInfo> extractServiceGraph(Object input) {
        return new HashMap<>();
    }

    @Override
    public String getSourceType() {
        return SOURCE_TYPE;
    }

    private static List<Span> buildSpans(JsonNode root, String traceId) {
        List<Span> spans = new ArrayList<>();
        JsonNode events = root.path("events");
        if (events.isArray() && events.size() > 0) {
            String rootSpanId = traceId + "-root";
            spans.add(Span.builder()
                    .id(rootSpanId)
                    .traceId(traceId)
                    .parentSpanId(null)
                    .spanKind(TracingConstants.SpanKind.WORKFLOW)
                    .name("clickup-trace")
                    .startTimeMillis(resolveStartMillis(root))
                    .endTimeMillis(resolveEndMillis(root, resolveStartMillis(root)))
                    .status("success")
                    .metadata(Collections.singletonMap("source", SOURCE_TYPE))
                    .depth(0)
                    .tags(Collections.singletonList(SOURCE_TYPE))
                    .build());
            int i = 0;
            for (JsonNode ev : events) {
                String spanId = traceId + "-e-" + (i++);
                String evName = ev.path("eventType").asText(null);
                if (evName == null || evName.isEmpty()) {
                    evName = ev.path("type").asText("event");
                }
                long evStart = parseTimeField(ev, "timestamp", "date", "time");
                if (evStart <= 0) {
                    evStart = resolveStartMillis(root);
                }
                spans.add(Span.builder()
                        .id(spanId)
                        .traceId(traceId)
                        .parentSpanId(rootSpanId)
                        .spanKind(spanKindForEvent(evName))
                        .name(evName)
                        .startTimeMillis(evStart)
                        .endTimeMillis(evStart)
                        .status("success")
                        .metadata(wrapNode("event", ev))
                        .depth(1)
                        .tags(Collections.singletonList(SOURCE_TYPE))
                        .build());
            }
            return spans;
        }

        String spanId = traceId + "-root";
        spans.add(Span.builder()
                .id(spanId)
                .traceId(traceId)
                .parentSpanId(null)
                .spanKind(TracingConstants.SpanKind.WORKFLOW)
                .name(resolveDisplayName(root))
                .startTimeMillis(resolveStartMillis(root))
                .endTimeMillis(resolveEndMillis(root, resolveStartMillis(root)))
                .status(mapStatus(root.path("status").asText("unknown")))
                .metadata(Collections.singletonMap("source", SOURCE_TYPE))
                .input(Collections.singletonMap("data", root.toString()))
                .depth(0)
                .tags(Collections.singletonList(SOURCE_TYPE))
                .build());
        return spans;
    }

    private static String spanKindForEvent(String eventName) {
        if (eventName == null) {
            return TracingConstants.SpanKind.TASK;
        }
        String lower = eventName.toLowerCase();
        if (lower.contains("tool")) {
            return TracingConstants.SpanKind.TOOL;
        }
        if (lower.contains("llm") || lower.contains("model") || lower.contains("chat")) {
            return TracingConstants.SpanKind.LLM;
        }
        return TracingConstants.SpanKind.TASK;
    }

    private static Map<String, Object> wrapNode(String key, JsonNode node) {
        Map<String, Object> m = new HashMap<>();
        m.put(key, node.toString());
        return m;
    }

    private static Map<String, Object> buildTraceMetadata(JsonNode root, String traceId) {
        Map<String, Object> m = new HashMap<>();
        m.put("sourceType", SOURCE_TYPE);
        m.put("traceSummaryId", traceId);
        Iterator<String> it = root.fieldNames();
        int n = 0;
        while (it.hasNext() && n++ < 20) {
            String f = it.next();
            JsonNode v = root.get(f);
            if (v != null && (v.isTextual() || v.isNumber() || v.isBoolean())) {
                m.put(f, v.asText());
            }
        }
        return m;
    }

    private static String resolveTraceId(JsonNode root) {
        if (root.hasNonNull("traceSummaryId")) {
            return asIdText(root.get("traceSummaryId"));
        }
        if (root.hasNonNull("id")) {
            return asIdText(root.get("id"));
        }
        if (root.hasNonNull("_id")) {
            return asIdText(root.get("_id"));
        }
        return null;
    }

    private static String asIdText(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isNumber()) {
            return n.asText();
        }
        return n.asText();
    }

    private static String resolveDisplayName(JsonNode root) {
        for (String k : new String[]{"name", "title", "summary", "traceName"}) {
            if (root.hasNonNull(k)) {
                return root.get(k).asText();
            }
        }
        return "ClickUp trace";
    }

    private static long resolveStartMillis(JsonNode root) {
        long t = parseTimeField(root, "startedAt", "createdAt", "dateCreated", "created", "startTime", "timestamp");
        return t > 0 ? t : System.currentTimeMillis();
    }

    private static long resolveEndMillis(JsonNode root, long startFallback) {
        long t = parseTimeField(root, "stoppedAt", "updatedAt", "dateUpdated", "endTime");
        return t > 0 ? t : startFallback;
    }

    private static long parseTimeField(JsonNode node, String... fieldNames) {
        for (String f : fieldNames) {
            if (!node.has(f) || node.get(f).isNull()) {
                continue;
            }
            JsonNode v = node.get(f);
            if (v.isNumber()) {
                long n = v.asLong();
                return n < 1_000_000_000_000L ? n * 1000L : n;
            }
            if (v.isTextual()) {
                try {
                    return java.time.Instant.parse(v.asText()).toEpochMilli();
                } catch (Exception ignored) {
                    try {
                        return Long.parseLong(v.asText());
                    } catch (Exception ignored2) {
                        // continue
                    }
                }
            }
        }
        return 0L;
    }

    private static String mapStatus(String raw) {
        if (raw == null) {
            return "unknown";
        }
        String s = raw.toLowerCase();
        if (s.contains("success") || s.contains("complete") || s.contains("done")) {
            return "success";
        }
        if (s.contains("fail") || s.contains("error")) {
            return "error";
        }
        if (s.contains("run")) {
            return "running";
        }
        return "unknown";
    }
}

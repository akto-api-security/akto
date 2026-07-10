package com.akto.jobs.executors.clickup;

import com.akto.dto.ApiCollection;
import com.akto.dto.tracing.constants.TracingConstants;
import com.akto.dto.tracing.model.Span;
import com.akto.dto.tracing.model.Trace;
import com.akto.log.LoggerMaker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ClickupTraceParser {

    private static final LoggerMaker logger = new LoggerMaker(ClickupTraceParser.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String SOURCE_TYPE = "clickup";

    public ParseResult parse(JsonNode responseNode, int apiCollectionId) {
        if (responseNode == null || !responseNode.has("data") || !responseNode.get("data").isArray()) {
            return new ParseResult(Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), 0L);
        }

        List<Trace> traces = new ArrayList<>();
        List<Span> spans = new ArrayList<>();
        Map<String, ApiCollection.ServiceGraphEdgeInfo> edges = new LinkedHashMap<>();
        long maxSeenTimestamp = 0L;

        for (JsonNode traceNode : responseNode.get("data")) {
            try {
                TraceWithSpans parsed = parseSingleTrace(traceNode, apiCollectionId, edges);
                if (parsed == null) {
                    continue;
                }

                traces.add(parsed.trace);
                spans.addAll(parsed.spans);
                maxSeenTimestamp = Math.max(maxSeenTimestamp, parsed.maxTimestamp);
            } catch (Exception e) {
                logger.errorAndAddToDb("Failed to parse ClickUp trace summary: " + e.getMessage());
            }
        }

        return new ParseResult(traces, spans, edges, maxSeenTimestamp);
    }

    private TraceWithSpans parseSingleTrace(
        JsonNode traceNode,
        int apiCollectionId,
        Map<String, ApiCollection.ServiceGraphEdgeInfo> edges
    ) {
        String traceSummaryId = textOrNull(traceNode, "traceSummaryId");
        if (traceSummaryId == null || traceSummaryId.isEmpty()) {
            return null;
        }

        JsonNode eventsNode = traceNode.path("events");
        List<Span> parsedSpans = new ArrayList<>();
        List<String> spanIds = new ArrayList<>();
        long startTimeMs = parseTimestamp(textOrNull(traceNode, "startTime"), traceNode.path("startTimestamp").asLong(0));
        long endTimeMs = parseTimestamp(textOrNull(traceNode, "endTime"), traceNode.path("endTimestamp").asLong(0));
        long maxSeenTimestamp = Math.max(startTimeMs, endTimeMs);

        String previousSpanId = null;
        String previousNodeName = null;
        String rootSpanId = null;
        int depth = 0;

        if (eventsNode.isArray()) {
            for (int i = 0; i < eventsNode.size(); i++) {
                JsonNode eventNode = eventsNode.get(i);
                String spanId = textOrNull(eventNode, "auditEventId");
                if (spanId == null || spanId.isEmpty()) {
                    spanId = traceSummaryId + "-event-" + i;
                }

                long eventStartTime = parseTimestamp(textOrNull(eventNode, "startTime"), 0);
                long eventEndTime = parseTimestamp(textOrNull(eventNode, "endTime"), eventStartTime);
                maxSeenTimestamp = Math.max(maxSeenTimestamp, Math.max(eventStartTime, eventEndTime));
                startTimeMs = startTimeMs == 0 ? eventStartTime : Math.min(startTimeMs, eventStartTime);
                endTimeMs = Math.max(endTimeMs, eventEndTime);

                String nodeName = deriveSpanName(eventNode);
                Span span = Span.builder()
                    .id(spanId)
                    .traceId(traceSummaryId)
                    .parentSpanId(previousSpanId)
                    .spanKind(determineSpanKind(eventNode))
                    .name(nodeName)
                    .startTimeMillis(eventStartTime)
                    .endTimeMillis(eventEndTime)
                    .status(mapStatus(textOrNull(eventNode, "eventStatus")))
                    .input(extractInput(eventNode))
                    .output(extractOutput(eventNode))
                    .metadata(extractSpanMetadata(eventNode))
                    .depth(depth++)
                    .tags(arraysList(SOURCE_TYPE, textOrNull(eventNode, "eventType"), textOrNull(eventNode, "appName")))
                    .build();

                if (rootSpanId == null) {
                    rootSpanId = spanId;
                }

                parsedSpans.add(span);
                spanIds.add(spanId);

                if (previousNodeName != null) {
                    Map<String, Object> edgeMetadata = new HashMap<>();
                    edgeMetadata.put("type", textOrNull(eventNode, "eventType"));
                    edgeMetadata.put("status", textOrNull(eventNode, "eventStatus"));
                    String tool = extractToolName(eventNode);
                    if (tool != null) {
                        edgeMetadata.put("tool", tool);
                    }
                    String edgeKey = traceSummaryId + ":" + spanId;
                    edges.put(edgeKey, new ApiCollection.ServiceGraphEdgeInfo(previousNodeName, nodeName, edgeMetadata));
                }

                previousSpanId = spanId;
                previousNodeName = nodeName;
            }
        }

        Trace trace = Trace.builder()
            .id(traceSummaryId)
            .rootSpanId(rootSpanId)
            .aiAgentName(
                firstNonEmpty(
                    textOrNull(traceNode, "automationName"),
                    textOrNull(traceNode, "agentOwner"),
                    textOrNull(traceNode, "agentManager"),
                    "ClickUp Agent"
                )
            )
            .name(
                firstNonEmpty(
                    textOrNull(traceNode, "title"),
                    textOrNull(traceNode, "taskName"),
                    textOrNull(traceNode, "automationName"),
                    "ClickUp Trace"
                )
            )
            .startTimeMillis(startTimeMs)
            .endTimeMillis(endTimeMs)
            .status(mapStatus(textOrNull(traceNode, "traceStatus")))
            .totalSpans(parsedSpans.size())
            .totalTokens(0)
            .totalInputTokens(0)
            .totalOutputTokens(0)
            .rootInput(parsedSpans.isEmpty() ? Collections.emptyMap() : parsedSpans.get(0).getInput())
            .rootOutput(parsedSpans.isEmpty() ? Collections.emptyMap() : parsedSpans.get(parsedSpans.size() - 1).getOutput())
            .metadata(extractTraceMetadata(traceNode))
            .spanIds(spanIds)
            .apiCollectionId(apiCollectionId)
            .build();

        return new TraceWithSpans(trace, parsedSpans, maxSeenTimestamp);
    }

    private String determineSpanKind(JsonNode eventNode) {
        String eventType = valueToLower(textOrNull(eventNode, "eventType"));
        String tool = valueToLower(extractToolName(eventNode));
        if (tool != null && !tool.isEmpty()) {
            return TracingConstants.SpanKind.TOOL;
        }
        if ("actioninprogress".equals(eventType)) {
            return TracingConstants.SpanKind.AGENT;
        }
        if ("actionfailed".equals(eventType) || "actionwarn".equals(eventType)) {
            return TracingConstants.SpanKind.TASK;
        }
        return TracingConstants.SpanKind.WORKFLOW;
    }

    private String deriveSpanName(JsonNode eventNode) {
        String tool = extractToolName(eventNode);
        if (tool != null && !tool.isEmpty()) {
            return tool;
        }

        JsonNode actionNode = eventNode.path("eventData").path("action");
        String actionTitle = textOrNull(actionNode, "title");
        if (actionTitle != null && !actionTitle.isEmpty()) {
            return actionTitle;
        }

        return firstNonEmpty(textOrNull(eventNode, "eventType"), "clickup-event");
    }

    private String extractToolName(JsonNode eventNode) {
        return textOrNull(eventNode.path("eventData").path("ai"), "tool");
    }

    private Map<String, Object> extractInput(JsonNode eventNode) {
        JsonNode triggerNode = eventNode.path("eventData").path("trigger");
        if (triggerNode.isMissingNode() || triggerNode.isNull()) {
            return Collections.emptyMap();
        }
        return nodeToMap(triggerNode);
    }

    private Map<String, Object> extractOutput(JsonNode eventNode) {
        JsonNode aiNode = eventNode.path("eventData").path("ai");
        if (aiNode.isMissingNode() || aiNode.isNull()) {
            return Collections.emptyMap();
        }
        return nodeToMap(aiNode);
    }

    private Map<String, Object> extractSpanMetadata(JsonNode eventNode) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sourceType", SOURCE_TYPE);
        metadata.put("eventType", textOrNull(eventNode, "eventType"));
        metadata.put("eventStatus", textOrNull(eventNode, "eventStatus"));
        metadata.put("appName", textOrNull(eventNode, "appName"));
        metadata.put("locationName", textOrNull(eventNode, "locationName"));

        JsonNode eventDataNode = eventNode.path("eventData");
        if (!eventDataNode.isMissingNode() && !eventDataNode.isNull()) {
            metadata.put("eventData", nodeToMap(eventDataNode));
        }

        return metadata;
    }

    private Map<String, Object> extractTraceMetadata(JsonNode traceNode) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sourceType", SOURCE_TYPE);
        metadata.put("workspaceId", textOrNull(traceNode, "workspaceId"));
        metadata.put("traceId", textOrNull(traceNode, "traceId"));
        metadata.put("taskId", textOrNull(traceNode, "taskId"));
        metadata.put("taskName", textOrNull(traceNode, "taskName"));
        metadata.put("automationId", textOrNull(traceNode, "automationId"));
        metadata.put("automationName", textOrNull(traceNode, "automationName"));
        metadata.put("locationId", textOrNull(traceNode, "locationId"));
        metadata.put("locationName", textOrNull(traceNode, "locationName"));
        metadata.put("agentViewId", textOrNull(traceNode, "agentViewId"));
        return metadata;
    }

    private String mapStatus(String status) {
        if (status == null) {
            return "unknown";
        }
        String value = status.toLowerCase();
        if (value.contains("success") || value.contains("succeed")) return "success";
        if (value.contains("fail")) return "error";
        if (value.contains("skip")) return "skipped";
        if (value.contains("start") || value.contains("progress")) return "running";
        if (value.contains("warn")) return "warning";
        return value;
    }

    private Map<String, Object> nodeToMap(JsonNode node) {
        return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {});
    }

    private long parseTimestamp(String isoTimestamp, long fallback) {
        if (isoTimestamp == null || isoTimestamp.isEmpty()) {
            return fallback;
        }
        try {
            return Instant.parse(isoTimestamp).toEpochMilli();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String textOrNull(JsonNode node, String key) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode child = node.get(key);
        if (child == null || child.isMissingNode() || child.isNull()) {
            return null;
        }
        return child.asText();
    }

    private String valueToLower(String value) {
        return value == null ? null : value.toLowerCase();
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private List<String> arraysList(String... values) {
        List<String> tags = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                tags.add(value);
            }
        }
        return tags;
    }

    private static class TraceWithSpans {
        private final Trace trace;
        private final List<Span> spans;
        private final long maxTimestamp;

        private TraceWithSpans(Trace trace, List<Span> spans, long maxTimestamp) {
            this.trace = trace;
            this.spans = spans;
            this.maxTimestamp = maxTimestamp;
        }
    }

    public static class ParseResult {
        private final List<Trace> traces;
        private final List<Span> spans;
        private final Map<String, ApiCollection.ServiceGraphEdgeInfo> serviceGraphEdges;
        private final long maxSeenTimestampMs;

        public ParseResult(
            List<Trace> traces,
            List<Span> spans,
            Map<String, ApiCollection.ServiceGraphEdgeInfo> serviceGraphEdges,
            long maxSeenTimestampMs
        ) {
            this.traces = traces;
            this.spans = spans;
            this.serviceGraphEdges = serviceGraphEdges;
            this.maxSeenTimestampMs = maxSeenTimestampMs;
        }

        public List<Trace> getTraces() {
            return traces;
        }

        public List<Span> getSpans() {
            return spans;
        }

        public Map<String, ApiCollection.ServiceGraphEdgeInfo> getServiceGraphEdges() {
            return serviceGraphEdges;
        }

        public long getMaxSeenTimestampMs() {
            return maxSeenTimestampMs;
        }
    }
}

package com.akto.jobs.executors.clickup;

import com.akto.dto.tracing.model.Span;
import com.akto.dto.tracing.model.Trace;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClickupTraceParserTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testParsesClickupTraceSummaryPayload() throws Exception {
        String payload = "{\n" +
            "  \"data\": [\n" +
            "    {\n" +
            "      \"traceSummaryId\": \"summary-1\",\n" +
            "      \"workspaceId\": \"10527597\",\n" +
            "      \"traceStatus\": \"SKIPPED\",\n" +
            "      \"startTime\": \"2026-04-26T23:40:51.904Z\",\n" +
            "      \"endTime\": \"2026-04-26T23:42:39.854Z\",\n" +
            "      \"automationName\": \"ClickUp Super Agent\",\n" +
            "      \"taskName\": \"Migrate Homepage to DAP\",\n" +
            "      \"events\": [\n" +
            "        {\n" +
            "          \"auditEventId\": \"ev-1\",\n" +
            "          \"eventType\": \"actionInProgress\",\n" +
            "          \"eventStatus\": \"started\",\n" +
            "          \"startTime\": \"2026-04-26T23:40:51.904Z\",\n" +
            "          \"endTime\": \"2026-04-26T23:40:51.904Z\",\n" +
            "          \"eventData\": {\n" +
            "            \"action\": {\"title\": \"Launch AI Agent\"},\n" +
            "            \"trigger\": {\"title\": \"automation trigger\"},\n" +
            "            \"ai\": {\"sdTraceId\": \"123\"}\n" +
            "          }\n" +
            "        },\n" +
            "        {\n" +
            "          \"auditEventId\": \"ev-2\",\n" +
            "          \"eventType\": \"actionSucceded\",\n" +
            "          \"eventStatus\": \"success\",\n" +
            "          \"startTime\": \"2026-04-26T23:41:06.422Z\",\n" +
            "          \"endTime\": \"2026-04-26T23:41:06.422Z\",\n" +
            "          \"eventData\": {\n" +
            "            \"action\": {\"title\": \"Launch AI Agent\"},\n" +
            "            \"trigger\": {\"title\": \"automation trigger\"},\n" +
            "            \"ai\": {\"tool\": \"todo_write\", \"sdTraceId\": \"123\"}\n" +
            "          }\n" +
            "        }\n" +
            "      ]\n" +
            "    }\n" +
            "  ]\n" +
            "}";

        JsonNode node = mapper.readTree(payload);
        ClickupTraceParser parser = new ClickupTraceParser();
        ClickupTraceParser.ParseResult result = parser.parse(node, 77);

        List<Trace> traces = result.getTraces();
        List<Span> spans = result.getSpans();
        assertEquals(1, traces.size());
        assertEquals(2, spans.size());
        assertFalse(result.getServiceGraphEdges().isEmpty());

        Trace trace = traces.get(0);
        assertEquals("summary-1", trace.getId());
        assertEquals(77, trace.getApiCollectionId());
        assertEquals(2, trace.getTotalSpans());
        assertEquals("skipped", trace.getStatus());

        Span firstSpan = spans.get(0);
        Span secondSpan = spans.get(1);
        assertEquals("ev-1", firstSpan.getId());
        assertEquals("summary-1", firstSpan.getTraceId());
        assertEquals("agent", firstSpan.getSpanKind());
        assertNull(firstSpan.getParentSpanId());
        assertEquals("ev-1", secondSpan.getParentSpanId());
        assertEquals("tool", secondSpan.getSpanKind());
        assertEquals("todo_write", secondSpan.getName());
        assertTrue(result.getMaxSeenTimestampMs() > 0);
    }

    @Test
    public void testInvalidPayloadReturnsEmptyResult() throws Exception {
        JsonNode invalidNode = mapper.readTree("{\"meta\": {\"page\": 1}}");
        ClickupTraceParser.ParseResult result = new ClickupTraceParser().parse(invalidNode, 88);

        assertTrue(result.getTraces().isEmpty());
        assertTrue(result.getSpans().isEmpty());
        assertTrue(result.getServiceGraphEdges().isEmpty());
        assertEquals(0L, result.getMaxSeenTimestampMs());
    }
}

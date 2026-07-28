package com.akto.tracing.browser_extension;

import com.akto.dto.ApiCollection.ServiceGraphEdgeInfo;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.tracing.Span;
import com.akto.dto.tracing.Trace;
import com.akto.tracing.TraceParseResult;
import com.akto.tracing.TraceParser;
import com.akto.util.Constants;
import com.akto.util.HttpRequestResponseUtils;
import com.akto.util.JSONUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds a single-span trace per turn captured by the Akto browser extension. */
public class BrowserExtensionTraceParser implements TraceParser {

    private static final BrowserExtensionTraceParser INSTANCE = new BrowserExtensionTraceParser();
    private static final String SOURCE_TYPE = "browser-extension";

    // Set by the browser extension per captured turn; same keys AgentQueryRecord reads for ES.
    private static final String MESSAGE_ID_HEADER = "x-akto-installer-akto_message_id";
    private static final String SESSION_ID_HEADER = "x-akto-installer-akto_session_id";

    public static BrowserExtensionTraceParser getInstance() { return INSTANCE; }

    @Override
    public boolean canParse(Object input) {
        if (!(input instanceof HttpResponseParams)) return false;
        HttpResponseParams p = (HttpResponseParams) input;
        if (p.getRequestParams() == null) return false;
        Map<String, Object> tagsMap = JSONUtils.getMap(p.getTags());
        if (tagsMap == null || !tagsMap.containsKey(Constants.AKTO_BROWSER_LLM_TAG)) return false;
        // Older extension builds send no trace id — nothing to build.
        String traceId = HttpRequestResponseUtils.getHeaderValue(p.getRequestParams().getHeaders(), MESSAGE_ID_HEADER);
        if (traceId == null || traceId.isEmpty()) return false;
        // A guardrail block has no answer and reuses the turn's trace id, so it has nothing to
        // show in a waterfall and would collide with the real turn's trace.
        Object assistantResponse = payloadToMap(p.getPayload()).get("assistantResponse");
        return assistantResponse instanceof String && !((String) assistantResponse).isEmpty();
    }

    @Override
    public TraceParseResult parse(Object input) throws Exception {
        HttpResponseParams p = (HttpResponseParams) input;
        Map<String, List<String>> headers = p.getRequestParams().getHeaders();

        String traceId = HttpRequestResponseUtils.getHeaderValue(headers, MESSAGE_ID_HEADER);
        String sessionId = HttpRequestResponseUtils.getHeaderValue(headers, SESSION_ID_HEADER);
        String host = HttpRequestResponseUtils.getHeaderValue(headers, "host");
        String vendor = extractVendor(host);

        // One span per turn, so the id is the trace id — a replay overwrites nothing.
        long timeMillis = p.getTime() * 1000L;
        String spanId = traceId;
        String status = p.getStatusCode() >= 400 ? "ERROR" : "OK";

        Map<String, Object> spanInput = payloadToMap(p.getRequestParams().getPayload());
        Map<String, Object> spanOutput = payloadToMap(p.getPayload());
        // Extension-measured latency, so the waterfall bar has a real width.
        long endTimeMillis = timeMillis + durationMs(spanOutput);
        Map<String, Object> metadata = new HashMap<>();
        if (sessionId != null) metadata.put("akto_session_id", sessionId);
        if (host != null) metadata.put("host", host);

        Span span = Span.builder()
                .id(spanId)
                .traceId(traceId)
                .spanKind("llm")
                .name(vendor)
                .startTimeMillis(timeMillis)
                .endTimeMillis(endTimeMillis)
                .status(status)
                .input(spanInput)
                .output(spanOutput)
                .metadata(metadata)
                .depth(0)
                .build();

        Trace trace = Trace.builder()
                .id(traceId)
                .rootSpanId(spanId)
                .aiAgentName(vendor)
                .name(vendor)
                .startTimeMillis(timeMillis)
                .endTimeMillis(endTimeMillis)
                .status(status)
                .totalSpans(1)
                .apiCollectionId(p.getRequestParams().getApiCollectionId())
                .rootInput(spanInput)
                .rootOutput(spanOutput)
                .metadata(metadata)
                .spanIds(Collections.singletonList(spanId))
                .build();

        return TraceParseResult.builder()
                .trace(trace)
                .spans(Collections.singletonList(span))
                .metadata(metadata)
                .sourceIdentifier(sessionId)
                .build();
    }

    /** One span per turn, so there are no service-to-service edges to report. */
    @Override
    public Map<String, ServiceGraphEdgeInfo> extractServiceGraph(Object input) throws Exception {
        return Collections.emptyMap();
    }

    @Override
    public String getSourceType() { return SOURCE_TYPE; }

    /** Latency the extension measured, 0 when it reported none. */
    private long durationMs(Map<String, Object> output) {
        Object value = output.get("durationMs");
        return value instanceof Number && ((Number) value).longValue() > 0 ? ((Number) value).longValue() : 0L;
    }

    /** Host id is <user>.<browser>.<vendor host> — the vendor is what's worth showing. */
    private String extractVendor(String host) {
        if (host == null) return null;
        String[] parts = host.split("\\.", 3);
        return parts.length == 3 ? parts[2] : host;
    }

    /** Payloads are already {"userInput": ...} / {"assistantResponse": ...}; keep raw text if not JSON. */
    private Map<String, Object> payloadToMap(String payload) {
        if (payload == null || payload.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, Object> parsed = JSONUtils.getMap(payload);
        if (parsed != null) {
            return parsed;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("body", payload);
        return map;
    }
}

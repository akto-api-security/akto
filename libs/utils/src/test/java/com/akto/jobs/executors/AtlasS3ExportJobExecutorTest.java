package com.akto.jobs.executors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

public class AtlasS3ExportJobExecutorTest {

    private static JSONObject doc(String serviceId, String traceId, String spanId, Boolean guardrailViolated) throws JSONException {
        JSONObject d = new JSONObject()
            .put("accountId", 1000)
            .put("serviceId", serviceId)
            .put("deviceId", "device-1")
            .put("userName", "alice@example.com")
            .put("sessionIdentifier", "sess-1")
            .put("queryPayload", "{\"model\":\"claude-sonnet\",\"messages\":[]}")
            .put("responsePayload", "{\"content\":\"hi\"}")
            .put("timestamp", 1700000000123L)
            .put("inputTokens", 12)
            .put("outputTokens", 34)
            .put("traceId", traceId)
            .put("spanId", spanId)
            .put("isAtlasTraffic", true);
        if (guardrailViolated != null) {
            d.put("guardrailViolated", guardrailViolated).put("guardrailAction", "block").put("guardrailPolicy", "PII");
        }
        return d;
    }

    private static Map<String, JSONObject> attrs(JSONObject span) throws JSONException {
        Map<String, JSONObject> m = new HashMap<>();
        JSONArray a = span.getJSONArray("attributes");
        for (int i = 0; i < a.length(); i++) m.put(a.getJSONObject(i).getString("key"), a.getJSONObject(i).getJSONObject("value"));
        return m;
    }

    private static JSONArray spansOf(JSONObject resourceSpan) throws JSONException {
        return resourceSpan.getJSONArray("scopeSpans").getJSONObject(0).getJSONArray("spans");
    }

    @Test
    public void mapsDocsToOtlpSpansGroupedByService() throws JSONException {
        JSONArray docs = new JSONArray()
            .put(doc("claudecli", "trace-a", "span_x", true))
            .put(doc("cursor", "0123456789abcdef0123456789abcdef", "0123456789abcdef", null))
            .put(doc("claudecli", "trace-a", "span_y", false));
        JSONArray resourceSpans = AtlasS3ExportJobExecutor.toOtlpJson(1000, docs).getJSONArray("resourceSpans");
        assertEquals(2, resourceSpans.length());

        JSONObject claude = resourceSpans.getJSONObject(0);
        assertEquals("claudecli", claude.getJSONObject("resource").getJSONArray("attributes")
            .getJSONObject(0).getJSONObject("value").getString("stringValue"));
        JSONArray claudeSpans = spansOf(claude);
        assertEquals(2, claudeSpans.length());

        JSONObject span = claudeSpans.getJSONObject(0);
        assertEquals(32, span.getString("traceId").length());
        assertEquals(16, span.getString("spanId").length());
        assertEquals(claudeSpans.getJSONObject(1).getString("traceId"), span.getString("traceId"));
        assertEquals("chat claude-sonnet", span.getString("name"));
        assertEquals("1700000000123000000", span.getString("startTimeUnixNano"));

        Map<String, JSONObject> a = attrs(span);
        assertEquals("claude-sonnet", a.get("gen_ai.request.model").getString("stringValue"));
        assertEquals("12", a.get("gen_ai.usage.input_tokens").getString("intValue"));
        assertEquals("34", a.get("gen_ai.usage.output_tokens").getString("intValue"));
        assertEquals("alice@example.com", a.get("user.name").getString("stringValue"));
        assertTrue(a.get("akto.guardrail.violated").getBoolean("boolValue"));
        assertEquals("PII", a.get("akto.guardrail.policy").getString("stringValue"));
        assertTrue(a.containsKey("akto.request.payload"));
        assertTrue(a.containsKey("akto.response.payload"));

        // Valid hex ids pass through; no guardrail attrs when guardrails did not run.
        JSONObject cursorSpan = spansOf(resourceSpans.getJSONObject(1)).getJSONObject(0);
        assertEquals("0123456789abcdef0123456789abcdef", cursorSpan.getString("traceId"));
        assertEquals("0123456789abcdef", cursorSpan.getString("spanId"));
        assertFalse(attrs(cursorSpan).containsKey("akto.guardrail.violated"));
    }

    @Test
    public void buildsDeterministicCollectorStyleKeys() {
        // 2023-11-14T22:13:20Z
        long windowStart = 1700000000000L;
        assertEquals("akto/atlas/year=2023/month=11/day=14/hour=22/minute=13/traces_1700000000000_00002.json.gz",
            AtlasS3ExportJobExecutor.buildKey("akto/atlas", windowStart, 2));
        assertEquals("year=2023/month=11/day=14/hour=22/minute=13/traces_1700000000000_00000.json.gz",
            AtlasS3ExportJobExecutor.buildKey(null, windowStart, 0));
    }

    @Test
    public void hashesNonHexIds() {
        assertEquals(AtlasS3ExportJobExecutor.toHexId("span_abc", 16), AtlasS3ExportJobExecutor.toHexId("span_abc", 16));
        assertTrue(AtlasS3ExportJobExecutor.toHexId("span_abc", 16).matches("^[0-9a-f]{16}$"));
        assertTrue(AtlasS3ExportJobExecutor.toHexId(null, 32).matches("^[0-9a-f]{32}$"));
        assertFalse(AtlasS3ExportJobExecutor.toHexId("00000000000000000000000000000000", 32).matches("^0+$"));
    }
}

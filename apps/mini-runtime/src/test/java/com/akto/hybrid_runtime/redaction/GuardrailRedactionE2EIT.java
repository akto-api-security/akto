package com.akto.hybrid_runtime.redaction;

import static org.junit.jupiter.api.Assertions.*;

import com.akto.hybrid_runtime.redaction.RedactionResult.RedactionStatus;
import com.akto.utils.RedactSampleData;
import com.akto.utils.elasticsearch.AgentQueryRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Drives the real client, sample redactor and agent queue over real HTTP against a running
 * guardrails-service. Skipped unless AKTO_GUARDRAIL_REDACTION_E2E_URL is set, so it never runs in a
 * normal build. The service needs an Anonymize scanner that masks the name "Jane Doe" (the
 * throwaway harness used to verify this feature does).
 */
@EnabledIfEnvironmentVariable(named = "AKTO_GUARDRAIL_REDACTION_E2E_URL", matches = ".+")
public class GuardrailRedactionE2EIT {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String SENSITIVE = "{\"msg\":\"mail jane@example.com, key AKIAIOSFODNN7EXAMPLE, ask Jane Doe\"}";

    private static RedactionConfig config(String url) {
        return new RedactionConfig(true, url, 1000, 8000, 20000, 100, 1 << 20, 5, 30000, 10, 0, false);
    }

    private static GuardrailRedactionClient liveClient() {
        return GuardrailRedactionClient.fromEnv(config(System.getenv("AKTO_GUARDRAIL_REDACTION_E2E_URL")));
    }

    private static void assertMasked(String text) {
        assertFalse(text.contains("jane@example.com"), text);
        assertFalse(text.contains("AKIAIOSFODNN7EXAMPLE"), text);
        assertFalse(text.contains("Jane Doe"), text);
    }

    @Test
    public void clientGetsFullyMaskedPayloadsAndCleanOnesComeBackUnchanged() throws Exception {
        Map<String, RedactionResult> results = liveClient().redact(Arrays.asList(
                new RedactionItem("1", "POST", "api.example.com", "/v1/chat", SENSITIVE, "{\"ok\":true}"),
                new RedactionItem("2", "GET", "api.example.com", "/v1/ping", "{\"a\":\"nothing sensitive\"}", null)),
                System.currentTimeMillis() + 20_000);

        RedactionResult masked = results.get("1");
        assertEquals(RedactionStatus.REDACTED, masked.getRequest().getStatus());
        assertMasked(masked.getRequest().getPayload());
        mapper.readTree(masked.getRequest().getPayload()); // still valid JSON
        assertNull(masked.getRequest().marker());
        assertEquals(RedactionStatus.UNCHANGED, masked.getResponse().getStatus());
        assertEquals(RedactionStatus.UNCHANGED, results.get("2").getRequest().getStatus());
        assertNull(results.get("2").getResponse());
    }

    @Test
    public void storedSampleSurvivesTheExistingPipelineMaskedAndUnmarked() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode orig = mapper.createObjectNode();
        orig.put("method", "POST");
        orig.put("path", "/v1/chat");
        orig.put("type", "HTTP/1.1");
        orig.put("requestHeaders", "{\"host\":\"api.example.com\",\"content-type\":\"application/json\"}");
        orig.put("responseHeaders", "{\"content-type\":\"application/json\"}");
        orig.put("requestPayload", SENSITIVE);
        orig.put("responsePayload", "{\"ok\":true}");
        orig.put("statusCode", "200");
        orig.put("akto_vxlan_id", 123);
        String message = mapper.writeValueAsString(orig);

        OrigRedactor redactor = new OrigRedactor(liveClient(), 20_000, false);
        redactor.prepare(Collections.singletonList(message));
        OrigRedactor.Result result = redactor.lookup(message);

        assertNull(result.marker);
        String stored = RedactSampleData.redactIfRequired(result.orig, false, false);
        assertMasked(stored);
        JsonNode node = mapper.readTree(stored);
        assertNull(node.get(OrigRedactor.MARKER_KEY));
        assertTrue(node.get("akto_vxlan_id").isInt());
    }

    @Test
    public void agentRecordIsStoredMaskedWithTokenCountsOfTheOriginal() throws Exception {
        RedactionConfig config = config(System.getenv("AKTO_GUARDRAIL_REDACTION_E2E_URL"));
        AgentRecordRedactionQueue queue = new AgentRecordRedactionQueue(GuardrailRedactionClient.fromEnv(config), config);
        BlockingQueue<AgentQueryRecord> sink = new LinkedBlockingQueue<>();

        queue.submit(new AgentQueryRecord("doc", 1000, "svc", "dev", "user", "sess", SENSITIVE, "{\"ok\":true}",
                1700000000000L, 11, 22, "trace", "span", true), "POST", "api.example.com", "/v1/chat", sink::add);

        AgentQueryRecord stored = sink.poll(15, TimeUnit.SECONDS);
        assertNotNull(stored);
        assertMasked(stored.getQueryPayload());
        assertNull(stored.getPayloadRedaction());
        assertEquals(11, stored.getInputTokens());
    }

    @Test
    public void aServiceThatIsNotThereMeansUnavailableNotUnchanged() {
        Map<String, RedactionResult> results = GuardrailRedactionClient.fromEnv(config("http://127.0.0.1:1")).redact(
                Collections.singletonList(new RedactionItem("1", "POST", "h", "/p", SENSITIVE, null)),
                System.currentTimeMillis() + 20_000);

        assertEquals(RedactionStatus.UNAVAILABLE, results.get("1").getRequest().getStatus());
        assertEquals("UNAVAILABLE", results.get("1").getRequest().marker());
        assertNull(results.get("1").getRequest().getPayload());
    }
}

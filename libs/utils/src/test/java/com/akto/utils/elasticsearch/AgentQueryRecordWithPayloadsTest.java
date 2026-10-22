package com.akto.utils.elasticsearch;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public class AgentQueryRecordWithPayloadsTest {

    private static AgentQueryRecord record() {
        return new AgentQueryRecord("doc-1", 1000, "svc", "dev", "user", "sess",
                "raw query", "raw response", 1700000000000L, 11, 22, "trace", "span", true);
    }

    @Test
    public void swapsPayloadsAndKeepsEverythingElse() throws Exception {
        AgentQueryRecord original = record();
        Method setVerdict = AgentQueryRecord.class.getDeclaredMethod("setGuardrailVerdict", String.class);
        setVerdict.setAccessible(true);
        setVerdict.invoke(original, "{\"guardrailViolated\":true,\"guardrailAction\":\"block\","
                + "\"guardrailPolicy\":\"p\",\"guardrailRule\":\"r\",\"guardrailReason\":\"why\","
                + "\"guardrailSeverity\":\"HIGH\"}");

        AgentQueryRecord copy = original.withPayloads("masked query", "masked response", null);

        assertEquals("masked query", copy.getQueryPayload());
        assertEquals("masked response", copy.getResponsePayload());
        assertEquals(11, copy.getInputTokens());
        assertEquals(22, copy.getOutputTokens());
        assertEquals("doc-1", copy.getDocId());
        assertEquals("span", copy.getSpanId());
        assertTrue(copy.getIsAtlasTraffic());
        assertEquals(Boolean.TRUE, copy.getGuardrailViolated());
        assertEquals("block", copy.getGuardrailAction());
        assertEquals("HIGH", copy.getGuardrailSeverity());
        assertNull(copy.getPayloadRedaction());
        // The original is untouched.
        assertEquals("raw query", original.getQueryPayload());
    }

    @Test
    public void nullPayloadKeepsCurrentAndMarkerIsCarried() {
        AgentQueryRecord copy = record().withPayloads(null, null, "UNAVAILABLE");
        assertEquals("raw query", copy.getQueryPayload());
        assertEquals("raw response", copy.getResponsePayload());
        assertEquals("UNAVAILABLE", copy.getPayloadRedaction());
    }

    @Test
    public void markerIsOmittedFromJsonWhenAbsent() {
        Gson gson = new Gson();
        assertFalse(gson.toJson(record()).contains("payloadRedaction"));
        assertTrue(gson.toJson(record().withPayloads(null, null, "UNAVAILABLE"))
                .contains("\"payloadRedaction\":\"UNAVAILABLE\""));
    }
}

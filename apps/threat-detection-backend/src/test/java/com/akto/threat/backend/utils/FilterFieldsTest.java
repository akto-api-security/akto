package com.akto.threat.backend.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.akto.dto.threat_detection_backend.MaliciousEventDto;
import com.akto.dto.type.URLMethods;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

public class FilterFieldsTest {

    private static JsonNode fixture() throws Exception {
        try (InputStream in = FilterFieldsTest.class.getResourceAsStream("/filter_fields_fixture.json")) {
            return new ObjectMapper().readTree(in);
        }
    }

    @Test
    public void matchesTheJsClassifiersForEveryFixtureUrl() throws Exception {
        for (JsonNode c : fixture().get("cases")) {
            String url = c.get("url").asText();
            String method = c.get("method").asText();
            assertEquals(c.get("agenticType").asText(), FilterFields.agenticType(url), "agenticType " + url);
            assertEquals(c.get("endpointKind").asText(), FilterFields.endpointKind(url, method), "endpointKind " + url);
        }
    }

    @Test
    public void behaviourMatchesTheJsParser() throws Exception {
        for (JsonNode b : fixture().get("behaviour")) {
            String expected = b.get("behaviour").isNull() ? null : b.get("behaviour").asText();
            assertEquals(expected, FilterFields.behaviour(b.get("metadata").asText()), "behaviour " + b.get("metadata"));
        }
    }

    @Test
    public void missingUrlAndMethodAreHandled() {
        assertEquals("Prompt", FilterFields.agenticType(null));
        assertNull(FilterFields.endpointKind(null, null));
        assertNull(FilterFields.behaviour(null));
    }

    @Test
    public void onlyAgenticContextsAreDerived() {
        assertTrue(FilterFields.appliesTo("ENDPOINT"));
        assertTrue(FilterFields.appliesTo("AGENTIC"));
        assertTrue(FilterFields.appliesTo("MCP"));
        assertFalse(FilterFields.appliesTo("API"));
        assertFalse(FilterFields.appliesTo("DAST"));
        assertFalse(FilterFields.appliesTo(null));
        assertNull(FilterFields.derive("API", "/tool", "POST", "behaviour: \"block\""));
    }

    @Test
    public void applyLeavesApiSecurityEventsUntouched() {
        MaliciousEventDto api = MaliciousEventDto.newBuilder()
            .setContextSource("API")
            .setLatestApiEndpoint("/claude/tool")
            .setLatestApiMethod(URLMethods.Method.POST)
            .setMetadata("behaviour: \"block\"")
            .build();
        FilterFields.apply(api);
        assertNull(api.getAgenticType());
        assertNull(api.getEndpointKind());
        assertNull(api.getBehaviour());
    }

    @Test
    public void applyFillsEndpointEvents() {
        MaliciousEventDto endpoint = MaliciousEventDto.newBuilder()
            .setContextSource("ENDPOINT")
            .setLatestApiEndpoint("/claude/config/settings.json")
            .setLatestApiMethod(URLMethods.Method.POST)
            .setMetadata("policy_name: \"P\"\nbehaviour: \"block\"\n")
            .build();
        FilterFields.apply(endpoint);
        assertEquals("Config", endpoint.getAgenticType());
        assertEquals("CONFIG", endpoint.getEndpointKind());
        assertEquals("block", endpoint.getBehaviour());
    }
}

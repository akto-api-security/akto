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

    @Test
    public void caseSensitivityDiffersBetweenTheTwoUis() {
        assertEquals("Tool", FilterFields.agenticType("/Resource/TOOL"));
        assertEquals("POST", FilterFields.endpointKind("/Resource/TOOL", "POST"));
    }

    @Test
    public void queryStringIsIgnoredOnlyByTheOldUiBadge() {
        assertEquals("Tool", FilterFields.agenticType("/x?bl=server_y"));
        assertEquals("POST", FilterFields.endpointKind("/x?bl=server_y", "POST"));
    }

    @Test
    public void pluginPathIsPluginOnlyForTheOldUiBadge() {
        assertEquals("Tool", FilterFields.agenticType("/claude/plugin/x/tool"));
        assertEquals("PLUGIN", FilterFields.endpointKind("/claude/plugin/x/tool", "POST"));
    }

    @Test
    public void urlWithNoKeywordStaysPostAndPrompt() {
        assertEquals("Prompt", FilterFields.agenticType("/claude/guardrail"));
        assertEquals("POST", FilterFields.endpointKind("/claude/guardrail", "POST"));
        assertEquals("OTHER", FilterFields.endpointKind("/claude/guardrail", "OTHER"));
    }

    @Test
    public void toolConfigSkillResourceServerKeepTheirOwnKind() {
        assertEquals("TOOL", FilterFields.endpointKind("/claude/guardrail/tool-call", "POST"));
        assertEquals("CONFIG", FilterFields.endpointKind("/claude/config/settings.json", "POST"));
        assertEquals("SKILL", FilterFields.endpointKind("/skills/pdf", "POST"));
        assertEquals("RESOURCE", FilterFields.endpointKind("/mcp/resource/read/x", "POST"));
        assertEquals("SERVER", FilterFields.endpointKind("/mcp/server/ab12", "POST"));
    }

    @Test
    public void behaviourIsStoredAsWritten() {
        assertEquals("BLOCK", FilterFields.behaviour("behaviour: \"BLOCK\"\n"));
        assertEquals("warn", FilterFields.behaviour("policy_name: \"P\"\nbehaviour: \"warn\"\n"));
    }

    @Test
    public void deriveReturnsAllThreeForEveryAgenticContext() {
        for (String context : new String[] {"ENDPOINT", "AGENTIC", "MCP"}) {
            FilterFields.Derived d = FilterFields.derive(context, "/claude/guardrail/tool-call", "POST", "behaviour: \"alert\"\n");
            assertEquals("Tool", d.agenticType, context);
            assertEquals("TOOL", d.endpointKind, context);
            assertEquals("alert", d.behaviour, context);
        }
    }

    @Test
    public void applyOnAnEmptyEventDoesNotThrowAndFillsNothingForMissingContext() {
        MaliciousEventDto empty = new MaliciousEventDto();
        FilterFields.apply(empty);
        assertNull(empty.getAgenticType());
        assertNull(empty.getEndpointKind());
        assertNull(empty.getBehaviour());
    }

    @Test
    public void applyWithoutMetadataOrMethodStillSetsTheType() {
        MaliciousEventDto event = MaliciousEventDto.newBuilder()
            .setContextSource("ENDPOINT")
            .setLatestApiEndpoint("/claude/guardrail")
            .build();
        FilterFields.apply(event);
        assertEquals("Prompt", event.getAgenticType());
        assertNull(event.getBehaviour());
    }

    @Test
    public void applyOverwritesNothingForApiSecurityEvenWithToolLikeUrl() {
        for (String context : new String[] {"API", "DAST", "GEN_AI"}) {
            MaliciousEventDto event = MaliciousEventDto.newBuilder()
                .setContextSource(context)
                .setLatestApiEndpoint("/claude/guardrail/tool-call")
                .setLatestApiMethod(URLMethods.Method.POST)
                .setMetadata("behaviour: \"block\"\n")
                .build();
            FilterFields.apply(event);
            assertNull(event.getAgenticType(), context);
            assertNull(event.getEndpointKind(), context);
            assertNull(event.getBehaviour(), context);
        }
    }
}

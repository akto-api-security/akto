package com.akto.threat.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.akto.threat.backend.utils.FilterFields;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.bson.Document;
import org.junit.jupiter.api.Test;

public class MaliciousEventServiceFilterConditionTest {

    private static final String[] TYPES = {"Tool", "Skill", "Resource", "Prompt", "Config"};

    private static JsonNode fixture() throws Exception {
        try (InputStream in = MaliciousEventServiceFilterConditionTest.class.getResourceAsStream("/filter_fields_fixture.json")) {
            return new ObjectMapper().readTree(in);
        }
    }

    // Evaluates the small subset of Mongo operators these conditions use, against one document.
    private static boolean matches(Document cond, Map<String, Object> doc) {
        for (Map.Entry<String, Object> e : cond.entrySet()) {
            String key = e.getKey();
            Object value = e.getValue();
            if (key.equals("$or")) {
                boolean any = false;
                for (Object o : (List<?>) value) {
                    any |= matches((Document) o, doc);
                }
                if (!any) {
                    return false;
                }
            } else if (key.equals("$and")) {
                for (Object o : (List<?>) value) {
                    if (!matches((Document) o, doc)) {
                        return false;
                    }
                }
            } else if (!fieldMatches(value, doc.get(key))) {
                return false;
            }
        }
        return true;
    }

    private static boolean fieldMatches(Object cond, Object actual) {
        if (cond instanceof Pattern) {
            return actual instanceof String && ((Pattern) cond).matcher((String) actual).find();
        }
        if (cond instanceof Document) {
            for (Map.Entry<String, Object> op : ((Document) cond).entrySet()) {
                boolean ok;
                switch (op.getKey()) {
                    case "$not":
                        ok = !fieldMatches(op.getValue(), actual);
                        break;
                    case "$in":
                        ok = ((List<?>) op.getValue()).contains(actual);
                        break;
                    case "$exists":
                        ok = (actual != null) == (Boolean) op.getValue();
                        break;
                    default:
                        throw new IllegalArgumentException("unsupported operator " + op.getKey());
                }
                if (!ok) {
                    return false;
                }
            }
            return true;
        }
        return cond.equals(actual);
    }

    private static Map<String, Object> row(Object... keyValues) {
        Map<String, Object> doc = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            doc.put((String) keyValues[i], keyValues[i + 1]);
        }
        return doc;
    }

    private static Document typeFilter(String... types) {
        List<String> list = Arrays.asList(types);
        return MaliciousEventService.withStoredFieldFallback(
            MaliciousEventService.agenticTypeStoredCondition(list), MaliciousEventService.agenticTypeCondition(list));
    }

    private static Document behaviourFilter(String... behaviours) {
        List<String> list = Arrays.asList(behaviours);
        return MaliciousEventService.withStoredFieldFallback(
            MaliciousEventService.behaviourStoredCondition(list), MaliciousEventService.behaviourCondition(list));
    }

    @Test
    public void emptyOrMissingSelectionAddsNoCondition() {
        assertNull(MaliciousEventService.agenticTypeCondition(null));
        assertNull(MaliciousEventService.agenticTypeCondition(Collections.emptyList()));
        assertNull(MaliciousEventService.agenticTypeStoredCondition(null));
        assertNull(MaliciousEventService.agenticTypeStoredCondition(Collections.emptyList()));
        assertNull(MaliciousEventService.behaviourCondition(null));
        assertNull(MaliciousEventService.behaviourCondition(Collections.emptyList()));
        assertNull(MaliciousEventService.behaviourStoredCondition(null));
        assertNull(MaliciousEventService.behaviourStoredCondition(Collections.emptyList()));
        assertNull(MaliciousEventService.withStoredFieldFallback(null, new Document()));
        assertNull(MaliciousEventService.withStoredFieldFallback(new Document(), null));
    }

    @Test
    public void unknownTypeAddsNoCondition() {
        assertNull(MaliciousEventService.agenticTypeCondition(Arrays.asList("NotAType")));
    }

    @Test
    public void storedTypeConditionIsAPlainIn() {
        assertEquals(
            new Document("agenticType", new Document("$in", Arrays.asList("Tool", "Resource"))),
            MaliciousEventService.agenticTypeStoredCondition(Arrays.asList("Tool", "Resource")));
    }

    @Test
    public void storedBehaviourConditionIsAPlainIn() {
        assertEquals(
            new Document("agenticType", new Document("$exists", true))
                .append("behaviour", new Document("$in", Arrays.asList("block"))),
            MaliciousEventService.behaviourStoredCondition(Arrays.asList("block")));
    }

    @Test
    public void flaggedAlsoCoversWarnAndMissingBehaviour() {
        Document cond = MaliciousEventService.behaviourStoredCondition(Arrays.asList("flag"));
        assertTrue(matches(cond, row("agenticType", "Prompt", "behaviour", "flag")));
        assertTrue(matches(cond, row("agenticType", "Prompt", "behaviour", "warn")));
        assertTrue(matches(cond, row("agenticType", "Prompt")));
        assertFalse(matches(cond, row("agenticType", "Prompt", "behaviour", "block")));
        assertFalse(matches(cond, row("agenticType", "Prompt", "behaviour", "alert")));
    }

    @Test
    public void fallbackWrapsStoredAndRegexConditions() {
        Document stored = new Document("agenticType", "Tool");
        Document regex = new Document("latestApiEndpoint", Pattern.compile("tool"));
        Document wrapped = MaliciousEventService.withStoredFieldFallback(stored, regex);
        assertEquals(
            new Document("$or", Arrays.asList(
                stored,
                new Document("$and", Arrays.asList(new Document("agenticType", new Document("$exists", false)), regex)))),
            wrapped);
    }

    @Test
    public void regexTypeConditionAgreesWithIngestForEveryFixtureUrl() throws Exception {
        for (JsonNode c : fixture().get("cases")) {
            String url = c.get("url").asText();
            for (String type : TYPES) {
                boolean expected = FilterFields.agenticType(url).equals(type);
                boolean actual = matches(MaliciousEventService.agenticTypeCondition(Arrays.asList(type)), row("latestApiEndpoint", url));
                assertEquals(expected, actual, type + " " + url);
            }
        }
    }

    @Test
    public void regexTypeConditionTreatsAMissingUrlAsPrompt() {
        assertTrue(matches(MaliciousEventService.agenticTypeCondition(Arrays.asList("Prompt")), row()));
        assertFalse(matches(MaliciousEventService.agenticTypeCondition(Arrays.asList("Tool")), row()));
    }

    @Test
    public void multipleSelectedTypesMatchAnyOfThem() {
        Document cond = MaliciousEventService.agenticTypeCondition(Arrays.asList("Tool", "Resource"));
        assertTrue(matches(cond, row("latestApiEndpoint", "/claude/guardrail/tool-call")));
        assertTrue(matches(cond, row("latestApiEndpoint", "/mcp/resource/read/x")));
        assertFalse(matches(cond, row("latestApiEndpoint", "/claude/guardrail")));
    }

    @Test
    public void aSkillUrlIsNotReturnedForAResourceFilter() {
        Document cond = MaliciousEventService.agenticTypeCondition(Arrays.asList("Resource"));
        assertFalse(matches(cond, row("latestApiEndpoint", "/a/skill/resource")));
        assertFalse(matches(cond, row("latestApiEndpoint", "/a/tool/resource")));
        assertTrue(matches(cond, row("latestApiEndpoint", "/a/resource/x")));
    }

    @Test
    public void regexBehaviourConditionAgreesWithIngestForEveryFixtureMetadata() throws Exception {
        for (JsonNode b : fixture().get("behaviour")) {
            String metadata = b.get("metadata").asText();
            String stored = FilterFields.behaviour(metadata);
            boolean noBehaviourAtAll = !metadata.contains("behaviour");
            Map<String, Object> doc = row("metadata", metadata);
            assertEquals("block".equals(stored), matches(MaliciousEventService.behaviourCondition(Arrays.asList("block")), doc), "block " + metadata);
            assertEquals("alert".equals(stored), matches(MaliciousEventService.behaviourCondition(Arrays.asList("alert")), doc), "alert " + metadata);
            boolean flagged = "warn".equals(stored) || "flag".equals(stored) || noBehaviourAtAll;
            assertEquals(flagged, matches(MaliciousEventService.behaviourCondition(Arrays.asList("flag")), doc), "flag " + metadata);
        }
    }

    @Test
    public void oldEventWithoutStoredFieldsIsStillFound() {
        Map<String, Object> oldEvent = row(
            "latestApiEndpoint", "/claude/guardrail/tool-call",
            "metadata", "policy_name: \"P\"\nbehaviour: \"block\"\n");
        assertTrue(matches(typeFilter("Tool"), oldEvent));
        assertFalse(matches(typeFilter("Resource"), oldEvent));
        assertTrue(matches(behaviourFilter("block"), oldEvent));
        assertFalse(matches(behaviourFilter("alert"), oldEvent));
    }

    @Test
    public void newEventIsFoundThroughItsStoredFields() {
        Map<String, Object> newEvent = row("agenticType", "Tool", "behaviour", "block");
        assertTrue(matches(typeFilter("Tool"), newEvent));
        assertFalse(matches(typeFilter("Prompt"), newEvent));
        assertTrue(matches(behaviourFilter("block"), newEvent));
        assertFalse(matches(behaviourFilter("alert"), newEvent));
    }

    @Test
    public void whenTheFieldIsStoredItWinsOverTheUrlRegex() {
        Map<String, Object> event = row("agenticType", "Prompt", "latestApiEndpoint", "/x/tool");
        assertTrue(matches(typeFilter("Prompt"), event));
        assertFalse(matches(typeFilter("Tool"), event));
    }

    @Test
    public void newEventWithoutBehaviourCountsAsFlaggedOnly() {
        Map<String, Object> event = row("agenticType", "Prompt");
        assertTrue(matches(behaviourFilter("flag"), event));
        assertFalse(matches(behaviourFilter("block"), event));
        assertFalse(matches(behaviourFilter("alert"), event));
    }

    @Test
    public void oldEventWithoutBehaviourCountsAsFlaggedOnly() {
        Map<String, Object> event = row("metadata", "policy_name: \"P\"\n");
        assertTrue(matches(behaviourFilter("flag"), event));
        assertFalse(matches(behaviourFilter("block"), event));
    }

    @Test
    public void mixedOldAndNewEventsAreAllReturnedForOneFilter() {
        Map<String, Object> oldTool = row("latestApiEndpoint", "/claude/guardrail/tool-call");
        Map<String, Object> newTool = row("agenticType", "Tool");
        Map<String, Object> newPrompt = row("agenticType", "Prompt");
        Map<String, Object> oldPrompt = row("latestApiEndpoint", "/claude/guardrail");
        Document filter = typeFilter("Tool");
        assertTrue(matches(filter, oldTool));
        assertTrue(matches(filter, newTool));
        assertFalse(matches(filter, newPrompt));
        assertFalse(matches(filter, oldPrompt));
    }

    @Test
    public void selectedBehaviourIsNotTreatedAsARegex() {
        Document cond = MaliciousEventService.behaviourCondition(Arrays.asList("b.*"));
        assertFalse(matches(cond, row("metadata", "behaviour: \"block\"\n")));
    }
}

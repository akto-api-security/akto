package com.akto.action;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LLMServiceTest {

    private static Map<String, Object> message(String role, String content) {
        Map<String, Object> m = new HashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    // --- extractPrompt ---

    @Test
    public void extractPrompt_returnsFirstMessageContent() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("messages", Collections.singletonList(message("user", "hello")));

        assertEquals("hello", LLMService.extractPrompt(payload));
    }

    @Test
    public void extractPrompt_usesOnlyFirstMessageWhenMultiplePresent() {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(message("system", "first"));
        messages.add(message("user", "second"));
        Map<String, Object> payload = new HashMap<>();
        payload.put("messages", messages);

        assertEquals("first", LLMService.extractPrompt(payload));
    }

    @Test
    public void extractPrompt_throwsWhenMessagesMissing() {
        try {
            LLMService.extractPrompt(new HashMap<>());
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void extractPrompt_throwsWhenMessagesEmpty() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("messages", new ArrayList<>());
        try {
            LLMService.extractPrompt(payload);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // --- extractContent ---

    @Test
    public void extractContent_returnsNullForNullRaw() {
        assertNull(LLMService.extractContent(null));
    }

    @Test
    public void extractContent_returnsNullWhenChoicesMissingOrEmpty() {
        assertNull(LLMService.extractContent(new HashMap<>()));
        Map<String, Object> raw = new HashMap<>();
        raw.put("choices", new ArrayList<>());
        assertNull(LLMService.extractContent(raw));
    }

    @Test
    public void extractContent_extractsNestedContent() {
        Map<String, Object> message = new HashMap<>();
        message.put("content", "hello back");
        Map<String, Object> choice = new HashMap<>();
        choice.put("message", message);
        Map<String, Object> raw = new HashMap<>();
        raw.put("choices", Collections.singletonList(choice));

        assertEquals("hello back", LLMService.extractContent(raw));
    }

    // --- wrapAsChoices ---

    @Test
    public void wrapAsChoices_returnsNullForNullContent() {
        assertNull(LLMService.wrapAsChoices(null));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void wrapAsChoices_producesChoicesMessageContentShape() {
        Map<String, Object> wrapped = LLMService.wrapAsChoices("the answer");

        List<Map<String, Object>> choices = (List<Map<String, Object>>) wrapped.get("choices");
        assertEquals(1, choices.size());
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        assertEquals("the answer", message.get("content"));
        assertTrue(message.containsKey("role"));
    }
}

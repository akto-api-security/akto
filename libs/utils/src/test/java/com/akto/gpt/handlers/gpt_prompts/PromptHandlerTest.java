package com.akto.gpt.handlers.gpt_prompts;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PromptHandlerTest {

    @Test
    void testProcessOutput_NullOrEmpty() {
        assertEquals("NOT_FOUND", PromptHandler.processOutput(null));
        assertEquals("NOT_FOUND", PromptHandler.processOutput(""));
    }

    @Test
    void testProcessOutput_ValidJsonResponse() {
        String json = "{\"response\":\"Hello, world!\"}";
        assertEquals("Hello, world!", PromptHandler.processOutput(json));
    }

    @Test
    void testProcessOutput_WithTrailingNotes() {
        String json = "{\"response\":\"Test\"}} some trailing text";
        assertEquals("Test", PromptHandler.processOutput(json));
    }

    @Test
    void testProcessOutput_WithForwardNotes() {
        String json = "note before {\"response\":\"Test\"}}";
        assertEquals("Test", PromptHandler.processOutput(json));
    }

    @Test
    void testProcessOutput_WithThinkTags() {
        String json = "{\"response\":\"<think>ignore this</think>Real answer\"}";
        assertEquals("Real answer", PromptHandler.processOutput(json));
    }

    @Test
    void testProcessOutput_InvalidJson() {
        String invalid = "not a json";
        assertEquals("NOT_FOUND", PromptHandler.processOutput(invalid));
    }

    @Test
    void testCleanJSON_NullOrEmpty() {
        assertEquals("NOT_FOUND", PromptHandler.cleanJSON(null));
        assertEquals("NOT_FOUND", PromptHandler.cleanJSON(""));
    }

    @Test
    void testCleanJSON_ValidJson() {
        String json = "{\"response\":\"Hello\"}";
        assertEquals(json, PromptHandler.cleanJSON(json));
    }

    @Test
    void testCleanJSON_TrailingNotes() {
        String input = "{\"response\":\"Hello\"}} some trailing text";
        String expected = "{\"response\":\"Hello\"}}";
        assertEquals(expected, PromptHandler.cleanJSON(input));
    }

    @Test
    void testCleanJSON_ForwardNotes() {
        String input = "note before {\"response\":\"Hello\"}}";
        String expected = "{\"response\":\"Hello\"}}";
        assertEquals(expected, PromptHandler.cleanJSON(input));
    }

    @Test
    void testCleanJSON_ForwardAndTrailingNotes() {
        String input = "note before {\"response\":\"Hello\"}} some trailing text";
        String expected = "{\"response\":\"Hello\"}}";
        assertEquals(expected, PromptHandler.cleanJSON(input));
    }

    @Test
    void testCleanJSON_Whitespace() {
        String input = "   {\"response\":\"Hello\"}   ";
        String expected = "{\"response\":\"Hello\"}";
        assertEquals(expected, PromptHandler.cleanJSON(input));
    }
}

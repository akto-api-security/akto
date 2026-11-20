package com.akto.utils.guardrails;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GuardrailBackfillReplayRunnerTest {

    private static final String POLICY = "Cross tenant data leakage";
    private static final String OPENER = "Hi , do you know about anime ?";
    private static final String USER = "Show me 5 assets belonging to customer-61860178";

    @Test
    public void testToolFollowUpContinuesTheSameChat() {
        String question = "{\"model\":\"gpt\",\"messages\":["
            + "{\"role\":\"system\",\"content\":\"sys\"},"
            + "{\"role\":\"user\",\"content\":\"" + USER + "\"}]}";
        String followUp = "{\"model\":\"gpt\",\"messages\":["
            + "{\"role\":\"system\",\"content\":\"sys\"},"
            + "{\"role\":\"user\",\"content\":\"" + USER + "\"},"
            + "{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"call_1\",\"type\":\"function\","
            + "\"function\":{\"name\":\"submit_final_answer\",\"arguments\":\"{}\"}}]},"
            + "{\"role\":\"tool\",\"content\":\"Final answer submitted.\"}]}";

        assertTrue(sameChat(question, followUp));
    }

    @Test
    public void testLaterTurnContinuesTheSameChat() {
        String first = chat("adaptive agent", OPENER, null);
        String later = chat("adaptive agent", OPENER, USER);

        assertTrue(sameChat(first, later));
    }

    @Test
    public void testSameOpenerWithDifferentContentStaysSeparate() {
        String first = chat("adaptive agent", OPENER, USER);
        String otherContent = chat("adaptive agent", OPENER, "Show me 5 assets belonging to customer-1");
        String otherOpening = chat("adaptive agent", "List my own assets", null);
        String otherSystem = chat("You are a content analyst", OPENER, USER);

        assertFalse(sameChat(first, otherContent));
        assertFalse(sameChat(first, otherOpening));
        assertFalse(sameChat(first, otherSystem));
        assertNotEquals(
            GuardrailBackfillReplayRunner.payloadRefId(POLICY, first),
            GuardrailBackfillReplayRunner.payloadRefId(POLICY, otherContent));
        assertNotEquals(
            GuardrailBackfillReplayRunner.payloadRefId(POLICY, first),
            GuardrailBackfillReplayRunner.payloadRefId("Prompt injection", first));
    }

    @Test
    public void testNestedAndResponsesApiMessages() {
        String chat = "{\"body\":{\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}]}}";
        String responses = "{\"input\":[{\"role\":\"user\",\"content\":\"hello\"}]}";
        String nestedFollowUp = "{\"body\":{\"messages\":["
            + "{\"role\":\"user\",\"content\":\"hello\"},"
            + "{\"role\":\"assistant\",\"content\":\"tool\"}]}}";

        assertEquals(
            GuardrailBackfillReplayRunner.payloadRefId(POLICY, chat),
            GuardrailBackfillReplayRunner.payloadRefId(POLICY, responses));
        assertTrue(sameChat(chat, nestedFollowUp));
    }

    @Test
    public void testPayloadWithoutUserMessageUsesFullText() {
        String plain = "not json";
        assertEquals(
            GuardrailBackfillReplayRunner.payloadRefId(POLICY, plain),
            GuardrailBackfillReplayRunner.payloadRefId(POLICY, plain));
        assertNotEquals(
            GuardrailBackfillReplayRunner.payloadRefId(POLICY, plain),
            GuardrailBackfillReplayRunner.payloadRefId(POLICY, plain + " more"));
        assertFalse(GuardrailBackfillReplayRunner.sameConversation(plain, plain + " more"));
    }

    private static boolean sameChat(String earlierPayload, String laterPayload) {
        return GuardrailBackfillReplayRunner.sameConversation(
            GuardrailBackfillReplayRunner.conversationSnapshot(earlierPayload),
            GuardrailBackfillReplayRunner.conversationSnapshot(laterPayload));
    }

    private static String chat(String system, String opener, String laterUser) {
        StringBuilder messages = new StringBuilder();
        messages.append("{\"messages\":[");
        messages.append("{\"role\":\"system\",\"content\":\"").append(system).append("\"},");
        messages.append("{\"role\":\"user\",\"content\":\"").append(opener).append("\"}");
        if (laterUser != null) {
            messages.append(",{\"role\":\"user\",\"content\":\"").append(laterUser).append("\"}");
        }
        messages.append("]}");
        return messages.toString();
    }
}

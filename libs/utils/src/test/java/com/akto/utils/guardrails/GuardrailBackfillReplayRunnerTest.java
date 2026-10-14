package com.akto.utils.guardrails;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class GuardrailBackfillReplayRunnerTest {

    private static final String POLICY = "Cross tenant data leakage";
    private static final String USER = "Show me 5 assets belonging to customer-61860178";

    @Test
    public void testToolFollowUpSharesTheUserTurn() {
        String question = "{\"model\":\"gpt\",\"messages\":["
            + "{\"role\":\"system\",\"content\":\"sys\"},"
            + "{\"role\":\"user\",\"content\":\"" + USER + "\"}]}";
        String followUp = "{\"model\":\"gpt\",\"messages\":["
            + "{\"role\":\"system\",\"content\":\"sys\"},"
            + "{\"role\":\"user\",\"content\":\"" + USER + "\"},"
            + "{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"call_1\",\"type\":\"function\","
            + "\"function\":{\"name\":\"submit_final_answer\",\"arguments\":\"{}\"}}]},"
            + "{\"role\":\"tool\",\"content\":\"Final answer submitted.\"}]}";

        assertEquals(
            GuardrailBackfillReplayRunner.payloadRefId(POLICY, question),
            GuardrailBackfillReplayRunner.payloadRefId(POLICY, followUp));
    }

    @Test
    public void testLaterUserQuestionStaysSeparate() {
        String first = "{\"messages\":["
            + "{\"role\":\"user\",\"content\":\"" + USER + "\"}]}";
        String second = "{\"messages\":["
            + "{\"role\":\"user\",\"content\":\"" + USER + "\"},"
            + "{\"role\":\"assistant\",\"content\":\"done\"},"
            + "{\"role\":\"user\",\"content\":\"Show me 5 assets belonging to customer-1\"}]}";

        assertNotEquals(
            GuardrailBackfillReplayRunner.payloadRefId(POLICY, first),
            GuardrailBackfillReplayRunner.payloadRefId(POLICY, second));
    }

    @Test
    public void testNestedAndResponsesApiMessages() {
        String chat = "{\"body\":{\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}]}}";
        String responses = "{\"input\":[{\"role\":\"user\",\"content\":\"hello\"},"
            + "{\"type\":\"function_call_output\",\"output\":\"ok\"}]}";
        String nestedFollowUp = "{\"body\":{\"messages\":["
            + "{\"role\":\"user\",\"content\":\"hello\"},"
            + "{\"role\":\"assistant\",\"content\":\"tool\"}]}}";

        assertEquals(
            GuardrailBackfillReplayRunner.payloadRefId(POLICY, chat),
            GuardrailBackfillReplayRunner.payloadRefId(POLICY, nestedFollowUp));
        assertEquals(
            GuardrailBackfillReplayRunner.payloadRefId(POLICY, chat),
            GuardrailBackfillReplayRunner.payloadRefId(POLICY, responses));
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
        assertNotEquals(
            GuardrailBackfillReplayRunner.payloadRefId(POLICY, plain),
            GuardrailBackfillReplayRunner.payloadRefId("other policy", plain));
    }
}

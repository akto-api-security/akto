package com.akto.action.testing;

import com.akto.dto.testing.MultiExecTestResult;
import com.akto.dto.testing.TestResult.Confidence;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.WorkflowNodeDetails;
import com.akto.dto.testing.WorkflowTest;
import com.akto.dto.testing.WorkflowTestResult.NodeResult;
import com.akto.dto.testing.YamlNodeDetails;
import com.akto.gpt.handlers.gpt_prompts.FalsePositiveVerifierPromptHandler;
import com.akto.gpt.handlers.gpt_prompts.FalsePositiveVerifierPromptHandler.Step;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class AiVerificationActionTest {

    // A step's stored message: the List.toString() of its request/response JSON strings.
    private static String nodeMessage(String... bodies) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < bodies.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append("{\"request\": {\"method\": \"POST\"}, \"response\": {\"statusCode\": 200, \"body\": \"")
              .append(bodies[i]).append("\"}, \"responseTime\": 100}");
        }
        return sb.append("]").toString();
    }

    private static MultiExecTestResult multi(Map<String, NodeResult> nodes, String... order) {
        return new MultiExecTestResult(nodes, true, Confidence.HIGH, Arrays.asList(order));
    }

    @Test
    public void stepsFollowTheExecutionOrderWithTheirBaselines() {
        Map<String, NodeResult> nodes = new HashMap<>();
        nodes.put("x1", new NodeResult(nodeMessage("a"), true, Collections.emptyList(), "passed"));
        nodes.put("x2", new NodeResult(nodeMessage("b", "c"), false, Collections.emptyList(), ""));
        YamlNodeDetails x1 = new YamlNodeDetails();
        x1.setOriginalMessage("original-x1");
        Map<String, WorkflowNodeDetails> details = new HashMap<>();
        details.put("x1", x1);
        WorkflowTest workflowTest = new WorkflowTest();
        workflowTest.setMapNodeIdToWorkflowNodeDetails(details);
        TestingRunResult result = new TestingRunResult();
        result.setWorkflowTest(workflowTest);

        List<Step> steps = AiVerificationAction.stepsToJudge(result, multi(nodes, "x1", "x2"));

        assertEquals(2, steps.size());
        String prompt = FalsePositiveVerifierPromptHandler.buildStepsPrompt("T", null, steps);
        assertTrue(prompt, prompt.contains("Step x1 (1 of 2, 1 request, step validate passed: passed):"));
        assertTrue(prompt, prompt.contains("Step x2 (2 of 2, last of 2 requests, step validate did not pass):"));
        assertTrue("the last request of x2 is shown", prompt.contains("Body: c"));
    }

    @Test
    public void onlyConfidentTruePositivesConfirmTheFinding() {
        int min = AiVerificationAction.MIN_TRUE_POSITIVE_CONFIDENCE;
        assertTrue(AiVerificationAction.isConfirmed(FalsePositiveVerifierPromptHandler.TRUE_POSITIVE, min));
        assertFalse(AiVerificationAction.isConfirmed(FalsePositiveVerifierPromptHandler.TRUE_POSITIVE, min - 1));
        assertFalse(AiVerificationAction.isConfirmed(FalsePositiveVerifierPromptHandler.FALSE_POSITIVE, 100));
    }

    @Test
    public void missingWorkflowDetailsDoNotFailAndEmptyStepsAreNotJudged() {
        Map<String, NodeResult> nodes = new HashMap<>();
        nodes.put("x1", new NodeResult(nodeMessage("a"), true, Collections.emptyList()));
        assertEquals("no workflowTest stored", 1,
            AiVerificationAction.stepsToJudge(new TestingRunResult(), multi(nodes, "x1", "x9")).size());

        Map<String, NodeResult> noRequests = new HashMap<>();
        noRequests.put("x1", new NodeResult("[]", false, Collections.singletonList("Failed building request body")));
        assertTrue(AiVerificationAction.stepsToJudge(new TestingRunResult(), multi(noRequests, "x1")).isEmpty());
        assertTrue(AiVerificationAction.stepsToJudge(new TestingRunResult(), multi(null, "x1")).isEmpty());
    }
}

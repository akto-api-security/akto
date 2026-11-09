package com.akto.dto.testing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class TestingRunCustomPromptTest {

    @Test
    public void theCustomPromptIsStoredOnTheRun() {
        TestingRun run = new TestingRun();
        assertNull(run.getAutomatedTestsCustomPrompt());
        run.setAutomatedTestsCustomPrompt("Org id: 69f8. Find the rest yourself.");
        assertEquals("Org id: 69f8. Find the rest yourself.", run.getAutomatedTestsCustomPrompt());
        assertEquals("automatedTestsCustomPrompt", TestingRun.AUTOMATED_TESTS_CUSTOM_PROMPT);
    }
}

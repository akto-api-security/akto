package com.akto.testing.kafka_utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.After;
import org.junit.Test;

public class TestingConfigurationsCustomPromptTest {

    @After
    public void clear() {
        TestingConfigurations.getInstance().setAutomatedTestsCustomPrompt(null);
    }

    @Test
    public void theRunsCustomPromptIsKeptForItsSmartTests() {
        TestingConfigurations configurations = TestingConfigurations.getInstance();
        configurations.setAutomatedTestsCustomPrompt("Project id: 69f8. Fix the request until it works.");
        assertEquals("Project id: 69f8. Fix the request until it works.", configurations.getAutomatedTestsCustomPrompt());
        configurations.setAutomatedTestsCustomPrompt(null);
        assertNull(configurations.getAutomatedTestsCustomPrompt());
    }
}

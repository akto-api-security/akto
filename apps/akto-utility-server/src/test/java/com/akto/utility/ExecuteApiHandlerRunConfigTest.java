package com.akto.utility;

import com.akto.agent.ApiExecutionJobStore;
import com.akto.dto.testing.TestingRunConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The config /utility/execute replays under carries the testing run's script selection. */
public class ExecuteApiHandlerRunConfigTest {

    @Test
    public void registeredSelectionReachesTheConfig() {
        ApiExecutionJobStore store = new ApiExecutionJobStore();
        store.registerConversationScripts("c1", "8752de62-e609-481f-b35e-6e9f38379d9c", "");

        TestingRunConfig config = ExecuteApiHandler.buildRunConfig(store, "c1");

        assertEquals("8752de62-e609-481f-b35e-6e9f38379d9c", config.getPreRequestScriptId());
        assertEquals("", config.getPostRequestScriptId());
        assertEquals("c1", config.getConversationId());
    }

    @Test
    public void unregisteredConversationKeepsDefaultSelection() {
        TestingRunConfig config = ExecuteApiHandler.buildRunConfig(new ApiExecutionJobStore(), "unknown");

        assertNull(config.getPreRequestScriptId());
        assertNull(config.getPostRequestScriptId());
        assertEquals("unknown", config.getConversationId());
    }

    @Test
    public void noConversationIdKeepsDefaultSelection() {
        ApiExecutionJobStore store = new ApiExecutionJobStore();
        store.registerConversationScripts("c1", "a", "b");

        TestingRunConfig config = ExecuteApiHandler.buildRunConfig(store, null);

        assertNull(config.getPreRequestScriptId());
        assertNull(config.getPostRequestScriptId());
    }
}

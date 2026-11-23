package com.akto.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ApiExecutionJobStoreConversationScriptsTest {

    @Test
    public void registeredIdsComeBackRaw() {
        ApiExecutionJobStore store = new ApiExecutionJobStore();
        store.registerConversationScripts("c1", "8752de62", "");

        ApiExecutionJobStore.ConversationScripts s = store.getConversationScripts("c1");
        assertNotNull(s);
        assertEquals("8752de62", s.getPreRequestScriptId());
        // "" means "no script" and must not be turned into null (= default script)
        assertEquals("", s.getPostRequestScriptId());
    }

    @Test
    public void nullIdsStayNullSoDefaultScriptsStillApply() {
        ApiExecutionJobStore store = new ApiExecutionJobStore();
        store.registerConversationScripts("c1", null, null);

        ApiExecutionJobStore.ConversationScripts s = store.getConversationScripts("c1");
        assertNotNull(s);
        assertNull(s.getPreRequestScriptId());
        assertNull(s.getPostRequestScriptId());
    }

    @Test
    public void unknownOrUnregisteredConversationIsAbsentNotEmpty() {
        ApiExecutionJobStore store = new ApiExecutionJobStore();
        assertNull(store.getConversationScripts("nope"));
        assertNull(store.getConversationScripts(null));

        store.registerConversationScripts("c1", "a", "b");
        store.unregisterConversationScripts("c1");
        assertNull(store.getConversationScripts("c1"));
    }

    @Test
    public void conversationsAreIsolated() {
        ApiExecutionJobStore store = new ApiExecutionJobStore();
        store.registerConversationScripts("c1", "a", "b");
        store.registerConversationScripts("c2", "x", "y");
        store.unregisterConversationScripts("c1");

        assertEquals("x", store.getConversationScripts("c2").getPreRequestScriptId());
    }

    @Test
    public void blankConversationIdIsNotRegistered() {
        ApiExecutionJobStore store = new ApiExecutionJobStore();
        store.registerConversationScripts("", "a", "b");
        store.registerConversationScripts(null, "a", "b");
        assertNull(store.getConversationScripts(""));
    }
}

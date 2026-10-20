package com.akto.action;

import com.akto.action.llm.ServiceKey;
import com.opensymphony.xwork2.Action;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class LLMActionV3Test {

    @Test
    public void getLLMResponseV3_errorsWhenPromptMissing() {
        LLMAction action = new LLMAction();
        action.setServiceKey(ServiceKey.MINI_TESTING_TEST_EDITOR.getKey());

        assertEquals(Action.ERROR.toUpperCase(), action.getLLMResponseV3());
        assertFalse(action.getActionErrors().isEmpty());
    }

    @Test
    public void getLLMResponseV3_errorsWhenServiceKeyMissing() {
        LLMAction action = new LLMAction();
        action.setPrompt("hello");

        assertEquals(Action.ERROR.toUpperCase(), action.getLLMResponseV3());
        assertFalse(action.getActionErrors().isEmpty());
    }

    @Test
    public void getLLMResponseV3_errorsWhenServiceKeyIsInternalOnly() {
        LLMAction action = new LLMAction();
        action.setPrompt("hello");
        action.setServiceKey(ServiceKey.ENDPOINT_SHIELD_SKILL_VALIDATION.getKey());

        assertEquals(Action.ERROR.toUpperCase(), action.getLLMResponseV3());
        assertFalse(action.getActionErrors().isEmpty());
    }

    @Test
    public void getLLMResponseV3_errorsWhenServiceKeyUnrecognized() {
        LLMAction action = new LLMAction();
        action.setPrompt("hello");
        action.setServiceKey("not-a-real-key");

        assertEquals(Action.ERROR.toUpperCase(), action.getLLMResponseV3());
        assertFalse(action.getActionErrors().isEmpty());
    }
}

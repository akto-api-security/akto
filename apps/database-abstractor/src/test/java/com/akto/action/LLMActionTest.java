package com.akto.action;

import com.akto.llm.ServiceKey;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LLMActionTest {

    @Test
    public void resolveServiceKey_defaultsWhenNoneSet() {
        LLMAction action = new LLMAction();

        assertEquals(ServiceKey.DEFAULT, action.resolveServiceKey());
    }

    @Test
    public void resolveServiceKey_defaultsOnUnknownKey() {
        LLMAction action = new LLMAction();
        action.setServiceKey("not-a-real-key");

        assertEquals(ServiceKey.DEFAULT, action.resolveServiceKey());
    }

    @Test
    public void resolveServiceKey_returnsTheRequestedKeyWhenExternallySelectable() {
        LLMAction action = new LLMAction();
        action.setServiceKey(ServiceKey.MINI_TESTING_TEST_EDITOR.getKey());

        assertEquals(ServiceKey.MINI_TESTING_TEST_EDITOR, action.resolveServiceKey());
    }

    // The security-relevant case: an external caller must not be able to name itself into a key
    // that's only ever set by this repo's own direct Java calls (see ServiceKey's javadoc).
    @Test
    public void resolveServiceKey_fallsBackToDefaultForInternalOnlyKeys() {
        LLMAction action = new LLMAction();

        action.setServiceKey(ServiceKey.ENDPOINT_SHIELD_SKILL_VALIDATION.getKey());
        assertEquals(ServiceKey.DEFAULT, action.resolveServiceKey());

        action.setServiceKey(ServiceKey.ENDPOINT_SHIELD_SETTINGS_SCAN.getKey());
        assertEquals(ServiceKey.DEFAULT, action.resolveServiceKey());
    }
}

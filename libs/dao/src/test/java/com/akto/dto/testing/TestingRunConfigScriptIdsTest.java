package com.akto.dto.testing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.akto.dto.testing.config.TestScript;

public class TestingRunConfigScriptIdsTest {

    private static TestingRunConfig base(String pre, String post) {
        TestingRunConfig c = new TestingRunConfig();
        c.setPreRequestScriptId(pre);
        c.setPostRequestScriptId(post);
        return c;
    }

    @Test
    public void rebaseKeepsOwnValuesIncludingExplicitNone() {
        TestingRunConfig own = base(TestScript.NO_SCRIPT, "own-post");
        own.rebaseOn(base("base-pre", "base-post"));
        assertEquals(TestScript.NO_SCRIPT, own.getPreRequestScriptId());
        assertEquals("own-post", own.getPostRequestScriptId());
    }

    @Test
    public void rebaseFillsOnlyNullValues() {
        TestingRunConfig own = base(null, null);
        own.rebaseOn(base("base-pre", TestScript.NO_SCRIPT));
        assertEquals("base-pre", own.getPreRequestScriptId());
        assertEquals(TestScript.NO_SCRIPT, own.getPostRequestScriptId());
    }

    @Test
    public void rebaseOnConfigWithoutIdsLeavesDefaultSemantics() {
        TestingRunConfig own = base(null, null);
        own.rebaseOn(base(null, null));
        assertNull(own.getPreRequestScriptId());
        assertNull(own.getPostRequestScriptId());
    }

    @Test
    public void defaultNames() {
        assertEquals("default-test-pre", TestScript.defaultNameFor(TestScript.Type.PRE_REQUEST));
        assertEquals("default-test-post", TestScript.defaultNameFor(TestScript.Type.POST_REQUEST));
    }
}

package com.akto.testing.scripts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.config.TestScript;

public class TestScriptResolverTest {

    private StubTestScriptProvider provider;
    private TestScriptResolver resolver;

    @BeforeEach
    public void setUp() {
        provider = new StubTestScriptProvider();
        provider.defaults.put(TestScript.Type.PRE_REQUEST, StubTestScriptProvider.script("def-pre", TestScript.Type.PRE_REQUEST, "def();"));
        provider.byId.put("chosen", StubTestScriptProvider.script("chosen", TestScript.Type.PRE_REQUEST, "chosen();"));
        provider.byId.put("a-post", StubTestScriptProvider.script("a-post", TestScript.Type.POST_REQUEST, "post();"));
        provider.byId.put("empty", StubTestScriptProvider.script("empty", TestScript.Type.PRE_REQUEST, ""));
        resolver = new TestScriptResolver(provider);
    }

    private static TestingRunConfig config(String pre, String post) {
        TestingRunConfig c = new TestingRunConfig();
        c.setId(42);
        c.setPreRequestScriptId(pre);
        c.setPostRequestScriptId(post);
        return c;
    }

    @Test
    public void noConfigMeansNoScript() {
        assertFalse(resolver.resolve(null, TestScript.Type.PRE_REQUEST).isPresent());
        assertEquals(0, provider.defaultCalls + provider.byIdCalls);
    }

    @Test
    public void nullIdUsesDefault() {
        assertEquals("def-pre", resolver.resolve(config(null, null), TestScript.Type.PRE_REQUEST).get().getId());
        // no default post script configured
        assertFalse(resolver.resolve(config(null, null), TestScript.Type.POST_REQUEST).isPresent());
    }

    @Test
    public void emptyIdMeansNoneEvenIfDefaultExists() {
        assertFalse(resolver.resolve(config(TestScript.NO_SCRIPT, null), TestScript.Type.PRE_REQUEST).isPresent());
        assertEquals(0, provider.defaultCalls);
    }

    @Test
    public void explicitIdWinsOverDefault() {
        assertEquals("chosen", resolver.resolve(config("chosen", null), TestScript.Type.PRE_REQUEST).get().getId());
    }

    @Test
    public void missingOrWrongTypeOrEmptyScriptRunsNothing() {
        assertFalse(resolver.resolve(config("deleted", null), TestScript.Type.PRE_REQUEST).isPresent());
        assertFalse(resolver.resolve(config("a-post", null), TestScript.Type.PRE_REQUEST).isPresent());
        assertFalse(resolver.resolve(config("empty", null), TestScript.Type.PRE_REQUEST).isPresent());
    }

    @Test
    public void phaseReadsItsOwnField() {
        assertEquals("a-post", resolver.resolve(config(TestScript.NO_SCRIPT, "a-post"), TestScript.Type.POST_REQUEST).get().getId());
    }

    @Test
    public void providerFailurePropagates() {
        provider.failWith = new IllegalStateException("db down");
        assertThrows(IllegalStateException.class, () -> resolver.resolve(config(null, null), TestScript.Type.PRE_REQUEST));
    }
}

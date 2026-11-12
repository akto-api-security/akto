package com.akto.testing.scripts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.akto.dto.testing.config.TestScript;

public class FallbackTestScriptProviderTest {

    @Test
    public void primaryWinsWhenPresent() {
        StubTestScriptProvider primary = new StubTestScriptProvider();
        StubTestScriptProvider fallback = new StubTestScriptProvider();
        primary.defaults.put(TestScript.Type.PRE_REQUEST, StubTestScriptProvider.script("lib", TestScript.Type.PRE_REQUEST, "lib();"));
        fallback.defaults.put(TestScript.Type.PRE_REQUEST, StubTestScriptProvider.script("legacy", TestScript.Type.PRE_REQUEST, "legacy();"));

        FallbackTestScriptProvider provider = new FallbackTestScriptProvider(primary, fallback);
        assertEquals("lib", provider.findDefault(TestScript.Type.PRE_REQUEST).getId());
        assertEquals(0, fallback.defaultCalls);
    }

    @Test
    public void fallbackOnlyOnNotFound() {
        StubTestScriptProvider primary = new StubTestScriptProvider();
        StubTestScriptProvider fallback = new StubTestScriptProvider();
        fallback.defaults.put(TestScript.Type.POST_REQUEST, StubTestScriptProvider.script("legacy", TestScript.Type.POST_REQUEST, "legacy();"));

        FallbackTestScriptProvider provider = new FallbackTestScriptProvider(primary, fallback);
        assertEquals("legacy", provider.findDefault(TestScript.Type.POST_REQUEST).getId());
        assertNull(provider.findById("missing"));
    }

    @Test
    public void primaryFailurePropagatesWithoutFallback() {
        StubTestScriptProvider primary = new StubTestScriptProvider();
        StubTestScriptProvider fallback = new StubTestScriptProvider();
        primary.failWith = new IllegalStateException("db down");
        fallback.defaults.put(TestScript.Type.PRE_REQUEST, StubTestScriptProvider.script("legacy", TestScript.Type.PRE_REQUEST, "legacy();"));

        FallbackTestScriptProvider provider = new FallbackTestScriptProvider(primary, fallback);
        assertThrows(IllegalStateException.class, () -> provider.findDefault(TestScript.Type.PRE_REQUEST));
        assertEquals(0, fallback.defaultCalls);
    }
}

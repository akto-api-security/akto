package com.akto.testing.scripts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.akto.dto.testing.config.TestScript;

public class CachingTestScriptProviderTest {

    private final AtomicInteger now = new AtomicInteger(1000);
    private final AtomicInteger account = new AtomicInteger(1);

    private CachingTestScriptProvider cache(StubTestScriptProvider delegate, int max) {
        return new CachingTestScriptProvider(delegate, max, 300, now::get, account::get);
    }

    @Test
    public void hitsAndAbsencesAreCachedUntilTtl() {
        StubTestScriptProvider delegate = new StubTestScriptProvider();
        delegate.byId.put("s1", StubTestScriptProvider.script("s1", TestScript.Type.PRE_REQUEST, "a();"));
        CachingTestScriptProvider provider = cache(delegate, 100);

        provider.findById("s1");
        provider.findById("s1");
        assertNull(provider.findDefault(TestScript.Type.PRE_REQUEST));
        assertNull(provider.findDefault(TestScript.Type.PRE_REQUEST));
        assertEquals(1, delegate.byIdCalls);
        assertEquals(1, delegate.defaultCalls);

        now.addAndGet(300);
        provider.findById("s1");
        provider.findDefault(TestScript.Type.PRE_REQUEST);
        assertEquals(2, delegate.byIdCalls);
        assertEquals(2, delegate.defaultCalls);
    }

    @Test
    public void exceptionsAreNeverCached() {
        StubTestScriptProvider delegate = new StubTestScriptProvider();
        delegate.failWith = new IllegalStateException("db down");
        CachingTestScriptProvider provider = cache(delegate, 100);

        assertThrows(IllegalStateException.class, () -> provider.findById("s1"));
        assertEquals(0, provider.size());

        delegate.failWith = null;
        delegate.byId.put("s1", StubTestScriptProvider.script("s1", TestScript.Type.PRE_REQUEST, "a();"));
        assertEquals("s1", provider.findById("s1").getId());
        assertEquals(2, delegate.byIdCalls);
    }

    @Test
    public void keysAreScopedPerAccount() {
        StubTestScriptProvider delegate = new StubTestScriptProvider();
        CachingTestScriptProvider provider = cache(delegate, 100);

        provider.findDefault(TestScript.Type.PRE_REQUEST);
        account.set(2);
        provider.findDefault(TestScript.Type.PRE_REQUEST);
        assertEquals(2, delegate.defaultCalls);
    }

    @Test
    public void sizeStaysBounded() {
        StubTestScriptProvider delegate = new StubTestScriptProvider();
        CachingTestScriptProvider provider = cache(delegate, 3);
        for (int i = 0; i < 50; i++) {
            provider.findById("id-" + i);
        }
        assertEquals(true, provider.size() <= 3);
    }
}

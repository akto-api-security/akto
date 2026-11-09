package com.akto.testing.scripts;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import com.akto.dao.context.Context;
import com.akto.dto.testing.config.TestScript;

/**
 * Bounded, TTL cache in front of another provider, keyed per account. Caches found scripts and
 * "not found" (most accounts have no default script, and every test request asks). Never caches an
 * exception: the next request retries. No I/O happens while touching the map.
 * When the map is full it is cleared; scripts are few per account, so this only triggers under
 * an unexpected key explosion and costs one refetch per live key.
 */
public class CachingTestScriptProvider implements TestScriptProvider {

    public static final int DEFAULT_MAX_ENTRIES = 10_000;
    public static final int DEFAULT_TTL_SECONDS = 5 * 60;

    private static final class Entry {
        final TestScript script;
        final int fetchedAt;

        Entry(TestScript script, int fetchedAt) {
            this.script = script;
            this.fetchedAt = fetchedAt;
        }
    }

    private final TestScriptProvider delegate;
    private final int maxEntries;
    private final int ttlSeconds;
    private final IntSupplier nowSeconds;
    private final IntSupplier accountId;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public CachingTestScriptProvider(TestScriptProvider delegate) {
        this(delegate, DEFAULT_MAX_ENTRIES, DEFAULT_TTL_SECONDS, Context::now, () -> Context.accountId.get());
    }

    CachingTestScriptProvider(TestScriptProvider delegate, int maxEntries, int ttlSeconds,
            IntSupplier nowSeconds, IntSupplier accountId) {
        this.delegate = delegate;
        this.maxEntries = maxEntries;
        this.ttlSeconds = ttlSeconds;
        this.nowSeconds = nowSeconds;
        this.accountId = accountId;
    }

    @Override
    public TestScript findById(String id) {
        return cached("id:" + id, () -> delegate.findById(id));
    }

    @Override
    public TestScript findDefault(TestScript.Type type) {
        return cached("default:" + type.name(), () -> delegate.findDefault(type));
    }

    int size() {
        return cache.size();
    }

    private TestScript cached(String suffix, Supplier<TestScript> loader) {
        String key = accountId.getAsInt() + "|" + suffix;
        int now = nowSeconds.getAsInt();
        Entry entry = cache.get(key);
        if (entry != null && now - entry.fetchedAt < ttlSeconds) {
            return entry.script;
        }
        // load outside any map operation; an exception propagates and nothing is stored
        TestScript script = loader.get();
        if (cache.size() >= maxEntries) {
            cache.clear();
        }
        cache.put(key, new Entry(script, now));
        return script;
    }
}
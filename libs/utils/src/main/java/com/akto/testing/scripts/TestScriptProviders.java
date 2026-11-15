package com.akto.testing.scripts;

/**
 * Wires the provider chain for runtimes that read Mongo directly (dashboard, apps/testing):
 * library first, legacy store only when the library has nothing (accounts not migrated yet).
 */
public final class TestScriptProviders {

    private static final TestScriptProvider DATABASE =
        new FallbackTestScriptProvider(new LibraryTestScriptProvider(), new LegacyTestScriptAdapter());

    private static final TestScriptProvider CACHED_DATABASE = new CachingTestScriptProvider(DATABASE);

    private TestScriptProviders() {}

    /** Uncached; for request paths that must see the latest state (e.g. the database-abstractor). */
    public static TestScriptProvider database() {
        return DATABASE;
    }

    /** Cached for the testing hot path; script edits become visible within the cache TTL. */
    public static TestScriptProvider cachedDatabase() {
        return CACHED_DATABASE;
    }
}

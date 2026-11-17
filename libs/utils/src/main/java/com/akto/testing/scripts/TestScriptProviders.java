package com.akto.testing.scripts;

import java.util.function.BooleanSupplier;

/**
 * Wires the provider chain for runtimes that read Mongo directly (dashboard, apps/testing):
 * library first, legacy store only when the library has nothing (accounts not migrated yet).
 */
public final class TestScriptProviders {

    /** Set to "false" on the database-abstractor to stop serving Ask Akto validation drafts to mini-testing. */
    static final String SERVE_DRAFTS_ENV = "AKTO_SERVE_SCRIPT_DRAFTS";

    private static final TestScriptProvider DATABASE =
        new FallbackTestScriptProvider(new LibraryTestScriptProvider(), new LegacyTestScriptAdapter());

    private static final TestScriptProvider CACHED_DATABASE = new CachingTestScriptProvider(DATABASE);

    private static final TestScriptProvider SERVED = served(
        new LibraryTestScriptProvider(), new DraftTestScriptProvider(), new LegacyTestScriptAdapter(),
        TestScriptProviders::draftsServed);

    private TestScriptProviders() {}

    /** Uncached; for request paths that must see the latest state (e.g. the database-abstractor). */
    public static TestScriptProvider database() {
        return DATABASE;
    }

    /** Cached for the testing hot path; script edits become visible within the cache TTL. */
    public static TestScriptProvider cachedDatabase() {
        return CACHED_DATABASE;
    }

    /**
     * What the database-abstractor serves to mini-testing by id: library, then Ask Akto validation drafts
     * (the Validate replay in a mini-testing module fetches its unsaved scripts this way), then legacy.
     * Drafts sit behind AKTO_SERVE_SCRIPT_DRAFTS. Mini-testing builds that predate the draft sandbox run
     * what they are served without it (validation/CLAUDE.md in the akto repo).
     */
    public static TestScriptProvider served() {
        return SERVED;
    }

    static TestScriptProvider served(TestScriptProvider library, TestScriptProvider drafts, TestScriptProvider legacy,
                                     BooleanSupplier draftsOn) {
        return new FallbackTestScriptProvider(library,
            new FallbackTestScriptProvider(new SwitchedTestScriptProvider("drafts", drafts, draftsOn), legacy));
    }

    /** On unless explicitly "false": an unset or garbled value keeps today's behaviour for validation. */
    static boolean draftsServed(String envValue) {
        return envValue == null || !"false".equalsIgnoreCase(envValue.trim());
    }

    private static boolean draftsServed() {
        return draftsServed(System.getenv(SERVE_DRAFTS_ENV));
    }
}

package com.akto.testing.scripts;

import com.akto.dao.context.Context;
import com.akto.data_actor.DataActorFactory;

/**
 * Provider wiring for mini-testing. Mirrors TestScriptProviders in the main akto repo:
 * database()      - Mongo reads (DbLayer, when this process talks to Mongo directly): library, then legacy.
 * cachedRemote()  - the testing hot path: DataActor reads behind the bounded TTL cache, keyed by the
 *                   actual account id (mini-testing's ApiExecutorUtil uses Context.getActualAccountId()).
 */
public final class TestScriptProviders {

    private static final TestScriptProvider DATABASE =
        new FallbackTestScriptProvider(new LibraryTestScriptProvider(), new LegacyTestScriptAdapter());

    private static volatile TestScriptProvider cachedRemote;

    private TestScriptProviders() {}

    public static TestScriptProvider database() {
        return DATABASE;
    }

    public static TestScriptProvider cachedRemote() {
        if (cachedRemote == null) {
            synchronized (TestScriptProviders.class) {
                if (cachedRemote == null) {
                    cachedRemote = new CachingTestScriptProvider(
                        new RemoteTestScriptProvider(DataActorFactory.fetchInstance()),
                        CachingTestScriptProvider.DEFAULT_MAX_ENTRIES,
                        CachingTestScriptProvider.DEFAULT_TTL_SECONDS,
                        Context::now,
                        Context::getActualAccountId);
                }
            }
        }
        return cachedRemote;
    }
}

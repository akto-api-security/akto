package com.akto.behaviour_modelling;

import com.akto.behaviour_modelling.core.WindowAccumulator;
import com.akto.behaviour_modelling.model.TransitionKey;
import com.akto.behaviour_modelling.model.UserSessionState;
import com.akto.behaviour_modelling.model.WindowSnapshot;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.ApiInfoCatalog;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.type.APICatalog;
import com.akto.dto.type.URLStatic;
import com.akto.dto.type.URLTemplate;
import com.akto.hybrid_runtime.APICatalogSync;
import com.akto.hybrid_runtime.policies.AktoPolicyNew;

import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class SessionAnalyzer {

    private final SequenceAnalyzerConfig config;

    // Replaced atomically on each window flip. Volatile ensures all threads
    // see the new reference immediately without locking the hot path.
    private volatile WindowAccumulator currentAccumulator;

    // Tracks when the current window started. Per-user state older than this
    // is lazily reset on next access — no sweep needed.
    private final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());

    // Minimal per-user state: just the recent API deque for transition detection.
    private final ConcurrentHashMap<String, UserSessionState> userStates = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "session-analyzer-window-flipper");
        t.setDaemon(true);
        return t;
    });

    public SessionAnalyzer(SequenceAnalyzerConfig config) {
        this.config = config;
        this.currentAccumulator = config.getAccumulatorFactory().get();

        scheduler.scheduleAtFixedRate(
                this::onWindowEnd,
                config.getWindowDurationMs(),
                config.getWindowDurationMs(),
                TimeUnit.MILLISECONDS
        );
    }

    /**
     * Process a single HTTP record. This is the hot path — must stay low-latency.
     */
    public void process(HttpResponseParams record) {
        // Normalize raw URL to templatized ApiInfoKey (e.g. /users/123 → /users/INTEGER).
        // Returns early if the URL is not yet known in the catalog.
        AktoPolicyNew aktoPolicyNew = config.getAktoPolicyNew();
        ApiInfoKey apiKey = AktoPolicyNew.generateFromHttpResponseParams(record, aktoPolicyNew.isMergeUrlsOnVersions());

        // Skip if this API is not yet cataloged — keeps the model bounded to known APIs.
        // Check directly against apiInfoCatalogMap to avoid the side effect of
        // getApiInfoFromMap(), which inserts a new entry for unknown URLs.
        ApiInfoCatalog apiInfoCatalog = aktoPolicyNew.getApiInfoCatalogMap().get(apiKey.getApiCollectionId());
        if (apiInfoCatalog == null) return;

        boolean known;
        if (APICatalog.isTemplateUrl(apiKey.getUrl())) {
            URLTemplate urlTemplate = APICatalogSync.createUrlTemplate(apiKey.getUrl(), apiKey.getMethod());
            known = apiInfoCatalog.getTemplateURLToMethods().containsKey(urlTemplate);
        } else {
            known = apiInfoCatalog.getStrictURLToMethods().containsKey(
                    new URLStatic(apiKey.getUrl(), apiKey.getMethod()));
        }
        if (!known) return;

        String userId = config.getUserIdentifier().extractUserId(record);
        long now = System.currentTimeMillis();
        long currentWindowStart = windowStart.get();

        // Snapshot the accumulator reference once — window flip may swap it
        // mid-call, but LongAdder writes are safe regardless.
        WindowAccumulator accumulator = currentAccumulator;

        UserSessionState state = userStates.computeIfAbsent(userId, k -> new UserSessionState(now));

        synchronized (state) {
            // Lazy reset: if this user's session predates the current window, clear context.
            if (state.getSessionStart() < currentWindowStart) {
                state.reset(now);
            }

            Deque<ApiInfoKey> recentApis = state.getRecentApis();

            // Emit transitions for all orders 1..maxOrder where we have enough context.
            // Order N requires N prior APIs in the deque.
            int maxOrder = config.getMaxOrder();
            for (int order = 1; order <= maxOrder; order++) {
                if (recentApis.size() >= order) {
                    accumulator.recordTransition(buildTransitionKeyForOrder(recentApis, apiKey, order), userId);
                }
            }

            accumulator.recordApiCall(apiKey, userId);

            // Maintain deque at max size (maxOrder).
            recentApis.addLast(apiKey);
            if (recentApis.size() > maxOrder) {
                recentApis.removeFirst();
            }
        }
    }

    /**
     * Called at the end of each window. Swaps to a fresh accumulator, then
     * flushes the completed window asynchronously so the hot path is unblocked.
     */
    private void onWindowEnd() {
        long now = System.currentTimeMillis();
        long oldWindowStart = windowStart.getAndSet(now);

        // Swap accumulator first so new writes go to the fresh one immediately.
        WindowAccumulator old = currentAccumulator;
        currentAccumulator = config.getAccumulatorFactory().get();

        WindowSnapshot snapshot = old.snapshot(oldWindowStart, now);

        if (!snapshot.isEmpty()) {
            CompletableFuture.runAsync(() -> config.getFlusher().flush(snapshot));
        }
    }

    public void shutdown() {
        scheduler.shutdown();
        // Flush whatever is in the current window before exiting.
        onWindowEnd();
    }

    /**
     * Forces an immediate window end. For testing only — avoids sleeping for
     * the full window duration in integration tests.
     */
    void triggerWindowEnd() {
        onWindowEnd();
    }

    /**
     * Builds a TransitionKey of length (order + 1) from the last `order` items
     * in the deque plus the current API.
     *
     * Example: deque=[A, B, C, D], currentApi=E, order=2
     *   → takes last 2 from deque: [C, D]
     *   → result: [C, D, E] (order-2 transition)
     */
    private TransitionKey buildTransitionKeyForOrder(Deque<ApiInfoKey> recentApis, ApiInfoKey currentApi, int order) {
        ApiInfoKey[] sequence = new ApiInfoKey[order + 1];

        // Iterate from the end of the deque, taking last `order` items
        int skip = recentApis.size() - order;
        int i = 0;
        Iterator<ApiInfoKey> it = recentApis.iterator();
        while (it.hasNext()) {
            ApiInfoKey key = it.next();
            if (skip > 0) {
                skip--;
                continue;
            }
            sequence[i++] = key;
        }
        sequence[i] = currentApi;
        return new TransitionKey(sequence);
    }
}

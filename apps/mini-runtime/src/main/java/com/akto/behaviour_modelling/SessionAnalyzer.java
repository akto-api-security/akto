package com.akto.behaviour_modelling;

import com.akto.behaviour_modelling.core.WindowAccumulator;
import com.akto.behaviour_modelling.model.TransitionKey;
import com.akto.behaviour_modelling.model.UserSessionState;
import com.akto.behaviour_modelling.model.WindowSnapshot;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.type.URLMethods;

import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class SessionAnalyzer {

    private final SequenceAnalyzerConfig config;
    private final ApiRegistry registry;

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

    public SessionAnalyzer(SequenceAnalyzerConfig config, ApiRegistry registry) {
        this.config = config;
        this.registry = registry;
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
        HttpRequestParams req = record.getRequestParams();
        if (req == null) return;

        ApiInfoKey apiKey = new ApiInfoKey(
                req.getApiCollectionId(),
                req.getUrl(),
                resolveMethod(req.getMethod())
        );

        int apiId = registry.resolve(apiKey);
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

            Deque<Integer> recentApis = state.getRecentApis();

            // Only emit a transition when we have a full context window (sequenceLength - 1 prior APIs).
            if (recentApis.size() == config.getSequenceLength() - 1) {
                accumulator.recordTransition(buildTransitionKey(recentApis, apiId), userId);
            }

            accumulator.recordApiCall(apiId, userId);

            // Maintain deque at max size (sequenceLength - 1).
            recentApis.addLast(apiId);
            if (recentApis.size() >= config.getSequenceLength()) {
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

    private TransitionKey buildTransitionKey(Deque<Integer> recentApis, int currentApiId) {
        int[] sequence = new int[recentApis.size() + 1];
        int i = 0;
        for (int id : recentApis) {
            sequence[i++] = id;
        }
        sequence[i] = currentApiId;
        return new TransitionKey(sequence);
    }

    private static URLMethods.Method resolveMethod(String method) {
        if (method == null || method.isEmpty()) return URLMethods.Method.OTHER;
        try {
            return URLMethods.Method.valueOf(method.toUpperCase());
        } catch (IllegalArgumentException e) {
            return URLMethods.Method.OTHER;
        }
    }
}

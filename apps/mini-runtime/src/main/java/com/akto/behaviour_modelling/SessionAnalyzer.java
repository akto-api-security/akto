package com.akto.behaviour_modelling;

import com.akto.behaviour_modelling.model.SequenceKey;
import com.akto.behaviour_modelling.model.SessionState;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.ApiInfoCatalog;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.SequenceCount;
import com.akto.dto.type.APICatalog;
import com.akto.dto.type.URLStatic;
import com.akto.dto.type.URLTemplate;
import com.akto.hybrid_runtime.APICatalogSync;
import com.akto.hybrid_runtime.policies.AktoPolicyNew;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class SessionAnalyzer {

    private static final LoggerMaker loggerMaker = new LoggerMaker(SessionAnalyzer.class, LogDb.RUNTIME);
    private static final int HOUR_SECONDS = 3600;

    private final SequenceAnalyzerConfig config;
    private final int idleSeconds;

    private final ConcurrentHashMap<ApiInfoKey, Integer> endpointIds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, ApiInfoKey> endpointsById = new ConcurrentHashMap<>();
    private final AtomicInteger nextEndpointId = new AtomicInteger();

    private final ConcurrentHashMap<String, SessionState> sessions = new ConcurrentHashMap<>();
    private final AtomicLong eventWatermark = new AtomicLong();
    private volatile long lastEventWallMs;

    private volatile ConcurrentHashMap<SequenceKey, LongAdder> counts = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "session-analyzer");
        t.setDaemon(true);
        return t;
    });

    public SessionAnalyzer(SequenceAnalyzerConfig config) {
        this.config = config;
        this.idleSeconds = (int) (config.getSessionIdleMs() / 1000);
        scheduler.scheduleAtFixedRate(this::safeFlush, config.getFlushIntervalMs(),
                config.getFlushIntervalMs(), TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::safeEvict, config.getEvictionIntervalMs(),
                config.getEvictionIntervalMs(), TimeUnit.MILLISECONDS);
    }

    public void process(HttpResponseParams record) {
        AktoPolicyNew aktoPolicyNew = config.getAktoPolicyNew();
        ApiInfoKey apiKey = AktoPolicyNew.generateFromHttpResponseParams(record, aktoPolicyNew.isMergeUrlsOnVersions());

        // Check apiInfoCatalogMap directly: getApiInfoFromMap() inserts unknown URLs.
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

        String sessionId = config.getUserIdentifier().extractUserId(record);
        if (sessionId == null) return;

        int endpointId = endpointIds.computeIfAbsent(apiKey, this::registerEndpoint);
        long nowMs = config.getClockMs().getAsLong();
        int nowSec = (int) (nowMs / 1000);
        int eventSec = record.getTime() > 0 ? record.getTime() : nowSec;
        eventWatermark.accumulateAndGet(Math.min(eventSec, nowSec), Math::max);
        lastEventWallMs = nowMs;

        SessionState state = sessions.computeIfAbsent(sessionId, k -> new SessionState(config.getMaxHistory()));
        synchronized (state) {
            if (state.size() > 0 && eventSec - state.getLastSeen() > idleSeconds) {
                state.clear();
            }
            if (eventSec > state.getLastSeen()) {
                state.setLastSeen(eventSec);
            }
            if (state.size() > 0 && state.recent(1) == endpointId) return;

            int bucketStart = eventSec - eventSec % HOUR_SECONDS;
            ConcurrentHashMap<SequenceKey, LongAdder> accumulator = counts;
            int maxOrder = Math.min(config.getNMax(), state.size());
            for (int k = 0; k <= maxOrder; k++) {
                int[] ids = new int[k + 1];
                for (int i = 0; i < k; i++) {
                    ids[i] = state.recent(k - i);
                }
                ids[k] = endpointId;
                accumulator.computeIfAbsent(new SequenceKey(bucketStart, ids), x -> new LongAdder()).increment();
            }
            state.add(endpointId);
        }
    }

    private Integer registerEndpoint(ApiInfoKey key) {
        int id = nextEndpointId.getAndIncrement();
        endpointsById.put(id, key);
        return id;
    }

    private void flush() {
        ConcurrentHashMap<SequenceKey, LongAdder> old = counts;
        counts = new ConcurrentHashMap<>();
        if (old.isEmpty()) return;

        List<SequenceCount> out = new ArrayList<>(old.size());
        for (Map.Entry<SequenceKey, LongAdder> entry : old.entrySet()) {
            int[] ids = entry.getKey().getIds();
            List<String> context = new ArrayList<>(ids.length - 1);
            for (int i = 0; i < ids.length - 1; i++) {
                context.add(endpointsById.get(ids[i]).toString());
            }
            ApiInfoKey next = endpointsById.get(ids[ids.length - 1]);
            out.add(new SequenceCount(next.getApiCollectionId(), context, next.toString(),
                    entry.getKey().getBucketStart(), entry.getValue().sum()));
        }
        config.getSink().accept(out);
    }

    private void evictIdleSessions() {
        // Event time extrapolated by wall time since the last record: survives backlog replay and idle traffic.
        long effectiveNowSec = eventWatermark.get() + (config.getClockMs().getAsLong() - lastEventWallMs) / 1000;
        long cutoff = effectiveNowSec - idleSeconds;
        sessions.entrySet().removeIf(e -> e.getValue().getLastSeen() < cutoff);
    }

    private void safeFlush() {
        try {
            flush();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error flushing sequence counts: " + e.getMessage(), LogDb.RUNTIME);
        }
    }

    private void safeEvict() {
        try {
            evictIdleSessions();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error evicting sessions: " + e.getMessage(), LogDb.RUNTIME);
        }
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        safeFlush();
    }

    void triggerFlush() {
        flush();
    }

    void triggerEviction() {
        evictIdleSessions();
    }

    int activeSessionCount() {
        return sessions.size();
    }
}

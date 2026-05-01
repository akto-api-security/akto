package com.akto.threat.detection.cache;

import com.akto.data_actor.DataActor;
import com.akto.dto.ApiSequences;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * In-memory cache of API transition probabilities loaded from the api_sequences collection.
 * Also owns per-actor sequence state for anomaly detection.
 *
 * Transition key: "prevApi|currentApi" where each side is ApiInfoKey.toString().
 * Refreshes every 10 minutes (matching the mini-runtime sequence window).
 */
public class SequenceCache {

    private static final LoggerMaker logger = new LoggerMaker(SequenceCache.class, LogDb.THREAT_DETECTION);

    private static final int REFRESH_INTERVAL_SEC = 10 * 60;
    private static final float PROBABILITY_THRESHOLD = 0.05f;
    private static final int ANOMALY_COUNT_THRESHOLD = 5;
    private static final int MIN_CACHE_SIZE = 50;

    private final DataActor dataActor;

    // Key: "prevApi|currentApi", Value: transition probability
    private Map<String, Float> transitionProbabilities = new HashMap<>();
    private int lastUpdatedAt = 0;

    // actor → last API key seen; TTL 10 min matches sequence window
    private final Cache<String, String> actorLastApi = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    // actor → consecutive anomalous transition count; TTL 10 min
    private final Cache<String, Integer> actorAnomalyCount = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    public SequenceCache(DataActor dataActor) {
        this.dataActor = dataActor;
    }

    /**
     * Evaluates whether the actor's current API call is part of an anomalous sequence.
     * Updates per-actor state (last API, anomaly counter).
     *
     * Returns true when the consecutive anomaly count reaches ANOMALY_COUNT_THRESHOLD,
     * indicating an alert should be fired. Counter resets after returning true,
     * and also resets on any normal (expected) transition.
     */
    public boolean checkSequenceAnomaly(String actor, String currentApiKey) {
        if (transitionProbabilities.size() < MIN_CACHE_SIZE) return false;

        String prevApiKey = actorLastApi.getIfPresent(actor);
        actorLastApi.put(actor, currentApiKey);

        if (prevApiKey == null) return false;

        Float probability = transitionProbabilities.get(prevApiKey + "|" + currentApiKey);
        boolean isAnomalous = probability == null || probability < PROBABILITY_THRESHOLD;

        if (!isAnomalous) return false;

        int count = actorAnomalyCount.get(actor, k -> 0) + 1;
        actorAnomalyCount.put(actor, count);

        if (count >= ANOMALY_COUNT_THRESHOLD) {
            actorAnomalyCount.invalidate(actor);
            return true;
        }

        return false;
    }

    public int size() {
        return transitionProbabilities.size();
    }

    public void refresh() {
        int now = (int) (System.currentTimeMillis() / 1000);
        if (!transitionProbabilities.isEmpty() && now - lastUpdatedAt < REFRESH_INTERVAL_SEC) {
            return;
        }

        try {
            List<ApiSequences> sequences = dataActor.fetchApiSequences();
            Map<String, Float> updated = new HashMap<>();
            for (ApiSequences seq : sequences) {
                List<String> paths = seq.getPaths();
                if (paths == null || paths.size() < 2) continue;
                String prevApi = paths.get(0);
                String currentApi = paths.get(paths.size() - 1);
                updated.put(prevApi + "|" + currentApi, seq.getProbability());
            }
            transitionProbabilities = updated;
            lastUpdatedAt = now;
            logger.infoAndAddToDb("SequenceCache refreshed: " + transitionProbabilities.size() + " transitions loaded");
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error refreshing SequenceCache: " + e.getMessage());
        }
    }
}

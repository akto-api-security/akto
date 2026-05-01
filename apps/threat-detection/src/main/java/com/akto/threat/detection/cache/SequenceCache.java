package com.akto.threat.detection.cache;

import com.akto.data_actor.DataActor;
import com.akto.dto.ApiSequences;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory cache of API transition probabilities loaded from the api_sequences collection.
 *
 * Key: "prevApi|currentApi" where each API is ApiInfoKey.toString() format.
 * Refreshes every 10 minutes (matching the mini-runtime sequence window).
 * Returns null for unknown transitions (never seen in training data).
 */
public class SequenceCache {

    private static final LoggerMaker logger = new LoggerMaker(SequenceCache.class, LogDb.THREAT_DETECTION);
    private static final int REFRESH_INTERVAL_SEC = 10 * 60;

    private final DataActor dataActor;

    // Key: "prevApi|currentApi", Value: transition probability
    private Map<String, Float> transitionProbabilities = new HashMap<>();
    private int lastUpdatedAt = 0;

    public SequenceCache(DataActor dataActor) {
        this.dataActor = dataActor;
    }

    /**
     * Returns the probability of transitioning from prevApi to currentApi,
     * or null if this transition has never been observed.
     *
     * Both prevApi and currentApi should be ApiInfoKey.toString() values.
     */
    public Float getProbability(String prevApi, String currentApi) {
        refreshIfStale();
        return transitionProbabilities.get(prevApi + "|" + currentApi);
    }

    public int size() {
        return transitionProbabilities.size();
    }

    private void refreshIfStale() {
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

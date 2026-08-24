package com.akto.agent_risk;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Process-local LRU + TTL. Duplicate work after restart/across pods is fine.
 */
public class RiskScoreCache {

    private static final int MAX_ENTRIES = 100_000;
    private static final long TTL_MS = 24L * 60 * 60 * 1000;
    private static final RiskScoreCache INSTANCE = new RiskScoreCache();

    public static RiskScoreCache instance() {
        return INSTANCE;
    }

    private static final class Entry {
        final AgentRiskScore score;
        final long expiresAt;

        Entry(AgentRiskScore score, long expiresAt) {
            this.score = score;
            this.expiresAt = expiresAt;
        }
    }

    private final Map<String, Entry> map = new LinkedHashMap<String, Entry>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    public synchronized AgentRiskScore get(int accountId, String hash) {
        String key = accountId + ":" + hash;
        Entry e = map.get(key);
        if (e == null) {
            return null;
        }
        if (System.currentTimeMillis() > e.expiresAt) {
            map.remove(key);
            return null;
        }
        return e.score;
    }

    public synchronized void put(int accountId, String hash, AgentRiskScore score) {
        map.put(accountId + ":" + hash, new Entry(score, System.currentTimeMillis() + TTL_MS));
    }
}

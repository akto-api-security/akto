package com.akto.data_actor;

import com.akto.dao.NewRelicIntegrationDao;
import com.akto.dao.OpenTelemetryIntegrationDao;
import com.mongodb.BasicDBObject;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches "does this account have a New Relic / OpenTelemetry integration configured?".
 *
 * Both answers used to be re-read from Mongo on every ingestMetricsData and
 * updateModuleInfo call - two round trips per batch from every guardrails,
 * agent-guard and traffic-collector instance, for config that changes rarely.
 *
 * Entries refresh lazily, at most once per REFRESH_INTERVAL_MS per account. The
 * DAOs resolve their database from Context.accountId, so callers must already
 * have the context set for the account they pass in.
 */
public class TelemetryIntegrationCache {

    private static final long REFRESH_INTERVAL_MS = 5 * 60 * 1000;

    private static class Entry {
        final boolean newRelic;
        final boolean openTelemetry;
        final long fetchedAtMs;

        Entry(boolean newRelic, boolean openTelemetry, long fetchedAtMs) {
            this.newRelic = newRelic;
            this.openTelemetry = openTelemetry;
            this.fetchedAtMs = fetchedAtMs;
        }
    }

    private final ConcurrentHashMap<Integer, Entry> cache = new ConcurrentHashMap<>();

    public boolean hasNewRelicIntegration(int accountId) {
        return get(accountId).newRelic;
    }

    public boolean hasOpenTelemetryIntegration(int accountId) {
        return get(accountId).openTelemetry;
    }

    /*
     * A racing caller may refresh the same account concurrently. That is two
     * wasted reads at worst, never a wrong answer, so it is left unsynchronized
     * rather than serialising every lookup behind a lock.
     */
    private Entry get(int accountId) {
        long now = System.currentTimeMillis();
        Entry entry = cache.get(accountId);
        if (entry != null && now - entry.fetchedAtMs < REFRESH_INTERVAL_MS) {
            return entry;
        }

        Entry refreshed = new Entry(
                NewRelicIntegrationDao.instance.findOne(new BasicDBObject()) != null,
                OpenTelemetryIntegrationDao.instance.findOne(new BasicDBObject()) != null,
                now);
        cache.put(accountId, refreshed);
        return refreshed;
    }
}

package com.akto.detection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Remembers what the external classifier decided about a parameter, so the runtime can apply that
 * decision to later values on the same parameter without asking again.
 *
 * Keyed by the parameter rather than by the value on purpose. Values like customer emails are
 * effectively unique per request, so a value-keyed memory would never be hit twice and every single
 * request would need a fresh answer. What actually needs classifying is the parameter: once we know
 * that {@code POST /booking/create -> guest#email} carries customer emails, the ten millionth email
 * seen there teaches us nothing new.
 *
 * A parameter the classifier declined to refine is remembered too, under {@link #NO_CORRECTION}, so
 * that a negative answer stops the runtime republishing the same parameter forever.
 *
 * Reads happen on the ingestion thread and writes on the Kafka consumer thread, so the map is
 * synchronized. Entries expire so a parameter is eventually re-examined if its traffic changes.
 */
public class ParamVerdictCache {

    /** Stored when the classifier looked at a parameter and chose not to refine it. */
    public static final String NO_CORRECTION = "__AKTO_NO_CORRECTION__";

    private static class Entry {
        final String label;
        final long insertedAtMs;

        Entry(String label, long insertedAtMs) {
            this.label = label;
            this.insertedAtMs = insertedAtMs;
        }
    }

    private final Map<ParamLocation, Entry> cache;
    private final long ttlMs;

    public ParamVerdictCache(int maxSize, int ttlSeconds) {
        this.ttlMs = ttlSeconds * 1000L;
        final int cap = maxSize;
        this.cache = Collections.synchronizedMap(new LinkedHashMap<ParamLocation, Entry>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<ParamLocation, Entry> eldest) {
                return size() > cap;
            }
        });
    }

    /**
     * The label the classifier gave this parameter, {@link #NO_CORRECTION} if it declined, or null
     * if we have never heard about this parameter or the answer has expired.
     */
    public String get(ParamLocation location) {
        Entry entry = cache.get(location);
        if (entry == null) return null;
        if (System.currentTimeMillis() - entry.insertedAtMs > ttlMs) return null;
        return entry.label;
    }

    public void put(ParamLocation location, String label) {
        cache.put(location, new Entry(label, System.currentTimeMillis()));
    }

    public int size() {
        return cache.size();
    }
}

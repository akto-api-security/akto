package com.akto.detection;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * LRU cache of param-level verdicts: (collectionId, url, method, param) -> correctedType.
 * Once a param is settled with a corrected type, all future values on that param use it
 * without hitting the async service. The runtime consumes verdicts from Kafka and populates this.
 *
 * Thread-safe for reads. Writes (from verdict consumer) happen in a background thread;
 * collisions are fine (last-writer-wins).
 */
public class ParamVerdictCache {

    public static class ParamKey {
        public final int apiCollectionId;
        public final String url;
        public final String method;
        public final String param;

        public ParamKey(int apiCollectionId, String url, String method, String param) {
            this.apiCollectionId = apiCollectionId;
            this.url = url;
            this.method = method;
            this.param = param;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ParamKey)) return false;
            ParamKey p = (ParamKey) o;
            return apiCollectionId == p.apiCollectionId
                    && url.equals(p.url)
                    && method.equals(p.method)
                    && param.equals(p.param);
        }

        @Override
        public int hashCode() {
            return Objects.hash(apiCollectionId, url, method, param);
        }
    }

    private final Map<ParamKey, String> cache;
    private final int maxSize;

    public ParamVerdictCache(int maxSize) {
        this.maxSize = maxSize;
        // Access-order LinkedHashMap for LRU eviction
        this.cache = new LinkedHashMap<ParamKey, String>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<ParamKey, String> eldest) {
                return size() > maxSize;
            }
        };
    }

    /**
     * Get the corrected type for a param, or null if not in cache.
     */
    public String getVerdict(int apiCollectionId, String url, String method, String param) {
        ParamKey key = new ParamKey(apiCollectionId, url, method, param);
        return cache.get(key);
    }

    /**
     * Store a verdict.
     */
    public void putVerdict(int apiCollectionId, String url, String method, String param, String correctedType) {
        ParamKey key = new ParamKey(apiCollectionId, url, method, param);
        cache.put(key, correctedType);
    }

    public int size() { return cache.size(); }
    public void clear() { cache.clear(); }
}

package com.akto.data_actor;

import com.akto.dto.type.SingleTypeInfo;
import com.akto.log.LoggerMaker;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntSupplier;

/*
 * Host-header STIs rarely change between refreshes of the mini-runtime, so instead of downloading all of them
 * every time, their raw JSON is kept (one instance per account) and a refresh only downloads what changed.
 * Anything that cannot be shown to match a full fetch falls back to one, and a full fetch is done at least
 * once every FULL_FETCH_INTERVAL_SECONDS.
 */
public class HostStiSync {

    private static final LoggerMaker loggerMaker = new LoggerMaker(HostStiSync.class);

    // lastSeen is the traffic time and reaches the DB a little later, so look back this far from the previous fetch
    static final int LAST_SEEN_LOOKBACK_SECONDS = 60 * 60;
    static final int FULL_FETCH_INTERVAL_SECONDS = 24 * 60 * 60;
    static final int MAX_CACHED_STIS = 100_000;

    // rawStis is the raw JSON of the STIs keyed by _id hex. complete is false if the walk ended early, i.e. stis is
    // only what was fetched before it did
    public static class FullFetch {
        final List<SingleTypeInfo> stis;
        final Map<String, String> rawStis;
        final boolean complete;

        public FullFetch(List<SingleTypeInfo> stis, Map<String, String> rawStis, boolean complete) {
            this.stis = stis;
            this.rawStis = rawStis;
            this.complete = complete;
        }
    }

    public interface Source {
        FullFetch fetchAll();

        // raw JSON of the STIs seen at or after lastSeenGte, keyed by _id hex; null if the list cannot be trusted to be complete
        Map<String, String> fetchSeenSince(int lastSeenGte);

        // raw JSON of the STIs created after the _id hex lastStiId (null = from the start); null if the list cannot be trusted to be complete
        Map<String, String> fetchCreatedAfter(String lastStiId);

        // how many host-header STIs the DB holds, -1 if it cannot say
        long countStis();

        // null if any of the STIs cannot be parsed
        List<SingleTypeInfo> parseStis(Collection<String> rawStis);
    }

    private final Source source;
    private final IntSupplier clock;
    // keyed by _id hex, so iteration order is the _id order the DB pages in
    private TreeMap<String, String> cachedRawStis = new TreeMap<>();
    // true once cachedRawStis came from a complete full fetch that agreed with the count of the DB
    private boolean cacheTrusted;
    private int lastSeenWatermark;
    private int lastFullFetchTs;

    public HostStiSync(Source source, IntSupplier clock) {
        this.source = source;
        this.clock = clock;
    }

    public synchronized List<SingleTypeInfo> fetch() {
        int fetchStartTs = clock.getAsInt();
        if (cacheTrusted && fetchStartTs - lastFullFetchTs < FULL_FETCH_INTERVAL_SECONDS) {
            List<SingleTypeInfo> refreshedStis = refreshFromCache(fetchStartTs);
            if (refreshedStis != null) {
                return refreshedStis;
            }
        }
        return fetchInFull(fetchStartTs);
    }

    // null whenever the result cannot be shown to equal a full fetch
    private List<SingleTypeInfo> refreshFromCache(int fetchStartTs) {
        Map<String, String> seenRawStis = source.fetchSeenSince(lastSeenWatermark);
        if (seenRawStis == null) {
            return null;
        }

        /*
         * A new STI is found by its _id and not by lastSeen, which is the traffic time, so a new STI can carry
         * an old one (delayed or replayed traffic). Missing it would make the runtime count that API as new
         * again against the usage limit of the plan.
         */
        Map<String, String> createdRawStis = source.fetchCreatedAfter(cachedRawStis.isEmpty() ? null : cachedRawStis.lastKey());
        if (createdRawStis == null) {
            return null;
        }

        TreeMap<String, String> refreshedRawStis = new TreeMap<>(cachedRawStis);
        refreshedRawStis.putAll(seenRawStis);
        refreshedRawStis.putAll(createdRawStis);

        // a deleted or merged STI only shows up as a difference between what is held here and what the DB holds
        long dbStiCount = source.countStis();
        if (dbStiCount != refreshedRawStis.size()) {
            loggerMaker.info("host STI count mismatch, db: " + dbStiCount + " local: " + refreshedRawStis.size() + ", doing a full fetch");
            return null;
        }

        List<SingleTypeInfo> stis = source.parseStis(refreshedRawStis.values());
        if (stis == null) {
            return null;
        }
        cachedRawStis = refreshedRawStis;
        lastSeenWatermark = fetchStartTs - LAST_SEEN_LOOKBACK_SECONDS;
        return stis;
    }

    private List<SingleTypeInfo> fetchInFull(int fetchStartTs) {
        FullFetch fullFetch = source.fetchAll();

        cacheTrusted = false;
        cachedRawStis = new TreeMap<>();
        // only trust a fetch that reached the end of the list and agrees with the count of the DB
        if (fullFetch.complete && fullFetch.rawStis.size() <= MAX_CACHED_STIS && source.countStis() == fullFetch.rawStis.size()) {
            cachedRawStis = new TreeMap<>(fullFetch.rawStis);
            cacheTrusted = true;
            lastFullFetchTs = fetchStartTs;
            lastSeenWatermark = fetchStartTs - LAST_SEEN_LOOKBACK_SECONDS;
        }
        return fullFetch.stis;
    }
}

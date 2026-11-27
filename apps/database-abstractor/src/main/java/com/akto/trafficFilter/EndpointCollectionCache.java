package com.akto.trafficFilter;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.akto.data_actor.DbLayer;
import com.akto.dto.ApiCollection;
import com.akto.dto.traffic.CollectionTags;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;

/**
 * Tells whether an API collection belongs to the ENDPOINT context source (browser LLM, endpoint shield...),
 * i.e. its "source" collection tag is ENDPOINT.
 *
 * Used so the sample-data bloom filter throttle only applies to API security traffic: ENDPOINT
 * collections keep writing every sample. Results are cached per (account, collection) so the hot
 * write path does not hit Mongo for each write.
 */
public class EndpointCollectionCache {

    private static final LoggerMaker loggerMaker = new LoggerMaker(EndpointCollectionCache.class, LogDb.DB_ABS);

    // A collection's tags can arrive just after its first sample, so a "not endpoint" answer is rechecked soon.
    static final int ENDPOINT_TTL_SECONDS = 10 * 60;
    static final int NOT_ENDPOINT_TTL_SECONDS = 60;

    private static final class Entry {
        final boolean endpoint;
        final int expiresAt;

        Entry(boolean endpoint, int expiresAt) {
            this.endpoint = endpoint;
            this.expiresAt = expiresAt;
        }
    }

    private static final ConcurrentMap<String, Entry> cache = new ConcurrentHashMap<>();

    private EndpointCollectionCache() {
    }

    /**
     * Never throws. On a lookup failure it answers true, so the caller skips the throttle and no
     * sample is dropped because of a failed lookup.
     */
    public static boolean isEndpointCollection(int accountId, int apiCollectionId, int now) {
        String key = accountId + "$" + apiCollectionId;
        Entry cached = cache.get(key);
        if (cached != null && cached.expiresAt > now) {
            return cached.endpoint;
        }

        try {
            List<ApiCollection> collections = DbLayer.fetchApiCollectionsByIds(Collections.singletonList(apiCollectionId));
            boolean endpoint = collections != null && !collections.isEmpty()
                    && hasEndpointSourceTag(collections.get(0).getTagsList());
            cache.put(key, new Entry(endpoint, now + (endpoint ? ENDPOINT_TTL_SECONDS : NOT_ENDPOINT_TTL_SECONDS)));
            return endpoint;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "EndpointCollectionCache lookup failed for collection " + apiCollectionId
                    + ", skipping sample data throttle: " + e.getMessage());
            return true;
        }
    }

    static boolean hasEndpointSourceTag(List<CollectionTags> tags) {
        if (tags == null) {
            return false;
        }
        for (CollectionTags tag : tags) {
            if (tag != null
                    && Constants.AKTO_COLLECTION_CONTEXT_TAG_KEY.equals(tag.getKeyName())
                    && CONTEXT_SOURCE.ENDPOINT.name().equalsIgnoreCase(tag.getValue())) {
                return true;
            }
        }
        return false;
    }

    static void clearForTests() {
        cache.clear();
    }
}

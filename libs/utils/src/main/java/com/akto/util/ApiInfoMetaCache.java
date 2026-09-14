package com.akto.util;

import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-process cache for ApiInfo lookups by ApiInfoKey. Mirrors ApiCollectionMetaCache: two per-test
 * hot-path call sites in FilterAction (applyFilterOnAccessType, isAgenticCollection's API_GROUP check)
 * each fetch the same ApiInfo - for its access-type classification or its collection-group membership
 * - on every single test, with no caching, so the same apiInfoKey was re-fetched remotely on every
 * test from every call site. This collapses repeat lookups of the same key to one remote fetch per
 * JVM lifetime, shared across all call sites and all worker threads (the map is static).
 *
 * Access-type classification and collection-group membership are both set at discovery time and not
 * refreshed on a timer, matching the same already-accepted caching policy as ApiCollectionMetaCache.
 *
 * Cached as Optional, not ApiInfo directly: ConcurrentHashMap.computeIfAbsent never stores a null
 * result, so a genuinely-missing/invalid apiInfoKey would otherwise never get cached and would
 * re-fetch remotely on every single lookup, forever. Wrapping in Optional means both hits and misses
 * are recorded after the first lookup.
 */
public class ApiInfoMetaCache {

    private static final DataActor dataActor = DataActorFactory.fetchInstance();
    private static final Map<ApiInfoKey, Optional<ApiInfo>> cache = new ConcurrentHashMap<>();

    private ApiInfoMetaCache() {
    }

    public static ApiInfo get(ApiInfoKey apiInfoKey) {
        return cache.computeIfAbsent(apiInfoKey, key -> Optional.ofNullable(dataActor.fetchApiInfo(key)))
                .orElse(null);
    }

    // Called once per testing run (TestingConfigurations.init()), not left to live for the whole JVM:
    // unlike ApiCollectionMetaCache's tags (set once at discovery, never refreshed), apiAccessTypes and
    // collectionIds genuinely change between runs (runtime reclassifies access type as it sees more
    // traffic; users edit API-group membership) - and mini-testing is long-lived across many runs
    // (Main.java's runModule polls for pending runs in a while(true) loop), so a JVM-lifetime cache here
    // would silently serve stale classifications for as long as the pod lives. Scoping the cache to one
    // run keeps the per-test savings within a run while removing the cross-run staleness window.
    public static void clear() {
        cache.clear();
    }
}

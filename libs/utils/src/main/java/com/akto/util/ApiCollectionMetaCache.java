package com.akto.util;

import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.ApiCollection;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-process cache for ApiCollection metadata lookups by id. Several per-test hot-path call sites
 * (FilterAction.isAgenticCollection, McpSseEndpointHelper.addSseEndpointHeader) each need the same
 * ApiCollection - for its classification tags or its SSE callback URL - on every single test, with
 * no caching previously, so the same apiCollectionId was re-fetched remotely on every test from
 * every call site. This collapses repeat lookups of the same id to one remote fetch per JVM
 * lifetime, shared across all call sites and all worker threads (the map is static).
 *
 * Classification tags are set once at discovery time (see HttpCallParser.updateApiCollectionTags)
 * and not refreshed on a timer, and TestExecutor already caches the same ApiCollection object for a
 * whole run's duration via testingUtil.getApiCollectionMap() - this mirrors that already-accepted
 * caching policy, just for call sites that were missing it.
 *
 * Cached as Optional, not ApiCollection directly: ConcurrentHashMap.computeIfAbsent never stores a
 * null result, so a genuinely-missing/invalid collectionId would otherwise never get cached and
 * would re-fetch remotely on every single lookup, forever. Wrapping in Optional means both hits and
 * misses are recorded after the first lookup.
 */
public class ApiCollectionMetaCache {

    private static final DataActor dataActor = DataActorFactory.fetchInstance();
    private static final Map<Integer, Optional<ApiCollection>> cache = new ConcurrentHashMap<>();

    private ApiCollectionMetaCache() {
    }

    public static ApiCollection get(int apiCollectionId) {
        return cache.computeIfAbsent(apiCollectionId, id -> Optional.ofNullable(dataActor.fetchApiCollectionMeta(id)))
                .orElse(null);
    }
}

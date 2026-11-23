package com.akto.hybrid_runtime;

import com.akto.billing.UsageMetricUtils;
import com.akto.dto.ApiCollection;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.billing.SyncLimit;
import com.akto.dto.usage.MetricTypes;
import com.akto.util.Pair;

/**
 * Limit the mini-runtime applies to new endpoints of a collection, and the metric whose usage to
 * update with the endpoints that were let through (null: nothing to update).
 *  - API / DAST collections: counted per endpoint against ACTIVE_ENDPOINTS.
 *  - Agentic collections (AI_ASSET_COUNT counts collections): no per-endpoint limit. Once the org
 *    is over its limit past the grace period, only collections created after the overage are blocked.
 *  - Endpoint-source collections (ENDPOINT_ASSET_COUNT counts devices): same rule against
 *    ENDPOINT_ASSET_COUNT. A null access (entitlement not configured) means no limit.
 * Asset metrics are measured by the usage cron, so they never get endpoint deltas.
 */
final class SyncLimitPolicy {

    private SyncLimitPolicy() {}

    static Pair<SyncLimit, MetricTypes> forCollection(ApiCollection apiCollection, SyncLimit apiSyncLimit,
            FeatureAccess aiAssetAccess, FeatureAccess endpointAssetAccess) {
        if (apiCollection == null) {
            return new Pair<>(apiSyncLimit, MetricTypes.ACTIVE_ENDPOINTS);
        }

        if (apiCollection.isAgenticCollection()) {
            return new Pair<>(assetLimit(aiAssetAccess, apiCollection), null);
        }

        if (apiCollection.isEndpointCollection()) {
            if (endpointAssetAccess == null) {
                return new Pair<>(SyncLimit.noLimit, null);
            }
            return new Pair<>(assetLimit(endpointAssetAccess, apiCollection), null);
        }

        return new Pair<>(apiSyncLimit, MetricTypes.ACTIVE_ENDPOINTS);
    }

    private static SyncLimit assetLimit(FeatureAccess access, ApiCollection apiCollection) {
        return UsageMetricUtils.shouldSkipNewAsset(access, apiCollection) ? new SyncLimit(true, 0) : SyncLimit.noLimit;
    }
}

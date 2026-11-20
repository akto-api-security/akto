package com.akto.runtime;

import com.akto.billing.UsageMetricUtils;
import com.akto.dto.ApiCollection;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.billing.SyncLimit;
import com.akto.dto.usage.MetricTypes;
import com.akto.util.Pair;

/**
 * Limit the runtime applies to new endpoints of a collection, and the metric whose usage to update
 * with the endpoints that were let through (null: nothing to update).
 *  - API / DAST collections: counted per endpoint against ACTIVE_ENDPOINTS.
 *  - Agentic collections (AI_ASSET_COUNT counts collections): no per-endpoint limit. Once the org
 *    is over its limit, only collections created after the overage are blocked.
 *  - Endpoint-source collections: not limited here.
 */
final class SyncLimitPolicy {

    private SyncLimitPolicy() {}

    static Pair<SyncLimit, MetricTypes> forCollection(ApiCollection apiCollection, SyncLimit apiSyncLimit, FeatureAccess aiAssetAccess) {
        if (apiCollection == null) {
            return new Pair<>(apiSyncLimit, MetricTypes.ACTIVE_ENDPOINTS);
        }

        if (apiCollection.isAgenticCollection()) {
            if (UsageMetricUtils.shouldSkipNewAsset(aiAssetAccess, apiCollection)) {
                return new Pair<>(new SyncLimit(true, 0), null);
            }
            return new Pair<>(SyncLimit.noLimit, null);
        }

        if (apiCollection.isEndpointCollection()) {
            return new Pair<>(SyncLimit.noLimit, null);
        }

        return new Pair<>(apiSyncLimit, MetricTypes.ACTIVE_ENDPOINTS);
    }
}

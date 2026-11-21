package com.akto.hybrid_runtime;

import com.akto.dao.context.Context;
import com.akto.data_actor.DbLayer;
import com.akto.dto.ApiCollection;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.billing.SyncLimit;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.usage.MetricTypes;
import com.akto.util.Constants;
import com.akto.util.Pair;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which limit the mini-runtime applies to a collection's new endpoints, and which metric gets the
 * endpoint deltas. Asset metrics only block new collections once over the limit past the grace period.
 */
public class SyncLimitPolicyTest {

    private static final int DAY = 24 * 60 * 60;
    private static final SyncLimit API_LIMIT = new SyncLimit(true, 5);

    private static ApiCollection collection(int startTs, String... tagKeyValues) {
        ApiCollection c = new ApiCollection(1, "c", startTs, new HashSet<>(), "host", 0, false, true);
        List<CollectionTags> tags = new ArrayList<>();
        for (int i = 0; i < tagKeyValues.length; i += 2) {
            tags.add(new CollectionTags(startTs, tagKeyValues[i], tagKeyValues[i + 1], CollectionTags.TagSource.KUBERNETES));
        }
        c.setTagsList(tags);
        return c;
    }

    private static ApiCollection mcp(int startTs) {
        return collection(startTs, Constants.AKTO_MCP_SERVER_TAG, "v");
    }

    private static ApiCollection endpointSource(int startTs) {
        return collection(startTs, Constants.AKTO_ENDPOINT_SOURCE_TAG, Constants.AKTO_ENDPOINT_SOURCE_VALUE);
    }

    // usage >= limit, overage first seen `overageAgo` seconds ago
    private static FeatureAccess over(int overageAgo) {
        return new FeatureAccess(true, Context.now() - overageAgo, 10, 10);
    }

    private static boolean blocks(SyncLimit limit) {
        return limit.checkLimit && limit.updateUsageLeftAndCheckSkip();
    }

    @Test
    public void apiAndDastCollectionsUseTheEndpointLimit() {
        for (ApiCollection c : new ApiCollection[]{null, collection(0), collection(0, "dast", "v")}) {
            Pair<SyncLimit, MetricTypes> p = SyncLimitPolicy.forCollection(c, API_LIMIT, over(2 * DAY), over(2 * DAY));
            assertSame(API_LIMIT, p.getFirst());
            assertEquals(MetricTypes.ACTIVE_ENDPOINTS, p.getSecond());
        }
    }

    @Test
    public void agenticCollectionsNeverReportEndpointDeltas() {
        Pair<SyncLimit, MetricTypes> p = SyncLimitPolicy.forCollection(mcp(Context.now()), API_LIMIT,
                FeatureAccess.fullAccess, null);
        assertNull(p.getSecond());
        assertFalse(blocks(p.getFirst()));
    }

    @Test
    public void newAgenticCollectionBlockedOnlyAfterGrace() {
        int now = Context.now();
        // overage 2 days ago, past the 1 day grace: a collection created after it is blocked
        assertTrue(blocks(SyncLimitPolicy.forCollection(mcp(now - DAY), API_LIMIT, over(2 * DAY), null).getFirst()));
        // a collection that existed before the overage keeps ingesting
        assertFalse(blocks(SyncLimitPolicy.forCollection(mcp(now - 3 * DAY), API_LIMIT, over(2 * DAY), null).getFirst()));
        // still inside the grace period: nothing is blocked
        assertFalse(blocks(SyncLimitPolicy.forCollection(mcp(now), API_LIMIT, over(60 * 60), null).getFirst()));
    }

    @Test
    public void aiAgentCallerCollectionIsGatedAsAgentic() {
        ApiCollection caller = collection(Context.now() - DAY, Constants.AKTO_AI_AGENT_CALLER_TAG, "openai");
        Pair<SyncLimit, MetricTypes> p = SyncLimitPolicy.forCollection(caller, API_LIMIT, over(2 * DAY), null);
        assertNull(p.getSecond());
        assertTrue(blocks(p.getFirst()));
    }

    @Test
    public void unknownOverageTimeOrUnderLimitNeverBlocks() {
        ApiCollection newMcp = mcp(Context.now());
        assertFalse(blocks(SyncLimitPolicy.forCollection(newMcp, API_LIMIT, new FeatureAccess(true, -1, 10, 10), null).getFirst()));
        assertFalse(blocks(SyncLimitPolicy.forCollection(newMcp, API_LIMIT, new FeatureAccess(true, Context.now() - 2 * DAY, 10, 3), null).getFirst()));
        assertFalse(blocks(SyncLimitPolicy.forCollection(newMcp, API_LIMIT, new FeatureAccess(true, Context.now() - 2 * DAY, -1, 50), null).getFirst()));
    }

    @Test
    public void missingAiEntitlementBlocks() {
        assertTrue(blocks(SyncLimitPolicy.forCollection(mcp(0), API_LIMIT, new FeatureAccess(false), null).getFirst()));
    }

    @Test
    public void endpointCollectionsAreGatedByEndpointAssetsAndUnlimitedWhenNotConfigured() {
        int now = Context.now();
        Pair<SyncLimit, MetricTypes> notConfigured = SyncLimitPolicy.forCollection(endpointSource(now), API_LIMIT, over(2 * DAY), null);
        assertNull(notConfigured.getSecond());
        assertFalse(blocks(notConfigured.getFirst()));

        Pair<SyncLimit, MetricTypes> over = SyncLimitPolicy.forCollection(endpointSource(now - DAY), API_LIMIT, FeatureAccess.fullAccess, over(2 * DAY));
        assertNull(over.getSecond());
        assertTrue(blocks(over.getFirst()));

        // an endpoint-source collection with an MCP tag is an endpoint asset, not an agentic one
        ApiCollection endpointMcp = collection(now - DAY, Constants.AKTO_MCP_SERVER_TAG, "v",
                Constants.AKTO_ENDPOINT_SOURCE_TAG, Constants.AKTO_ENDPOINT_SOURCE_VALUE);
        assertFalse(blocks(SyncLimitPolicy.forCollection(endpointMcp, API_LIMIT, over(2 * DAY), null).getFirst()));
    }

    @Test
    public void cyborgIgnoresEndpointDeltasForAssetMetrics() {
        assertTrue(DbLayer.acceptsDeltaUsage(MetricTypes.ACTIVE_ENDPOINTS));
        assertFalse(DbLayer.acceptsDeltaUsage(MetricTypes.AI_ASSET_COUNT));
        assertFalse(DbLayer.acceptsDeltaUsage(MetricTypes.MCP_ASSET_COUNT));
        assertFalse(DbLayer.acceptsDeltaUsage(MetricTypes.ENDPOINT_ASSET_COUNT));
    }
}

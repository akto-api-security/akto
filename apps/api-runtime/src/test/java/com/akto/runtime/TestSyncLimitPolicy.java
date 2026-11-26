package com.akto.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import org.junit.jupiter.api.Test;

import com.akto.dto.ApiCollection;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.billing.SyncLimit;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.usage.MetricTypes;
import com.akto.util.Constants;
import com.akto.util.Pair;

/**
 * Which limit the runtime applies to new endpoints of a collection. API / DAST collections are
 * counted per endpoint, agentic collections are gated by asset count (only collections created
 * after the overage are blocked) and never send endpoint deltas, endpoint-source collections are
 * not limited here.
 *
 * JUnit 5 on purpose: JUnit 4 tests do not start in this module (the vintage engine fails to parse
 * the junit version), so a JUnit 4 test here would silently run zero tests.
 */
public class TestSyncLimitPolicy {

    private static final int OVERAGE_AT = 1000;
    private static final SyncLimit API_LIMIT = new SyncLimit(true, 5);

    private static final FeatureAccess AI_UNLIMITED = FeatureAccess.fullAccess;
    private static final FeatureAccess AI_OVER_LIMIT = new FeatureAccess(true, OVERAGE_AT, 10, 12);

    private static ApiCollection collection(int startTs, CollectionTags... tags) {
        ApiCollection c = new ApiCollection(1, "c", startTs, new HashSet<>(), "c", 0, false, true);
        c.setTagsList(new ArrayList<>(Arrays.asList(tags)));
        return c;
    }

    private static CollectionTags tag(String key) {
        return new CollectionTags(0, key, "v", CollectionTags.TagSource.KUBERNETES);
    }

    private static CollectionTags endpointSource() {
        return new CollectionTags(0, Constants.AKTO_ENDPOINT_SOURCE_TAG, Constants.AKTO_ENDPOINT_SOURCE_VALUE,
                CollectionTags.TagSource.KUBERNETES);
    }

    @Test
    public void unknownCollectionCountsAgainstActiveEndpoints() {
        Pair<SyncLimit, MetricTypes> p = SyncLimitPolicy.forCollection(null, API_LIMIT, AI_OVER_LIMIT);
        assertSame(API_LIMIT, p.getFirst());
        assertEquals(MetricTypes.ACTIVE_ENDPOINTS, p.getSecond());
    }

    @Test
    public void apiAndDastCollectionsCountAgainstActiveEndpoints() {
        for (ApiCollection c : Arrays.asList(collection(OVERAGE_AT + 1), collection(OVERAGE_AT + 1, tag(Constants.AKTO_DAST_TAG)))) {
            Pair<SyncLimit, MetricTypes> p = SyncLimitPolicy.forCollection(c, API_LIMIT, AI_OVER_LIMIT);
            assertSame(API_LIMIT, p.getFirst());
            assertEquals(MetricTypes.ACTIVE_ENDPOINTS, p.getSecond());
        }
    }

    @Test
    public void agenticCollectionIsUnlimitedAndSendsNoEndpointDeltas() {
        for (String agenticTag : Arrays.asList(Constants.AKTO_MCP_SERVER_TAG, Constants.AKTO_GEN_AI_TAG, Constants.AKTO_AI_AGENT_CALLER_TAG)) {
            Pair<SyncLimit, MetricTypes> p = SyncLimitPolicy.forCollection(collection(1, tag(agenticTag)), API_LIMIT, AI_UNLIMITED);
            assertFalse(p.getFirst().checkLimit);
            assertNull(p.getSecond(), "asset metrics are measured by the cron, not by endpoint deltas");
        }
    }

    @Test
    public void newAgenticCollectionIsBlockedOnceOverLimit() {
        Pair<SyncLimit, MetricTypes> p = SyncLimitPolicy.forCollection(
                collection(OVERAGE_AT + 1, tag(Constants.AKTO_MCP_SERVER_TAG)), API_LIMIT, AI_OVER_LIMIT);
        assertTrue(p.getFirst().checkLimit);
        assertTrue(p.getFirst().updateUsageLeftAndCheckSkip(), "its new endpoints are skipped");
        assertNull(p.getSecond());
    }

    @Test
    public void existingAgenticCollectionKeepsBeingIngestedOverLimit() {
        Pair<SyncLimit, MetricTypes> p = SyncLimitPolicy.forCollection(
                collection(OVERAGE_AT - 1, tag(Constants.AKTO_GEN_AI_TAG)), API_LIMIT, AI_OVER_LIMIT);
        assertFalse(p.getFirst().checkLimit);
        assertNull(p.getSecond());
    }

    @Test
    public void agenticCollectionWithoutEntitlementIsBlocked() {
        Pair<SyncLimit, MetricTypes> p = SyncLimitPolicy.forCollection(
                collection(1, tag(Constants.AKTO_MCP_SERVER_TAG)), API_LIMIT, FeatureAccess.noAccess);
        assertTrue(p.getFirst().checkLimit);
        assertTrue(p.getFirst().updateUsageLeftAndCheckSkip());
    }

    @Test
    public void endpointSourceCollectionIsNotLimitedAndNotCountedAsAnApiEndpoint() {
        // even when it also carries an agentic tag
        for (ApiCollection c : Arrays.asList(collection(OVERAGE_AT + 1, endpointSource()),
                collection(OVERAGE_AT + 1, tag(Constants.AKTO_GEN_AI_TAG), endpointSource()))) {
            Pair<SyncLimit, MetricTypes> p = SyncLimitPolicy.forCollection(c, API_LIMIT, AI_OVER_LIMIT);
            assertFalse(p.getFirst().checkLimit);
            assertNull(p.getSecond());
        }
    }
}

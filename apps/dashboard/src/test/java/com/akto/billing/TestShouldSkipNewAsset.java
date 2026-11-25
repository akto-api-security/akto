package com.akto.billing;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;

import org.junit.Test;

import com.akto.dto.ApiCollection;
import com.akto.dto.billing.FeatureAccess;

/**
 * At or over its limit an org stops getting NEW assets (collections created after the overage was
 * first detected); assets that already existed keep being ingested.
 */
public class TestShouldSkipNewAsset {

    private static final int LIMIT = 10;
    private static final int OVERAGE_AT = 1000;

    private static ApiCollection collectionStartedAt(int startTs) {
        return new ApiCollection(1, "c", startTs, new HashSet<>(), "c", 0, false, true);
    }

    private static FeatureAccess access(boolean granted, int overageFirstDetected, int limit, int usage) {
        return new FeatureAccess(granted, overageFirstDetected, limit, usage);
    }

    @Test
    public void newCollectionIsBlockedOnceOverLimit() {
        assertTrue(UsageMetricUtils.shouldSkipNewAsset(
                access(true, OVERAGE_AT, LIMIT, LIMIT + 1), collectionStartedAt(OVERAGE_AT + 1)));
    }

    @Test
    public void newCollectionIsBlockedExactlyAtLimit() {
        assertTrue(UsageMetricUtils.shouldSkipNewAsset(
                access(true, OVERAGE_AT, LIMIT, LIMIT), collectionStartedAt(OVERAGE_AT + 1)));
    }

    @Test
    public void existingCollectionKeepsBeingIngestedOverLimit() {
        assertFalse(UsageMetricUtils.shouldSkipNewAsset(
                access(true, OVERAGE_AT, LIMIT, LIMIT + 5), collectionStartedAt(OVERAGE_AT - 1)));
    }

    @Test
    public void collectionCreatedExactlyAtOverageTimeIsNotNew() {
        assertFalse(UsageMetricUtils.shouldSkipNewAsset(
                access(true, OVERAGE_AT, LIMIT, LIMIT + 5), collectionStartedAt(OVERAGE_AT)));
    }

    @Test
    public void nothingIsBlockedUnderLimit() {
        assertFalse(UsageMetricUtils.shouldSkipNewAsset(
                access(true, OVERAGE_AT, LIMIT, LIMIT - 1), collectionStartedAt(OVERAGE_AT + 1)));
    }

    @Test
    public void nothingIsBlockedWhenUnlimited() {
        assertFalse(UsageMetricUtils.shouldSkipNewAsset(
                access(true, OVERAGE_AT, -1, 1_000_000), collectionStartedAt(OVERAGE_AT + 1)));
    }

    @Test
    public void unknownOverageTimeNeverBlocksExistingAssets() {
        assertFalse(UsageMetricUtils.shouldSkipNewAsset(
                access(true, -1, LIMIT, LIMIT + 5), collectionStartedAt(OVERAGE_AT + 1)));
    }

    @Test
    public void orgWithoutEntitlementIsBlocked() {
        assertTrue(UsageMetricUtils.shouldSkipNewAsset(FeatureAccess.noAccess, collectionStartedAt(1)));
    }

    @Test
    public void unknownCollectionIsNotGated() {
        assertFalse(UsageMetricUtils.shouldSkipNewAsset(access(true, OVERAGE_AT, LIMIT, LIMIT + 5), null));
    }
}

package com.akto.service.collections;

import com.akto.dto.ApiCollectionStatsMeta;
import com.akto.service.collections.CollectionStatsRefresher.Metric;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestCollectionStatsRefresherStaleness {

    private static ApiCollectionStatsMeta refreshedAt(int ts) {
        ApiCollectionStatsMeta m = new ApiCollectionStatsMeta();
        m.setRefreshedAt(ts);
        return m;
    }

    @Test
    public void neverRefreshedIsStale() {
        assertTrue(CollectionStatsRefresher.isStale(null, Metric.RISK_SCORE, false, 1000));
        assertTrue(CollectionStatsRefresher.isStale(refreshedAt(0), Metric.LAST_SEEN, false, 1000));
    }

    @Test
    public void staleOnlyAfterTheMetricTtl() {
        int now = 10_000;
        assertFalse(CollectionStatsRefresher.isStale(refreshedAt(now - Metric.LAST_SEEN.getTtlSeconds()), Metric.LAST_SEEN, false, now));
        assertTrue(CollectionStatsRefresher.isStale(refreshedAt(now - Metric.LAST_SEEN.getTtlSeconds() - 1), Metric.LAST_SEEN, false, now));
        assertFalse(CollectionStatsRefresher.isStale(refreshedAt(now - 100), Metric.SENSITIVE, false, now));
    }

    @Test
    public void forceShortensTableMetricsToTheCooldownButNotOthers() {
        int now = 10_000;
        assertTrue(CollectionStatsRefresher.isStale(refreshedAt(now - 20), Metric.ENDPOINTS_COUNT, true, now));
        assertFalse(CollectionStatsRefresher.isStale(refreshedAt(now - 10), Metric.ENDPOINTS_COUNT, true, now));
        assertFalse(CollectionStatsRefresher.isStale(refreshedAt(now - 20), Metric.SENSITIVE, true, now));
    }
}

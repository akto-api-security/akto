package com.akto.service.insights;

import com.akto.MongoBasedTest;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.type.URLMethods.Method;
import com.akto.service.insights.InsightProvider.Scope;
import com.akto.service.insights.providers.UntestedHighRiskApisProvider;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;

/**
 * Covers UntestedHighRiskApisProvider: the exact-ApiInfoDao.count() path (never a scan over
 * bundle.apiInfoRows(), which is capped at 50k -- see the provider's own javadoc), the
 * CRITICAL/HIGH/MEDIUM ratio ladder at its 0.5/0.25 boundaries, the never-tested-vs-stale-30d
 * "untested" definition, and out-of-testing-scope collection exclusion.
 */
public class TestUntestedHighRiskApisProvider extends MongoBasedTest {

    private static final float HIGH_RISK = 4.5f; // > HIGH_RISK_THRESHOLD (4.0)
    private static final int THIRTY_ONE_DAYS_AGO_OFFSET = -(31 * 24 * 3600);

    private final UntestedHighRiskApisProvider provider = new UntestedHighRiskApisProvider();

    @Before
    public void setUp() {
        Context.accountId.set(ACCOUNT_ID);
        Context.userId.set(1);
        Context.contextSource.set(CONTEXT_SOURCE.API);
        ApiInfoDao.instance.getMCollection().drop();
    }

    private InsightContext ctx() {
        return new InsightContext(ACCOUNT_ID, 1, CONTEXT_SOURCE.API, 0, Context.now());
    }

    private InsightDataBundle buildBundle(List<ApiCollection> collections) {
        InsightContext c = ctx();
        return new InsightDataBundle(c,
                collections, new HashMap<>(), new HashMap<>(), new HashMap<>(),
                new ArrayList<>(), new ArrayList<>(), new HashSet<>(), new HashMap<>(),
                new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                true, new ArrayList<>(), new HashMap<>(),
                new InsightsThreatBackendAccess(), new InsightLazySources(c));
    }

    private ApiInfo highRiskApi(int collectionId, String url, int lastTested) {
        ApiInfo info = new ApiInfo(new ApiInfoKey(collectionId, url, Method.GET));
        info.setRiskScore(HIGH_RISK);
        info.setLastTested(lastTested);
        return info;
    }

    private InsightResult computeUnscoped(InsightDataBundle bundle) {
        try (MockedStatic<UsersCollectionsList> m = mockStatic(UsersCollectionsList.class)) {
            m.when(() -> UsersCollectionsList.getCollectionsIdForUser(anyInt(), anyInt())).thenReturn(null);
            return provider.compute(bundle, bundle.ctx, Scope.LIST);
        }
    }

    private Number metricValue(InsightResult r, String key) {
        for (InsightResult.Metric m : r.getMetrics()) {
            if (key.equals(m.getKey())) return m.getValue();
        }
        throw new AssertionError("metric not found: " + key);
    }

    // ── empty / no-throw ─────────────────────────────────────────────────────────────

    @Test
    public void testNoHighRiskApis_noThrow_noDataStatus_metricsComplete() {
        InsightResult r = computeUnscoped(buildBundle(new ArrayList<>()));

        assertEquals(InsightResult.Status.NO_DATA.name(), r.getStatus());
        assertNull(r.getSeverity());
        assertEquals(true, r.isMetricsComplete());
        assertEquals("All high-risk APIs have recent test coverage", r.getHeadline());
    }

    // ── never-tested vs stale-30d vs recently-tested ────────────────────────────────

    @Test
    public void testNeverTested_lastTestedZero_countsAsUntested() {
        ApiInfoDao.instance.insertMany(Arrays.asList(highRiskApi(1, "/api/never", 0)));

        InsightResult r = computeUnscoped(buildBundle(new ArrayList<>()));

        assertEquals(1, metricValue(r, "highRiskApis").longValue());
        assertEquals(1, metricValue(r, "untestedHighRiskApis").longValue());
    }

    @Test
    public void testStaleOver30Days_countsAsUntested() {
        int staleTs = Context.now() + THIRTY_ONE_DAYS_AGO_OFFSET;
        ApiInfoDao.instance.insertMany(Arrays.asList(highRiskApi(1, "/api/stale", staleTs)));

        InsightResult r = computeUnscoped(buildBundle(new ArrayList<>()));

        assertEquals(1, metricValue(r, "untestedHighRiskApis").longValue());
    }

    @Test
    public void testRecentlyTested_notCountedAsUntested() {
        int recentTs = Context.now() - (24 * 3600); // 1 day ago, well within the 30-day window
        ApiInfoDao.instance.insertMany(Arrays.asList(highRiskApi(1, "/api/fresh", recentTs)));

        InsightResult r = computeUnscoped(buildBundle(new ArrayList<>()));

        assertEquals(1, metricValue(r, "highRiskApis").longValue());
        assertEquals(0, metricValue(r, "untestedHighRiskApis").longValue());
        // status is keyed off highRiskCount, not untestedHighRiskCount -- a high-risk API with
        // full test coverage is still READY, not NO_DATA.
        assertEquals(InsightResult.Status.READY.name(), r.getStatus());
        assertEquals("All high-risk APIs have recent test coverage", r.getHeadline());
    }

    // ── ratio severity ladder: >=0.5 CRITICAL, >=0.25 HIGH, else MEDIUM ─────────────

    @Test
    public void testRatioExactly0Point5_severityCritical() {
        List<ApiInfo> rows = new ArrayList<>();
        for (int i = 0; i < 4; i++) rows.add(highRiskApi(1, "/api/tested-" + i, Context.now())); // tested
        for (int i = 0; i < 4; i++) rows.add(highRiskApi(1, "/api/untested-" + i, 0));           // untested
        ApiInfoDao.instance.insertMany(rows);

        InsightResult r = computeUnscoped(buildBundle(new ArrayList<>()));

        assertEquals(8, metricValue(r, "highRiskApis").longValue());
        assertEquals(4, metricValue(r, "untestedHighRiskApis").longValue());
        assertEquals("CRITICAL", r.getSeverity());
    }

    @Test
    public void testRatioJustUnder0Point5_severityHigh() {
        List<ApiInfo> rows = new ArrayList<>();
        for (int i = 0; i < 5; i++) rows.add(highRiskApi(1, "/api/tested-" + i, Context.now()));
        for (int i = 0; i < 3; i++) rows.add(highRiskApi(1, "/api/untested-" + i, 0));
        ApiInfoDao.instance.insertMany(rows); // 3/8 = 0.375, < 0.5

        InsightResult r = computeUnscoped(buildBundle(new ArrayList<>()));

        assertEquals("HIGH", r.getSeverity());
    }

    @Test
    public void testRatioExactly0Point25_severityHigh() {
        List<ApiInfo> rows = new ArrayList<>();
        for (int i = 0; i < 6; i++) rows.add(highRiskApi(1, "/api/tested-" + i, Context.now()));
        for (int i = 0; i < 2; i++) rows.add(highRiskApi(1, "/api/untested-" + i, 0));
        ApiInfoDao.instance.insertMany(rows); // 2/8 = 0.25

        InsightResult r = computeUnscoped(buildBundle(new ArrayList<>()));

        assertEquals("HIGH", r.getSeverity());
    }

    @Test
    public void testRatioJustUnder0Point25_severityMedium() {
        List<ApiInfo> rows = new ArrayList<>();
        for (int i = 0; i < 7; i++) rows.add(highRiskApi(1, "/api/tested-" + i, Context.now()));
        rows.add(highRiskApi(1, "/api/untested-0", 0));
        ApiInfoDao.instance.insertMany(rows); // 1/8 = 0.125, < 0.25

        InsightResult r = computeUnscoped(buildBundle(new ArrayList<>()));

        assertEquals("MEDIUM", r.getSeverity());
    }

    // ── out-of-testing-scope collections excluded ───────────────────────────────────

    @Test
    public void testOutOfTestingScopeCollection_excludedFromHighRiskCount() {
        ApiCollection outOfScope = new ApiCollection(9001, "excluded", 0, new HashSet<>(), null, 0, false, true);
        outOfScope.setIsOutOfTestingScope(true);

        ApiInfoDao.instance.insertMany(Arrays.asList(
                highRiskApi(9001, "/api/excluded", 0),
                highRiskApi(9002, "/api/included", 0)));

        InsightResult r = computeUnscoped(buildBundle(Arrays.asList(outOfScope)));

        assertEquals("only the in-scope collection's high-risk API must be counted",
                1, metricValue(r, "highRiskApis").longValue());
    }

    // ── metricsComplete is unconditionally true -- exact counts, not a bundle-row scan ──

    /** Even when the bundle's apiInfoRows() lazy read is (simulated as) truncated, the provider's
     *  metricsComplete must stay true -- it never reads bundle.apiInfoRows() or its truncation
     *  flag at all, only ApiInfoDao.count()/findAll() directly. A naive scan-based reimplementation
     *  reading bundle.apiInfoRows() would have had to flip metricsComplete to false here; this
     *  proves the real implementation doesn't. */
    @Test
    public void testMetricsCompleteTrue_evenWhenBundleApiInfoRowsIsTruncated() throws Exception {
        ApiInfoDao.instance.insertMany(Arrays.asList(highRiskApi(1, "/api/a", 0)));
        InsightDataBundle bundle = buildBundle(new ArrayList<>());

        bundle.apiInfoRows(); // force the memoized lazy read to run once
        // Flip the truncation flag by reflection -- the flag is a package-private implementation
        // detail (InsightLazySources.apiInfoRowsTruncated), not reachable via any public setter,
        // which is exactly why simulating it needs reflection rather than seeding 50k+ real rows.
        Field lazyField = InsightDataBundle.class.getDeclaredField("lazy");
        lazyField.setAccessible(true);
        InsightLazySources lazy = (InsightLazySources) lazyField.get(bundle);
        Field truncatedField = InsightLazySources.class.getDeclaredField("apiInfoRowsTruncated");
        truncatedField.setAccessible(true);
        truncatedField.set(lazy, true);
        assertTrue("sanity: the flag really is flipped now", bundle.isApiInfoRowsTruncated());

        InsightResult r = computeUnscoped(bundle);

        assertEquals("UntestedHighRiskApisProvider must ignore bundle truncation entirely -- its "
                + "counts come from exact ApiInfoDao.count() calls", true, r.isMetricsComplete());
        assertEquals(0, r.getDataGaps().size());
    }
}

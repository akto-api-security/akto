package com.akto.service.insights;

import com.akto.MongoBasedTest;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiAccessType;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.type.URLMethods.Method;
import com.akto.service.insights.InsightProvider.Scope;
import com.akto.service.insights.providers.UnauthenticatedSensitiveApisProvider;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;

/**
 * Covers UnauthenticatedSensitiveApisProvider: the CRITICAL/HIGH/MEDIUM severity ladder
 * (unauth+public+sensitive / unauth+public / unauth-only), the exact combinations that must NOT
 * count at all, and the empty/malformed-row cases. Reads bundle.apiInfoRows() through real
 * embedded Mongo (same infra as TestInsightLazySources/TestInsightDataBundle) so
 * calculateActualAuth()'s real per-row logic is exercised, not a stub.
 */
public class TestUnauthenticatedSensitiveApisProvider extends MongoBasedTest {

    private final UnauthenticatedSensitiveApisProvider provider = new UnauthenticatedSensitiveApisProvider();

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

    private InsightDataBundle buildBundle() {
        InsightContext c = ctx();
        return new InsightDataBundle(c,
                new java.util.ArrayList<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(),
                new java.util.ArrayList<>(), new java.util.ArrayList<>(), new HashSet<>(), new HashMap<>(),
                new java.util.ArrayList<>(), new java.util.ArrayList<>(),
                new java.util.ArrayList<>(), new java.util.ArrayList<>(), new java.util.ArrayList<>(),
                true, new java.util.ArrayList<>(), new HashMap<>(),
                new InsightsThreatBackendAccess(), new InsightLazySources(c));
    }

    private ApiInfo apiInfoWithAuth(int collectionId, String url, String... authTypes) {
        ApiInfo info = new ApiInfo(new ApiInfoKey(collectionId, url, Method.GET));
        Set<Set<String>> allAuth = new HashSet<>();
        allAuth.add(new HashSet<>(Arrays.asList(authTypes)));
        info.setAllAuthTypesFound(allAuth);
        return info;
    }

    /** RBAC (UsersCollectionsList) sits outside the business logic under test in every case here
     *  -- unscope it so seeded rows aren't silently filtered by a missing ApiCollection
     *  registration rather than the logic actually being tested. */
    private InsightResult computeUnscoped(InsightDataBundle bundle) {
        try (MockedStatic<UsersCollectionsList> m = mockStatic(UsersCollectionsList.class)) {
            m.when(() -> UsersCollectionsList.getCollectionsIdForUser(anyInt(), anyInt())).thenReturn(null);
            return provider.compute(bundle, bundle.ctx, Scope.LIST);
        }
    }

    @Test
    public void testEmptyApiInfo_noThrow_noDataStatus() {
        InsightResult r = computeUnscoped(buildBundle());

        assertEquals(InsightResult.Status.NO_DATA.name(), r.getStatus());
        assertNull(r.getSeverity());
        assertEquals("No unauthenticated APIs exposed to the public", r.getHeadline());
    }

    @Test
    public void testUnauthPublicSensitive_severityCritical() {
        ApiInfo info = apiInfoWithAuth(1, "/api/critical", ApiInfo.AuthType.UNAUTHENTICATED);
        info.setApiAccessTypes(new HashSet<>(Arrays.asList(ApiAccessType.PUBLIC)));
        info.setIsSensitive(true);
        ApiInfoDao.instance.insertMany(Arrays.asList(info));

        InsightResult r = computeUnscoped(buildBundle());

        assertEquals("CRITICAL", r.getSeverity());
        assertEquals(1, metricValue(r, "unauthenticatedExposed").intValue());
        assertEquals(1, metricValue(r, "unauthenticatedExposedSensitive").intValue());
    }

    @Test
    public void testUnauthPublicNotSensitive_severityHigh() {
        ApiInfo info = apiInfoWithAuth(1, "/api/high", ApiInfo.AuthType.UNAUTHENTICATED);
        info.setApiAccessTypes(new HashSet<>(Arrays.asList(ApiAccessType.PUBLIC)));
        info.setIsSensitive(false);
        ApiInfoDao.instance.insertMany(Arrays.asList(info));

        InsightResult r = computeUnscoped(buildBundle());

        assertEquals("HIGH", r.getSeverity());
        assertEquals(1, metricValue(r, "unauthenticatedExposed").intValue());
        assertEquals(0, metricValue(r, "unauthenticatedExposedSensitive").intValue());
    }

    @Test
    public void testUnauthOnly_notPublic_severityMedium() {
        ApiInfo info = apiInfoWithAuth(1, "/api/medium", ApiInfo.AuthType.UNAUTHENTICATED);
        // apiAccessTypes left as the default empty set -- not public, not third-party.
        info.setIsSensitive(true); // sensitivity must be irrelevant when not exposed at all
        ApiInfoDao.instance.insertMany(Arrays.asList(info));

        InsightResult r = computeUnscoped(buildBundle());

        assertEquals("MEDIUM", r.getSeverity());
        assertEquals(0, metricValue(r, "unauthenticatedExposed").intValue());
    }

    @Test
    public void testAuthenticatedPublicSensitive_notCountedAtAll() {
        ApiInfo info = apiInfoWithAuth(1, "/api/authed", ApiInfo.AuthType.BEARER);
        info.setApiAccessTypes(new HashSet<>(Arrays.asList(ApiAccessType.PUBLIC)));
        info.setIsSensitive(true);
        ApiInfoDao.instance.insertMany(Arrays.asList(info));

        InsightResult r = computeUnscoped(buildBundle());

        // status is keyed off total row count, not the unauthenticated-specific counts -- one
        // (non-matching) row is still READY, not NO_DATA.
        assertEquals(InsightResult.Status.READY.name(), r.getStatus());
        assertNull("an authenticated API must never drive severity, no matter how exposed/sensitive", r.getSeverity());
        assertEquals(1, metricValue(r, "totalApis").intValue());
        assertEquals(0, metricValue(r, "unauthenticatedExposed").intValue());
    }

    @Test
    public void testThirdPartyAccessType_countsSameAsPublic() {
        ApiInfo info = apiInfoWithAuth(1, "/api/third-party", ApiInfo.AuthType.UNAUTHENTICATED);
        info.setApiAccessTypes(new HashSet<>(Arrays.asList(ApiAccessType.THIRD_PARTY)));
        info.setIsSensitive(false);
        ApiInfoDao.instance.insertMany(Arrays.asList(info));

        InsightResult r = computeUnscoped(buildBundle());

        assertEquals("HIGH", r.getSeverity());
        assertEquals(1, metricValue(r, "unauthenticatedExposed").intValue());
    }

    @Test
    public void testMultipleObservedAuthTypes_noUnauthenticated_notCounted() {
        ApiInfo info = new ApiInfo(new ApiInfoKey(1, "/api/multi-auth", Method.GET));
        Set<Set<String>> allAuth = new HashSet<>();
        allAuth.add(new HashSet<>(Arrays.asList(ApiInfo.AuthType.BEARER)));
        allAuth.add(new HashSet<>(Arrays.asList(ApiInfo.AuthType.API_KEY)));
        info.setAllAuthTypesFound(allAuth);
        info.setApiAccessTypes(new HashSet<>(Arrays.asList(ApiAccessType.PUBLIC)));
        ApiInfoDao.instance.insertMany(Arrays.asList(info));

        InsightResult r = computeUnscoped(buildBundle());

        assertEquals(InsightResult.Status.READY.name(), r.getStatus());
        assertEquals(0, metricValue(r, "unauthenticatedExposed").intValue());
    }

    /** allAuthTypesFound=null makes calculateActualAuth() throw per-row inside
     *  InsightLazySources.apiInfoRows() (see TestInsightLazySources); the row survives with
     *  actualAuthType left null. The provider must treat a null actualAuthType as "not
     *  unauthenticated-only" rather than throwing or miscounting it. */
    @Test
    public void testMalformedRow_nullAllAuthTypesFound_gracefullyExcluded() {
        ApiInfo healthy = apiInfoWithAuth(1, "/api/healthy", ApiInfo.AuthType.UNAUTHENTICATED);
        healthy.setApiAccessTypes(new HashSet<>(Arrays.asList(ApiAccessType.PUBLIC)));

        ApiInfo malformed = new ApiInfo(new ApiInfoKey(1, "/api/malformed", Method.GET));
        malformed.setAllAuthTypesFound(null);

        ApiInfoDao.instance.insertMany(Arrays.asList(healthy, malformed));

        InsightResult r = computeUnscoped(buildBundle());

        assertEquals(2, metricValue(r, "totalApis").intValue());
        assertEquals("the malformed row must not be counted or throw", 1, metricValue(r, "unauthenticatedExposed").intValue());
        assertEquals("HIGH", r.getSeverity());
    }

    @Test
    public void testMetricsComplete_whenApiInfoRowsNotTruncated() {
        ApiInfoDao.instance.insertMany(Arrays.asList(apiInfoWithAuth(1, "/api/a", ApiInfo.AuthType.UNAUTHENTICATED)));
        InsightResult r = computeUnscoped(buildBundle());
        assertFalse("no truncation happened, but metricsComplete/dataGaps must reflect that", r.isDisabled());
        assertEquals(true, r.isMetricsComplete());
        assertEquals(0, r.getDataGaps().size());
    }

    private Number metricValue(InsightResult r, String key) {
        for (InsightResult.Metric m : r.getMetrics()) {
            if (key.equals(m.getKey())) return m.getValue();
        }
        throw new AssertionError("metric not found: " + key);
    }
}

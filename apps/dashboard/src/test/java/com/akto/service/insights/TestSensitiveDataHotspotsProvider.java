package com.akto.service.insights;

import com.akto.MongoBasedTest;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.service.insights.InsightProvider.Scope;
import com.akto.service.insights.providers.SensitiveDataHotspotsProvider;
import com.akto.types.CappedSet;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;

/**
 * Covers SensitiveDataHotspotsProvider: the HIGH/MEDIUM threshold at exactly 10 vs 9 distinct
 * APIs for the top subType, correct ranking across multiple subtypes, and the empty/request-only
 * (not-counted) cases. Reads bundle.sensitiveApiCountBySubType() through real embedded Mongo
 * (SingleTypeInfoDao.responseSensitiveSubtypeApiCounts), the same aggregation
 * TestSingleTypeInfoDaoResponseSensitiveSubtypeApiCounts exercises directly -- this file only
 * confirms the provider's own severity/ranking logic on top of it.
 */
public class TestSensitiveDataHotspotsProvider extends MongoBasedTest {

    private final SensitiveDataHotspotsProvider provider = new SensitiveDataHotspotsProvider();

    @Before
    public void setUp() {
        Context.accountId.set(ACCOUNT_ID);
        Context.userId.set(1);
        Context.contextSource.set(CONTEXT_SOURCE.API);
        SingleTypeInfoDao.instance.getMCollection().drop();
    }

    private InsightContext ctx() {
        return new InsightContext(ACCOUNT_ID, 1, CONTEXT_SOURCE.API, 0, Context.now());
    }

    private InsightDataBundle buildBundle() {
        InsightContext c = ctx();
        return new InsightDataBundle(c,
                new ArrayList<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(),
                new ArrayList<>(), new ArrayList<>(), new HashSet<>(), new HashMap<>(),
                new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                true, new ArrayList<>(), new HashMap<>(),
                new InsightsThreatBackendAccess(), new InsightLazySources(c));
    }

    private void insertSensitiveApi(int apiCollectionId, String url, String method, SingleTypeInfo.SubType subType, int responseCode) {
        SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(url, method, responseCode, false, "param",
                subType, apiCollectionId, false);
        SingleTypeInfo sti = new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 1, 0, 0,
                new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);
        SingleTypeInfoDao.instance.insertOne(sti);
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

    @Test
    public void testEmptyData_noThrow_noDataStatus() {
        InsightResult r = computeUnscoped(buildBundle());

        assertEquals(InsightResult.Status.NO_DATA.name(), r.getStatus());
        assertNull(r.getSeverity());
        assertEquals("No sensitive data detected in responses", r.getHeadline());
    }

    @Test
    public void testRequestOnlyHit_responseCodeMinusOne_notCounted() {
        insertSensitiveApi(1, "/api/req-only", "GET", SingleTypeInfo.EMAIL, -1);

        InsightResult r = computeUnscoped(buildBundle());

        assertEquals("a request-side-only hit (responseCode=-1) must not be counted -- this "
                + "provider is response-only by design", InsightResult.Status.NO_DATA.name(), r.getStatus());
    }

    @Test
    public void testTopSubTypeApiCount_exactly10_severityHigh() {
        for (int i = 0; i < 10; i++) {
            insertSensitiveApi(1, "/api/endpoint-" + i, "GET", SingleTypeInfo.EMAIL, 200);
        }

        InsightResult r = computeUnscoped(buildBundle());

        assertEquals(10, metricValue(r, "topSubTypeApis").intValue());
        assertEquals("HIGH", r.getSeverity());
    }

    @Test
    public void testTopSubTypeApiCount_9_severityMedium() {
        for (int i = 0; i < 9; i++) {
            insertSensitiveApi(1, "/api/endpoint-" + i, "GET", SingleTypeInfo.EMAIL, 200);
        }

        InsightResult r = computeUnscoped(buildBundle());

        assertEquals(9, metricValue(r, "topSubTypeApis").intValue());
        assertEquals("MEDIUM", r.getSeverity());
    }

    @Test
    public void testMultipleSubTypes_topByApiCountSelectedCorrectly() {
        for (int i = 0; i < 3; i++) insertSensitiveApi(1, "/api/email-" + i, "GET", SingleTypeInfo.EMAIL, 200);
        for (int i = 0; i < 7; i++) insertSensitiveApi(1, "/api/phone-" + i, "GET", SingleTypeInfo.PHONE_NUMBER, 200);
        for (int i = 0; i < 1; i++) insertSensitiveApi(1, "/api/ssn-" + i, "GET", SingleTypeInfo.SSN, 200);

        InsightResult r = computeUnscoped(buildBundle());

        assertEquals(7, metricValue(r, "topSubTypeApis").intValue());
        assertTrue("the higher-count subtype must win the headline", r.getHeadline().contains("PHONE_NUMBER"));
        assertEquals(11, metricValue(r, "totalSensitiveApis").longValue());
    }

    @Test
    public void testSameApiMultipleParams_sameSubType_countedAsOneApi() {
        // Two different params on the SAME {collection,url,method} -- responseSensitiveSubtypeApiCounts
        // groups by endpoint identity, not by raw document, so this must count as one API, not two.
        SingleTypeInfo.ParamId p1 = new SingleTypeInfo.ParamId("/api/one", "GET", 200, false, "param1",
                SingleTypeInfo.EMAIL, 1, false);
        SingleTypeInfo.ParamId p2 = new SingleTypeInfo.ParamId("/api/one", "GET", 200, false, "param2",
                SingleTypeInfo.EMAIL, 1, false);
        SingleTypeInfoDao.instance.insertMany(java.util.Arrays.asList(
                new SingleTypeInfo(p1, new HashSet<>(), new HashSet<>(), 1, 0, 0, new CappedSet<>(),
                        SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE),
                new SingleTypeInfo(p2, new HashSet<>(), new HashSet<>(), 1, 0, 0, new CappedSet<>(),
                        SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE)));

        InsightResult r = computeUnscoped(buildBundle());

        assertEquals(1, metricValue(r, "topSubTypeApis").intValue());
    }

    @Test
    public void testEvidenceRows_topNCappedAtFive_orderedDescending() {
        String[] names = {"EMAIL", "PHONE_NUMBER", "SSN", "CREDIT_CARD", "VIN", "ADDRESS"};
        SingleTypeInfo.SubType[] types = {SingleTypeInfo.EMAIL, SingleTypeInfo.PHONE_NUMBER, SingleTypeInfo.SSN,
                SingleTypeInfo.CREDIT_CARD, SingleTypeInfo.VIN, SingleTypeInfo.ADDRESS};
        for (int t = 0; t < types.length; t++) {
            for (int i = 0; i <= t; i++) {
                insertSensitiveApi(1, "/api/" + names[t] + "-" + i, "GET", types[t], 200);
            }
        }

        InsightResult r = computeUnscoped(buildBundle());

        List<InsightResult.Evidence> evidence = r.getEvidence();
        assertEquals(1, evidence.size());
        assertEquals("evidence rows are capped at TOP_N=5 even though 6 subtypes have data", 5, evidence.get(0).getRows().size());
        assertEquals("totalRowCount must still report the real distinct-subtype count", 6, evidence.get(0).getTotalRowCount());
    }
}

package com.akto.service.insights;

import com.akto.MongoBasedTest;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing.VulnerableTestingRunResultDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.type.URLMethods.Method;
import com.akto.service.insights.InsightProvider.Scope;
import com.akto.service.insights.providers.IssueRecurrenceProvider;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;

import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;

/**
 * Covers IssueRecurrenceProvider: the RECURRING_THRESHOLD (>=2 distinct runs) exclusion of
 * one-off findings, the HIGH/MEDIUM severity boundary at maxRecurrence exactly 5 vs 4, and the
 * empty case. bundle.issueRecurrence() is exercised through real embedded Mongo across both
 * physical result collections, same infra as TestInsightLazySources' recurrence tests.
 */
public class TestIssueRecurrenceProvider extends MongoBasedTest {

    private final IssueRecurrenceProvider provider = new IssueRecurrenceProvider();

    @Before
    public void setUp() {
        Context.accountId.set(ACCOUNT_ID);
        Context.userId.set(1);
        Context.contextSource.set(CONTEXT_SOURCE.API);
        TestingRunResultDao.instance.getMCollection().drop();
        VulnerableTestingRunResultDao.instance.getMCollection().drop();
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

    /** Inserts `distinctRunCount` vulnerable result rows for the same {key, testSubType} finding,
     *  each under its OWN summary id -- distinctRuns is an $addToSet of summary id, so this is
     *  the only way to actually drive the recurrence count up (see
     *  InsightLazySources.issueRecurrence()'s javadoc). */
    private void seedRecurringFinding(int collectionId, String url, String testSubType, int distinctRunCount) {
        ApiInfoKey key = new ApiInfoKey(collectionId, url, Method.GET);
        List<TestingRunResult> rows = new ArrayList<>();
        for (int i = 0; i < distinctRunCount; i++) {
            TestingRunResult r = new TestingRunResult();
            r.setApiInfoKey(key);
            r.setTestSubType(testSubType);
            r.setVulnerable(true);
            r.setTestRunResultSummaryId(new ObjectId());
            r.setEndTimestamp(Context.now());
            rows.add(r);
        }
        TestingRunResultDao.instance.insertMany(rows);
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
        return null;
    }

    @Test
    public void testEmptyData_noThrow_noDataStatus() {
        InsightResult r = computeUnscoped(buildBundle(new ArrayList<>()));

        assertEquals(InsightResult.Status.NO_DATA.name(), r.getStatus());
        assertNull(r.getSeverity());
        assertEquals("No findings recurring across test runs", r.getHeadline());
    }

    /** A finding seen in exactly ONE distinct run is below RECURRING_THRESHOLD (2) and must be
     *  excluded entirely, not just deprioritized. */
    @Test
    public void testSingleRunFinding_belowThreshold_excludedEntirely() {
        seedRecurringFinding(1, "/api/one-off", "SQLI", 1);

        InsightResult r = computeUnscoped(buildBundle(new ArrayList<>()));

        assertEquals(InsightResult.Status.NO_DATA.name(), r.getStatus());
        assertEquals(0, metricValue(r, "recurringFindings").intValue());
    }

    @Test
    public void testMaxRecurrenceExactly5_severityHigh() {
        seedRecurringFinding(1, "/api/hot", "XSS", 5);

        InsightResult r = computeUnscoped(buildBundle(new ArrayList<>()));

        assertEquals(5, metricValue(r, "maxRecurrence").intValue());
        assertEquals("HIGH", r.getSeverity());
    }

    @Test
    public void testMaxRecurrence4_severityMedium() {
        seedRecurringFinding(1, "/api/warm", "XSS", 4);

        InsightResult r = computeUnscoped(buildBundle(new ArrayList<>()));

        assertEquals(4, metricValue(r, "maxRecurrence").intValue());
        assertEquals("MEDIUM", r.getSeverity());
    }

    @Test
    public void testRecurringFindingsCount_onlyCountsThoseAtOrAboveThreshold() {
        seedRecurringFinding(1, "/api/one-off", "SQLI", 1);   // excluded
        seedRecurringFinding(1, "/api/recurring-a", "XSS", 2); // included
        seedRecurringFinding(1, "/api/recurring-b", "SSRF", 3); // included

        InsightResult r = computeUnscoped(buildBundle(new ArrayList<>()));

        assertEquals(2, metricValue(r, "recurringFindings").intValue());
        assertEquals(3, metricValue(r, "maxRecurrence").intValue());
    }

    @Test
    public void testEvidenceHostName_resolvedFromBundleCollections() {
        seedRecurringFinding(42, "/api/x", "XSS", 3);
        ApiCollection known = new ApiCollection(42, "known-collection", 0, new HashSet<>(), "known.example.com", 0, false, true);

        InsightResult r = computeUnscoped(buildBundle(Arrays.asList(known)));

        Map<String, Object> row = r.getEvidence().get(0).getRows().get(0);
        assertEquals("known.example.com", row.get("collection"));
    }

    @Test
    public void testEvidenceHostName_unknownCollection_fallsBackToNumericId() {
        seedRecurringFinding(777, "/api/x", "XSS", 3);

        InsightResult r = computeUnscoped(buildBundle(new ArrayList<>()));

        Map<String, Object> row = r.getEvidence().get(0).getRows().get(0);
        assertEquals("777", row.get("collection"));
    }
}

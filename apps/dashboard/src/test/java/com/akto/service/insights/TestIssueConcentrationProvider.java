package com.akto.service.insights;

import com.akto.MongoBasedTest;
import com.akto.dao.context.Context;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.type.URLMethods.Method;
import com.akto.service.insights.InsightProvider.Scope;
import com.akto.service.insights.providers.IssueConcentrationProvider;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.akto.util.enums.GlobalEnums.Severity;
import com.akto.util.enums.GlobalEnums.TestErrorSource;
import com.akto.util.enums.GlobalEnums.TestRunIssueStatus;

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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;

/**
 * Covers IssueConcentrationProvider: the divide-by-zero guard when totalOpen==0, the
 * CRITICAL-overrides-share rule, the topCollectionShare boundary at exactly 50% vs. just under,
 * and hostname-lookup fallback for a collection id the bundle doesn't recognize.
 */
public class TestIssueConcentrationProvider extends MongoBasedTest {

    private final IssueConcentrationProvider provider = new IssueConcentrationProvider();

    @Before
    public void setUp() {
        Context.accountId.set(ACCOUNT_ID);
        Context.userId.set(1);
        Context.contextSource.set(CONTEXT_SOURCE.API);
        TestingRunIssuesDao.instance.getMCollection().drop();
    }

    private InsightContext ctx() {
        return new InsightContext(ACCOUNT_ID, 1, CONTEXT_SOURCE.API, 0, Context.now());
    }

    /** bundle.collections is a plain constructor field, not a lazy Mongo read -- pass whatever
     *  ApiCollection list the test needs directly, no ApiCollectionsDao seeding required. */
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

    private void insertIssues(int collectionId, Severity severity, int count) {
        List<TestingRunIssues> issues = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            TestingIssuesId id = new TestingIssuesId(new ApiInfoKey(collectionId, "/api/" + collectionId + "-" + severity + "-" + i, Method.GET),
                    TestErrorSource.AUTOMATED_TESTING, "CAT");
            issues.add(new TestingRunIssues(id, severity, TestRunIssueStatus.OPEN, Context.now(), Context.now(), null, null, Context.now()));
        }
        TestingRunIssuesDao.instance.insertMany(issues);
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
    public void testNoOpenIssues_noThrow_noDataStatus_noDivideByZero() {
        InsightResult r = computeUnscoped(buildBundle(new ArrayList<>()));

        assertEquals(InsightResult.Status.NO_DATA.name(), r.getStatus());
        assertNull(r.getSeverity());
        assertEquals("No open issues", r.getHeadline());
        assertNull("topCollectionShare must not be reported when there are no collections with issues",
                metricValue(r, "topCollectionShare"));
    }

    @Test
    public void testAnyCriticalPresent_severityCritical_regardlessOfShare() {
        // Collection 1 holds a single CRITICAL among a much larger MEDIUM pile elsewhere -- share
        // of the CRITICAL-holding collection is low, but CRITICAL must still win.
        insertIssues(1, Severity.CRITICAL, 1);
        insertIssues(2, Severity.MEDIUM, 20);

        InsightResult r = computeUnscoped(buildBundle(new ArrayList<>()));

        assertEquals("CRITICAL", r.getSeverity());
    }

    @Test
    public void testTopShareExactly50Percent_noCritical_severityHigh() {
        insertIssues(1, Severity.HIGH, 5);
        insertIssues(2, Severity.MEDIUM, 5);

        InsightResult r = computeUnscoped(buildBundle(new ArrayList<>()));

        assertEquals(50L, metricValue(r, "topCollectionShare").longValue());
        assertEquals("HIGH", r.getSeverity());
    }

    @Test
    public void testTopShareJustUnder50Percent_noCritical_severityMedium() {
        insertIssues(1, Severity.HIGH, 9);   // top collection: 9/20 = 45%
        insertIssues(2, Severity.MEDIUM, 6);
        insertIssues(3, Severity.LOW, 5);

        InsightResult r = computeUnscoped(buildBundle(new ArrayList<>()));

        assertEquals(45L, metricValue(r, "topCollectionShare").longValue());
        assertEquals("MEDIUM", r.getSeverity());
    }

    @Test
    public void testEvidenceHostName_resolvedFromBundleCollections() {
        insertIssues(42, Severity.HIGH, 3);
        ApiCollection known = new ApiCollection(42, "known-collection", 0, new HashSet<>(), "known.example.com", 0, false, true);

        InsightResult r = computeUnscoped(buildBundle(Arrays.asList(known)));

        Map<String, Object> row = r.getEvidence().get(0).getRows().get(0);
        assertEquals("known.example.com", row.get("collection"));
    }

    /** The collection id in openIssueSeverityByCollection() has no matching ApiCollection in the
     *  bundle -- hostNameById.getOrDefault must fall back to the numeric id as a string rather
     *  than throwing or leaving the field null. */
    @Test
    public void testEvidenceHostName_unknownCollectionId_fallsBackToNumericId() {
        insertIssues(999, Severity.HIGH, 2);

        InsightResult r = computeUnscoped(buildBundle(new ArrayList<>()));

        Map<String, Object> row = r.getEvidence().get(0).getRows().get(0);
        assertEquals("999", row.get("collection"));
    }

    @Test
    public void testFixedIssues_notCountedTowardConcentration() {
        List<TestingRunIssues> issues = new ArrayList<>();
        TestingIssuesId id = new TestingIssuesId(new ApiInfoKey(1, "/api/fixed", Method.GET), TestErrorSource.AUTOMATED_TESTING, "CAT");
        issues.add(new TestingRunIssues(id, Severity.CRITICAL, TestRunIssueStatus.FIXED, Context.now(), Context.now(), null, null, Context.now()));
        TestingRunIssuesDao.instance.insertMany(issues);

        InsightResult r = computeUnscoped(buildBundle(new ArrayList<>()));

        assertFalse("a FIXED-only issue set must read as no open issues", InsightResult.Status.READY.name().equals(r.getStatus()));
        assertEquals(InsightResult.Status.NO_DATA.name(), r.getStatus());
    }
}

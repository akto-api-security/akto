package com.akto.service.insights;

import com.akto.MongoBasedTest;
import com.akto.dao.context.Context;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.type.URLMethods.Method;
import com.akto.service.insights.InsightProvider.Scope;
import com.akto.service.insights.providers.AgingOpenCriticalsProvider;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;

/**
 * Covers AgingOpenCriticalsProvider: the empty case, the exact 30-day wall-clock boundary (just
 * under vs. just over -- mirroring InsightLazySources.agingOpenIssues()'s own cutoff filter,
 * which is strictly-less-than), the HIGH/MEDIUM split on whether any aging issue is
 * CRITICAL/HIGH, and a null-severity row degrading gracefully instead of throwing.
 */
public class TestAgingOpenCriticalsProvider extends MongoBasedTest {

    private static final int THIRTY_DAYS_SECONDS = 30 * 24 * 3600;

    private final AgingOpenCriticalsProvider provider = new AgingOpenCriticalsProvider();

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

    private TestingRunIssues issueOf(int collectionId, String url, String subCategory, Severity severity,
                                      TestRunIssueStatus status, int creationTime) {
        TestingIssuesId id = new TestingIssuesId(new ApiInfoKey(collectionId, url, Method.GET), TestErrorSource.AUTOMATED_TESTING, subCategory);
        return new TestingRunIssues(id, severity, status, creationTime, creationTime, null, null, creationTime);
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
        assertEquals("No open issues older than 30 days", r.getHeadline());
    }

    /** The underlying filter is Filters.lt(creationTime, cutoff) -- strictly less-than, and the
     *  cutoff itself is recomputed from the real wall clock inside InsightLazySources at call
     *  time, not passed in from the test. A margin of a few seconds (rather than the literal
     *  same second used to seed the row) keeps this deterministic against that real elapsed time
     *  while still exercising "just under 30 days -> not aging". */
    @Test
    public void testCreationTimeJustUnderThirtyDays_notAging() {
        int justUnder = Context.now() - THIRTY_DAYS_SECONDS + 5; // 29d 23h59m55s ago
        TestingRunIssuesDao.instance.insertMany(Arrays.asList(
                issueOf(1, "/api/just-under-cutoff", "CAT", Severity.CRITICAL, TestRunIssueStatus.OPEN, justUnder)));

        InsightResult r = computeUnscoped(buildBundle());

        assertEquals(InsightResult.Status.NO_DATA.name(), r.getStatus());
        assertEquals(0, metricValue(r, "agingOpenIssues").intValue());
    }

    @Test
    public void testCreationTimeJustOverThirtyDays_isAging() {
        int justOver = Context.now() - THIRTY_DAYS_SECONDS - 5; // 30d 0h0m5s ago
        TestingRunIssuesDao.instance.insertMany(Arrays.asList(
                issueOf(1, "/api/just-over-cutoff", "CAT", Severity.MEDIUM, TestRunIssueStatus.OPEN, justOver)));

        InsightResult r = computeUnscoped(buildBundle());

        assertEquals(1, metricValue(r, "agingOpenIssues").intValue());
        assertEquals(InsightResult.Status.READY.name(), r.getStatus());
    }

    @Test
    public void testAgingCriticalPresent_severityHigh() {
        int thirtyOneDaysAgo = Context.now() - THIRTY_DAYS_SECONDS - (24 * 3600);
        TestingRunIssuesDao.instance.insertMany(Arrays.asList(
                issueOf(1, "/api/aging-critical", "CAT", Severity.CRITICAL, TestRunIssueStatus.OPEN, thirtyOneDaysAgo),
                issueOf(1, "/api/aging-low", "CAT2", Severity.LOW, TestRunIssueStatus.OPEN, thirtyOneDaysAgo)));

        InsightResult r = computeUnscoped(buildBundle());

        assertEquals("HIGH", r.getSeverity());
        assertEquals(2, metricValue(r, "agingOpenIssues").intValue());
        assertEquals(1, metricValue(r, "agingCriticalOrHigh").intValue());
    }

    @Test
    public void testAgingOnlyMediumAndLow_severityMedium() {
        int thirtyOneDaysAgo = Context.now() - THIRTY_DAYS_SECONDS - (24 * 3600);
        TestingRunIssuesDao.instance.insertMany(Arrays.asList(
                issueOf(1, "/api/aging-medium", "CAT", Severity.MEDIUM, TestRunIssueStatus.OPEN, thirtyOneDaysAgo),
                issueOf(1, "/api/aging-low", "CAT2", Severity.LOW, TestRunIssueStatus.OPEN, thirtyOneDaysAgo)));

        InsightResult r = computeUnscoped(buildBundle());

        assertEquals("MEDIUM", r.getSeverity());
        assertEquals(0, metricValue(r, "agingCriticalOrHigh").intValue());
    }

    /** FIXED issues, however old, must never be counted -- only OPEN status ages. */
    @Test
    public void testFixedAgingIssue_excluded() {
        int thirtyOneDaysAgo = Context.now() - THIRTY_DAYS_SECONDS - (24 * 3600);
        TestingRunIssuesDao.instance.insertMany(Arrays.asList(
                issueOf(1, "/api/aging-but-fixed", "CAT", Severity.CRITICAL, TestRunIssueStatus.FIXED, thirtyOneDaysAgo)));

        InsightResult r = computeUnscoped(buildBundle());

        assertEquals(InsightResult.Status.NO_DATA.name(), r.getStatus());
    }

    /** A row with a null severity (malformed/legacy data) must not throw and must not be counted
     *  toward criticalOrHigh -- it still counts toward the raw aging total. */
    @Test
    public void testNullSeverityRow_gracefulDegradation_notCountedAsCriticalOrHigh() {
        int thirtyOneDaysAgo = Context.now() - THIRTY_DAYS_SECONDS - (24 * 3600);
        TestingIssuesId id = new TestingIssuesId(new ApiInfoKey(1, "/api/null-severity", Method.GET), TestErrorSource.AUTOMATED_TESTING, "CAT");
        TestingRunIssues nullSeverityIssue = new TestingRunIssues(id, null, TestRunIssueStatus.OPEN,
                thirtyOneDaysAgo, thirtyOneDaysAgo, null, null, thirtyOneDaysAgo);
        TestingRunIssuesDao.instance.insertMany(Arrays.asList(nullSeverityIssue));

        InsightResult r = computeUnscoped(buildBundle());

        assertEquals(1, metricValue(r, "agingOpenIssues").intValue());
        assertEquals(0, metricValue(r, "agingCriticalOrHigh").intValue());
        assertEquals("MEDIUM", r.getSeverity());
    }
}

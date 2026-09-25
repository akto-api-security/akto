package com.akto.service.insights;

import com.akto.MongoBasedTest;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing.VulnerableTestingRunResultDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.type.URLMethods.Method;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.akto.util.enums.GlobalEnums.Severity;
import com.akto.util.enums.GlobalEnums.TestErrorSource;
import com.akto.util.enums.GlobalEnums.TestRunIssueStatus;

import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;

/**
 * Covers InsightLazySources -- the actual Mongo reads InsightDataBundle's lazy accessors
 * delegate to. Focused on the business logic the CLAUDE.md for this feature calls out
 * explicitly: apiInfoRows()'s per-row partial-failure tolerance, issueRecurrence()'s
 * $addToSet-not-$sum:1 merge across both physical result collections, and
 * addRbacCollectionFilter's null-collectionIds-means-no-filter convention.
 */
public class TestInsightLazySources extends MongoBasedTest {

    @Before
    public void setUp() {
        Context.accountId.set(ACCOUNT_ID);
        Context.userId.set(1);
        Context.contextSource.set(CONTEXT_SOURCE.API);

        ApiCollectionsDao.instance.getMCollection().drop();
        ApiInfoDao.instance.getMCollection().drop();
        TestingRunIssuesDao.instance.getMCollection().drop();
        TestingRunResultDao.instance.getMCollection().drop();
        VulnerableTestingRunResultDao.instance.getMCollection().drop();

        for (CONTEXT_SOURCE cs : CONTEXT_SOURCE.values()) {
            UsersCollectionsList.deleteContextCollectionsForUser(ACCOUNT_ID, cs);
        }
        UsersCollectionsList.deleteCollectionIdsFromCache(1, ACCOUNT_ID);
    }

    private InsightLazySources lazySources() {
        return new InsightLazySources(new InsightContext(ACCOUNT_ID, 1, CONTEXT_SOURCE.API, 0, Context.now()));
    }

    private void insertUntaggedCollection(int id) {
        ApiCollectionsDao.instance.insertMany(java.util.Collections.singletonList(
                new ApiCollection(id, "collection-" + id, 0, new HashSet<>(), null, 0, false, true)));
    }

    /** Every read in this class is RBAC-scoped to UsersCollectionsList's collection set (see the
     *  same trap documented in this repo's ask/CLAUDE.md) -- apiInfoRows()/agingOpenIssues()/
     *  openIssueSeverityByCollection() via the DAO-level filter, issueRecurrence() via its own
     *  addRbacCollectionFilter. Tests that aren't specifically targeting that scoping stub
     *  UsersCollectionsList to "unrestricted" here so seeded data isn't silently filtered away by
     *  a missing ApiCollection registration rather than the business logic under test. */
    private void runUnscoped(Runnable action) {
        try (MockedStatic<UsersCollectionsList> m = mockStatic(UsersCollectionsList.class)) {
            m.when(() -> UsersCollectionsList.getCollectionsIdForUser(anyInt(), anyInt())).thenReturn(null);
            action.run();
        }
    }

    // ── apiInfoRows(): per-row partial-failure tolerance ────────────────────────────────

    /** calculateActualAuth() NPEs when allAuthTypesFound is null (it iterates the set directly).
     *  One bad row must not sink the other, healthy rows in the same read. */
    @Test
    public void testApiInfoRows_oneRowsAuthCalculationThrows_otherRowsStillReturned() {
        insertUntaggedCollection(2001);

        ApiInfo healthy = new ApiInfo(new ApiInfoKey(2001, "/api/healthy", Method.GET));
        Set<Set<String>> allAuth = new HashSet<>();
        Set<String> only = new HashSet<>();
        only.add(ApiInfo.AuthType.UNAUTHENTICATED);
        allAuth.add(only);
        healthy.setAllAuthTypesFound(allAuth);

        ApiInfo malformed = new ApiInfo(new ApiInfoKey(2001, "/api/malformed", Method.GET));
        malformed.setAllAuthTypesFound(null);

        ApiInfoDao.instance.insertMany(Arrays.asList(healthy, malformed));

        runUnscoped(() -> {
            List<ApiInfo> rows = lazySources().apiInfoRows();
            assertEquals("the malformed row must not sink the whole read", 2, rows.size());

            ApiInfo healthyResult = findByUrl(rows, "/api/healthy");
            ApiInfo malformedResult = findByUrl(rows, "/api/malformed");
            assertEquals(Arrays.asList(ApiInfo.AuthType.UNAUTHENTICATED), healthyResult.getActualAuthType());
            assertNull("a row whose auth calculation threw must be returned with actualAuthType left "
                    + "null, not silently defaulted to something callers could misread as 'no auth'",
                    malformedResult.getActualAuthType());
        });
    }

    private ApiInfo findByUrl(List<ApiInfo> rows, String url) {
        for (ApiInfo r : rows) if (url.equals(r.getId().getUrl())) return r;
        throw new AssertionError("row not found: " + url);
    }

    // ── agingOpenIssues(): 30-day-wall-clock + OPEN-status filter ───────────────────────

    @Test
    public void testAgingOpenIssues_onlyOpenAndOlderThan30Days() {
        insertUntaggedCollection(2002);
        int now = Context.now();
        int thirtyOneDaysAgo = now - (31 * 24 * 3600);

        TestingRunIssuesDao.instance.insertMany(Arrays.asList(
                issueOf(2002, "/api/aging-open", "CAT_A", Severity.CRITICAL, TestRunIssueStatus.OPEN, thirtyOneDaysAgo),
                issueOf(2002, "/api/recent-open", "CAT_B", Severity.CRITICAL, TestRunIssueStatus.OPEN, now),
                issueOf(2002, "/api/aging-fixed", "CAT_C", Severity.CRITICAL, TestRunIssueStatus.FIXED, thirtyOneDaysAgo)));

        runUnscoped(() -> {
            List<TestingRunIssues> rows = lazySources().agingOpenIssues();
            assertEquals(1, rows.size());
            assertEquals("/api/aging-open", rows.get(0).getId().getApiInfoKey().getUrl());
        });
    }

    private TestingRunIssues issueOf(int collectionId, String url, String subCategory, Severity severity,
                                      TestRunIssueStatus status, int creationTime) {
        TestingIssuesId id = new TestingIssuesId(new ApiInfoKey(collectionId, url, Method.GET), TestErrorSource.AUTOMATED_TESTING, subCategory);
        return new TestingRunIssues(id, severity, status, creationTime, creationTime, null, null, creationTime);
    }

    // ── openIssueSeverityByCollection(): grouped counts per collection ──────────────────

    @Test
    public void testOpenIssueSeverityByCollection_groupsBySeverityPerCollection_excludesFixed() {
        int now = Context.now();
        TestingRunIssuesDao.instance.insertMany(Arrays.asList(
                issueOf(3001, "/a", "C1", Severity.CRITICAL, TestRunIssueStatus.OPEN, now),
                issueOf(3001, "/b", "C2", Severity.HIGH, TestRunIssueStatus.OPEN, now),
                issueOf(3002, "/c", "C3", Severity.MEDIUM, TestRunIssueStatus.OPEN, now),
                issueOf(3002, "/d", "C4", Severity.CRITICAL, TestRunIssueStatus.FIXED, now)));

        runUnscoped(() -> {
            Map<Integer, Map<String, Integer>> result = lazySources().openIssueSeverityByCollection();

            assertEquals(Integer.valueOf(1), result.get(3001).get("CRITICAL"));
            assertEquals(Integer.valueOf(1), result.get(3001).get("HIGH"));
            assertEquals(Integer.valueOf(1), result.get(3002).get("MEDIUM"));
            assertNull("the FIXED issue must not be counted", result.get(3002).get("CRITICAL"));
        });
    }

    // ── issueRecurrence(): $addToSet (not $sum:1), and merge across both collections ────

    /** Two result rows within the SAME testRunResultSummaryId (a rerun inside one summary) must
     *  count as ONE distinct run, not two -- the exact overcounting bug $addToSet avoids. */
    @Test
    public void testIssueRecurrence_rerunWithinSameSummary_countsAsOneDistinctRun() {
        ApiInfoKey key = new ApiInfoKey(4001, "/api/rerun", Method.GET);
        ObjectId sharedSummaryId = new ObjectId();

        TestingRunResultDao.instance.insertMany(Arrays.asList(
                vulnerableResult(key, "SQLI", sharedSummaryId, true),
                vulnerableResult(key, "SQLI", sharedSummaryId, true)));

        runUnscoped(() -> {
            List<IssueRecurrenceRow> rows = lazySources().issueRecurrence();

            assertEquals(1, rows.size());
            assertEquals("a rerun within one summary must not inflate the recurrence count", 1, rows.get(0).getDistinctRuns());
        });
    }

    /** Merges the legacy testing_run_result (vulnerable=true rows) collection with the newer
     *  vulnerable_testing_run_results collection into one combined distinct-run count for the
     *  same finding -- neither double-counted nor read from only one source. */
    @Test
    public void testIssueRecurrence_mergesAcrossBothPhysicalCollections() {
        ApiInfoKey key = new ApiInfoKey(4002, "/api/merged", Method.GET);

        TestingRunResultDao.instance.insertMany(java.util.Collections.singletonList(
                vulnerableResult(key, "XSS", new ObjectId(), true)));
        VulnerableTestingRunResultDao.instance.insertMany(java.util.Collections.singletonList(
                vulnerableResult(key, "XSS", new ObjectId(), true)));

        runUnscoped(() -> {
            List<IssueRecurrenceRow> rows = lazySources().issueRecurrence();

            assertEquals(1, rows.size());
            assertEquals("both collections' distinct summary ids must merge into one combined count",
                    2, rows.get(0).getDistinctRuns());
        });
    }

    /** Only the legacy collection mixes vulnerable and non-vulnerable rows -- a vulnerable=false
     *  row there must be excluded (vulnerable_testing_run_results is vulnerable-only by
     *  construction, so this filter only applies to the legacy read). */
    @Test
    public void testIssueRecurrence_legacyCollection_nonVulnerableRowsExcluded() {
        ApiInfoKey excludedKey = new ApiInfoKey(4003, "/api/not-vulnerable", Method.GET);
        ApiInfoKey includedKey = new ApiInfoKey(4003, "/api/vulnerable", Method.GET);

        TestingRunResultDao.instance.insertMany(Arrays.asList(
                vulnerableResult(excludedKey, "SSRF", new ObjectId(), false),
                vulnerableResult(includedKey, "SSRF", new ObjectId(), true)));

        runUnscoped(() -> {
            List<IssueRecurrenceRow> rows = lazySources().issueRecurrence();

            assertEquals(1, rows.size());
            assertEquals("/api/vulnerable", rows.get(0).getUrl());
        });
    }

    private TestingRunResult vulnerableResult(ApiInfoKey key, String testSubType, ObjectId summaryId, boolean vulnerable) {
        TestingRunResult r = new TestingRunResult();
        r.setApiInfoKey(key);
        r.setTestSubType(testSubType);
        r.setVulnerable(vulnerable);
        r.setTestRunResultSummaryId(summaryId);
        r.setEndTimestamp(Context.now());
        return r;
    }

    // ── addRbacCollectionFilter (via issueRecurrence): null collectionIds -> no filter ──

    /** A null collectionIds list (this codebase's "RBAC not in force for this caller, e.g. an
     *  admin/unscoped caller" convention) must mean NO filter is applied -- every collection's
     *  data is visible, not zero of it. */
    @Test
    public void testAddRbacCollectionFilter_nullCollectionIds_appliesNoFilter() {
        try (MockedStatic<UsersCollectionsList> m = mockStatic(UsersCollectionsList.class)) {
            m.when(() -> UsersCollectionsList.getCollectionsIdForUser(anyInt(), anyInt())).thenReturn(null);

            TestingRunResultDao.instance.insertMany(Arrays.asList(
                    vulnerableResult(new ApiInfoKey(5001, "/a", Method.GET), "T1", new ObjectId(), true),
                    vulnerableResult(new ApiInfoKey(5002, "/b", Method.GET), "T2", new ObjectId(), true)));

            List<IssueRecurrenceRow> rows = lazySources().issueRecurrence();

            Set<Integer> collectionIdsSeen = new HashSet<>();
            for (IssueRecurrenceRow row : rows) collectionIdsSeen.add(row.getApiCollectionId());
            assertEquals("null collectionIds must mean no filter -- both collections' data visible",
                    new HashSet<>(Arrays.asList(5001, 5002)), collectionIdsSeen);
        }
    }

    /** A non-null, explicit collectionIds list must narrow results to only that set. */
    @Test
    public void testAddRbacCollectionFilter_explicitCollectionIds_onlyMatchingCollectionReturned() {
        try (MockedStatic<UsersCollectionsList> m = mockStatic(UsersCollectionsList.class)) {
            m.when(() -> UsersCollectionsList.getCollectionsIdForUser(anyInt(), anyInt()))
                    .thenReturn(java.util.Collections.singletonList(5001));

            TestingRunResultDao.instance.insertMany(Arrays.asList(
                    vulnerableResult(new ApiInfoKey(5001, "/a", Method.GET), "T1", new ObjectId(), true),
                    vulnerableResult(new ApiInfoKey(5002, "/b", Method.GET), "T2", new ObjectId(), true)));

            List<IssueRecurrenceRow> rows = lazySources().issueRecurrence();

            assertEquals(1, rows.size());
            assertEquals(5001, rows.get(0).getApiCollectionId());
        }
    }
}

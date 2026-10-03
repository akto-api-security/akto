package com.akto.service.insights;

import com.akto.MongoBasedTest;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.type.URLMethods.Method;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.akto.util.enums.GlobalEnums.Severity;
import com.akto.util.enums.GlobalEnums.TestErrorSource;
import com.akto.util.enums.GlobalEnums.TestRunIssueStatus;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Covers the InsightDataBundle surface added for the Ask Akto overlay: the memo() cache (a lazy
 * read must happen at most once per bundle instance) and withCtx() (a lazy read must run under
 * the BUNDLE's own captured account/user/contextSource, not whatever the calling thread's
 * ambient Context ThreadLocals happen to hold). See this class's own javadoc: "a lazily-touched
 * supplier runs on whichever thread arrives first -- a PROVIDER_EXECUTOR worker, not the servlet
 * thread" -- the withCtx tests below simulate exactly that.
 */
public class TestInsightDataBundle extends MongoBasedTest {

    @Before
    public void setUp() {
        Context.accountId.set(ACCOUNT_ID);
        Context.userId.set(1);
        Context.contextSource.set(CONTEXT_SOURCE.API);

        ApiCollectionsDao.instance.getMCollection().drop();
        ApiInfoDao.instance.getMCollection().drop();
        TestingRunIssuesDao.instance.getMCollection().drop();

        for (CONTEXT_SOURCE cs : CONTEXT_SOURCE.values()) {
            UsersCollectionsList.deleteContextCollectionsForUser(ACCOUNT_ID, cs);
        }
        UsersCollectionsList.deleteCollectionIdsFromCache(1, ACCOUNT_ID);
    }

    private InsightContext ctx(int accountId) {
        return new InsightContext(accountId, 1, CONTEXT_SOURCE.API, 0, Context.now());
    }

    /** Every field the bundle doesn't need for these tests is handed an empty-but-non-null
     *  value, matching the "every collection field is empty, never null" convention its own
     *  javadoc documents for InsightDataLoader-built bundles. */
    private InsightDataBundle buildBundle(InsightContext ctx) {
        return new InsightDataBundle(ctx,
                new java.util.ArrayList<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(),
                new java.util.ArrayList<>(), new java.util.ArrayList<>(), new HashSet<>(), new HashMap<>(),
                new java.util.ArrayList<>(), new java.util.ArrayList<>(),
                new java.util.ArrayList<>(), new java.util.ArrayList<>(), new java.util.ArrayList<>(),
                true, new java.util.ArrayList<>(), new HashMap<>(),
                new InsightsThreatBackendAccess(), new InsightLazySources(ctx));
    }

    /** RBAC collection-scoping trap (see this repo's CLAUDE.md): ApiInfoDao's 5-arg findAll and
     *  TestingRunIssuesDao.addCollectionsFilterForDashboard both narrow results to
     *  UsersCollectionsList.getContextCollectionsForUser's API-scoped collection set. Without an
     *  untagged ApiCollection document to match, that set is empty and every read below would
     *  silently return zero rows -- a false "the threadlocal bug happened" reading, not a real
     *  one. Call this once per distinct collectionId before seeding rows under it. */
    private void insertUntaggedCollection(int id) {
        ApiCollectionsDao.instance.insertMany(java.util.Collections.singletonList(
                new ApiCollection(id, "collection-" + id, 0, new HashSet<>(), null, 0, false, true)));
    }

    private void insertApiInfo(int collectionId, String url) {
        ApiInfoDao.instance.insertMany(java.util.Collections.singletonList(
                new ApiInfo(new ApiInfoKey(collectionId, url, Method.GET))));
    }

    private void insertAgingOpenCritical(int collectionId, String url) {
        int thirtyOneDaysAgo = Context.now() - (31 * 24 * 3600);
        TestingIssuesId id = new TestingIssuesId(new ApiInfoKey(collectionId, url, Method.GET),
                TestErrorSource.AUTOMATED_TESTING, "CAT");
        TestingRunIssuesDao.instance.insertMany(java.util.Collections.singletonList(
                new TestingRunIssues(id, Severity.CRITICAL, TestRunIssueStatus.OPEN,
                        thirtyOneDaysAgo, thirtyOneDaysAgo, null, null, thirtyOneDaysAgo)));
    }

    // ── memo(): one read per bundle instance ────────────────────────────────────────────

    @Test
    public void testApiInfoRows_memoized_secondReadReturnsStaleCachedValue() {
        insertUntaggedCollection(1001);
        insertApiInfo(1001, "/api/one");
        InsightDataBundle bundle = buildBundle(ctx(ACCOUNT_ID));

        assertEquals(1, bundle.apiInfoRows().size());

        insertApiInfo(1001, "/api/two"); // mutate the underlying data after the first read
        assertEquals("a memoized accessor must return the STALE cached value on a second call, "
                + "not re-query and pick up the new row", 1, bundle.apiInfoRows().size());
    }

    @Test
    public void testAgingOpenIssues_memoized_secondReadReturnsStaleCachedValue() {
        insertUntaggedCollection(1002);
        insertAgingOpenCritical(1002, "/api/aging-one");
        InsightDataBundle bundle = buildBundle(ctx(ACCOUNT_ID));

        assertEquals(1, bundle.agingOpenIssues().size());

        insertAgingOpenCritical(1002, "/api/aging-two");
        assertEquals("agingOpenIssues() must also be memoized -- second call stays stale",
                1, bundle.agingOpenIssues().size());
    }

    @Test
    public void testIsApiInfoRowsTruncated_triggersTheMemoizedReadAsASideEffect() {
        insertUntaggedCollection(1003);
        insertApiInfo(1003, "/api/one");
        InsightDataBundle bundle = buildBundle(ctx(ACCOUNT_ID));

        // Calling isApiInfoRowsTruncated() FIRST (never calling apiInfoRows() directly) must
        // still force the underlying read to run, since the truncated flag is a side effect of it.
        assertFalse(bundle.isApiInfoRowsTruncated());
        assertEquals(1, bundle.apiInfoRows().size());
    }

    /** Smoke-covers the remaining three memo() wrappers (sensitiveApiCountBySubType,
     *  openIssueSeverityByCollection, issueRecurrence) -- their own business logic is exercised
     *  against InsightLazySources directly in TestInsightLazySources; this only proves the bundle
     *  wiring delegates and never returns null. */
    @Test
    public void testRemainingLazyAccessors_delegateToLazySourcesAndNeverReturnNull() {
        InsightDataBundle bundle = buildBundle(ctx(ACCOUNT_ID));

        assertNotNull(bundle.sensitiveApiCountBySubType());
        assertNotNull(bundle.openIssueSeverityByCollection());
        assertNotNull(bundle.issueRecurrence());
    }

    // ── withCtx(): the bundle's own captured context, not the calling thread's ambient one ──

    /**
     * The flagship test for this whole batch. Sets Context on the MAIN thread to match the
     * bundle's own accountId, then reads apiInfoRows() from a brand-new plain Thread that has
     * never touched Context (its ThreadLocals are naturally unset there -- exactly what a
     * PROVIDER_EXECUTOR worker looks like on first touch). If withCtx() actually restores the
     * bundle's own ctx fields (rather than trusting the calling thread's ambient state), the
     * worker thread must still see this account's real data. Per-account data lives in a
     * separate physical Mongo database keyed off Context.accountId (AccountsContextDao.
     * getDBName()) -- so a broken withCtx would silently read from database "null" and return
     * an empty list, not throw, which is exactly the failure mode this proves doesn't happen.
     */
    @Test
    public void testWithCtx_workerThreadWithNoAmbientContext_stillReadsThisAccountsRealData() throws Exception {
        insertUntaggedCollection(1004);
        insertApiInfo(1004, "/api/real-account-data");
        InsightDataBundle bundle = buildBundle(ctx(ACCOUNT_ID));

        // Simulate the main/servlet thread's own context, already correctly set.
        Context.accountId.set(ACCOUNT_ID);
        Context.userId.set(1);
        Context.contextSource.set(CONTEXT_SOURCE.API);

        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Callable<java.util.List<ApiInfo>> readOnWorker = () -> {
                // Sanity check this really is a fresh thread with no ambient Context -- if this
                // assertion ever fails, the test stops proving what it claims to prove.
                assertEquals(null, Context.accountId.get());
                return bundle.apiInfoRows();
            };
            Future<java.util.List<ApiInfo>> future = worker.submit(readOnWorker);
            java.util.List<ApiInfo> rows = future.get(10, TimeUnit.SECONDS);

            assertEquals("withCtx must restore the bundle's OWN accountId on the worker thread, "
                    + "not rely on (or be defeated by) that thread's unset ambient Context", 1, rows.size());
        } finally {
            worker.shutdownNow();
        }
    }

    /** After withCtx() runs, the CALLING thread's own prior (non-null) Context must be restored
     *  -- not left as the bundle's accountId, and not wiped to null. */
    @Test
    public void testWithCtx_restoresCallingThreadsPriorNonNullContext_afterTheRead() {
        int callerAccountId = ACCOUNT_ID + 1;
        insertApiInfo(1005, "/api/x");
        InsightDataBundle bundle = buildBundle(ctx(ACCOUNT_ID)); // bundle's own ctx: ACCOUNT_ID

        Context.accountId.set(callerAccountId); // caller's ambient context: a DIFFERENT account
        try {
            bundle.apiInfoRows();
            assertEquals("the calling thread's own prior context must be restored after the read, "
                    + "not left as whatever the bundle used internally",
                    Integer.valueOf(callerAccountId), Context.accountId.get());
        } finally {
            Context.accountId.set(ACCOUNT_ID);
        }
    }

    /** If the calling thread had NO Context set beforehand, withCtx() must remove it afterward
     *  (the prevAcc == null branch) rather than leaving the bundle's accountId set behind. */
    @Test
    public void testWithCtx_removesContext_whenCallingThreadHadNoneSetBeforehand() {
        insertApiInfo(1006, "/api/y");
        InsightDataBundle bundle = buildBundle(ctx(ACCOUNT_ID));

        Context.accountId.remove();
        try {
            bundle.apiInfoRows();
            assertEquals("no prior context on the calling thread must mean none is left behind either",
                    null, Context.accountId.get());
        } finally {
            Context.accountId.set(ACCOUNT_ID);
        }
    }
}

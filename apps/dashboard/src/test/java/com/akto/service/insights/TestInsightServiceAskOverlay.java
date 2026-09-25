package com.akto.service.insights;

import com.akto.MongoBasedTest;
import com.akto.dao.AgentUsersDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.GuardrailPoliciesDao;
import com.akto.dao.McpAllowlistDao;
import com.akto.dao.McpAuditInfoDao;
import com.akto.dao.RBACDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.agentic_sessions.UserAnalysisDataDao;
import com.akto.dao.context.Context;
import com.akto.dao.nhi_governance.NhiIdentityDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing.VulnerableTestingRunResultDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiAccessType;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.RBAC.Role;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.type.URLMethods.Method;
import com.akto.service.ask.RecommendationCatalog;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.DashboardMode;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.akto.util.enums.GlobalEnums.Severity;
import com.akto.util.enums.GlobalEnums.TestErrorSource;
import com.akto.util.enums.GlobalEnums.TestRunIssueStatus;

import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

/**
 * Covers the two methods InsightService gained for the Ask Akto overlay:
 * groupVisible() (the RBAC fail-open/fail-closed gate) and buildAskOverlay() (Layer 1
 * recommendations + Layer 2 insight tiles + Layer 3 what-changed feed orchestration).
 * Every other method on InsightService (listInsights, getInsightDetail, computeSafely,
 * boundEvidence, ...) is pre-existing and intentionally not covered here.
 */
public class TestInsightServiceAskOverlay extends MongoBasedTest {

    private final InsightService service = new InsightService();

    @Before
    public void setUp() throws Exception {
        Context.accountId.set(ACCOUNT_ID);
        Context.userId.set(1);
        Context.contextSource.set(CONTEXT_SOURCE.API);

        ApiCollectionsDao.instance.getMCollection().drop();
        ApiInfoDao.instance.getMCollection().drop();
        SingleTypeInfoDao.instance.getMCollection().drop();
        TestingRunIssuesDao.instance.getMCollection().drop();
        TestingRunResultDao.instance.getMCollection().drop();
        VulnerableTestingRunResultDao.instance.getMCollection().drop();
        McpAuditInfoDao.instance.getMCollection().drop();
        McpAllowlistDao.instance.getMCollection().drop();
        GuardrailPoliciesDao.instance.getMCollection().drop();
        UserAnalysisDataDao.instance.getMCollection().drop();
        AgentUsersDao.instance.getMCollection().drop();
        NhiIdentityDao.instance.getMCollection().drop();

        for (CONTEXT_SOURCE cs : CONTEXT_SOURCE.values()) {
            UsersCollectionsList.deleteContextCollectionsForUser(ACCOUNT_ID, cs);
        }
        UsersCollectionsList.deleteCollectionIdsFromCache(1, ACCOUNT_ID);

        clearBundleCache();
    }

    /** BUNDLE_CACHE is a static, 60s-TTL, process-wide map keyed by accountId/userId/contextSource/
     *  startTs/endTs — every test here reuses the same account/user/contextSource, so without
     *  clearing it a later test could silently read an earlier test's cached (now stale) bundle. */
    @SuppressWarnings("unchecked")
    private void clearBundleCache() throws Exception {
        Field f = InsightService.class.getDeclaredField("BUNDLE_CACHE");
        f.setAccessible(true);
        Map<String, Object> cache = (Map<String, Object>) f.get(null);
        cache.clear();
    }

    private InsightContext ctx() {
        return new InsightContext(ACCOUNT_ID, 1, CONTEXT_SOURCE.API, 0, Context.now());
    }

    private void insertUntaggedCollection(int id) {
        ApiCollectionsDao.instance.insertMany(java.util.Collections.singletonList(
                new ApiCollection(id, "collection-" + id, 0, new HashSet<>(), null, 0, false, true)));
    }

    // ── groupVisible(): the four RBAC branches ──────────────────────────────────────────

    /** Fail-open #1, and the FIRST check: even with role resolving to null (which on its own
     *  would fail closed, per the third test below), an unmetered deployment must still see
     *  the group. If a future refactor reordered the checks, this test would start failing. */
    @Test
    public void testGroupVisible_notMetered_failsOpen_regardlessOfRole() {
        try (MockedStatic<DashboardMode> dashboardMode = mockStatic(DashboardMode.class);
             MockedStatic<RBACDao> rbacDao = mockStatic(RBACDao.class)) {
            dashboardMode.when(DashboardMode::isMetered).thenReturn(false);
            rbacDao.when(() -> RBACDao.getCurrentRoleForUser(anyInt(), anyInt())).thenReturn(null);

            assertTrue(service.groupVisible(ctx(), InsightId.Group.API_POSTURE));
        }
    }

    /** Fail-open #2: metered, but the account isn't RBAC-licensed — again true regardless of role. */
    @Test
    public void testGroupVisible_meteredButRbacFeatureUnavailable_failsOpen_regardlessOfRole() {
        try (MockedStatic<DashboardMode> dashboardMode = mockStatic(DashboardMode.class);
             MockedStatic<UsageMetricCalculator> usage = mockStatic(UsageMetricCalculator.class);
             MockedStatic<RBACDao> rbacDao = mockStatic(RBACDao.class)) {
            dashboardMode.when(DashboardMode::isMetered).thenReturn(true);
            usage.when(() -> UsageMetricCalculator.isRbacFeatureAvailable(anyInt())).thenReturn(false);
            rbacDao.when(() -> RBACDao.getCurrentRoleForUser(anyInt(), anyInt())).thenReturn(null);

            assertTrue(service.groupVisible(ctx(), InsightId.Group.TESTING_POSTURE));
        }
    }

    /** Fail-closed: once RBAC is actually in force, a null role (no RBAC row resolvable) must
     *  deny access — the opposite outcome of the two fail-open tests above. */
    @Test
    public void testGroupVisible_meteredAndRbacAvailable_roleNull_failsClosed() {
        try (MockedStatic<DashboardMode> dashboardMode = mockStatic(DashboardMode.class);
             MockedStatic<UsageMetricCalculator> usage = mockStatic(UsageMetricCalculator.class);
             MockedStatic<RBACDao> rbacDao = mockStatic(RBACDao.class)) {
            dashboardMode.when(DashboardMode::isMetered).thenReturn(true);
            usage.when(() -> UsageMetricCalculator.isRbacFeatureAvailable(anyInt())).thenReturn(true);
            rbacDao.when(() -> RBACDao.getCurrentRoleForUser(anyInt(), anyInt())).thenReturn(null);

            assertFalse(service.groupVisible(ctx(), InsightId.Group.API_POSTURE));
        }
    }

    /** Once RBAC is in force with a resolvable role, the decision defers to
     *  role.getReadWriteAccessForFeature(group.getRequiredFeature()) != NO_ACCESS. */
    @Test
    public void testGroupVisible_meteredAndRbacAvailable_realRole_grantedAndDenied() {
        try (MockedStatic<DashboardMode> dashboardMode = mockStatic(DashboardMode.class);
             MockedStatic<UsageMetricCalculator> usage = mockStatic(UsageMetricCalculator.class);
             MockedStatic<RBACDao> rbacDao = mockStatic(RBACDao.class)) {
            dashboardMode.when(DashboardMode::isMetered).thenReturn(true);
            usage.when(() -> UsageMetricCalculator.isRbacFeatureAvailable(anyInt())).thenReturn(true);

            rbacDao.when(() -> RBACDao.getCurrentRoleForUser(anyInt(), anyInt())).thenReturn(Role.MEMBER);
            assertTrue("MEMBER has read/write on API_COLLECTIONS (API_POSTURE's required feature)",
                    service.groupVisible(ctx(), InsightId.Group.API_POSTURE));

            rbacDao.when(() -> RBACDao.getCurrentRoleForUser(anyInt(), anyInt())).thenReturn(Role.NO_ACCESS);
            assertFalse("NO_ACCESS role strategy maps every feature to NO_ACCESS",
                    service.groupVisible(ctx(), InsightId.Group.TESTING_POSTURE));
        }
    }

    // ── buildAskOverlay(): omit-don't-fail, per-provider isolation, caps, layer independence ──

    /** A group the caller can't see must be silently omitted (reported via omittedGroups), not
     *  fail the whole response — and a visible sibling group must still render its tiles. */
    @Test
    public void testBuildAskOverlay_notVisibleGroup_omittedSilently_responseStillSucceeds() {
        int collectionId = 8001;
        insertUntaggedCollection(collectionId);
        insertUnauthPublicSensitiveApi(collectionId, "/api/pure-unauth");

        try (MockedStatic<DashboardMode> dashboardMode = mockStatic(DashboardMode.class)) {
            // Real groupVisible() would return true for both groups here (unmetered); we only
            // want TESTING_POSTURE hidden, so spy just that one call and let API_POSTURE run real.
            dashboardMode.when(DashboardMode::isMetered).thenReturn(false);

            InsightService spyService = Mockito.spy(new InsightService());
            Mockito.doReturn(false).when(spyService)
                    .groupVisible(any(InsightContext.class), eq(InsightId.Group.TESTING_POSTURE));

            Set<InsightId.Group> groups = new LinkedHashSet<>();
            groups.add(InsightId.Group.API_POSTURE);
            groups.add(InsightId.Group.TESTING_POSTURE);

            AskOverlayResponse response = spyService.buildAskOverlay(ctx(), CONTEXT_SOURCE.API, groups, 10, 10);

            assertEquals(java.util.Collections.singletonList("TESTING_POSTURE"), response.getOmittedGroups());
            assertNotNull(response.getInsightTiles());
            boolean anyTestingPostureTile = response.getInsightTiles().stream()
                    .anyMatch(t -> "TESTING_POSTURE".equals(t.getGroup()));
            assertFalse("omitted group must contribute no tiles", anyTestingPostureTile);
            boolean anyApiPostureTile = response.getInsightTiles().stream()
                    .anyMatch(t -> "API_POSTURE".equals(t.getGroup()));
            assertTrue("visible sibling group must still render", anyApiPostureTile);
            assertNotNull(response.getRecommendations());
            assertNotNull(response.getWhatChanged());
        }
    }

    /** One provider throwing (SensitiveDataHotspotsProvider, via UsageMetricCalculator.
     *  getDemosAndDeactivated() blowing up) must not take down its sibling provider in the same
     *  group, nor a provider in a different group — computeSafely's per-provider catch is what
     *  buildAskOverlay's tile loop relies on for this. */
    @Test
    public void testBuildAskOverlay_oneProviderThrows_siblingAndOtherGroupTilesSurvive() {
        int collectionId = 8002;
        insertUntaggedCollection(collectionId);
        insertUnauthPublicSensitiveApi(collectionId, "/api/pure-unauth");
        insertOldOpenCriticalIssue(collectionId, "/api/old-critical");

        try (MockedStatic<DashboardMode> dashboardMode = mockStatic(DashboardMode.class);
             MockedStatic<UsageMetricCalculator> usage = mockStatic(UsageMetricCalculator.class)) {
            dashboardMode.when(DashboardMode::isMetered).thenReturn(false);
            usage.when(UsageMetricCalculator::getDemosAndDeactivated).thenThrow(new RuntimeException("boom"));
            usage.when(() -> UsageMetricCalculator.excludeDemosAndDeactivated(any())).thenCallRealMethod();

            Set<InsightId.Group> groups = new LinkedHashSet<>();
            groups.add(InsightId.Group.API_POSTURE);
            groups.add(InsightId.Group.TESTING_POSTURE);

            AskOverlayResponse response = service.buildAskOverlay(ctx(), CONTEXT_SOURCE.API, groups, 10, 10);

            Set<String> tileIds = new HashSet<>();
            for (InsightTile t : response.getInsightTiles()) tileIds.add(t.getInsightId());

            assertTrue("sibling API_POSTURE provider must still produce a tile",
                    tileIds.contains(InsightId.UNAUTHENTICATED_SENSITIVE_APIS.name()));
            assertTrue("TESTING_POSTURE (a different group) must be unaffected",
                    tileIds.contains(InsightId.AGING_OPEN_CRITICALS.name()));
            assertFalse("the throwing provider's own insight must not appear as a tile",
                    tileIds.contains(InsightId.SENSITIVE_DATA_HOTSPOTS.name()));
            assertTrue("neither group was itself invisible", response.getOmittedGroups().isEmpty());
        }
    }

    /** Seeds enough CRITICAL/HIGH candidates to cross BOTH caps at once: TESTING_POSTURE alone
     *  produces 3 tile-worthy insights (cut to 2 by the per-group cap), and combined with
     *  API_POSTURE's 1 candidate that's still 3 -- cut to 2 by tileLimit. Confirms the two caps
     *  compose correctly rather than one masking the other. */
    @Test
    public void testBuildAskOverlay_perGroupCapAndTileLimit_bothEnforcedTogether() {
        int collectionId = 8003;
        insertUntaggedCollection(collectionId);
        insertUnauthPublicSensitiveApi(collectionId, "/api/pure-unauth"); // API_POSTURE: 1 CRITICAL candidate
        insertOldOpenCriticalIssue(collectionId, "/api/old-critical");    // -> AGING (HIGH) + ISSUE_CONCENTRATION (CRITICAL)
        insertRecurringFinding(collectionId, "/api/recurring", 5);       // -> ISSUE_RECURRENCE (HIGH), 5 distinct runs

        try (MockedStatic<DashboardMode> dashboardMode = mockStatic(DashboardMode.class)) {
            dashboardMode.when(DashboardMode::isMetered).thenReturn(false);

            Set<InsightId.Group> groups = new LinkedHashSet<>();
            groups.add(InsightId.Group.API_POSTURE);
            groups.add(InsightId.Group.TESTING_POSTURE);

            AskOverlayResponse response = service.buildAskOverlay(ctx(), CONTEXT_SOURCE.API, groups, 2, 10);

            List<InsightTile> tiles = response.getInsightTiles();
            assertEquals("tileLimit=2 must be the final size even though 3 candidates survived the per-group cap",
                    2, tiles.size());

            Set<String> tileIds = new HashSet<>();
            for (InsightTile t : tiles) tileIds.add(t.getInsightId());
            assertTrue(tileIds.contains(InsightId.UNAUTHENTICATED_SENSITIVE_APIS.name()));
            assertTrue("CRITICAL beats HIGH in the cross-group sort", tileIds.contains(InsightId.ISSUE_CONCENTRATION.name()));
            assertFalse("ISSUE_RECURRENCE must already be gone -- TESTING_POSTURE's 2-per-group cap dropped it",
                    tileIds.contains(InsightId.ISSUE_RECURRENCE.name()));
            assertFalse("AGING_OPEN_CRITICALS survived the per-group cap but not the overall tileLimit",
                    tileIds.contains(InsightId.AGING_OPEN_CRITICALS.name()));
        }
    }

    /** Layer 1 (recommendations) is wrapped in its own try/catch, independent of Layers 2/3 --
     *  a Layer 1 failure must degrade recommendations to empty without preventing insight tiles
     *  or the what-changed feed from still being computed and returned. */
    @Test
    public void testBuildAskOverlay_layer1RecommendationsThrows_layer2And3StillSucceed() {
        int collectionId = 8004;
        insertUntaggedCollection(collectionId);
        insertUnauthPublicSensitiveApi(collectionId, "/api/pure-unauth");
        ApiInfo newApi = new ApiInfo(new ApiInfoKey(collectionId, "/api/new-endpoint", Method.GET));
        newApi.setDiscoveredTimestamp(Context.now());
        ApiInfoDao.instance.insertMany(java.util.Collections.singletonList(newApi));

        try (MockedStatic<DashboardMode> dashboardMode = mockStatic(DashboardMode.class);
             MockedStatic<RecommendationCatalog> recCatalog = mockStatic(RecommendationCatalog.class)) {
            dashboardMode.when(DashboardMode::isMetered).thenReturn(false);
            recCatalog.when(() -> RecommendationCatalog.compute(any())).thenThrow(new RuntimeException("layer1 boom"));

            Set<InsightId.Group> groups = new LinkedHashSet<>();
            groups.add(InsightId.Group.API_POSTURE);

            AskOverlayResponse response = service.buildAskOverlay(ctx(), CONTEXT_SOURCE.API, groups, 10, 10);

            assertNotNull(response);
            assertTrue("Layer 1 failure must degrade to an empty list, not propagate", response.getRecommendations().isEmpty());
            assertFalse("Layer 2 must still succeed independently of Layer 1", response.getInsightTiles().isEmpty());
            assertFalse("Layer 3 must still succeed independently of Layer 1", response.getWhatChanged().isEmpty());
        }
    }

    // ── seeding helpers ──────────────────────────────────────────────────────────────────

    private void insertUnauthPublicSensitiveApi(int collectionId, String url) {
        ApiInfo api = new ApiInfo(new ApiInfoKey(collectionId, url, Method.GET));
        Set<Set<String>> allAuth = new HashSet<>();
        Set<String> only = new HashSet<>();
        only.add(ApiInfo.AuthType.UNAUTHENTICATED);
        allAuth.add(only);
        api.setAllAuthTypesFound(allAuth);
        Set<ApiAccessType> accessTypes = new HashSet<>();
        accessTypes.add(ApiAccessType.PUBLIC);
        api.setApiAccessTypes(accessTypes);
        api.setIsSensitive(true);
        ApiInfoDao.instance.insertMany(java.util.Collections.singletonList(api));
    }

    private void insertOldOpenCriticalIssue(int collectionId, String url) {
        int thirtyOneDaysAgo = Context.now() - (31 * 24 * 3600);
        TestingIssuesId id = new TestingIssuesId(new ApiInfoKey(collectionId, url, Method.GET),
                TestErrorSource.AUTOMATED_TESTING, "CAT_OLD_CRITICAL");
        TestingRunIssues issue = new TestingRunIssues(id, Severity.CRITICAL, TestRunIssueStatus.OPEN,
                thirtyOneDaysAgo, thirtyOneDaysAgo, null, null, thirtyOneDaysAgo);
        TestingRunIssuesDao.instance.insertMany(java.util.Collections.singletonList(issue));
    }

    private void insertRecurringFinding(int collectionId, String url, int distinctRuns) {
        ApiInfoKey key = new ApiInfoKey(collectionId, url, Method.GET);
        List<TestingRunResult> rows = new java.util.ArrayList<>();
        for (int i = 0; i < distinctRuns; i++) {
            TestingRunResult result = new TestingRunResult();
            result.setApiInfoKey(key);
            result.setTestSubType("SQLI");
            result.setVulnerable(true);
            result.setTestRunResultSummaryId(new ObjectId());
            result.setEndTimestamp(Context.now());
            rows.add(result);
        }
        TestingRunResultDao.instance.insertMany(rows);
    }
}

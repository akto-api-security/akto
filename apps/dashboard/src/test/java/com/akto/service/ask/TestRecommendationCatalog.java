package com.akto.service.ask;

import com.akto.MongoBasedTest;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.GuardrailPoliciesDao;
import com.akto.dao.McpAllowlistDao;
import com.akto.dao.McpAuditInfoDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.agentic_sessions.UserAnalysisDataDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.ComponentRiskAnalysis;
import com.akto.dto.McpAllowlist;
import com.akto.dto.McpAuditInfo;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.type.URLMethods.Method;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListMaliciousRequestsResponse;
import com.akto.types.CappedSet;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.akto.util.enums.GlobalEnums.Severity;
import com.akto.util.enums.GlobalEnums.TestErrorSource;
import com.akto.util.enums.GlobalEnums.TestRunIssueStatus;
import com.akto.utils.threat_detection.ThreatDetectionBackendClient;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;

/**
 * Covers RecommendationCatalog.compute() for all three domains (API/AGENTIC/ENDPOINT):
 * zero-data degradation, severity threshold crossings, the exact-array-match auth filter,
 * the response-vs-request sensitive-data distinction, the malicious-skills unanchored-regex
 * false-positive risk, threatActivity()'s degrade-on-backend-failure contract, and per-account
 * data isolation. See apps/dashboard/src/main/java/com/akto/service/ask/CLAUDE.md for the
 * feature's known correctness traps this file guards against re-introducing.
 */
public class TestRecommendationCatalog extends MongoBasedTest {

    @Before
    public void setUp() {
        Context.accountId.set(ACCOUNT_ID);
        Context.userId.set(1);
        Context.contextSource.remove();

        ApiCollectionsDao.instance.getMCollection().drop();
        ApiInfoDao.instance.getMCollection().drop();
        SingleTypeInfoDao.instance.getMCollection().drop();
        TestingRunIssuesDao.instance.getMCollection().drop();
        McpAuditInfoDao.instance.getMCollection().drop();
        McpAllowlistDao.instance.getMCollection().drop();
        GuardrailPoliciesDao.instance.getMCollection().drop();
        UserAnalysisDataDao.instance.getMCollection().drop();

        // UsersCollectionsList.getContextCollectionsForUser caches its per-(accountId, source)
        // result for 120s (CONTEXT_EXPIRY_TIME) in a static, JVM-wide map. Without busting it here,
        // a collection inserted by one test wouldn't be picked up by contextCollectionIds() in a
        // later test running within that window — a real cross-test pollution hazard given every
        // test in this class shares MongoBasedTest.ACCOUNT_ID.
        for (CONTEXT_SOURCE cs : CONTEXT_SOURCE.values()) {
            UsersCollectionsList.deleteContextCollectionsForUser(ACCOUNT_ID, cs);
        }
    }

    /**
     * Mirrors production request context: the palette always sends an x-context-source header
     * matching the requested domain, which a servlet filter turns into Context.contextSource
     * before RecommendationCatalog.compute() ever runs. AccountsContextDaoWithRbac's own RBAC
     * filter (see ApiInfoDao's 5-arg findAll, the one maliciousSkillsTotal() deliberately uses —
     * modifyFilters() in AccountsContextDaoWithRbac) keys off that ThreadLocal, not off the
     * domain parameter passed here — with Context.userId set but contextSource left null, that
     * filter falls back to API-scoped collection ids regardless of which domain is being computed,
     * so an ENDPOINT/AGENTIC-tagged collection would be silently RBAC'd out to a false "0 count"
     * that has nothing to do with the business logic under test. Setting it explicitly here is
     * what a real request already does.
     */
    private List<Recommendation> computeForDomain(CONTEXT_SOURCE domain) {
        Context.contextSource.set(domain);
        try {
            return RecommendationCatalog.compute(domain);
        } finally {
            Context.contextSource.remove();
        }
    }

    private Recommendation findById(List<Recommendation> recs, String id) {
        for (Recommendation r : recs) {
            if (id.equals(r.getId())) return r;
        }
        fail("Recommendation not found: " + id + " in " + recs);
        return null;
    }

    private void insertUntaggedCollection(int id) {
        ApiCollection collection = new ApiCollection(id, "collection-" + id, 0, new HashSet<>(), null, 0, false, true);
        ApiCollectionsDao.instance.insertMany(java.util.Collections.singletonList(collection));
    }

    private void insertEndpointTaggedCollection(int id) {
        ApiCollection collection = new ApiCollection(id, "endpoint-collection-" + id, 0, new HashSet<>(), null, 0, false, true);
        collection.setTagsList(java.util.Collections.singletonList(
                new CollectionTags(0, Constants.AKTO_ENDPOINT_SOURCE_TAG, Constants.AKTO_ENDPOINT_SOURCE_VALUE, CollectionTags.TagSource.USER)));
        ApiCollectionsDao.instance.insertMany(java.util.Collections.singletonList(collection));
    }

    // ── compute(null) and zero-data degradation ─────────────────────────────────────────

    @Test
    public void testCompute_null_defaultsToApiTileSet() {
        List<Recommendation> nullDomain = RecommendationCatalog.compute(null);
        List<Recommendation> apiDomain = computeForDomain(CONTEXT_SOURCE.API);

        assertEquals(4, nullDomain.size());
        HashSet<String> nullIds = new HashSet<>();
        for (Recommendation r : nullDomain) nullIds.add(r.getId());
        HashSet<String> apiIds = new HashSet<>();
        for (Recommendation r : apiDomain) apiIds.add(r.getId());
        assertEquals("compute(null) must dispatch to the same tile set as compute(API)", apiIds, nullIds);
    }

    @Test
    public void testCompute_emptyCollections_api_allCountsZeroAndSeverityNull() {
        List<Recommendation> recs = computeForDomain(CONTEXT_SOURCE.API);
        assertEquals(4, recs.size());
        for (Recommendation r : recs) {
            assertEquals("count should be 0 for " + r.getId(), 0, r.getCount());
            assertNull("severity should be null (per the 'severity only when count>0' convention) for " + r.getId(), r.getSeverity());
        }
    }

    @Test
    public void testCompute_emptyCollections_agentic_allCountsZeroAndSeverityNull() throws Exception {
        try (MockedStatic<ThreatDetectionBackendClient> threatMock = mockStatic(ThreatDetectionBackendClient.class)) {
            threatMock.when(() -> ThreatDetectionBackendClient.listMaliciousRequests(
                            anyInt(), anyInt(), anyInt(), anyInt(), any(), any(), any()))
                    .thenThrow(new RuntimeException("stubbed — no live threat-detection-backend in tests"));

            List<Recommendation> recs = computeForDomain(CONTEXT_SOURCE.AGENTIC);
            assertEquals(4, recs.size());
            for (Recommendation r : recs) {
                assertEquals("count should be 0 for " + r.getId(), 0, r.getCount());
                assertNull("severity should be null for " + r.getId(), r.getSeverity());
            }
        }
    }

    @Test
    public void testCompute_emptyCollections_endpoint_allCountsZeroAndSeverityNull() {
        List<Recommendation> recs = computeForDomain(CONTEXT_SOURCE.ENDPOINT);
        assertEquals(3, recs.size());
        for (Recommendation r : recs) {
            assertEquals("count should be 0 for " + r.getId(), 0, r.getCount());
            assertNull("severity should be null for " + r.getId(), r.getSeverity());
        }
    }

    // ── openCriticals(): severity/status filtering ──────────────────────────────────────

    @Test
    public void testOpenCriticals_onlyCountsOpenCriticalIssues_severityCriticalWhenPositive() {
        int collectionId = 5001;
        insertUntaggedCollection(collectionId);

        TestingRunIssues openCritical = issueOf(collectionId, "/api/a", "CAT_OPEN_CRITICAL", Severity.CRITICAL, TestRunIssueStatus.OPEN);
        TestingRunIssues fixedCritical = issueOf(collectionId, "/api/b", "CAT_FIXED_CRITICAL", Severity.CRITICAL, TestRunIssueStatus.FIXED);
        TestingRunIssues openHigh = issueOf(collectionId, "/api/c", "CAT_OPEN_HIGH", Severity.HIGH, TestRunIssueStatus.OPEN);
        TestingRunIssuesDao.instance.insertMany(Arrays.asList(openCritical, fixedCritical, openHigh));

        Recommendation rec = findById(computeForDomain(CONTEXT_SOURCE.API), "open_criticals");

        assertEquals(1, rec.getCount());
        assertEquals("CRITICAL", rec.getSeverity());
    }

    private TestingRunIssues issueOf(int collectionId, String url, String subCategory, Severity severity, TestRunIssueStatus status) {
        TestingIssuesId id = new TestingIssuesId(new ApiInfoKey(collectionId, url, Method.GET), TestErrorSource.AUTOMATED_TESTING, subCategory);
        int now = Context.now();
        return new TestingRunIssues(id, severity, status, now, now, null, null, now);
    }

    // ── unauthenticatedSensitive(): exact-array-match filter ────────────────────────────

    @Test
    public void testUnauthenticatedSensitive_exactArrayMatch_onlyPureUnauthenticatedApiCounted() {
        int collectionId = 5002;
        insertUntaggedCollection(collectionId);

        ApiInfo pureUnauth = new ApiInfo(new ApiInfoKey(collectionId, "/api/pure-unauth", Method.GET));
        pureUnauth.setIsSensitive(true);
        pureUnauth.setAllAuthTypesFound(setOfSets(setOf(ApiInfo.AuthType.UNAUTHENTICATED)));

        ApiInfo comboAuth = new ApiInfo(new ApiInfoKey(collectionId, "/api/combo-auth", Method.GET));
        comboAuth.setIsSensitive(true);
        comboAuth.setAllAuthTypesFound(setOfSets(setOf(ApiInfo.AuthType.UNAUTHENTICATED, ApiInfo.AuthType.API_TOKEN)));

        ApiInfo unauthButNotSensitive = new ApiInfo(new ApiInfoKey(collectionId, "/api/unauth-not-sensitive", Method.GET));
        unauthButNotSensitive.setIsSensitive(false);
        unauthButNotSensitive.setAllAuthTypesFound(setOfSets(setOf(ApiInfo.AuthType.UNAUTHENTICATED)));

        ApiInfoDao.instance.insertMany(Arrays.asList(pureUnauth, comboAuth, unauthButNotSensitive));

        Recommendation rec = findById(computeForDomain(CONTEXT_SOURCE.API), "unauth_sensitive");

        assertEquals("only the sensitive API whose allAuthTypesFound is exactly [[UNAUTHENTICATED]] should count", 1, rec.getCount());
        assertEquals("HIGH", rec.getSeverity());
    }

    private Set<String> setOf(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    private Set<Set<String>> setOfSets(Set<String> inner) {
        Set<Set<String>> outer = new HashSet<>();
        outer.add(inner);
        return outer;
    }

    // ── sensitiveDataTypesInResponse(): response-side vs request-side ──────────────────

    @Test
    public void testSensitiveDataTypesInResponse_responseSideCounted_requestSideOnlyNotCounted() {
        int collectionId = 5003;
        insertUntaggedCollection(collectionId);

        SingleTypeInfo.ParamId responseParamId = new SingleTypeInfo.ParamId(
                "/api/resp", "GET", 200, false, "email", SingleTypeInfo.EMAIL, collectionId, false);
        SingleTypeInfo responseSti = new SingleTypeInfo(responseParamId, new HashSet<>(), new HashSet<>(), 0, 0, 0,
                new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);

        SingleTypeInfo.ParamId requestOnlyParamId = new SingleTypeInfo.ParamId(
                "/api/req", "GET", -1, false, "email", SingleTypeInfo.EMAIL, collectionId, false);
        SingleTypeInfo requestOnlySti = new SingleTypeInfo(requestOnlyParamId, new HashSet<>(), new HashSet<>(), 0, 0, 0,
                new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);

        SingleTypeInfoDao.instance.insertMany(Arrays.asList(responseSti, requestOnlySti));

        Recommendation rec = findById(computeForDomain(CONTEXT_SOURCE.API), "sensitive_data_types");

        assertEquals("responseCode==-1 (request side) must NOT be counted — only response-side sensitive data types", 1, rec.getCount());
        assertEquals("HIGH", rec.getSeverity());
    }

    // ── maliciousSkillsTotal(): substring match + tag gate + unanchored-regex risk ──────

    @Test
    public void testMaliciousSkillsTotal_matchingUrlWithMaliciousTag_isCounted() {
        int collectionId = 5004;
        insertEndpointTaggedCollection(collectionId);

        ApiInfo skillApi = new ApiInfo(new ApiInfoKey(collectionId, "/agent/skills/currency-converter", Method.GET));
        skillApi.setTagsList(java.util.Collections.singletonList(new CollectionTags(0, "malicious-skill-tag", "true", CollectionTags.TagSource.USER)));
        ApiInfoDao.instance.insertMany(java.util.Collections.singletonList(skillApi));

        Recommendation rec = findById(computeForDomain(CONTEXT_SOURCE.ENDPOINT), "malicious_skills");

        assertEquals(1, rec.getCount());
        assertEquals("CRITICAL", rec.getSeverity());
    }

    @Test
    public void testMaliciousSkillsTotal_matchingUrlWithoutMaliciousTag_notCounted() {
        int collectionId = 5005;
        insertEndpointTaggedCollection(collectionId);

        ApiInfo skillApiNoTag = new ApiInfo(new ApiInfoKey(collectionId, "/agent/skills/weather", Method.GET));
        ApiInfoDao.instance.insertMany(java.util.Collections.singletonList(skillApiNoTag));

        Recommendation rec = findById(computeForDomain(CONTEXT_SOURCE.ENDPOINT), "malicious_skills");

        assertEquals("URL matches the regex but has no malicious-skill-tag=true — must not be counted", 0, rec.getCount());
        assertNull(rec.getSeverity());
    }

    /**
     * The url regex is unanchored ("skills/" can appear anywhere in the path), so a completely
     * unrelated endpoint whose path merely CONTAINS "skills/" as a substring — e.g. an
     * "/campaigns/adminskills/launch" marketing endpoint, not an agent skill at all — still
     * matches. This documents that real false-positive risk (flagged, not fixed, in
     * RecommendationCatalog's own javadoc) so a future regex tightening shows up here as an
     * intentional behavior change rather than a silent one.
     */
    @Test
    public void testMaliciousSkillsTotal_unanchoredRegex_matchesUnrelatedUrlContainingSubstring() {
        int collectionId = 5006;
        insertEndpointTaggedCollection(collectionId);

        ApiInfo falsePositive = new ApiInfo(new ApiInfoKey(collectionId, "/campaigns/adminskills/launch", Method.GET));
        falsePositive.setTagsList(java.util.Collections.singletonList(new CollectionTags(0, "malicious-skill-tag", "true", CollectionTags.TagSource.USER)));
        ApiInfoDao.instance.insertMany(java.util.Collections.singletonList(falsePositive));

        Recommendation rec = findById(computeForDomain(CONTEXT_SOURCE.ENDPOINT), "malicious_skills");

        assertEquals("unanchored regex means '/campaigns/adminskills/launch' matches 'skills/' as a substring", 1, rec.getCount());
    }

    // ── maliciousMcpTools() / unapprovedMcpServers(): AGENTIC MCP governance ───────────

    private void insertMcpTaggedCollection(int id, String hostName, boolean malicious, boolean local) {
        ApiCollection collection = new ApiCollection(id, "mcp-collection-" + id, 0, new HashSet<>(), hostName, 0, false, true);
        List<CollectionTags> tags = new java.util.ArrayList<>();
        tags.add(new CollectionTags(0, Constants.AKTO_MCP_SERVER_TAG, "true", CollectionTags.TagSource.USER));
        if (malicious) tags.add(new CollectionTags(0, Constants.AKTO_MALICIOUS_MCP_SERVER_TAG, "true", CollectionTags.TagSource.USER));
        if (local) tags.add(new CollectionTags(0, "local-mcp-server", "true", CollectionTags.TagSource.USER));
        collection.setTagsList(tags);
        ApiCollectionsDao.instance.insertMany(java.util.Collections.singletonList(collection));
    }

    /**
     * maliciousMcpTools() unions two independent signals into one distinct-service-name count:
     * an ApiCollection tagged malicious-mcp-server=true, and an McpAuditInfo row whose
     * componentRiskAnalysis flags the component malicious. Seeds one of each, under different
     * service names, and confirms both are counted (not deduplicated away, not one masking
     * the other).
     */
    @Test
    public void testMaliciousMcpTools_taggedCollectionAndAuditRow_bothCountedAsDistinctServices() {
        insertMcpTaggedCollection(7001, "malicious-collection-tag", true, false);

        McpAuditInfo maliciousAudit = new McpAuditInfo();
        maliciousAudit.setMcpHost("malicious-audit-row");
        maliciousAudit.setContextSource(CONTEXT_SOURCE.AGENTIC.name());
        maliciousAudit.setComponentRiskAnalysis(new ComponentRiskAnalysis(false, true, "flagged by scan"));
        McpAuditInfoDao.instance.insertMany(java.util.Collections.singletonList(maliciousAudit));

        Recommendation rec = findById(computeForDomain(CONTEXT_SOURCE.AGENTIC), "malicious_mcp_tools");

        assertEquals(2, rec.getCount());
        assertEquals("CRITICAL", rec.getSeverity());
    }

    /**
     * unapprovedMcpServers() flags a server if it's local OR not on the allowlist/not
     * audit-approved. Seeds a local server (counted regardless of approval), a server that's
     * neither local nor approved (counted), and a server that IS on the allowlist (not counted).
     */
    @Test
    public void testUnapprovedMcpServers_localAndUnapproved_countedApprovedExcluded() {
        insertMcpTaggedCollection(7002, "local-server", false, true);
        insertMcpTaggedCollection(7003, "random-unapproved-server", false, false);
        insertMcpTaggedCollection(7004, "approved-server", false, false);

        McpAllowlist allowlistEntry = new McpAllowlist();
        allowlistEntry.setName("approved-server");
        McpAllowlistDao.instance.insertMany(java.util.Collections.singletonList(allowlistEntry));

        Recommendation rec = findById(computeForDomain(CONTEXT_SOURCE.AGENTIC), "unapproved_mcp_servers");

        assertEquals("local server + unapproved server counted, allowlisted server excluded", 2, rec.getCount());
        assertEquals("MEDIUM", rec.getSeverity());
    }

    // ── threatActivity(): degrade on backend failure + severity filtering ──────────────

    @Test
    public void testThreatActivity_backendThrows_degradesToZeroCount() throws Exception {
        try (MockedStatic<ThreatDetectionBackendClient> threatMock = mockStatic(ThreatDetectionBackendClient.class)) {
            threatMock.when(() -> ThreatDetectionBackendClient.listMaliciousRequests(
                            anyInt(), anyInt(), anyInt(), anyInt(), any(), any(), any()))
                    .thenThrow(new RuntimeException("simulated backend outage"));

            Recommendation rec = findById(computeForDomain(CONTEXT_SOURCE.AGENTIC), "threat_activity");

            assertEquals(0, rec.getCount());
            assertNull(rec.getSeverity());
        }
    }

    @Test
    public void testThreatActivity_countsOnlyCriticalAndHighSeverityEvents() throws Exception {
        ListMaliciousRequestsResponse response = ListMaliciousRequestsResponse.newBuilder()
                .addMaliciousEvents(ListMaliciousRequestsResponse.MaliciousEvent.newBuilder().setSeverity("CRITICAL").build())
                .addMaliciousEvents(ListMaliciousRequestsResponse.MaliciousEvent.newBuilder().setSeverity("HIGH").build())
                .addMaliciousEvents(ListMaliciousRequestsResponse.MaliciousEvent.newBuilder().setSeverity("MEDIUM").build())
                .addMaliciousEvents(ListMaliciousRequestsResponse.MaliciousEvent.newBuilder().setSeverity("LOW").build())
                .build();

        try (MockedStatic<ThreatDetectionBackendClient> threatMock = mockStatic(ThreatDetectionBackendClient.class)) {
            threatMock.when(() -> ThreatDetectionBackendClient.listMaliciousRequests(
                            anyInt(), anyInt(), anyInt(), anyInt(), any(), any(), any()))
                    .thenReturn(response);

            Recommendation rec = findById(computeForDomain(CONTEXT_SOURCE.AGENTIC), "threat_activity");

            assertEquals("only CRITICAL and HIGH events should be counted, not MEDIUM/LOW", 2, rec.getCount());
            assertEquals("CRITICAL", rec.getSeverity());
        }
    }

    // ── Per-account data isolation ───────────────────────────────────────────────────────

    /**
     * Akto's multi-tenancy is one Mongo database per account (AccountsContextDao#getDBName ==
     * Context.accountId.get()), so data written under a different account must be invisible to
     * this account's compute() regardless of collection-level RBAC. Seeds an open CRITICAL issue
     * under a second account, then confirms the account-under-test's own count is unaffected.
     */
    @Test
    public void testCrossAccountData_notCountedForDifferentAccount() {
        int otherAccountId = 909090;
        int otherCollectionId = 6001;
        try {
            Context.accountId.set(otherAccountId);
            insertUntaggedCollection(otherCollectionId);
            TestingRunIssues otherAccountIssue = issueOf(otherCollectionId, "/other/api", "CAT_OTHER", Severity.CRITICAL, TestRunIssueStatus.OPEN);
            TestingRunIssuesDao.instance.insertMany(java.util.Collections.singletonList(otherAccountIssue));
        } finally {
            Context.accountId.set(ACCOUNT_ID);
        }

        Recommendation rec = findById(computeForDomain(CONTEXT_SOURCE.API), "open_criticals");

        assertEquals(0, rec.getCount());
        assertNull(rec.getSeverity());
    }
}

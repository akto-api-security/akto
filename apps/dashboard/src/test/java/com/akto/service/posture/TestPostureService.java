package com.akto.service.posture;

import com.akto.MongoBasedTest;
import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.action.threat_detection.HostSeverityCount;
import com.akto.action.threat_detection.ThreatCategoryCount;
import com.akto.dao.threat_detection.ComplianceClauseCoverageDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.threat_detection.ComplianceClauseCoverage;
import com.akto.dto.threat_detection.ComplianceClauseCoverage.ClauseHit;
import com.akto.dto.traffic.CollectionTags;
import com.akto.service.insights.InsightContext;
import com.akto.service.insights.InsightDataBundle;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.BasicDBObject;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Logic-level coverage for PostureService — every test drives it through its real public/
 * package-private entry points (buildSummary, fetchDrill, trendBucketBoundaries,
 * bucketedSparkline, matchedPolicyCounts, paginate, ...) with hand-built fixtures rather than
 * mocking, matching this repo's existing action-test convention (see TestCustomAuthTypeAction).
 * PostureService's own private KPI/panel/drill builders have no other entry point, so exercising
 * buildSummary/fetchDrill with both a fully-populated bundle and an empty one is what actually
 * covers them — this is not incidental, it's the only way to reach that code from outside the
 * class.
 */
public class TestPostureService extends MongoBasedTest {

    private static final int TREND_START = 1_000_000;
    private static final int TREND_END = 1_008_000; // 8000s range, TREND_BUCKET_COUNT=8 -> 1000s/bucket

    // buildSummary (and the framework-readiness drill) reads ComplianceClauseCoverageDao — JUnit
    // doesn't guarantee method execution order, so without this reset a doc a later-run framework-
    // readiness test inserted leaks into an earlier-declared test's "no scan yet" assertions.
    @Before
    public void dropComplianceClauseCoverage() {
        ComplianceClauseCoverageDao.instance.getMCollection().drop();
    }

    // ── Fixture builders ─────────────────────────────────────────────────────────

    private static ApiCollection endpointCollection(int id, String hostName, int startTs) {
        ApiCollection c = new ApiCollection(id, hostName, startTs, new HashSet<>(), hostName, 0, false, false);
        c.setTagsList(Collections.singletonList(
                new CollectionTags(0, Constants.AKTO_ENDPOINT_SOURCE_TAG, Constants.AKTO_ENDPOINT_SOURCE_VALUE, null)));
        return c;
    }

    private static GuardrailPolicies policy(String name, String behaviour, List<String> applyToDeviceIds) {
        GuardrailPolicies p = new GuardrailPolicies();
        p.setName(name);
        p.setActive(true);
        p.setBehaviour(behaviour);
        p.setApplyToDeviceIds(applyToDeviceIds);
        return p;
    }

    private static DashboardMaliciousEvent event(int apiCollectionId, long timestamp, String category,
                                                  String host, String actor, String severity, String status) {
        return new DashboardMaliciousEvent("id-" + timestamp, actor, "filter", "/x", null, apiCollectionId,
                "1.1.1.1", null, null, timestamp, "type", "ref-" + timestamp, category, "sub", null, null, null,
                false, status, "GUARDRAIL", host, null, severity, null);
    }

    /** Four vendor-shaped ApiCollections (see InsightUtil#endpointVendorName's own hostname
     *  contract): OpenAI x2 devices, DeepSeek x1 (unapproved), Anthropic x1. Doubles as both
     *  bundle.collections and endpointCollections in these tests, same as production does for a
     *  request under CONTEXT_SOURCE.ENDPOINT. */
    private static List<ApiCollection> fourVendorCollections() {
        return Arrays.asList(
                endpointCollection(101, "deviceA.ai-agent.chatgpt.com", 1_000_100),  // -> OpenAI
                endpointCollection(102, "deviceB.ai-agent.chatgpt.com", 1_000_200),  // -> OpenAI
                endpointCollection(103, "deviceC.ai-agent.deepseek.com", 1_000_300), // -> DeepSeek
                endpointCollection(104, "deviceA.ai-agent.claude.ai", 1_000_400));   // -> Anthropic
    }

    /** block / warn / alert / approval — one policy per GuardrailPolicies.behaviour PostureService
     *  actually branches on, so enforcementFunnel's every stage (incl. the "unclassified" gap) and
     *  dataLeavingBreakdown's DEFAULT/"Others" split both get real coverage. */
    private static List<GuardrailPolicies> fourBehaviourPolicies() {
        GuardrailPolicies piiBlock = policy("Default-Customer PII", "block", null);
        piiBlock.setPiiTypes(Collections.singletonList(new GuardrailPolicies.PiiType("Customer PII", "Block", 1)));

        GuardrailPolicies sourceWarn = policy("Default-Source code detection", "warn", null);
        GuardrailPolicies.LLMRule llmRule = new GuardrailPolicies.LLMRule();
        llmRule.setEnabled(true);
        Map<String, List<String>> compliance = new HashMap<>();
        compliance.put("NIST AI RMF", Collections.singletonList("Clause 1"));
        llmRule.setCompliance(compliance);
        sourceWarn.setLlmRule(llmRule);

        GuardrailPolicies customAlert = policy("Custom Alert Policy", "alert", null);
        GuardrailPolicies approval = policy("Approval Policy", "approval", null);

        return Arrays.asList(piiBlock, sourceWarn, customAlert, approval);
    }

    private static List<ThreatCategoryCount> fourMatchingSubCategoryCounts() {
        return Arrays.asList(
                new ThreatCategoryCount("Default-Customer PII", "PII-Customer", 40),
                new ThreatCategoryCount("Default-Source code detection", "SourceCode", 25),
                new ThreatCategoryCount("Custom Alert Policy", "Other", 10),
                new ThreatCategoryCount("Approval Policy", "Other", 7),
                new ThreatCategoryCount("UnknownCategory", "x", 5)); // no matching policy name -> dropped
    }

    private static List<DashboardMaliciousEvent> fiveTrendWindowEvents() {
        return Arrays.asList(
                event(101, 1_000_500, "Default-Customer PII", "deviceA.ai-agent.chatgpt.com", "alice", "CRITICAL", "ACTIVE"),
                event(103, 1_003_500, "Default-Source code detection", "deviceC.ai-agent.deepseek.com", "carol", "HIGH", "ACTIVE"),
                event(102, 1_005_500, "Custom Alert Policy", "deviceB.ai-agent.chatgpt.com", "bob", "MEDIUM", "UNDER_REVIEW"),
                event(104, 1_006_500, "Approval Policy", "deviceA.ai-agent.claude.ai", "alice", "LOW", "ACTIVE"),
                event(999, 1_007_500, "UnknownCategory", "deviceX.something", "mallory", "CRITICAL", "ACTIVE"));
    }

    private static InsightDataBundle bundle(List<ApiCollection> collections, List<GuardrailPolicies> policies,
                                             Set<String> allowlistNamesLower, Map<String, String> deviceIdToUsername,
                                             Map<Integer, Integer> collectionLastTrafficSeen,
                                             List<HostSeverityCount> hostSeverityCounts,
                                             List<ThreatCategoryCount> subCategoryCounts,
                                             boolean threatBackendAvailable, int startTs, int endTs) {
        InsightContext ctx = new InsightContext(ACCOUNT_ID, 1, CONTEXT_SOURCE.AGENTIC, startTs, endTs);
        return new InsightDataBundle(ctx, collections, new HashMap<>(), deviceIdToUsername, new HashMap<>(),
                new ArrayList<>(), policies, allowlistNamesLower, new HashMap<>(), new ArrayList<>(), new ArrayList<>(),
                hostSeverityCounts, subCategoryCounts, new ArrayList<>(), threatBackendAvailable, collections,
                collectionLastTrafficSeen, null);
    }

    private static Map<Integer, Integer> lastTrafficSeenForFourVendors() {
        Map<Integer, Integer> m = new HashMap<>();
        m.put(101, 1_007_000);
        m.put(102, 1_007_500);
        m.put(103, 1_006_000);
        m.put(104, 1_005_000);
        return m;
    }

    private static Map<String, String> deviceIdToUsernameForFourVendors() {
        Map<String, String> m = new HashMap<>();
        m.put("deviceA", "alice");
        m.put("deviceB", "bob");
        m.put("deviceC", "carol");
        return m;
    }

    @SuppressWarnings("unchecked")
    private static BasicDBObject kpiById(BasicDBObject response, String id) {
        for (Object o : (List<Object>) response.get(PostureService.KEY_KPIS)) {
            BasicDBObject kpi = (BasicDBObject) o;
            if (id.equals(kpi.getString("id"))) return kpi;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> gapsOf(BasicDBObject obj) {
        return (List<Map<String, Object>>) obj.get("dataGaps");
    }

    // ── trendBucketBoundaries / bucketedSparkline ────────────────────────────────

    @Test
    public void testTrendBucketBoundaries() {
        List<Integer> boundaries = PostureService.trendBucketBoundaries(TREND_START, TREND_END, 8);
        assertEquals(8, boundaries.size());
        assertEquals(Integer.valueOf(1_001_000), boundaries.get(0));
        assertEquals(Integer.valueOf(1_004_000), boundaries.get(3));
        // Last boundary always snaps to endTs exactly, even if the range doesn't divide evenly.
        assertEquals(Integer.valueOf(TREND_END), boundaries.get(7));
    }

    @Test
    public void testTrendBucketBoundaries_unevenRange() {
        List<Integer> boundaries = PostureService.trendBucketBoundaries(0, 10, 3);
        assertEquals(3, boundaries.size());
        assertEquals(Integer.valueOf(10), boundaries.get(2));
    }

    @Test
    public void testBucketedSparkline() {
        List<Integer> boundaries = PostureService.trendBucketBoundaries(TREND_START, TREND_END, 8);
        List<DashboardMaliciousEvent> events = fiveTrendWindowEvents();
        List<Long> critical = PostureService.bucketedSparkline(events, TREND_START, boundaries,
                e -> "CRITICAL".equalsIgnoreCase(e.getSeverity()));
        assertEquals(8, critical.size());
        long total = 0;
        for (long c : critical) total += c;
        assertEquals(2, total); // e1 (CRITICAL) + the unmatched-category event (also CRITICAL)
        assertEquals(Long.valueOf(1), critical.get(0)); // e1 at ts 1_000_500 -> bucket 0
        assertEquals(Long.valueOf(1), critical.get(7)); // ts 1_007_500 -> bucket 7
    }

    @Test
    public void testBucketedSparkline_emptyEventsIsAllZero() {
        List<Integer> boundaries = PostureService.trendBucketBoundaries(TREND_START, TREND_END, 8);
        List<Long> counts = PostureService.bucketedSparkline(null, TREND_START, boundaries, e -> true);
        assertEquals(8, counts.size());
        for (long c : counts) assertEquals(0, c);
    }

    // ── matchedPolicyCounts / policyByNameLower ──────────────────────────────────

    @Test
    public void testMatchedPolicyCounts_joinsOnCategoryNotSubCategory() {
        InsightDataBundle b = bundle(new ArrayList<>(), fourBehaviourPolicies(), new HashSet<>(), new HashMap<>(),
                new HashMap<>(), new ArrayList<>(), fourMatchingSubCategoryCounts(), true, TREND_START, TREND_END);

        List<PostureService.PolicyMatch> matches = PostureService.matchedPolicyCounts(b);
        // 5 subCategoryCounts in, 4 resolve to a policy by category name, 1 (unknown category) is dropped.
        assertEquals(4, matches.size());
        long total = 0;
        for (PostureService.PolicyMatch m : matches) total += m.count;
        assertEquals(82, total);
    }

    @Test
    public void testPolicyByNameLower_lowercasesKeys() {
        Map<String, GuardrailPolicies> byName = PostureService.policyByNameLower(fourBehaviourPolicies());
        assertEquals(4, byName.size());
        assertNotNull(byName.get("default-customer pii"));
        assertNull(byName.get("Default-Customer PII")); // keys are lowercased, exact-case lookup misses
    }

    // ── policyEnforcing / policyHasComplianceMapping ─────────────────────────────

    @Test
    public void testPolicyHasComplianceMapping() {
        List<GuardrailPolicies> policies = fourBehaviourPolicies();
        GuardrailPolicies piiBlock = policies.get(0);   // no llmRule at all
        GuardrailPolicies sourceWarn = policies.get(1); // llmRule enabled + compliance mapped

        assertFalse(PostureService.policyEnforcing(piiBlock));
        assertFalse(PostureService.policyHasComplianceMapping(piiBlock));
        assertTrue(PostureService.policyEnforcing(sourceWarn));
        assertTrue(PostureService.policyHasComplianceMapping(sourceWarn));

        sourceWarn.getLlmRule().setEnabled(false);
        assertFalse(PostureService.policyEnforcing(sourceWarn));
        assertFalse(PostureService.policyHasComplianceMapping(sourceWarn));
    }

    // ── kpi() / addGap() / gapRow() / safe() ─────────────────────────────────────

    @Test
    public void testKpiFactoryAndGapHelpers() {
        BasicDBObject kpi = PostureService.kpi("someId", "Some label", 5L, 10L, "/some/route");
        assertEquals("someId", kpi.getString("id"));
        assertEquals("Some label", kpi.getString("label"));
        assertEquals(Long.valueOf(5), kpi.get("value"));
        assertEquals("neutral", kpi.getString("deltaTone"));
        assertTrue(gapsOf(kpi).isEmpty());

        PostureService.addGap(kpi, "SOME_SOURCE", "NOT_CONFIGURED", "human explanation");
        assertEquals(1, gapsOf(kpi).size());
        assertEquals("human explanation", gapsOf(kpi).get(0).get("impact"));
    }

    @Test
    public void testSafe_nullBecomesEmptyList() {
        assertTrue(PostureService.safe(null).isEmpty());
        List<String> real = Arrays.asList("a", "b");
        assertEquals(real, PostureService.safe(real));
    }

    // ── paginate() ────────────────────────────────────────────────────────────────

    @Test
    public void testPaginate_middlePage() {
        List<Map<String, Object>> all = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            Map<String, Object> row = new HashMap<>();
            row.put("i", i);
            all.add(row);
        }
        PostureDrillResult result = new PostureDrillResult();
        PostureService.paginate(result, all, 20, 10);
        assertEquals(25, result.getTotal());
        assertEquals(20, result.getSkip());
        assertEquals(10, result.getLimit());
        assertEquals(5, result.getRows().size()); // only 5 rows left after skipping 20 of 25
        assertEquals(20, result.getRows().get(0).get("i"));
    }

    @Test
    public void testPaginate_skipBeyondSizeReturnsEmpty() {
        List<Map<String, Object>> all = Collections.singletonList(Collections.singletonMap("i", 0));
        PostureDrillResult result = new PostureDrillResult();
        PostureService.paginate(result, all, 50, 10);
        assertEquals(1, result.getTotal());
        assertTrue(result.getRows().isEmpty());
        assertEquals(1, result.getSkip()); // clamped to total, not left at the requested 50
    }

    // ── buildSummary — real data path ────────────────────────────────────────────

    @Test
    public void testBuildSummary_realData() {
        List<ApiCollection> collections = fourVendorCollections();
        List<GuardrailPolicies> policies = fourBehaviourPolicies();
        Set<String> allowlist = new HashSet<>(Arrays.asList("openai", "anthropic"));
        Map<String, String> deviceIdToUsername = deviceIdToUsernameForFourVendors();
        Map<Integer, Integer> lastTrafficSeen = lastTrafficSeenForFourVendors();
        List<HostSeverityCount> hostSeverityCounts = Collections.singletonList(
                new HostSeverityCount("deviceA.ai-agent.chatgpt.com", 2, 1, 0, 0));
        List<ThreatCategoryCount> subCategoryCounts = fourMatchingSubCategoryCounts();

        InsightDataBundle b = bundle(collections, policies, allowlist, deviceIdToUsername, lastTrafficSeen,
                hostSeverityCounts, subCategoryCounts, true, TREND_START, TREND_END);

        List<HostSeverityCount> priorHostSeverity = Collections.singletonList(
                new HostSeverityCount("deviceA.ai-agent.chatgpt.com", 1, 0, 0, 0));
        List<ThreatCategoryCount> priorSubCategory = Collections.singletonList(
                new ThreatCategoryCount("Default-Customer PII", "PII-Customer", 30));
        List<Integer> weeklyAttackCounts = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 70);
        List<DashboardMaliciousEvent> trendWindowEvents = fiveTrendWindowEvents();

        PostureService postureService = new PostureService();
        BasicDBObject response = postureService.buildSummary(b, priorHostSeverity, priorSubCategory, collections,
                200L, weeklyAttackCounts, trendWindowEvents);

        // Critical alerts KPI: current=2 (hostSeverityCounts), prior=1 -> delta +1, worse (critical).
        BasicDBObject criticalAlerts = kpiById(response, PostureService.KPI_CRITICAL_ALERTS);
        assertNotNull(criticalAlerts);
        assertEquals(2L, criticalAlerts.get("value"));
        assertEquals(1L, criticalAlerts.get("delta"));
        assertEquals("critical", criticalAlerts.getString("deltaTone"));
        assertEquals(2L, sumSparkline(criticalAlerts));

        // Monitoring coverage: every policy has null targeting (applies to all) -> 100% of 3 devices.
        BasicDBObject coverage = kpiById(response, PostureService.KPI_MONITORING_COVERAGE);
        assertNotNull(coverage);
        assertEquals(100.0, (double) coverage.get("value"), 0.001);
        assertEquals(3L, coverage.get("numerator"));
        assertEquals(3L, coverage.get("denominator"));

        // Sensitive data incidents: current=40 (Default-Customer PII), prior=30 -> +33.3%.
        BasicDBObject sensitive = kpiById(response, PostureService.KPI_SENSITIVE_INCIDENTS);
        assertNotNull(sensitive);
        assertEquals(40L, sensitive.get("value"));
        assertEquals(33.3, (double) sensitive.get("delta"), 0.001);
        assertEquals("percent", sensitive.getString("deltaKind"));
        assertEquals(1L, sumSparkline(sensitive));

        // Data leaving: Customer PII=40, Source Code=25, Others=17 (Custom Alert 10 + Approval 7).
        BasicDBObject dataLeaving = (BasicDBObject) response.get(PostureService.KEY_DATA_LEAVING);
        assertEquals(82L, dataLeaving.get("total"));
        assertEquals(3, ((List<?>) dataLeaving.get("segments")).size());
        assertEquals(40L, segmentByLabel(dataLeaving, "Customer PII").get("count"));
        assertEquals(17L, segmentByLabel(dataLeaving, "Others").get("count"));

        // Enforcement funnel: hardBlocked=40, warnedOnly=25, warningOverridden=10, matched=82,
        // and a GUARDRAIL_POLICIES gap for the 7 unclassified ("approval") events.
        BasicDBObject funnel = (BasicDBObject) response.get(PostureService.KEY_ENFORCEMENT_FUNNEL);
        assertEquals(82L, stageById(funnel, "matched").get("count"));
        assertEquals(40L, stageById(funnel, "hardBlocked").get("count"));
        assertEquals(25L, stageById(funnel, "warnedOnly").get("count"));
        assertEquals(10L, stageById(funnel, "warningOverridden").get("count"));
        assertTrue(gapsOf(funnel).stream().anyMatch(g -> PostureService.GAP_GUARDRAIL_POLICIES.equals(g.get("source"))));

        // Shadow AI trend: OpenAI/Anthropic are SANCTIONED (allowlisted), DeepSeek is SHADOW.
        BasicDBObject shadowTrend = (BasicDBObject) response.get(PostureService.KEY_SHADOW_AI_TREND);
        assertEquals(3L, ((Number) shadowTrend.get("currentSanctioned")).longValue()); // deviceA+deviceB (OpenAI) + deviceA (Anthropic)
        assertEquals(1L, ((Number) shadowTrend.get("currentUnsanctioned")).longValue()); // deviceC (DeepSeek)

        // Framework readiness: no ComplianceClauseCoverage docs seeded for this test -> gap, no rows.
        BasicDBObject frameworkReadiness = (BasicDBObject) response.get(PostureService.KEY_FRAMEWORK_READINESS);
        assertTrue(((List<?>) frameworkReadiness.get("frameworks")).isEmpty());
        assertFalse(gapsOf(frameworkReadiness).isEmpty());

        // Attack attempts: real weeklyAttackCounts of the right size -> no fetch-failed gap, last bucket wins.
        BasicDBObject attackAttempts = (BasicDBObject) response.get(PostureService.KEY_ATTACK_ATTEMPTS);
        assertEquals(70L, attackAttempts.get("currentTotal"));

        // Risk score composite is present (RiskScoreCalculator exercised as part of this same call).
        BasicDBObject riskScore = kpiById(response, PostureService.KPI_RISK_SCORE);
        assertNotNull(riskScore);
        assertNotNull(riskScore.get("value"));
    }

    @SuppressWarnings("unchecked")
    private static long sumSparkline(BasicDBObject kpi) {
        long total = 0;
        for (Object o : (List<Object>) kpi.get("sparkline")) total += ((Number) o).longValue();
        return total;
    }

    @SuppressWarnings("unchecked")
    private static BasicDBObject segmentByLabel(BasicDBObject dataLeaving, String label) {
        for (Object o : (List<Object>) dataLeaving.get("segments")) {
            BasicDBObject seg = (BasicDBObject) o;
            if (label.equals(seg.getString("label"))) return seg;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static BasicDBObject stageById(BasicDBObject funnel, String id) {
        for (Object o : (List<Object>) funnel.get("stages")) {
            BasicDBObject stage = (BasicDBObject) o;
            if (id.equals(stage.getString("id"))) return stage;
        }
        return null;
    }

    // ── buildSummary — empty/gap path ────────────────────────────────────────────

    @Test
    public void testBuildSummary_emptyBundle_reportsGapsInsteadOfZeros() {
        InsightDataBundle empty = bundle(new ArrayList<>(), new ArrayList<>(), new HashSet<>(), new HashMap<>(),
                new HashMap<>(), new ArrayList<>(), new ArrayList<>(), false, 0, 0);

        PostureService postureService = new PostureService();
        BasicDBObject response = postureService.buildSummary(empty, null, null, new ArrayList<>(),
                null, null, new ArrayList<>());

        // No devices at all -> DEVICE_IDENTITY/NO_ROWS gap, no coverage value.
        BasicDBObject coverage = kpiById(response, PostureService.KPI_MONITORING_COVERAGE);
        assertNull(coverage.get("value"));
        assertTrue(gapsOf(coverage).stream().anyMatch(g -> "DEVICE_IDENTITY".equals(g.get("source"))));

        // No policy has PII detection configured -> GAP_GUARDRAIL_POLICIES, not a bare 0.
        BasicDBObject sensitive = kpiById(response, PostureService.KPI_SENSITIVE_INCIDENTS);
        assertTrue(gapsOf(sensitive).stream().anyMatch(g -> PostureService.GAP_GUARDRAIL_POLICIES.equals(g.get("source"))));

        // threatBackendAvailable=false -> critical alerts KPI reports the outage, value null.
        BasicDBObject criticalAlerts = kpiById(response, PostureService.KPI_CRITICAL_ALERTS);
        assertNull(criticalAlerts.get("value"));
        assertTrue(gapsOf(criticalAlerts).stream().anyMatch(g -> PostureService.GAP_THREAT_BACKEND.equals(g.get("source"))));

        // No totalInspectedActions -> enforcement funnel's own INSPECTED_ACTIONS gap.
        BasicDBObject funnel = (BasicDBObject) response.get(PostureService.KEY_ENFORCEMENT_FUNNEL);
        assertTrue(gapsOf(funnel).stream().anyMatch(g -> "INSPECTED_ACTIONS".equals(g.get("source"))));

        // null weeklyAttackCounts -> attack attempts trend reports the threat-backend gap, zeroed series.
        BasicDBObject attackAttempts = (BasicDBObject) response.get(PostureService.KEY_ATTACK_ATTEMPTS);
        assertEquals(0L, attackAttempts.get("currentTotal"));
        assertTrue(gapsOf(attackAttempts).stream().anyMatch(g -> PostureService.GAP_THREAT_BACKEND.equals(g.get("source"))));

        // Nothing in bundle.collections -> shadow AI trend is all zero, no exception.
        BasicDBObject shadowTrend = (BasicDBObject) response.get(PostureService.KEY_SHADOW_AI_TREND);
        assertEquals(0L, ((Number) shadowTrend.get("currentSanctioned")).longValue());
        assertEquals(0L, ((Number) shadowTrend.get("currentUnsanctioned")).longValue());
    }

    @Test
    public void testMonitoringCoverage_partialCoverage_intersectsWithLiveDevices() {
        // One policy targets exactly one (live) device; a second live device isn't covered.
        GuardrailPolicies restricted = policy("Restricted policy", "block", Collections.singletonList("deviceA"));
        Map<String, String> deviceIdToUsername = new HashMap<>();
        deviceIdToUsername.put("deviceA", "alice");
        deviceIdToUsername.put("deviceB", "bob");

        InsightDataBundle b = bundle(new ArrayList<>(), Collections.singletonList(restricted), new HashSet<>(),
                deviceIdToUsername, new HashMap<>(), new ArrayList<>(), new ArrayList<>(), true, TREND_START, TREND_END);

        PostureService postureService = new PostureService();
        BasicDBObject response = postureService.buildSummary(b, null, null, new ArrayList<>(), null,
                Arrays.asList(0, 0, 0, 0, 0, 0, 0, 0), new ArrayList<>());

        BasicDBObject coverage = kpiById(response, PostureService.KPI_MONITORING_COVERAGE);
        assertEquals(50.0, (double) coverage.get("value"), 0.001); // 1 of 2 live devices covered
        assertEquals(1L, coverage.get("numerator"));
        assertEquals(2L, coverage.get("denominator"));
        assertEquals("1 devices unmonitored", coverage.getString("footnote"));
    }

    @Test
    public void testSensitiveDataIncidents_noPriorWindowAndZeroPriorBranches() {
        List<GuardrailPolicies> policies = Collections.singletonList(fourBehaviourPolicies().get(0)); // PII policy only
        List<ThreatCategoryCount> subCategoryCounts = Collections.singletonList(
                new ThreatCategoryCount("Default-Customer PII", "PII-Customer", 40));
        InsightDataBundle b = bundle(new ArrayList<>(), policies, new HashSet<>(), new HashMap<>(), new HashMap<>(),
                new ArrayList<>(), subCategoryCounts, true, TREND_START, TREND_END);
        PostureService postureService = new PostureService();

        // priorSubCategory == null (unbounded "all time" range, nothing to diff against).
        BasicDBObject noPriorResponse = postureService.buildSummary(b, null, null, new ArrayList<>(), null,
                Arrays.asList(0, 0, 0, 0, 0, 0, 0, 0), new ArrayList<>());
        BasicDBObject sensitiveNoPrior = kpiById(noPriorResponse, PostureService.KPI_SENSITIVE_INCIDENTS);
        assertNull(sensitiveNoPrior.get("delta"));
        assertTrue(gapsOf(sensitiveNoPrior).stream().anyMatch(g -> "PRIOR_WINDOW".equals(g.get("source"))));

        // priorSubCategory present but confirms zero prior activity -> percentChange is undefined
        // from a zero base, so the KPI reports the absolute rise instead of a fabricated "+100%".
        BasicDBObject zeroPriorResponse = postureService.buildSummary(b, null, new ArrayList<>(), new ArrayList<>(),
                null, Arrays.asList(0, 0, 0, 0, 0, 0, 0, 0), new ArrayList<>());
        BasicDBObject sensitiveZeroPrior = kpiById(zeroPriorResponse, PostureService.KPI_SENSITIVE_INCIDENTS);
        assertEquals(40L, sensitiveZeroPrior.get("delta"));
        assertEquals("absolute", sensitiveZeroPrior.getString("deltaKind"));
        assertEquals("No incidents in the previous period", sensitiveZeroPrior.getString("footnote"));
    }

    @Test
    public void testBiggestMovers_deviceAndAttackThresholdsCrossed() {
        // 21 distinct devices on one vendor crosses BIGGEST_MOVERS_DEVICE_THRESHOLD (20).
        List<ApiCollection> collections = new ArrayList<>();
        Map<Integer, Integer> lastSeen = new HashMap<>();
        for (int i = 0; i < 21; i++) {
            ApiCollection c = endpointCollection(200 + i, "device" + i + ".ai-agent.grok.com", TREND_START);
            collections.add(c);
            lastSeen.put(c.getId(), TREND_END);
        }
        // 1001 attacks on a second vendor crosses BIGGEST_MOVERS_ATTACK_THRESHOLD (1000).
        ApiCollection attackedVendor = endpointCollection(300, "deviceZ.ai-agent.chatgpt.com", TREND_START);
        collections.add(attackedVendor);
        lastSeen.put(attackedVendor.getId(), TREND_END);

        List<DashboardMaliciousEvent> events = new ArrayList<>();
        for (int i = 0; i < 1001; i++) {
            events.add(event(300, TREND_START + 1, "Any", "deviceZ.ai-agent.chatgpt.com", "z", "LOW", "ACTIVE"));
        }

        InsightDataBundle b = bundle(collections, new ArrayList<>(), new HashSet<>(), new HashMap<>(), lastSeen,
                new ArrayList<>(), new ArrayList<>(), true, TREND_START, TREND_END);

        PostureService postureService = new PostureService();
        BasicDBObject response = postureService.buildSummary(b, null, null, collections, null,
                Arrays.asList(0, 0, 0, 0, 0, 0, 0, 0), events);

        BasicDBObject biggestMovers = (BasicDBObject) response.get(PostureService.KEY_BIGGEST_MOVERS);
        @SuppressWarnings("unchecked")
        List<BasicDBObject> movers = (List<BasicDBObject>) biggestMovers.get("movers");
        assertFalse(movers.isEmpty());
        assertTrue(movers.stream().anyMatch(m -> "devices".equals(m.getString("condition")) && "xAI".equals(m.getString("vendor"))));
        assertTrue(movers.stream().anyMatch(m -> "attacks".equals(m.getString("condition")) && "OpenAI".equals(m.getString("vendor"))));
    }

    // ── fetchDrill — Shadow AI tools ──────────────────────────────────────────────

    @Test
    public void testFetchDrill_shadowAiTools_rootLevel() {
        InsightDataBundle b = bundle(fourVendorCollections(), new ArrayList<>(),
                new HashSet<>(Arrays.asList("openai", "anthropic")), deviceIdToUsernameForFourVendors(),
                lastTrafficSeenForFourVendors(), new ArrayList<>(), new ArrayList<>(), true, TREND_START, TREND_END);

        PostureService postureService = new PostureService();
        PostureDrillResult result = postureService.fetchDrill(b, new ArrayList<>(), new ArrayList<>(),
                TREND_START, TREND_END, PostureService.DRILL_SHADOW_AI, "", 0, 20);

        assertEquals("Shadow AI tools", result.getTitle());
        assertTrue(result.isDrillable());
        assertEquals(3, result.getTotal()); // OpenAI, DeepSeek, Anthropic
        Map<String, Object> openAiRow = rowById(result, "OpenAI");
        assertNotNull(openAiRow);
        assertEquals("SANCTIONED", openAiRow.get("status"));
        assertEquals(2, openAiRow.get("devices")); // deviceA + deviceB
        Map<String, Object> deepSeekRow = rowById(result, "DeepSeek");
        assertEquals("SHADOW", deepSeekRow.get("status"));
    }

    @Test
    public void testFetchDrill_shadowAiTools_deviceLevel() {
        InsightDataBundle b = bundle(fourVendorCollections(), new ArrayList<>(),
                new HashSet<>(Arrays.asList("openai", "anthropic")), deviceIdToUsernameForFourVendors(),
                lastTrafficSeenForFourVendors(), new ArrayList<>(), new ArrayList<>(), true, TREND_START, TREND_END);

        PostureService postureService = new PostureService();
        PostureDrillResult result = postureService.fetchDrill(b, new ArrayList<>(), new ArrayList<>(),
                TREND_START, TREND_END, PostureService.DRILL_SHADOW_AI, "OpenAI", 0, 20);

        assertFalse(result.isDrillable());
        assertEquals(2, result.getRows().size());
        assertEquals(2, result.getBreadcrumb().size());
        assertEquals("OpenAI", result.getBreadcrumb().get(1).getLabel());
        assertTrue(result.getRows().stream().anyMatch(r -> "alice".equals(r.get("device"))));
        assertTrue(result.getRows().stream().anyMatch(r -> "bob".equals(r.get("device"))));
    }

    @Test
    public void testFetchDrill_shadowAiTools_unknownToolReportsGap() {
        InsightDataBundle b = bundle(fourVendorCollections(), new ArrayList<>(), new HashSet<>(),
                deviceIdToUsernameForFourVendors(), lastTrafficSeenForFourVendors(), new ArrayList<>(), new ArrayList<>(),
                true, TREND_START, TREND_END);

        PostureService postureService = new PostureService();
        PostureDrillResult result = postureService.fetchDrill(b, new ArrayList<>(), new ArrayList<>(),
                TREND_START, TREND_END, PostureService.DRILL_SHADOW_AI, "NoSuchTool", 0, 20);

        assertTrue(result.getRows().isEmpty());
        assertFalse(result.getDataGaps().isEmpty());
    }

    // ── fetchDrill — What data is leaving ────────────────────────────────────────

    @Test
    public void testFetchDrill_dataLeaving_rootAndMemberLevel() {
        InsightDataBundle b = bundle(new ArrayList<>(), fourBehaviourPolicies(), new HashSet<>(), new HashMap<>(),
                new HashMap<>(), new ArrayList<>(), fourMatchingSubCategoryCounts(), true, TREND_START, TREND_END);

        PostureService postureService = new PostureService();
        PostureDrillResult root = postureService.fetchDrill(b, new ArrayList<>(), fiveTrendWindowEvents(),
                TREND_START, TREND_END, PostureService.DRILL_DATA_LEAVING, "", 0, 20);
        assertEquals(3, root.getTotal());
        assertEquals(40L, rowById(root, "Customer PII").get("incidents"));
        assertEquals(17L, rowById(root, "Others").get("incidents"));

        PostureDrillResult member = postureService.fetchDrill(b, new ArrayList<>(), fiveTrendWindowEvents(),
                TREND_START, TREND_END, PostureService.DRILL_DATA_LEAVING, "Customer PII", 0, 20);
        assertEquals(1, member.getRows().size());
        assertEquals("deviceA.ai-agent.chatgpt.com", member.getRows().get(0).get("host"));
        assertEquals("CRITICAL", member.getRows().get(0).get("severity"));
    }

    // ── fetchDrill — Enforcement funnel ──────────────────────────────────────────

    @Test
    public void testFetchDrill_enforcementFunnel_rootAndHardBlockedLevel() {
        InsightDataBundle b = bundle(new ArrayList<>(), fourBehaviourPolicies(), new HashSet<>(), new HashMap<>(),
                new HashMap<>(), new ArrayList<>(), fourMatchingSubCategoryCounts(), true, TREND_START, TREND_END);

        PostureService postureService = new PostureService();
        PostureDrillResult root = postureService.fetchDrill(b, new ArrayList<>(), fiveTrendWindowEvents(),
                TREND_START, TREND_END, PostureService.DRILL_ENFORCEMENT_FUNNEL, "", 0, 20);
        assertEquals(4, root.getTotal());
        assertEquals(82L, rowById(root, "matched").get("count"));
        assertEquals(40L, rowById(root, "hardBlocked").get("count"));

        PostureDrillResult hardBlocked = postureService.fetchDrill(b, new ArrayList<>(), fiveTrendWindowEvents(),
                TREND_START, TREND_END, PostureService.DRILL_ENFORCEMENT_FUNNEL, "hardBlocked", 0, 20);
        assertEquals(1, hardBlocked.getRows().size());
        assertEquals("Default-Customer PII", hardBlocked.getRows().get(0).get("policy"));
        assertEquals("Block", hardBlocked.getRows().get(0).get("mode"));
    }

    @Test
    public void testFetchDrill_enforcementFunnel_warningOverriddenHasNoEventSource() {
        InsightDataBundle b = bundle(new ArrayList<>(), fourBehaviourPolicies(), new HashSet<>(), new HashMap<>(),
                new HashMap<>(), new ArrayList<>(), fourMatchingSubCategoryCounts(), true, TREND_START, TREND_END);

        PostureService postureService = new PostureService();
        PostureDrillResult overridden = postureService.fetchDrill(b, new ArrayList<>(), fiveTrendWindowEvents(),
                TREND_START, TREND_END, PostureService.DRILL_ENFORCEMENT_FUNNEL, "warningOverridden", 0, 20);

        assertTrue(overridden.getRows().isEmpty());
        assertTrue(overridden.getDataGaps().stream()
                .anyMatch(g -> PostureService.GAP_GUARDRAIL_POLICIES.equals(g.getSource())));
    }

    // ── fetchDrill — Vendor risk (dispatches into RiskScoreCalculator) ───────────

    @Test
    public void testFetchDrill_vendorRisk_rootAndMemberLevel() {
        InsightDataBundle b = bundle(fourVendorCollections(), new ArrayList<>(),
                new HashSet<>(Arrays.asList("openai", "anthropic")), deviceIdToUsernameForFourVendors(),
                new HashMap<>(), new ArrayList<>(), new ArrayList<>(), true, TREND_START, TREND_END);

        PostureService postureService = new PostureService();
        PostureDrillResult root = postureService.fetchDrill(b, fourVendorCollections(), new ArrayList<>(),
                TREND_START, TREND_END, PostureService.DRILL_VENDOR_RISK, "", 0, 20);
        assertEquals(3, root.getTotal());
        assertEquals(true, rowById(root, "OpenAI").get("approved"));
        assertEquals(false, rowById(root, "DeepSeek").get("approved"));
        assertEquals(3, rowById(root, "DeepSeek").get("weight")); // unapproved -> flat weight 3

        PostureDrillResult member = postureService.fetchDrill(b, fourVendorCollections(), new ArrayList<>(),
                TREND_START, TREND_END, PostureService.DRILL_VENDOR_RISK, "OpenAI", 0, 20);
        assertEquals(2, member.getRows().size());
    }

    // ── fetchDrill — Framework readiness (backed by real Mongo) ──────────────────

    @Test
    public void testFetchDrill_frameworkReadiness_rootAndMemberLevel() {
        ComplianceClauseCoverageDao.instance.getMCollection().drop();
        ComplianceClauseCoverage coverage = new ComplianceClauseCoverage();
        coverage.setId("NIST AI RMF");
        coverage.setTotalClauses(10);
        coverage.setLastScannedAt(TREND_END);
        // BSON field names can't contain "." — sub-clause text is stored as a map key, so keep test
        // fixtures dot-free the same way real sub-clause catalog text would need to be.
        Map<String, List<ClauseHit>> hits = new HashMap<>();
        hits.put("Clause 1a", Collections.singletonList(new ClauseHit("ref1", TREND_START + 100, "Default-Source code detection")));
        hits.put("Clause 1b", Collections.singletonList(new ClauseHit("ref2", TREND_START + 200, "Default-Source code detection")));
        coverage.setClauseHits(hits);
        ComplianceClauseCoverageDao.instance.insertOne(coverage);

        PostureService postureService = new PostureService();
        PostureDrillResult root = postureService.fetchDrill(bundle(new ArrayList<>(), new ArrayList<>(), new HashSet<>(),
                        new HashMap<>(), new HashMap<>(), new ArrayList<>(), new ArrayList<>(), true, TREND_START, TREND_END),
                new ArrayList<>(), new ArrayList<>(), TREND_START, TREND_END,
                PostureService.DRILL_FRAMEWORK_READINESS, "", 0, 20);

        assertEquals(1, root.getTotal());
        Map<String, Object> row = root.getRows().get(0);
        assertEquals("NIST AI RMF", row.get("framework"));
        assertEquals(20, row.get("value")); // 2 of 10 clauses covered
        assertEquals(2, row.get("clausesCovered"));

        PostureDrillResult member = postureService.fetchDrill(bundle(new ArrayList<>(), new ArrayList<>(), new HashSet<>(),
                        new HashMap<>(), new HashMap<>(), new ArrayList<>(), new ArrayList<>(), true, TREND_START, TREND_END),
                new ArrayList<>(), new ArrayList<>(), TREND_START, TREND_END,
                PostureService.DRILL_FRAMEWORK_READINESS, "NIST AI RMF", 0, 20);
        assertEquals(2, member.getRows().size());
        assertTrue(member.getRows().stream().anyMatch(r -> "Clause 1a".equals(r.get("subClause"))));
    }

    @Test
    public void testFetchDrill_frameworkReadiness_noScanYetReportsGap() {
        ComplianceClauseCoverageDao.instance.getMCollection().drop();
        PostureService postureService = new PostureService();
        PostureDrillResult root = postureService.fetchDrill(bundle(new ArrayList<>(), new ArrayList<>(), new HashSet<>(),
                        new HashMap<>(), new HashMap<>(), new ArrayList<>(), new ArrayList<>(), true, TREND_START, TREND_END),
                new ArrayList<>(), new ArrayList<>(), TREND_START, TREND_END,
                PostureService.DRILL_FRAMEWORK_READINESS, "", 0, 20);
        assertTrue(root.getRows().isEmpty());
        assertFalse(root.getDataGaps().isEmpty());
    }

    // ── fetchDrill — unknown drillId ──────────────────────────────────────────────

    @Test
    public void testFetchDrill_unknownDrillIdReportsGapNotException() {
        InsightDataBundle b = bundle(new ArrayList<>(), new ArrayList<>(), new HashSet<>(), new HashMap<>(),
                new HashMap<>(), new ArrayList<>(), new ArrayList<>(), true, TREND_START, TREND_END);
        PostureService postureService = new PostureService();

        PostureDrillResult result = postureService.fetchDrill(b, new ArrayList<>(), new ArrayList<>(),
                TREND_START, TREND_END, "notARealDrill", "", 0, 20);
        assertEquals("Unknown drilldown", result.getTitle());
        assertFalse(result.getDataGaps().isEmpty());

        PostureDrillResult nullDrill = postureService.fetchDrill(b, new ArrayList<>(), new ArrayList<>(),
                TREND_START, TREND_END, null, "", 0, 20);
        assertEquals("Unknown drilldown", nullDrill.getTitle());
    }

    private static Map<String, Object> rowById(PostureDrillResult result, String id) {
        for (Map<String, Object> row : result.getRows()) {
            if (id.equals(row.get("id"))) return row;
        }
        return null;
    }
}

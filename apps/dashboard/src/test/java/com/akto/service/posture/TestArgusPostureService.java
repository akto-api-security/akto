package com.akto.service.posture;

import com.akto.dto.AgenticPostureScoreHistory;
import com.akto.dto.ApiCollection;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.GuardrailPolicies.SelectedServer;
import com.akto.dto.traffic.CollectionTags;
import com.akto.service.insights.InsightContext;
import com.akto.service.insights.InsightDataBundle;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.BasicDBObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

// Pure-function tests for ArgusPostureService; no Mongo needed. Everything asserted here is either
// public or package-private, so nothing in the production class had its scope widened for testing.
public class TestArgusPostureService {

    private final ArgusPostureService service = new ArgusPostureService();

    @Test
    public void buildPostureScore_noHistoryYet_notComputedYetGap() {
        BasicDBObject postureScore = service.buildPostureScore(null, new ArrayList<>(), null);

        assertNull(postureScore.get("value"));
        List<?> gaps = (List<?>) postureScore.get("dataGaps");
        assertEquals(1, gaps.size());
        assertEquals("NOT_COMPUTED_YET", ((Map<?, ?>) gaps.get(0)).get("reason"));
    }

    @Test
    public void buildPostureScore_noAgentsDiscovered_noRowsGap() {
        AgenticPostureScoreHistory latest = new AgenticPostureScoreHistory(0, 0, 0, 1000);
        BasicDBObject postureScore = service.buildPostureScore(latest, Arrays.asList(latest), null);

        assertEquals(0L, postureScore.get("value"));
        List<?> gaps = (List<?>) postureScore.get("dataGaps");
        assertEquals(1, gaps.size());
        assertEquals("NO_ROWS", ((Map<?, ?>) gaps.get(0)).get("reason"));
    }

    @Test
    public void buildPostureScore_fullyScored_noGaps() {
        AgenticPostureScoreHistory latest = new AgenticPostureScoreHistory(62.0, 10, 0, 3000);
        BasicDBObject postureScore = service.buildPostureScore(latest, Arrays.asList(latest), null);

        assertEquals(62L, postureScore.get("value"));
        assertEquals(10, postureScore.get("agentsScored"));
        assertEquals(0, postureScore.get("agentsWithNoSignal"));
        assertEquals(0, ((List<?>) postureScore.get("dataGaps")).size());
    }

    @Test
    public void buildPostureScore_partiallyScored_partialCoverageGap() {
        AgenticPostureScoreHistory latest = new AgenticPostureScoreHistory(80.0, 5, 2, 3000);
        BasicDBObject postureScore = service.buildPostureScore(latest, Arrays.asList(latest), null);

        assertEquals(80L, postureScore.get("value"));
        List<?> gaps = (List<?>) postureScore.get("dataGaps");
        assertEquals(1, gaps.size());
        assertEquals("PARTIAL_COVERAGE", ((Map<?, ?>) gaps.get(0)).get("reason"));
    }

    @Test
    public void buildPostureScore_withTrendAndDelta_realNotFabricated() {
        AgenticPostureScoreHistory p1 = new AgenticPostureScoreHistory(50.0, 5, 0, 1000);
        AgenticPostureScoreHistory p2 = new AgenticPostureScoreHistory(60.0, 5, 0, 2000);
        AgenticPostureScoreHistory latest = new AgenticPostureScoreHistory(70.0, 5, 0, 3000);
        AgenticPostureScoreHistory weekAgo = new AgenticPostureScoreHistory(50.0, 5, 0, 500);

        BasicDBObject postureScore = service.buildPostureScore(latest, Arrays.asList(p1, p2, latest), weekAgo);

        assertEquals(70L, postureScore.get("value"));
        assertEquals(Arrays.asList(50.0, 60.0, 70.0), postureScore.get("trend"));
        assertEquals(20L, postureScore.get("delta")); // 70 - 50
        assertEquals("critical", postureScore.get("deltaTone")); // higher is worse, score went up
    }

    @Test
    public void buildPostureScore_noWeekAgoRow_deltaAbsentNotFabricated() {
        AgenticPostureScoreHistory latest = new AgenticPostureScoreHistory(70.0, 5, 0, 3000);
        BasicDBObject postureScore = service.buildPostureScore(latest, Arrays.asList(latest), null);

        assertEquals(70L, postureScore.get("value"));
        assertNull(postureScore.get("delta"));
    }

    @Test
    public void buildHighestRiskAgents_rankedByScore_onlyAgenticScope() {
        Map<String, Object> sensitiveDriven = subScores(0, 0, 50, 100, 50, 0);
        ApiCollection top = collection(1, "mcp-api.lambdatest.com", null, Constants.AKTO_MCP_SERVER_TAG, 24.5, sensitiveDriven);
        ApiCollection second = collection(2, "", "mcp.kite.trade", Constants.AKTO_MCP_SERVER_TAG, 10.0, subScores(0, 0, 100, 0, 0, 0));
        ApiCollection notAgentic = collection(3, "plain-api", null, "env", 99.0, sensitiveDriven);
        ApiCollection unscored = collection(4, "unscored", null, Constants.AKTO_GEN_AI_TAG, null, null);

        List<BasicDBObject> rows = service.buildHighestRiskAgents(bundle(Arrays.asList(second, notAgentic, top, unscored)), "all");

        assertEquals(2, rows.size());
        assertEquals("1", rows.get(0).get("groupKey"));
        assertEquals(1, rows.get(0).get("rank"));
        assertEquals(25L, rows.get(0).get("score"));
        assertEquals("Accesses sensitive data", rows.get(0).get("issue"));
        assertEquals("mcp.kite.trade", rows.get(1).get("name")); // blank name falls back to raw hostname
        assertEquals("Not covered by a guardrail policy or red-team scan", rows.get(1).get("issue"));
    }

    @Test
    public void buildHighestRiskAgents_heavierWeightWinsIssue() {
        // redTeam 40 earns 12 pts (weight 30); sensitiveData 100 earns only 10 (weight 10).
        ApiCollection c = collection(1, "agent", null, Constants.AKTO_GEN_AI_TAG, 30.0, subScores(40, 0, 0, 100, 0, 0));
        List<BasicDBObject> rows = service.buildHighestRiskAgents(bundle(Arrays.asList(c)), "all");
        assertEquals("Has open red-teaming findings", rows.get(0).get("issue"));
    }

    private static Map<String, Object> subScores(double redTeam, double guardrailMalicious, double coverage,
                                                 double sensitiveData, double accessAuth, double overprivilegedTools) {
        Map<String, Object> m = new HashMap<>();
        m.put("redTeam", redTeam);
        m.put("guardrailMalicious", guardrailMalicious);
        m.put("coverage", coverage);
        m.put("sensitiveData", sensitiveData);
        m.put("accessAuth", accessAuth);
        m.put("overprivilegedTools", overprivilegedTools);
        return m;
    }

    private static ApiCollection collection(int id, String name, String hostName, String tagKey,
                                            Double postureScore, Map<String, Object> subScores) {
        ApiCollection c = new ApiCollection();
        c.setId(id);
        c.setName(name);
        c.setHostName(hostName);
        c.setTagsList(new ArrayList<>(Arrays.asList(new CollectionTags(0, tagKey, "x", null))));
        c.setPostureScore(postureScore);
        c.setPostureSubScores(subScores);
        return c;
    }

    private static InsightDataBundle bundle(List<ApiCollection> collections) {
        InsightContext ctx = new InsightContext(1, 1, CONTEXT_SOURCE.AGENTIC, 0, 0);
        return new InsightDataBundle(ctx, collections, new HashMap<>(), new HashMap<>(), new HashMap<>(),
                new ArrayList<>(), new ArrayList<>(), new java.util.HashSet<>(), new HashMap<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), false, collections,
                new HashMap<>(), null);
    }

    // ── computeCoverage ────────────────────────────────────────────────────────

    @Test
    public void computeCoverage_noPolicies_everythingUncovered() {
        List<ApiCollection> assets = Arrays.asList(host(1, "a.akto.io"), host(2, "b.akto.io"));

        ArgusPostureService.GuardrailsCoverageBreakdown b =
                ArgusPostureService.computeCoverage(assets, new ArrayList<>());

        assertEquals(2, b.uncovered.size());
        assertEquals(0, b.covered());
        assertTrue(b.coveringPolicies.isEmpty());
    }

    @Test
    public void computeCoverage_applyToAllServers_coversEveryHost() {
        List<ApiCollection> assets = Arrays.asList(host(1, "a.akto.io"), host(2, "b.akto.io"));

        ArgusPostureService.GuardrailsCoverageBreakdown b =
                ArgusPostureService.computeCoverage(assets, Arrays.asList(fleetWide("all", "block")));

        assertEquals(0, b.uncovered.size());
        assertEquals(2, b.enforcing.size());
        assertEquals(2, b.covered());
    }

    @Test
    public void computeCoverage_blockingPolicy_isEnforcing() {
        ArgusPostureService.GuardrailsCoverageBreakdown b = ArgusPostureService.computeCoverage(
                Arrays.asList(host(1, "a.akto.io")),
                Arrays.asList(targeted("p", "block", "a.akto.io")));

        assertEquals(1, b.enforcing.size());
        assertEquals(0, b.alertOnly.size());
    }

    @Test
    public void computeCoverage_nonBlockingBehaviours_areAlertOnly() {
        for (String behaviour : Arrays.asList("alert", "warn", "approval")) {
            ArgusPostureService.GuardrailsCoverageBreakdown b = ArgusPostureService.computeCoverage(
                    Arrays.asList(host(1, "a.akto.io")),
                    Arrays.asList(targeted("p", behaviour, "a.akto.io")));

            assertEquals(behaviour, 1, b.alertOnly.size());
            assertEquals(behaviour, 0, b.enforcing.size());
        }
    }

    @Test
    public void computeCoverage_blockingWinsOverAlertOnSameAsset() {
        ArgusPostureService.GuardrailsCoverageBreakdown b = ArgusPostureService.computeCoverage(
                Arrays.asList(host(1, "a.akto.io")),
                Arrays.asList(targeted("alerting", "alert", "a.akto.io"),
                              targeted("blocking", "block", "a.akto.io")));

        assertEquals(1, b.enforcing.size());
        assertEquals(0, b.alertOnly.size());
        assertEquals(2, b.coveringPolicies.get(1).size());
    }

    @Test
    public void computeCoverage_behaviourIsCaseInsensitive() {
        ArgusPostureService.GuardrailsCoverageBreakdown b = ArgusPostureService.computeCoverage(
                Arrays.asList(host(1, "a.akto.io")),
                Arrays.asList(targeted("p", "BLOCK", "a.akto.io")));

        assertEquals(1, b.enforcing.size());
    }

    @Test
    public void computeCoverage_hostMatchesBySuffix() {
        ArgusPostureService.GuardrailsCoverageBreakdown b = ArgusPostureService.computeCoverage(
                Arrays.asList(host(1, "team.mcp.akto.io")),
                Arrays.asList(targeted("p", "block", "mcp.akto.io")));

        assertEquals(1, b.enforcing.size());
    }

    @Test
    public void computeCoverage_hostMatchesAsInnerSegment() {
        ArgusPostureService.GuardrailsCoverageBreakdown b = ArgusPostureService.computeCoverage(
                Arrays.asList(host(1, "dev.mcp.akto.io")),
                Arrays.asList(targeted("p", "block", "mcp")));

        assertEquals(1, b.enforcing.size());
    }

    @Test
    public void computeCoverage_partialNameIsNotASubstringMatch() {
        ArgusPostureService.GuardrailsCoverageBreakdown b = ArgusPostureService.computeCoverage(
                Arrays.asList(host(1, "notakto.io")),
                Arrays.asList(targeted("p", "block", "akto.io")));

        assertEquals(1, b.uncovered.size());
    }

    @Test
    public void computeCoverage_emptySelectedServersMatchesNothing() {
        GuardrailPolicies p = new GuardrailPolicies();
        p.setName("p");
        p.setBehaviour("block");
        p.setApplyToAllServers(false);
        p.setSelectedMcpServersV2(new ArrayList<>());

        ArgusPostureService.GuardrailsCoverageBreakdown b =
                ArgusPostureService.computeCoverage(Arrays.asList(host(1, "a.akto.io")), Arrays.asList(p));

        assertEquals(1, b.uncovered.size());
    }

    @Test
    public void computeCoverage_agentServerListAlsoMatches() {
        GuardrailPolicies p = new GuardrailPolicies();
        p.setName("p");
        p.setBehaviour("block");
        p.setApplyToAllServers(false);
        p.setSelectedAgentServersV2(new ArrayList<>(Arrays.asList(new SelectedServer("a.akto.io", "a.akto.io"))));

        ArgusPostureService.GuardrailsCoverageBreakdown b =
                ArgusPostureService.computeCoverage(Arrays.asList(host(1, "a.akto.io")), Arrays.asList(p));

        assertEquals(1, b.enforcing.size());
    }

    @Test
    public void computeCoverage_nullHostNameIsNeverCovered() {
        ArgusPostureService.GuardrailsCoverageBreakdown b = ArgusPostureService.computeCoverage(
                Arrays.asList(host(1, null)),
                Arrays.asList(fleetWide("all", "block")));

        assertEquals(1, b.uncovered.size());
        assertEquals(0, b.covered());
    }

    @Test
    public void computeCoverage_nullPolicyInListIsSkipped() {
        ArgusPostureService.GuardrailsCoverageBreakdown b = ArgusPostureService.computeCoverage(
                Arrays.asList(host(1, "a.akto.io")),
                Arrays.asList(null, targeted("p", "block", "a.akto.io")));

        assertEquals(1, b.enforcing.size());
    }

    // applyToDeviceIds == null means "no device targeting", which is the only shape an Argus
    // policy ever has; a non-null list is an Atlas policy and must not widen to every device.
    @Test
    public void computeCoverage_resolvedDeviceIdsThatExcludeTheAsset_leaveItUncovered() {
        GuardrailPolicies p = fleetWide("p", "block");
        p.setApplyToDeviceIds(new ArrayList<>(Arrays.asList("some-other-device")));

        ArgusPostureService.GuardrailsCoverageBreakdown b =
                ArgusPostureService.computeCoverage(Arrays.asList(host(1, "a.akto.io")), Arrays.asList(p));

        assertEquals(1, b.uncovered.size());
    }

    @Test
    public void computeCoverage_coveringPoliciesRecordedOnlyForCoveredAssets() {
        List<ApiCollection> assets = Arrays.asList(host(1, "a.akto.io"), host(2, "b.akto.io"));

        ArgusPostureService.GuardrailsCoverageBreakdown b =
                ArgusPostureService.computeCoverage(assets, Arrays.asList(targeted("p", "alert", "a.akto.io")));

        assertNotNull(b.coveringPolicies.get(1));
        assertNull(b.coveringPolicies.get(2));
    }

    @Test
    public void computeCoverage_bucketsAlwaysSumToAssetCount() {
        List<ApiCollection> assets = Arrays.asList(
                host(1, "a.akto.io"), host(2, "b.akto.io"), host(3, "c.akto.io"), host(4, "d.akto.io"));

        ArgusPostureService.GuardrailsCoverageBreakdown b = ArgusPostureService.computeCoverage(assets,
                Arrays.asList(targeted("blocking", "block", "a.akto.io"),
                              targeted("alerting", "alert", "b.akto.io")));

        assertEquals(1, b.enforcing.size());
        assertEquals(1, b.alertOnly.size());
        assertEquals(2, b.uncovered.size());
        assertEquals(assets.size(), b.uncovered.size() + b.covered());
    }

    @Test
    public void computeCoverage_noAssets_isEmptyNotNull() {
        ArgusPostureService.GuardrailsCoverageBreakdown b =
                ArgusPostureService.computeCoverage(new ArrayList<>(), Arrays.asList(fleetWide("p", "block")));

        assertEquals(0, b.covered());
        assertTrue(b.uncovered.isEmpty());
    }

    // ── assetsIn ───────────────────────────────────────────────────────────────

    @Test
    public void assetsIn_allOrBlank_returnsEverything() {
        List<ApiCollection> assets = Arrays.asList(env(1, "DEV"), env(2, "STAGING"), env(3, null));

        assertEquals(3, ArgusPostureService.assetsIn(assets, "all").size());
        assertEquals(3, ArgusPostureService.assetsIn(assets, null).size());
        assertEquals(3, ArgusPostureService.assetsIn(assets, "   ").size());
    }

    @Test
    public void assetsIn_unknownEnvironmentId_returnsEverythingRatherThanNothing() {
        List<ApiCollection> assets = Arrays.asList(env(1, "DEV"), env(2, "STAGING"));

        assertEquals(2, ArgusPostureService.assetsIn(assets, "does-not-exist").size());
    }

    @Test
    public void assetsIn_filtersToTheRequestedBucket() {
        List<ApiCollection> assets = Arrays.asList(
                env(1, "DEV"), env(2, "STAGING"), env(3, "UAT"), env(4, null), env(5, "PROD"));

        assertEquals(1, ArgusPostureService.assetsIn(assets, "development").size());
        assertEquals(2, ArgusPostureService.assetsIn(assets, "staging").size());
        assertEquals(2, ArgusPostureService.assetsIn(assets, "production").size());
    }

    // ── envBucket ──────────────────────────────────────────────────────────────

    @Test
    public void envBucket_untaggedIsProduction() {
        assertEquals("Production", ArgusPostureService.envBucket(null));
        assertEquals("Production", ArgusPostureService.envBucket(""));
        assertEquals("Production", ArgusPostureService.envBucket("   "));
    }

    @Test
    public void envBucket_knownStagingAliases() {
        for (String v : Arrays.asList("STAGING", "PREPROD", "UAT", "QA", "INTEG")) {
            assertEquals(v, "Staging", ArgusPostureService.envBucket(v));
        }
    }

    @Test
    public void envBucket_isCaseAndWhitespaceInsensitive() {
        assertEquals("Development", ArgusPostureService.envBucket("dev"));
        assertEquals("Staging", ArgusPostureService.envBucket("  uat  "));
    }

    @Test
    public void envBucket_unrecognisedValueFallsBackToProduction() {
        assertEquals("Production", ArgusPostureService.envBucket("PROD"));
        assertEquals("Production", ArgusPostureService.envBucket("anything-else"));
    }

    // ── envTagValue ────────────────────────────────────────────────────────────

    @Test
    public void envTagValue_nullSafeOnMissingCollectionOrTags() {
        assertNull(ArgusPostureService.envTagValue(null));
        assertNull(ArgusPostureService.envTagValue(new ApiCollection()));
    }

    @Test
    public void envTagValue_readsTheEnvTypeTagIgnoringCase() {
        ApiCollection c = new ApiCollection();
        c.setTagsList(new ArrayList<>(Arrays.asList(
                new CollectionTags(0, "mcp-server", "MCP Server", null),
                new CollectionTags(0, "ENVTYPE", "STAGING", null))));

        assertEquals("STAGING", ArgusPostureService.envTagValue(c));
    }

    @Test
    public void envTagValue_noEnvTypeTagIsNull() {
        ApiCollection c = new ApiCollection();
        c.setTagsList(new ArrayList<>(Arrays.asList(new CollectionTags(0, "mcp-server", "MCP Server", null))));

        assertNull(ArgusPostureService.envTagValue(c));
    }

    // ── environmentKey ─────────────────────────────────────────────────────────

    @Test
    public void environmentKey_normalisesBlankAndAllToAll() {
        assertEquals("all", ArgusPostureService.environmentKey(null));
        assertEquals("all", ArgusPostureService.environmentKey(""));
        assertEquals("all", ArgusPostureService.environmentKey("ALL"));
    }

    @Test
    public void environmentKey_lowercasesAndTrims() {
        assertEquals("production", ArgusPostureService.environmentKey("  Production "));
    }

    // ── formatPercent ──────────────────────────────────────────────────────────

    @Test
    public void formatPercent_dropsTrailingZeroSoItMatchesTheTile() {
        assertEquals("0%", ArgusPostureService.formatPercent(0d));
        assertEquals("55%", ArgusPostureService.formatPercent(55d));
        assertEquals("100%", ArgusPostureService.formatPercent(100d));
    }

    @Test
    public void formatPercent_keepsARealFraction() {
        assertEquals("61.5%", ArgusPostureService.formatPercent(61.5d));
    }

    // ── filterForEnvironment ───────────────────────────────────────────────────

    @Test
    public void filterForEnvironment_blankOrUnknownIsUnfiltered() {
        assertEquals(new org.bson.BsonDocument(), ArgusPostureService.filterForEnvironment(null)
                .toBsonDocument(org.bson.BsonDocument.class, com.mongodb.MongoClientSettings.getDefaultCodecRegistry()));
        assertEquals(new org.bson.BsonDocument(), ArgusPostureService.filterForEnvironment("nope")
                .toBsonDocument(org.bson.BsonDocument.class, com.mongodb.MongoClientSettings.getDefaultCodecRegistry()));
    }

    @Test
    public void filterForEnvironment_knownEnvironmentsProduceARealFilter() {
        for (String env : Arrays.asList("development", "staging", "production")) {
            org.bson.BsonDocument d = ArgusPostureService.filterForEnvironment(env)
                    .toBsonDocument(org.bson.BsonDocument.class, com.mongodb.MongoClientSettings.getDefaultCodecRegistry());
            assertFalse(env, d.isEmpty());
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static ApiCollection host(int id, String hostName) {
        ApiCollection c = new ApiCollection();
        c.setId(id);
        c.setHostName(hostName);
        return c;
    }

    private static ApiCollection env(int id, String envType) {
        ApiCollection c = host(id, "h" + id + ".akto.io");
        if (envType != null) {
            c.setTagsList(new ArrayList<>(Arrays.asList(
                    new CollectionTags(0, Constants.AKTO_ENV_TYPE_TAG, envType, null))));
        }
        return c;
    }

    private static GuardrailPolicies fleetWide(String name, String behaviour) {
        GuardrailPolicies p = new GuardrailPolicies();
        p.setName(name);
        p.setBehaviour(behaviour);
        p.setApplyToAllServers(true);
        return p;
    }

    private static GuardrailPolicies targeted(String name, String behaviour, String server) {
        GuardrailPolicies p = new GuardrailPolicies();
        p.setName(name);
        p.setBehaviour(behaviour);
        p.setApplyToAllServers(false);
        p.setSelectedMcpServersV2(new ArrayList<>(Arrays.asList(new SelectedServer(server, server))));
        return p;
    }
}

package com.akto.service.posture;

import com.akto.dto.ApiCollection;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.GuardrailPolicies.SelectedServer;
import com.akto.dto.traffic.CollectionTags;
import com.akto.util.Constants;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

// Pure-function tests for ArgusPostureUtils; no Mongo needed. Everything asserted here is either
// public or package-private, so nothing in the production class had its scope widened for testing.
public class TestArgusPostureUtils {

    // ── computeCoverage ────────────────────────────────────────────────────────

    @Test
    public void computeCoverage_noPolicies_everythingUncovered() {
        List<ApiCollection> assets = Arrays.asList(host(1, "a.akto.io"), host(2, "b.akto.io"));

        ArgusPostureUtils.GuardrailsCoverageBreakdown b =
                ArgusPostureUtils.computeCoverage(assets, new ArrayList<>());

        assertEquals(2, b.uncovered.size());
        assertEquals(0, b.covered());
        assertTrue(b.coveringPolicies.isEmpty());
    }

    @Test
    public void computeCoverage_applyToAllServers_coversEveryHost() {
        List<ApiCollection> assets = Arrays.asList(host(1, "a.akto.io"), host(2, "b.akto.io"));

        ArgusPostureUtils.GuardrailsCoverageBreakdown b =
                ArgusPostureUtils.computeCoverage(assets, Arrays.asList(fleetWide("all", "block")));

        assertEquals(0, b.uncovered.size());
        assertEquals(2, b.enforcing.size());
        assertEquals(2, b.covered());
    }

    @Test
    public void computeCoverage_blockingPolicy_isEnforcing() {
        ArgusPostureUtils.GuardrailsCoverageBreakdown b = ArgusPostureUtils.computeCoverage(
                Arrays.asList(host(1, "a.akto.io")),
                Arrays.asList(targeted("p", "block", "a.akto.io")));

        assertEquals(1, b.enforcing.size());
        assertEquals(0, b.alertOnly.size());
    }

    @Test
    public void computeCoverage_nonBlockingBehaviours_areAlertOnly() {
        for (String behaviour : Arrays.asList("alert", "warn", "approval")) {
            ArgusPostureUtils.GuardrailsCoverageBreakdown b = ArgusPostureUtils.computeCoverage(
                    Arrays.asList(host(1, "a.akto.io")),
                    Arrays.asList(targeted("p", behaviour, "a.akto.io")));

            assertEquals(behaviour, 1, b.alertOnly.size());
            assertEquals(behaviour, 0, b.enforcing.size());
        }
    }

    @Test
    public void computeCoverage_blockingWinsOverAlertOnSameAsset() {
        ArgusPostureUtils.GuardrailsCoverageBreakdown b = ArgusPostureUtils.computeCoverage(
                Arrays.asList(host(1, "a.akto.io")),
                Arrays.asList(targeted("alerting", "alert", "a.akto.io"),
                              targeted("blocking", "block", "a.akto.io")));

        assertEquals(1, b.enforcing.size());
        assertEquals(0, b.alertOnly.size());
        assertEquals(2, b.coveringPolicies.get(1).size());
    }

    @Test
    public void computeCoverage_behaviourIsCaseInsensitive() {
        ArgusPostureUtils.GuardrailsCoverageBreakdown b = ArgusPostureUtils.computeCoverage(
                Arrays.asList(host(1, "a.akto.io")),
                Arrays.asList(targeted("p", "BLOCK", "a.akto.io")));

        assertEquals(1, b.enforcing.size());
    }

    @Test
    public void computeCoverage_hostMatchesBySuffix() {
        ArgusPostureUtils.GuardrailsCoverageBreakdown b = ArgusPostureUtils.computeCoverage(
                Arrays.asList(host(1, "team.mcp.akto.io")),
                Arrays.asList(targeted("p", "block", "mcp.akto.io")));

        assertEquals(1, b.enforcing.size());
    }

    @Test
    public void computeCoverage_hostMatchesAsInnerSegment() {
        ArgusPostureUtils.GuardrailsCoverageBreakdown b = ArgusPostureUtils.computeCoverage(
                Arrays.asList(host(1, "dev.mcp.akto.io")),
                Arrays.asList(targeted("p", "block", "mcp")));

        assertEquals(1, b.enforcing.size());
    }

    @Test
    public void computeCoverage_partialNameIsNotASubstringMatch() {
        ArgusPostureUtils.GuardrailsCoverageBreakdown b = ArgusPostureUtils.computeCoverage(
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

        ArgusPostureUtils.GuardrailsCoverageBreakdown b =
                ArgusPostureUtils.computeCoverage(Arrays.asList(host(1, "a.akto.io")), Arrays.asList(p));

        assertEquals(1, b.uncovered.size());
    }

    @Test
    public void computeCoverage_agentServerListAlsoMatches() {
        GuardrailPolicies p = new GuardrailPolicies();
        p.setName("p");
        p.setBehaviour("block");
        p.setApplyToAllServers(false);
        p.setSelectedAgentServersV2(new ArrayList<>(Arrays.asList(new SelectedServer("a.akto.io", "a.akto.io"))));

        ArgusPostureUtils.GuardrailsCoverageBreakdown b =
                ArgusPostureUtils.computeCoverage(Arrays.asList(host(1, "a.akto.io")), Arrays.asList(p));

        assertEquals(1, b.enforcing.size());
    }

    @Test
    public void computeCoverage_nullHostNameIsNeverCovered() {
        ArgusPostureUtils.GuardrailsCoverageBreakdown b = ArgusPostureUtils.computeCoverage(
                Arrays.asList(host(1, null)),
                Arrays.asList(fleetWide("all", "block")));

        assertEquals(1, b.uncovered.size());
        assertEquals(0, b.covered());
    }

    @Test
    public void computeCoverage_nullPolicyInListIsSkipped() {
        ArgusPostureUtils.GuardrailsCoverageBreakdown b = ArgusPostureUtils.computeCoverage(
                Arrays.asList(host(1, "a.akto.io")),
                Arrays.asList(null, targeted("p", "block", "a.akto.io")));

        assertEquals(1, b.enforcing.size());
    }

    // Devices are an Atlas concept; an Argus policy never populates applyToDeviceIds, so coverage
    // here is decided purely by server targeting.
    @Test
    public void computeCoverage_deviceTargetingIsIgnored() {
        GuardrailPolicies p = fleetWide("p", "block");
        p.setApplyToDeviceIds(new ArrayList<>(Arrays.asList("some-other-device")));

        ArgusPostureUtils.GuardrailsCoverageBreakdown b =
                ArgusPostureUtils.computeCoverage(Arrays.asList(host(1, "a.akto.io")), Arrays.asList(p));

        assertEquals(0, b.uncovered.size());
        assertEquals(1, b.enforcing.size());
    }

    @Test
    public void computeCoverage_coveringPoliciesRecordedOnlyForCoveredAssets() {
        List<ApiCollection> assets = Arrays.asList(host(1, "a.akto.io"), host(2, "b.akto.io"));

        ArgusPostureUtils.GuardrailsCoverageBreakdown b =
                ArgusPostureUtils.computeCoverage(assets, Arrays.asList(targeted("p", "alert", "a.akto.io")));

        assertNotNull(b.coveringPolicies.get(1));
        assertNull(b.coveringPolicies.get(2));
    }

    @Test
    public void computeCoverage_bucketsAlwaysSumToAssetCount() {
        List<ApiCollection> assets = Arrays.asList(
                host(1, "a.akto.io"), host(2, "b.akto.io"), host(3, "c.akto.io"), host(4, "d.akto.io"));

        ArgusPostureUtils.GuardrailsCoverageBreakdown b = ArgusPostureUtils.computeCoverage(assets,
                Arrays.asList(targeted("blocking", "block", "a.akto.io"),
                              targeted("alerting", "alert", "b.akto.io")));

        assertEquals(1, b.enforcing.size());
        assertEquals(1, b.alertOnly.size());
        assertEquals(2, b.uncovered.size());
        assertEquals(assets.size(), b.uncovered.size() + b.covered());
    }

    @Test
    public void computeCoverage_noAssets_isEmptyNotNull() {
        ArgusPostureUtils.GuardrailsCoverageBreakdown b =
                ArgusPostureUtils.computeCoverage(new ArrayList<>(), Arrays.asList(fleetWide("p", "block")));

        assertEquals(0, b.covered());
        assertTrue(b.uncovered.isEmpty());
    }

    @Test
    public void computeCoverage_hostlessAssetIsCoveredByFleetWidePolicy() {
        ApiCollection hostless = host(1, null);
        hostless.setName("aria-agentic");

        ArgusPostureUtils.GuardrailsCoverageBreakdown b = ArgusPostureUtils.computeCoverage(
                Arrays.asList(hostless), Arrays.asList(fleetWide("all", "block")));

        assertEquals(0, b.uncovered.size());
        assertEquals(1, b.enforcing.size());
    }

    @Test
    public void computeCoverage_hostlessAssetMatchesAPolicyTargetingItsName() {
        ApiCollection hostless = host(1, null);
        hostless.setName("aria-agentic");

        ArgusPostureUtils.GuardrailsCoverageBreakdown b = ArgusPostureUtils.computeCoverage(
                Arrays.asList(hostless), Arrays.asList(targeted("p", "alert", "aria-agentic")));

        assertEquals(1, b.alertOnly.size());
    }

    @Test
    public void computeCoverage_assetWithNeitherHostNorNameStaysUncovered() {
        ArgusPostureUtils.GuardrailsCoverageBreakdown b = ArgusPostureUtils.computeCoverage(
                Arrays.asList(host(1, null)), Arrays.asList(fleetWide("all", "block")));

        assertEquals(1, b.uncovered.size());
    }

    // ── worstIssue ─────────────────────────────────────────────────────────────

    @Test
    public void worstIssue_neverScannedWithMaliciousActivity_usesGapTextNotGenericFindingsText() {
        ApiCollection agent = agentWithRedTeamGap(100,
                "Red-teaming scan not run for this agent, and it has malicious activity");

        assertEquals("Red-teaming scan not run for this agent, and it has malicious activity",
                ArgusPostureUtils.worstIssue(agent));
    }

    @Test
    public void worstIssue_neverScannedNoMaliciousActivity_usesGapTextWithoutMaliciousClause() {
        ApiCollection agent = agentWithRedTeamGap(76, "Red-teaming scan not run for this agent");

        assertEquals("Red-teaming scan not run for this agent", ArgusPostureUtils.worstIssue(agent));
    }

    @Test
    public void worstIssue_realOpenFindings_usesFixedCategoryTextNotGapText() {
        ApiCollection agent = new ApiCollection();
        agent.setPostureSubScores(subScores(90, 0, 0, 0, 0, 0));
        agent.setPostureGaps(null); // scanned agents never get a redTeam gap entry

        assertEquals("Has open red-teaming findings", ArgusPostureUtils.worstIssue(agent));
    }

    @Test
    public void worstIssue_noWorstCategory_fallsBackToNoSignificantIssues() {
        ApiCollection agent = new ApiCollection();
        agent.setPostureSubScores(subScores(0, 0, 0, 0, 0, 0));

        assertEquals("No significant issues detected", ArgusPostureUtils.worstIssue(agent));
    }

    private static ApiCollection agentWithRedTeamGap(double redTeamSubScore, String gapText) {
        ApiCollection agent = new ApiCollection();
        agent.setPostureSubScores(subScores(redTeamSubScore, 0, 0, 0, 0, 0));
        Map<String, String> gaps = new HashMap<>();
        gaps.put(PostureScoreCategory.RED_TEAM.key, gapText);
        agent.setPostureGaps(gaps);
        return agent;
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

    // ── assetsIn ───────────────────────────────────────────────────────────────

    @Test
    public void assetsIn_allOrBlank_returnsEverything() {
        List<ApiCollection> assets = Arrays.asList(env(1, "DEV"), env(2, "STAGING"), env(3, null));

        assertEquals(3, ArgusPostureUtils.assetsIn(assets, "all").size());
        assertEquals(3, ArgusPostureUtils.assetsIn(assets, null).size());
        assertEquals(3, ArgusPostureUtils.assetsIn(assets, "   ").size());
    }

    @Test
    public void assetsIn_unknownEnvironmentId_returnsEverythingRatherThanNothing() {
        List<ApiCollection> assets = Arrays.asList(env(1, "DEV"), env(2, "STAGING"));

        assertEquals(2, ArgusPostureUtils.assetsIn(assets, "does-not-exist").size());
    }

    @Test
    public void assetsIn_filtersToTheRequestedBucket() {
        List<ApiCollection> assets = Arrays.asList(
                env(1, "DEV"), env(2, "STAGING"), env(3, "UAT"), env(4, null), env(5, "PROD"));

        assertEquals(1, ArgusPostureUtils.assetsIn(assets, "development").size());
        assertEquals(2, ArgusPostureUtils.assetsIn(assets, "staging").size());
        assertEquals(2, ArgusPostureUtils.assetsIn(assets, "production").size());
    }

    // ── envBucket ──────────────────────────────────────────────────────────────

    @Test
    public void envBucket_untaggedIsProduction() {
        assertEquals("Production", ArgusPostureUtils.envBucket(null));
        assertEquals("Production", ArgusPostureUtils.envBucket(""));
        assertEquals("Production", ArgusPostureUtils.envBucket("   "));
    }

    @Test
    public void envBucket_knownStagingAliases() {
        for (String v : Arrays.asList("STAGING", "PREPROD", "UAT", "QA", "INTEG")) {
            assertEquals(v, "Staging", ArgusPostureUtils.envBucket(v));
        }
    }

    @Test
    public void envBucket_isCaseAndWhitespaceInsensitive() {
        assertEquals("Development", ArgusPostureUtils.envBucket("dev"));
        assertEquals("Staging", ArgusPostureUtils.envBucket("  uat  "));
    }

    @Test
    public void envBucket_unrecognisedValueFallsBackToProduction() {
        assertEquals("Production", ArgusPostureUtils.envBucket("PROD"));
        assertEquals("Production", ArgusPostureUtils.envBucket("anything-else"));
    }

    // ── envTagValue ────────────────────────────────────────────────────────────

    @Test
    public void envTagValue_nullSafeOnMissingCollectionOrTags() {
        assertNull(ArgusPostureUtils.envTagValue(null));
        assertNull(ArgusPostureUtils.envTagValue(new ApiCollection()));
    }

    @Test
    public void envTagValue_readsTheEnvTypeTagIgnoringCase() {
        ApiCollection c = new ApiCollection();
        c.setTagsList(new ArrayList<>(Arrays.asList(
                new CollectionTags(0, "mcp-server", "MCP Server", null),
                new CollectionTags(0, "ENVTYPE", "STAGING", null))));

        assertEquals("STAGING", ArgusPostureUtils.envTagValue(c));
    }

    @Test
    public void envTagValue_noEnvTypeTagIsNull() {
        ApiCollection c = new ApiCollection();
        c.setTagsList(new ArrayList<>(Arrays.asList(new CollectionTags(0, "mcp-server", "MCP Server", null))));

        assertNull(ArgusPostureUtils.envTagValue(c));
    }

    // ── environmentKey ─────────────────────────────────────────────────────────

    @Test
    public void environmentKey_normalisesBlankAndAllToAll() {
        assertEquals("all", ArgusPostureUtils.environmentKey(null));
        assertEquals("all", ArgusPostureUtils.environmentKey(""));
        assertEquals("all", ArgusPostureUtils.environmentKey("ALL"));
    }

    @Test
    public void environmentKey_lowercasesAndTrims() {
        assertEquals("production", ArgusPostureUtils.environmentKey("  Production "));
    }

    // ── filterForEnvironment ───────────────────────────────────────────────────

    @Test
    public void filterForEnvironment_blankOrUnknownIsUnfiltered() {
        assertEquals(new org.bson.BsonDocument(), ArgusPostureUtils.filterForEnvironment(null)
                .toBsonDocument(org.bson.BsonDocument.class, com.mongodb.MongoClientSettings.getDefaultCodecRegistry()));
        assertEquals(new org.bson.BsonDocument(), ArgusPostureUtils.filterForEnvironment("nope")
                .toBsonDocument(org.bson.BsonDocument.class, com.mongodb.MongoClientSettings.getDefaultCodecRegistry()));
    }

    @Test
    public void filterForEnvironment_knownEnvironmentsProduceARealFilter() {
        for (String env : Arrays.asList("development", "staging", "production")) {
            org.bson.BsonDocument d = ArgusPostureUtils.filterForEnvironment(env)
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

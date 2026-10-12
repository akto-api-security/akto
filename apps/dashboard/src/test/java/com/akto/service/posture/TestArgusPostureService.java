package com.akto.service.posture;

import com.akto.dto.AgenticPostureScoreHistory;
import com.akto.dto.ApiCollection;
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
}

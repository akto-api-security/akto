package com.akto.service.posture;

import com.akto.dto.ApiCollection;
import com.akto.dto.traffic.CollectionTags;
import com.akto.service.insights.InsightContext;
import com.akto.service.insights.InsightDataBundle;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

// DAO-free drill levels only: the posture score breakdown and the paginated agent list.
public class TestArgusAgentPostureDrillService {

    private final ArgusAgentPostureDrillService service = new ArgusAgentPostureDrillService();

    @Test
    public void postureScoreRoot_pointsAddUpToAverageComposite() {
        // Composites: agent A = 30*0.5 + 10*1.0 = 25, agent B = 10*1.0 = 10 -> account average 17.5.
        ApiCollection a = agent(1, "a", 25.0, subScores(50, 0, 0, 100, 0, 0));
        ApiCollection b = agent(2, "b", 10.0, subScores(0, 0, 100, 0, 0, 0));

        PostureDrillResult result = service.fetchPostureScoreDrill(bundle(Arrays.asList(a, b)), "", 0, 10);

        assertEquals(PostureScoreCategory.values().length, result.getTotal());
        double totalPoints = 0;
        for (Map<String, Object> row : result.getRows()) totalPoints += ((Number) row.get("points")).doubleValue();
        assertEquals(17.5, totalPoints, 0.05);
        assertEquals("17.5", String.valueOf(result.getSummary().get(0).getValue()));
        assertEquals("Red teaming", result.getRows().get(0).get("category")); // 7.5 pts, the largest
        assertEquals("a", result.getRows().get(0).get("topContributor"));
        assertTrue(result.isDrillable());
    }

    @Test
    public void highRiskAgents_listsEveryScoredAgentWithPagination() {
        List<ApiCollection> agents = new ArrayList<>();
        for (int i = 1; i <= 12; i++) agents.add(agent(i, "agent-" + i, (double) i, subScores(0, 0, i, 0, 0, 0)));

        PostureDrillResult page1 = service.fetchHighRiskAgentsDrill(bundle(agents), "all", "", 0, 10);
        PostureDrillResult page2 = service.fetchHighRiskAgentsDrill(bundle(agents), "all", "", 10, 10);

        assertEquals(12, page1.getTotal());
        assertEquals(10, page1.getRows().size());
        assertEquals(12, page1.getRows().get(0).get("id")); // highest score first
        assertEquals(2, page2.getRows().size());
        assertEquals("LOW", page2.getRows().get(1).get("severity")); // low scores are listed, not filtered out
    }

    @Test
    public void unknownAgent_returnsEmptyMessageInsteadOfFailing() {
        PostureDrillResult result = service.fetchHighRiskAgentsDrill(bundle(new ArrayList<>()), "all", "999", 0, 10);
        assertFalse(result.isDrillable());
        assertTrue(result.getEmptyMessage() != null);
    }

    @Test
    public void remediationFor_neverScanned_suggestsSchedulingAScanNotReRunning() {
        ApiCollection notScanned = agent(1, "a", 100.0, subScores(100, 0, 0, 0, 0, 0));
        Map<String, String> gaps = new HashMap<>();
        gaps.put(PostureScoreCategory.RED_TEAM.key, "Red-teaming scan not run for this agent, and it has malicious activity");
        notScanned.setPostureGaps(gaps);

        assertEquals("Schedule a red-team scan for this agent — it has never been scanned.",
                ArgusAgentPostureDrillService.remediationFor(PostureScoreCategory.RED_TEAM, notScanned));
    }

    @Test
    public void remediationFor_realOpenFindings_keepsTheFixOrAcceptText() {
        ApiCollection scanned = agent(1, "a", 90.0, subScores(90, 0, 0, 0, 0, 0));
        scanned.setPostureGaps(null);

        assertEquals(PostureScoreCategory.RED_TEAM.remediation,
                ArgusAgentPostureDrillService.remediationFor(PostureScoreCategory.RED_TEAM, scanned));
    }

    @Test
    public void remediationFor_nonRedTeamCategory_alwaysUsesItsOwnFixedText() {
        ApiCollection a = agent(1, "a", 50.0, subScores(0, 0, 0, 0, 100, 0));
        Map<String, String> gaps = new HashMap<>();
        gaps.put(PostureScoreCategory.RED_TEAM.key, "Red-teaming scan not run for this agent");
        a.setPostureGaps(gaps);

        assertEquals(PostureScoreCategory.ACCESS_AUTH.remediation,
                ArgusAgentPostureDrillService.remediationFor(PostureScoreCategory.ACCESS_AUTH, a));
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

    private static ApiCollection agent(int id, String name, Double postureScore, Map<String, Object> subScores) {
        ApiCollection c = new ApiCollection();
        c.setId(id);
        c.setName(name);
        c.setTagsList(new ArrayList<>(Arrays.asList(new CollectionTags(0, Constants.AKTO_GEN_AI_TAG, "x", null))));
        c.setPostureScore(postureScore);
        c.setPostureSubScores(subScores);
        return c;
    }

    private static InsightDataBundle bundle(List<ApiCollection> collections) {
        InsightContext ctx = new InsightContext(1, 1, CONTEXT_SOURCE.AGENTIC, 0, 0);
        return new InsightDataBundle(ctx, collections, new HashMap<>(), new HashMap<>(), new HashMap<>(),
                new ArrayList<>(), new ArrayList<>(), new HashSet<>(), new HashMap<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), false, collections,
                new HashMap<>(), null);
    }
}

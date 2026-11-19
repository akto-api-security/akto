package com.akto.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiAccessType;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.ApiInfo.RiskScoreFactor;
import com.akto.dto.ComponentRiskAnalysis;
import com.akto.dto.type.URLMethods;
import com.akto.util.Constants;
import com.akto.utils.McpApiRiskScoreUtils.McpRiskScoreContext;

import static com.akto.jobs.utils.Utils.getRiskScoreValueFromSeverityScore;

public class TestRiskScoreExplainer {

    private ApiInfo baseApi(int collectionId, String url, int discoveredAt, float threatScore) {
        ApiInfoKey key = new ApiInfoKey(collectionId, url, URLMethods.Method.GET);
        ApiInfo apiInfo = new ApiInfo();
        apiInfo.setId(key);
        apiInfo.setDiscoveredTimestamp(discoveredAt);
        apiInfo.setThreatScore(threatScore);
        apiInfo.setApiAccessTypes(new HashSet<>());
        return apiInfo;
    }

    private McpRiskScoreContext emptyContext() {
        return new McpRiskScoreContext(
                Collections.emptySet(), Collections.emptySet(), Collections.emptyMap(),
                Collections.emptyMap(), Collections.emptyMap());
    }

    private McpRiskScoreContext contextWithAgent(int collectionId, double base, String reason) {
        Map<Integer, Double> scores = new HashMap<>();
        scores.put(collectionId, base);
        Map<Integer, String> reasons = new HashMap<>();
        if (reason != null) {
            reasons.put(collectionId, reason);
        }
        return new McpRiskScoreContext(
                Collections.emptySet(), Collections.emptySet(), Collections.emptyMap(),
                scores, reasons);
    }

    private McpRiskScoreContext contextWithMaliciousTool(int collectionId, String toolName, boolean malicious,
            boolean privileged, boolean maliciousServer) {
        Set<Integer> mcpIds = new HashSet<>(Collections.singletonList(collectionId));
        Set<Integer> maliciousIds = maliciousServer
                ? new HashSet<>(Collections.singletonList(collectionId))
                : Collections.emptySet();
        Map<String, ComponentRiskAnalysis> map = new HashMap<>();
        ComponentRiskAnalysis cra = new ComponentRiskAnalysis();
        cra.setIsComponentMalicious(malicious);
        cra.setHasPrivilegedAccess(privileged);
        String key = collectionId + "\0" + Constants.AKTO_MCP_TOOL + '\0' + toolName;
        map.put(key, cra);
        return new McpRiskScoreContext(mcpIds, maliciousIds, map, Collections.emptyMap(), Collections.emptyMap());
    }

    private float computeScore(ApiInfo apiInfo, boolean isSensitive, float severityScore, McpRiskScoreContext ctx) {
        float base = ApiInfoDao.getRiskScore(apiInfo, isSensitive, getRiskScoreValueFromSeverityScore(severityScore));
        if (ctx == null || ctx.isEmpty()) {
            return base;
        }
        return McpApiRiskScoreUtils.getRiskScoreWithMcpAdjustments(apiInfo, base, ctx);
    }

    private List<RiskScoreFactor> explain(ApiInfo apiInfo, boolean isSensitive, float severityScore,
            List<String> issueNames, List<String> sensitiveNames, McpRiskScoreContext ctx, float score) {
        int now = Context.now();
        return RiskScoreExplainer.explain(apiInfo, isSensitive, severityScore, issueNames, sensitiveNames, ctx,
                score, now, now);
    }

    @Test
    public void newPublicSensitiveHighFinding() {
        int now = Context.now();
        ApiInfo apiInfo = baseApi(1, "/users", now - 12 * 24 * 60 * 60, 0);
        apiInfo.getApiAccessTypes().add(ApiAccessType.PUBLIC);
        float severityScore = 100f;
        float score = computeScore(apiInfo, true, severityScore, emptyContext());
        List<RiskScoreFactor> factors = explain(apiInfo, true, severityScore,
                Collections.singletonList("BOLA"), Arrays.asList("Email", "Phone"), emptyContext(), score);

        assertNotNull(factors);
        assertFalse(factors.isEmpty());
        assertTrue(RiskScoreExplainer.isAcceptable(factors, score, emptyContext(), apiInfo));
        assertEquals(2f, factors.stream().filter(f -> f.getLabel().equals("Open security findings"))
                .findFirst().get().getPoints(), 0.001f);
        assertTrue(factors.stream().anyMatch(f -> "BOLA".equals(f.getDetail())
                || (f.getDetail() != null && f.getDetail().contains("BOLA"))));
        assertTrue(factors.stream().anyMatch(f -> f.getDetail() != null && f.getDetail().contains("Email")));
    }

    @Test
    public void sensitiveWithDataTypeNames() {
        int now = Context.now();
        ApiInfo apiInfo = baseApi(1, "/profile", now - 60 * 24 * 60 * 60, 0);
        float score = computeScore(apiInfo, true, 0, emptyContext());
        List<RiskScoreFactor> factors = explain(apiInfo, true, 0, Collections.emptyList(),
                Collections.singletonList("Email"), emptyContext(), score);
        assertNotNull(factors);
        assertEquals(1, factors.size());
        assertEquals(1f, factors.get(0).getPoints(), 0.001f);
        assertEquals("Email", factors.get(0).getDetail());
    }

    @Test
    public void threatSuppressesNewAndUsesSeverityWord() {
        int now = Context.now();
        ApiInfo apiInfo = baseApi(1, "/users", now - 5 * 24 * 60 * 60, 0.75f);
        apiInfo.getApiAccessTypes().add(ApiAccessType.PUBLIC);
        float score = computeScore(apiInfo, false, 0, emptyContext());
        List<RiskScoreFactor> factors = explain(apiInfo, false, 0, Collections.emptyList(),
                Collections.emptyList(), emptyContext(), score);
        assertNotNull(factors);
        assertTrue(RiskScoreExplainer.isAcceptable(factors, score, emptyContext(), apiInfo));
        assertFalse(factors.stream().anyMatch(f -> f.getLabel().contains("Discovered")));
        assertTrue(factors.stream().anyMatch(f -> f.getLabel().contains("high severity")));
        assertEquals(0.75f, factors.stream().filter(f -> f.getLabel().contains("Threat"))
                .findFirst().get().getPoints(), 0.001f);
    }

    @Test
    public void maliciousMcpComponentCappedAtFive() {
        int now = Context.now();
        ApiInfo apiInfo = baseApi(10, "/tools/delete_user", now - 60 * 24 * 60 * 60, 0);
        apiInfo.getApiAccessTypes().add(ApiAccessType.PUBLIC);
        apiInfo.setParentMcpToolNames(Collections.singletonList("delete_user"));
        // public(1) + sensitive(1) + malicious(3) + privileged(1) = 6, capped to 5
        McpRiskScoreContext ctx = contextWithMaliciousTool(10, "delete_user", true, true, false);
        float score = computeScore(apiInfo, true, 0, ctx);
        assertEquals(5f, score, 0.001f);
        List<RiskScoreFactor> factors = explain(apiInfo, true, 0, Collections.emptyList(),
                Collections.singletonList("Email"), ctx, score);
        assertNotNull(factors);
        assertTrue(factors.stream().anyMatch(f -> RiskScoreExplainer.CAP_LABEL.equals(f.getLabel())));
        assertEquals(-1f, factors.stream().filter(f -> RiskScoreExplainer.CAP_LABEL.equals(f.getLabel()))
                .findFirst().get().getPoints(), 0.001f);
        assertEquals(score, RiskScoreExplainer.sumPoints(factors), 0.05f);
        assertTrue(RiskScoreExplainer.isAcceptable(factors, score, ctx, apiInfo));
    }

    @Test
    public void maliciousServerFloor() {
        int now = Context.now();
        ApiInfo apiInfo = baseApi(20, "/tools/ping", now - 60 * 24 * 60 * 60, 0);
        apiInfo.getApiAccessTypes().add(ApiAccessType.PUBLIC);
        apiInfo.setParentMcpToolNames(Collections.singletonList("ping"));
        McpRiskScoreContext ctx = contextWithMaliciousTool(20, "ping", false, false, true);
        float score = computeScore(apiInfo, false, 0, ctx);
        assertEquals(3f, score, 0.001f);
        List<RiskScoreFactor> factors = explain(apiInfo, false, 0, Collections.emptyList(),
                Collections.emptyList(), ctx, score);
        assertNotNull(factors);
        assertTrue(factors.stream().anyMatch(f -> RiskScoreExplainer.FLOOR_LABEL.equals(f.getLabel())));
        assertEquals(2f, factors.stream().filter(f -> RiskScoreExplainer.FLOOR_LABEL.equals(f.getLabel()))
                .findFirst().get().getPoints(), 0.001f);
        assertEquals(score, RiskScoreExplainer.sumPoints(factors), 0.05f);
        assertTrue(RiskScoreExplainer.isAcceptable(factors, score, ctx, apiInfo));
    }

    @Test
    public void maliciousServerFloorWithNoOtherFactors() {
        int now = Context.now();
        ApiInfo apiInfo = baseApi(21, "/tools/idle", now - 60 * 24 * 60 * 60, 0);
        apiInfo.setParentMcpToolNames(Collections.singletonList("idle"));
        McpRiskScoreContext ctx = contextWithMaliciousTool(21, "idle", false, false, true);
        float score = computeScore(apiInfo, false, 0, ctx);
        assertEquals(3f, score, 0.001f);
        List<RiskScoreFactor> factors = explain(apiInfo, false, 0, Collections.emptyList(),
                Collections.emptyList(), ctx, score);
        assertNotNull(factors);
        assertEquals(1, factors.size());
        assertEquals(RiskScoreExplainer.FLOOR_LABEL, factors.get(0).getLabel());
        assertEquals(3f, factors.get(0).getPoints(), 0.001f);
    }

    @Test
    public void agentBaseScoreUsesFixedLabelAndDetail() {
        int now = Context.now();
        ApiInfo apiInfo = baseApi(30, "/agent", now - 60 * 24 * 60 * 60, 0);
        String reason = "Uses a tool that can change production data";
        McpRiskScoreContext ctx = contextWithAgent(30, 1.5, reason);
        float score = computeScore(apiInfo, true, 10f, ctx);
        List<RiskScoreFactor> factors = explain(apiInfo, true, 10f, Collections.singletonList("Broken Authentication"),
                Collections.singletonList("Email"), ctx, score);
        assertNotNull(factors);
        assertTrue(RiskScoreExplainer.isAcceptable(factors, score, ctx, apiInfo));
        RiskScoreFactor agent = factors.stream()
                .filter(f -> RiskScoreExplainer.AGENT_RISK_LABEL.equals(f.getLabel()))
                .findFirst().get();
        assertEquals(reason, agent.getDetail());
    }

    @Test
    public void agentReasonIsTruncatedInDetail() {
        int now = Context.now();
        ApiInfo apiInfo = baseApi(31, "/agent", now - 60 * 24 * 60 * 60, 0);
        StringBuilder longReason = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            longReason.append("abcdefghij");
        }
        McpRiskScoreContext ctx = contextWithAgent(31, 1.0, longReason.toString());
        float score = computeScore(apiInfo, false, 0, ctx);
        List<RiskScoreFactor> factors = explain(apiInfo, false, 0, Collections.emptyList(),
                Collections.emptyList(), ctx, score);
        assertNotNull(factors);
        RiskScoreFactor agent = factors.stream()
                .filter(f -> RiskScoreExplainer.AGENT_RISK_LABEL.equals(f.getLabel()))
                .findFirst().get();
        assertEquals(200, agent.getDetail().length());
    }

    @Test
    public void emptyContextDoesNotCap() {
        int now = Context.now();
        ApiInfo apiInfo = baseApi(1, "/users", now - 5 * 24 * 60 * 60, 1.0f);
        apiInfo.getApiAccessTypes().add(ApiAccessType.PUBLIC);
        float severityScore = 100f;
        float score = computeScore(apiInfo, true, severityScore, emptyContext());
        assertEquals(5f, score, 0.001f);
        List<RiskScoreFactor> factors = explain(apiInfo, true, severityScore, Collections.singletonList("BOLA"),
                Collections.singletonList("Email"), emptyContext(), score);
        assertNotNull(factors);
        assertFalse(factors.stream().anyMatch(f -> RiskScoreExplainer.CAP_LABEL.equals(f.getLabel())));
        assertEquals(RiskScoreExplainer.sumPoints(factors), score, 0.05f);
        assertTrue(RiskScoreExplainer.isAcceptable(factors, score, emptyContext(), apiInfo));
    }

    @Test
    public void emptyContextExactMatchWithoutCapLine() {
        int now = Context.now();
        ApiInfo apiInfo = baseApi(1, "/users", now - 5 * 24 * 60 * 60, 0);
        apiInfo.getApiAccessTypes().add(ApiAccessType.PUBLIC);
        float severityScore = 100f;
        float score = computeScore(apiInfo, true, severityScore, emptyContext());
        assertTrue(score <= 5.01f);
        List<RiskScoreFactor> factors = explain(apiInfo, true, severityScore, Collections.singletonList("BOLA"),
                Collections.singletonList("Email"), emptyContext(), score);
        assertNotNull(factors);
        assertEquals(RiskScoreExplainer.sumPoints(factors), score, 0.05f);
    }

    @Test
    public void lowSeverityOnly() {
        int now = Context.now();
        ApiInfo apiInfo = baseApi(1, "/users", now - 60 * 24 * 60 * 60, 0);
        float severityScore = 1f;
        float score = computeScore(apiInfo, false, severityScore, emptyContext());
        assertEquals(0.5f, score, 0.001f);
        List<RiskScoreFactor> factors = explain(apiInfo, false, severityScore,
                Collections.singletonList("SOME_LOW"), Collections.emptyList(), emptyContext(), score);
        assertNotNull(factors);
        assertEquals(1, factors.size());
        assertEquals(0.5f, factors.get(0).getPoints(), 0.001f);
    }

    @Test
    public void nullAccessTypesDoesNotAddPublicPoint() {
        int now = Context.now();
        ApiInfo apiInfo = baseApi(1, "/users", now - 60 * 24 * 60 * 60, 0);
        apiInfo.setApiAccessTypes(null);
        List<RiskScoreFactor> factors = RiskScoreExplainer.buildFactors(apiInfo, false, 0,
                Collections.emptyList(), Collections.emptyList(), emptyContext(), now);
        assertTrue(factors.isEmpty());
        assertFalse(factors.stream().anyMatch(f -> f.getLabel() != null && f.getLabel().contains("Public")));
    }

    @Test
    public void ensureApiAccessTypesContractKeepsNullAccessNonPublic() {
        int now = Context.now();
        ApiInfo apiInfo = baseApi(1, "/users", now - 60 * 24 * 60 * 60, 0);
        // Writer path normalizes null → empty set before getRiskScore; without that, getRiskScore NPEs.
        // Explainer must treat null the same as empty: no public point.
        apiInfo.setApiAccessTypes(new HashSet<>());
        float expected = ApiInfoDao.getRiskScore(apiInfo, false, 0);
        apiInfo.setApiAccessTypes(null);
        List<RiskScoreFactor> factors = RiskScoreExplainer.buildFactors(apiInfo, false, 0,
                Collections.emptyList(), Collections.emptyList(), emptyContext(), now);
        assertEquals(expected, RiskScoreExplainer.sumPoints(factors), 0.05f);
        assertFalse(factors.stream().anyMatch(f -> f.getLabel() != null && f.getLabel().contains("Public")));
    }

    @Test
    public void discoveredExactlyThirtyDaysAgoTwoTimeCheck() {
        int now = Context.now();
        int discovered = now - Constants.ONE_MONTH_TIMESTAMP;
        ApiInfo apiInfo = baseApi(1, "/users", discovered, 0);
        float baseAtNow = 0;
        if ((now - discovered) <= Constants.ONE_MONTH_TIMESTAMP) {
            baseAtNow += 1;
        }
        List<RiskScoreFactor> factors = RiskScoreExplainer.explain(apiInfo, false, 0,
                Collections.emptyList(), Collections.emptyList(), emptyContext(),
                baseAtNow, now, now + 1);
        assertNotNull(factors);
        assertFalse(factors.isEmpty());
        assertTrue(factors.stream().anyMatch(f -> f.getLabel().contains("Discovered")));
    }

    @Test
    public void mismatchReturnsNullNotEmptyList() {
        int now = Context.now();
        ApiInfo apiInfo = baseApi(1, "/users", now - 60 * 24 * 60 * 60, 0);
        apiInfo.getApiAccessTypes().add(ApiAccessType.PUBLIC);
        // Claim a score of 5 while factors only explain public=1
        List<RiskScoreFactor> factors = RiskScoreExplainer.explain(apiInfo, false, 0,
                Collections.emptyList(), Collections.emptyList(), emptyContext(),
                5f, now, now);
        assertNull(factors);
    }

    @Test
    public void matchingZeroScoreReturnsEmptyListNotNull() {
        int now = Context.now();
        ApiInfo apiInfo = baseApi(1, "/users", now - 60 * 24 * 60 * 60, 0);
        List<RiskScoreFactor> factors = explain(apiInfo, false, 0, Collections.emptyList(),
                Collections.emptyList(), emptyContext(), 0f);
        assertNotNull(factors);
        assertTrue(factors.isEmpty());
    }

    @Test
    public void removingMismatchGuardWouldStillNeedExactSum() {
        // Documents that isAcceptable requires exact sum; cap/floor leniency is gone.
        int now = Context.now();
        ApiInfo apiInfo = baseApi(1, "/users", now - 60 * 24 * 60 * 60, 0);
        apiInfo.getApiAccessTypes().add(ApiAccessType.PUBLIC);
        List<RiskScoreFactor> factors = RiskScoreExplainer.buildFactors(apiInfo, false, 0,
                Collections.emptyList(), Collections.emptyList(), emptyContext(), now);
        assertEquals(1f, RiskScoreExplainer.sumPoints(factors), 0.001f);
        assertFalse(RiskScoreExplainer.isAcceptable(factors, 5f, emptyContext(), apiInfo));
    }

    @Test
    public void mcpAdjustmentParityMaliciousPrivilegedAgentCapFloor() {
        int collectionId = 40;
        String tool = "admin_tool";
        ApiInfo apiInfo = baseApi(collectionId, "/mcp/" + tool, Context.now() - 60 * 24 * 60 * 60, 0);
        apiInfo.getApiAccessTypes().add(ApiAccessType.PUBLIC);
        apiInfo.setParentMcpToolNames(Collections.singletonList(tool));

        McpRiskScoreContext both = contextWithMaliciousTool(collectionId, tool, true, true, false);
        float base = ApiInfoDao.getRiskScore(apiInfo, false, 0);
        float withHelper = McpApiRiskScoreUtils.getRiskScoreWithMcpAdjustments(apiInfo, base, both);
        assertEquals(5f, withHelper, 0.001f);

        McpRiskScoreContext agent = contextWithAgent(collectionId, 2.0, "reason");
        float withAgent = McpApiRiskScoreUtils.getRiskScoreWithMcpAdjustments(apiInfo, base, agent);
        assertEquals(Math.min(5f, base + 2f), withAgent, 0.001f);

        McpRiskScoreContext floor = contextWithMaliciousTool(collectionId, tool, false, false, true);
        float withFloor = McpApiRiskScoreUtils.getRiskScoreWithMcpAdjustments(apiInfo, base, floor);
        assertEquals(3f, withFloor, 0.001f);

        assertEquals(base, McpApiRiskScoreUtils.getRiskScoreWithMcpAdjustments(apiInfo, base, emptyContext()), 0.001f);
    }
}

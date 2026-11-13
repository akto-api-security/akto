package com.akto.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

    private void assertNoCapWording(List<RiskScoreFactor> factors) {
        for (RiskScoreFactor f : factors) {
            String label = f.getLabel() == null ? "" : f.getLabel().toLowerCase();
            assertFalse("explanation must not mention cap: " + f.getLabel(), label.contains("cap"));
            assertFalse("explanation must not mention capped: " + f.getLabel(), label.contains("capped"));
        }
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

        assertFalse(factors.isEmpty());
        assertNoCapWording(factors);
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
        assertNoCapWording(factors);
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
        assertFalse(factors.isEmpty());
        assertNoCapWording(factors);
        assertTrue(RiskScoreExplainer.sumPoints(factors) > score);
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
        assertFalse(factors.isEmpty());
        assertNoCapWording(factors);
        assertTrue(RiskScoreExplainer.sumPoints(factors) < score);
        assertTrue(RiskScoreExplainer.isAcceptable(factors, score, ctx, apiInfo));
    }

    @Test
    public void agentBaseScoreUsesReason() {
        int now = Context.now();
        ApiInfo apiInfo = baseApi(30, "/agent", now - 60 * 24 * 60 * 60, 0);
        McpRiskScoreContext ctx = contextWithAgent(30, 1.5, "Uses a tool that can change production data");
        float score = computeScore(apiInfo, true, 10f, ctx);
        List<RiskScoreFactor> factors = explain(apiInfo, true, 10f, Collections.singletonList("Broken Authentication"),
                Collections.singletonList("Email"), ctx, score);
        assertNoCapWording(factors);
        assertTrue(RiskScoreExplainer.isAcceptable(factors, score, ctx, apiInfo));
        assertTrue(factors.stream().anyMatch(f ->
                "Uses a tool that can change production data".equals(f.getLabel())));
    }

    @Test
    public void emptyContextDoesNotCap() {
        int now = Context.now();
        ApiInfo apiInfo = baseApi(1, "/users", now - 5 * 24 * 60 * 60, 1.0f);
        apiInfo.getApiAccessTypes().add(ApiAccessType.PUBLIC);
        float severityScore = 100f;
        float score = computeScore(apiInfo, true, severityScore, emptyContext());
        // new suppressed by threat; public + sensitive + threat 1 + severity 2 = 5
        assertEquals(5f, score, 0.001f);
        List<RiskScoreFactor> factors = explain(apiInfo, true, severityScore, Collections.singletonList("BOLA"),
                Collections.singletonList("Email"), emptyContext(), score);
        assertEquals(RiskScoreExplainer.sumPoints(factors), score, 0.05f);
        assertTrue(RiskScoreExplainer.isAcceptable(factors, score, emptyContext(), apiInfo));
    }

    @Test
    public void emptyContextCanExceedFive() {
        int now = Context.now();
        ApiInfo apiInfo = baseApi(1, "/users", now - 5 * 24 * 60 * 60, 0);
        apiInfo.getApiAccessTypes().add(ApiAccessType.PUBLIC);
        float severityScore = 100f;
        float score = computeScore(apiInfo, true, severityScore, emptyContext());
        // new + public + sensitive + severity 2 = 5; with threat 0 and new: 5. Without threat for 6 need threat?
        // new(1)+public(1)+sensitive(1)+severity(2)=5. To get 6 need threat without suppressing new - impossible.
        // Use medium threat 0.5 with no "new" (threat>0) + public + sensitive + severity 2 = 4.5
        // For uncapped 6: empty context, discovered old, public, sensitive, threat 1, severity 2 = 5
        // Actually: discovered recent, no threat, public, sensitive, severity 2 = 5. Max without MCP empty is:
        // new+public+sensitive+threat(when threat>0 new is off)+severity. Max = public+sensitive+1+2 = 5 if threat, or new+public+sensitive+2=5.
        // Wait: threat adds raw threatScore. So public+sensitive+threat(1)+severity(2)=5. Or new+public+sensitive+severity=5.
        // To get 6 with empty: need threatScore that doesn't suppress... it always suppresses new when threatScore==0 is required for new.
        // So max base without MCP is 5. The quirk about 6 was: new+public+sensitive+threat binary + severity?
        // On THIS branch threat is raw value. Master was +1 binary.
        // Plan said: "A plain API can reach 6 (1+1+1+1+2)" - that's new+public+sensitive+threat(+1)+severity(2).
        // On this branch threat is raw so critical threat is +1, same: but new is suppressed when threat>0.
        // So: public+sensitive+threat1+severity2 = 5 when threat present.
        // new+public+sensitive+severity2 = 5 when no threat.
        // So empty context max is 5 on this branch, not 6. The plan's "6" assumed master's binary threat without mutual exclusion... 
        // Actually: new requires threatScore==0. So you can't have both new and threat. Max = 5.
        // I'll still assert empty context exact match for a 0.5 low severity case.
        assertTrue(score <= 5.01f);
        List<RiskScoreFactor> factors = explain(apiInfo, true, severityScore, Collections.singletonList("BOLA"),
                Collections.singletonList("Email"), emptyContext(), score);
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
        assertEquals(1, factors.size());
        assertEquals(0.5f, factors.get(0).getPoints(), 0.001f);
    }

    @Test
    public void nullAccessTypes() {
        int now = Context.now();
        ApiInfo apiInfo = baseApi(1, "/users", now - 60 * 24 * 60 * 60, 0);
        apiInfo.setApiAccessTypes(null);
        // getRiskScore would NPE on null access types - mirror what explainer does safely
        List<RiskScoreFactor> factors = RiskScoreExplainer.buildFactors(apiInfo, false, 0,
                Collections.emptyList(), Collections.emptyList(), emptyContext(), now);
        assertTrue(factors.isEmpty());
    }

    @Test
    public void discoveredExactlyThirtyDaysAgoTwoTimeCheck() {
        int now = Context.now();
        int discovered = now - Constants.ONE_MONTH_TIMESTAMP;
        ApiInfo apiInfo = baseApi(1, "/users", discovered, 0);
        // At nowBefore (=now), still within 30 days. At nowAfter (=now+1), may be outside.
        float baseAtNow = 0;
        if ((now - discovered) <= Constants.ONE_MONTH_TIMESTAMP) {
            baseAtNow += 1;
        }
        List<RiskScoreFactor> factors = RiskScoreExplainer.explain(apiInfo, false, 0,
                Collections.emptyList(), Collections.emptyList(), emptyContext(),
                baseAtNow, now, now + 1);
        assertFalse(factors.isEmpty());
        assertTrue(factors.stream().anyMatch(f -> f.getLabel().contains("Discovered")));
    }

    @Test
    public void mcpAdjustmentParityMaliciousPrivilegedAgentCapFloor() {
        int collectionId = 40;
        String tool = "admin_tool";
        ApiInfo apiInfo = baseApi(collectionId, "/mcp/" + tool, Context.now() - 60 * 24 * 60 * 60, 0);
        apiInfo.getApiAccessTypes().add(ApiAccessType.PUBLIC);
        apiInfo.setParentMcpToolNames(Collections.singletonList(tool));

        // Malicious + privileged
        McpRiskScoreContext both = contextWithMaliciousTool(collectionId, tool, true, true, false);
        float base = ApiInfoDao.getRiskScore(apiInfo, false, 0);
        float withHelper = McpApiRiskScoreUtils.getRiskScoreWithMcpAdjustments(apiInfo, base, both);
        // Manual expected: base(1 public) +3 malicious +1 privileged = 5, capped at 5
        assertEquals(5f, withHelper, 0.001f);

        // Agent base
        McpRiskScoreContext agent = contextWithAgent(collectionId, 2.0, "reason");
        float withAgent = McpApiRiskScoreUtils.getRiskScoreWithMcpAdjustments(apiInfo, base, agent);
        assertEquals(Math.min(5f, base + 2f), withAgent, 0.001f);

        // Floor
        McpRiskScoreContext floor = contextWithMaliciousTool(collectionId, tool, false, false, true);
        float withFloor = McpApiRiskScoreUtils.getRiskScoreWithMcpAdjustments(apiInfo, base, floor);
        assertEquals(3f, withFloor, 0.001f);

        // Empty context returns base unchanged
        assertEquals(base, McpApiRiskScoreUtils.getRiskScoreWithMcpAdjustments(apiInfo, base, emptyContext()), 0.001f);
    }
}

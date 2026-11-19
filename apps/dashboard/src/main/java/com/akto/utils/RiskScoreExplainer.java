package com.akto.utils;

import java.util.ArrayList;
import java.util.List;

import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiAccessType;
import com.akto.dto.ApiInfo.RiskScoreFactor;
import com.akto.util.Constants;
import com.akto.utils.McpApiRiskScoreUtils.McpAuditFlags;
import com.akto.utils.McpApiRiskScoreUtils.McpRiskScoreContext;

import static com.akto.jobs.utils.Utils.getRiskScoreValueFromSeverityScore;

/**
 * Describes a risk score that was already computed by {@link com.akto.dao.ApiInfoDao#getRiskScore}
 * and {@link McpApiRiskScoreUtils#getRiskScoreWithMcpAdjustments}. Never changes the stored score.
 */
public final class RiskScoreExplainer {

    private static final float MATCH_TOLERANCE = 0.05f;
    private static final int MAX_DETAIL_NAMES = 5;
    private static final int MAX_AGENT_REASON_DETAIL_CHARS = 200;
    static final String CAP_LABEL = "Capped at maximum risk score of 5";
    static final String FLOOR_LABEL = "Malicious MCP server collection (minimum risk)";
    static final String AGENT_RISK_LABEL = "AI agent risk";

    private RiskScoreExplainer() {}

    /**
     * Build reason lines for {@code computedScore}. Tries both clock readings around the score
     * computation for the 30-day discovery check. Returns {@code null} when the lines cannot
     * be matched to the score so writers can {@code $unset} factors instead of overwriting
     * good data. An empty list means the score is explained with no contributing lines.
     */
    public static List<RiskScoreFactor> explain(ApiInfo apiInfo, boolean isSensitive, float severityScore,
            List<String> issueNames, List<String> sensitiveSubtypes, McpRiskScoreContext ctx,
            float computedScore, int nowBefore, int nowAfter) {
        List<RiskScoreFactor> factors = buildFactors(apiInfo, isSensitive, severityScore, issueNames,
                sensitiveSubtypes, ctx, nowBefore);
        if (isAcceptable(factors, computedScore, ctx, apiInfo)) {
            return factors;
        }
        if (nowAfter != nowBefore) {
            factors = buildFactors(apiInfo, isSensitive, severityScore, issueNames, sensitiveSubtypes, ctx, nowAfter);
            if (isAcceptable(factors, computedScore, ctx, apiInfo)) {
                return factors;
            }
        }
        return null;
    }

    static List<RiskScoreFactor> buildFactors(ApiInfo apiInfo, boolean isSensitive, float severityScore,
            List<String> issueNames, List<String> sensitiveSubtypes, McpRiskScoreContext ctx, int now) {
        List<RiskScoreFactor> factors = new ArrayList<>();
        if (apiInfo == null) {
            return factors;
        }

        float threatScore = apiInfo.getThreatScore();
        if ((now - apiInfo.getDiscoveredTimestamp()) <= Constants.ONE_MONTH_TIMESTAMP && threatScore == 0) {
            factors.add(new RiskScoreFactor(1f, "Discovered in the last 30 days", null));
        }

        if (apiInfo.getApiAccessTypes() != null && apiInfo.getApiAccessTypes().contains(ApiAccessType.PUBLIC)) {
            factors.add(new RiskScoreFactor(1f, "Publicly accessible", null));
        }

        if (isSensitive) {
            factors.add(new RiskScoreFactor(1f, "Response contains sensitive data", joinNames(sensitiveSubtypes)));
        }

        if (threatScore > 0) {
            factors.add(new RiskScoreFactor(threatScore, threatLabel(threatScore), null));
        }

        float severityRiskPart = getRiskScoreValueFromSeverityScore(severityScore);
        if (severityRiskPart > 0) {
            factors.add(new RiskScoreFactor(severityRiskPart, "Open security findings", joinNames(issueNames)));
        }

        if (ctx != null && !ctx.isEmpty()) {
            McpAuditFlags flags = McpApiRiskScoreUtils.resolveMcpAuditFlags(apiInfo, ctx);
            if (flags.malicious) {
                factors.add(new RiskScoreFactor(McpApiRiskScoreUtils.getMcpComponentMaliciousDelta(),
                        "This MCP tool is marked malicious", null));
            }
            if (flags.privileged) {
                factors.add(new RiskScoreFactor(McpApiRiskScoreUtils.getMcpComponentPrivilegeDelta(),
                        "This MCP tool has privileged access", null));
            }
            int cid = apiInfo.getId().getApiCollectionId();
            Double agentBase = ctx.agentBaseRiskScoreByCollectionId.get(cid);
            if (agentBase != null && agentBase > 0) {
                String reason = ctx.agentBaseRiskScoreReasonByCollectionId.get(cid);
                factors.add(new RiskScoreFactor(agentBase.floatValue(), AGENT_RISK_LABEL,
                        truncateDetail(reason, MAX_AGENT_REASON_DETAIL_CHARS)));
            }

            // Same order as getRiskScoreWithMcpAdjustments: cap then floor.
            float sum = sumPoints(factors);
            float cap = McpApiRiskScoreUtils.getMcpRiskScoreCap();
            if (sum > cap) {
                factors.add(new RiskScoreFactor(cap - sum, CAP_LABEL, null));
                sum = cap;
            }
            float floor = McpApiRiskScoreUtils.getMcpMaliciousServerFloor();
            if (flags.maliciousServerTag && sum < floor) {
                factors.add(new RiskScoreFactor(floor - sum, FLOOR_LABEL, null));
            }
        }

        return factors;
    }

    static boolean isAcceptable(List<RiskScoreFactor> factors, float computedScore, McpRiskScoreContext ctx,
            ApiInfo apiInfo) {
        return Math.abs(sumPoints(factors) - computedScore) <= MATCH_TOLERANCE;
    }

    static float sumPoints(List<RiskScoreFactor> factors) {
        float sum = 0;
        if (factors == null) {
            return sum;
        }
        for (RiskScoreFactor f : factors) {
            if (f != null) {
                sum += f.getPoints();
            }
        }
        return sum;
    }

    private static String threatLabel(float threatScore) {
        if (Math.abs(threatScore - 1.0f) < 1e-4f) {
            return "Threat activity, critical severity";
        }
        if (Math.abs(threatScore - 0.75f) < 1e-4f) {
            return "Threat activity, high severity";
        }
        if (Math.abs(threatScore - 0.5f) < 1e-4f) {
            return "Threat activity, medium severity";
        }
        if (Math.abs(threatScore - 0.25f) < 1e-4f) {
            return "Threat activity, low severity";
        }
        return "Threat activity";
    }

    static String truncateDetail(String value, int maxChars) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars);
    }

    static String joinNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return null;
        }
        List<String> cleaned = new ArrayList<>();
        for (String n : names) {
            if (n != null && !n.isEmpty() && !cleaned.contains(n)) {
                cleaned.add(n);
            }
        }
        if (cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.size() <= MAX_DETAIL_NAMES) {
            return String.join(", ", cleaned);
        }
        int more = cleaned.size() - MAX_DETAIL_NAMES;
        return String.join(", ", cleaned.subList(0, MAX_DETAIL_NAMES)) + " (+" + more + " more)";
    }
}

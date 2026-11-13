package com.akto.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiAccessType;
import com.akto.dto.ApiInfo.RiskScoreFactor;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.akto.utils.McpApiRiskScoreUtils.McpAuditFlags;
import com.akto.utils.McpApiRiskScoreUtils.McpRiskScoreContext;

import static com.akto.jobs.utils.Utils.getRiskScoreValueFromSeverityScore;

/**
 * Describes a risk score that was already computed by {@link com.akto.dao.ApiInfoDao#getRiskScore}
 * and {@link McpApiRiskScoreUtils#getRiskScoreWithMcpAdjustments}. Never changes the stored score.
 */
public final class RiskScoreExplainer {

    private static final LoggerMaker loggerMaker = new LoggerMaker(RiskScoreExplainer.class, LogDb.DASHBOARD);
    private static final float MATCH_TOLERANCE = 0.05f;
    private static final int MAX_DETAIL_NAMES = 5;

    private RiskScoreExplainer() {}

    /**
     * Build reason lines for {@code computedScore}. Tries both clock readings around the score
     * computation for the 30-day discovery check. Returns an empty list when the lines cannot
     * be matched to the score (including after allowing for the existing cap of 5 and
     * malicious-server floor of 3). Never invents reconciling lines.
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
        String key = apiInfo != null && apiInfo.getId() != null ? apiInfo.getId().toString() : "unknown";
        loggerMaker.warnAndAddToDb(
                "Risk score explanation does not match computed score for " + key
                        + " computedScore=" + computedScore + " factorSum=" + sumPoints(factors),
                LogDb.DASHBOARD);
        return Collections.emptyList();
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
                String label = (reason != null && !reason.isEmpty()) ? reason : "AI agent risk";
                factors.add(new RiskScoreFactor(agentBase.floatValue(), label, null));
            }
        }

        return factors;
    }

    static boolean isAcceptable(List<RiskScoreFactor> factors, float computedScore, McpRiskScoreContext ctx,
            ApiInfo apiInfo) {
        float sum = sumPoints(factors);
        if (Math.abs(sum - computedScore) <= MATCH_TOLERANCE) {
            return true;
        }
        // Existing cap of 5: lines may add up to more than the stored score.
        if (Math.abs(computedScore - McpApiRiskScoreUtils.getMcpRiskScoreCap()) <= MATCH_TOLERANCE
                && sum > computedScore + MATCH_TOLERANCE) {
            return true;
        }
        // Existing malicious-server floor of 3: lines may add up to less than the stored score.
        boolean maliciousServer = false;
        if (ctx != null && apiInfo != null && apiInfo.getId() != null) {
            maliciousServer = ctx.maliciousMcpServerCollectionIds.contains(apiInfo.getId().getApiCollectionId());
        }
        if (maliciousServer
                && Math.abs(computedScore - McpApiRiskScoreUtils.getMcpMaliciousServerFloor()) <= MATCH_TOLERANCE
                && sum < computedScore - MATCH_TOLERANCE) {
            return true;
        }
        return false;
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

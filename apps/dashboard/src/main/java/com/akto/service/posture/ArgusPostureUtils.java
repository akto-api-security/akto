package com.akto.service.posture;

import com.akto.dto.ApiCollection;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.test_editor.Info;
import com.akto.dto.traffic.CollectionTags;
import com.akto.service.insights.InsightUtil;
import com.akto.util.AgenticObserveUtil;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.Severity;
import com.mongodb.client.model.Filters;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

// Agentic-posture helpers shared across ArgusPostureService, ArgusAgentPostureDrillService and
// ArgusPostureChangesService: agent identity/scope, environment bucketing, and the worst-category
// posture-score read.
public class ArgusPostureUtils {

    private ArgusPostureUtils() {}

    static final String UNKNOWN_AGENT = "Unknown agent";

    private static final int SEVERITY_CRITICAL_AT = 75;
    static final int SEVERITY_HIGH_AT = 50;
    private static final int SEVERITY_MEDIUM_AT = 25;

    // Display buckets/tag-value lists moved to InsightUtil (environmentBucket/envTagValue) so any
    // AGENTIC insight provider can group by environment too, without depending on this package.
    private static final String ENV_PRODUCTION  = InsightUtil.ENV_PRODUCTION;
    private static final String ENV_STAGING     = InsightUtil.ENV_STAGING;
    private static final String ENV_DEVELOPMENT = InsightUtil.ENV_DEVELOPMENT;

    static final String ENV_ID_ALL         = "all";
    static final String ENV_ID_PRODUCTION  = "production";
    static final String ENV_ID_STAGING     = "staging";
    static final String ENV_ID_DEVELOPMENT = "development";

    private static final List<String> DEV_ENVS     = Arrays.asList("DEV");
    private static final List<String> STAGING_ENVS = Arrays.asList("STAGING", "PREPROD", "UAT", "QA", "INTEG");

    // Mirrors UsersCollectionsList#getContextCollections(AGENTIC); must match AgenticPostureScoreCron's gate.
    static boolean isAgenticInScope(ApiCollection c) {
        return (c.isMcpCollection() || c.isGenAICollection()) && !c.isEndpointCollection();
    }

    static String agentDisplayName(ApiCollection c) {
        // Not extractServiceName(hostName): it mis-parses real DNS hosts ("mcp.kite.trade" -> "trade").
        String assetValue = AgenticObserveUtil.getAssetTagValue(c);
        if (assetValue != null && !assetValue.trim().isEmpty()) return AgenticObserveUtil.formatDisplayName(assetValue);
        if (c.getName() != null && !c.getName().trim().isEmpty()) return c.getName();
        return c.getHostName() != null ? c.getHostName() : UNKNOWN_AGENT;
    }

    // In-scope, active agents with a cron-computed score, highest score first.
    static List<ApiCollection> scoredAgents(List<ApiCollection> collections) {
        List<ApiCollection> scored = new ArrayList<>();
        for (ApiCollection c : collections) {
            if (c == null || c.isDeactivated() || c.getPostureScore() == null) continue;
            if (isAgenticInScope(c)) scored.add(c);
        }
        scored.sort(Comparator.comparingDouble(ApiCollection::getPostureScore).reversed());
        return scored;
    }

    static String testDisplayName(String type, Map<String, Info> testInfoByType) {
        Info info = testInfoByType.get(type);
        return info != null && info.getName() != null ? info.getName() : type;
    }

    static String severityForScore(long score) {
        if (score >= SEVERITY_CRITICAL_AT) return Severity.CRITICAL.name();
        if (score >= SEVERITY_HIGH_AT) return Severity.HIGH.name();
        if (score >= SEVERITY_MEDIUM_AT) return Severity.MEDIUM.name();
        return Severity.LOW.name();
    }

    // Category contributing the most weighted points, not the highest raw sub-score.
    static PostureScoreCategory worstCategory(Map<String, Object> subScores) {
        PostureScoreCategory worst = null;
        double worstPoints = 0;
        for (PostureScoreCategory category : PostureScoreCategory.values()) {
            double points = category.points(subScores);
            if (points > worstPoints) {
                worstPoints = points;
                worst = category;
            }
        }
        return worst;
    }

    // RED_TEAM's subScore is also the "never scanned" signal (AgenticPostureScoreCron.untestedRedTeamScore)
    // when there are no actual open findings — then its fixed "Has open red-teaming findings" text is
    // wrong. postureGaps.redTeam carries the real reason in that case, so it wins when present.
    static String worstIssue(ApiCollection agent) {
        PostureScoreCategory worst = worstCategory(agent.getPostureSubScores());
        if (worst == null) return "No significant issues detected";
        if (worst == PostureScoreCategory.RED_TEAM) {
            String gap = agent.getPostureGaps() == null ? null : agent.getPostureGaps().get(PostureScoreCategory.RED_TEAM.key);
            if (gap != null) return gap;
        }
        return worst.issue;
    }

    public static class GuardrailsCoverageBreakdown {
        public final List<ApiCollection> uncovered = new ArrayList<>();
        public final List<ApiCollection> alertOnly = new ArrayList<>();
        public final List<ApiCollection> enforcing = new ArrayList<>();
        public final Map<Integer, List<GuardrailPolicies>> coveringPolicies = new HashMap<>();

        public int covered() {
            return alertOnly.size() + enforcing.size();
        }
    }

    static GuardrailsCoverageBreakdown computeCoverage(List<ApiCollection> assets, List<GuardrailPolicies> policies) {
        GuardrailsCoverageBreakdown breakdown = new GuardrailsCoverageBreakdown();
        for (ApiCollection asset : assets) {
            List<GuardrailPolicies> covering = new ArrayList<>();
            boolean blocking = false;
            for (GuardrailPolicies p : policies) {
                if (p == null) continue;
                if (!InsightUtil.policyCoversHost(p, InsightUtil.assetIdentity(asset))) continue;
                covering.add(p);
                if (InsightUtil.isBlockingPolicy(p)) blocking = true;
            }
            if (covering.isEmpty()) {
                breakdown.uncovered.add(asset);
                continue;
            }
            breakdown.coveringPolicies.put(asset.getId(), covering);
            if (blocking) breakdown.enforcing.add(asset);
            else breakdown.alertOnly.add(asset);
        }
        return breakdown;
    }

    // Package-private (not private): TestArgusPostureUtils exercises this wrapper directly, and
    // ArgusAgentPostureDrillService calls InsightUtil.envTagValue directly instead — same package,
    // same convention this class's own paginate/worstSeverity-style helpers use.
    static String envTagValue(ApiCollection c) {
        return InsightUtil.envTagValue(c);
    }

    static List<ApiCollection> assetsIn(List<ApiCollection> assets, String environment) {
        if (isAllEnvironments(environment)) return assets;

        String bucket = bucketForId(environment);
        if (bucket == null) return assets;

        List<ApiCollection> out = new ArrayList<>();
        for (ApiCollection asset : assets) {
            if (bucket.equals(envBucket(envTagValue(asset)))) out.add(asset);
        }
        return out;
    }

    private static String bucketForId(String environment) {
        if (StringUtils.isBlank(environment)) return null;
        switch (environment.trim().toLowerCase(Locale.ROOT)) {
            case ENV_ID_PRODUCTION:
                return ENV_PRODUCTION;
            case ENV_ID_STAGING:
                return ENV_STAGING;
            case ENV_ID_DEVELOPMENT:
                return ENV_DEVELOPMENT;
            default:
                return null;
        }
    }

    public static String envBucket(String envTagValue) {
        return InsightUtil.environmentBucket(envTagValue);
    }

    static boolean isAllEnvironments(String environment) {
        return StringUtils.isBlank(environment) || ENV_ID_ALL.equalsIgnoreCase(environment.trim());
    }

    public static String environmentKey(String environment) {
        return isAllEnvironments(environment) ? ENV_ID_ALL : environment.trim().toLowerCase(Locale.ROOT);
    }

    public static Bson filterForEnvironment(String environment) {
        if (StringUtils.isBlank(environment)) return Filters.empty();
        switch (environment.trim().toLowerCase(Locale.ROOT)) {
            case ENV_ID_DEVELOPMENT:
                return envTagIn(DEV_ENVS);
            case ENV_ID_STAGING:
                return envTagIn(STAGING_ENVS);
            case ENV_ID_PRODUCTION:
                List<String> nonProd = new ArrayList<>(DEV_ENVS);
                nonProd.addAll(STAGING_ENVS);
                return Filters.nor(envTagIn(nonProd));
            default:
                return Filters.empty();
        }
    }

    private static Bson envTagIn(List<String> values) {
        List<Pattern> patterns = new ArrayList<>(values.size());
        for (String value : values) {
            patterns.add(Pattern.compile("^" + Pattern.quote(value) + "$", Pattern.CASE_INSENSITIVE));
        }
        return Filters.elemMatch(ApiCollection.TAGS_STRING,
                Filters.and(
                        Filters.eq(CollectionTags.KEY_NAME, Constants.AKTO_ENV_TYPE_TAG),
                        Filters.in(CollectionTags.VALUE, patterns)));
    }
}

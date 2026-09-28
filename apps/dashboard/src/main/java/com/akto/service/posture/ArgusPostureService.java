package com.akto.service.posture;

import com.akto.dao.ApiInfoDao;
import com.akto.dto.AgenticPostureScoreHistory;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.traffic.CollectionTags;
import com.akto.gpt.handlers.gpt_prompts.ToolCapabilityClassifier;
import com.akto.service.insights.InsightDataBundle;
import com.akto.service.insights.InsightResult;
import com.akto.service.insights.InsightRoutes;
import com.akto.service.insights.InsightUtil;
import com.akto.util.AgenticObserveUtil;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ArgusPostureService {

    private static final String KEY_KPIS = "kpis";
    private static final String KEY_ENVIRONMENTS = "environments";

    private static final String KPI_ASSETS              = "assets";
    private static final String KPI_HIGH_RISK_AGENTS    = "highRiskAgents";
    private static final String KPI_IDENTITY_ACCESS     = "identityAccess";
    private static final String KPI_PRIVILEGED_TOOLS    = "privilegedTools";
    private static final String KPI_SENSITIVE_DATA      = "sensitiveData";
    private static final String KPI_PROTECTION_COVERAGE = "protectionCoverage";

    private static final String ENV_PRODUCTION  = "Production";
    private static final String ENV_STAGING     = "Staging";
    private static final String ENV_DEVELOPMENT = "Development";

    private static final String ENV_ID_ALL         = "all";
    private static final String ENV_ID_PRODUCTION  = "production";
    private static final String ENV_ID_STAGING     = "staging";
    private static final String ENV_ID_DEVELOPMENT = "development";

    private static final List<String> DEV_ENVS     = Arrays.asList("DEV");
    private static final List<String> STAGING_ENVS = Arrays.asList("STAGING", "PREPROD", "UAT", "QA", "INTEG");

    private static final double TONE_SUCCESS_AT = 100d;
    private static final double TONE_WARNING_AT = 70d;

    public static final String DRILL_PROTECTION_COVERAGE = KPI_PROTECTION_COVERAGE;

    private static final String PROTECTION_NONE = "None";
    private static final String PROTECTION_ALERT_ONLY = "Alert only";
    private static final int DEFAULT_DRILL_LIMIT = 20;

    public BasicDBObject buildSummary(InsightDataBundle bundle, String environment) {
        List<ApiCollection> assets = new ArrayList<>();
        List<Integer> deactivatedIds = new ArrayList<>();
        for (ApiCollection c : bundle.collections) {
            if (c == null) continue;
            if (c.isDeactivated()) deactivatedIds.add(c.getId());
            else assets.add(c);
        }
        List<ApiCollection> scoped = assetsIn(assets, environment);

        List<BasicDBObject> kpis = new ArrayList<>();
        kpis.add(assetsKpi(scoped, environment));
        kpis.add(highRiskAgentsKpi(scoped));
        kpis.add(identityAccessKpi());
        kpis.add(privilegedToolsKpi(scoped, environment, deactivatedIds));
        kpis.add(sensitiveDataKpi(scoped, bundle.sensitiveByCollection));
        kpis.add(protectionCoverageKpi(scoped, bundle.policies));

        BasicDBObject response = new BasicDBObject();
        response.put(KEY_ENVIRONMENTS, environments(countByEnvironment(assets)));
        response.put(KEY_KPIS, kpis);
        return response;
    }

    private BasicDBObject assetsKpi(List<ApiCollection> assets, String environment) {
        BasicDBObject kpi = kpi(KPI_ASSETS, "Assets", (long) assets.size());

        if (isAllEnvironments(environment)) {
            long production = 0;
            for (ApiCollection asset : assets) {
                if (ENV_PRODUCTION.equals(envBucket(envTagValue(asset)))) production++;
            }
            kpi.put("footnote", countLine(production, "production", "None in production"));
        }

        long externallyExposed = 0;
        kpi.put("secondaryFootnote", countLine(externallyExposed, "externally exposed", "None externally exposed"));
        kpi.put("secondaryTone", riskTone(externallyExposed, "warning"));
        return kpi;
    }

    // Same scoping and threshold as buildHighestRiskAgents, so the tile and the table agree.
    private BasicDBObject highRiskAgentsKpi(List<ApiCollection> assets) {
        long highRisk = 0;
        for (ApiCollection c : assets) {
            if (c == null || !isAgenticInScope(c) || c.getPostureScore() == null) continue;
            if (Math.round(c.getPostureScore()) >= SEVERITY_HIGH_AT) highRisk++;
        }
        BasicDBObject kpi = kpi(KPI_HIGH_RISK_AGENTS, "High-Risk Agents", highRisk);
        // No per-agent history is stored, so a week-over-week delta isn't available yet.
        kpi.put("secondaryFootnote", "Week-over-week change not tracked yet");
        kpi.put("secondaryTone", "subdued");
        return kpi;
    }

    private BasicDBObject identityAccessKpi() {
        BasicDBObject kpi = kpi(KPI_IDENTITY_ACCESS, "Identity & Access", 0L);
        kpi.put("footnote", "overprivileged identity(s)");
        kpi.put("secondaryFootnote", sharedOrphanedLine(0, 0));
        kpi.put("secondaryTone", "subdued");
        return kpi;
    }

    private BasicDBObject privilegedToolsKpi(List<ApiCollection> assets, String environment,
                                             List<Integer> deactivatedIds) {
        long privileged = 0;
        long destructive = 0;

        if (!assets.isEmpty()) {
            Bson inScope = scopeFilter(assets, environment, deactivatedIds);

            privileged = ApiInfoDao.instance.count(Filters.and(inScope, privilegedToolFilter()));

            destructive = ApiInfoDao.instance.count(Filters.and(inScope,
                    Filters.in(ApiInfo.TOOL_INFO_CAPABILITY,
                            ToolCapabilityClassifier.RESOURCE_DELETE,
                            ToolCapabilityClassifier.CRITICAL_RESOURCE_WRITE)));
        }

        BasicDBObject kpi = kpi(KPI_PRIVILEGED_TOOLS, "Privileged Tools", privileged);
        kpi.put("footnote", "privileged");
        kpi.put("secondaryFootnote", countLine(destructive, "destructive", "None destructive"));
        kpi.put("secondaryTone", riskTone(destructive, "critical"));
        return kpi;
    }

    private static Bson privilegedToolFilter() {
        return Filters.and(
                Filters.exists(ApiInfo.TOOL_INFO_CAPABILITY),
                Filters.ne(ApiInfo.TOOL_INFO_CAPABILITY, ToolCapabilityClassifier.SAFE));
    }

    private static Bson scopeFilter(List<ApiCollection> assets, String environment,
                                    List<Integer> deactivatedIds) {
        if (isAllEnvironments(environment)) {
            if (deactivatedIds.isEmpty()) return Filters.empty();
            return Filters.nin(ApiInfo.ID_API_COLLECTION_ID, deactivatedIds);
        }

        List<Integer> ids = new ArrayList<>(assets.size());
        for (ApiCollection asset : assets) ids.add(asset.getId());
        return Filters.in(ApiInfo.ID_API_COLLECTION_ID, ids);
    }

    private BasicDBObject sensitiveDataKpi(List<ApiCollection> assets,
                                           Map<Integer, List<String>> sensitiveByCollection) {
        long withSensitive = 0;
        for (ApiCollection asset : assets) {
            List<String> types = sensitiveByCollection.get(asset.getId());
            if (types != null && !types.isEmpty()) withSensitive++;
        }

        BasicDBObject kpi = kpi(KPI_SENSITIVE_DATA, "Sensitive Data", withSensitive);
        kpi.put("footnote", "asset(s) access sensitive data");
        long canSendExternally = 0;
        kpi.put("secondaryFootnote", countLine(canSendExternally, "can send it externally", "None can send it externally"));
        kpi.put("secondaryTone", riskTone(canSendExternally, "critical"));
        return kpi;
    }

    private BasicDBObject protectionCoverageKpi(List<ApiCollection> assets, List<GuardrailPolicies> policies) {
        BasicDBObject kpi = kpi(KPI_PROTECTION_COVERAGE, "Protection Coverage", 0L);
        kpi.put("unit", "percent");
        kpi.put("tone", toneForPercent(0d));

        if (assets.isEmpty()) {
            kpi.put("value", 0d);
            kpi.put("secondaryFootnote", "No asset(s) discovered");
            kpi.put("secondaryTone", toneForPercent(0d));
            return kpi;
        }

        GuardrailsCoverageBreakdown breakdown = computeCoverage(assets, policies);
        long notCovered = breakdown.uncovered.size();
        double percent = percentOf(breakdown.covered(), assets.size());

        kpi.put("value", percent);
        kpi.put("tone", toneForPercent(percent));
        kpi.put("footnote", enforcingLine(breakdown.enforcing.size(), assets.size()));
        kpi.put("secondaryFootnote", countLine(notCovered, "asset(s) not covered", "All asset(s) covered"));
        kpi.put("secondaryTone", toneForPercent(percent));
        return kpi;
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
                if (!InsightUtil.policyCoversCollection(p, p.getApplyToDeviceIds(), asset)) continue;
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

    private static String enforcingLine(long enforcing, long total) {
        if (enforcing == 0) return "No asset(s) enforcing";
        return enforcing + " of " + total + " asset(s) enforcing";
    }

    public PostureDrillResult fetchProtectionCoverageDrill(InsightDataBundle bundle, String environment,
                                                           int skip, int limit) {
        int effectiveLimit = limit > 0 ? limit : DEFAULT_DRILL_LIMIT;
        int effectiveSkip = Math.max(skip, 0);

        List<ApiCollection> assets = new ArrayList<>();
        for (ApiCollection c : bundle.collections) {
            if (c == null || c.isDeactivated()) continue;
            assets.add(c);
        }
        List<ApiCollection> scoped = assetsIn(assets, environment);
        GuardrailsCoverageBreakdown breakdown = computeCoverage(scoped, bundle.policies);

        List<Integer> uncoveredIds = new ArrayList<>();
        for (ApiCollection c : breakdown.uncovered) uncoveredIds.add(c.getId());
        Map<Integer, Integer> toolCounts =
                ApiInfoDao.instance.getCountsByCollection(uncoveredIds, privilegedToolFilter());

        List<ApiCollection> attention = new ArrayList<>(breakdown.uncovered);
        attention.sort(Comparator.comparingInt(
                (ApiCollection c) -> toolCounts.getOrDefault(c.getId(), 0)).reversed());

        PostureDrillResult result = new PostureDrillResult();
        result.setTitle("Protection coverage");
        result.setBreadcrumb(Collections.singletonList(
                new PostureDrillResult.BreadcrumbItem("", "Protection coverage")));
        result.setColumns(protectionCoverageColumns());
        result.setDrillable(false);
        result.setTotal(attention.size());
        result.setSkip(effectiveSkip);
        result.setLimit(effectiveLimit);
        result.setSummary(protectionCoverageSummary(scoped.size(), breakdown));
        result.setEmptyMessage(scoped.isEmpty()
                ? "No asset(s) discovered in this environment."
                : "All " + scoped.size() + " asset(s) are covered by a policy.");
        result.setCtas(Arrays.asList(
                new InsightResult.Cta("create_policy", "Create guardrail policy", "NAVIGATE",
                        InsightRoutes.GUARDRAIL_POLICIES, new HashMap<>(), true),
                new InsightResult.Cta("view_violations", "View violations", "NAVIGATE",
                        InsightRoutes.GUARDRAIL_VIOLATIONS, new HashMap<>(), false)));

        int from = Math.min(effectiveSkip, attention.size());
        int to = Math.min(from + effectiveLimit, attention.size());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ApiCollection asset : attention.subList(from, to)) {
            rows.add(protectionCoverageRow(asset, bundle, breakdown, toolCounts));
        }
        result.setRows(rows);
        return result;
    }

    private static List<PostureDrillResult.ColumnDef> protectionCoverageColumns() {
        return Arrays.asList(
                new PostureDrillResult.ColumnDef("asset", "Unprotected asset"),
                new PostureDrillResult.ColumnDef("type", "Type"),
                new PostureDrillResult.ColumnDef("environment", "Environment"),
                new PostureDrillResult.ColumnDef("protection", "Protection"),
                new PostureDrillResult.ColumnDef("sensitiveData", "Sensitive data"),
                new PostureDrillResult.ColumnDef("privilegedTools", "Privileged tools"));
    }

    private static List<InsightResult.Metric> protectionCoverageSummary(int inScope,
                                                                        GuardrailsCoverageBreakdown breakdown) {
        double percent = percentOf(breakdown.covered(), inScope);
        return Arrays.asList(
                new InsightResult.Metric("coverage", "Coverage", percent, "percent", formatPercent(percent)),
                new InsightResult.Metric("alertMode", "Alert mode", breakdown.alertOnly.size(), "count",
                        InsightUtil.grouped(breakdown.alertOnly.size())),
                new InsightResult.Metric("enforcing", "Enforcing", breakdown.enforcing.size(), "count",
                        InsightUtil.grouped(breakdown.enforcing.size())),
                new InsightResult.Metric("notCovered", "Not covered", breakdown.uncovered.size(), inScope, "count",
                        InsightUtil.grouped(breakdown.uncovered.size()), null));
    }

    private static Map<String, Object> protectionCoverageRow(ApiCollection asset, InsightDataBundle bundle,
                                                             GuardrailsCoverageBreakdown breakdown,
                                                             Map<Integer, Integer> toolCounts) {
        List<GuardrailPolicies> covering = breakdown.coveringPolicies.get(asset.getId());
        List<String> policyNames = new ArrayList<>();
        if (covering != null) {
            for (GuardrailPolicies p : covering) {
                if (p.getName() != null) policyNames.add(p.getName());
            }
        }
        List<String> sensitiveTypes = bundle.sensitiveByCollection.get(asset.getId());

        Map<String, Object> row = new HashMap<>();
        row.put("asset", asset.getHostName());
        row.put("type", AgenticObserveUtil.getTypeFromCollection(asset));
        row.put("environment", envBucket(envTagValue(asset)));
        row.put("protection", policyNames.isEmpty()
                ? PROTECTION_NONE
                : PROTECTION_ALERT_ONLY + " (" + String.join(", ", policyNames) + ")");
        row.put("sensitiveData", sensitiveTypes == null || sensitiveTypes.isEmpty()
                ? "None detected" : String.join(", ", sensitiveTypes));
        row.put("privilegedTools", toolCounts.getOrDefault(asset.getId(), 0));
        return row;
    }

    private static String countLine(long count, String whenSome, String whenNone) {
        return count > 0 ? count + " " + whenSome : whenNone;
    }

    private static String sharedOrphanedLine(long shared, long orphaned) {
        if (shared == 0 && orphaned == 0) return "No shared or orphaned identity(s)";
        if (orphaned == 0) return shared + " shared";
        if (shared == 0) return orphaned + " orphaned";
        return shared + " shared · " + orphaned + " orphaned";
    }

    private static String envTagValue(ApiCollection c) {
        if (c == null || c.getEnvType() == null) return null;
        for (CollectionTags tag : c.getEnvType()) {
            if (tag != null && Constants.AKTO_ENV_TYPE_TAG.equalsIgnoreCase(tag.getKeyName())) return tag.getValue();
        }
        return null;
    }

    private static List<ApiCollection> assetsIn(List<ApiCollection> assets, String environment) {
        if (isAllEnvironments(environment)) return assets;

        String bucket = bucketForId(environment);
        if (bucket == null) return assets;

        List<ApiCollection> out = new ArrayList<>();
        for (ApiCollection asset : assets) {
            if (bucket.equals(envBucket(envTagValue(asset)))) out.add(asset);
        }
        return out;
    }

    private static Map<String, Integer> countByEnvironment(List<ApiCollection> assets) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ApiCollection asset : assets) {
            String bucket = envBucket(envTagValue(asset));
            counts.put(bucket, counts.getOrDefault(bucket, 0) + 1);
        }
        return counts;
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
        if (StringUtils.isBlank(envTagValue)) return ENV_PRODUCTION;
        String value = envTagValue.trim().toUpperCase(Locale.ROOT);
        if (DEV_ENVS.contains(value)) return ENV_DEVELOPMENT;
        if (STAGING_ENVS.contains(value)) return ENV_STAGING;
        return ENV_PRODUCTION;
    }

    private static boolean isAllEnvironments(String environment) {
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

    private static BasicDBObject kpi(String id, String label, Long value) {
        BasicDBObject kpi = new BasicDBObject();
        kpi.put("id", id);
        kpi.put("label", label);
        kpi.put("value", value);
        return kpi;
    }

    private static List<BasicDBObject> environments(Map<String, Integer> counts) {
        List<BasicDBObject> out = new ArrayList<>();
        out.add(environment(ENV_ID_ALL, "All environments", null));
        out.add(environment(ENV_ID_PRODUCTION, ENV_PRODUCTION, counts.getOrDefault(ENV_PRODUCTION, 0)));
        out.add(environment(ENV_ID_STAGING, ENV_STAGING, counts.getOrDefault(ENV_STAGING, 0)));
        out.add(environment(ENV_ID_DEVELOPMENT, ENV_DEVELOPMENT, counts.getOrDefault(ENV_DEVELOPMENT, 0)));
        return out;
    }

    private static BasicDBObject environment(String id, String label, Integer count) {
        BasicDBObject env = new BasicDBObject();
        env.put("id", id);
        env.put("label", label);
        env.put("count", count);
        return env;
    }

    private static String formatPercent(double percent) {
        if (percent == Math.floor(percent)) return (long) percent + "%";
        return percent + "%";
    }

    private static double percentOf(long part, long total) {
        if (total <= 0) return 0d;
        return Math.floor((part * 1000d) / total) / 10d;
    }

    private static String riskTone(long count, String toneWhenPresent) {
        return count > 0 ? toneWhenPresent : "subdued";
    }

    private static String toneForPercent(double percent) {
        if (percent >= TONE_SUCCESS_AT) return "success";
        if (percent >= TONE_WARNING_AT) return "warning";
        return "critical";
    }

    // Pure read of cron-written history rows; nothing is recomputed here.
    public BasicDBObject buildPostureScore(AgenticPostureScoreHistory latest, List<AgenticPostureScoreHistory> trendHistory,
                                            AgenticPostureScoreHistory weekAgoHistory) {
        BasicDBObject postureScore = new BasicDBObject();
        postureScore.put("value", latest != null ? Math.round(latest.getValue()) : null);
        postureScore.put("agentsScored", latest != null ? latest.getAgentsScored() : 0);
        postureScore.put("agentsWithNoSignal", latest != null ? latest.getAgentsWithNoSignal() : 0);
        postureScore.put("dataGaps", postureScoreGaps(latest));

        List<Double> trend = safe(trendHistory).stream().filter(Objects::nonNull)
                .map(AgenticPostureScoreHistory::getValue).collect(Collectors.toList());
        if (!trend.isEmpty()) postureScore.put("trend", trend);

        if (latest != null && weekAgoHistory != null) {
            long current = Math.round(latest.getValue());
            long prior = Math.round(weekAgoHistory.getValue());
            postureScore.put("delta", current - prior);
            postureScore.put("deltaTone", current > prior ? "critical" : current < prior ? "success" : "neutral"); // higher is worse
        }

        return postureScore;
    }

    // NOT_COMPUTED_YET / NO_ROWS / PARTIAL_COVERAGE depending on how much of the account is scored.
    private static List<Map<String, Object>> postureScoreGaps(AgenticPostureScoreHistory latest) {
        List<Map<String, Object>> gaps = new ArrayList<>();
        if (latest == null) {
            gaps.add(gapRow("AGENTIC_ASSETS", "NOT_COMPUTED_YET", "The posture score hasn't been computed for this account yet — check back shortly."));
        } else if (latest.getAgentsScored() == 0) {
            gaps.add(gapRow("AGENTIC_ASSETS", "NO_ROWS", "No AI agents have been discovered yet, so the posture score can't be computed."));
        } else if (latest.getAgentsWithNoSignal() > 0) {
            gaps.add(gapRow("AGENTIC_ASSETS", "PARTIAL_COVERAGE",
                    latest.getAgentsWithNoSignal() + " of " + latest.getAgentsScored() + " agents haven't been scored yet and are excluded from this average."));
        }
        return gaps;
    }

    private static <T> List<T> safe(List<T> list) {
        return list == null ? new ArrayList<>() : list;
    }

    private static final int HIGHEST_RISK_AGENTS_LIMIT = 5;

    private static final Map<String, Integer> SUB_SCORE_WEIGHTS = new HashMap<>();
    private static final Map<String, String> SUB_SCORE_ISSUE_LABELS = new HashMap<>();
    static {
        SUB_SCORE_WEIGHTS.put("redTeam", 30);
        SUB_SCORE_ISSUE_LABELS.put("redTeam", "Has open red-teaming findings");
        SUB_SCORE_WEIGHTS.put("guardrailMalicious", 30);
        SUB_SCORE_ISSUE_LABELS.put("guardrailMalicious", "Has guardrail-caught or malicious activity");
        SUB_SCORE_WEIGHTS.put("coverage", 10);
        SUB_SCORE_ISSUE_LABELS.put("coverage", "Not covered by a guardrail policy or red-team scan");
        SUB_SCORE_WEIGHTS.put("sensitiveData", 10);
        SUB_SCORE_ISSUE_LABELS.put("sensitiveData", "Accesses sensitive data");
        SUB_SCORE_WEIGHTS.put("accessAuth", 10);
        SUB_SCORE_ISSUE_LABELS.put("accessAuth", "Publicly accessible or unauthenticated");
        SUB_SCORE_WEIGHTS.put("overprivilegedTools", 10);
        SUB_SCORE_ISSUE_LABELS.put("overprivilegedTools", "Has privileged tool access");
    }

    public List<BasicDBObject> buildHighestRiskAgents(InsightDataBundle bundle) {
        List<ApiCollection> scored = new ArrayList<>();
        for (ApiCollection c : bundle.collections) {
            if (c == null || c.isDeactivated() || c.getPostureScore() == null) continue;
            if (isAgenticInScope(c)) scored.add(c);
        }
        scored.sort(Comparator.comparingDouble(ApiCollection::getPostureScore).reversed());

        List<BasicDBObject> rows = new ArrayList<>();
        int rank = 1;
        for (ApiCollection c : scored) {
            if (rank > HIGHEST_RISK_AGENTS_LIMIT) break;
            long score = Math.round(c.getPostureScore());

            BasicDBObject row = new BasicDBObject();
            row.put("rank", rank++);
            row.put("groupKey", String.valueOf(c.getId()));
            row.put("name", agentDisplayName(c));
            row.put("environment", envBucket(envTagValue(c)));
            row.put("score", score);
            row.put("issue", worstIssue(c.getPostureSubScores()));
            row.put("severity", severityForScore(score));
            rows.add(row);
        }
        return rows;
    }

    // Mirrors UsersCollectionsList#getContextCollections(AGENTIC); must match AgenticPostureScoreCron's gate.
    private static boolean isAgenticInScope(ApiCollection c) {
        return (c.isMcpCollection() || c.isGenAICollection()) && !c.isEndpointCollection();
    }

    private static String agentDisplayName(ApiCollection c) {
        // Not extractServiceName(hostName): it mis-parses real DNS hosts ("mcp.kite.trade" -> "trade").
        String assetValue = AgenticObserveUtil.getAssetTagValue(c);
        if (assetValue != null && !assetValue.trim().isEmpty()) return AgenticObserveUtil.formatDisplayName(assetValue);
        if (c.getName() != null && !c.getName().trim().isEmpty()) return c.getName();
        return c.getHostName() != null ? c.getHostName() : "Unknown agent";
    }

    // Category contributing the most weighted points (subScore/100 * weight), not the highest raw sub-score.
    private static String worstIssue(Map<String, Object> subScores) {
        if (subScores != null) {
            String worstKey = null;
            double worstEarned = 0;
            for (Map.Entry<String, Object> e : subScores.entrySet()) {
                Integer weight = SUB_SCORE_WEIGHTS.get(e.getKey());
                if (weight == null || !(e.getValue() instanceof Number)) continue;
                double earned = weight * (((Number) e.getValue()).doubleValue() / 100.0);
                if (earned > worstEarned) {
                    worstEarned = earned;
                    worstKey = e.getKey();
                }
            }
            if (worstKey != null) return SUB_SCORE_ISSUE_LABELS.get(worstKey);
        }
        return "No significant issues detected";
    }

    private static final int SEVERITY_CRITICAL_AT = 75;
    private static final int SEVERITY_HIGH_AT = 10;
    private static final int SEVERITY_MEDIUM_AT = 5;

    private static String severityForScore(long score) {
        if (score >= SEVERITY_CRITICAL_AT) return "CRITICAL";
        if (score >= SEVERITY_HIGH_AT) return "HIGH";
        if (score >= SEVERITY_MEDIUM_AT) return "MEDIUM";
        return "LOW";
    }

    // Same shape as InsightResult.Gap so the frontend's GapHint renders it.
    private static Map<String, Object> gapRow(String source, String reason, String impact) {
        Map<String, Object> row = new HashMap<>();
        row.put("source", source);
        row.put("reason", reason);
        row.put("impact", impact);
        return row;
    }
}

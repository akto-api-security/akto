package com.akto.service.posture;

import com.akto.dao.ApiInfoDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.traffic.CollectionTags;
import com.akto.gpt.handlers.gpt_prompts.ToolCapabilityClassifier;
import com.akto.service.insights.InsightDataBundle;
import com.akto.service.insights.InsightUtil;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

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

    private static final double TONE_SUCCESS_AT = 95d;
    private static final double TONE_WARNING_AT = 60d;

    private static final String CONTROL_RATE_LIMIT        = "rateLimit";
    private static final String CONTROL_PROMPT_INJECTION  = "promptInjectionFiltering";
    private static final String CONTROL_PII               = "piiDetection";
    private static final String CONTROL_OUTPUT_VALIDATION = "outputValidation";

    private static final List<String> DEFAULT_REQUIRED_CONTROLS = Arrays.asList(
            CONTROL_RATE_LIMIT, CONTROL_PROMPT_INJECTION, CONTROL_PII, CONTROL_OUTPUT_VALIDATION);

    private static List<String> requiredControls(ApiCollection asset) {
        return DEFAULT_REQUIRED_CONTROLS;
    }

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
        kpis.add(highRiskAgentsKpi());
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

    private BasicDBObject highRiskAgentsKpi() {
        BasicDBObject kpi = kpi(KPI_HIGH_RISK_AGENTS, "High-Risk Agents", 0L);
        long newlyHighRisk = 0;
        kpi.put("secondaryFootnote", countLine(newlyHighRisk, "newly high risk this week", "No change since last week"));
        kpi.put("secondaryTone", riskTone(newlyHighRisk, "critical"));
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

            privileged = ApiInfoDao.instance.count(Filters.and(inScope,
                    Filters.exists(ApiInfo.TOOL_INFO_CAPABILITY),
                    Filters.ne(ApiInfo.TOOL_INFO_CAPABILITY, ToolCapabilityClassifier.SAFE)));

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
            kpi.put("secondaryTone", riskTone(0, "critical"));
            return kpi;
        }

        if (hasFleetWidePolicy(policies)) {
            kpi.put("value", 100d);
            kpi.put("tone", toneForPercent(100d));
            kpi.put("secondaryFootnote", "All asset(s) covered");
            kpi.put("secondaryTone", riskTone(0, "critical"));
            return kpi;
        }

        long covered = 0;
        for (ApiCollection asset : assets) {
            if (isCovered(asset, policies)) covered++;
        }

        long notCovered = assets.size() - covered;
        double percent = percentOf(covered, assets.size());

        kpi.put("value", percent);
        kpi.put("tone", toneForPercent(percent));
        kpi.put("secondaryFootnote", countLine(notCovered, "asset(s) not covered", "All asset(s) covered"));
        kpi.put("secondaryTone", riskTone(notCovered, "critical"));
        return kpi;
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

    private static boolean hasFleetWidePolicy(List<GuardrailPolicies> policies) {
        for (GuardrailPolicies p : policies) {
            if (p != null && p.isApplyToAllServers()) return true;
        }
        return false;
    }

    private static boolean isCovered(ApiCollection asset, List<GuardrailPolicies> policies) {
        for (GuardrailPolicies p : policies) {
            if (p == null) continue;
            if (InsightUtil.policyCoversCollection(p, p.getApplyToDeviceIds(), asset)) return true;
        }
        return false;
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
}

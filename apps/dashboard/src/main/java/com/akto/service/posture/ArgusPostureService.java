package com.akto.service.posture;

import com.akto.dto.ApiCollection;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.traffic.CollectionTags;
import com.akto.service.insights.InsightDataBundle;
import com.akto.service.insights.InsightUtil;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
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
        for (ApiCollection c : bundle.collections) {
            if (c != null && !c.isDeactivated()) assets.add(c);
        }
        List<ApiCollection> scoped = assetsIn(assets, environment);

        List<BasicDBObject> kpis = new ArrayList<>();
        kpis.add(assetsKpi(scoped, environment));
        kpis.add(highRiskAgentsKpi());
        kpis.add(identityAccessKpi());
        kpis.add(privilegedToolsKpi());
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
            kpi.put("footnote", production + " production");
        }

        long externallyExposed = 0;
        kpi.put("secondaryFootnote", externallyExposed + " externally exposed");
        kpi.put("secondaryTone", riskTone(externallyExposed, "warning"));
        return kpi;
    }

    private BasicDBObject highRiskAgentsKpi() {
        BasicDBObject kpi = kpi(KPI_HIGH_RISK_AGENTS, "High-Risk Agents", 0L);
        long newlyHighRisk = 0;
        kpi.put("secondaryFootnote", newlyHighRisk + " newly high risk this week");
        kpi.put("secondaryTone", riskTone(newlyHighRisk, "critical"));
        return kpi;
    }

    private BasicDBObject identityAccessKpi() {
        BasicDBObject kpi = kpi(KPI_IDENTITY_ACCESS, "Identity & Access", 0L);
        kpi.put("footnote", "overprivileged identity(s)");
        kpi.put("secondaryFootnote", "0 shared · 0 orphaned");
        kpi.put("secondaryTone", "subdued");
        return kpi;
    }

    private BasicDBObject privilegedToolsKpi() {
        BasicDBObject kpi = kpi(KPI_PRIVILEGED_TOOLS, "Privileged Tools", 0L);
        kpi.put("footnote", "privileged");
        long destructiveNoApproval = 0;
        kpi.put("secondaryFootnote", destructiveNoApproval + " destructive, no approval");
        kpi.put("secondaryTone", riskTone(destructiveNoApproval, "critical"));
        return kpi;
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
        kpi.put("secondaryFootnote", canSendExternally + " can send it externally");
        kpi.put("secondaryTone", riskTone(canSendExternally, "critical"));
        return kpi;
    }

    private BasicDBObject protectionCoverageKpi(List<ApiCollection> assets, List<GuardrailPolicies> policies) {
        BasicDBObject kpi = kpi(KPI_PROTECTION_COVERAGE, "Protection Coverage", 0L);
        kpi.put("unit", "percent");
        kpi.put("tone", toneForPercent(0d));

        if (assets.isEmpty()) {
            kpi.put("value", 0d);
            kpi.put("secondaryFootnote", "0 asset(s) missing required controls");
            kpi.put("secondaryTone", riskTone(0, "critical"));
            return kpi;
        }

        List<Set<String>> providedByPolicy = new ArrayList<>(policies.size());
        for (GuardrailPolicies p : policies) providedByPolicy.add(providedControls(p));

        long fullyProtected = 0;
        for (ApiCollection asset : assets) {
            if (missingControls(asset, requiredControls(asset), policies, providedByPolicy).isEmpty()) fullyProtected++;
        }

        long missing = assets.size() - fullyProtected;
        double percent = percentOf(fullyProtected, assets.size());

        kpi.put("value", percent);
        kpi.put("tone", toneForPercent(percent));
        kpi.put("secondaryFootnote", missing + " asset(s) missing required controls");
        kpi.put("secondaryTone", riskTone(missing, toneForPercent(percent)));
        return kpi;
    }

    private static Set<String> providedControls(GuardrailPolicies p) {
        Set<String> provided = new HashSet<>();
        for (String control : DEFAULT_REQUIRED_CONTROLS) {
            if (policyProvides(p, control)) provided.add(control);
        }
        return provided;
    }

    private static List<String> missingControls(ApiCollection asset, List<String> required,
                                                List<GuardrailPolicies> policies, List<Set<String>> providedByPolicy) {
        Set<String> have = new HashSet<>();
        for (int i = 0; i < policies.size(); i++) {
            GuardrailPolicies p = policies.get(i);
            if (!InsightUtil.policyCoversCollection(p, p.getApplyToDeviceIds(), asset)) continue;
            have.addAll(providedByPolicy.get(i));
            if (have.containsAll(required)) return new ArrayList<>();
        }

        List<String> missing = new ArrayList<>();
        for (String control : required) {
            if (!have.contains(control)) missing.add(control);
        }
        return missing;
    }

    private static boolean policyProvides(GuardrailPolicies p, String control) {
        if (p == null || control == null) return false;

        switch (control) {
            case CONTROL_RATE_LIMIT: {
                GuardrailPolicies.AnomalyDetection anomaly = p.getAnomalyDetection();
                if (anomaly != null && anomaly.isEnabled()
                        && (anomaly.getToolCallLimit() > 0 || anomaly.getErrorLimit() > 0)) {
                    return true;
                }
                GuardrailPolicies.TokenLimitDetection tokens = p.getTokenLimitDetection();
                return tokens != null && tokens.isEnabled() && tokens.getThreshold() > 0;
            }

            case CONTROL_PROMPT_INJECTION: {
                Map<String, Object> filtering = p.getContentFiltering();
                return filtering != null && filtering.get("promptAttacks") != null;
            }

            case CONTROL_PII:
                return notEmpty(p.getPiiTypes());

            case CONTROL_OUTPUT_VALIDATION:
                return p.isApplyOnResponse();
            default:
                return false;
        }
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
        if (isBlank(environment)) return null;
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
        if (isBlank(envTagValue)) return ENV_PRODUCTION;
        String value = envTagValue.trim().toUpperCase(Locale.ROOT);
        if (DEV_ENVS.contains(value)) return ENV_DEVELOPMENT;
        if (STAGING_ENVS.contains(value)) return ENV_STAGING;
        return ENV_PRODUCTION;
    }

    private static boolean isAllEnvironments(String environment) {
        return isBlank(environment) || ENV_ID_ALL.equalsIgnoreCase(environment.trim());
    }

    public static Bson filterForEnvironment(String environment) {
        if (isBlank(environment)) return Filters.empty();
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
        return Math.round((part * 1000d) / total) / 10d;
    }

    private static String riskTone(long count, String toneWhenPresent) {
        return count > 0 ? toneWhenPresent : "subdued";
    }

    private static String toneForPercent(double percent) {
        if (percent >= TONE_SUCCESS_AT) return "success";
        if (percent >= TONE_WARNING_AT) return "warning";
        return "critical";
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static boolean notEmpty(List<?> list) {
        return list != null && !list.isEmpty();
    }
}

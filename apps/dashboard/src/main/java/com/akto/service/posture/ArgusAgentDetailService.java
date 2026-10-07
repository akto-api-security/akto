package com.akto.service.posture;

import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.action.threat_detection.OwnAgentThreatStats;
import com.akto.action.threat_detection.ThreatCategoryCount;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.test_editor.Info;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.log.LoggerMaker;
import com.akto.mcp.McpSchema;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.service.insights.InsightContext;
import com.akto.service.insights.InsightDataBundle;
import com.akto.service.insights.InsightResult;
import com.akto.service.insights.InsightRoutes;
import com.akto.service.insights.InsightService;
import com.akto.service.insights.InsightUtil;
import com.akto.service.insights.InsightsThreatBackendAccess;
import com.akto.gpt.handlers.gpt_prompts.ToolCapabilityClassifier;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.TestRunIssueStatus;
import com.akto.utils.crons.ToolClassificationCron;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ArgusAgentDetailService {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ArgusAgentDetailService.class, LogDb.DASHBOARD);

    private static final int GUARDRAIL_WINDOW_SECONDS = 90 * 86400;
    private static final int EVENT_FETCH_LIMIT = 10000;
    private static final int RED_TEAM_FINDINGS_CAP = 5;
    // The gateway writes exactly "PII-"; the other two mirror guardrailRuleDefinitions.js, which
    // already recognises them, so an event written by an older or different producer still counts.
    private static final List<String> PII_PREFIXES = Arrays.asList("PII-", "PII_", "PII");

    private final InsightsThreatBackendAccess threatAccess = new InsightsThreatBackendAccess();
    private final InsightService insightService = new InsightService();

    public AgentDetailResult fetchAgentDetail(int collectionId, String finding) {
        ApiCollection agent = ApiCollectionsDao.instance.findOne(Filters.eq(Constants.ID, collectionId));
        if (agent == null || agent.isDeactivated() || !ArgusPostureService.isAgenticInScope(agent)) return null;

        AgentDetailResult result = new AgentDetailResult();
        result.setHeader(buildHeader(agent));
        result.setTools(buildTools(agent));
        result.setData(buildSensitiveData(agent));
        result.setProtection(buildProtection(agent));
        result.setRedTeam(buildRedTeam(agent));
        result.setOpenedFromFinding(resolveFinding(collectionId, finding));
        return result;
    }

    private AgentDetailResult.Header buildHeader(ApiCollection agent) {
        AgentDetailResult.Header header = new AgentDetailResult.Header();
        header.setCollectionId(agent.getId());
        header.setName(ArgusPostureService.agentDisplayName(agent));
        header.setDescription(agent.getDescription());
        long score = agent.getPostureScore() == null ? 0 : Math.round(agent.getPostureScore());
        header.setRiskScore(score);
        header.setSeverity(ArgusPostureService.severityForScore(score));
        header.setEnvironment(InsightUtil.environmentBucket(InsightUtil.envTagValue(agent)));
        header.setCreatedAt(agent.getStartTs() == 0 ? null : agent.getStartTs());
        header.setLastActive(lastActive(agent.getId()));
        return header;
    }

    private Integer lastActive(int collectionId) {
        ApiInfo latest = ApiInfoDao.instance.findOne(
                Filters.eq(ApiInfo.ID_API_COLLECTION_ID, collectionId),
                Sorts.descending(ApiInfo.LAST_SEEN));
        return latest == null ? null : latest.getLastSeen();
    }

    /**
     * Only the agent's tool endpoints. ToolClassificationCron.TOOL_URL is what defines one, and
     * McpSchema.METHOD_TOOLS_LIST is the discovery call rather than a tool. Every other endpoint the agent serves —
     * a model invoke, for instance — is not a tool and does not belong in this section.
     */
    private List<AgentDetailResult.Tool> buildTools(ApiCollection agent) {
        List<AgentDetailResult.Tool> tools = new ArrayList<>();
        Bson toolFilter = Filters.and(
                Filters.eq(ApiInfo.ID_API_COLLECTION_ID, agent.getId()),
                Filters.regex(ApiInfo.ID_URL, ToolClassificationCron.TOOL_URL),
                Filters.not(Filters.regex(ApiInfo.ID_URL, McpSchema.METHOD_TOOLS_LIST)));
        for (ApiInfo api : ApiInfoDao.instance.findAll(toolFilter)) {
            if (api.getId() == null) continue;
            AgentDetailResult.Tool tool = new AgentDetailResult.Tool();
            String method = api.getId().getMethod() == null ? null : api.getId().getMethod().name();
            tool.setMethod(method);
            tool.setUrl(api.getId().getUrl());
            tool.setName(api.getId().getUrl() == null ? null : ToolClassificationCron.toolNameFromUrl(api.getId().getUrl()));
            String capability = api.getToolInfo() == null ? null : api.getToolInfo().getCapability();
            tool.setCapability(capability);
            boolean privileged = capability != null && !capability.trim().isEmpty()
                    && !"SAFE".equalsIgnoreCase(capability.trim());
            tool.setPrivileged(privileged);
            tool.setCapabilityLabel(privileged ? InsightUtil.humanizeToolCapability(capability) : null);
            tool.setDetail(detailFor(capability, privileged));
            tool.setLastSeen(api.getLastSeen());
            tools.add(tool);
        }
        return tools;
    }

    /**
     * The line under the tool name. "privileged" and "destructive" are the same two classes
     * ArgusPostureService's privilegedToolFilter and destructiveToolFilter already define; the
     * design's trailing clause is a tool description, which nothing records.
     */
    private static String detailFor(String capability, boolean privileged) {
        if (!privileged) return null;
        boolean destructive = ToolCapabilityClassifier.RESOURCE_DELETE.equalsIgnoreCase(capability)
                || ToolCapabilityClassifier.CRITICAL_RESOURCE_WRITE.equalsIgnoreCase(capability);
        return destructive ? "privileged \u00b7 destructive" : "privileged";
    }

    /**
     * An event names the policy that fired on `category` and the rule within it on
     * metadata's ruleViolated, which the gateway mirrors onto subCategory. PII rules are written
     * as PII-&lt;type&gt;, so the prefix is what separates a sensitive-data detection from a
     * denied-topic or regex one; a policy carrying PII types alongside other rules must not have
     * them attributed on the strength of the policy alone.
     */
    private AgentDetailResult.SensitiveData buildSensitiveData(ApiCollection agent) {
        AgentDetailResult.SensitiveData data = new AgentDetailResult.SensitiveData();
        String hostKey = ArgusPostureService.assetIdentity(agent);
        if (StringUtils.isBlank(hostKey)) return data;

        List<DashboardMaliciousEvent> events = fetchAgentEvents(hostKey);
        if (events == null) {
            data.setAvailable(false);
            return data;
        }

        List<ThreatCategoryCount> counts = new OwnAgentThreatStats(events).subCategoryCounts(new HashMap<>());
        Set<String> types = new LinkedHashSet<>();
        long detections = 0;
        for (ThreatCategoryCount count : counts) {
            String type = piiTypeOf(count.getCategory(), count.getSubCategory());
            if (type == null) continue;
            types.add(type);
            detections += count.getCount();
        }
        data.setTypes(new ArrayList<>(types));
        data.setSensitiveDataAccess(!types.isEmpty());
        data.setDetections(detections);
        return data;
    }

    /** Null when the guardrail activity could not be read — not the same as no detections. */
    private List<DashboardMaliciousEvent> fetchAgentEvents(String hostKey) {
        int now = Context.now();
        int since = now - GUARDRAIL_WINDOW_SECONDS;
        try {
            // Two calls, not one with both filters set: MaliciousEventService ANDs them — `actors`
            // is a top-level query field and the host match is pushed into $and, so one call would
            // ask for events whose host and actor are both this agent. The OR in that service is
            // only inside the host group (hosts / looseHostKeys / claudeDeviceIds).
            Map<String, DashboardMaliciousEvent> byId = new LinkedHashMap<>();
            for (String field : Arrays.asList("hosts", "actors")) {
                Map<String, Object> filters = new HashMap<>();
                filters.put(field, new ArrayList<>(Collections.singletonList(hostKey)));
                List<DashboardMaliciousEvent> page = threatAccess.violationEventsMinimal(since, now, EVENT_FETCH_LIMIT, filters);
                if (page == null) continue;
                for (DashboardMaliciousEvent event : page) {
                    if (event != null && event.getId() != null) byId.put(event.getId(), event);
                }
            }
            return new ArrayList<>(byId.values());
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("ArgusAgentDetailService: guardrail activity fetch failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * The sensitive type a subCategory names, or null when it names anything else.
     *
     * The gateway writes subCategory from the rule that fired and falls back to the policy's own
     * name only when no rule was recorded — the one case where it equals category. Without that
     * comparison a policy named "pii-policy" is indistinguishable from a PII rule and would be
     * read as a type called "policy".
     */
    static String piiTypeOf(String category, String subCategory) {
        if (subCategory == null) return null;
        if (category != null && category.trim().equalsIgnoreCase(subCategory.trim())) return null;
        String trimmed = subCategory.trim();
        for (String prefix : PII_PREFIXES) {
            if (trimmed.length() <= prefix.length()) continue;
            if (!trimmed.regionMatches(true, 0, prefix, 0, prefix.length())) continue;
            String type = trimmed.substring(prefix.length());
            while (!type.isEmpty() && (type.charAt(0) == '-' || type.charAt(0) == '_')) {
                type = type.substring(1);
            }
            return type.isEmpty() ? null : type;
        }
        return null;
    }
    /** The rules shown for an agent, in order. */
    private static final List<RuleSpec> RULE_SPECS = Arrays.asList(
            new RuleSpec("Denied topics", p -> isNotEmpty(p.getDeniedTopics()),
                    p -> names(p.getDeniedTopics(), GuardrailPolicies.DeniedTopic::getTopic)),
            new RuleSpec("PII detection", p -> isNotEmpty(p.getPiiTypes()),
                    p -> names(p.getPiiTypes(), GuardrailPolicies.PiiType::getType)),
            new RuleSpec("Harmful content filtering", p -> contentFilter(p, "harmfulCategories") != null,
                    ArgusAgentDetailService::harmfulCategories),
            new RuleSpec("Prompt injection filtering", p -> contentFilter(p, "promptAttacks") != null, p -> null),
            new RuleSpec("Secrets detection",
                    p -> p.getSecretsDetection() != null && p.getSecretsDetection().isEnabled(), p -> null));

    private static final class RuleSpec {
        final String name;
        final Predicate<GuardrailPolicies> enabled;
        final Function<GuardrailPolicies, List<String>> details;

        RuleSpec(String name, Predicate<GuardrailPolicies> enabled, Function<GuardrailPolicies, List<String>> details) {
            this.name = name;
            this.enabled = enabled;
            this.details = details;
        }
    }

    /**
     * Every rule in the catalogue, each marked enabled or not for this agent — a rule nobody turned
     * on is as much a part of the protection picture as one that is. Subtypes are collected across
     * every policy that enables the rule; a policy watching five PII types is still one row.
     */
    private List<AgentDetailResult.Rule> buildProtection(ApiCollection agent) {
        List<GuardrailPolicies> covering = new ArrayList<>();
        for (GuardrailPolicies policy : loadPolicies()) {
            if (policy != null && InsightUtil.policyCoversCollection(policy, policy.getApplyToDeviceIds(), agent)) {
                covering.add(policy);
            }
        }

        List<AgentDetailResult.Rule> rules = new ArrayList<>();
        for (RuleSpec spec : RULE_SPECS) {
            AgentDetailResult.Rule row = new AgentDetailResult.Rule();
            row.setName(spec.name);

            Set<String> details = new LinkedHashSet<>();
            for (GuardrailPolicies policy : covering) {
                if (!spec.enabled.test(policy)) continue;
                if (!row.isEnabled()) {
                    row.setEnabled(true);
                    row.setAppliesOn(direction(policy));
                }
                List<String> fromPolicy = spec.details.apply(policy);
                if (fromPolicy != null) details.addAll(fromPolicy);
            }
            row.setDetails(new ArrayList<>(details));
            row.setDetailsTotal(details.size());
            rules.add(row);
        }
        return rules;
    }

    /**
     * scanned comes from the agent's own postureGaps — AgenticPostureScoreCron already decided
     * collectionEverTested (an open finding OR a stamped ApiInfo.lastTested) when it scored this
     * agent, so this reuses that decision rather than re-deriving it.
     */
    private AgentDetailResult.RedTeam buildRedTeam(ApiCollection agent) {
        AgentDetailResult.RedTeam redTeam = new AgentDetailResult.RedTeam();
        String scanGap = agent.getPostureGaps() == null ? null
                : agent.getPostureGaps().get(PostureScoreCategory.RED_TEAM.key);
        boolean scanned = scanGap == null;
        redTeam.setScanned(scanned);

        Bson issueFilter = Filters.and(
                Filters.eq(TestingRunIssues.ID_API_COLLECTION_ID, agent.getId()),
                Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, TestRunIssueStatus.OPEN.name()));
        long openIssues = TestingRunIssuesDao.instance.count(issueFilter);
        redTeam.setOpenFindings(openIssues);
        redTeam.getCtas().add(runRedTeamScanCta());
        if (!scanned) return redTeam;

        List<TestingRunIssues> issues = TestingRunIssuesDao.instance.findAll(issueFilter, 0, RED_TEAM_FINDINGS_CAP,
                Sorts.descending(TestingRunIssues.LAST_SEEN));
        issues.sort(Comparator.comparingInt(
                i -> InsightUtil.severityRank(i.getSeverity() == null ? null : i.getSeverity().name())));

        redTeam.setLastScannedAt(lastScannedAt(agent.getId(), issues));
        redTeam.setSeverityBreakdown(severityBreakdown(agent.getId()));

        Map<String, Info> infoByType = findingInfoFor(issues);
        List<AgentDetailResult.RedTeamFinding> findings = new ArrayList<>();
        for (TestingRunIssues issue : issues) {
            String testSubCategory = issue.getId() == null ? null : issue.getId().getTestSubCategory();
            AgentDetailResult.RedTeamFinding finding = new AgentDetailResult.RedTeamFinding();
            Info info = testSubCategory == null ? null : infoByType.get(testSubCategory);
            finding.setTest(testSubCategory == null ? "-" : ArgusPostureService.testDisplayName(testSubCategory, infoByType));
            finding.setDescription(info == null ? null : info.getDescription());
            finding.setEndpoint(issue.getId() == null || issue.getId().getApiInfoKey() == null ? "-"
                    : issue.getId().getApiInfoKey().getMethod() + " " + issue.getId().getApiInfoKey().getUrl());
            finding.setSeverity(issue.getSeverity() == null ? null : issue.getSeverity().name());
            finding.setLastSeen(issue.getLastSeen());
            findings.add(finding);
        }
        redTeam.setFindings(findings);
        if (openIssues > findings.size()) redTeam.getCtas().add(viewAllFindingsCta(agent.getId(), openIssues));
        return redTeam;
    }

    /** Open-issue counts by severity for this one agent — the same aggregation
     *  AgenticPostureScoreCron groups by collection when it scores redTeam, scoped here to a single
     *  collection instead of re-deriving the count-by-severity logic. */
    private String severityBreakdown(int collectionId) {
        BasicDBObject groupedId = new BasicDBObject(SingleTypeInfo._API_COLLECTION_ID,
                "$" + TestingRunIssues.ID_API_COLLECTION_ID).append(TestingRunIssues.KEY_SEVERITY,
                "$" + TestingRunIssues.KEY_SEVERITY);
        Map<Integer, Map<String, Integer>> bySeverity = TestingRunIssuesDao.instance.getSeveritiesMapForCollections(
                Filters.eq(TestingRunIssues.ID_API_COLLECTION_ID, collectionId), false, groupedId);
        Map<String, Integer> counts = bySeverity.getOrDefault(collectionId, Collections.emptyMap());

        List<String> parts = new ArrayList<>();
        for (String severity : Arrays.asList("CRITICAL", "HIGH", "MEDIUM", "LOW")) {
            Integer count = counts.get(severity);
            if (count != null && count > 0) parts.add(count + " " + severity.toLowerCase(Locale.ROOT));
        }
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

    /** One batched lookup for every distinct test type among the shown findings, rather than one
     *  YamlTemplateDao call per row. */
    private Map<String, Info> findingInfoFor(List<TestingRunIssues> issues) {
        Set<String> testSubCategories = new HashSet<>();
        for (TestingRunIssues issue : issues) {
            if (issue.getId() != null && issue.getId().getTestSubCategory() != null) {
                testSubCategories.add(issue.getId().getTestSubCategory());
            }
        }
        if (testSubCategories.isEmpty()) return Collections.emptyMap();
        return YamlTemplateDao.instance.fetchTestInfoMap(Filters.in(Constants.ID, new ArrayList<>(testSubCategories)));
    }

    private static InsightResult.Cta runRedTeamScanCta() {
        return new InsightResult.Cta("run_red_team", "Run red-team scan", "NAVIGATE",
                InsightRoutes.TESTING, new HashMap<>(), false);
    }

    private static InsightResult.Cta viewAllFindingsCta(int collectionId, long openIssues) {
        Map<String, Object> params = new HashMap<>();
        params.put("filters", "activeCollections__true&apiCollectionId__" + collectionId);
        return new InsightResult.Cta("view_findings", "View all " + openIssues + " findings", "NAVIGATE",
                InsightRoutes.ISSUES, params, false);
    }

    /** Latest ApiInfo.lastTested stamp across the agent's endpoints, or — when a scan ran but
     *  nothing was stamped (collectionEverTested's open-finding branch) — the most recent open
     *  finding's lastSeen as the closest available signal. */
    private Integer lastScannedAt(int collectionId, List<TestingRunIssues> issues) {
        Integer maxTested = null;
        for (ApiInfo api : ApiInfoDao.instance.findAll(Filters.eq(ApiInfo.ID_API_COLLECTION_ID, collectionId),
                Projections.include(ApiInfo.LAST_TESTED))) {
            if (api.getLastTested() > 0 && (maxTested == null || api.getLastTested() > maxTested)) {
                maxTested = api.getLastTested();
            }
        }
        if (maxTested != null) return maxTested;

        Integer latestIssueSeen = null;
        for (TestingRunIssues issue : issues) {
            if (latestIssueSeen == null || issue.getLastSeen() > latestIssueSeen) latestIssueSeen = issue.getLastSeen();
        }
        return latestIssueSeen;
    }

    private static <T> List<String> names(List<T> items, Function<T, String> extractor) {
        List<String> names = new ArrayList<>();
        if (items == null) return names;
        for (T item : items) {
            if (item == null) continue;
            String name = extractor.apply(item);
            if (StringUtils.isNotBlank(name)) names.add(name.trim());
        }
        return names;
    }

    private static Map<String, Object> contentFilter(GuardrailPolicies policy, String key) {
        if (policy.getContentFiltering() == null) return null;
        Object value = policy.getContentFiltering().get(key);
        return value instanceof Map ? castMap(value) : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    /** The categories the policy actually filters — those left at "none" are not configured. */
    private static List<String> harmfulCategories(GuardrailPolicies policy) {
        List<String> categories = new ArrayList<>();
        Map<String, Object> harmful = contentFilter(policy, "harmfulCategories");
        if (harmful == null) return categories;
        for (Map.Entry<String, Object> entry : harmful.entrySet()) {
            if ("useForResponses".equals(entry.getKey())) continue;
            String level = entry.getValue() == null ? null : String.valueOf(entry.getValue());
            if (StringUtils.isBlank(level) || "none".equalsIgnoreCase(level) || "false".equalsIgnoreCase(level)) continue;
            categories.add(entry.getKey());
        }
        return categories;
    }

    private static boolean isNotEmpty(List<?> list) {
        return list != null && !list.isEmpty();
    }


    private List<GuardrailPolicies> loadPolicies() {
        try {
            int now = Context.now();
            InsightContext ctx = new InsightContext(Context.accountId.get(), Context.userId.get(),
                    Context.contextSource.get(), now - GUARDRAIL_WINDOW_SECONDS, now);
            InsightDataBundle bundle = insightService.getOrLoadBundle(ctx);
            return bundle == null || bundle.policies == null ? new ArrayList<>() : bundle.policies;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("ArgusAgentDetailService: policy load failed: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private static String direction(GuardrailPolicies policy) {
        if (policy.isApplyOnRequest() && policy.isApplyOnResponse()) return "Request + Response";
        if (policy.isApplyOnRequest()) return "Request";
        if (policy.isApplyOnResponse()) return "Response";
        return null;
    }

    /** `finding` names an open red-team finding's test type on this agent. Absent or unresolvable
     *  leaves the banner off rather than naming something that is not there. */
    private AgentDetailResult.Finding resolveFinding(int collectionId, String finding) {
        if (StringUtils.isBlank(finding)) return null;
        try {
            TestingRunIssues issue = TestingRunIssuesDao.instance.findOne(Filters.and(
                    Filters.eq(TestingRunIssues.ID_API_COLLECTION_ID, collectionId),
                    Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, "OPEN"),
                    Filters.eq("_id.testSubCategory", finding.trim())));
            if (issue == null) return null;

            AgentDetailResult.Finding row = new AgentDetailResult.Finding();
            row.setTitle(findingTitle(finding.trim()));
            row.setSeverity(issue.getSeverity() == null ? null : issue.getSeverity().name());
            return row;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("ArgusAgentDetailService: finding lookup failed: " + e.getMessage());
            return null;
        }
    }

    private static String findingTitle(String testSubCategory) {
        Map<String, Info> infoByType = YamlTemplateDao.instance.fetchTestInfoMap(
                Filters.in(Constants.ID, new ArrayList<>(new HashSet<>(
                        java.util.Collections.singletonList(testSubCategory)))));
        return ArgusPostureService.testDisplayName(testSubCategory, infoByType);
    }
}

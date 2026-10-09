package com.akto.service.posture;

import com.akto.action.threat_detection.DashboardMaliciousEvent;
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
import com.akto.mcp.McpSchema;
import com.akto.service.insights.InsightDataBundle;
import com.akto.service.insights.InsightResult;
import com.akto.service.insights.InsightRoutes;
import com.akto.service.insights.InsightUtil;
import com.akto.gpt.handlers.gpt_prompts.ToolCapabilityClassifier;
import com.akto.util.AgenticObserveUtil;
import com.akto.util.Constants;
import com.akto.utils.crons.AgenticPostureScoreCron;
import com.akto.utils.crons.ToolClassificationCron;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

// Flyout drills for the Argus posture score and the agent list, sharing one per-agent profile level.
public class ArgusAgentPostureDrillService {

    public static final String DRILL_POSTURE_SCORE = "postureScore";
    public static final String DRILL_HIGH_RISK_AGENTS = "highRiskAgents";

    private static final int DEFAULT_LIMIT = 10;
    private static final String ISSUES_ROUTE = "/dashboard/reports/issues";
    private static final String TESTING_ROUTE = "/dashboard/testing";
    private static final int PROFILE_SECTION_CAP = 10;
    // Same lookback AgenticPostureScoreCron scores malicious events over.
    private static final int MALICIOUS_EVENTS_WINDOW_SECONDS = 90 * 86400;
    // The agent-detail page's own caps — smaller than the flyout's PROFILE_SECTION_CAP since each
    // of its cards has more detail per row.
    private static final int RED_TEAM_FINDINGS_CAP = 5;
    private static final int GUARDRAIL_ACTIVITY_CAP = 5;

    private static final String REMEDIATION_RED_TEAM_NEVER_SCANNED =
            "Schedule a red-team scan for this agent — it has never been scanned.";

    private static final Map<Integer, String> CAPABILITY_FOR_POINTS = new HashMap<>();
    static {
        CAPABILITY_FOR_POINTS.put(100, "Can delete resources");
        CAPABILITY_FOR_POINTS.put(85, "Can read credentials or PII");
        CAPABILITY_FOR_POINTS.put(70, "Can write critical resources");
        CAPABILITY_FOR_POINTS.put(55, "Can write files");
    }

    private static final Map<String, Integer> SEVERITY_RANK = new HashMap<>();
    static {
        SEVERITY_RANK.put("CRITICAL", 0);
        SEVERITY_RANK.put("HIGH", 1);
        SEVERITY_RANK.put("MEDIUM", 2);
        SEVERITY_RANK.put("LOW", 3);
    }

    // path "" = category breakdown, "<category>" = agents driving it, "<category>/<collectionId>" = agent profile.
    public PostureDrillResult fetchPostureScoreDrill(InsightDataBundle bundle, String path, int skip, int limit) {
        // Account-wide on purpose: the hero score it explains isn't environment-scoped.
        List<ApiCollection> agents = ArgusPostureUtils.scoredAgents(bundle.collections);
        String[] segments = splitPath(path);
        if (segments.length == 0) return postureScoreRoot(bundle, agents, skip, limit);

        PostureScoreCategory category = PostureScoreCategory.fromKey(segments[0]);
        if (category == null) return notFound("Posture score", "Unknown category: " + segments[0]);
        List<PostureDrillResult.BreadcrumbItem> trail = new ArrayList<>(Arrays.asList(
                new PostureDrillResult.BreadcrumbItem("", "Posture score"),
                new PostureDrillResult.BreadcrumbItem(category.key, category.label)));
        if (segments.length == 1) return categoryAgents(bundle, agents, category, trail, skip, limit);
        return agentProfile(bundle, agents, segments[1], trail, category.key + "/" + segments[1]);
    }

    // path "" = every scored agent by score, "<collectionId>" = agent profile.
    public PostureDrillResult fetchHighRiskAgentsDrill(InsightDataBundle bundle, String environment, String path,
                                                       int skip, int limit) {
        List<ApiCollection> agents = ArgusPostureUtils.assetsIn(
                ArgusPostureUtils.scoredAgents(bundle.collections), environment);
        String[] segments = splitPath(path);
        List<PostureDrillResult.BreadcrumbItem> trail = new ArrayList<>(Collections.singletonList(
                new PostureDrillResult.BreadcrumbItem("", "Agents by risk")));
        if (segments.length == 0) return agentList(agents, trail, skip, limit);
        return agentProfile(bundle, agents, segments[0], trail, segments[0]);
    }

    private PostureDrillResult postureScoreRoot(InsightDataBundle bundle, List<ApiCollection> agents, int skip, int limit) {
        List<ApiCollection> slice = AgenticPostureScoreCron.worstSlice(agents, ApiCollection::getPostureScore);
        Set<Integer> sliceIds = new HashSet<>();
        for (ApiCollection agent : slice) sliceIds.add(agent.getId());

        List<Map<String, Object>> rows = new ArrayList<>();
        Map<PostureScoreCategory, Integer> affectedByCategory = new HashMap<>();
        double composite = 0;
        for (PostureScoreCategory category : PostureScoreCategory.values()) {
            double subScoreSum = 0;
            int affected = 0;
            ApiCollection top = null;
            double topSubScore = 0;
            for (ApiCollection agent : agents) {
                double sub = category.subScore(agent.getPostureSubScores());
                if (sliceIds.contains(agent.getId())) subScoreSum += sub;
                if (sub > 0) affected++;
                if (sub > topSubScore) {
                    topSubScore = sub;
                    top = agent;
                }
            }
            double avg = slice.isEmpty() ? 0 : subScoreSum / slice.size();
            double points = category.weight * avg / 100d;
            composite += points;
            affectedByCategory.put(category, affected);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", category.key);
            row.put("category", category.label);
            row.put("weight", category.weight + "%");
            row.put("averageScore", round1(avg));
            row.put("points", round1(points));
            row.put("agentsAffected", affected + " of " + agents.size());
            row.put("topContributor", top == null ? "-" : ArgusPostureUtils.agentDisplayName(top));
            row.put("remediation", affected == 0 ? "No action needed" : category.remediation);
            rows.add(row);
        }
        rows.sort(Comparator.comparingDouble((Map<String, Object> r) -> ((Number) r.get("points")).doubleValue()).reversed());

        PostureDrillResult result = base("How the posture score is calculated",
                Collections.singletonList(new PostureDrillResult.BreadcrumbItem("", "Posture score")),
                Arrays.asList(
                        col("category", "Category"), col("weight", "Weight"), col("averageScore", "Avg sub-score"),
                        col("points", "Points"), col("agentsAffected", "Agents affected"),
                        col("topContributor", "Top contributor"), col("remediation", "Remediation")),
                true);
        result.setSummary(Arrays.asList(
                new InsightResult.Metric("postureScore",
                        "Posture score · Calculated based on worst " + slice.size() + " agent(s)",
                        round1(composite), "count", Math.round(composite) + " / 100"),
                new InsightResult.Metric("agentsScored", "Agents scored", agents.size(), "count",
                        InsightUtil.grouped(agents.size())),
                new InsightResult.Metric("topCategory", "Biggest contributor", null, "text",
                        agents.isEmpty() ? "-" : String.valueOf(rows.get(0).get("category")))));
        result.setEmptyMessage("No agents have been scored yet.");
        int withoutPolicy = ArgusPostureUtils.computeCoverage(agents, bundle.policies).uncovered.size();
        result.setCtas(rootCtas(affectedByCategory, withoutPolicy));
        if (agents.isEmpty()) {
            result.addDataGap(new InsightResult.Gap("AGENTIC_ASSETS", "NO_ROWS",
                    "No AI agents have been scored yet, so there is nothing to break down."));
            rows = new ArrayList<>();
        }
        return page(result, rows, skip, limit);
    }

    private PostureDrillResult categoryAgents(InsightDataBundle bundle, List<ApiCollection> agents,
                                              PostureScoreCategory category,
                                              List<PostureDrillResult.BreadcrumbItem> trail, int skip, int limit) {
        List<ApiCollection> affected = new ArrayList<>();
        double subScoreSum = 0;
        for (ApiCollection agent : agents) {
            double sub = category.subScore(agent.getPostureSubScores());
            subScoreSum += sub;
            if (sub > 0) affected.add(agent);
        }
        affected.sort(Comparator.comparingDouble((ApiCollection a) -> category.subScore(a.getPostureSubScores()))
                .thenComparingDouble(ApiCollection::getPostureScore).reversed());

        int effectiveLimit = limit > 0 ? limit : DEFAULT_LIMIT;
        int from = Math.min(Math.max(skip, 0), affected.size());
        List<ApiCollection> pageAgents = affected.subList(from, Math.min(from + effectiveLimit, affected.size()));
        CategoryEvidence evidence = new CategoryEvidence(bundle, pageAgents);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (ApiCollection agent : pageAgents) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", agent.getId());
            row.put("agent", ArgusPostureUtils.agentDisplayName(agent));
            row.put("environment", ArgusPostureUtils.envBucket(InsightUtil.envTagValue(agent)));
            row.put("subScore", round1(category.subScore(agent.getPostureSubScores())));
            row.put("points", round1(category.points(agent.getPostureSubScores())));
            row.put("postureScore", Math.round(agent.getPostureScore()));
            row.put("detail", evidence.detail(category, agent));
            rows.add(row);
        }

        double avg = agents.isEmpty() ? 0 : subScoreSum / agents.size();
        PostureDrillResult result = base(category.label, trail, Arrays.asList(
                col("agent", "Agent"), col("environment", "Environment"), col("subScore", "Sub-score"),
                col("points", "Points to agent score"), col("postureScore", "Agent score"), col("detail", "Why")),
                true);
        result.setSummary(Arrays.asList(
                new InsightResult.Metric("affected", "Agents affected", affected.size(), agents.size(), "count",
                        InsightUtil.grouped(affected.size()), null),
                new InsightResult.Metric("averageScore", "Avg sub-score", round1(avg), "count", round1(avg) + " / 100"),
                new InsightResult.Metric("pointsToScore", "Points to posture score", round1(category.weight * avg / 100d),
                        "count", String.valueOf(round1(category.weight * avg / 100d))),
                new InsightResult.Metric("weight", "Weight", category.weight, "percent", category.weight + "%")));
        result.setEmptyMessage("No agent has any " + category.label.toLowerCase(Locale.ROOT) + " risk.");
        result.setCtas(categoryCtas(category));
        result.setTotal(affected.size());
        result.setSkip(from);
        result.setLimit(effectiveLimit);
        result.setRows(rows);
        return result;
    }

    private PostureDrillResult agentList(List<ApiCollection> agents, List<PostureDrillResult.BreadcrumbItem> trail,
                                         int skip, int limit) {
        long highOrCritical = 0;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ApiCollection agent : agents) {
            long score = Math.round(agent.getPostureScore());
            if (score >= ArgusPostureUtils.SEVERITY_HIGH_AT) highOrCritical++;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", agent.getId());
            row.put("agent", ArgusPostureUtils.agentDisplayName(agent));
            row.put("type", AgenticObserveUtil.getTypeFromCollection(agent));
            row.put("environment", ArgusPostureUtils.envBucket(InsightUtil.envTagValue(agent)));
            row.put("score", score);
            row.put("severity", ArgusPostureUtils.severityForScore(score));
            row.put("topIssue", ArgusPostureUtils.worstIssue(agent));
            rows.add(row);
        }

        PostureDrillResult result = base("Agents by posture score", trail, Arrays.asList(
                col("agent", "Agent"), col("type", "Type"), col("environment", "Environment"),
                col("score", "Score"), col("severity", "Severity"), col("topIssue", "Top issue")), true);
        result.setSummary(Arrays.asList(
                new InsightResult.Metric("agents", "Agents scored", agents.size(), "count", InsightUtil.grouped(agents.size())),
                new InsightResult.Metric("highOrCritical", "High or critical", highOrCritical, agents.size(), "count",
                        InsightUtil.grouped(highOrCritical), null)));
        result.setEmptyMessage("No agents have been scored in this environment yet.");
        return page(result, rows, skip, limit);
    }

    private PostureDrillResult agentProfile(InsightDataBundle bundle, List<ApiCollection> agents, String idSegment,
                                            List<PostureDrillResult.BreadcrumbItem> trail, String path) {
        ApiCollection agent = findAgent(agents, idSegment);
        if (agent == null) return notFound(trail.get(0).getLabel(), "This agent isn't in scope or hasn't been scored.");

        String name = ArgusPostureUtils.agentDisplayName(agent);
        long score = Math.round(agent.getPostureScore());
        String severity = ArgusPostureUtils.severityForScore(score);
        String environment = ArgusPostureUtils.envBucket(InsightUtil.envTagValue(agent));
        String type = AgenticObserveUtil.getTypeFromCollection(agent);
        Map<String, Object> subScores = agent.getPostureSubScores();
        int now = Context.now();

        long openIssues = openFindingsCount(agent.getId());
        List<TestingRunIssues> issues = openFindingsFor(agent.getId(), PROFILE_SECTION_CAP);

        // Null when the threat backend is unavailable; the malicious metric and section are then left out.
        List<Integer> agentIds = Collections.singletonList(agent.getId());
        int eventsSince = now - MALICIOUS_EVENTS_WINDOW_SECONDS;
        AgentEvents agentEvents = maliciousEventsFor(bundle, agent, PROFILE_SECTION_CAP);
        Long eventCount = agentEvents.events == null ? null : agentEvents.total;
        List<DashboardMaliciousEvent> events = agentEvents.events;

        // URL-pattern definition (same as the agent-detail page's own tool list) is the single
        // source of truth for "is this endpoint a tool" — "privileged" is a filter on top of it,
        // not a second, narrower query.
        List<ApiInfo> allTools = toolsFor(agent);
        List<ApiInfo> privilegedTools = new ArrayList<>();
        for (ApiInfo t : allTools) {
            String capability = t.getToolInfo() == null ? null : t.getToolInfo().getCapability();
            if (capability != null && !capability.trim().isEmpty() && !"SAFE".equalsIgnoreCase(capability.trim())) {
                privilegedTools.add(t);
            }
        }
        long toolCount = privilegedTools.size();
        List<ApiInfo> tools = privilegedTools.subList(0, Math.min(PROFILE_SECTION_CAP, privilegedTools.size()));

        Map<Integer, Map<String, Integer>> sensitiveData = bundle.sensitiveDataCounts(agentIds, eventsSince);
        Map<String, Integer> agentSensitiveData = sensitiveData == null ? null : sensitiveData.get(agent.getId());
        List<String> coveringPolicies = coveringPolicyNames(bundle.policies, agent);
        String scanGap = agent.getPostureGaps() == null ? null : agent.getPostureGaps().get(PostureScoreCategory.RED_TEAM.key);

        PostureDrillResult result = base(name, withLeaf(trail, path, name), new ArrayList<>(), false);
        result.setLayout("profile");
        result.setSeverity(severity);
        result.setBadge(new PostureDrillResult.Badge(capitalize(severity), badgeTone(severity)));
        result.setSubtitle("Posture score " + score + " / 100 · " + environment + " · " + type);
        List<InsightResult.Metric> summary = new ArrayList<>();
        summary.add(new InsightResult.Metric("postureScore", "Posture score", score, "count", score + " / 100"));
        summary.add(new InsightResult.Metric("openFindings", "Open red-team findings", openIssues, "count", InsightUtil.grouped(openIssues)));
        if (eventCount != null) {
            summary.add(new InsightResult.Metric("maliciousEvents", "Malicious events (90d)", eventCount, "count", InsightUtil.grouped(eventCount)));
        }
        summary.add(new InsightResult.Metric("privilegedTools", "Privileged tools", toolCount, "count", InsightUtil.grouped(toolCount)));
        result.setSummary(summary);
        result.setFacts(Arrays.asList(
                new PostureDrillResult.Fact("Host", agent.getHostName() == null ? "-" : agent.getHostName(), null),
                new PostureDrillResult.Fact("Top issue", ArgusPostureUtils.worstIssue(agent), null),
                new PostureDrillResult.Fact("Guardrail coverage",
                        coveringPolicies.isEmpty() ? "Not covered" : String.join(", ", coveringPolicies),
                        coveringPolicies.isEmpty() ? "critical" : null),
                new PostureDrillResult.Fact("Red-team scan", scanGap == null ? "Scanned" : scanGap, scanGap == null ? null : "critical"),
                new PostureDrillResult.Fact("Sensitive data",
                        sensitiveData == null ? "Unavailable"
                                : agentSensitiveData == null || agentSensitiveData.isEmpty() ? "None flagged in the last 90 days"
                                : InsightUtil.sensitiveDataLine(agentSensitiveData), null)));
        List<PostureDrillResult.Section> sections = new ArrayList<>(Arrays.asList(
                scoreBreakdownSection(subScores), remediationSection(agent), redTeamSection(issues, openIssues)));
        if (events != null) sections.add(maliciousEventsSection(events, eventCount));
        sections.add(privilegedToolsSection(tools, toolCount));
        result.setSections(sections);
        result.setCtas(profileCtas(agent, openIssues, scanGap != null, eventCount != null && eventCount > 0,
                coveringPolicies.isEmpty()));
        return result;
    }

    // ============ Agent detail page (/dashboard/agentic-posture/agent/:collectionId) ============
    // The full-page equivalent of agentProfile above, sharing its query helpers (openFindingsFor,
    // coveringPoliciesFor, maliciousEventsFor, toolsFor, byPointsDesc/round1) but assembling a
    // typed AgentDetailResult instead of the generic PostureDrillResult the flyout renders.
    //
    // Deliberately NOT using agentProfile's own agent list (scoredAgents/assetsIn): those exclude
    // never-scored agents (postureScore == null) and require guessing the right environment bucket
    // — fine for the flyout's drills, wrong for a direct-link detail page that must keep working
    // for every in-scope agent regardless of score or environment.
    public AgentDetailResult fetchAgentDetail(InsightDataBundle bundle, int collectionId, String finding) {
        ApiCollection agent = ApiCollectionsDao.instance.findOne(Filters.eq(Constants.ID, collectionId));
        if (agent == null || agent.isDeactivated() || !ArgusPostureUtils.isAgenticInScope(agent)) return null;

        AgentDetailResult result = new AgentDetailResult();
        result.setHeader(buildAgentHeader(agent, bundle));
        result.setTools(buildAgentTools(agent));
        result.setData(buildAgentSensitiveData(agent, bundle));
        result.setProtection(buildAgentProtection(agent, bundle));
        result.setRedTeam(buildAgentRedTeam(agent));
        result.setScoreBreakdown(buildScoreBreakdown(agent.getPostureSubScores()));
        result.setRemediation(buildRemediation(agent));
        result.setGuardrailActivity(buildGuardrailActivity(agent, bundle));
        result.setOpenedFromFinding(resolveAgentFinding(collectionId, finding));

        // Same CTA set, same rule, agentProfile's own header uses — reused, not rebuilt.
        boolean noPolicy = "Not covered".equals(result.getHeader().getGuardrailCoverage());
        boolean hasEvents = result.getGuardrailActivity().isAvailable() && result.getGuardrailActivity().getTotal() > 0;
        result.getHeader().setCtas(profileCtas(agent, result.getRedTeam().getOpenFindings(),
                !result.getRedTeam().isScanned(), hasEvents, noPolicy));
        return result;
    }

    private AgentDetailResult.Header buildAgentHeader(ApiCollection agent, InsightDataBundle bundle) {
        AgentDetailResult.Header header = new AgentDetailResult.Header();
        header.setCollectionId(agent.getId());
        header.setName(ArgusPostureUtils.agentDisplayName(agent));
        header.setDescription(agent.getDescription());
        long score = agent.getPostureScore() == null ? 0 : Math.round(agent.getPostureScore());
        header.setRiskScore(score);
        header.setSeverity(ArgusPostureUtils.severityForScore(score));
        header.setEnvironment(InsightUtil.environmentBucket(InsightUtil.envTagValue(agent)));
        header.setCreatedAt(agent.getStartTs() == 0 ? null : agent.getStartTs());
        header.setLastActive(agentLastActive(agent.getId()));
        header.setHost(agent.getHostName());
        header.setTopIssue(ArgusPostureUtils.worstIssue(agent));
        List<String> coveringPolicies = coveringPolicyNames(bundle.policies, agent);
        header.setGuardrailCoverage(coveringPolicies.isEmpty() ? "Not covered" : String.join(", ", coveringPolicies));
        return header;
    }

    private Integer agentLastActive(int collectionId) {
        ApiInfo latest = ApiInfoDao.instance.findOne(
                Filters.eq(ApiInfo.ID_API_COLLECTION_ID, collectionId),
                Sorts.descending(ApiInfo.LAST_SEEN));
        return latest == null ? null : latest.getLastSeen();
    }

    private List<AgentDetailResult.Tool> buildAgentTools(ApiCollection agent) {
        List<AgentDetailResult.Tool> tools = new ArrayList<>();
        for (ApiInfo api : toolsFor(agent)) {
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
            tool.setDetail(toolDetailFor(capability, privileged));
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
    private static String toolDetailFor(String capability, boolean privileged) {
        if (!privileged) return null;
        boolean destructive = ToolCapabilityClassifier.RESOURCE_DELETE.equalsIgnoreCase(capability)
                || ToolCapabilityClassifier.CRITICAL_RESOURCE_WRITE.equalsIgnoreCase(capability);
        return destructive ? "privileged · destructive" : "privileged";
    }

    /** Reuses bundle.sensitiveDataCounts — the same attribution and PII/LLM-rule definition
     *  agentProfile's own "Sensitive data" fact and the posture score cron already use. */
    private AgentDetailResult.SensitiveData buildAgentSensitiveData(ApiCollection agent, InsightDataBundle bundle) {
        AgentDetailResult.SensitiveData data = new AgentDetailResult.SensitiveData();
        int eventsSince = Context.now() - MALICIOUS_EVENTS_WINDOW_SECONDS;
        Map<Integer, Map<String, Integer>> sensitiveData = bundle.sensitiveDataCounts(
                Collections.singletonList(agent.getId()), eventsSince);
        if (sensitiveData == null) {
            data.setAvailable(false);
            return data;
        }
        Map<String, Integer> counts = sensitiveData.getOrDefault(agent.getId(), Collections.emptyMap());
        data.setTypes(new ArrayList<>(counts.keySet()));
        data.setSensitiveDataAccess(!counts.isEmpty());
        long detections = 0;
        for (int c : counts.values()) detections += c;
        data.setDetections(detections);
        return data;
    }

    /** The rules shown for an agent, in order. */
    private static final List<RuleSpec> RULE_SPECS = Arrays.asList(
            new RuleSpec("Denied topics", p -> isNotEmpty(p.getDeniedTopics()),
                    p -> ruleNames(p.getDeniedTopics(), GuardrailPolicies.DeniedTopic::getTopic)),
            new RuleSpec("PII detection", p -> isNotEmpty(p.getPiiTypes()),
                    p -> ruleNames(p.getPiiTypes(), GuardrailPolicies.PiiType::getType)),
            new RuleSpec("Harmful content filtering", p -> ruleContentFilter(p, "harmfulCategories") != null,
                    ArgusAgentPostureDrillService::harmfulCategories),
            new RuleSpec("Prompt injection filtering", p -> ruleContentFilter(p, "promptAttacks") != null, p -> null),
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
    private List<AgentDetailResult.Rule> buildAgentProtection(ApiCollection agent, InsightDataBundle bundle) {
        List<GuardrailPolicies> covering = coveringPoliciesFor(bundle.policies, agent);

        List<AgentDetailResult.Rule> rules = new ArrayList<>();
        for (RuleSpec spec : RULE_SPECS) {
            AgentDetailResult.Rule row = new AgentDetailResult.Rule();
            row.setName(spec.name);

            Set<String> details = new LinkedHashSet<>();
            for (GuardrailPolicies policy : covering) {
                if (!spec.enabled.test(policy)) continue;
                if (!row.isEnabled()) {
                    row.setEnabled(true);
                    row.setAppliesOn(ruleDirection(policy));
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
    private AgentDetailResult.RedTeam buildAgentRedTeam(ApiCollection agent) {
        AgentDetailResult.RedTeam redTeam = new AgentDetailResult.RedTeam();
        String scanGap = agent.getPostureGaps() == null ? null
                : agent.getPostureGaps().get(PostureScoreCategory.RED_TEAM.key);
        boolean scanned = scanGap == null;
        redTeam.setScanned(scanned);

        long openIssues = openFindingsCount(agent.getId());
        redTeam.setOpenFindings(openIssues);
        redTeam.getCtas().add(runRedTeamScanCta());
        if (!scanned) return redTeam;

        List<TestingRunIssues> issues = openFindingsFor(agent.getId(), RED_TEAM_FINDINGS_CAP);

        redTeam.setLastScannedAt(agentLastScannedAt(agent.getId(), issues));
        redTeam.setSeverityCounts(agentSeverityCounts(agent.getId()));

        Map<String, Info> infoByType = findingInfoFor(issues);
        List<AgentDetailResult.RedTeamFinding> findings = new ArrayList<>();
        for (TestingRunIssues issue : issues) {
            String testSubCategory = issue.getId() == null ? null : issue.getId().getTestSubCategory();
            AgentDetailResult.RedTeamFinding finding = new AgentDetailResult.RedTeamFinding();
            Info info = testSubCategory == null ? null : infoByType.get(testSubCategory);
            finding.setTest(testSubCategory == null ? "-" : ArgusPostureUtils.testDisplayName(testSubCategory, infoByType));
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
    private List<AgentDetailResult.SeverityCount> agentSeverityCounts(int collectionId) {
        BasicDBObject groupedId = new BasicDBObject(SingleTypeInfo._API_COLLECTION_ID,
                "$" + TestingRunIssues.ID_API_COLLECTION_ID).append(TestingRunIssues.KEY_SEVERITY,
                "$" + TestingRunIssues.KEY_SEVERITY);
        Map<Integer, Map<String, Integer>> bySeverity = TestingRunIssuesDao.instance.getSeveritiesMapForCollections(
                Filters.eq(TestingRunIssues.ID_API_COLLECTION_ID, collectionId), false, groupedId);
        Map<String, Integer> counts = bySeverity.getOrDefault(collectionId, Collections.emptyMap());

        List<AgentDetailResult.SeverityCount> result = new ArrayList<>();
        for (String severity : Arrays.asList("CRITICAL", "HIGH", "MEDIUM", "LOW")) {
            Integer count = counts.get(severity);
            if (count == null || count <= 0) continue;
            AgentDetailResult.SeverityCount sc = new AgentDetailResult.SeverityCount();
            sc.setSeverity(severity);
            sc.setCount(count);
            result.add(sc);
        }
        return result;
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
    private Integer agentLastScannedAt(int collectionId, List<TestingRunIssues> issues) {
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

    private List<AgentDetailResult.ScoreBreakdownRow> buildScoreBreakdown(Map<String, Object> subScores) {
        List<AgentDetailResult.ScoreBreakdownRow> rows = new ArrayList<>();
        for (PostureScoreCategory category : byPointsDesc(subScores)) {
            AgentDetailResult.ScoreBreakdownRow row = new AgentDetailResult.ScoreBreakdownRow();
            row.setCategory(category.label);
            row.setWeight(category.weight);
            row.setSubScore(round1(category.subScore(subScores)));
            row.setPoints(round1(category.points(subScores)));
            rows.add(row);
        }
        return rows;
    }

    // Same "never scanned" override as ArgusPostureUtils.worstIssue, for the per-category remediation text.
    static String remediationFor(PostureScoreCategory category, ApiCollection agent) {
        if (category == PostureScoreCategory.RED_TEAM) {
            Map<String, String> gaps = agent.getPostureGaps();
            if (gaps != null && gaps.containsKey(PostureScoreCategory.RED_TEAM.key)) {
                return REMEDIATION_RED_TEAM_NEVER_SCANNED;
            }
        }
        return category.remediation;
    }

    private List<AgentDetailResult.RemediationRow> buildRemediation(ApiCollection agent) {
        Map<String, Object> subScores = agent.getPostureSubScores();
        List<AgentDetailResult.RemediationRow> rows = new ArrayList<>();
        for (PostureScoreCategory category : byPointsDesc(subScores)) {
            if (category.points(subScores) <= 0) continue;
            AgentDetailResult.RemediationRow row = new AgentDetailResult.RemediationRow();
            row.setCategory(category.label);
            row.setPoints(round1(category.points(subScores)));
            row.setRemediation(remediationFor(category, agent));
            rows.add(row);
        }
        return rows;
    }

    /** Same row shape agentProfile's own maliciousEventsSection timeline already uses — event
     *  (subCategory, else category), url, severity — so both render identically. */
    private AgentDetailResult.GuardrailActivity buildGuardrailActivity(ApiCollection agent, InsightDataBundle bundle) {
        AgentDetailResult.GuardrailActivity activity = new AgentDetailResult.GuardrailActivity();
        AgentEvents agentEvents = maliciousEventsFor(bundle, agent, GUARDRAIL_ACTIVITY_CAP);
        if (agentEvents.events == null) {
            activity.setAvailable(false);
            return activity;
        }
        activity.setTotal(agentEvents.total);

        List<AgentDetailResult.GuardrailEvent> rows = new ArrayList<>();
        for (DashboardMaliciousEvent event : agentEvents.events) {
            AgentDetailResult.GuardrailEvent row = new AgentDetailResult.GuardrailEvent();
            row.setTimestamp(event.getTimestamp());
            row.setEvent(event.getSubCategory() != null ? event.getSubCategory() : event.getCategory());
            row.setUrl(event.getUrl());
            row.setSeverity(event.getSeverity());
            rows.add(row);
        }
        activity.setEvents(rows);
        activity.getCtas().add(violationsCta(false));
        return activity;
    }

    private static <T> List<String> ruleNames(List<T> items, Function<T, String> extractor) {
        List<String> names = new ArrayList<>();
        if (items == null) return names;
        for (T item : items) {
            if (item == null) continue;
            String name = extractor.apply(item);
            if (StringUtils.isNotBlank(name)) names.add(name.trim());
        }
        return names;
    }

    private static Map<String, Object> ruleContentFilter(GuardrailPolicies policy, String key) {
        if (policy.getContentFiltering() == null) return null;
        Object value = policy.getContentFiltering().get(key);
        return value instanceof Map ? castRuleMap(value) : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castRuleMap(Object value) {
        return (Map<String, Object>) value;
    }

    /** The categories the policy actually filters — those left at "none" are not configured. */
    private static List<String> harmfulCategories(GuardrailPolicies policy) {
        List<String> categories = new ArrayList<>();
        Map<String, Object> harmful = ruleContentFilter(policy, "harmfulCategories");
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

    private static String ruleDirection(GuardrailPolicies policy) {
        if (policy.isApplyOnRequest() && policy.isApplyOnResponse()) return "Request + Response";
        if (policy.isApplyOnRequest()) return "Request";
        if (policy.isApplyOnResponse()) return "Response";
        return null;
    }

    /** `finding` names an open red-team finding's test type on this agent. Absent or unresolvable
     *  leaves the banner off rather than naming something that is not there. */
    private AgentDetailResult.Finding resolveAgentFinding(int collectionId, String finding) {
        if (StringUtils.isBlank(finding)) return null;
        TestingRunIssues issue = TestingRunIssuesDao.instance.findOne(Filters.and(
                Filters.eq(TestingRunIssues.ID_API_COLLECTION_ID, collectionId),
                Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, "OPEN"),
                Filters.eq("_id.testSubCategory", finding.trim())));
        if (issue == null) return null;

        AgentDetailResult.Finding row = new AgentDetailResult.Finding();
        row.setTitle(agentFindingTitle(finding.trim()));
        row.setSeverity(issue.getSeverity() == null ? null : issue.getSeverity().name());
        return row;
    }

    private static String agentFindingTitle(String testSubCategory) {
        Map<String, Info> infoByType = YamlTemplateDao.instance.fetchTestInfoMap(
                Filters.in(Constants.ID, new ArrayList<>(Collections.singletonList(testSubCategory))));
        return ArgusPostureUtils.testDisplayName(testSubCategory, infoByType);
    }

    private static PostureDrillResult.Section scoreBreakdownSection(Map<String, Object> subScores) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PostureScoreCategory category : byPointsDesc(subScores)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("category", category.label);
            row.put("weight", category.weight + "%");
            row.put("subScore", round1(category.subScore(subScores)) + " / 100");
            row.put("points", String.valueOf(round1(category.points(subScores))));
            rows.add(row);
        }
        return new PostureDrillResult.Section("scoreBreakdown", "How this score is calculated",
                "Each category's sub-score × weight; the points add up to the posture score.", "table",
                Arrays.asList(col("category", "Category"), col("weight", "Weight"), col("subScore", "Sub-score"),
                        col("points", "Points")),
                rows, rows.size());
    }

    private static PostureDrillResult.Section remediationSection(ApiCollection agent) {
        Map<String, Object> subScores = agent.getPostureSubScores();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PostureScoreCategory category : byPointsDesc(subScores)) {
            if (category.points(subScores) <= 0) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("category", category.label);
            row.put("points", String.valueOf(round1(category.points(subScores))));
            row.put("remediation", remediationFor(category, agent));
            rows.add(row);
        }
        return new PostureDrillResult.Section("remediation", "Recommended fixes",
                "Ordered by how many points each fix removes.", "table",
                Arrays.asList(col("category", "Category"), col("points", "Points removed"), col("remediation", "Fix")),
                rows, rows.size());
    }

    private static PostureDrillResult.Section redTeamSection(List<TestingRunIssues> issues, long total) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (TestingRunIssues issue : issues) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("test", issue.getId() == null ? "-" : issue.getId().getTestSubCategory());
            row.put("endpoint", issue.getId() == null || issue.getId().getApiInfoKey() == null
                    ? "-" : issue.getId().getApiInfoKey().getMethod() + " " + issue.getId().getApiInfoKey().getUrl());
            row.put("severity", issue.getSeverity() == null ? null : issue.getSeverity().name());
            row.put("lastSeen", issue.getLastSeen());
            rows.add(row);
        }
        return new PostureDrillResult.Section("redTeamFindings", "Open red-team findings", null, "table",
                Arrays.asList(col("test", "Test"), col("endpoint", "Endpoint"), col("severity", "Severity"),
                        col("lastSeen", "Last seen")),
                rows, total);
    }

    private static PostureDrillResult.Section maliciousEventsSection(List<DashboardMaliciousEvent> events, long total) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (DashboardMaliciousEvent event : events) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("timestamp", event.getTimestamp());
            row.put("title", event.getSubCategory() != null ? event.getSubCategory() : event.getCategory());
            row.put("detail", event.getUrl());
            row.put("severity", event.getSeverity());
            rows.add(row);
        }
        return new PostureDrillResult.Section("maliciousEvents", "Guardrail & malicious activity",
                "Last 90 days, newest first.", "timeline", new ArrayList<>(), rows, total);
    }

    private static PostureDrillResult.Section privilegedToolsSection(List<ApiInfo> tools, long total) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ApiInfo tool : tools) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tool", tool.getId() == null ? "-" : tool.getId().getUrl());
            row.put("capability", tool.getToolInfo() == null ? "-" : tool.getToolInfo().getCapability());
            rows.add(row);
        }
        return new PostureDrillResult.Section("privilegedTools", "Privileged tools", null, "table",
                Arrays.asList(col("tool", "Tool"), col("capability", "Capability")), rows, total);
    }

    private static List<InsightResult.Cta> categoryCtas(PostureScoreCategory category) {
        switch (category) {
            case RED_TEAM:
                return Arrays.asList(
                        issuesCta(null, true),
                        cta("run_red_team", "Run red-team scan", TESTING_ROUTE, false));
            case GUARDRAIL_MALICIOUS:
                return Collections.singletonList(violationsCta(true));
            case COVERAGE:
                return Arrays.asList(
                        policyCta(true),
                        cta("run_red_team", "Run red-team scan", TESTING_ROUTE, false));
            default:
                return Collections.singletonList(cta("view_inventory", "View inventory", InsightRoutes.INVENTORY, true));
        }
    }

    // One CTA per category that actually has affected agents; red-team findings first when present.
    private static List<InsightResult.Cta> rootCtas(Map<PostureScoreCategory, Integer> affected, int withoutPolicy) {
        List<InsightResult.Cta> ctas = new ArrayList<>();
        if (affected.getOrDefault(PostureScoreCategory.RED_TEAM, 0) > 0) ctas.add(issuesCta(null, false));
        if (affected.getOrDefault(PostureScoreCategory.GUARDRAIL_MALICIOUS, 0) > 0) ctas.add(violationsCta(false));
        if (withoutPolicy > 0) ctas.add(policyCta(false));
        if (!ctas.isEmpty()) ctas.get(0).setPrimary(true);
        return ctas;
    }

    // Header renders the last CTA as primary, so the most urgent action goes last.
    private static List<InsightResult.Cta> profileCtas(ApiCollection agent, long openIssues, boolean neverScanned,
                                                       boolean hasEvents, boolean noPolicy) {
        List<InsightResult.Cta> ctas = new ArrayList<>();
        ctas.add(cta("open_collection", "Open collection", InsightRoutes.INVENTORY + "/" + agent.getId(), false));
        if (noPolicy) ctas.add(policyCta(false));
        if (hasEvents) ctas.add(violationsCta(false));
        if (openIssues > 0) ctas.add(issuesCta(agent.getId(), false));
        else if (neverScanned) ctas.add(cta("run_red_team", "Run red-team scan", TESTING_ROUTE, false));
        ctas.get(ctas.size() - 1).setPrimary(true);
        return ctas;
    }

    // collectionId null = all open findings; otherwise pre-filters the issues page to that agent.
    private static InsightResult.Cta issuesCta(Integer collectionId, boolean primary) {
        Map<String, Object> params = new HashMap<>();
        params.put("filters", "activeCollections__true" + (collectionId == null ? "" : "&apiCollectionId__" + collectionId));
        return new InsightResult.Cta("view_findings", "View red-team findings", "NAVIGATE", ISSUES_ROUTE, params, primary);
    }

    private static InsightResult.Cta violationsCta(boolean primary) {
        return cta("view_violations", "View guardrail violations", InsightRoutes.GUARDRAIL_VIOLATIONS, primary);
    }

    private static InsightResult.Cta policyCta(boolean primary) {
        return cta("create_policy", "Create guardrail policy", InsightRoutes.GUARDRAIL_POLICIES, primary);
    }

    // Per-page evidence for the category drill's "Why" column, fetched only for the visible rows.
    private static class CategoryEvidence {
        private final InsightDataBundle bundle;
        private final Map<Integer, Map<String, Integer>> redTeam;
        private final Map<Integer, Map<String, Integer>> malicious;
        private final Map<Integer, Map<String, Integer>> sensitive;
        private final ArgusPostureUtils.GuardrailsCoverageBreakdown coverage;

        CategoryEvidence(InsightDataBundle bundle, List<ApiCollection> agents) {
            this.bundle = bundle;
            List<Integer> ids = new ArrayList<>();
            for (ApiCollection a : agents) ids.add(a.getId());
            BasicDBObject groupedId = new BasicDBObject(SingleTypeInfo._API_COLLECTION_ID, "$" + TestingRunIssues.ID_API_COLLECTION_ID)
                    .append(TestingRunIssues.KEY_SEVERITY, "$" + TestingRunIssues.KEY_SEVERITY);
            this.redTeam = ids.isEmpty() ? new HashMap<>()
                    : TestingRunIssuesDao.instance.getSeveritiesMapForCollections(
                            Filters.in(TestingRunIssues.ID_API_COLLECTION_ID, ids), false, groupedId);
            Map<Integer, Map<String, Integer>> counts = ids.isEmpty() ? null
                    : bundle.maliciousSeverityCounts(ids, Context.now() - MALICIOUS_EVENTS_WINDOW_SECONDS);
            this.malicious = counts == null ? new HashMap<>() : counts;
            Map<Integer, Map<String, Integer>> sensitiveCounts = ids.isEmpty() ? null
                    : bundle.sensitiveDataCounts(ids, Context.now() - MALICIOUS_EVENTS_WINDOW_SECONDS);
            this.sensitive = sensitiveCounts == null ? new HashMap<>() : sensitiveCounts;
            this.coverage = ArgusPostureUtils.computeCoverage(agents, bundle.policies);
        }

        String detail(PostureScoreCategory category, ApiCollection agent) {
            Map<String, Object> subScores = agent.getPostureSubScores();
            switch (category) {
                case RED_TEAM: {
                    Map<String, Integer> bySeverity = redTeam.get(agent.getId());
                    if (bySeverity != null && !bySeverity.isEmpty()) return severityLine(bySeverity) + " open";
                    String notScanned = agent.getPostureGaps() == null ? null : agent.getPostureGaps().get(PostureScoreCategory.RED_TEAM.key);
                    return notScanned != null ? notScanned : "Open findings";
                }
                case GUARDRAIL_MALICIOUS: {
                    // Same threat-backend aggregation the score is computed from.
                    Map<String, Integer> bySeverity = malicious.get(agent.getId());
                    return bySeverity == null || bySeverity.isEmpty() ? null : severityLine(bySeverity) + " in the last 90 days";
                }
                case COVERAGE: {
                    List<String> gaps = new ArrayList<>();
                    if (!coverage.coveringPolicies.containsKey(agent.getId())) gaps.add("No guardrail policy");
                    if (agent.getPostureGaps() != null && agent.getPostureGaps().containsKey(PostureScoreCategory.RED_TEAM.key)) {
                        gaps.add("Never red-team scanned");
                    }
                    return gaps.isEmpty() ? "Partially covered" : String.join(" · ", gaps);
                }
                case SENSITIVE_DATA: {
                    Map<String, Integer> byData = sensitive.get(agent.getId());
                    if (byData == null || byData.isEmpty()) return "Sensitive data in guardrail violations";
                    return InsightUtil.sensitiveDataLine(byData) + " in the last 90 days";
                }
                case ACCESS_AUTH:
                    return category.subScore(subScores) >= 100 ? "Public and unauthenticated" : "Public or unauthenticated";
                case OVERPRIVILEGED_TOOLS: {
                    String capability = CAPABILITY_FOR_POINTS.get((int) Math.round(category.subScore(subScores)));
                    return capability == null ? "Privileged tool access" : capability;
                }
                default:
                    return "-";
            }
        }
    }

    private static String severityLine(Map<String, Integer> bySeverity) {
        List<String> parts = new ArrayList<>();
        for (String severity : Arrays.asList("CRITICAL", "HIGH", "MEDIUM", "LOW")) {
            Integer count = bySeverity.get(severity);
            if (count != null && count > 0) parts.add(count + " " + severity.toLowerCase(Locale.ROOT));
        }
        return parts.isEmpty() ? "Open findings" : String.join(", ", parts);
    }

    private static List<String> coveringPolicyNames(List<GuardrailPolicies> policies, ApiCollection agent) {
        List<String> names = new ArrayList<>();
        for (GuardrailPolicies p : coveringPoliciesFor(policies, agent)) {
            names.add(p.getName() == null ? "Unnamed policy" : p.getName());
        }
        return names;
    }

    private static List<GuardrailPolicies> coveringPoliciesFor(List<GuardrailPolicies> policies, ApiCollection agent) {
        List<GuardrailPolicies> covering = new ArrayList<>();
        for (GuardrailPolicies p : policies) {
            if (p != null && InsightUtil.policyCoversCollection(p, p.getApplyToDeviceIds(), agent)) covering.add(p);
        }
        return covering;
    }

    // collectionId's open red-team findings: the filter both the flyout's profile and the
    // agent-detail page need.
    private static Bson openIssueFilter(int collectionId) {
        return Filters.and(
                Filters.eq(TestingRunIssues.ID_API_COLLECTION_ID, collectionId),
                Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, "OPEN"));
    }

    private static long openFindingsCount(int collectionId) {
        return TestingRunIssuesDao.instance.count(openIssueFilter(collectionId));
    }

    // Most recent first from the DB, then re-ordered worst-severity-first.
    private static List<TestingRunIssues> openFindingsFor(int collectionId, int cap) {
        List<TestingRunIssues> issues = TestingRunIssuesDao.instance.findAll(openIssueFilter(collectionId), 0, cap,
                Sorts.descending(TestingRunIssues.LAST_SEEN));
        issues.sort(Comparator.comparingInt((TestingRunIssues i) -> severityRank(i.getSeverity() == null ? null : i.getSeverity().name())));
        return issues;
    }

    /** events=null means the threat backend was unavailable; total is only meaningful then. */
    private static class AgentEvents {
        final List<DashboardMaliciousEvent> events;
        final long total;

        AgentEvents(List<DashboardMaliciousEvent> events, long total) {
            this.events = events;
            this.total = total;
        }
    }

    private static AgentEvents maliciousEventsFor(InsightDataBundle bundle, ApiCollection agent, int cap) {
        int now = Context.now();
        int eventsSince = now - MALICIOUS_EVENTS_WINDOW_SECONDS;
        List<Integer> agentIds = Collections.singletonList(agent.getId());
        Map<Integer, Map<String, Integer>> severityCounts = bundle.maliciousSeverityCounts(agentIds, eventsSince);
        if (severityCounts == null) return new AgentEvents(null, 0);
        long total = severityCounts.getOrDefault(agent.getId(), Collections.emptyMap())
                .values().stream().mapToLong(Integer::longValue).sum();
        List<DashboardMaliciousEvent> events = bundle.listMaliciousEvents(eventsSince, now, cap, agentIds);
        return new AgentEvents(events, total);
    }

    /**
     * Only the agent's tool endpoints. ToolClassificationCron.TOOL_URL is what defines one, and
     * McpSchema.METHOD_TOOLS_LIST is the discovery call rather than a tool. Every other endpoint the
     * agent serves — a model invoke, for instance — is not a tool and does not belong here.
     */
    private static List<ApiInfo> toolsFor(ApiCollection agent) {
        Bson toolFilter = Filters.and(
                Filters.eq(ApiInfo.ID_API_COLLECTION_ID, agent.getId()),
                Filters.regex(ApiInfo.ID_URL, ToolClassificationCron.TOOL_URL),
                Filters.not(Filters.regex(ApiInfo.ID_URL, McpSchema.METHOD_TOOLS_LIST)));
        return ApiInfoDao.instance.findAll(toolFilter);
    }

    private static List<PostureScoreCategory> byPointsDesc(Map<String, Object> subScores) {
        List<PostureScoreCategory> categories = new ArrayList<>(Arrays.asList(PostureScoreCategory.values()));
        categories.sort(Comparator.comparingDouble((PostureScoreCategory c) -> c.points(subScores)).reversed());
        return categories;
    }

    private static ApiCollection findAgent(List<ApiCollection> agents, String idSegment) {
        try {
            int id = Integer.parseInt(idSegment);
            for (ApiCollection a : agents) {
                if (a.getId() == id) return a;
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private static PostureDrillResult base(String title, List<PostureDrillResult.BreadcrumbItem> breadcrumb,
                                           List<PostureDrillResult.ColumnDef> columns, boolean drillable) {
        PostureDrillResult result = new PostureDrillResult();
        result.setTitle(title);
        result.setBreadcrumb(breadcrumb);
        result.setColumns(columns);
        result.setDrillable(drillable);
        return result;
    }

    private static PostureDrillResult page(PostureDrillResult result, List<Map<String, Object>> rows, int skip, int limit) {
        int effectiveLimit = limit > 0 ? limit : DEFAULT_LIMIT;
        int from = Math.min(Math.max(skip, 0), rows.size());
        result.setRows(new ArrayList<>(rows.subList(from, Math.min(from + effectiveLimit, rows.size()))));
        result.setTotal(rows.size());
        result.setSkip(from);
        result.setLimit(effectiveLimit);
        return result;
    }

    private static PostureDrillResult notFound(String rootLabel, String message) {
        PostureDrillResult result = base(rootLabel,
                Collections.singletonList(new PostureDrillResult.BreadcrumbItem("", rootLabel)), new ArrayList<>(), false);
        result.setEmptyMessage(message);
        return result;
    }

    private static List<PostureDrillResult.BreadcrumbItem> withLeaf(List<PostureDrillResult.BreadcrumbItem> trail,
                                                                    String path, String label) {
        List<PostureDrillResult.BreadcrumbItem> out = new ArrayList<>(trail);
        out.add(new PostureDrillResult.BreadcrumbItem(path, label));
        return out;
    }

    private static String[] splitPath(String path) {
        if (path == null || path.trim().isEmpty()) return new String[0];
        return path.trim().split("/");
    }

    private static PostureDrillResult.ColumnDef col(String field, String header) {
        return new PostureDrillResult.ColumnDef(field, header);
    }

    private static InsightResult.Cta cta(String id, String label, String route, boolean primary) {
        return new InsightResult.Cta(id, label, "NAVIGATE", route, new HashMap<>(), primary);
    }

    private static int severityRank(String severity) {
        return severity == null ? 99 : SEVERITY_RANK.getOrDefault(severity, 99);
    }

    private static String badgeTone(String severity) {
        switch (severity) {
            case "CRITICAL": return "critical";
            case "HIGH": return "warning";
            case "MEDIUM": return "attention";
            default: return "info";
        }
    }

    private static String capitalize(String s) {
        return s.charAt(0) + s.substring(1).toLowerCase(Locale.ROOT);
    }

    private static double round1(double v) {
        return Math.round(v * 10d) / 10d;
    }
}

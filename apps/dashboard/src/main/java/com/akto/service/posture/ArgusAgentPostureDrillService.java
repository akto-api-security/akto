package com.akto.service.posture;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.MaliciousEventDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.service.insights.InsightDataBundle;
import com.akto.service.insights.InsightResult;
import com.akto.service.insights.InsightRoutes;
import com.akto.service.insights.InsightUtil;
import com.akto.util.AgenticObserveUtil;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
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
        List<ApiCollection> agents = ArgusPostureService.scoredAgents(bundle.collections);
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
        List<ApiCollection> agents = ArgusPostureService.assetsIn(
                ArgusPostureService.scoredAgents(bundle.collections), environment);
        String[] segments = splitPath(path);
        List<PostureDrillResult.BreadcrumbItem> trail = new ArrayList<>(Collections.singletonList(
                new PostureDrillResult.BreadcrumbItem("", "Agents by risk")));
        if (segments.length == 0) return agentList(agents, trail, skip, limit);
        return agentProfile(bundle, agents, segments[0], trail, segments[0]);
    }

    private PostureDrillResult postureScoreRoot(InsightDataBundle bundle, List<ApiCollection> agents, int skip, int limit) {
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
                subScoreSum += sub;
                if (sub > 0) affected++;
                if (sub > topSubScore) {
                    topSubScore = sub;
                    top = agent;
                }
            }
            double avg = agents.isEmpty() ? 0 : subScoreSum / agents.size();
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
            row.put("topContributor", top == null ? "-" : ArgusPostureService.agentDisplayName(top));
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
                new InsightResult.Metric("postureScore", "Posture score", round1(composite), "count",
                        Math.round(composite) + " / 100"),
                new InsightResult.Metric("agentsScored", "Agents scored", agents.size(), "count",
                        InsightUtil.grouped(agents.size())),
                new InsightResult.Metric("topCategory", "Biggest contributor", null, "text",
                        agents.isEmpty() ? "-" : String.valueOf(rows.get(0).get("category")))));
        result.setEmptyMessage("No agents have been scored yet.");
        int withoutPolicy = ArgusPostureService.computeCoverage(agents, bundle.policies).uncovered.size();
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
            row.put("agent", ArgusPostureService.agentDisplayName(agent));
            row.put("environment", ArgusPostureService.envBucket(ArgusPostureService.envTagValue(agent)));
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
        double scoreSum = 0;
        List<Map<String, Object>> rows = new ArrayList<>();
        int rank = 1;
        for (ApiCollection agent : agents) {
            long score = Math.round(agent.getPostureScore());
            scoreSum += agent.getPostureScore();
            if (score >= ArgusPostureService.SEVERITY_HIGH_AT) highOrCritical++;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", agent.getId());
            row.put("rank", rank++);
            row.put("agent", ArgusPostureService.agentDisplayName(agent));
            row.put("type", AgenticObserveUtil.getTypeFromCollection(agent));
            row.put("environment", ArgusPostureService.envBucket(ArgusPostureService.envTagValue(agent)));
            row.put("score", score);
            row.put("severity", ArgusPostureService.severityForScore(score));
            row.put("topIssue", ArgusPostureService.worstIssue(agent.getPostureSubScores()));
            rows.add(row);
        }

        PostureDrillResult result = base("Agents by posture score", trail, Arrays.asList(
                col("rank", "Rank"), col("agent", "Agent"), col("type", "Type"), col("environment", "Environment"),
                col("score", "Score"), col("severity", "Severity"), col("topIssue", "Top issue")), true);
        double avg = agents.isEmpty() ? 0 : scoreSum / agents.size();
        result.setSummary(Arrays.asList(
                new InsightResult.Metric("agents", "Agents scored", agents.size(), "count", InsightUtil.grouped(agents.size())),
                new InsightResult.Metric("highOrCritical", "High or critical", highOrCritical, agents.size(), "count",
                        InsightUtil.grouped(highOrCritical), null),
                new InsightResult.Metric("averageScore", "Average score", round1(avg), "count", Math.round(avg) + " / 100")));
        result.setEmptyMessage("No agents have been scored in this environment yet.");
        return page(result, rows, skip, limit);
    }

    private PostureDrillResult agentProfile(InsightDataBundle bundle, List<ApiCollection> agents, String idSegment,
                                            List<PostureDrillResult.BreadcrumbItem> trail, String path) {
        ApiCollection agent = findAgent(agents, idSegment);
        if (agent == null) return notFound(trail.get(0).getLabel(), "This agent isn't in scope or hasn't been scored.");

        String name = ArgusPostureService.agentDisplayName(agent);
        long score = Math.round(agent.getPostureScore());
        String severity = ArgusPostureService.severityForScore(score);
        String environment = ArgusPostureService.envBucket(ArgusPostureService.envTagValue(agent));
        String type = AgenticObserveUtil.getTypeFromCollection(agent);
        Map<String, Object> subScores = agent.getPostureSubScores();
        int now = Context.now();

        Bson issueFilter = Filters.and(
                Filters.eq(TestingRunIssues.ID_API_COLLECTION_ID, agent.getId()),
                Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, "OPEN"));
        long openIssues = TestingRunIssuesDao.instance.count(issueFilter);
        List<TestingRunIssues> issues = TestingRunIssuesDao.instance.findAll(issueFilter, 0, PROFILE_SECTION_CAP,
                Sorts.descending(TestingRunIssues.LAST_SEEN));
        issues.sort(Comparator.comparingInt((TestingRunIssues i) -> severityRank(i.getSeverity() == null ? null : i.getSeverity().name())));

        String accountId = String.valueOf(Context.accountId.get());
        Bson eventFilter = Filters.and(Filters.eq("latestApiCollectionId", agent.getId()),
                Filters.gte("detectedAt", (long) now - MALICIOUS_EVENTS_WINDOW_SECONDS));
        long eventCount = MaliciousEventDao.instance.countDocuments(accountId, eventFilter);
        List<Document> events = new ArrayList<>();
        MaliciousEventDao.instance.aggregateRaw(accountId, Arrays.asList(
                new Document("$match", new Document("latestApiCollectionId", agent.getId())
                        .append("detectedAt", new Document("$gte", (long) now - MALICIOUS_EVENTS_WINDOW_SECONDS))),
                new Document("$sort", new Document("detectedAt", -1)),
                new Document("$limit", PROFILE_SECTION_CAP))).into(events);

        Bson toolFilter = Filters.and(Filters.eq(ApiInfo.ID_API_COLLECTION_ID, agent.getId()),
                Filters.exists(ApiInfo.TOOL_INFO_CAPABILITY), Filters.ne(ApiInfo.TOOL_INFO_CAPABILITY, "SAFE"));
        long toolCount = ApiInfoDao.instance.count(toolFilter);
        List<ApiInfo> tools = ApiInfoDao.instance.findAll(toolFilter, 0, PROFILE_SECTION_CAP, null,
                Projections.include(ApiInfo.TOOL_INFO));

        List<String> sensitiveTypes = bundle.sensitiveByCollection.get(agent.getId());
        List<String> coveringPolicies = coveringPolicyNames(bundle.policies, agent);
        String scanGap = agent.getPostureGaps() == null ? null : agent.getPostureGaps().get(PostureScoreCategory.RED_TEAM.key);

        PostureDrillResult result = base(name, withLeaf(trail, path, name), new ArrayList<>(), false);
        result.setLayout("profile");
        result.setSeverity(severity);
        result.setBadge(new PostureDrillResult.Badge(capitalize(severity), badgeTone(severity)));
        result.setSubtitle("Posture score " + score + " / 100 · " + environment + " · " + type);
        result.setSummary(Arrays.asList(
                new InsightResult.Metric("postureScore", "Posture score", score, "count", score + " / 100"),
                new InsightResult.Metric("openFindings", "Open red-team findings", openIssues, "count", InsightUtil.grouped(openIssues)),
                new InsightResult.Metric("maliciousEvents", "Malicious events (90d)", eventCount, "count", InsightUtil.grouped(eventCount)),
                new InsightResult.Metric("privilegedTools", "Privileged tools", toolCount, "count", InsightUtil.grouped(toolCount))));
        result.setFacts(Arrays.asList(
                new PostureDrillResult.Fact("Host", agent.getHostName() == null ? "-" : agent.getHostName(), null),
                new PostureDrillResult.Fact("Top issue", ArgusPostureService.worstIssue(subScores), null),
                new PostureDrillResult.Fact("Guardrail coverage",
                        coveringPolicies.isEmpty() ? "Not covered" : String.join(", ", coveringPolicies),
                        coveringPolicies.isEmpty() ? "critical" : null),
                new PostureDrillResult.Fact("Red-team scan", scanGap == null ? "Scanned" : scanGap, scanGap == null ? null : "critical"),
                new PostureDrillResult.Fact("Sensitive data",
                        sensitiveTypes == null || sensitiveTypes.isEmpty() ? "None detected" : String.join(", ", sensitiveTypes), null)));
        result.setSections(Arrays.asList(
                scoreBreakdownSection(subScores),
                remediationSection(subScores),
                redTeamSection(issues, openIssues),
                maliciousEventsSection(events, eventCount),
                privilegedToolsSection(tools, toolCount)));
        result.setCtas(profileCtas(agent, openIssues, scanGap != null, eventCount, coveringPolicies.isEmpty()));
        return result;
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

    private static PostureDrillResult.Section remediationSection(Map<String, Object> subScores) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PostureScoreCategory category : byPointsDesc(subScores)) {
            if (category.points(subScores) <= 0) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("category", category.label);
            row.put("points", String.valueOf(round1(category.points(subScores))));
            row.put("remediation", category.remediation);
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

    private static PostureDrillResult.Section maliciousEventsSection(List<Document> events, long total) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Document event : events) {
            Object detectedAt = event.get("detectedAt");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("timestamp", detectedAt instanceof Number ? ((Number) detectedAt).longValue() : 0L);
            row.put("title", event.getString("subCategory") != null ? event.getString("subCategory") : event.getString("category"));
            row.put("detail", event.getString("latestApiEndpoint"));
            row.put("severity", event.getString("severity"));
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
                                                       long eventCount, boolean noPolicy) {
        List<InsightResult.Cta> ctas = new ArrayList<>();
        ctas.add(cta("open_collection", "Open collection", InsightRoutes.INVENTORY + "/" + agent.getId(), false));
        if (noPolicy) ctas.add(policyCta(false));
        if (eventCount > 0) ctas.add(violationsCta(false));
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
        private final Map<Integer, Long> malicious;
        private final ArgusPostureService.GuardrailsCoverageBreakdown coverage;

        CategoryEvidence(InsightDataBundle bundle, List<ApiCollection> agents) {
            this.bundle = bundle;
            List<Integer> ids = new ArrayList<>();
            for (ApiCollection a : agents) ids.add(a.getId());
            BasicDBObject groupedId = new BasicDBObject(SingleTypeInfo._API_COLLECTION_ID, "$" + TestingRunIssues.ID_API_COLLECTION_ID)
                    .append(TestingRunIssues.KEY_SEVERITY, "$" + TestingRunIssues.KEY_SEVERITY);
            this.redTeam = ids.isEmpty() ? new HashMap<>()
                    : TestingRunIssuesDao.instance.getSeveritiesMapForCollections(
                            Filters.in(TestingRunIssues.ID_API_COLLECTION_ID, ids), false, groupedId);
            this.malicious = maliciousCounts(ids);
            this.coverage = ArgusPostureService.computeCoverage(agents, bundle.policies);
        }

        String detail(PostureScoreCategory category, ApiCollection agent) {
            Map<String, Object> subScores = agent.getPostureSubScores();
            switch (category) {
                case RED_TEAM: {
                    Map<String, Integer> bySeverity = redTeam.get(agent.getId());
                    return bySeverity == null || bySeverity.isEmpty() ? "Open findings" : severityLine(bySeverity) + " open";
                }
                case GUARDRAIL_MALICIOUS: {
                    long count = malicious.getOrDefault(agent.getId(), 0L);
                    return count + " event" + (count == 1 ? "" : "s") + " in the last 90 days";
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
                    List<String> types = bundle.sensitiveByCollection.get(agent.getId());
                    return types == null || types.isEmpty() ? "Sensitive data detected" : String.join(", ", types);
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

        private static Map<Integer, Long> maliciousCounts(List<Integer> ids) {
            Map<Integer, Long> out = new HashMap<>();
            if (ids.isEmpty()) return out;
            long since = (long) Context.now() - MALICIOUS_EVENTS_WINDOW_SECONDS;
            List<Document> pipeline = Arrays.asList(
                    new Document("$match", new Document("latestApiCollectionId", new Document("$in", ids))
                            .append("detectedAt", new Document("$gte", since))),
                    new Document("$group", new Document("_id", "$latestApiCollectionId").append("count", new Document("$sum", 1))));
            for (Document d : MaliciousEventDao.instance.aggregateRaw(String.valueOf(Context.accountId.get()), pipeline)) {
                Object id = d.get("_id");
                if (id instanceof Number) out.put(((Number) id).intValue(), ((Number) d.get("count")).longValue());
            }
            return out;
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
        for (GuardrailPolicies p : policies) {
            if (p == null || !InsightUtil.policyCoversCollection(p, p.getApplyToDeviceIds(), agent)) continue;
            names.add(p.getName() == null ? "Unnamed policy" : p.getName());
        }
        return names;
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

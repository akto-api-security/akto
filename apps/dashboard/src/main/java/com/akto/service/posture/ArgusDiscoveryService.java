package com.akto.service.posture;

import com.akto.dao.ApiInfoDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.McpAuditInfo;
import com.akto.service.insights.InsightDataBundle;
import com.akto.service.insights.InsightUtil;
import com.akto.service.insights.InsightUtil.GovernanceBucket;
import com.akto.util.AgenticObserveUtil;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.Severity;
import com.akto.utils.crons.ToolClassificationCron;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

// Argus "Agent discovery" overview: what was discovered, what nobody has reviewed or is exposed, and a short to-do
// list ranked by severity. Counts come from the shared bundle; only tool counts and last-seen are read here.
public class ArgusDiscoveryService {

    private static final int WEEK_SECONDS = 7 * 86400;
    private static final int STALE_SECONDS = 30 * 86400;
    private static final int TODO_LIMIT = 10;
    private static final int ASSETS_PER_TODO = 5;

    // Sub-score cut-offs from AgenticPostureScoreCron: public and unauthenticated; delete or critical-write tool.
    private static final double EXPOSED_AT = 100;
    private static final double DESTRUCTIVE_AT = 70;

    private static final String OTHER_PLATFORM = "Other";
    private static final Map<String, String> PLATFORM_NAMES = Map.of(
            "N8N", "n8n", "LANGCHAIN", "LangChain", "COPILOT_STUDIO", "Copilot Studio",
            "AWS_BEDROCK", "AWS Bedrock", "aws-vpc", "AWS VPC", "k8s-svc", "Kubernetes");

    public BasicDBObject build(InsightDataBundle bundle, int now) {
        List<ApiCollection> assets = assetsIn(bundle.collections);
        List<Integer> ids = assets.stream().map(ApiCollection::getId).collect(Collectors.toList());
        Map<Integer, Integer> lastSeen = ApiInfoDao.instance.getLastTrafficSeenForCollections(ids);
        Bson inScope = Filters.in(ApiInfo.ID_API_COLLECTION_ID, ids);
        long tools = ApiInfoDao.instance.count(Filters.and(inScope, Filters.regex(ApiInfo.ID_URL, ToolClassificationCron.TOOL_URL)));
        long privileged = ApiInfoDao.instance.count(Filters.and(inScope, ArgusPostureService.privilegedToolFilter()));
        return compose(assets, bundle, lastSeen, tools, privileged, now);
    }

    // In-scope, active assets, riskiest first so every list below shows the worst ones.
    static List<ApiCollection> assetsIn(List<ApiCollection> collections) {
        List<ApiCollection> assets = new ArrayList<>();
        for (ApiCollection c : collections) {
            if (c != null && !c.isDeactivated() && ArgusPostureUtils.isAgenticInScope(c)) assets.add(c);
        }
        assets.sort(Comparator.comparingDouble((ApiCollection c) -> c.getPostureScore() == null ? 0 : c.getPostureScore()).reversed());
        return assets;
    }

    BasicDBObject compose(List<ApiCollection> assets, InsightDataBundle bundle, Map<Integer, Integer> lastSeen,
                          long tools, long privileged, int now) {
        int weekStart = now - WEEK_SECONDS;
        List<ApiCollection> agents = ofType(assets, AgenticObserveUtil.CLIENT_TYPE_AI_AGENT, AgenticObserveUtil.CLIENT_TYPE_SAAS_AGENT);
        List<ApiCollection> servers = ofType(assets, AgenticObserveUtil.CLIENT_TYPE_MCP_SERVER);

        // Reviewed means someone approved or rejected it, or it is on the allowlist (the rule Atlas uses for shadow AI).
        // Audit rows link to their server by collection id, since mcpHost is often missing, so that link is checked too.
        // Only a decision on the server itself counts: not "Under review", and not a decision on one of its tools.
        Map<String, String> remarks = InsightUtil.remarksByServiceName(bundle.auditRows);
        Set<Integer> decided = bundle.auditRows.stream()
                .filter(a -> Constants.AKTO_MCP_SERVER_TAG.equals(a.getType()) && isDecision(a.getRemarks()))
                .map(McpAuditInfo::getHostCollectionId).collect(Collectors.toSet());
        Predicate<ApiCollection> unreviewed = c -> {
            GovernanceBucket bucket = InsightUtil.governanceBucket(c, bundle.allowlistNamesLower, remarks);
            return !decided.contains(c.getId())
                    && (bucket == GovernanceBucket.SHADOW || bucket == GovernanceBucket.UNAPPROVED || bucket == GovernanceBucket.PERSONAL);
        };
        // Shadow AI: unreviewed, with at least one exposure signal. Unreviewed alone is nearly every agent.
        Predicate<ApiCollection> sensitive = c -> !bundle.sensitiveByCollection.getOrDefault(c.getId(), Collections.emptyList()).isEmpty();
        Predicate<ApiCollection> observedOnly = c -> InsightUtil.everSeenObserve(c) && !InsightUtil.everSeenInline(c);
        Predicate<ApiCollection> personal = InsightUtil::isPersonalAccount;
        List<ApiCollection> shadow = filter(agents, c -> unreviewed.test(c) && (sensitive.test(c) || observedOnly.test(c) || personal.test(c)));

        ArgusPostureUtils.GuardrailsCoverageBreakdown coverage = ArgusPostureUtils.computeCoverage(assets, bundle.policies);
        Set<Integer> uncoveredIds = coverage.uncovered.stream().map(ApiCollection::getId).collect(Collectors.toSet());
        List<ApiCollection> stale = filter(assets, c -> c.getStartTs() < now - STALE_SECONDS && lastSeen.getOrDefault(c.getId(), 0) < now - STALE_SECONDS);
        long withTraffic = assets.stream().filter(c -> lastSeen.getOrDefault(c.getId(), 0) >= now - STALE_SECONDS).count();
        long highRisk = assets.stream().filter(c -> c.getPostureScore() != null && Math.round(c.getPostureScore()) >= ArgusPostureUtils.SEVERITY_HIGH_AT).count();

        List<BasicDBObject> todos = new ArrayList<>();
        todos.add(shadowTodo(shadow, sensitive, observedOnly, personal));
        List<ApiCollection> exposed = filter(servers, c -> subScore(c, PostureScoreCategory.ACCESS_AUTH) >= EXPOSED_AT);
        todos.add(todo("exposedMcp", "Exposed MCP server", Severity.CRITICAL, " reachable from the internet with no authentication",
                "Public clients were seen calling them and no authentication was recorded. Anyone who finds them can call their tools.",
                exposed, "MCP server", countChip(filter(exposed, unreviewed).size(), "unreviewed")));
        todos.add(todo("supplyChain", "Supply chain", Severity.CRITICAL, " flagged as malicious",
                "Tool analysis found malicious behavior in their tool definitions. Remove them from every agent that uses them.",
                filter(servers, InsightUtil::isMaliciousMcpServer), "MCP server", Collections.emptyList()));
        todos.add(todo("dangerousTool", "Dangerous tool", Severity.HIGH, " with tools that can delete data, write to critical systems or read credentials",
                "Remove the tools they don't need, or require approval before each call.",
                filter(assets, c -> subScore(c, PostureScoreCategory.OVERPRIVILEGED_TOOLS) >= DESTRUCTIVE_AT), "asset", Collections.emptyList()));
        List<ApiCollection> uncovered = filter(assets, c -> uncoveredIds.contains(c.getId()));
        todos.add(todo("coverage", "Coverage", Severity.HIGH, " without a guardrail policy",
                "Nothing checks their prompts or tool calls. Apply a policy to each.",
                uncovered, "asset", countChip(filter(uncovered, c -> c.getStartTs() >= weekStart).size(), "new this week")));
        todos.add(todo("stale", "Stale assets", Severity.LOW, " with no traffic in 30 days",
                "Retire the ones nobody uses so they stop being something to defend.",
                stale, "asset", Collections.emptyList()));
        todos.removeIf(t -> t == null);
        todos.sort(Comparator.comparingInt((BasicDBObject t) -> InsightUtil.severityRank(t.getString("severity")))
                .thenComparing(Comparator.comparingInt((BasicDBObject t) -> t.getInt("count")).reversed()));
        if (todos.size() > TODO_LIMIT) todos = new ArrayList<>(todos.subList(0, TODO_LIMIT));

        return new BasicDBObject()
                .append("kpis", new BasicDBObject()
                        .append("agents", new BasicDBObject("value", agents.size()).append("newThisWeek", filter(agents, c -> c.getStartTs() >= weekStart).size()))
                        .append("mcpServers", new BasicDBObject("value", servers.size()).append("unreviewed", filter(servers, unreviewed).size()))
                        .append("tools", new BasicDBObject("value", tools).append("privileged", privileged))
                        .append("shadowAi", new BasicDBObject("value", shadow.size()).append("sensitive", filter(shadow, sensitive).size())))
                .append("funnel", new BasicDBObject("assets", assets.size()).append("agents", agents.size())
                        .append("highRisk", highRisk).append("todos", todos.size()))
                .append("platforms", platforms(assets, uncoveredIds))
                .append("coverage", new BasicDBObject("assets", assets.size()).append("withTraffic", withTraffic)
                        .append("withPolicy", assets.size() - uncoveredIds.size()))
                .append("todos", todos);
    }

    private static BasicDBObject shadowTodo(List<ApiCollection> shadow, Predicate<ApiCollection> sensitive,
                                           Predicate<ApiCollection> observedOnly, Predicate<ApiCollection> personal) {
        long withData = shadow.stream().filter(sensitive).count();
        long observed = shadow.stream().filter(observedOnly).count();
        Severity severity = withData > 0 ? Severity.CRITICAL : observed > 0 ? Severity.HIGH : Severity.MEDIUM;
        List<String> chips = new ArrayList<>();
        chips.addAll(countChip(withData, "with sensitive data"));
        chips.addAll(countChip(observed, "observed only"));
        chips.addAll(countChip(shadow.stream().filter(personal).count(), "personal account"));
        return todo("shadowAi", "Shadow AI", severity, " that nobody has reviewed, with risk signals",
                "Seen in traffic with no approval on record. Observed only means the traffic was never routed through the gateway.",
                shadow, "AI agent", chips);
    }

    // One to-do, or null when nothing needs doing. Assets are the first few, already riskiest first.
    private static BasicDBObject todo(String id, String kind, Severity severity, String titleTail, String why,
                                      List<ApiCollection> affected, String noun, List<String> chips) {
        if (affected.isEmpty()) return null;
        List<BasicDBObject> shown = affected.stream().limit(ASSETS_PER_TODO)
                .map(c -> new BasicDBObject("id", c.getId()).append("name", ArgusPostureUtils.agentDisplayName(c)))
                .collect(Collectors.toList());
        return new BasicDBObject("id", id).append("kind", kind).append("severity", severity.name())
                .append("title", affected.size() + " " + plural(noun, affected.size()) + titleTail)
                .append("why", why).append("chips", chips).append("count", affected.size()).append("assets", shown);
    }

    // Per platform (the collection's source tag): how many assets, and how many have a guardrail policy.
    private static List<BasicDBObject> platforms(List<ApiCollection> assets, Set<Integer> uncoveredIds) {
        Map<String, int[]> counts = new LinkedHashMap<>();
        for (ApiCollection c : assets) {
            int[] n = counts.computeIfAbsent(platformOf(c), k -> new int[2]);
            n[0]++;
            if (!uncoveredIds.contains(c.getId())) n[1]++;
        }
        return counts.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<String, int[]> e) -> OTHER_PLATFORM.equals(e.getKey()))
                        .thenComparing(e -> -e.getValue()[0]))
                .map(e -> new BasicDBObject("name", e.getKey()).append("total", e.getValue()[0]).append("withPolicy", e.getValue()[1]))
                .collect(Collectors.toList());
    }

    // Instance-style sources ("aws-vpc_vpc-123", "k8s-svc_cards.prod") collapse to their prefix.
    static String platformOf(ApiCollection c) {
        List<String> sources = InsightUtil.tagValues(c, Constants.AKTO_ENDPOINT_SOURCE_TAG);
        if (sources.isEmpty()) return OTHER_PLATFORM;
        String source = sources.get(0).replaceFirst("^source=", "");
        int sep = source.indexOf('_');
        if (sep > 0 && source.equals(source.toLowerCase(Locale.ROOT))) source = source.substring(0, sep);
        return PLATFORM_NAMES.getOrDefault(source, source);
    }

    private static boolean isDecision(String remarks) {
        return StringUtils.isNotBlank(remarks) && !McpAuditInfo.REMARKS_UNDER_REVIEW.equals(remarks);
    }

    private static double subScore(ApiCollection c, PostureScoreCategory category) {
        return category.subScore(c.getPostureSubScores());
    }

    private static List<ApiCollection> ofType(List<ApiCollection> assets, String... types) {
        Set<String> wanted = new HashSet<>(List.of(types));
        return filter(assets, c -> wanted.contains(AgenticObserveUtil.getTypeFromCollection(c)));
    }

    private static List<ApiCollection> filter(List<ApiCollection> assets, Predicate<ApiCollection> keep) {
        return assets.stream().filter(keep).collect(Collectors.toList());
    }

    private static List<String> countChip(long count, String label) {
        return count > 0 ? Collections.singletonList(count + " " + label) : Collections.emptyList();
    }

    private static String plural(String noun, int count) {
        return count == 1 ? noun : noun + "s";
    }
}

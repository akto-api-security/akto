package com.akto.service.posture;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.nhi_governance.NhiIdentityDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.nhi_governance.NhiIdentity;
import com.akto.gpt.handlers.gpt_prompts.ToolCapabilityClassifier;
import com.akto.service.insights.InsightContext;
import com.akto.service.insights.InsightDataBundle;
import com.akto.service.insights.InsightService;
import com.akto.service.insights.InsightUtil;
import com.akto.util.AgenticObserveUtil;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.akto.utils.TtlCache;
import com.akto.utils.crons.ToolClassificationCron;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Reads the assets behind the inventory page and rolls each into a row; a user's rows are cached for a minute. */
public class ArgusInventorySource {

    private static final long CACHE_TTL_MS = 60_000;
    private static final int CACHE_MAX_ENTRIES = 200;
    private static final int WEEK_SECONDS = 7 * 86400;
    private static final int QUIET_LIMIT = 5;
    private static final String TEAM_TAG_KEY = "team";
    private static final String NHI_INACTIVE = "INACTIVE";
    // The MCP methods, which end a tool URL the same way a tool name does.
    private static final Set<String> MCP_METHODS = Set.of("list", "call");
    private static final Set<ArgusInventoryFlag> NEEDS_ATTENTION = EnumSet.of(ArgusInventoryFlag.DORMANT, ArgusInventoryFlag.NO_POLICY,
            ArgusInventoryFlag.EXPOSED, ArgusInventoryFlag.DANGEROUS, ArgusInventoryFlag.SHADOW, ArgusInventoryFlag.MALICIOUS);
    private static final Set<String> AGENT_TYPES = Set.of(AgenticObserveUtil.CLIENT_TYPE_AI_AGENT, AgenticObserveUtil.CLIENT_TYPE_SAAS_AGENT);
    private static final TtlCache<String, Snapshot> CACHE = new TtlCache<>(CACHE_TTL_MS, CACHE_MAX_ENTRIES);

    private final InsightService insightService = new InsightService();

    /** The rows and the time they were read, which every date on the page is measured from. */
    @Getter
    @AllArgsConstructor
    public static class Snapshot {
        private final List<ArgusInventoryRow> rows;
        private final int now;
    }

    @Getter
    @AllArgsConstructor
    static final class ToolSummary {
        private final int count;
        private final int privileged;
        private final List<String> names;
        private final Set<String> privilegedNames;
    }

    public Snapshot load(InsightContext ctx) {
        String key = ctx.getAccountId() + "_" + ctx.getUserId() + "_" + ctx.getContextSource();
        Snapshot cached = CACHE.get(key);
        if (cached != null) return cached;
        int now = Context.now();
        InsightDataBundle bundle = insightService.getOrLoadBundle(ctx);
        List<ApiCollection> assets = ArgusDiscoveryService.assetsIn(bundle.collections);
        List<Integer> ids = new ArrayList<>();
        for (ApiCollection asset : assets) ids.add(asset.getId());
        Map<Integer, Integer> lastSeen = ApiInfoDao.instance.getLastTrafficSeenForCollections(ids);
        Snapshot snapshot = new Snapshot(compose(assets, bundle, lastSeen, readTools(ids), now), now);
        CACHE.put(key, snapshot);
        return snapshot;
    }

    private static Map<Integer, List<ApiInfo>> readTools(List<Integer> collectionIds) {
        Bson toolFilter = Filters.and(Filters.in(ApiInfo.ID_API_COLLECTION_ID, collectionIds),
                Filters.regex(ApiInfo.ID_URL, ToolClassificationCron.TOOL_URL));
        Map<Integer, List<ApiInfo>> toolsOf = new HashMap<>();
        for (ApiInfo tool : ApiInfoDao.instance.findAll(toolFilter, Projections.include(ApiInfo.TOOL_INFO))) {
            toolsOf.computeIfAbsent(tool.getId().getApiCollectionId(), id -> new ArrayList<>()).add(tool);
        }
        return toolsOf;
    }

    /** One row per asset, riskiest first as given; everything it needs is passed in. */
    static List<ArgusInventoryRow> compose(List<ApiCollection> assets, InsightDataBundle bundle, Map<Integer, Integer> lastSeen,
                                           Map<Integer, List<ApiInfo>> toolsOf, int now) {
        RowBuilder builder = new RowBuilder(bundle, ArgusPostureUtils.computeCoverage(assets, bundle.policies), lastSeen, toolsOf, now);
        List<ArgusInventoryRow> rows = new ArrayList<>();
        for (ApiCollection asset : assets) rows.add(builder.build(asset));
        return rows;
    }

    /** Active identities that were not used for 30 days and still have access, longest quiet first. */
    public BasicDBObject quiet(int now) {
        List<Bson> conditions = new ArrayList<>();
        conditions.add(Filters.ne(NhiIdentity.STATUS, NHI_INACTIVE));
        conditions.add(Filters.lt(NhiIdentity.LAST_USED_AT, now - ArgusDiscoveryService.STALE_SECONDS));
        // An expiry of zero means it never expires.
        conditions.add(Filters.or(Filters.eq(NhiIdentity.EXPIRY_DATE, 0), Filters.gt(NhiIdentity.EXPIRY_DATE, now)));
        CONTEXT_SOURCE contextSource = Context.contextSource.get();
        if (contextSource != null) conditions.add(Filters.eq(NhiIdentity.CONTEXT_SOURCE, contextSource.name()));
        Bson dormant = Filters.and(conditions);
        List<NhiIdentity> identities = NhiIdentityDao.instance.findAll(dormant, 0, QUIET_LIMIT, Sorts.ascending(NhiIdentity.LAST_USED_AT));
        return quietOf(NhiIdentityDao.instance.count(dormant), identities);
    }

    public static BasicDBObject quietOf(long total, List<NhiIdentity> identities) {
        List<BasicDBObject> items = new ArrayList<>();
        for (NhiIdentity identity : identities) {
            BasicDBObject item = new BasicDBObject();
            item.put("id", identity.getHexId());
            item.put("name", identity.getIdentityName());
            item.put("agentName", identity.getAgentName());
            item.put("accessLevel", identity.getAccessLevel());
            item.put("lastUsedTs", identity.getLastUsedAt());
            items.add(item);
        }
        BasicDBObject quiet = new BasicDBObject();
        quiet.put("total", total);
        quiet.put("items", items);
        return quiet;
    }

    /** Turns one asset into a row; the rules for unreviewed and sensitive are the discovery overview's. */
    private static final class RowBuilder {

        private final Predicate<ApiCollection> unreviewed;
        private final Predicate<ApiCollection> sensitive;
        private final ArgusPostureUtils.GuardrailsCoverageBreakdown coverage;
        private final Map<Integer, Integer> lastSeen;
        private final Map<Integer, List<ApiInfo>> toolsOf;
        private final int now;

        private RowBuilder(InsightDataBundle bundle, ArgusPostureUtils.GuardrailsCoverageBreakdown coverage, Map<Integer, Integer> lastSeen,
                           Map<Integer, List<ApiInfo>> toolsOf, int now) {
            this.unreviewed = ArgusDiscoveryService.unreviewedIn(bundle);
            this.sensitive = ArgusDiscoveryService.sensitiveIn(bundle);
            this.coverage = coverage;
            this.lastSeen = lastSeen;
            this.toolsOf = toolsOf;
            this.now = now;
        }

        private ArgusInventoryRow build(ApiCollection asset) {
            int id = asset.getId();
            String type = AgenticObserveUtil.getTypeFromCollection(asset);
            int risk = asset.getPostureScore() == null ? 0 : (int) Math.round(asset.getPostureScore());
            int seenTs = lastSeen.getOrDefault(id, 0);
            int policies = coverage.coveringPolicies.getOrDefault(id, Collections.emptyList()).size();
            ToolSummary tools = summarize(toolsOf.getOrDefault(id, Collections.emptyList()));
            List<String> teams = InsightUtil.tagValues(asset, TEAM_TAG_KEY);
            Set<ArgusInventoryFlag> flags = flagsOf(asset, type, risk, seenTs, policies, tools, teams);
            return ArgusInventoryRow.builder()
                    .id(id)
                    .name(ArgusPostureUtils.agentDisplayName(asset))
                    .type(type)
                    .platform(ArgusDiscoveryService.platformOf(asset))
                    .risk(risk)
                    .createdTs(asset.getStartTs())
                    .seenTs(seenTs)
                    .policies(policies)
                    .tools(tools.getCount())
                    .privileged(tools.getPrivileged())
                    .teams(teams)
                    .toolNames(tools.getNames())
                    .privilegedToolNames(tools.getPrivilegedNames())
                    .posture(postureOf(flags))
                    .flags(flags)
                    .build();
        }

        private ToolSummary summarize(List<ApiInfo> toolInfos) {
            int privileged = 0;
            List<String> names = new ArrayList<>();
            Set<String> privilegedNames = new HashSet<>();
            for (ApiInfo tool : toolInfos) {
                String capability = tool.getToolInfo() == null ? null : tool.getToolInfo().getCapability();
                boolean isPrivileged = capability != null && !ToolCapabilityClassifier.SAFE.equals(capability);
                if (isPrivileged) privileged++;
                String name = ToolClassificationCron.toolNameFromUrl(tool.getId().getUrl().replaceAll("/+$", ""));
                // All caps is a path parameter, not a tool.
                if (name.isEmpty() || MCP_METHODS.contains(name) || name.equals(name.toUpperCase(Locale.ROOT))) continue;
                if (!names.contains(name)) names.add(name);
                if (isPrivileged) privilegedNames.add(name);
            }
            Collections.sort(names);
            return new ToolSummary(toolInfos.size(), privileged, names, privilegedNames);
        }

        private Set<ArgusInventoryFlag> flagsOf(ApiCollection asset, String type, int risk, int seenTs, int policies,
                                                ToolSummary tools, List<String> teams) {
            Set<ArgusInventoryFlag> flags = EnumSet.noneOf(ArgusInventoryFlag.class);
            boolean isUnreviewed = unreviewed.test(asset);
            // Dormant: old enough to have had traffic, and none for 30 days.
            boolean dormant = asset.getStartTs() < now - ArgusDiscoveryService.STALE_SECONDS && seenTs < now - ArgusDiscoveryService.STALE_SECONDS;
            addIf(flags, ArgusInventoryFlag.UNREVIEWED, isUnreviewed);
            addIf(flags, ArgusInventoryFlag.SENSITIVE, sensitive.test(asset));
            addIf(flags, ArgusInventoryFlag.SHADOW, AGENT_TYPES.contains(type) && ArgusDiscoveryService.isShadow(asset, unreviewed, sensitive));
            double accessAuth = PostureScoreCategory.ACCESS_AUTH.subScore(asset.getPostureSubScores());
            double overprivileged = PostureScoreCategory.OVERPRIVILEGED_TOOLS.subScore(asset.getPostureSubScores());
            addIf(flags, ArgusInventoryFlag.EXPOSED, accessAuth >= ArgusDiscoveryService.EXPOSED_AT);
            addIf(flags, ArgusInventoryFlag.DANGEROUS, overprivileged >= ArgusDiscoveryService.DESTRUCTIVE_AT);
            addIf(flags, ArgusInventoryFlag.PRIVILEGED, tools.getPrivileged() > 0);
            addIf(flags, ArgusInventoryFlag.MALICIOUS, InsightUtil.isMaliciousMcpServer(asset));
            addIf(flags, ArgusInventoryFlag.NO_POLICY, policies == 0);
            addIf(flags, ArgusInventoryFlag.DORMANT, dormant);
            addIf(flags, ArgusInventoryFlag.HIGH, risk >= ArgusPostureUtils.SEVERITY_HIGH_AT);
            addIf(flags, ArgusInventoryFlag.NO_TEAM, teams.isEmpty());
            addIf(flags, ArgusInventoryFlag.NEW, asset.getStartTs() >= now - WEEK_SECONDS);
            // Posture first, then the combinations, which are made of the signals above.
            boolean needsAttention = !Collections.disjoint(flags, NEEDS_ATTENTION);
            boolean atRisk = flags.contains(ArgusInventoryFlag.HIGH) && !dormant;
            flags.add(atRisk ? ArgusInventoryFlag.AT_RISK : needsAttention ? ArgusInventoryFlag.ATTENTION : ArgusInventoryFlag.HEALTHY);
            for (ArgusInventoryCombo combo : ArgusInventoryCombo.values()) {
                if (combo.matches(flags)) flags.add(combo.getFlag());
            }
            return flags;
        }

        private static void addIf(Set<ArgusInventoryFlag> flags, ArgusInventoryFlag flag, boolean condition) {
            if (condition) flags.add(flag);
        }

        private static ArgusInventoryFlag postureOf(Set<ArgusInventoryFlag> flags) {
            if (flags.contains(ArgusInventoryFlag.AT_RISK)) return ArgusInventoryFlag.AT_RISK;
            return flags.contains(ArgusInventoryFlag.ATTENTION) ? ArgusInventoryFlag.ATTENTION : ArgusInventoryFlag.HEALTHY;
        }
    }
}

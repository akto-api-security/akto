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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Reads the assets behind the inventory page and turns each into a row; a user's rows are cached for a minute. */
public class ArgusInventorySource {

    private static final long CACHE_TTL_MS = 60_000;
    private static final int CACHE_MAX_ENTRIES = 200;
    private static final int QUIET_LIMIT = 5;
    private static final String TEAM_TAG_KEY = "team";
    private static final String NHI_INACTIVE = "INACTIVE";
    // The MCP methods, which end a tool URL the same way a tool name does.
    private static final Set<String> MCP_METHODS = Set.of("list", "call");
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

    /** One row per asset. This is the only place the facts on a row are set; ArgusInventoryRow says what each means. */
    static List<ArgusInventoryRow> compose(List<ApiCollection> assets, InsightDataBundle bundle, Map<Integer, Integer> lastSeen,
                                           Map<Integer, List<ApiInfo>> toolsOf, int now) {
        // The Overview's rules for unreviewed, sensitive and shadow AI.
        Predicate<ApiCollection> unreviewedRule = ArgusDiscoveryService.unreviewedIn(bundle);
        Predicate<ApiCollection> sensitiveRule = ArgusDiscoveryService.sensitiveIn(bundle);
        ArgusPostureUtils.GuardrailsCoverageBreakdown coverage = ArgusPostureUtils.computeCoverage(assets, bundle.policies);
        int staleBefore = now - ArgusDiscoveryService.STALE_SECONDS;

        List<ArgusInventoryRow> rows = new ArrayList<>();
        for (ApiCollection asset : assets) {
            int id = asset.getId();
            String type = AgenticObserveUtil.getTypeFromCollection(asset);
            int seenTs = lastSeen.getOrDefault(id, 0);
            Map<String, Object> subScores = asset.getPostureSubScores();

            List<ApiInfo> toolInfos = toolsOf.getOrDefault(id, Collections.emptyList());
            int privileged = 0;
            List<String> toolNames = new ArrayList<>();
            Set<String> privilegedToolNames = new HashSet<>();
            for (ApiInfo tool : toolInfos) {
                boolean isPrivileged = isPrivileged(tool);
                if (isPrivileged) privileged++;
                String name = toolName(tool);
                if (name == null) continue;
                if (!toolNames.contains(name)) toolNames.add(name);
                if (isPrivileged) privilegedToolNames.add(name);
            }
            Collections.sort(toolNames);

            rows.add(ArgusInventoryRow.builder()
                    .id(id)
                    .name(ArgusPostureUtils.agentDisplayName(asset))
                    .type(type)
                    .platform(ArgusDiscoveryService.platformOf(asset))
                    .createdTs(asset.getStartTs())
                    .seenTs(seenTs)
                    .teams(InsightUtil.tagValues(asset, TEAM_TAG_KEY))
                    .risk(asset.getPostureScore() == null ? 0 : (int) Math.round(asset.getPostureScore()))
                    .policies(coverage.coveringPolicies.getOrDefault(id, Collections.emptyList()).size())
                    .tools(toolInfos.size())
                    .privileged(privileged)
                    .toolNames(toolNames)
                    .privilegedToolNames(privilegedToolNames)
                    .exposed(PostureScoreCategory.ACCESS_AUTH.subScore(subScores) >= ArgusDiscoveryService.EXPOSED_AT)
                    .dangerous(PostureScoreCategory.OVERPRIVILEGED_TOOLS.subScore(subScores) >= ArgusDiscoveryService.DESTRUCTIVE_AT)
                    .malicious(InsightUtil.isMaliciousMcpServer(asset))
                    .sensitive(sensitiveRule.test(asset))
                    .unreviewed(unreviewedRule.test(asset))
                    .shadow(AGENT_TYPES.contains(type) && ArgusDiscoveryService.isShadow(asset, unreviewedRule, sensitiveRule))
                    .dormant(asset.getStartTs() < staleBefore && seenTs < staleBefore)
                    .newThisWeek(asset.getStartTs() >= now - ArgusDiscoveryService.WEEK_SECONDS)
                    .build());
        }
        return rows;
    }

    private static boolean isPrivileged(ApiInfo tool) {
        String capability = tool.getToolInfo() == null ? null : tool.getToolInfo().getCapability();
        return capability != null && !ToolCapabilityClassifier.SAFE.equals(capability);
    }

    // The tool's name from its URL, or null for the MCP methods and all-caps path parameters, which are not tools.
    private static String toolName(ApiInfo tool) {
        String name = ToolClassificationCron.toolNameFromUrl(tool.getId().getUrl().replaceAll("/+$", ""));
        if (name.isEmpty() || MCP_METHODS.contains(name) || name.equals(name.toUpperCase(Locale.ROOT))) return null;
        return name;
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
}

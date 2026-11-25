package com.akto.agentic;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.traffic.CollectionTags;
import com.akto.util.AgenticObserveUtil;
import com.akto.util.Constants;
import com.akto.util.McpClientRegistry;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

/**
 * Groups collections into the agentic assets (agent / service / llm / skill / plugin) the Agentic
 * assets page shows. Lives in libs/utils so usage metering counts exactly what the page shows.
 */
public final class AgenticAssetGrouping {

    private AgenticAssetGrouping() {}

    // ==================== Server-side pagination for Agentic Assets ====================
    // See the class-level doc comment and atlas-scale-test/DASHBOARD_OPTIMIZATION.md for why this
    // exists. Same suffix list as constants.js's SUFFIX_TLDS — hostnames like
    // "vulnerable-mcp-kong.akto.io" aren't in device.agent.service format, so grouping by a bare TLD
    // would incorrectly collapse unrelated collections together; fall back to the full hostname.
    private static final Set<String> SERVICE_NAME_SUFFIX_TLDS = new HashSet<>(Arrays.asList(
            "io", "com", "net", "org", "cloud", "dev", "app", "ai", "co"
    ));
    // Mirrors mcpClientHelper.js's AGENT_OWNER_TAG_KEYS.
    private static final Set<String> AGENT_OWNER_TAG_KEYS = new HashSet<>(Arrays.asList(
            Constants.AKTO_MCP_CLIENT_TAG, Constants.AKTO_AI_AGENT_TAG
    ));
    // String.split(String) compiles a new Pattern on every call unless the regex is a single
    // non-metacharacter literal — precompile once, this runs once per (collection, group) pair.
    public static final Pattern DOT_SPLIT = Pattern.compile("\\.");

    public static boolean hasTagKey(List<CollectionTags> envType, String keyName) {
        if (envType == null) return false;
        for (CollectionTags tag : envType) {
            if (tag != null && keyName.equals(tag.getKeyName())) return true;
        }
        return false;
    }

    public static boolean hasAgentOwnerTag(List<CollectionTags> envType) {
        if (envType == null) return false;
        for (CollectionTags tag : envType) {
            if (tag == null) continue;
            if (AGENT_OWNER_TAG_KEYS.contains(tag.getKeyName()) || "agent-name".equals(tag.getKeyName())) return true;
        }
        return false;
    }

    public static String extractServiceNameForGrouping(String hostName) {
        if (StringUtils.isBlank(hostName)) return null;
        String[] parts = DOT_SPLIT.split(hostName);
        if (parts.length < 3) return hostName;
        if (SERVICE_NAME_SUFFIX_TLDS.contains(parts[2].toLowerCase(Locale.ROOT))) return hostName;
        StringBuilder sb = new StringBuilder(parts[2]);
        for (int i = 3; i < parts.length; i++) sb.append(".").append(parts[i]);
        return sb.toString();
    }

    // Matches agenticPageBuilders.js's buildDeviceEndpointsPageData's connector-ingested exclusion —
    // these represent the AI agent as seen through an external connector (e.g. MICROSOFT_DEFENDER),
    // not a real service child row.
    public static boolean isConnectorIngested(List<CollectionTags> envType) {
        if (envType == null) return false;
        for (CollectionTags tag : envType) {
            if (tag == null) continue;
            if ("connector".equals(tag.getKeyName())) return true;
            if ("source".equals(tag.getKeyName()) && "DEFENDER".equals(tag.getValue())) return true;
        }
        return false;
    }

    // The llm group a collection belongs to, as classifyAllGroups keys it; null when it has no browser-llm tag.
    public static String llmGroupKey(ApiCollection c) {
        if (!hasTagKey(c.getEnvType(), Constants.AKTO_BROWSER_LLM_TAG)) return null;
        String serviceName = extractServiceNameForGrouping(c.getHostName());
        return StringUtils.isNotBlank(serviceName) ? serviceName : null;
    }

    // The agent group a collection belongs to, as classifyAllGroups keys it; null when it has no agent tag.
    public static String agentGroupKey(ApiCollection c) {
        CollectionTags assetTag = AgenticObserveUtil.findAssetTag(c);
        if (assetTag == null || StringUtils.isBlank(assetTag.getValue())
                || Constants.AKTO_BROWSER_LLM_AGENT_TAG.equals(assetTag.getKeyName())) return null;
        return McpClientRegistry.resolveClientKey(assetTag.getValue());
    }

    // The service group a collection belongs to, as classifyAllGroups keys it; null when it gets no service row.
    // constants.js's groupCollectionsByService only excludes an agent-owned collection from
    // ALSO getting its own service row when the type tag is gen-ai — an agent-owned
    // mcp-server collection (e.g. mcp-client=cursor + mcp-server) still gets a service row
    // too, deliberately double-counted across the agent and service groups. Mirror that
    // exact scoping rather than unconditionally skipping the service branch whenever a bound
    // or orphaned (unbound) owner tag was found.
    public static String serviceGroupKey(ApiCollection c, boolean inAgentGroup) {
        CollectionTags typeTag = AgenticObserveUtil.findTypeTag(c);
        if (typeTag == null) return null;
        if (Constants.AKTO_GEN_AI_TAG.equals(typeTag.getKeyName())
                && (inAgentGroup || hasAgentOwnerTag(c.getEnvType()))) return null;
        String serviceName = extractServiceNameForGrouping(c.getHostName());
        return StringUtils.isNotBlank(serviceName) ? serviceName : null;
    }

    /**
     * Every collection, with the projection the grouping needs. Shared by the Agentic assets page
     * (which caches the result) and usage metering, so both group the same data.
     */
    public static List<ApiCollection> fetchCollections() {
        List<ApiCollection> collections = ApiCollectionsDao.instance.findAll(
                Filters.empty(),
                Projections.include(ApiCollection.ID, ApiCollection.HOST_NAME, ApiCollection.TAGS_STRING, ApiCollection.SKILLS, ApiCollection.START_TS,
                        ApiCollection.BASE_RISK_SCORE, ApiCollection.BASE_RISK_SCORE_REASON, ApiCollection._DEACTIVATED, ApiCollection.DESCRIPTION)
        );
        AgenticObserveUtil.retainSkillsWithApiInfo(collections);
        return collections;
    }

    /**
     * ENDPOINT_ASSET_COUNT: the largest endpointsCount (distinct devices) among the "AI Agent"
     * groups, i.e. the same endpointsCount the Agentic assets page shows per row.
     */
    public static int maxEndpointsPerAiAgent(Map<String, GroupSummary> groups) {
        int max = 0;
        for (GroupSummary g : groups.values()) {
            if (AgenticObserveUtil.CLIENT_TYPE_AI_AGENT.equals(g.clientType)) {
                max = Math.max(max, g.endpointIds.size());
            }
        }
        return max;
    }

    /**
     * Endpoints per agent do not depend on traffic, risk or sensitive-data maps, so grouping runs
     * with empty ones. Reads fresh from the DB; deliberately bypasses the page's classification cache.
     */
    public static int computeMaxEndpointsPerAiAgent() {
        return maxEndpointsPerAiAgent(classifyAllGroups(
                fetchCollections(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap()));
    }

    public static Map<String, GroupSummary> classifyAllGroups(List<ApiCollection> collections,
            Map<String, Integer> traffic, Map<String, Double> risk, Map<String, List<String>> sensitiveMap) {
        Map<String, GroupSummary> groups = new LinkedHashMap<>();

        // A plugin-bundled skill's own collection (tagPluginSkill's isolation fix) carries the skill's
        // name on its "skill" tag, not in ApiCollection.skills (that array only gets populated by the
        // legacy per-agent skill-detector collection, a DIFFERENT collection than the plugin's own —
        // so the skill fan-out loop below, which only reads .skills, never sees the plugin-tagged
        // collection at all). Built as its own pre-pass so the fallback below is available regardless
        // of which collection the fan-out loop is currently on.
        Map<String, String> skillNameToOwningPlugin = new HashMap<>();
        for (ApiCollection c : collections) {
            String pluginName = AgenticObserveUtil.getOwningPluginName(c);
            if (pluginName == null) continue;
            String skillName = AgenticObserveUtil.getPluginTagValue(c, Constants.AKTO_SKILL_TAG);
            if (StringUtils.isNotBlank(skillName)) skillNameToOwningPlugin.put(skillName, pluginName);
        }

        for (ApiCollection c : collections) {
            if (c.isDeactivated()) continue;
            String hostName = c.getHostName();
            if (StringUtils.isBlank(hostName)) continue;

            List<CollectionTags> envType = c.getEnvType();
            String idStr = String.valueOf(c.getId());
            Integer trafficVal = traffic.get(idStr);
            // Falls back to the collection's own creation time when it has no directly-attributed
            // traffic entry (e.g. an MCP server/LLM collection only ever discovered via a tag/config
            // scan) — otherwise maxTrafficTimestamp stays 0 and the group gets silently dropped by
            // any date-range filter narrower than "All time" in fetchAgenticAssetsStats/Summary, even
            // though the asset genuinely exists. Mirrors constants.js's lastSeenOrStartTs (dead code
            // for this rebuilt page, but the same real bug it was written to fix).
            int collTraffic = trafficVal != null ? trafficVal : c.getStartTs();
            Double riskVal = risk.get(idStr);
            double collRisk = riskVal != null ? riskVal : 0.0;
            List<String> sensitive = sensitiveMap != null ? sensitiveMap.get(idStr) : null;
            String deviceId = AgenticObserveUtil.extractEndpointId(hostName);

            // Skill fan-out — a collection with multiple skills is a member of multiple skill
            // groups, independent of its agent/service/llm classification below. Counted even for a
            // "not-attached" hostname (an orphan skill bucket, not a real agent/service/llm) —
            // matches constants.js's groupCollectionsBySkill, which never checks
            // isNotAttachedHostName, unlike its Agent/Service/LLM siblings below.
            boolean fannedOutSkillFromArray = false;
            if (c.getSkills() != null) {
                for (String skill : c.getSkills()) {
                    if (StringUtils.isBlank(skill)) continue;
                    fannedOutSkillFromArray = true;
                    GroupSummary g = groups.computeIfAbsent("skill|" + skill, k -> {
                        GroupSummary gs = new GroupSummary(skill, "skill");
                        gs.name = skill;
                        gs.clientType = AgenticObserveUtil.CLIENT_TYPE_SKILL;
                        return gs;
                    });
                    g.accumulateCheap(c, hostName, envType, collRisk, collTraffic, deviceId, sensitive);
                    // Same skill name can fan out from both a plugin's own collection and the shared
                    // agent collection (which never carries plugin-name) — first non-null wins, so
                    // whichever member collection is actually plugin-tagged sets it, unambiguous per
                    // collection even though the group itself spans several. Falls back to the
                    // skillNameToOwningPlugin pre-pass since a plugin-bundled skill's own collection
                    // (tagPluginSkill's isolation fix) never has a populated .skills array itself — only
                    // its "skill" tag names it, so this collection (c) is never the one contributing it.
                    if (g.owningPluginName == null) g.owningPluginName = AgenticObserveUtil.getOwningPluginName(c);
                    if (g.owningPluginName == null) g.owningPluginName = skillNameToOwningPlugin.get(skill);
                }
            }
            // Standalone skill collections (no owning agent's .skills array lists them — the
            // collection IS the skill, named by its own "skill" tag(s) rather than a member of some
            // other collection's .skills array) never fan out above since .skills is empty/null on
            // them. Without this fallback they fall through to the agent/service classification
            // below, find no mcp-client/ai-agent/mcp-server/gen-ai/browser-llm tag, and get dropped
            // by the `if (typeTag == null) continue;` a few lines down — silently excluded from every
            // group despite being tagged AKTO_SKILL_TAG in the DB. Only applies when the array fan-out
            // above found nothing, so a collection already counted via .skills isn't double-counted.
            // A single collection can carry MULTIPLE "skill" tags (one per skill the owning
            // user/agent has created) — unlike getOwningPluginName's single-value lookup, this must
            // walk every tag, not just the first, or every skill past the first on that collection
            // silently disappears from the count.
            if (!fannedOutSkillFromArray && envType != null) {
                for (CollectionTags tag : envType) {
                    if (tag == null || !Constants.AKTO_SKILL_TAG.equals(tag.getKeyName()) || StringUtils.isBlank(tag.getValue())) {
                        continue;
                    }
                    String ownSkillTag = tag.getValue();
                    GroupSummary g = groups.computeIfAbsent("skill|" + ownSkillTag, k -> {
                        GroupSummary gs = new GroupSummary(ownSkillTag, "skill");
                        gs.name = ownSkillTag;
                        gs.clientType = AgenticObserveUtil.CLIENT_TYPE_SKILL;
                        return gs;
                    });
                    g.accumulateCheap(c, hostName, envType, collRisk, collTraffic, deviceId, sensitive);
                    if (g.owningPluginName == null) g.owningPluginName = AgenticObserveUtil.getOwningPluginName(c);
                    if (g.owningPluginName == null) g.owningPluginName = skillNameToOwningPlugin.get(ownSkillTag);
                }
            }

            // Plugins get their own collection (<device>.claude-plugin.<name>), like MCP servers — so
            // this is one group per collection, and the collection belongs to no other group. The
            // `continue` stops it also becoming an agent/service row off its own mcp-client/ai-agent
            // tags, which name the agent the plugin is installed on.
            String pluginName = AgenticObserveUtil.getPluginName(c);
            if (pluginName != null) {
                final ApiCollection pc = c;
                CollectionTags ownerTag = AgenticObserveUtil.findAssetTag(pc);
                // Canonicalized to match the agent group's own key below (raw tag values like
                // "claude"/"claudecli"/"claude-cli-user" all collapse to the same canonical agent).
                String ownerKey = ownerTag != null ? McpClientRegistry.resolveClientKey(ownerTag.getValue()) : null;
                // Compound identity: the SAME plugin name installed under two different agents (e.g.
                // "figma" on both claude and copilot) are genuinely different installs — bare
                // pluginName alone used to collapse them into one merged/wrong asset. ingestPlugin
                // always stamps ai-agent/mcp-client on a plugin's own collection, so ownerKey should
                // never actually be null in practice; the collection id fallback (always real, never
                // a placeholder string) only matters for malformed/legacy data missing that tag.
                String pluginKey = pluginName + "|" + (ownerKey != null ? ownerKey : String.valueOf(c.getId()));
                GroupSummary g = groups.computeIfAbsent("plugin|" + pluginKey, k -> {
                    GroupSummary gs = new GroupSummary(pluginKey, "plugin");
                    gs.name = pluginName;
                    gs.clientType = AgenticObserveUtil.CLIENT_TYPE_PLUGIN;
                    gs.pluginVersion = AgenticObserveUtil.getPluginTagValue(pc, Constants.AKTO_PLUGIN_VERSION_TAG);
                    gs.pluginScope = AgenticObserveUtil.getPluginTagValue(pc, Constants.AKTO_PLUGIN_SCOPE_TAG);
                    gs.pluginStatus = AgenticObserveUtil.getPluginTagValue(pc, Constants.AKTO_PLUGIN_STATUS_TAG);
                    gs.pluginMarketplace = AgenticObserveUtil.getPluginTagValue(pc, Constants.AKTO_PLUGIN_MARKETPLACE_TAG);
                    gs.pluginParentAgent = ownerKey != null ? McpClientRegistry.formatDisplayName(ownerKey) : null;
                    return gs;
                });
                g.accumulateCheap(c, hostName, envType, collRisk, collTraffic, deviceId, sensitive);
                // Reverse link: the agent's own group learns it has this plugin too (mirrors how an
                // agent group already learns its own serviceNames in accumulateCheap above) — this is
                // what lets the flyout's Components tab list a Plugin row under its parent AI Agent,
                // the same way it already lists that agent's MCP servers. Stores the compound key
                // (not bare pluginName) since fetchAgenticComponentsPage's "Installed plugins" row
                // round-trips this straight into fetchAgenticAssetDetail's groupKey lookup.
                if (ownerKey != null) {
                    GroupSummary agentGroup = groups.computeIfAbsent("agent|" + ownerKey, k -> {
                        GroupSummary gs = new GroupSummary(ownerKey, "agent");
                        gs.name = McpClientRegistry.formatDisplayName(ownerKey);
                        gs.clientType = McpClientRegistry.getAgentTypeFromValue(ownerKey);
                        return gs;
                    });
                    agentGroup.pluginNames.add(pluginKey);
                }

                if (hasTagKey(envType, Constants.AKTO_MCP_SERVER_TAG)) {
                    String serverGroupName = extractServiceNameForGrouping(hostName);
                    if (StringUtils.isNotBlank(serverGroupName)) {
                        GroupSummary sg = groups.computeIfAbsent("service|" + serverGroupName, k -> {
                            GroupSummary gs = new GroupSummary(serverGroupName, "service");
                            gs.name = serverGroupName;
                            gs.clientType = AgenticObserveUtil.CLIENT_TYPE_MCP_SERVER;
                            return gs;
                        });
                        sg.accumulateCheap(c, hostName, envType, collRisk, collTraffic, deviceId, sensitive);
                        if (sg.owningPluginName == null) sg.owningPluginName = pluginName;
                    }
                }
                continue;
            }

            if (hostName.contains(AgenticObserveUtil.NOT_ATTACHED_VALUE)) continue; // orphan bucket — not a real agent/service/llm

            boolean hasBrowserLlm = hasTagKey(envType, Constants.AKTO_BROWSER_LLM_TAG);
            if (hasBrowserLlm) {
                String serviceName = llmGroupKey(c);
                if (serviceName != null) {
                    GroupSummary g = groups.computeIfAbsent("llm|" + serviceName, k -> {
                        GroupSummary gs = new GroupSummary(serviceName, "llm");
                        gs.name = serviceName;
                        gs.clientType = AgenticObserveUtil.CLIENT_TYPE_LLM;
                        return gs;
                    });
                    g.accumulateCheap(c, hostName, envType, collRisk, collTraffic, deviceId, sensitive);
                    if (g.owningPluginName == null) g.owningPluginName = AgenticObserveUtil.getOwningPluginName(c);
                }
                continue; // matches groupCollectionsByAgent/Service's browser-llm skip
            }

            CollectionTags assetTag = AgenticObserveUtil.findAssetTag(c);
            boolean addedToAgentGroup = false;
            String key = agentGroupKey(c);
            if (key != null) {
                boolean isSaasAgent = hasTagKey(envType, Constants.AKTO_SAAS_AGENT_TAG);
                GroupSummary g = groups.computeIfAbsent("agent|" + key, k -> {
                    GroupSummary gs = new GroupSummary(key, "agent");
                    gs.name = McpClientRegistry.formatDisplayName(key);
                    gs.clientType = isSaasAgent ? AgenticObserveUtil.CLIENT_TYPE_SAAS_AGENT : McpClientRegistry.getAgentTypeFromValue(key);
                    return gs;
                });
                g.tagKey = assetTag.getKeyName();
                g.rawTagValues.add(assetTag.getValue());
                g.accumulateCheap(c, hostName, envType, collRisk, collTraffic, deviceId, sensitive);
                addedToAgentGroup = true;
            }

            String serviceName = serviceGroupKey(c, addedToAgentGroup);
            if (serviceName == null) continue;
            final ApiCollection fc = c;
            GroupSummary g = groups.computeIfAbsent("service|" + serviceName, k -> {
                GroupSummary gs = new GroupSummary(serviceName, "service");
                gs.name = serviceName;
                gs.clientType = AgenticObserveUtil.getTypeFromCollection(fc);
                return gs;
            });
            g.accumulateCheap(c, hostName, envType, collRisk, collTraffic, deviceId, sensitive);
            if (g.owningPluginName == null) g.owningPluginName = AgenticObserveUtil.getOwningPluginName(c);
        }

        // Matches constants.js's agentGroupKeys/servicesToShow filter — ONLY service groups (not
        // llm/skill) are excluded when their groupKey collides with a canonical agent group's key.
        Set<String> agentGroupKeys = new HashSet<>();
        for (GroupSummary g : groups.values()) {
            if ("agent".equals(g.rowType)) agentGroupKeys.add(g.groupKey);
        }
        groups.values().removeIf(g -> "service".equals(g.rowType) && agentGroupKeys.contains(g.groupKey));

        return groups;
    }
}

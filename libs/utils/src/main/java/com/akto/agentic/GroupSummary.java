package com.akto.agentic;

import static com.akto.agentic.AgenticAssetGrouping.extractServiceNameForGrouping;
import static com.akto.agentic.AgenticAssetGrouping.isConnectorIngested;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.akto.dto.ApiCollection;
import com.akto.dto.traffic.CollectionTags;
import com.akto.util.AgenticObserveUtil;
import com.akto.util.McpClientRegistry;
import com.mongodb.BasicDBObject;

// Cheap per-group accumulator, built for EVERY group (agent/service/llm/skill — ~800 at real
// scale) in one pass over all collections. Deliberately does NOT build a per-device breakdown —
// that's the expensive part (needs a Map<deviceId, ...> with its own Set<String> per device),
// and only happens for the page actually being returned, in buildDevicesForGroup below.
public class GroupSummary {
    public final String groupKey;
    public final String rowType; // "agent" | "service" | "llm" | "skill"
    public String name;
    public String clientType;
    // Agent rows only — the specific tag key that identified this asset (e.g. "mcp-client"),
    // and the raw tag value variants that collapsed into this canonical group (e.g. "cursor",
    // "Cursor", "cursor-ide" all -> group "cursor"). Cheap/bounded (distinct tag-variant count,
    // not collection count) but only used by the legacy Endpoints.jsx page's row-click ->
    // Inventory-filter feature, so exposed via fetchAgenticAssetDetail (lazy, one asset at a
    // time) rather than toSummaryResponse() (every row of every page).
    public String tagKey;
    public final Set<String> rawTagValues = new HashSet<>();
    public final Set<String> hostNames = new HashSet<>();
    public final Set<String> endpointIds = new HashSet<>();
    // Atlas: device -> its latest traffic on this group.
    public final Map<String, Integer> endpointLastSeen = new HashMap<>();
    public final Set<String> misconfiguredEndpointIds = new HashSet<>();
    public final Set<String> skillNames = new HashSet<>();
    // Only the count is serialized per row, same as skillNames — full list comes from the detail call.
    public final Set<String> pluginNames = new HashSet<>();
    // Plugin rows only — read off the plugin collection's own tags.
    public String pluginVersion;
    public String pluginScope;
    public String pluginStatus;
    public String pluginMarketplace;
    // Plugin rows only — the agent this plugin is installed under (ai-agent/mcp-client tag
    // value), e.g. "claude". A plugin collection carries the SAME owner tag as its parent agent
    // collection, so this is how the UI answers "whose plugin is this" without a second query.
    public String pluginParentAgent;
    // Service/LLM/Skill rows — set when a member collection is tagged plugin-name by a plugin
    // that bundles it (AgenticObserveUtil.getOwningPluginName), first-non-null-wins.
    public String owningPluginName;
    // Small — bounded by the account's distinct sensitive-data type names (e.g. "Email",
    // "IP Address"), not by member-collection count, so safe to serialize eagerly unlike
    // hostNames/collectionIds/skillNames above. Mirrors constants.js's groupCollectionsByAgent's
    // (etc.) sensitiveTypes accumulation for the legacy Endpoints.jsx page's "Sensitive data"
    // column, which the original server-side rebuild of this class didn't carry over.
    public final Set<String> sensitiveTypes = new HashSet<>();
    // Agent rows only — the distinct services (MCP servers) this agent's own collections
    // represent, mirrors constants.js's mcpServers derivation exactly (extractServiceName over
    // non-connector-ingested collections, excluding the agent's own name).
    public final Set<String> serviceNames = new HashSet<>();
    // Agent rows only — serviceNames' own collection ids, so the flyout's MCP-server tools
    // drill-down (AgentMcpToolsView) can fetch a specific server's tools without needing the
    // account-wide agenticFlatData this rebuild deliberately doesn't send.
    public final Map<String, Set<Integer>> serviceCollectionIds = new HashMap<>();
    public final List<Integer> collectionIds = new ArrayList<>();
    // Atlas flyout: the collections that hold this tool's own observed tools.
    public final Set<Integer> capabilityCollectionIds = new LinkedHashSet<>();
    public final Set<String> personalEndpointIds = new HashSet<>();
    public double maxRiskScore = 0;
    public Double baseRiskScore;
    public String baseRiskScoreReason;
    public int maxTrafficTimestamp = 0;
    public int minStartTs = 0; // 0 == "no discovered time seen yet", same convention as EndpointGroup.startTs
    public boolean hasPersonalAccount = false;
    public boolean hasLocalMcpServer = false;
    public boolean hasMisconfiguredConfig = false;
    // A group is a rollup of many collections — e.g. the "cursor" agent group contains both
    // <device>.ai_agent.cursor (the agent client itself) and <device>.ai_agent.github (a
    // service it happened to call). Prefer the description on the collection whose hostname
    // service segment matches the group's own key (the canonical "self" collection) over any
    // unrelated member's; fall back to the first non-blank description seen otherwise.
    public String selfDescription;
    public String fallbackDescription;

    public String description() {
        return selfDescription != null ? selfDescription : fallbackDescription;
    }

    public GroupSummary(String groupKey, String rowType) {
        this.groupKey = groupKey;
        this.rowType = rowType;
    }

    // An MCP server's collections are all the server; an agent's own collection is the one named after it.
    private boolean holdsOwnTools(String hostName) {
        if ("service".equals(rowType)) return true;
        if (!"agent".equals(rowType)) return false;
        String serviceName = extractServiceNameForGrouping(hostName);
        return serviceName != null && McpClientRegistry.resolveClientKey(serviceName).equalsIgnoreCase(groupKey);
    }

    public void accumulateCheap(ApiCollection c, String hostName, List<CollectionTags> envType, double collRisk, int collTraffic, String deviceId, List<String> sensitive) {
        collectionIds.add(c.getId());
        if (holdsOwnTools(hostName)) capabilityCollectionIds.add(c.getId());
        hostNames.add(hostName);
        if (deviceId != null) {
            endpointIds.add(deviceId);
            endpointLastSeen.merge(deviceId, collTraffic, Math::max);
        }
        if (sensitive != null) {
            for (String s : sensitive) {
                if (StringUtils.isNotBlank(s)) sensitiveTypes.add(s);
            }
        }
        if (c.getSkills() != null) {
            for (String s : c.getSkills()) {
                if (StringUtils.isNotBlank(s)) skillNames.add(s);
            }
        }
        String cPluginName = AgenticObserveUtil.getPluginName(c);
        if (cPluginName != null) pluginNames.add(cPluginName);
        if (StringUtils.isNotBlank(c.getDescription())) {
            if (fallbackDescription == null) fallbackDescription = c.getDescription();
            String cServiceName = extractServiceNameForGrouping(hostName);
            if (selfDescription == null && cServiceName != null && cServiceName.equalsIgnoreCase(groupKey)) {
                selfDescription = c.getDescription();
            }
        }
        if ("agent".equals(rowType) && !isConnectorIngested(envType)) {
            String serviceName = extractServiceNameForGrouping(hostName);
            // groupKey is the canonical registry key (e.g. "claude2"), but serviceName is the raw
            // hostname segment (e.g. "claudecli"/"claude-cli-project") — comparing them directly
            // never matches for any agent whose raw tag value differs from its canonical key, so
            // the agent's own identity collection was being added as if it were one of its own
            // linked MCP servers. Canonicalize serviceName the same way groupKey was derived.
            if (serviceName != null && !McpClientRegistry.resolveClientKey(serviceName).equalsIgnoreCase(groupKey)) {
                serviceNames.add(serviceName);
                serviceCollectionIds.computeIfAbsent(serviceName, k -> new HashSet<>()).add(c.getId());
            }
        }
        if (envType != null) {
            for (CollectionTags tag : envType) {
                if (tag == null) continue;
                String key = tag.getKeyName();
                String value = tag.getValue();
                if (("browser-llm-account-type".equals(key) || "login-user-email-type".equals(key)) && "personal".equals(value)) {
                    hasPersonalAccount = true;
                    if (deviceId != null) personalEndpointIds.add(deviceId);
                }
                if ("local-mcp-server".equals(key)) hasLocalMcpServer = true;
                if ("misconfigured-config".equals(key) && "true".equals(value)) {
                    hasMisconfiguredConfig = true;
                    if (deviceId != null) misconfiguredEndpointIds.add(deviceId);
                }
            }
        }
        if (collRisk > maxRiskScore) {
            maxRiskScore = collRisk;
            baseRiskScore = c.getBaseRiskScore();
            baseRiskScoreReason = c.getBaseRiskScoreReason();
        }
        if (collTraffic > maxTrafficTimestamp) maxTrafficTimestamp = collTraffic;
        int cStartTs = c.getStartTs();
        if (cStartTs > 0 && (minStartTs == 0 || cStartTs < minStartTs)) minStartTs = cStartTs;
    }

    // Deliberately excludes hostNames/collectionIds/skillNames/mcpServers/mcpServerCollectionIds
    // (all Set/List/Map fields whose size scales with member-collection count — up to ~25,272 for
    // one agent group on the Atlas Scale Test account) and tagKey/rawTagValues (dead for this
    // page — only a different page/data source reads them). A single page of 50 rows including
    // these measured at 16MB; none of them are read by the main grid's own rendering (confirmed
    // by tracing every consumer), only by the flyout when a user opens ONE specific asset — see
    // fetchAgenticAssetDetail, the lazy per-asset endpoint the flyout now calls on open instead.
    // The two fields the grid's OWN rendering used to derive from collectionIds client-side
    // (violations, isMalicious) are precomputed server-side instead, in fetchAgenticAssetsSummary
    // itself, using the same in-memory collectionIds this method no longer serializes.
    public BasicDBObject toSummaryResponse() {
        BasicDBObject g = new BasicDBObject();
        g.put("id", rowType + "-" + groupKey);
        g.put("groupKey", groupKey);
        g.put("name", name);
        g.put("rowType", rowType);
        g.put("clientType", clientType);
        g.put("endpointsCount", endpointIds.size());
        g.put("skillCount", skillNames.size());
        g.put("pluginCount", pluginNames.size());
        if (pluginVersion != null) g.put("pluginVersion", pluginVersion);
        if (pluginStatus != null) g.put("pluginStatus", pluginStatus);
        if (pluginMarketplace != null) g.put("pluginMarketplace", pluginMarketplace);
        if (pluginParentAgent != null) g.put("pluginParentAgent", pluginParentAgent);
        if (owningPluginName != null) g.put("owningPluginName", owningPluginName);
        g.put("riskScore", maxRiskScore > 0 ? AgenticObserveUtil.roundRiskScore(maxRiskScore) : null);
        if (baseRiskScoreReason != null) {
            g.put("baseRiskScore", baseRiskScore);
            g.put("baseRiskScoreReason", baseRiskScoreReason);
        }
        g.put("lastSeenEpoch", maxTrafficTimestamp);
        g.put("discoveredAt", minStartTs);
        g.put("hasPersonalAccount", hasPersonalAccount);
        g.put("hasLocalMcpServer", hasLocalMcpServer);
        g.put("hasMisconfiguredConfig", hasMisconfiguredConfig);
        g.put("misconfiguredDeviceCount", misconfiguredEndpointIds.size());
        if (!sensitiveTypes.isEmpty()) g.put("sensitiveInRespTypes", new ArrayList<>(sensitiveTypes));
        if (StringUtils.isNotBlank(description())) g.put("description", description());
        return g;
    }
}

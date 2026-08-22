package com.akto.util;

import com.akto.dto.ApiCollection;
import com.akto.dto.traffic.CollectionTags;
import com.akto.mcp.McpRequestResponseUtils;
import com.akto.util.enums.GlobalEnums;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Helpers to map agentic inventory collections and endpoint-shield modules
 * into the shapes expected by the agentic observe UI.
 */
public final class AgenticObserveUtil {

    private AgenticObserveUtil() {}

    public static final String CLIENT_TYPE_LLM = "LLM";
    public static final String CLIENT_TYPE_AI_AGENT = "AI Agent";
    public static final String CLIENT_TYPE_MCP_SERVER = "MCP Server";
    public static final String CLIENT_TYPE_SKILL = "Skill";
    public static final String CLIENT_TYPE_PLUGIN = "Plugin";
    public static final String CLIENT_TYPE_SAAS_AGENT = "SaaS Agent";

    private static final Set<String> MCP_AGENT_KEYWORDS = new HashSet<>(Arrays.asList(
            "stripe", "aws", "azure", "playwright", "postgres", "atlassian", "docker",
            "filesystem", "universal"
    ));

    public static String extractEndpointId(String hostName) {
        if (StringUtils.isBlank(hostName)) {
            return null;
        }
        // Equivalent to hostName.split("\\.")[0] but without the regex-Pattern-compile-per-call cost
        // String.split() carries — matters for any caller running this per-collection at real
        // account scale (tens of thousands of collections).
        int dotIdx = hostName.indexOf('.');
        return dotIdx >= 0 ? hostName.substring(0, dotIdx) : hostName;
    }

    public static String extractServiceName(String hostName) {
        return McpRequestResponseUtils.extractServiceNameFromHost(hostName);
    }

    public static String toChildPathKey(String serviceName) {
        if (StringUtils.isBlank(serviceName)) {
            return "unknown";
        }
        return serviceName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

    public static String formatDisplayName(String raw) {
        if (StringUtils.isBlank(raw)) {
            return "Unknown";
        }
        String[] parts = raw.split("[-_\\s]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            if ("cli".equalsIgnoreCase(part) || "mcp".equalsIgnoreCase(part)) {
                sb.append(part.toUpperCase(Locale.ROOT));
            } else {
                sb.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
                if (part.length() > 1) {
                    sb.append(part.substring(1).toLowerCase(Locale.ROOT));
                }
            }
        }
        return sb.length() > 0 ? sb.toString() : raw;
    }

    public static Set<String> getSkillNames(ApiCollection collection) {
        Set<String> skills = new HashSet<>();
        if (collection == null) {
            return skills;
        }
        if (collection.getSkills() != null) {
            for (String s : collection.getSkills()) {
                if (StringUtils.isNotBlank(s)) {
                    skills.add(s);
                }
            }
        }
        return skills;
    }

    public static int skillCount(ApiCollection collection) {
        return getSkillNames(collection).size();
    }

    // Plugins get their own collection, named <device>.<agentName>.<pluginName> — the same shape an
    // MCP server uses, so the agent-plugin tag is what tells them apart.
    public static boolean isPluginCollection(ApiCollection collection) {
        return collection != null && hasTagKey(collection.getEnvType(), Constants.AKTO_AGENT_PLUGIN_TAG);
    }

    public static boolean hasSaasAgentTag(ApiCollection collection) {
        return collection != null && hasTagKey(collection.getEnvType(), Constants.AKTO_SAAS_AGENT_TAG);
    }

    // The plugin's own name, from its plugin-name tag; falls back to the hostname's trailing segment.
    public static String getPluginName(ApiCollection collection) {
        if (!isPluginCollection(collection)) return null;
        String tagged = getTagValue(collection.getEnvType(), Constants.AKTO_PLUGIN_NAME_TAG);
        if (StringUtils.isNotBlank(tagged)) return tagged;
        return extractServiceName(collection.getHostName());
    }

    // Plugin metadata (version/scope/status/marketplace) lives on the collection's own tags.
    public static String getPluginTagValue(ApiCollection collection, String tagKey) {
        return collection != null ? getTagValue(collection.getEnvType(), tagKey) : null;
    }

    // Reverse direction — does this collection belong to a plugin?
    public static String getOwningPluginName(ApiCollection collection) {
        return collection != null ? getTagValue(collection.getEnvType(), Constants.AKTO_PLUGIN_NAME_TAG) : null;
    }

    private static boolean hasTagKey(List<CollectionTags> envType, String keyName) {
        if (envType == null) return false;
        for (CollectionTags tag : envType) {
            if (tag != null && keyName.equals(tag.getKeyName())) return true;
        }
        return false;
    }

    private static String getTagValue(List<CollectionTags> envType, String keyName) {
        if (envType == null) return null;
        for (CollectionTags tag : envType) {
            if (tag != null && keyName.equals(tag.getKeyName()) && StringUtils.isNotBlank(tag.getValue())) {
                return tag.getValue();
            }
        }
        return null;
    }

    public static void mergeViolations(Map<GlobalEnums.Severity, Integer> target, Map<String, Integer> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Integer> e : source.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) continue;
            GlobalEnums.Severity sev = parseSeverity(e.getKey());
            if (sev == null) continue;
            target.put(sev, target.getOrDefault(sev, 0) + e.getValue());
        }
    }

    public static Map<GlobalEnums.Severity, Integer> emptyViolations() {
        Map<GlobalEnums.Severity, Integer> v = new LinkedHashMap<>();
        for (GlobalEnums.Severity s : GlobalEnums.Severity.values()) {
            if (s == GlobalEnums.Severity.INFO) continue;
            v.put(s, 0);
        }
        return v;
    }

    public static GlobalEnums.Severity parseSeverity(String raw) {
        if (raw == null) return null;
        try {
            return GlobalEnums.Severity.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            String s = raw.toLowerCase(Locale.ROOT);
            if (s.contains("crit")) return GlobalEnums.Severity.CRITICAL;
            if (s.contains("high")) return GlobalEnums.Severity.HIGH;
            if (s.contains("med")) return GlobalEnums.Severity.MEDIUM;
            if (s.contains("low")) return GlobalEnums.Severity.LOW;
            return null;
        }
    }

    public static double roundRiskScore(double score) {
        return Math.round(score * 10.0) / 10.0;
    }

    public static final String NOT_ATTACHED_VALUE = "not-attached";

    public static CollectionTags findAssetTag(ApiCollection collection) {
        List<CollectionTags> envType = collection != null ? collection.getEnvType() : null;
        if (envType == null) {
            return null;
        }
        for (CollectionTags tag : envType) {
            if (tag == null || StringUtils.isBlank(tag.getKeyName())) {
                continue;
            }
            String key = tag.getKeyName();
            // Matches mcpClientHelper.js's findAssetTag — a tag present with value "not-attached"
            // means the asset-owner slot exists but isn't actually bound to anything yet, so it
            // must not be treated as agent-owned.
            if ((Constants.AKTO_MCP_CLIENT_TAG.equals(key) || Constants.AKTO_AI_AGENT_TAG.equals(key) || Constants.AKTO_BROWSER_LLM_AGENT_TAG.equals(key))
                    && !NOT_ATTACHED_VALUE.equals(tag.getValue())) {
                return tag;
            }
        }
        return null;
    }

    public static CollectionTags findTypeTag(ApiCollection collection) {
        List<CollectionTags> envType = collection != null ? collection.getEnvType() : null;
        if (envType == null) {
            return null;
        }
        // mcp-server takes priority regardless of tag insertion order — a collection explicitly
        // tagged as an MCP server (e.g. one that also carries gen-ai from generic LLM-traffic
        // detection) must classify as "MCP Server", not get swallowed into gen-ai/agent grouping
        // just because gen-ai happened to be tagged first.
        CollectionTags fallback = null;
        for (CollectionTags tag : envType) {
            if (tag == null || StringUtils.isBlank(tag.getKeyName())) {
                continue;
            }
            String key = tag.getKeyName();
            if (Constants.AKTO_MCP_SERVER_TAG.equals(key)) {
                return tag;
            }
            if (fallback == null && (Constants.AKTO_GEN_AI_TAG.equals(key) || Constants.AKTO_BROWSER_LLM_TAG.equals(key))) {
                fallback = tag;
            }
        }
        return fallback;
    }

    public static String getTypeFromCollection(ApiCollection collection) {
        // Checked before tags: a plugin collection also carries mcp-client/ai-agent (naming the agent
        // it belongs to), which would otherwise classify it as that agent.
        if (isPluginCollection(collection)) {
            return CLIENT_TYPE_PLUGIN;
        }
        if (hasSaasAgentTag(collection)) {
            return CLIENT_TYPE_SAAS_AGENT;
        }
        List<CollectionTags> envType = collection != null ? collection.getEnvType() : null;
        if (envType == null || envType.isEmpty()) {
            return CLIENT_TYPE_MCP_SERVER;
        }
        boolean hasSkill = false;
        boolean hasAiAgent = false;
        boolean hasMcpServer = false;
        for (CollectionTags tag : envType) {
            if (tag == null) {
                continue;
            }
            if (Constants.AKTO_SKILL_TAG.equals(tag.getKeyName())) {
                hasSkill = true;
            }
            // Matches mcpClientHelper.js's getTypeFromTags — a tag present with value "not-attached"
            // means the agent-owner slot exists but isn't actually bound, so it doesn't count as
            // "has an AI agent" (this is what lets a skill with only a not-attached ai-agent tag
            // still classify as Skill below, instead of falling through to MCP Server).
            if (Constants.AKTO_AI_AGENT_TAG.equals(tag.getKeyName()) && !NOT_ATTACHED_VALUE.equals(tag.getValue())) {
                hasAiAgent = true;
            }
            if (Constants.AKTO_MCP_SERVER_TAG.equals(tag.getKeyName())) {
                hasMcpServer = true;
            }
        }
        if (hasSkill && !hasAiAgent && !hasMcpServer) {
            return CLIENT_TYPE_SKILL;
        }
        for (CollectionTags tag : envType) {
            if (tag == null) {
                continue;
            }
            if (Constants.AKTO_MCP_SERVER_TAG.equals(tag.getKeyName())) {
                return CLIENT_TYPE_MCP_SERVER;
            }
            if (Constants.AKTO_GEN_AI_TAG.equals(tag.getKeyName())) {
                return CLIENT_TYPE_AI_AGENT;
            }
            if (Constants.AKTO_BROWSER_LLM_TAG.equals(tag.getKeyName())) {
                return CLIENT_TYPE_LLM;
            }
        }
        return CLIENT_TYPE_MCP_SERVER;
    }

    public static String getAgentTypeFromAssetValue(String tagValue) {
        if (StringUtils.isBlank(tagValue)) {
            return CLIENT_TYPE_AI_AGENT;
        }
        String lower = tagValue.toLowerCase(Locale.ROOT);
        for (String part : lower.split("[-_\\s]+")) {
            if (MCP_AGENT_KEYWORDS.contains(part)) {
                return CLIENT_TYPE_MCP_SERVER;
            }
        }
        for (String kw : MCP_AGENT_KEYWORDS) {
            if (lower.contains(kw)) {
                return CLIENT_TYPE_MCP_SERVER;
            }
        }
        return CLIENT_TYPE_AI_AGENT;
    }

    public static String getAssetTagValue(ApiCollection collection) {
        CollectionTags tag = findAssetTag(collection);
        return tag != null ? tag.getValue() : null;
    }

    public static boolean isInventoryCollection(ApiCollection collection) {
        return collection != null && !collection.isDeactivated();
    }

    public static List<String> splitUsernameToSlug(String username) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.isBlank(username)) {
            return parts;
        }
        for (String p : username.toLowerCase(Locale.ROOT).split("\\s+")) {
            if (!p.isEmpty()) {
                parts.add(p);
            }
        }
        return parts;
    }
}

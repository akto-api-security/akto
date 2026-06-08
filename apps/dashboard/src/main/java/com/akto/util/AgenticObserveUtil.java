package com.akto.util;

import com.akto.dto.ApiCollection;
import com.akto.dto.traffic.CollectionTags;
import com.akto.mcp.McpRequestResponseUtils;
import com.akto.util.Constants;
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

    private static final Set<String> MCP_AGENT_KEYWORDS = new HashSet<>(Arrays.asList(
            "stripe", "aws", "azure", "playwright", "postgres", "atlassian", "docker",
            "filesystem", "universal"
    ));

    public static String extractEndpointId(String hostName) {
        if (StringUtils.isBlank(hostName)) {
            return null;
        }
        String[] parts = hostName.split("\\.");
        return parts.length > 0 ? parts[0] : null;
    }

    public static String extractServiceName(String hostName) {
        return McpRequestResponseUtils.extractServiceNameFromHost(hostName);
    }


    private static boolean hasEnvTagValue(ApiCollection collection, String keyName, String value) {
        if (collection == null || collection.getEnvType() == null) {
            return false;
        }
        for (CollectionTags tag : collection.getEnvType()) {
            if (tag != null && keyName.equals(tag.getKeyName()) && value.equals(tag.getValue())) {
                return true;
            }
        }
        return false;
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
            if (Constants.AKTO_MCP_CLIENT_TAG.equals(key) || Constants.AKTO_AI_AGENT_TAG.equals(key) || Constants.AKTO_BROWSER_LLM_AGENT_TAG.equals(key)) {
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
        for (CollectionTags tag : envType) {
            if (tag == null || StringUtils.isBlank(tag.getKeyName())) {
                continue;
            }
            String key = tag.getKeyName();
            if (Constants.AKTO_MCP_SERVER_TAG.equals(key) || Constants.AKTO_GEN_AI_TAG.equals(key) || Constants.AKTO_BROWSER_LLM_TAG.equals(key)) {
                return tag;
            }
        }
        return null;
    }

    public static String getTypeFromCollection(ApiCollection collection) {
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
            if (Constants.AKTO_AI_AGENT_TAG.equals(tag.getKeyName())) {
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

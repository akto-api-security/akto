package com.akto.usage;

import java.util.Arrays;
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
import com.akto.util.Constants;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

/**
 * ENDPOINT_ASSET_COUNT for the usage cron: the largest number of distinct devices running a single
 * AI agent.
 *
 * Master computes this with the Agentic assets page's grouping (AgenticAssetGrouping.classifyAllGroups
 * plus McpClientRegistry). This branch runs only cron jobs, so it keeps just the rules that decide
 * which "AI Agent" groups exist and which devices belong to them: agent and service grouping keys,
 * agent-name aliases, agent types, and dropping service groups that collide with an agent. Skill,
 * plugin, LLM, risk, traffic and display logic is left out because it never changes the count.
 * Keep the tables below in sync with master's McpClientRegistry.
 */
public final class EndpointAssetCounter {

    private EndpointAssetCounter() {}

    static final String AI_AGENT = "AI Agent";
    private static final String LLM = "LLM";
    private static final String MCP_SERVER = "MCP Server";
    private static final String SKILL = "Skill";
    private static final String PLUGIN = "Plugin";
    private static final String SAAS_AGENT = "SaaS Agent";
    private static final String NOT_ATTACHED = "not-attached";

    // Agent tag value -> type, in master's registration order (the substring fallback depends on it).
    private static final Map<String, String> KNOWN_CLIENT_TYPES = new LinkedHashMap<>();
    // Raw tag value variant -> canonical agent key, so variants of one agent form one group.
    private static final Map<String, String> CLIENT_TAG_ALIASES = new HashMap<>();

    private static void register(String key, String agentType, String... variants) {
        KNOWN_CLIENT_TYPES.put(key, agentType);
        for (String v : variants) CLIENT_TAG_ALIASES.put(v, key);
    }

    static {
        register("claude", AI_AGENT);
        register("claude1", AI_AGENT, "claude-desktop", "claude-app");
        register("claude2", AI_AGENT, "claude", "claudecli", "claude-cli", "claude-cli-user", "claude-cli-project",
                "claude-cli-local", "claude-cli-enterprise", "claude-plugin", "claude-code");
        register("claude_cowork", AI_AGENT);
        register("claude3", AI_AGENT, "anthrophic.com");
        register("chatgpt", AI_AGENT);
        register("openai", AI_AGENT);
        register("gpt", LLM);
        register("codex", AI_AGENT);
        register("codex1", AI_AGENT, "codex-cli", "codexcli");
        register("codex2", AI_AGENT, "codex-desktop");
        register("gemini", LLM);
        register("geminicli", AI_AGENT, "geminicli", "gemini-cli", "gemini_cli");
        register("copilot", AI_AGENT);
        register("githubcopilot", AI_AGENT, "github-copilot");
        register("vscopilot", AI_AGENT, "visual-studio-copilot");
        register("cursor", AI_AGENT);
        register("grok", AI_AGENT);
        register("grokbot", AI_AGENT, "grok-bot");
        register("cody", AI_AGENT);
        register("windsurf", AI_AGENT);
        register("codeium", AI_AGENT);
        register("tabnine", AI_AGENT);
        register("github", AI_AGENT);
        register("githubcli", AI_AGENT, "github-cli");
        register("vscode", AI_AGENT);
        register("kirocli", AI_AGENT);
        register("kiroide", AI_AGENT);
        register("slack", AI_AGENT);
        register("notion", AI_AGENT);
        register("figma", AI_AGENT);
        register("stripe", MCP_SERVER);
        register("aws", MCP_SERVER);
        register("azure", MCP_SERVER);
        register("playwright", MCP_SERVER);
        register("postgres", MCP_SERVER);
        register("atlassian", MCP_SERVER);
        register("docker", MCP_SERVER);
        register("google", AI_AGENT);
        register("vs", AI_AGENT);
        register("antigravity", AI_AGENT);
        register("litellm", AI_AGENT);
        register("filesystem", MCP_SERVER);
        register("universal", MCP_SERVER);
    }

    // Hostnames like "vulnerable-mcp-kong.akto.io" are not device.agent.service, so they group whole.
    private static final Set<String> SERVICE_NAME_SUFFIX_TLDS = new HashSet<>(Arrays.asList(
            "io", "com", "net", "org", "cloud", "dev", "app", "ai", "co"));
    private static final Pattern DOT_SPLIT = Pattern.compile("\\.");
    private static final Pattern WORD_SPLIT = Pattern.compile("[-_\\s]+");

    private static final class Group {
        final String type;
        final Set<String> devices = new HashSet<>();
        Group(String type) {
            this.type = type;
        }
    }

    /** Reads every collection (id, host, tags, deactivated) and returns the count. */
    public static int compute() {
        List<ApiCollection> collections = ApiCollectionsDao.instance.findAll(
                Filters.empty(),
                Projections.include(ApiCollection.ID, ApiCollection.HOST_NAME, ApiCollection.TAGS_STRING, ApiCollection._DEACTIVATED));
        return maxDevicesPerAiAgent(collections);
    }

    static int maxDevicesPerAiAgent(List<ApiCollection> collections) {
        Map<String, Group> agents = new HashMap<>();
        Map<String, Group> services = new HashMap<>();

        for (ApiCollection c : collections) {
            if (c.isDeactivated()) continue;
            String hostName = c.getHostName();
            if (StringUtils.isBlank(hostName)) continue;
            List<CollectionTags> tags = c.getEnvType();
            String deviceId = extractDeviceId(hostName);

            // A plugin's collection is its own row. It still creates its owner agent's group (with no
            // device), and an mcp-server plugin adds its device to that server's service group.
            if (hasTagKey(tags, Constants.AKTO_AGENT_PLUGIN_TAG)) {
                CollectionTags ownerTag = findAssetTag(tags);
                String ownerKey = ownerTag != null ? resolveClientKey(ownerTag.getValue()) : null;
                if (ownerKey != null) {
                    agents.computeIfAbsent(ownerKey, k -> new Group(agentTypeOf(k)));
                }
                if (hasTagKey(tags, Constants.AKTO_MCP_SERVER_TAG)) {
                    String serverName = extractServiceName(hostName);
                    if (StringUtils.isNotBlank(serverName)) {
                        services.computeIfAbsent(serverName, k -> new Group(MCP_SERVER)).devices.add(deviceId);
                    }
                }
                continue;
            }

            if (hostName.contains(NOT_ATTACHED)) continue;
            if (hasTagKey(tags, Constants.AKTO_BROWSER_LLM_TAG)) continue;

            boolean inAgentGroup = false;
            CollectionTags assetTag = findAssetTag(tags);
            if (assetTag != null && StringUtils.isNotBlank(assetTag.getValue())
                    && !Constants.AKTO_BROWSER_LLM_AGENT_TAG.equals(assetTag.getKeyName())) {
                String key = resolveClientKey(assetTag.getValue());
                boolean saas = hasTagKey(tags, Constants.AKTO_SAAS_AGENT_TAG);
                agents.computeIfAbsent(key, k -> new Group(saas ? SAAS_AGENT : agentTypeOf(k))).devices.add(deviceId);
                inAgentGroup = true;
            }

            CollectionTags typeTag = findTypeTag(tags);
            if (typeTag == null) continue;
            if (Constants.AKTO_GEN_AI_TAG.equals(typeTag.getKeyName()) && (inAgentGroup || hasAgentOwnerTag(tags))) continue;
            String serviceName = extractServiceName(hostName);
            if (StringUtils.isBlank(serviceName)) continue;
            services.computeIfAbsent(serviceName, k -> new Group(serviceTypeOf(tags))).devices.add(deviceId);
        }

        // A service group whose key is also an agent group's key is not shown as its own row.
        services.keySet().removeAll(agents.keySet());

        int max = 0;
        for (Group g : agents.values()) {
            if (AI_AGENT.equals(g.type)) max = Math.max(max, g.devices.size());
        }
        for (Group g : services.values()) {
            if (AI_AGENT.equals(g.type)) max = Math.max(max, g.devices.size());
        }
        return max;
    }

    static String resolveClientKey(String rawValue) {
        if (rawValue == null) return null;
        String canonical = CLIENT_TAG_ALIASES.get(rawValue);
        return canonical != null ? canonical : rawValue;
    }

    // Exact word match first, then substring in registration order; unknown agents are AI agents.
    static String agentTypeOf(String tagValue) {
        if (tagValue == null || tagValue.isEmpty()) return AI_AGENT;
        String lower = tagValue.toLowerCase(Locale.ROOT);
        for (String part : WORD_SPLIT.split(lower)) {
            String type = KNOWN_CLIENT_TYPES.get(part);
            if (type != null) return type;
        }
        for (Map.Entry<String, String> e : KNOWN_CLIENT_TYPES.entrySet()) {
            if (lower.contains(e.getKey())) return e.getValue();
        }
        return AI_AGENT;
    }

    private static String serviceTypeOf(List<CollectionTags> tags) {
        if (hasTagKey(tags, Constants.AKTO_SAAS_AGENT_TAG)) return SAAS_AGENT;
        if (tags == null || tags.isEmpty()) return MCP_SERVER;
        boolean hasSkill = false;
        boolean hasAiAgent = false;
        boolean hasMcpServer = false;
        for (CollectionTags tag : tags) {
            if (tag == null) continue;
            if (Constants.AKTO_SKILL_TAG.equals(tag.getKeyName())) hasSkill = true;
            if (Constants.AKTO_AI_AGENT_TAG.equals(tag.getKeyName()) && !NOT_ATTACHED.equals(tag.getValue())) hasAiAgent = true;
            if (Constants.AKTO_MCP_SERVER_TAG.equals(tag.getKeyName())) hasMcpServer = true;
        }
        if (hasSkill && !hasAiAgent && !hasMcpServer) return SKILL;
        if (hasMcpServer && !hasAiAgent) return MCP_SERVER;
        boolean hasAiAgentCaller = false;
        for (CollectionTags tag : tags) {
            if (tag == null) continue;
            if (Constants.AKTO_MCP_SERVER_TAG.equals(tag.getKeyName())) return MCP_SERVER;
            if (Constants.AKTO_GEN_AI_TAG.equals(tag.getKeyName())) return AI_AGENT;
            if (Constants.AKTO_BROWSER_LLM_TAG.equals(tag.getKeyName())) return LLM;
            if (Constants.AKTO_AI_AGENT_CALLER_TAG.equals(tag.getKeyName())) hasAiAgentCaller = true;
        }
        return hasAiAgentCaller ? AI_AGENT : MCP_SERVER;
    }

    // mcp-server wins; then gen-ai or browser-llm; ai-agent-caller only when nothing stronger is present.
    private static CollectionTags findTypeTag(List<CollectionTags> tags) {
        if (tags == null) return null;
        CollectionTags fallback = null;
        CollectionTags callerFallback = null;
        for (CollectionTags tag : tags) {
            if (tag == null || StringUtils.isBlank(tag.getKeyName())) continue;
            String key = tag.getKeyName();
            if (Constants.AKTO_MCP_SERVER_TAG.equals(key)) return tag;
            if (fallback == null && (Constants.AKTO_GEN_AI_TAG.equals(key) || Constants.AKTO_BROWSER_LLM_TAG.equals(key))) fallback = tag;
            if (callerFallback == null && Constants.AKTO_AI_AGENT_CALLER_TAG.equals(key)) callerFallback = tag;
        }
        return fallback != null ? fallback : callerFallback;
    }

    // The tag naming the agent that owns a collection; a "not-attached" value is unbound.
    private static CollectionTags findAssetTag(List<CollectionTags> tags) {
        if (tags == null) return null;
        for (CollectionTags tag : tags) {
            if (tag == null || StringUtils.isBlank(tag.getKeyName())) continue;
            String key = tag.getKeyName();
            if ((Constants.AKTO_MCP_CLIENT_TAG.equals(key) || Constants.AKTO_AI_AGENT_TAG.equals(key)
                    || Constants.AKTO_BROWSER_LLM_AGENT_TAG.equals(key)) && !NOT_ATTACHED.equals(tag.getValue())) {
                return tag;
            }
        }
        return null;
    }

    private static boolean hasAgentOwnerTag(List<CollectionTags> tags) {
        if (tags == null) return false;
        for (CollectionTags tag : tags) {
            if (tag == null) continue;
            String key = tag.getKeyName();
            if (Constants.AKTO_MCP_CLIENT_TAG.equals(key) || Constants.AKTO_AI_AGENT_TAG.equals(key) || "agent-name".equals(key)) return true;
        }
        return false;
    }

    private static boolean hasTagKey(List<CollectionTags> tags, String keyName) {
        if (tags == null) return false;
        for (CollectionTags tag : tags) {
            if (tag != null && keyName.equals(tag.getKeyName())) return true;
        }
        return false;
    }

    // device.agent.service -> "service"; hostnames without that shape group as a whole.
    static String extractServiceName(String hostName) {
        if (StringUtils.isBlank(hostName)) return null;
        String[] parts = DOT_SPLIT.split(hostName);
        if (parts.length < 3) return hostName;
        if (SERVICE_NAME_SUFFIX_TLDS.contains(parts[2].toLowerCase(Locale.ROOT))) return hostName;
        StringBuilder sb = new StringBuilder(parts[2]);
        for (int i = 3; i < parts.length; i++) sb.append(".").append(parts[i]);
        return sb.toString();
    }

    // The device is the first label of the hostname.
    static String extractDeviceId(String hostName) {
        int dot = hostName.indexOf('.');
        return dot >= 0 ? hostName.substring(0, dot) : hostName;
    }
}

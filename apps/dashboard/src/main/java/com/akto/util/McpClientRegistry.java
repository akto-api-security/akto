package com.akto.util;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Java mirror of the frontend's mcpClientHelper.js KNOWN_CLIENTS/CLIENT_TAG_ALIASES tables — needed
 * so agent-type grouping (which display name/agentType a raw ai-agent tag value resolves to, and
 * which raw tag values collapse into the same canonical group, e.g. "claude"/"claude-cli"/"claudecli"
 * all becoming "Claude CLI") can run server-side. Keep this in sync with mcpClientHelper.js by hand —
 * there's no shared source of truth between the two runtimes.
 */
public final class McpClientRegistry {

    private McpClientRegistry() {}

    public static final class ClientInfo {
        public final String displayName;
        public final String agentType;
        public final String keyword; // the KNOWN_CLIENTS key that matched — needed to split the
                                      // original string into before/keyword/after for formatDisplayName

        ClientInfo(String displayName, String agentType, String keyword) {
            this.displayName = displayName;
            this.agentType = agentType;
            this.keyword = keyword;
        }
    }

    private static final class Entry {
        final String displayName;
        final String agentType;
        Entry(String displayName, String agentType) {
            this.displayName = displayName;
            this.agentType = agentType;
        }
    }

    private static final Map<String, Entry> KNOWN_CLIENTS = new LinkedHashMap<>();
    private static final Map<String, String> CLIENT_TAG_ALIASES = new LinkedHashMap<>();

    private static void register(String key, String displayName, String agentType, String... variants) {
        KNOWN_CLIENTS.put(key, new Entry(displayName, agentType));
        for (String v : variants) CLIENT_TAG_ALIASES.put(v, key);
    }

    static {
        String AI_AGENT = AgenticObserveUtil.CLIENT_TYPE_AI_AGENT;
        String LLM = AgenticObserveUtil.CLIENT_TYPE_LLM;
        String MCP_SERVER = AgenticObserveUtil.CLIENT_TYPE_MCP_SERVER;

        register("claude", "Claude", AI_AGENT);
        register("claude1", "Claude Desktop", AI_AGENT, "claude-desktop");
        register("claude2", "Claude CLI", AI_AGENT,
                "claude", "claudecli", "claude-cli", "claude-cli-user", "claude-cli-project",
                "claude-cli-local", "claude-cli-enterprise", "claude-plugin", "claude-code");
        register("claude_cowork", "Claude Cowork", AI_AGENT);
        // Tag as documented in the Tool Tags Mapping (Atlas) Notion doc — appears to be a typo for
        // "anthropic.com" upstream; kept verbatim since matching must be exact against the raw tag.
        register("claude3", "Claude Compliance", AI_AGENT, "anthrophic.com");
        register("chatgpt", "ChatGPT", AI_AGENT);
        register("openai", "OpenAI", AI_AGENT);
        register("gpt", "GPT", LLM);
        register("codex", "Codex", AI_AGENT);
        register("codex1", "Codex CLI", AI_AGENT, "codex-cli", "codexcli");
        register("codex2", "Codex Desktop", AI_AGENT, "codex-desktop");
        register("gemini", "Gemini", LLM);
        register("geminicli", "Gemini CLI", AI_AGENT, "geminicli", "gemini-cli", "gemini_cli");
        register("copilot", "Copilot", AI_AGENT);
        register("githubcopilot", "GitHub Copilot", AI_AGENT, "github-copilot");
        register("vscopilot", "Visual Studio Copilot", AI_AGENT, "visual-studio-copilot");
        register("cursor", "Cursor", AI_AGENT);
        register("grok", "Grok", AI_AGENT);
        register("cody", "Cody", AI_AGENT);
        register("windsurf", "Windsurf", AI_AGENT);
        register("codeium", "Codeium", AI_AGENT);
        register("tabnine", "Tabnine", AI_AGENT);
        register("github", "GitHub", AI_AGENT);
        register("githubcli", "GitHub CLI", AI_AGENT, "github-cli");
        register("vscode", "VS Code", AI_AGENT);
        register("kirocli", "Kiro CLI", AI_AGENT);
        register("kiroide", "Kiro IDE", AI_AGENT);
        register("slack", "Slack", AI_AGENT);
        register("notion", "Notion", AI_AGENT);
        register("figma", "Figma", AI_AGENT);
        register("stripe", "Stripe", MCP_SERVER);
        register("aws", "AWS", MCP_SERVER);
        register("azure", "Azure", MCP_SERVER);
        register("playwright", "Playwright", MCP_SERVER);
        register("postgres", "Postgres", MCP_SERVER);
        register("atlassian", "Atlassian", MCP_SERVER);
        register("docker", "Docker", MCP_SERVER);
        register("google", "Google", AI_AGENT);
        register("vs", "VS Code", AI_AGENT); // for VS Code
        register("antigravity", "Antigravity", AI_AGENT);
        register("litellm", "LiteLLM", AI_AGENT);
        register("filesystem", "Filesystem", MCP_SERVER);
        register("universal", "Universal", MCP_SERVER);
    }

    /** Canonical KNOWN_CLIENTS key for a raw wire-level tag value (identity if not an aliased variant). */
    public static String resolveClientKey(String rawValue) {
        if (rawValue == null) return null;
        String canonical = CLIENT_TAG_ALIASES.get(rawValue);
        return canonical != null ? canonical : rawValue;
    }

    private static final Pattern SPLIT_PATTERN = Pattern.compile("[-_\\s]+");

    /** Mirrors mcpClientHelper.js's findClientInfo: exact-word match first, then substring fallback. */
    public static ClientInfo findClientInfo(String tagValue) {
        if (tagValue == null || tagValue.isEmpty()) return null;
        String lower = tagValue.toLowerCase(Locale.ROOT);
        String[] parts = SPLIT_PATTERN.split(lower);
        for (String part : parts) {
            Entry e = KNOWN_CLIENTS.get(part);
            if (e != null) return new ClientInfo(e.displayName, e.agentType, part);
        }
        for (Map.Entry<String, Entry> kv : KNOWN_CLIENTS.entrySet()) {
            if (lower.contains(kv.getKey())) {
                return new ClientInfo(kv.getValue().displayName, kv.getValue().agentType, kv.getKey());
            }
        }
        return null;
    }

    private static String capitalizeWord(String w) {
        if (w.isEmpty()) return w;
        String lower = w.toLowerCase(Locale.ROOT);
        if (lower.equals("cli") || lower.equals("mcp")) return lower.toUpperCase(Locale.ROOT);
        return Character.toUpperCase(w.charAt(0)) + (w.length() > 1 ? lower.substring(1) : "");
    }

    private static String splitAndCapitalize(String s) {
        StringBuilder sb = new StringBuilder();
        for (String part : SPLIT_PATTERN.split(s)) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(capitalizeWord(part));
        }
        return sb.toString();
    }

    // Mirrors formatDisplayName's no-match fallback in mcpClientHelper.js, which (unlike the
    // matched-info before/after branches) doesn't filter empty tokens before joining — a leading or
    // trailing separator produces a stray leading/trailing space. Kept bit-for-bit rather than
    // "fixed" so this stays byte-for-byte interchangeable with the JS original it mirrors.
    private static String splitAndCapitalizeNoFilter(String s) {
        String[] parts = SPLIT_PATTERN.split(s);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(capitalizeWord(parts[i]));
        }
        return sb.toString();
    }

    /** Mirrors mcpClientHelper.js's formatDisplayName. */
    public static String formatDisplayName(String tagValue) {
        if (tagValue == null || tagValue.isEmpty()) return "Unknown";
        // Domain-style names (contain dots) are returned as-is to avoid mangling, e.g.
        // "mcp.notion.com" stays "mcp.notion.com".
        if (tagValue.contains(".")) return tagValue;
        ClientInfo info = findClientInfo(tagValue);
        if (info == null) return splitAndCapitalizeNoFilter(tagValue);
        String lower = tagValue.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf(info.keyword);
        String before = splitAndCapitalize(tagValue.substring(0, idx));
        String after = splitAndCapitalize(tagValue.substring(idx + info.keyword.length()));
        StringBuilder sb = new StringBuilder();
        if (!before.isEmpty()) sb.append(before).append(' ');
        sb.append(info.displayName);
        if (!after.isEmpty()) sb.append(' ').append(after);
        return sb.toString();
    }

    /** Mirrors mcpClientHelper.js's getAgentTypeFromValue. */
    public static String getAgentTypeFromValue(String tagValue) {
        ClientInfo info = findClientInfo(tagValue);
        return info != null && info.agentType != null ? info.agentType : AgenticObserveUtil.CLIENT_TYPE_AI_AGENT;
    }
}

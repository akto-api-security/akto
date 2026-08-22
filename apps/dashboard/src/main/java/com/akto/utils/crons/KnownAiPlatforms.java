package com.akto.utils.crons;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Curated keyword -> display name lookup for the closed set of ai-agent/mcp-client connector
 * identities Akto itself ships (apps/mcp-endpoint-shield/*, SentinelOneExecutor's endpoint scans),
 * plus a handful of well-known public MCP servers. Mirrors the frontend's KNOWN_CLIENTS map in
 * apps/dashboard/web/polaris_web/web/src/apps/dashboard/pages/observe/agentic/mcpClientHelper.js -
 * update both when adding a new connector or well-known server.
 */
public class KnownAiPlatforms {

    private static final Map<String, String> KNOWN_PLATFORMS = buildRegistry();

    private static Map<String, String> buildRegistry() {
        Map<String, String> m = new LinkedHashMap<>();
        // AI-agent / MCP-client connectors Akto ships (apps/mcp-endpoint-shield/*, SentinelOneExecutor scans)
        m.put("cursor", "Cursor");
        m.put("vscode", "VS Code");
        m.put("vs", "VS Code");
        m.put("copilot", "GitHub Copilot");
        m.put("copilotcli", "GitHub Copilot CLI");
        m.put("githubcopilot", "GitHub Copilot");
        m.put("claudecli", "Claude Code CLI");
        m.put("claudedesktop", "Claude Desktop");
        m.put("claude", "Claude");
        m.put("windsurf", "Windsurf");
        m.put("codeium", "Codeium");
        m.put("geminicli", "Gemini CLI");
        m.put("gemini", "Gemini");
        m.put("codexcli", "Codex CLI");
        m.put("codexdesktop", "Codex Desktop");
        m.put("codex", "Codex");
        m.put("opencode", "OpenCode");
        m.put("langchain", "LangChain");
        m.put("vertexaiadk", "Vertex AI ADK");
        m.put("neovim", "Neovim");
        m.put("hermes", "Hermes");
        m.put("githubcli", "GitHub CLI");
        m.put("github", "GitHub");
        m.put("antigravity", "Antigravity");
        m.put("microsoftvisualstudio", "Microsoft Visual Studio");
        m.put("kirocli", "Kiro CLI");
        m.put("chatgpt", "ChatGPT");
        m.put("grok", "Grok");
        // Well-known public MCP servers (subset of the frontend's KNOWN_CLIENTS MCP_SERVER entries)
        m.put("stripe", "Stripe");
        m.put("aws", "AWS");
        m.put("azure", "Azure");
        m.put("playwright", "Playwright");
        m.put("postgres", "Postgres");
        m.put("atlassian", "Atlassian");
        m.put("docker", "Docker");
        m.put("filesystem", "Filesystem");
        m.put("githubcopilot", "GitHub Copilot");
        m.put("razorpay", "Razorpay");
        return Collections.unmodifiableMap(m);
    }

    private KnownAiPlatforms() {
    }

    /**
     * Best-effort display name for a raw ai-agent/mcp-client tag value or MCP server-name segment.
     * Never returns null for a non-blank input - falls back to a light prettification (title-case,
     * "-"/"_" replaced with spaces) for values not yet in the curated map, since real accounts have
     * legitimate but not-yet-catalogued values (e.g. "kirocli", "browser-grok") that still deserve a
     * readable name rather than being silently dropped.
     */
    public static String displayName(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return null;
        }
        String exact = KNOWN_PLATFORMS.get(normalize(rawValue));
        if (exact != null) {
            return exact;
        }
        // Fuzzy: try each "-"/"_"/"."/":"/space-separated token against the map (mirrors the
        // frontend's findClientInfo in mcpClientHelper.js, plus "." for multi-segment MCP server
        // domains like "api.githubcopilot.com", and ":" for Ollama/Docker-style "name:tag" values
        // like "nomic-embed-text:latest"), so e.g. "browser-chatgpt" resolves via "chatgpt".
        for (String token : rawValue.toLowerCase(Locale.ROOT).split("[-_.:\\s]+")) {
            String match = KNOWN_PLATFORMS.get(normalize(token));
            if (match != null) {
                return match;
            }
        }
        return prettify(rawValue);
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[-_\\s]+", "");
    }

    private static String prettify(String rawValue) {
        String[] words = rawValue.replaceAll("[-_.:]+", " ").trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.length() > 0 ? sb.toString() : rawValue;
    }
}

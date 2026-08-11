package com.akto.action;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SettingsScanAction extends ActionSupport {

    private static final LoggerMaker logger = new LoggerMaker(SettingsScanAction.class, LogDb.DB_ABS);
    private static final Gson gson = new Gson();

    // Tool identifiers accepted in the `tool` field
    private static final String TOOL_CLAUDE = "claude";
    private static final String TOOL_CODEX = "codex";
    private static final String TOOL_CODEX_REQUIREMENTS = "codex_requirements";
    private static final String TOOL_COPILOT = "copilot";

    // ─── Shared base: rules, output schema, and message style used by every tool prompt ───
    private static final String BASE_SCAN_PROMPT = "You are a security analyst auditing an AI coding agent's config file for settings that weaken its permission model, sandbox, or approval flow. Use your own judgment: a field is a finding only if its actual value measurably increases what the agent can do without a human checking it, or exposes/reaches credentials and sensitive paths. The mere presence of a field, or a config that mentions credentials/paths/URLs, is NOT itself a finding — judge the value.\n" +
        "\n" +
        "What generally counts as risky (use judgment, this is not an exhaustive list):\n" +
        "- Anything that skips human approval before running commands or edits (e.g. an auto-approve / bypass-permissions / \"never ask\" mode).\n" +
        "- Anything that disables or weakens the sandbox, or lets the agent reach outside its workspace (filesystem paths like .ssh/.aws/.gnupg/.kube/.npmrc/.netrc/.., or unrestricted network egress, wildcard domains).\n" +
        "- Anything that turns off monitoring/validation entirely (e.g. disabling all hooks), as opposed to a tool simply having no hooks configured.\n" +
        "- An allowlist entry scoped to a destructive or exfil-capable command (rm, sudo, curl, wget, chmod, dd, eval) or a bare wildcard covering an entire tool.\n" +
        "What is normal and must NOT be flagged: ordinary command-scoped dev allowlists (git, npm, go, make, docker, etc. with wildcarded arguments only), bare tool-name grants, standard dev domains (npmjs.com, pypi.org, github.com, githubusercontent.com, docker.com), credential-manager/helper fields that store credentials safely, and any field whose value keeps a protection ON or keeps access restricted.\n" +
        "\n" +
        "OUTPUT FORMAT — read this even if you found multiple findings:\n" +
        "Return exactly ONE raw JSON array, starting with '[' and ending with ']', containing every finding as\n" +
        "an element of that SAME array. Never return more than one array. Never return separate\n" +
        "```json { ... } ``` blocks side by side, one per finding — that is a formatting error, not valid\n" +
        "output, even if each block is individually valid JSON. No prose before, between, or after the array.\n" +
        "No code fences around it. Found nothing? Return exactly: []\n" +
        "\n" +
        "Each finding: {\"severity\":\"LOW\"|\"MEDIUM\"|\"HIGH\"|\"CRITICAL\", \"category\":\"risky\"|\"malicious\", \"fieldPath\":\"...\", \"title\":\"...\", \"message\":\"...\", \"evidence\":\"...\", \"overview\":\"...\", \"remediation\":\"...\"}\n" +
        "\n" +
        "message: one direct sentence (add a second only if needed) naming the exact field and its actual value, then stating exactly what that value lets happen — no hedging, no abstraction.\n" +
        "  GOOD: \"permissions.defaultMode is set to bypassPermissions. This lets the agent run any command or edit any file with no user confirmation, so a prompt-injected instruction executes immediately instead of waiting for approval.\"\n" +
        "  GOOD: \"disableAllHooks is set to true. Every hook in this file stops running, including monitoring or security-validation hooks, so nothing is checking or logging what the agent does.\"\n" +
        "  BAD (never write like this): \"This field may pose a security risk if misconfigured.\" / \"could allow unauthorized execution.\" / \"can be quite risky.\"\n" +
        "\n" +
        "COMPLETENESS — do not stop after your first finding: after drafting your findings, re-scan the input\n" +
        "top to bottom one more time against every risk category above, independently of what you already\n" +
        "found. Config files routinely carry more than one issue at once (e.g. an unsafe approval mode AND a\n" +
        "disabled-hooks flag AND a broad allowlist entry, all in the same file) — finding one is not a signal\n" +
        "to stop, and every category above must be checked against this specific input regardless of how many\n" +
        "findings you already have.\n" +
        "evidence: the single offending \"key\":value pair exactly as it appears in the input — never the bare value, never a whole array or section.\n" +
        "overview: markdown, two \"## \" sections separated by a blank line — \"## What is this?\" (what the field controls and what this value enables, in plain terms) and \"## Why is it dangerous?\" (the concrete attack chain: how prompt injection, a malicious tool/skill, or a poisoned file abuses this specific value, ending with the impact).\n" +
        "remediation: markdown, a numbered list (1., 2., 3.) naming the exact field and safe value and how to enforce it, followed by a fenced corrected-config snippet. No other sections.\n" +
        "\n" +
        "GROUNDING — DO NOT HALLUCINATE: every fieldPath, evidence, and quoted value must be copied\n" +
        "character-for-character from the JSON below. Never invent or assume a field's value — if you\n" +
        "cannot point to its literal key/value in the input, do not report it. Before emitting a finding for\n" +
        "a boolean/enum field, reread what its actual value does per your own \"## What is this?\" text — if\n" +
        "that value keeps a protection ON or access restricted, it is safe and must not be a finding.";

    // Field scope below is grounded in OpenAI's own Codex security/config docs:
    // https://learn.chatgpt.com/docs/security-administration
    // https://learn.chatgpt.com/codex/config-file/config-reference
    // https://learn.chatgpt.com/codex/sandboxing
    // https://learn.chatgpt.com/codex/permissions
    private static final String CODEX_SCAN_PROMPT = BASE_SCAN_PROMPT + "\n\n" +
        "TOOL: OpenAI Codex CLI config.toml (given to you as TOML-derived JSON), per learn.chatgpt.com/docs/security-administration.\n" +
        "\n" +
        "SCOPE — sandbox, approval, and permissions fields ONLY: approval_policy (and its granular sub-flags),\n" +
        "approvals_reviewer, sandbox_mode, sandbox_workspace_write.*, default_permissions,\n" +
        "permissions.<name>.workspace_roots / .filesystem.*, permissions.<name>.network.* (including any\n" +
        "\"dangerously_*\" flag — OpenAI names them that deliberately, treat as a strong signal),\n" +
        "features.network_proxy.*. Everything else in this file is OUT OF SCOPE — never read, quote, or\n" +
        "reason about: projects (including trust_level — a folder's own name/path is never evidence of\n" +
        "anything), mcp_servers, apps, plugins, marketplaces, model_providers, otel, mcp_oauth_*,\n" +
        "cli_auth_credentials_store, notify, developer_instructions, model_instructions_file, compact_prompt,\n" +
        "experimental_compact_prompt_file, skills, notice, windows, tui, desktop, history, memories, hooks,\n" +
        "shell_environment_policy, allow_login_shell.\n" +
        "\n" +
        "Within scope, judge each field the same way as above: does its actual value skip approval, weaken\n" +
        "the sandbox, or let the agent read/write outside its workspace (credential paths, /etc, .., ~, /) or\n" +
        "reach the network unrestricted (wildcard domains, unix sockets, non-loopback proxy)? A safe default\n" +
        "(read-only / workspace-write sandbox, approvals required, no filesystem/network escape) is not a\n" +
        "finding.\n" +
        "EXCEPTION — filesystem paths inside an application bundle (contains \".app/\", including a relative\n" +
        "path like \"./Some Thing.app/...\") are never a finding: that proves it is the tool's own\n" +
        "vendor-shipped binary, not user or attacker controlled.\n" +
        "\n" +
        "NOW SCAN THE TOML-DERIVED JSON BELOW, restricted to the in-scope fields above.";

    private static final String CLAUDE_SETTINGS_SCAN_PROMPT = BASE_SCAN_PROMPT + "\n\n" +
        "TOOL: Claude Code settings.json.\n" +
        "\n" +
        "HARD EXCLUSIONS — never flag these:\n" +
        "- Any MCP allow/deny field: allowAllMcpServers, enabledMcpjsonServers, disabledMcpjsonServers,\n" +
        "  enableAllProjectMcpServers, mcpServers entries. No exceptions, at any value.\n" +
        "- statusLine and everything under it (statusLine.command, statusLine.type, etc). No exceptions, at\n" +
        "  any value.\n" +
        "- disableAllHooks when its value is false. Flag it when the value is true. Everything else under\n" +
        "  \"hooks\" — hook commands, hook prompts, hook events — skip entirely regardless of value.\n" +
        "- credentialHelper fields.\n" +
        "- Bare grants of read-only/inert tools in permissions.allow (\"Read\", \"Grep\", \"Glob\", \"Agent\",\n" +
        "  \"Skill\", MCP tool names, etc.) — these cannot change state, so listing them is normal, not a risk.\n" +
        "  A bare grant of \"Write\", \"Edit\", \"Bash\", \"NotebookEdit\", or \"WebFetch\" (no command/path scoping\n" +
        "  at all) IS a finding (HIGH / risky) — it lets every project loading this file write/execute/fetch\n" +
        "  anything with no per-command review, which is materially riskier than a read-only grant.\n" +
        "\n" +
        "NOW SCAN THE JSON BELOW.";

    private static final String CODEX_REQUIREMENTS_SCAN_PROMPT = BASE_SCAN_PROMPT + "\n\n" +
        "TOOL: OpenAI Codex CLI requirements.toml. This file is written by IT admins (via MDM or system file\n" +
        "placement) to CONSTRAIN what an end user's config.toml is allowed to set. Because of that, the risk\n" +
        "model here is INVERTED from a normal config: judge what restriction is MISSING, EMPTY, or TOO LOOSE,\n" +
        "not merely what is present. You may correctly report a finding even when the fieldPath itself is\n" +
        "absent from the input — the absence of a constraint (e.g. nothing restricting sandbox_mode or\n" +
        "approval_policy) is itself the finding. Only a claim of the form \"field is set to X\" still requires\n" +
        "X to be literally present in the input; a claim that a restriction is missing does not.\n" +
        "\n" +
        "DO NOT FLAG (ignore these completely):\n" +
        "- Any [[hooks.<Event>]] entry whose command references akto-, Akto's own hook wrapper scripts, or\n" +
        "  the managed_dir path set up by Akto's own installer — this is Akto's own instrumentation, not\n" +
        "  third-party interference. Any OTHER third-party hook injected here IS a finding (HIGH / malicious)\n" +
        "  — managed hooks in requirements.toml run with no user approval, so an unexpected one is a stronger\n" +
        "  signal than the same entry in config.toml.\n" +
        "- Absence of the file entirely — an unmanaged deployment is a config.toml-level concern, not this\n" +
        "  prompt's.\n" +
        "- Cosmetic/administrative metadata with no access-control meaning.\n" +
        "\n" +
        "Judge admin-level restrictions the same way: does this policy leave the door open for a user's\n" +
        "config.toml to set an unsafe approval mode, sandbox mode, filesystem/network access, or an\n" +
        "unrestricted MCP server / plugin marketplace source? A tight, present allowlist is not a finding.\n" +
        "\n" +
        "NOW SCAN THE TOML-DERIVED JSON BELOW.";

    private static final String COPILOT_SCAN_PROMPT = BASE_SCAN_PROMPT + "\n\n" +
        "TOOL: GitHub Copilot CLI settings.json.\n" +
        "\n" +
        "DO NOT FLAG (ignore these completely — cosmetic/benign, no access-control meaning):\n" +
        "theme, colorMode, banner, beep, mouse, scrollbar, screenReader, renderMarkdown, tabs.*, footer.*,\n" +
        "statusLine, showTipsOnStartup, updateTerminalTitle, terminalProgress, keepAlive, autoUpdate,\n" +
        "logLevel, compactPaste, copyOnSelect, respectGitignore, companyAnnouncements,\n" +
        "includeCoAuthoredBy, stream, streamerMode, model, effortLevel, toolSearch, ide.*, powershellFlags,\n" +
        "dynamicRetrieval, skillDirectories, disabledSkills, disabledMcpServers (a denylist is a restriction,\n" +
        "never risky), mergeStrategy, subagents.*, customAgents.defaultLocalOnly, builtInAgents.*, and all\n" +
        "hooks content (Akto's own instrumentation hooks live here).\n" +
        "\n" +
        "This tool has one absence-based exception: permissions.disableBypassPermissionsMode not being\n" +
        "present (or not set to \"disable\") means nothing suppresses --allow-all/bypass-permissions flags —\n" +
        "report that even though the field is absent (MEDIUM / risky). Every other finding must point to a\n" +
        "literal key/value in the input.\n" +
        "You are NOT given the org's approved URL/plugin/marketplace allowlist. Do not flag a normal https://\n" +
        "URL or a well-known vendor domain merely as \"not in the approved list\" — flag only an entry that is\n" +
        "itself suspicious (wildcard, raw IP, plaintext http://, typosquat, or an obviously untrusted source).\n" +
        "\n" +
        "NOW SCAN THE JSON BELOW.";

    // Input fields
    private String tool;
    private String cfgPath;
    private String settingsJson;

    // Output field
    private List<Map<String, Object>> findings;

    public String scanSettingsFile() {
        if (tool == null || tool.isEmpty()) {
            addActionError("tool is required");
            return Action.ERROR.toUpperCase();
        }
        if (settingsJson == null || settingsJson.isEmpty()) {
            addActionError("settingsJson is required");
            return Action.ERROR.toUpperCase();
        }

        String prompt = resolvePrompt(tool);
        if (prompt == null) {
            addActionError("Unknown tool: " + tool);
            return Action.ERROR.toUpperCase();
        }

        String fullPrompt = prompt + "\n\nSettings JSON to analyse:\n" + settingsJson;

        String rawContent;
        try {
            rawContent = callLLM(fullPrompt);
        } catch (Exception e) {
            logger.error("LLM call failed for tool=" + tool + ": " + e.getMessage());
            addActionError("LLM call failed: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }

        List<Map<String, Object>> parsed;
        try {
            String cleaned = extractJsonArray(rawContent);
            parsed = gson.fromJson(cleaned, new TypeToken<List<Map<String, Object>>>() {}.getType());
        } catch (Exception e) {
            logger.error("Failed to parse LLM response for tool=" + tool + ": " + rawContent);
            addActionError("Failed to parse LLM response");
            return Action.ERROR.toUpperCase();
        }

        List<Map<String, Object>> rawFindings = parsed != null ? parsed : new ArrayList<>();
        boolean disableAllHooksIsFalse = settingsJson != null
                && (settingsJson.contains("\"disableAllHooks\": false") || settingsJson.contains("\"disableAllHooks\":false"));
        findings = new ArrayList<>();
        for (Map<String, Object> finding : rawFindings) {
            if ("disableAllHooks".equals(finding.get("fieldPath")) && disableAllHooksIsFalse) {
                logger.info("[SettingsScan] Dropping disableAllHooks finding — settingsJson has disableAllHooks: false", LogDb.DB_ABS);
                continue;
            }
            findings.add(finding);
        }
        logger.info(String.format(
                "[SettingsScan] tool=%s cfgPath=%s findings=%d", tool, cfgPath, findings.size()), LogDb.DB_ABS);
        for (Map<String, Object> finding : findings) {
            logger.debug(String.format(
                    "[SettingsScan] tool=%s cfgPath=%s finding=%s", tool, cfgPath, gson.toJson(finding)), LogDb.DB_ABS);
        }
        return Action.SUCCESS.toUpperCase();
    }

    private String resolvePrompt(String toolName) {
        switch (toolName) {
            case TOOL_CLAUDE: return CLAUDE_SETTINGS_SCAN_PROMPT;
            case TOOL_CODEX: return CODEX_SCAN_PROMPT;
            case TOOL_CODEX_REQUIREMENTS: return CODEX_REQUIREMENTS_SCAN_PROMPT;
            case TOOL_COPILOT: return COPILOT_SCAN_PROMPT;
            default: return null;
        }
    }

    private String callLLM(String prompt) throws Exception {
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(userMessage);
        Map<String, Object> payload = new HashMap<>();
        payload.put("messages", messages);
        payload.put("temperature", 0);
        payload.put("max_tokens", 16000);

        Map<String, Object> llmResponse = LLMService.callLLM(payload);
        if (llmResponse == null) throw new RuntimeException("Empty LLM response");

        List<Map<String, Object>> choices = (List<Map<String, Object>>) llmResponse.get("choices");
        if (choices == null || choices.isEmpty()) throw new RuntimeException("No choices in LLM response");
        Map<String, Object> firstChoice = choices.get(0);
        Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
        if (message == null) throw new RuntimeException("No message in LLM response");
        Object content = message.get("content");
        if (content == null) throw new RuntimeException("No content in LLM message");
        return content.toString();
    }

    private static String extractJsonArray(String raw) {
        if (raw == null) return "[]";
        String s = raw.trim();
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            if (firstNewline != -1) s = s.substring(firstNewline + 1);
            if (s.endsWith("```")) s = s.substring(0, s.lastIndexOf("```"));
            s = s.trim();
        }
        int start = s.indexOf('[');
        int end = s.lastIndexOf(']');
        if (start != -1 && end != -1 && end > start) return s.substring(start, end + 1);
        return s;
    }
}

package com.akto.action;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private static final String CLAUDE_SETTINGS_SCAN_PROMPT = "You are a security analyst auditing a Claude Code settings.json file. Find EVERY security risk — do not skip any present field.\n" +
        "\n" +
        "DO NOT FLAG (ignore these completely):\n" +
        "- All hooks content — skip every hook type, hook command, hook prompt, hook event.\n" +
        "  Only flag disableAllHooks (see below). Do NOT analyze any hook field.\n" +
        "- credentialHelper fields — legitimate credential manager\n" +
        "- statusLine.command running a local script/binary — status-line rendering infra, like hooks. Only flag it if the command fetches or pipes REMOTE content (curl|bash, wget, base64 decode+exec).\n" +
        "- Standard dev domains: npmjs.com, pypi.org, github.com, githubusercontent.com, docker.com\n" +
        "\n" +
        "Return ONLY a raw JSON array starting with '['. No text, no fences.\n" +
        "No issues? Return: []\n" +
        "\n" +
        "Each finding: {\"severity\":\"LOW\"|\"MEDIUM\"|\"HIGH\"|\"CRITICAL\", \"category\":\"risky\"|\"malicious\", \"fieldPath\":\"...\", \"title\":\"...\", \"message\":\"...\", \"evidence\":\"...\", \"overview\":\"...\", \"remediation\":\"...\"}\n" +
        "message: one concise sentence naming the specific field and the concrete risk it creates — no generic filler, and do not restate the whole config.\n" +
        "evidence MUST be the single offending \"key\":value pair exactly as it appears in the input (e.g. \"disableAllHooks\":true or \"Bash(rm -rf *)\") — never the bare value, and never a whole array or section; cite only the one entry that triggered this finding.\n" +
        "overview: markdown with two \"## \" headed sections, each heading on its own line with a blank line between them: \"## What is this?\" (what the field controls in the tool's permission/sandbox model and what this value enables — explain the field, don't just restate the value) and \"## Why is it dangerous?\" (a concrete agentic attack chain — how prompt injection, a malicious MCP tool/skill, or a poisoned repo file makes the agent exploit this autonomously, bypassing the guardrail this value removes, ending with the impact) — 2-3 sentences each.\n" +
        "remediation: markdown with two \"### \" headed sections, each heading on its own line with a blank line between them: \"### 1. Steps to Remediate\" (a numbered list using sequential markers 1., 2., 3. naming the exact field and safe value plus how to enforce it org-wide via managed-settings.json; place the fenced corrected-config code block after the list, never between two numbered steps) and \"### 2. Custom Guardrails\" (a fenced code block containing a paste-ready guardrail rule that blocks the specific prompt or tool-call pattern this enables — a deployable rule, not a description).\n" +
        "\n" +
        "GROUNDING RULE — DO NOT HALLUCINATE: every fieldPath and evidence you report MUST be a key/value\n" +
        "that is literally present in the JSON below. Never report a field from the checklist just\n" +
        "because it's a known risky field name — only report it if you can point to its actual key\n" +
        "and value in the input JSON. If a checklist field is absent from the input, say nothing about it.\n" +
        "For example, do NOT report disableAllHooks unless the literal text \"disableAllHooks\":true appears in the JSON. An empty \"hooks\" object, or hook events mapped to empty arrays, is NOT disableAllHooks — never infer disableAllHooks from empty or unused hooks.\n" +
        "\n" +
        "A configured value is NOT automatically a risk. A default value or an expected setting is not a\n" +
        "finding — flag only a value that is genuinely dangerous per a rule below, report the specific\n" +
        "offending key (never a whole section), and never raise information-disclosure findings for\n" +
        "non-secret local data such as paths, versions, or hashes.\n" +
        "\n" +
        "SCAN EVERY FIELD PRESENT IN THE INPUT. CHECK EACH RULE BELOW.\n" +
        "Use these as examples — if you spot something similar that we missed, flag it too.\n" +
        "\n" +
        "--- permissions.defaultMode ---\n" +
        "  \"bypassPermissions\"  ->  CRITICAL / malicious\n" +
        "  \"dontAsk\" | \"auto\" | \"acceptEdits\"  ->  HIGH / risky\n" +
        "\n" +
        "--- permissions.allow ---\n" +
        "  A bare whole-tool wildcard whose entire scope is \"*\": Bash(*), Read(*), Write(*), Edit(*), WebFetch(*)  ->  HIGH / risky\n" +
        "  A command-scoped rule locks the command prefix and only wildcards its arguments — e.g. Bash(go test *), Bash(go build *), Bash(git log *), Bash(git checkout *), Bash(npm run *), Bash(make *). These are normal dev allowlists and MUST NOT be flagged. This holds for any ordinary build/test/VCS/package command (go, git, npm, yarn, pnpm, make, cargo, mvn, gradle, docker, kubectl, etc.).\n" +
        "  Exception — a command-scoped rule IS risky only when the locked command is itself destructive or exfil-capable: Bash(rm *), Bash(sudo *), Bash(curl *), Bash(wget *), Bash(chmod *), Bash(dd *), Bash(eval *)  ->  HIGH / risky\n" +
        "  Entry pointing at a credential path: .ssh, .aws, .kube, .gnupg, .npmrc, .pypirc, .netrc  ->  HIGH / malicious\n" +
        "  DO NOT flag bare tool-name grants (\"Read\", \"Write\", \"Edit\", \"Bash\", \"WebFetch\", \"Glob\", \"Grep\", \"Agent\", \"Skill\", MCP tool names, etc.) — an explicit allowlist of tool names is normal, expected Claude Code config, not a risk. Report only the specific offending entry, never permissions.allow as a whole.\n" +
        "\n" +
        "--- permissions.ask ---\n" +
        "  Bash(*), Read(*), Write(*), Edit(*), WebFetch(*)  ->  MEDIUM / risky\n" +
        "\n" +
        "--- permissions.deny ---\n" +
        "  Wildcard or absolute path  ->  LOW / risky\n" +
        "\n" +
        "--- permissions.additionalDirectories ---\n" +
        "  \"..\" | \"../\" | \"~\" | \"/\"  ->  HIGH / malicious\n" +
        "  .ssh | .aws | .kube | .gnupg | /etc/ | /var/run/ | /private/ | /tmp/  ->  HIGH / malicious\n" +
        "\n" +
        "--- permissions.skipDangerousModePermissionPrompt = true  ->  HIGH / risky ---\n" +
        "\n" +
        "--- sandbox.enabled = false  ->  HIGH / risky ---\n" +
        "--- sandbox.failIfUnavailable = false  ->  MEDIUM / risky ---\n" +
        "--- sandbox.allowUnsandboxedCommands = true  ->  HIGH / risky ---\n" +
        "--- sandbox.enableWeakerNestedSandbox = true  ->  HIGH / risky ---\n" +
        "--- sandbox.enableWeakerNetworkIsolation = true  ->  HIGH / risky ---\n" +
        "\n" +
        "--- sandbox.excludedCommands ---\n" +
        "  bash, sh, zsh, curl, wget, ssh, scp, rsync, aws, gcloud, python ->  HIGH / malicious\n" +
        "\n" +
        "--- sandbox.filesystem.allowRead or allowWrite ---\n" +
        "  .ssh | .aws | .kube | .gnupg | /etc/ | /var/run/ | /private/ | /tmp/  ->  HIGH / malicious\n" +
        "\n" +
        "--- sandbox.network.allowAllUnixSockets = true  ->  HIGH / risky ---\n" +
        "--- sandbox.network.allowLocalBinding = true  ->  MEDIUM / risky ---\n" +
        "--- sandbox.network.allowMachLookup = true  ->  MEDIUM / risky ---\n" +
        "--- sandbox.autoAllowBashIfSandboxed = true  ->  MEDIUM / risky ---\n" +
        "--- sandbox.network.allowedDomains ---\n" +
        "  \"*\"  ->  HIGH / risky\n" +
        "  .xyz, .top, .click, IP addresses, typosquats  ->  HIGH / malicious\n" +
        "\n" +
        "--- enableAllProjectMcpServers = true  ->  HIGH / risky ---\n" +
        "--- disableAllHooks = true  ->  HIGH / malicious ---\n" +
        "\n" +
        "\n" +
        "NOW SCAN THE JSON BELOW. CHECK EVERY FIELD THAT EXISTS. Report ONLY fields that are actually\n" +
        "present in this JSON — do not report a checklist field that this JSON does not contain.";

    private static final String CODEX_SCAN_PROMPT = "You are a security analyst auditing an OpenAI Codex CLI config.toml file. Report genuine security risks with high precision — a default, vendor-shipped, or benign-plumbing value is NOT a risk and must not be reported.\n" +
        "\n" +
        "HARD RULE — HIGHEST PRIORITY, overrides every rule below: NEVER report a command, args entry, notify target, or env value whose value is a path INSIDE an application bundle (the value contains \".app/\"). Those are the tool's own vendor-shipped binaries — e.g. Codex's node_repl at /Applications/Codex.app/..., or the Codex Computer Use notifier under a \"...Codex Computer Use.app/...\" path. Being located inside the app bundle is PROOF it is first-party and SAFE — it is NEVER, by itself, a reason to flag.\n" +
        "\n" +
        "DO NOT FLAG (ignore these completely — cosmetic/benign fields):\n" +
        "- All tui.* fields (theme, keymap, animations, notifications, status_line, terminal_title, etc.) — purely cosmetic\n" +
        "- personality, file_opener, hide_agent_reasoning, show_raw_agent_reasoning\n" +
        "- Product-nag dismissals: notice.hide_*, check_for_update_on_startup, feedback.enabled, suppress_unstable_features_warning\n" +
        "- Perf/telemetry-shape fields: history.*, log_dir, sqlite_home, tool_output_token_limit, service_tier, analytics.enabled, agents.max_threads, agents.max_depth, agents.job_max_runtime_seconds, model_reasoning_*, model_verbosity, model_context_window\n" +
        "- All of memories.*\n" +
        "- skills.config.<i>.enabled (only .path is checked)\n" +
        "- Hook command bodies: hooks.<Event>[].hooks[].command — Akto's own instrumentation hooks live there and would be noisy false positives\n" +
        "- Standard/expected domains: npmjs.com, pypi.org, github.com, githubusercontent.com, docker.com, api.openai.com\n" +
        "- MCP/provider plumbing metadata with no independent risk: cwd, env_http_headers, http_headers value shape, scopes, startup_timeout_*, tool_timeout_sec, enabled_tools/disabled_tools allowlists themselves, wire_api, request_max_retries, stream_*\n" +
        "- Codex's OWN bundled MCP servers: any mcp_servers.<id> whose command/args point inside the Codex app bundle (paths under /Applications/Codex.app/, .../Contents/Resources/, cua_node, node_repl, computer-use). These ship with Codex — they are first-party, not user-injected. Skip their command, args, and env entirely.\n" +
        "- mcp_servers.<id>.env.<KEY> whose VALUE is a filesystem path, URL, version string, SHA-256 hash or hash allowlist, timeout number, or a boolean/enum feature flag (e.g. NODE_REPL_*, BROWSER_USE_*, CODEX_*, SLACK_MCP_ENABLE_WRITE_TOOLS). These are plumbing config; do NOT flag them as secrets NOR as information disclosure — a version, path, or hash in a config the operator already owns is not an exposure.\n" +
        "- Any *_env_var / *_env / bearer_token_env_var field — it names the env var that HOLDS a secret; the safe indirection pattern. The secret is not in the file, so there is nothing to flag.\n" +
        "\n" +
        "Return ONLY a raw JSON array starting with '['. No text, no fences.\n" +
        "No issues? Return: []\n" +
        "\n" +
        "Each finding: {\"severity\":\"LOW\"|\"MEDIUM\"|\"HIGH\"|\"CRITICAL\", \"category\":\"risky\"|\"malicious\", \"fieldPath\":\"...\", \"title\":\"...\", \"message\":\"...\", \"evidence\":\"...\", \"overview\":\"...\", \"remediation\":\"...\"}\n" +
        "message: one concise sentence naming the specific field and the concrete risk it creates — no generic filler, and do not restate the whole config.\n" +
        "evidence MUST be the single offending \"key\":value pair exactly as it appears in the input (e.g. \"disableAllHooks\":true or \"Bash(rm -rf *)\") — never the bare value, and never a whole array or section; cite only the one entry that triggered this finding.\n" +
        "overview: markdown with two \"## \" headed sections, each heading on its own line with a blank line between them: \"## What is this?\" (what the field controls in the tool's permission/sandbox model and what this value enables — explain the field, don't just restate the value) and \"## Why is it dangerous?\" (a concrete agentic attack chain — how prompt injection, a malicious MCP tool/skill, or a poisoned repo file makes the agent exploit this autonomously, bypassing the guardrail this value removes, ending with the impact) — 2-3 sentences each.\n" +
        "remediation: markdown with two \"### \" headed sections, each heading on its own line with a blank line between them: \"### 1. Steps to Remediate\" (a numbered list using sequential markers 1., 2., 3. naming the exact field and safe value; place the fenced corrected-config code block after the list, never between two numbered steps) and \"### 2. Custom Guardrails\" (a fenced code block containing a paste-ready guardrail rule that blocks the specific prompt or tool-call pattern this enables — a deployable rule, not a description).\n" +
        "\n" +
        "GROUNDING RULE — DO NOT HALLUCINATE: every fieldPath and evidence you report MUST be a key/value\n" +
        "that is literally present in the JSON below. Never report a field from the checklist just\n" +
        "because it's a known risky field name — only report it if you can point to its actual key\n" +
        "and value in the input JSON. If a checklist field is absent from the input, say nothing about it.\n" +
        "\n" +
        "A configured value is NOT automatically a risk. A default/vendor-shipped server, a normal https URL,\n" +
        "an env-var-name reference, or a plumbing setting is expected config — flag only a value that is\n" +
        "genuinely dangerous per a rule below, and report the specific offending key, never a whole section.\n" +
        "\n" +
        "SCAN EVERY FIELD PRESENT IN THE INPUT. CHECK EACH RULE BELOW.\n" +
        "Use these as examples — if you spot something similar that we missed, flag it too.\n" +
        "\n" +
        "--- approval_policy = \"never\"  ->  CRITICAL / malicious ---\n" +
        "--- approval_policy.granular.* (any sub-flag implying approval is skipped)  ->  HIGH / risky ---\n" +
        "--- sandbox_mode = \"danger-full-access\"  ->  CRITICAL / malicious ---\n" +
        "--- sandbox_workspace_write.network_access = true  ->  HIGH / risky ---\n" +
        "--- sandbox_workspace_write.exclude_slash_tmp = false  ->  MEDIUM / risky ---\n" +
        "--- sandbox_workspace_write.exclude_tmpdir_env_var = false  ->  MEDIUM / risky ---\n" +
        "\n" +
        "--- permissions.<name>.workspace_roots / filesystem.<path> / sandbox_workspace_write.writable_roots ---\n" +
        "  .ssh, .aws, .gnupg, .kube, .npmrc, .netrc, /etc/, .., ~, /  ->  HIGH / malicious\n" +
        "\n" +
        "--- permissions.<name>.network.dangerously_allow_all_unix_sockets = true  ->  CRITICAL / malicious ---\n" +
        "--- permissions.<name>.network.dangerously_allow_non_loopback_proxy = true (+ features.network_proxy.* dupes)  ->  CRITICAL / malicious ---\n" +
        "  OpenAI names these \"dangerously\" — treat as a strong signal.\n" +
        "\n" +
        "--- permissions.<name>.network.domains.<pattern> / features.network_proxy.domains ---\n" +
        "  \"allow\" on wildcard or non-whitelisted domain  ->  HIGH / malicious\n" +
        "\n" +
        "--- permissions.<name>.network.unix_sockets.<path> ---\n" +
        "  \"allow\" on docker.sock / ssh-agent / gpg-agent sockets  ->  HIGH / malicious\n" +
        "\n" +
        "--- permissions.<name>.network.enable_socks5 = true  ->  MEDIUM / risky ---\n" +
        "--- permissions.<name>.network.enable_socks5_udp = true  ->  MEDIUM / risky ---\n" +
        "--- permissions.<name>.network.allow_local_binding = true  ->  MEDIUM / risky ---\n" +
        "--- permissions.<name>.network.allow_upstream_proxy = true  ->  MEDIUM / risky ---\n" +
        "\n" +
        "--- shell_environment_policy.inherit = \"all\"  ->  HIGH / risky ---\n" +
        "--- shell_environment_policy.ignore_default_excludes = true  ->  HIGH / risky ---\n" +
        "--- shell_environment_policy.experimental_use_profile = true  ->  MEDIUM / risky ---\n" +
        "--- shell_environment_policy.set.<key> ---\n" +
        "  value looks like a secret/token/key  ->  HIGH / malicious\n" +
        "\n" +
        "--- allow_login_shell = true  ->  HIGH / risky ---\n" +
        "\n" +
        "--- mcp_servers.<id>.default_tools_approval_mode / tools.<tool>.approval_mode (also under apps.<id> / plugins.<plugin>.mcp_servers.<server>) ---\n" +
        "  \"auto\"  ->  HIGH / risky\n" +
        "\n" +
        "--- mcp_servers.<id> server name / apps.<id> name ---\n" +
        "  Suspicious, unexpected, or unreviewed-looking server/app name  ->  HIGH / risky\n" +
        "\n" +
        "--- mcp_servers.<id>.url / experimental_environment=\"remote\" host ---\n" +
        "  Wildcard host, raw IP, or plaintext http:// URL  ->  HIGH / malicious\n" +
        "  A normal https:// URL to a SaaS/vendor API host is expected config — do NOT flag it.\n" +
        "\n" +
        "--- mcp_servers.<id>.command / mcp_servers.<id>.args ---\n" +
        "  Shell/network exfil patterns (curl|bash, base64-decode-and-exec, reverse shell)  ->  HIGH / malicious\n" +
        "  Do NOT flag bare npx/uvx/node/python/docker invocations, an absolute path to an interpreter or to a binary inside an app bundle (e.g. .../node_repl), or a plain script/JS file path passed as an arg. Only a genuine exfil/reverse-shell pipeline is a finding.\n" +
        "\n" +
        "--- apps.<id>.destructive_enabled = true  ->  HIGH / risky ---\n" +
        "--- apps.<id>.open_world_enabled = true  ->  HIGH / risky ---\n" +
        "--- approvals_reviewer = \"auto_review\" (also apps.<id>.approvals_reviewer)  ->  HIGH / risky ---\n" +
        "\n" +
        "--- model_providers.<id>.auth.command ---\n" +
        "  Any non-empty value (analog of apiKeyHelper)  ->  CRITICAL / malicious\n" +
        "\n" +
        "--- model_providers.<id>.experimental_bearer_token ---\n" +
        "  A literal inline token value  ->  MEDIUM / risky\n" +
        "  A reference to an env var name (bearer_token_env_var and similar) is the safe pattern — do NOT flag it.\n" +
        "\n" +
        "--- model_providers.<id>.requires_openai_auth = true + base_url ---\n" +
        "  Host isn't an OpenAI domain  ->  CRITICAL / malicious\n" +
        "\n" +
        "--- model_providers.<id>.base_url / openai_base_url / chatgpt_base_url / experimental_realtime_ws_base_url ---\n" +
        "  Wildcard or raw IP  ->  HIGH / malicious\n" +
        "\n" +
        "--- otel.log_user_prompt = true  ->  HIGH / risky ---\n" +
        "--- otel.exporter.<id>.endpoint / trace_exporter.<id>.endpoint / metrics exporter endpoint ---\n" +
        "  Suspicious or unexpected endpoint  ->  HIGH / risky\n" +
        "--- otel.exporter.<id>.headers ---\n" +
        "  Literal Authorization/bearer secret  ->  MEDIUM / risky\n" +
        "--- otel.exporter.<id>.tls.* ---\n" +
        "  Disables cert verification  ->  HIGH / risky\n" +
        "\n" +
        "--- mcp_oauth_callback_url ---\n" +
        "  Not localhost/127.0.0.1  ->  HIGH / malicious\n" +
        "\n" +
        "--- cli_auth_credentials_store = \"file\"  ->  MEDIUM / risky ---\n" +
        "--- mcp_oauth_credentials_store = \"file\"  ->  MEDIUM / risky ---\n" +
        "\n" +
        "--- notify ---\n" +
        "  Command contains bash/sh/curl/wget/nc/ssh/python/base64 or otherwise exfiltrates event data  ->  HIGH / malicious\n" +
        "  Do NOT flag notify merely for being present — Codex ships a default notify command; a benign notify (e.g. pointing at the Codex app bundle) is not a finding.\n" +
        "\n" +
        "--- developer_instructions / model_instructions_file / compact_prompt / experimental_compact_prompt_file ---\n" +
        "  Content instructs ignoring safety rules, exfiltrating data, hiding actions, or self-modifying config  ->  CRITICAL / malicious\n" +
        "\n" +
        "--- skills.config.<i>.path ---\n" +
        "  Sensitive/system path or traversal  ->  HIGH / malicious\n" +
        "\n" +
        "--- features.skill_mcp_dependency_install = true  ->  MEDIUM / risky ---\n" +
        "--- notice.hide_full_access_warning = true  ->  MEDIUM / risky ---\n" +
        "--- notice.hide_world_writable_warning = true  ->  MEDIUM / risky ---\n" +
        "--- windows.sandbox = \"elevated\"  ->  HIGH / risky ---\n" +
        "\n" +
        "\n" +
        "NOW SCAN THE TOML-DERIVED JSON BELOW. CHECK EVERY FIELD THAT EXISTS. Report ONLY fields that are\n" +
        "actually present in this JSON — do not report a checklist field that this JSON does not contain.";

    private static final String CODEX_REQUIREMENTS_SCAN_PROMPT = "You are a security analyst auditing an OpenAI Codex CLI requirements.toml file. This file is written by IT admins (via MDM or system file placement) to CONSTRAIN what an end user's config.toml is allowed to set. Find every place where a restriction is missing, empty, or still too permissive — do NOT treat the mere presence of a permissive-looking value the way you would in a normal config file, since the ONLY question that matters here is \"does this requirements.toml leave a dangerous door open.\"\n" +
        "\n" +
        "DO NOT FLAG (ignore these completely):\n" +
        "- Any [[hooks.<Event>]] entries whose command references akto-, Akto's own hook wrapper scripts, or the managed_dir path set up by Akto's own installer — this is Akto's own instrumentation, written by our installer into this same file, not third-party interference.\n" +
        "- Absence of the file entirely — a missing requirements.toml just means the deployment is unmanaged, which is a config.toml-level concern, not something this prompt should flag.\n" +
        "- Cosmetic/administrative metadata with no access-control meaning (comments, [marketplaces] entries that only add trusted registries, ordering of keys).\n" +
        "\n" +
        "Return ONLY a raw JSON array starting with '['. No text, no fences.\n" +
        "No issues? Return: []\n" +
        "\n" +
        "Each finding: {\"severity\":\"LOW\"|\"MEDIUM\"|\"HIGH\"|\"CRITICAL\", \"category\":\"risky\"|\"malicious\", \"fieldPath\":\"...\", \"title\":\"...\", \"message\":\"...\", \"evidence\":\"...\", \"overview\":\"...\", \"remediation\":\"...\"}\n" +
        "message: one concise sentence naming the specific field and the concrete risk it creates — no generic filler, and do not restate the whole config.\n" +
        "evidence MUST be the single offending \"key\":value pair exactly as it appears in the input (e.g. \"disableAllHooks\":true or \"Bash(rm -rf *)\") — never the bare value, and never a whole array or section; cite only the one entry that triggered this finding.\n" +
        "overview: markdown with two \"## \" headed sections, each heading on its own line with a blank line between them: \"## What is this?\" (what the restriction constrains in the admin-managed model and what its absence or looseness permits) and \"## Why is it dangerous?\" (a concrete agentic attack chain — how the missing restriction lets prompt injection, a malicious MCP tool, or a poisoned file drive the agent past a control that should have been enforced, ending with the impact) — 2-3 sentences each.\n" +
        "remediation: markdown with two \"### \" headed sections, each heading on its own line with a blank line between them: \"### 1. Steps to Remediate\" (a numbered list using sequential markers 1., 2., 3. naming the exact field to add or tighten in requirements.toml; place the fenced corrected-value code block after the list, never between two numbered steps) and \"### 2. Custom Guardrails\" (a fenced code block containing a paste-ready guardrail rule that blocks the specific prompt or tool-call pattern this gap enables — a deployable rule, not a description).\n" +
        "\n" +
        "GROUNDING RULE — DO NOT HALLUCINATE: when a rule below is about a PRESENT-but-too-permissive\n" +
        "value (e.g. \"allowed_sandbox_modes includes danger-full-access\"), you must point to that value's\n" +
        "actual key and content in the JSON below — never invent a value that isn't there. When a rule is\n" +
        "about a field being ABSENT or EMPTY (this file's inverted risk model), you may correctly report\n" +
        "it even though the fieldPath doesn't appear in the JSON — that is the finding itself. Do not\n" +
        "confuse the two: never claim a field \"is set to X\" unless X is literally in the input.\n" +
        "\n" +
        "SCAN EVERY FIELD PRESENT IN THE INPUT. CHECK EACH RULE BELOW — every rule here is about a restriction being absent, empty, or too loose, never about a value simply existing.\n" +
        "\n" +
        "--- allowed_approval_policies ---\n" +
        "  Field present but includes \"never\"  ->  CRITICAL / malicious\n" +
        "  Field absent entirely (nothing constrains approval_policy)  ->  MEDIUM / risky\n" +
        "\n" +
        "--- allowed_sandbox_modes ---\n" +
        "  Field present but includes \"danger-full-access\"  ->  CRITICAL / malicious\n" +
        "  Field absent entirely (nothing constrains sandbox_mode)  ->  MEDIUM / risky\n" +
        "\n" +
        "--- allowed_permission_profiles ---\n" +
        "  Field present but includes a \":danger-full-access\" style full-access profile  ->  HIGH / malicious\n" +
        "  Field absent entirely  ->  LOW / risky\n" +
        "\n" +
        "--- allowed_approvals_reviewers ---\n" +
        "  Field present but includes \"auto_review\"  ->  HIGH / risky\n" +
        "\n" +
        "--- allow_appshots = true  ->  LOW / risky ---\n" +
        "--- allow_remote_control = true  ->  MEDIUM / risky ---\n" +
        "\n" +
        "--- [permissions.filesystem].deny_read ---\n" +
        "  Field absent or empty (no credential/system paths denied at the admin level)  ->  MEDIUM / risky\n" +
        "  Field present but does not include .ssh, .aws, .gnupg, .kube, /etc  ->  LOW / risky\n" +
        "\n" +
        "--- [mcp_servers] allowlist ---\n" +
        "  Field absent entirely (any MCP server name/command is allowed unrestricted)  ->  MEDIUM / risky\n" +
        "  Field present but contains a wildcard entry that defeats the allowlist  ->  HIGH / malicious\n" +
        "\n" +
        "--- [marketplaces] allowlist ---\n" +
        "  Field present but allows an untrusted or wildcard plugin source  ->  HIGH / malicious\n" +
        "\n" +
        "--- [rules] command restrictions ---\n" +
        "  Field absent entirely (no admin-level command denylist at all)  ->  LOW / risky\n" +
        "  Field present but explicitly allows a known-dangerous command pattern (curl|bash, base64 decode+exec, reverse shell)  ->  HIGH / malicious\n" +
        "\n" +
        "--- [[hooks.<Event>]] entries NOT matching the Akto pattern above ---\n" +
        "  Any third-party command hook injected into this admin-trusted file  ->  HIGH / malicious\n" +
        "  (Managed hooks in requirements.toml are trusted by Codex with no user approval — an unexpected entry here is a stronger signal than the same entry in config.toml.)\n" +
        "\n" +
        "NOW SCAN THE TOML-DERIVED JSON BELOW. Remember: judge what is MISSING or TOO PERMISSIVE, not merely\n" +
        "what is present — but any value you cite as evidence for a \"too permissive\" finding must actually\n" +
        "appear in this JSON.";

    private static final String COPILOT_SCAN_PROMPT = "You are a security analyst auditing a GitHub Copilot CLI settings.json file. Find EVERY security risk — do not skip any present field.\n" +
        "\n" +
        "DO NOT FLAG (ignore these completely — cosmetic/benign fields):\n" +
        "- theme, colorMode, banner, beep, beepOnSchedule, mouse, scrollbar, screenReader, renderMarkdown — purely cosmetic\n" +
        "- tabs.*, footer.*, statusLine, showTipsOnStartup, updateTerminalTitle, terminalProgress — UI/status-line customization\n" +
        "- keepAlive, autoUpdate, autoUpdatesChannel, logLevel, compactPaste, copyOnSelect, respectGitignore, companyAnnouncements, includeCoAuthoredBy, stream, streamerMode, model, effortLevel, toolSearch — UX/perf tuning, no access-control implication\n" +
        "- ide.autoConnect, ide.openDiffOnEdit, powershellFlags, dynamicRetrieval, skillDirectories, disabledSkills — plumbing/personalization with no independent risk\n" +
        "- disabledMcpServers — a denylist is itself a restriction, never risky\n" +
        "- mergeStrategy, subagents.*, customAgents.defaultLocalOnly, builtInAgents.* — workflow/subagent tuning\n" +
        "- All hooks content — skip every hook field entirely (Akto's own instrumentation hooks live here and would be noisy false positives)\n" +
        "\n" +
        "Return ONLY a raw JSON array starting with '['. No text, no fences.\n" +
        "No issues? Return: []\n" +
        "\n" +
        "Each finding: {\"severity\":\"LOW\"|\"MEDIUM\"|\"HIGH\"|\"CRITICAL\", \"category\":\"risky\"|\"malicious\", \"fieldPath\":\"...\", \"title\":\"...\", \"message\":\"...\", \"evidence\":\"...\", \"overview\":\"...\", \"remediation\":\"...\"}\n" +
        "message: one concise sentence naming the specific field and the concrete risk it creates — no generic filler, and do not restate the whole config.\n" +
        "evidence MUST be the single offending \"key\":value pair exactly as it appears in the input (e.g. \"disableAllHooks\":true or \"Bash(rm -rf *)\") — never the bare value, and never a whole array or section; cite only the one entry that triggered this finding.\n" +
        "overview: markdown with two \"## \" headed sections, each heading on its own line with a blank line between them: \"## What is this?\" (what the field controls in the tool's permission/sandbox model and what this value enables — explain the field, don't just restate the value) and \"## Why is it dangerous?\" (a concrete agentic attack chain — how prompt injection, a malicious MCP tool/skill, or a poisoned repo file makes the agent exploit this autonomously, bypassing the guardrail this value removes, ending with the impact) — 2-3 sentences each.\n" +
        "remediation: markdown with two \"### \" headed sections, each heading on its own line with a blank line between them: \"### 1. Steps to Remediate\" (a numbered list using sequential markers 1., 2., 3. naming the exact field and safe value; place the fenced corrected-config code block after the list, never between two numbered steps) and \"### 2. Custom Guardrails\" (a fenced code block containing a paste-ready guardrail rule that blocks the specific prompt or tool-call pattern this enables — a deployable rule, not a description).\n" +
        "\n" +
        "GROUNDING RULE — DO NOT HALLUCINATE: every fieldPath and evidence you report MUST be a key/value\n" +
        "that is literally present in the JSON below, EXCEPT for the one rule below that is explicitly\n" +
        "about absence (permissions.disableBypassPermissionsMode not being set). For every other rule,\n" +
        "never report a field from the checklist just because it's a known risky field name — only report\n" +
        "it if you can point to its actual key and value in the input JSON.\n" +
        "\n" +
        "A configured value is NOT automatically a risk. A default value or an expected setting is not a\n" +
        "finding — flag only a value that is genuinely dangerous per a rule below, report the specific\n" +
        "offending key (never a whole section), and never raise information-disclosure findings for\n" +
        "non-secret local data such as paths, versions, or hashes.\n" +
        "You are NOT given the org's approved URL/plugin/marketplace allowlist. Do not flag a normal https://\n" +
        "URL, a well-known vendor domain, or a reputable plugin merely as \"not in the approved list\" — flag\n" +
        "only an entry that is itself suspicious (wildcard, raw IP, plaintext http://, typosquat, or an\n" +
        "obviously untrusted/unreviewed source).\n" +
        "\n" +
        "SCAN EVERY FIELD PRESENT IN THE INPUT. CHECK EACH RULE BELOW.\n" +
        "Use these as examples — if you spot something similar that we missed, flag it too.\n" +
        "\n" +
        "--- permissions.disableBypassPermissionsMode ---\n" +
        "  Present but not \"disable\"  ->  MEDIUM / risky\n" +
        "  Absent entirely (nothing suppresses --allow-all / bypass-permissions flags)  ->  MEDIUM / risky\n" +
        "\n" +
        "--- storeTokenPlaintext = true  ->  HIGH / risky ---\n" +
        "\n" +
        "--- allowedUrls ---\n" +
        "  Wildcard (\"*\") or a domain not in the org-approved list  ->  HIGH / malicious\n" +
        "\n" +
        "--- deniedUrls ---\n" +
        "  Empty or absent while allowedUrls is broad/wildcarded  ->  LOW / risky\n" +
        "\n" +
        "--- askUser = false  ->  MEDIUM / risky ---\n" +
        "  (Autonomous mode — no clarification prompts before acting, analogous to Claude's dontAsk/auto.)\n" +
        "\n" +
        "--- disableAllHooks = true  ->  MEDIUM / risky ---\n" +
        "\n" +
        "--- proxyUrl ---\n" +
        "  Host not in the org-approved list, wildcard, or raw IP  ->  HIGH / malicious\n" +
        "\n" +
        "--- proxyKerberosServicePrincipal ---\n" +
        "  Present and unexpected  ->  LOW / risky\n" +
        "\n" +
        "--- extraKnownMarketplaces ---\n" +
        "  Source URL is untrusted, unreviewed, or wildcard  ->  HIGH / malicious\n" +
        "\n" +
        "--- enabledPlugins ---\n" +
        "  Plugin name not in the org-approved list  ->  MEDIUM / risky\n" +
        "\n" +
        "--- bashEnv = true  ->  LOW / risky ---\n" +
        "\n" +
        "NOW SCAN THE JSON BELOW. CHECK EVERY FIELD THAT EXISTS. Report ONLY fields that are actually\n" +
        "present in this JSON (except the disableBypassPermissionsMode-absent rule above) — do not report\n" +
        "a checklist field that this JSON does not contain.";

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

        findings = parsed != null ? parsed : new ArrayList<>();
        logger.info(String.format(
                "[SettingsScan] tool=%s cfgPath=%s findings=%d", tool, cfgPath, findings.size()), LogDb.DB_ABS);
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
        payload.put("max_tokens", 10000);

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

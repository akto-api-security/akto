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
        "message: plain language a non-technical person can follow. Name the exact field and its actual value from the input, then say precisely what that value causes to happen — you may use more than one sentence if that is what it takes to be precise, but never pad with filler. Never write a vague line like \"this can be a security risk\" or \"could allow unauthorized execution.\" State the mechanism directly.\n" +
        "  GOOD example for \"disableAllHooks\":true: \"disableAllHooks is set to true. This turns off every hook configured in this file, including any monitoring or security-validation hooks — none of them will run anymore, so nothing is checking or logging what Claude does.\"\n" +
        "  BAD example (never write like this): \"This field may pose a security risk if misconfigured.\"\n" +
        "evidence MUST be the single offending \"key\":value pair exactly as it appears in the input, copied from the literal value you find in the JSON below — never the bare value, and never a whole array or section; cite only the one entry that triggered this finding.\n" +
        "overview: markdown with two \"## \" headed sections, each heading on its own line with a blank line between them: \"## What is this?\" (what the field controls in the tool's permission/sandbox model and what this value enables — explain the field in plain terms, don't just restate the value) and \"## Why is it dangerous?\" (a concrete agentic attack chain — how prompt injection, a malicious MCP tool/skill, or a poisoned repo file makes the agent exploit this autonomously, bypassing the guardrail this value removes, ending with the impact) — as many sentences as needed to be precise and concrete, written so a non-technical reader still understands the mechanism, not just that \"it's risky.\"\n" +
        "remediation: markdown with two \"### \" headed sections, each heading on its own line with a blank line between them: \"### 1. Steps to Remediate\" (a numbered list using sequential markers 1., 2., 3. naming the exact field and safe value plus how to enforce it org-wide via managed-settings.json; place the fenced corrected-config code block after the list, never between two numbered steps) and \"### 2. Custom Guardrails\" (a fenced code block containing a paste-ready guardrail rule that blocks the specific prompt or tool-call pattern this enables — a deployable rule, not a description).\n" +
        "\n" +
        "GROUNDING RULE — DO NOT HALLUCINATE: every fieldPath and evidence you report MUST be a key/value\n" +
        "that is literally present in the JSON below, WITH THE SAME VALUE as what you report in evidence and\n" +
        "message. Never report a field from the checklist just because it's a known risky field name — only\n" +
        "report it if you can point to its actual key and value in the input JSON. If a checklist field is\n" +
        "absent from the input, say nothing about it.\n" +
        "Several rules below name a boolean field with only one dangerous value (e.g. \"= true\" or \"= false\").\n" +
        "For those rules, think through the field's actual behavior before writing anything: your own \"## What\n" +
        "is this?\" text will describe what the field's actual value does — if that description is itself safe\n" +
        "(the value keeps a protection ON, or keeps something restricted OFF), that is a contradiction with\n" +
        "flagging it, and you must not emit a finding for it. Only emit a finding when the field's actual\n" +
        "value is the one specific value the rule names as dangerous. For disableAllHooks in particular: an\n" +
        "empty \"hooks\" object, or hook events mapped to empty arrays, is NOT disableAllHooks — never infer\n" +
        "disableAllHooks from empty or unused hooks.\n" +
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
        "--- disableAllHooks ---\n" +
        "  \"disableAllHooks\":true (literal boolean true)  ->  HIGH / malicious\n" +
        "  \"disableAllHooks\":false, or the field absent entirely  ->  NOT a finding, do not report it at all\n" +
        "\n" +
        "\n" +
        "NOW SCAN THE JSON BELOW. CHECK EVERY FIELD THAT EXISTS. Report ONLY fields that are actually\n" +
        "present in this JSON — do not report a checklist field that this JSON does not contain.";

    // Field scope below is grounded in OpenAI's own Codex security/config docs:
    // https://learn.chatgpt.com/docs/security-administration
    // https://learn.chatgpt.com/codex/config-file/config-reference
    // https://learn.chatgpt.com/codex/sandboxing
    // https://learn.chatgpt.com/codex/permissions
    private static final String CODEX_SCAN_PROMPT = "You are a security analyst auditing an OpenAI Codex CLI config.toml file (given to you as TOML-derived JSON), scoped strictly to sandbox and permissions configuration per learn.chatgpt.com/docs/security-administration. Your review is scoped to a fixed list of (fieldPath, dangerousValue) checks below — these are the exact risks you are looking for, not illustrative examples. Do not go hunting for other issues beyond this list.\n" +
        "\n" +
        "THE CHECKLIST BELOW IS THE ENTIRE SCOPE OF THIS REVIEW. There is no other rule, no \"use these as\n" +
        "examples,\" no \"flag anything similar.\" The following top-level keys and everything nested under them\n" +
        "are out of scope — never read, quote, or reason about their contents, even if something\n" +
        "under them looks suspicious: projects (including trust_level — a folder's own name/path is never\n" +
        "evidence of anything), mcp_servers, apps, plugins, marketplaces, model_providers, otel, mcp_oauth_*,\n" +
        "cli_auth_credentials_store, notify, developer_instructions, model_instructions_file, compact_prompt,\n" +
        "experimental_compact_prompt_file, skills, notice, windows, tui, desktop, history, memories, hooks,\n" +
        "shell_environment_policy, allow_login_shell.\n" +
        "\n" +
        "HARD RULE — applies to every row below: if the row's value is a filesystem path and that path is\n" +
        "INSIDE an application bundle (contains \".app/\", including a relative path like \"./Some Thing.app/...\"),\n" +
        "that row produces NO finding — being inside a .app bundle proves it is the tool's own vendor-shipped\n" +
        "binary, not user or attacker controlled. Check this before applying any row's dangerousValue test.\n" +
        "\n" +
        "HOW TO CHECK EACH CHECKLIST ROW — go through every row below, in order, without skipping any:\n" +
        "1. Look up fieldPath in the input JSON below. If that key path is not present, this row produces NO finding. Move to the next row.\n" +
        "2. If present, copy its literal value character-for-character. This is actualValue.\n" +
        "3. If actualValue is a path and it is inside a .app bundle (see HARD RULE above), this row produces NO finding. Move to the next row.\n" +
        "4. Compare actualValue to dangerousValue for this row using the stated comparison. If it does not match, this row produces NO finding, regardless of what the field name is or what it might suggest. Move to the next row.\n" +
        "5. Only if actualValue matches dangerousValue exactly: this row produces one finding. actualValue becomes both quotedValue and the value shown in evidence and message.\n" +
        "Before returning your answer, count the checklist rows and verify you produced a finding-or-no-finding decision for every single one.\n" +
        "\n" +
        "Return ONLY a raw JSON array starting with '['. No text, no fences.\n" +
        "No rows matched? Return: []\n" +
        "\n" +
        "Each finding: {\"fieldPath\":\"...\", \"quotedValue\":\"...\", \"severity\":\"LOW\"|\"MEDIUM\"|\"HIGH\"|\"CRITICAL\", \"category\":\"risky\"|\"malicious\", \"title\":\"...\", \"message\":\"...\", \"evidence\":\"...\", \"overview\":\"...\", \"remediation\":\"...\"}\n" +
        "quotedValue = actualValue from step 2 above, copied character-for-character from the input you are looking at right now. Every other field in this finding must describe THIS value and nothing else.\n" +
        "message: plain language a non-technical person can follow. Name the exact field and its actual value (quotedValue), then say precisely what that value causes to happen. You may use more than one sentence if that is what it takes to be precise, but never pad with filler. Never write a vague line like \"this may be a security risk\" or \"could allow unauthorized execution.\" State the mechanism directly.\n" +
        "  GOOD example: \"sandbox_mode is set to danger-full-access. This turns off Codex's sandbox entirely, so Codex can read, write, and delete any file on this machine with nothing standing in the way.\"\n" +
        "  BAD example (never write like this): \"This setting could allow for unauthorized execution of code.\"\n" +
        "evidence MUST be \"fieldPath\":quotedValue exactly as it appears in the input (e.g. \"sandbox_mode\":\"danger-full-access\") — never the bare value, never a whole section.\n" +
        "overview: markdown with two \"## \" headed sections, each heading on its own line with a blank line between them: \"## What is this?\" (what the field controls and what quotedValue specifically enables, in plain terms) and \"## Why is it dangerous?\" (a concrete agentic attack chain ending with the impact) — as many sentences as needed to be precise and concrete, plain language, no filler.\n" +
        "remediation: markdown with two \"### \" headed sections, each heading on its own line with a blank line between them: \"### 1. Steps to Remediate\" (a numbered list using sequential markers 1., 2., 3. naming the exact field and safe value; place the fenced corrected-config code block after the list, never between two numbered steps) and \"### 2. Custom Guardrails\" (a fenced code block containing a paste-ready guardrail rule that blocks the specific prompt or tool-call pattern this enables).\n" +
        "\n" +
        "THE CHECKLIST (fieldPath | dangerousValue → this IS a finding | the other/default value → NOT a finding):\n" +
        "\n" +
        "1. approval_policy\n" +
        "   dangerousValue \"never\" -> CRITICAL / malicious\n" +
        "\n" +
        "2. approval_policy.granular.<any sub-flag>\n" +
        "   dangerousValue: a sub-flag whose value means approval/sandbox/skill approval is skipped -> HIGH / risky\n" +
        "\n" +
        "3. approvals_reviewer\n" +
        "   dangerousValue \"auto_review\" -> HIGH / risky\n" +
        "\n" +
        "4. sandbox_mode\n" +
        "   dangerousValue \"danger-full-access\" -> CRITICAL / malicious. Value \"read-only\" or \"workspace-write\" -> NOT a finding.\n" +
        "\n" +
        "5. sandbox_workspace_write.network_access\n" +
        "   dangerousValue true -> HIGH / risky. Value false or absent -> NOT a finding.\n" +
        "\n" +
        "6. sandbox_workspace_write.exclude_slash_tmp\n" +
        "   dangerousValue false -> MEDIUM / risky. Value true or absent -> NOT a finding (true is the safe default).\n" +
        "\n" +
        "7. sandbox_workspace_write.exclude_tmpdir_env_var\n" +
        "   dangerousValue false -> MEDIUM / risky. Value true or absent -> NOT a finding (true is the safe default).\n" +
        "\n" +
        "8. sandbox_workspace_write.writable_roots[] entries\n" +
        "   dangerousValue: entry contains .ssh, .aws, .gnupg, .kube, .npmrc, .netrc, /etc/, .., ~, or / -> HIGH / malicious (unless caught by the HARD RULE above)\n" +
        "\n" +
        "9. default_permissions\n" +
        "   dangerousValue \":danger-full-access\" -> CRITICAL / malicious\n" +
        "\n" +
        "10. permissions.<name>.workspace_roots / permissions.<name>.filesystem.<path>\n" +
        "    dangerousValue: path is .ssh, .aws, .gnupg, .kube, .npmrc, .netrc, /etc/, .., ~, or / AND the access mode is write (not read/deny) -> HIGH / malicious\n" +
        "\n" +
        "11. permissions.<name>.network.enabled\n" +
        "    dangerousValue true -> MEDIUM / risky. Value false or absent -> NOT a finding.\n" +
        "\n" +
        "12. permissions.<name>.network.dangerously_allow_all_unix_sockets\n" +
        "    dangerousValue true -> CRITICAL / malicious. Value false or absent -> NOT a finding.\n" +
        "\n" +
        "13. permissions.<name>.network.dangerously_allow_non_loopback_proxy (also features.network_proxy.* equivalents)\n" +
        "    dangerousValue true -> CRITICAL / malicious. Value false or absent -> NOT a finding. (OpenAI names these \"dangerously\" — treat as a strong signal.)\n" +
        "\n" +
        "14. permissions.<name>.network.domains.<pattern> / features.network_proxy.domains\n" +
        "    dangerousValue: pattern is \"*\" and its value is \"allow\" -> HIGH / malicious\n" +
        "\n" +
        "15. permissions.<name>.network.allow_local_binding / features.network_proxy.allow_local_binding\n" +
        "    dangerousValue true -> MEDIUM / risky. Value false or absent -> NOT a finding.\n" +
        "\n" +
        "NOW SCAN THE TOML-DERIVED JSON BELOW. Go through checklist rows 1-15 in order. For each row, follow\n" +
        "the 5-step procedure above using the field's actual literal value in this specific input. Do not\n" +
        "skip step 2 (copying the literal value) for any row, even ones that seem obviously safe or obviously\n" +
        "dangerous at a glance.";

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
        "message: plain language a non-technical person can follow. Name the exact field (or the exact restriction that is missing/too loose) and say precisely what that gap causes to happen. You may use more than one sentence if that is what it takes to be precise, but never pad with filler. Never write a vague line like \"this may be a security risk.\" State the mechanism directly.\n" +
        "  GOOD example: \"allowed_sandbox_modes does not restrict danger-full-access. Because of this gap, a user's config.toml is free to set sandbox_mode to danger-full-access, and nothing in this admin policy will stop it.\"\n" +
        "  BAD example (never write like this): \"This could allow harmful actions.\"\n" +
        "evidence MUST be the single offending \"key\":value pair exactly as it appears in the input (e.g. \"disableAllHooks\":true or \"Bash(rm -rf *)\") — never the bare value, and never a whole array or section; cite only the one entry that triggered this finding.\n" +
        "overview: markdown with two \"## \" headed sections, each heading on its own line with a blank line between them: \"## What is this?\" (what the restriction constrains in the admin-managed model and what its absence or looseness permits, in plain terms) and \"## Why is it dangerous?\" (a concrete agentic attack chain — how the missing restriction lets prompt injection, a malicious MCP tool, or a poisoned file drive the agent past a control that should have been enforced, ending with the impact) — as many sentences as needed to be precise and concrete.\n" +
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
        "message: plain language a non-technical person can follow. Name the exact field and its actual value from the input, then say precisely what that value causes to happen. You may use more than one sentence if that is what it takes to be precise, but never pad with filler. Never write a vague line like \"this may be a security risk\" or \"could allow unauthorized execution.\" State the mechanism directly.\n" +
        "  GOOD example: \"allowedUrls contains a wildcard entry. This means Copilot CLI will fetch content from any URL at all, including one an attacker controls, with nothing in this config blocking it.\"\n" +
        "  BAD example (never write like this): \"This setting could allow for unauthorized execution of code.\"\n" +
        "evidence MUST be the single offending \"key\":value pair exactly as it appears in the input (e.g. \"disableAllHooks\":true or \"Bash(rm -rf *)\") — never the bare value, and never a whole array or section; cite only the one entry that triggered this finding.\n" +
        "overview: markdown with two \"## \" headed sections, each heading on its own line with a blank line between them: \"## What is this?\" (what the field controls in the tool's permission/sandbox model and what this value enables — explain the field in plain terms, don't just restate the value) and \"## Why is it dangerous?\" (a concrete agentic attack chain — how prompt injection, a malicious MCP tool/skill, or a poisoned repo file makes the agent exploit this autonomously, bypassing the guardrail this value removes, ending with the impact) — as many sentences as needed to be precise and concrete.\n" +
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

# Akto Guardrails for Codex CLI (bash)

Validates prompts and tool calls against Akto AI Guardrails in OpenAI Codex CLI.

`PreToolUse`/`PostToolUse` fire for built-in tools (`Bash`, `apply_patch`)
and for MCP tool calls (`mcp__<server>__<tool>`).

Requires only `bash`, `curl`, and `jq` — all preinstalled on macOS (Sequoia
and later ship `jq` natively); on Linux, `apt install jq` / `dnf install jq`
/ `apk add jq` if missing. Every hook checks for these at startup and fails
open (allows the request, logs a clear error) if either is missing, rather
than breaking silently.

## Prerequisites

Hooks are enabled by default in current Codex CLI releases. To explicitly
disable hooks instead: `[features] hooks = false` in `~/.codex/config.toml`.

If your `~/.codex/config.toml` has `[features] codex_hooks = true` from an
older setup, remove that line — it's a deprecated alias for
`[features] hooks = true` and Codex nags about it
(`deprecated: [features].codex_hooks is deprecated`) on every single run
otherwise:

```bash
sed -i '' '/^codex_hooks = true$/d' ~/.codex/config.toml   # macOS/BSD sed
# sed -i '/^codex_hooks = true$/d' ~/.codex/config.toml    # GNU sed (Linux)
```

Back up `config.toml` first if you're doing this by hand instead — it's a
shared file Codex also uses for models, MCP servers, plugins, and trusted
project paths, so a mistake here isn't limited to hooks.

`hooks.json` wires all 11 hook event names found in the installed Codex CLI
binary's own hook-schema (`codex --version` 0.147.0):

- **Validating** (guardrails, can block): `UserPromptSubmit`, `PreToolUse`,
  `PostToolUse`, `Stop`
- **Observability-only** (fire-and-forget via `akto-hooks.sh`):
  `SessionStart`, `SessionEnd`, `PermissionRequest`, `PreCompact`,
  `PostCompact`, `SubagentStart`, `SubagentStop`

Only the 4 validating events go through the real guardrails/ingestion
scripts; the other 8 are dispatched straight to `akto-hooks.sh <hookName>`
for ingestion only — they never block, since `akto-hooks.sh` is
fire-and-forget.

## Setup

### 1. Copy the hooks

```bash
mkdir -p ~/.codex/hooks
cp codex-cli-hooks/*.sh ~/.codex/hooks/
chmod +x ~/.codex/hooks/*.sh
```

Files copied:

- `akto-validate-prompt.sh`
- `akto-validate-response.sh`
- `akto-validate-pre-tool.sh`
- `akto-validate-post-tool.sh`
- `akto-hooks.sh`
- `akto-hook-wrapper.sh`
- `akto-validate-prompt-wrapper.sh`
- `akto-validate-response-wrapper.sh`
- `akto-validate-pre-tool-wrapper.sh`
- `akto-validate-post-tool-wrapper.sh`
- `akto_common.sh` — shared helpers (logging, machine-id/username,
  session-state, HTTP POST, warn/resubmit state) that every other script
  sources; keep it in the same directory as the rest

### 2. Configure environment

Edit the `export` lines at the top of each `*-wrapper.sh`:

```bash
export AKTO_DATA_INGESTION_URL="ingestion-service-url"
export AKTO_API_TOKEN=""             # optional: sent as the Authorization header
export AKTO_SYNC_MODE="true"
export AKTO_TIMEOUT="5"
export MODE="atlas"                  # atlas (default) or argus
export DEVICE_ID=""                  # optional, atlas mode / MCP mirroring only

export LOG_DIR="~/.codex/akto/logs"  # optional
export LOG_LEVEL="INFO"              # optional
export LOG_PAYLOADS="false"          # optional

export AKTO_INGEST_NON_MCP_TOOLS="false"   # built-in Bash/apply_patch tool mirror/ingest, off by default
```

Alternatively, point every wrapper at a single `.env` file instead of
duplicating values across five scripts — replace each wrapper's `export`
block with:

```bash
set -a
source "$(dirname "${BASH_SOURCE[0]}")/.env"
set +a
```

and keep `AKTO_DATA_INGESTION_URL`/`AKTO_API_TOKEN`/etc. in that one
`~/.codex/hooks/.env` file (`chmod 600` it, since it holds your token).

The Codex API host/path used as the mirrored `host` header (non-MCP
traffic only) are auto-detected from the same env vars Codex CLI itself
reads, in this order: `OPENAI_BASE_URL` set → `https://<that host>` +
`/v1/responses`; else `OPENAI_API_KEY` set → `https://api.openai.com` +
`/v1/responses`; else → `https://chatgpt.com` +
`/backend-api/codex/responses`.

### 3. Add hooks to Codex CLI

Copy `hooks.json` from this directory into `~/.codex/hooks.json`, or merge
its `hooks` block into your existing config:

```json
{
  "hooks": {
    "UserPromptSubmit": [
      { "hooks": [{ "type": "command", "command": "bash ~/.codex/hooks/akto-validate-prompt-wrapper.sh", "timeout": 10 }] }
    ],
    "Stop": [
      { "hooks": [{ "type": "command", "command": "bash ~/.codex/hooks/akto-validate-response-wrapper.sh", "timeout": 10 }] }
    ],
    "PreToolUse": [
      { "hooks": [{ "type": "command", "command": "bash ~/.codex/hooks/akto-validate-pre-tool-wrapper.sh", "timeout": 10 }] }
    ],
    "PostToolUse": [
      { "hooks": [{ "type": "command", "command": "bash ~/.codex/hooks/akto-validate-post-tool-wrapper.sh", "timeout": 10 }] }
    ],
    "SessionStart": [
      { "hooks": [{ "type": "command", "command": "bash ~/.codex/hooks/akto-hook-wrapper.sh akto-hooks.sh SessionStart", "timeout": 10 }] }
    ]
  }
}
```

Codex CLI discovers hooks from (in order of precedence): `~/.codex/hooks.json`,
inline `[hooks]` tables in `~/.codex/config.toml`, `<repo>/.codex/hooks.json`,
`<repo>/.codex/config.toml`. Per-repo config only applies inside repos you've
marked trusted.

### 4. Trust the hooks

Codex CLI requires new or changed hook commands to be reviewed before they
run — installing or editing `hooks.json` does **not** make them active by
itself. Start an interactive session and run the `/hooks` command to
inspect and trust each command; Codex records the decision as a hash of the
exact command string in `~/.codex/config.toml`'s `[hooks.state]` section
(one entry per `hooks.json path : event : array index : hook index`), so
you only need to do this once per command — edit a command's text later
(e.g. change the script path) and you'll need to re-trust it, since the
hash changes.

For one-off non-interactive testing (`codex exec`), skip the trust
requirement for that single invocation instead:

```bash
codex exec --dangerously-bypass-hook-trust "your prompt"
```

This does not persist trust — plain interactive `codex` sessions will still
prompt until you run `/hooks`.

### 5. Restart Codex CLI

## How it works

### `akto-validate-prompt.sh` — `UserPromptSubmit`

Reads `prompt` (and the common `session_id`/`transcript_path`/`cwd`/`model`/
`permission_mode` fields Codex sends on every hook), validates it against
guardrails, can block:

```json
{ "decision": "block", "reason": "Prompt blocked: <reason>" }
```

Allow: prints nothing, exits `0`.

### `akto-validate-response.sh` — `Stop`

Reads `transcript_path`, `stop_hook_active`, `last_assistant_message`.
Parses the Codex rollout JSONL format
(`{"type":"response_item","payload":{"type":"message","role":"user","content":[...]}}`,
with `input_text`/`output_text`/`text` content blocks) to recover the last
user message, then validates the prompt/response pair and ingests it.

- **Warn-behaviour block** (bypassable by resubmitting):

  ```json
  { "decision": "block", "reason": "Warning!!, response blocked, please review it. Send again to bypass. Reason for blocking: <reason>" }
  ```

- **Strict deny** — ends the turn outright, using `continue`/`stopReason`/
  `systemMessage`, plus the optional `hookSpecificOutput.additionalContext`:

  ```json
  {
    "continue": false, "stopReason": "<reason>", "systemMessage": "Response blocked: <reason>",
    "hookSpecificOutput": { "hookEventName": "Stop", "additionalContext": "<reason>" }
  }
  ```

- Skips the guardrails check (ingests only) when `stop_hook_active` is
  `true`, to avoid a block-retry loop on the `Stop` hook itself.

### `akto-validate-pre-tool.sh` — `PreToolUse`

Reads `tool_name`, `tool_input`. Parses `tool_name` to detect MCP calls
(`mcp__<server>__<tool>`, split on the literal `__` separator) vs. built-in
tools (`Bash`, `apply_patch`, etc.):

- MCP calls are mirrored as a JSON-RPC `tools/call` envelope
  (`{"jsonrpc":"2.0","method":"tools/call","params":{"name":...,"arguments":...},"id":1}`)
  against the MCP mirror host (`<device_id>.<connector>.<mcp_server>`), so
  Akto classifies the traffic as MCP.
- Built-in tools are mirrored as `{"body": <tool_input>, "toolName": "..."}`
  against a path derived from the tool name (`/tool/<normalized-name>` by
  default; see `NON_MCP_TOOL_PATH_PREFIX`/`NON_MCP_INGEST_PATH`).
- Can deny:

  ```json
  { "hookSpecificOutput": { "hookEventName": "PreToolUse", "permissionDecision": "deny", "permissionDecisionReason": "<reason>" } }
  ```

  The deny reason has no `"Tool request blocked: "` prefix on a strict
  deny — it's the raw guardrails reason.

- Blocked non-MCP (built-in tool) requests are **not** re-ingested by
  default — set `AKTO_INGEST_NON_MCP_TOOLS=true` to also mirror those.
- Does **not** support rewriting `tool_input` via `updatedInput` — the
  allow path here is always a plain allow.

### `akto-validate-post-tool.sh` — `PostToolUse`

Reads `tool_name`, `tool_input`, `tool_response`, `tool_use_id`. Same
MCP-vs-built-in mirroring as the pre-tool hook, but wraps the *result* too
(JSON-RPC `result` for MCP, `{"body":{"result":...}}` for built-in). Can
block:

```json
{ "decision": "block", "reason": "Tool result blocked: <reason>", "hookSpecificOutput": { "hookEventName": "PostToolUse", "additionalContext": "<reason>" } }
```

Same `AKTO_INGEST_NON_MCP_TOOLS` gate applies to non-MCP tool results.

### `akto-hooks.sh <hookName>` — `SessionStart` (and any event you add)

Fire-and-forget observability dispatch: ingests whatever fields are on the
hook's stdin JSON, tagged with the hook name, and always prints `{}` /
exits `0` — it never blocks, regardless of the guardrails result.

## Warn vs. block behaviour

A guardrails policy can return one of three behaviours for a violation:

- **block** (default) — denied outright, every time.
- **alert** — allowed through; the violation is recorded server-side only.
- **warn** — denied once, with a message telling the user to resend to
  bypass. The hook fingerprints the exact prompt/tool-call/response (a
  sorted-key SHA-256) and remembers it in a per-hook
  `akto_*_warn_pending.json` file; the *next* identical submission is let
  through and the fingerprint is cleared, so a genuinely new violation is
  blocked again rather than permanently allowed.

## Session correlation

Every mirrored event carries `x-akto-installer-*` headers (session id,
conversation id, current message id, model, transcript path, cwd,
permission mode, hook event name) so the backend can stitch a session's
prompt → tool calls → response into one trace: `session_id` +
`conversation_id` + `generation_id`, with the message id synthesized as a
per-session `<session_id>:<turn_number>` counter, since Codex hooks don't
expose a stable per-turn message id. The whole per-session row is
persisted to `akto_session_state.json` between hook invocations. Every
event also carries `x-akto-installer-user_email`, set to the OS account
running the hook (`get_username()` — `$USER`/`whoami`, resolving `sudo`
back to the invoking user); it's just a label, not a validated email
address.

## Configuration options

| Variable | Default | Description |
|----------|---------|-------------|
| `AKTO_DATA_INGESTION_URL` | (required) | Akto data ingestion service URL |
| `AKTO_API_TOKEN` | (empty) | Sent as the `Authorization` header to `AKTO_DATA_INGESTION_URL` |
| `AKTO_SYNC_MODE` | `true` | Synchronous guardrails mode for blocking hooks |
| `AKTO_TIMEOUT` | `5` | Timeout in seconds for the guardrails/ingestion HTTP call |
| `MODE` | `atlas` | Operation mode: `argus` or `atlas` |
| `DEVICE_ID` | (auto-generated) | Device id used in `atlas`-mode hostnames and in every MCP mirror host |
| `OPENAI_BASE_URL` | (unset) | If set, used to derive the mirrored Codex API host (`argus` mode, non-MCP only) |
| `OPENAI_API_KEY` | (unset) | If set (and `OPENAI_BASE_URL` isn't), mirrored host becomes `api.openai.com` |
| `AKTO_API_URL` | (empty) | Mirrored host used by `akto-hooks.sh`'s observability events in `argus` mode (ignored in `atlas` mode, where the device-id hostname is used instead) |
| `AKTO_CONNECTOR_VALUE` | `codexcli` | Short connector tag used in headers/tags/atlas hostnames |
| `CONTEXT_SOURCE` | `ENDPOINT` | Tag/field describing where traffic originated |
| `AKTO_INGEST_NON_MCP_TOOLS` | `false` | Also mirror blocked/allowed built-in (non-MCP) tool traffic |
| `MCP_INGEST_PATH` | `/mcp` | Mirrored path for MCP `tools/call` traffic |
| `NON_MCP_TOOL_PATH_PREFIX` | `/tool` | Path prefix for built-in tool traffic (`<prefix>/<normalized-tool-name>`) |
| `NON_MCP_INGEST_PATH` | (unset) | Force a fixed path for non-MCP traffic instead of deriving one from the tool name |
| `LOG_DIR` | `~/.codex/akto/logs` | Directory for log files and state files |
| `LOG_LEVEL` | `INFO` | Accepted for compatibility; every hook call is currently logged regardless of level |
| `LOG_PAYLOADS` | `false` | Log full prompt/response/tool-payload previews instead of truncated ones |

## Viewing logs

Default log directory: `~/.codex/akto/logs/`

- `validate-prompt.log` — prompt validation
- `validate-response.log` — conversation ingestion
- `validate-pre-tool.log` — tool request validation
- `validate-post-tool.log` — tool response ingestion
- `hook-executions.log` — every event dispatched via `akto-hooks.sh` (`SessionStart`, plus anything else you wire to it)

Plus state files in the same directory: `akto_session_state.json` (session
correlation) and `akto_prompt_warn_pending.json` /
`akto_response_warn_pending.json` / `akto_pretool_warn_pending.json` /
`akto_posttool_warn_pending.json` (warn-then-resubmit-to-bypass tracking,
one per hook).

Tail all logs:

```bash
tail -f ~/.codex/akto/logs/*.log
```

## Uninstall

**Disable (reversible)** — rename `hooks.json` out of the way; Codex CLI
only discovers it by that exact filename, so this switches every Akto hook
off without deleting anything:

```bash
mv ~/.codex/hooks.json ~/.codex/hooks.json.disabled
```

Restart Codex CLI. Reverse it with
`mv ~/.codex/hooks.json.disabled ~/.codex/hooks.json`. If `hooks.json` also
carries non-Akto hooks, remove only the Akto entries (the ones whose
`command` points at `~/.codex/hooks/akto-*`) with `jq` instead of renaming
the whole file. Don't use `[features] hooks = false` in `config.toml` for
this — that disables *all* Codex hooks, not just Akto's.

**Full removal** — also delete the copied scripts, logs, and state:

```bash
rm -rf ~/.codex/hooks ~/.codex/hooks.json ~/.codex/akto
```

## Troubleshooting

### Hooks not executing

1. **Check trust first** — this is the most common Codex-specific cause.
   Installing/editing `hooks.json` does not activate a hook; run `/hooks`
   in an interactive `codex` session and confirm each Akto command shows
   as trusted. A changed command (different path, different args) needs
   re-trusting.
2. Verify hook config path: `~/.codex/hooks.json`
3. Confirm `jq` and `curl` are on `PATH`: `jq --version && curl --version`
4. Verify environment vars are actually set inside the wrapper scripts (or
   your `.env`): `grep AKTO_DATA_INGESTION_URL ~/.codex/hooks/*.sh`
5. Ensure scripts are executable: `chmod +x ~/.codex/hooks/*.sh`
6. Check logs: `tail -f ~/.codex/akto/logs/*.log`
7. If `jq`/`curl` is missing, the hook logs `Akto hook disabled: missing
   required command(s): ...` and fails open (allows) instead of erroring —
   look for that line specifically.
8. For a quick non-interactive smoke test that bypasses the trust check:
   `codex exec --dangerously-bypass-hook-trust --skip-git-repo-check "say OK"`
   and watch for `hook: UserPromptSubmit` / `hook: Stop` lines in its output.

### Service unavailable errors

Every hook fails open on any internal error — missing dependency, network
error, non-2xx guardrails/ingestion response, or invalid JSON input all
result in the request being allowed, with the failure logged rather than
silently swallowed.

Inspect failures:

```bash
grep "API CALL FAILED" ~/.codex/akto/logs/*.log
```

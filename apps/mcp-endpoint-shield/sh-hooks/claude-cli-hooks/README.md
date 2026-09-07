# Akto Guardrails for Claude CLI (bash)

Validates prompts and MCP tool calls against Akto AI Guardrails in Claude CLI.

Requires only `bash`, `curl`, and `jq` — all preinstalled on macOS (Sequoia and
later ship `jq` natively); on Linux, `apt install jq` / `dnf install jq` /
`apk add jq` if missing. Every hook checks for these at startup and fails
open (allows the request, logs a clear error) if either is missing, rather
than breaking silently.

## Setup

### 1. Copy the hooks

```bash
mkdir -p ~/.claude/hooks
cp claude-cli-hooks/*.sh ~/.claude/hooks/
chmod +x ~/.claude/hooks/*.sh
```

Files copied:

- `akto-validate-prompt.sh`
- `akto-validate-response.sh`
- `akto-validate-mcp-request.sh`
- `akto-validate-mcp-response.sh`
- `akto-hooks.sh`
- `akto-hook-wrapper.sh`
- `akto-validate-prompt-wrapper.sh`
- `akto-validate-response-wrapper.sh`
- `akto-validate-mcp-request-wrapper.sh`
- `akto-validate-mcp-response-wrapper.sh`
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
export MODE="argus"                  # argus (default) or atlas
export DEVICE_ID=""                  # optional, atlas mode / MCP mirroring only

export LOG_DIR="~/.claude/akto/logs" # optional
export LOG_LEVEL="INFO"              # optional
export LOG_PAYLOADS="false"          # optional
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
`~/.claude/hooks/.env` file (`chmod 600` it, since it holds your token).

### 3. Add hooks to Claude CLI

Copy `settings.json` from this directory into `~/.claude/settings.json`, or
merge its `hooks` block into your existing settings:

```json
{
  "hooks": {
    "UserPromptSubmit": [
      { "hooks": [{ "type": "command", "command": "bash ~/.claude/hooks/akto-validate-prompt-wrapper.sh", "timeout": 10 }] }
    ],
    "Stop": [
      { "hooks": [{ "type": "command", "command": "bash ~/.claude/hooks/akto-validate-response-wrapper.sh", "timeout": 10 }] }
    ],
    "PreToolUse": [
      { "hooks": [{ "type": "command", "command": "bash ~/.claude/hooks/akto-validate-mcp-request-wrapper.sh", "timeout": 10 }] }
    ],
    "PostToolUse": [
      { "hooks": [{ "type": "command", "command": "bash ~/.claude/hooks/akto-validate-mcp-response-wrapper.sh", "timeout": 10 }] }
    ]
  }
}
```

The full `settings.json` in this directory wires all 33 Claude Code hook
events — everything beyond the four validating hooks above (`SessionStart`,
`SessionEnd`, `SubagentStop`, `PreCompact`, `WorktreeCreate`, etc.) is
dispatched to `akto-hooks.sh <hookName>` for fire-and-forget observability
ingestion; none of those can block.

### 4. Restart Claude CLI

```bash
claude
```

## How it works

### Prompt hooks

#### Before prompt submit (`akto-validate-prompt.sh`)

- Trigger: `UserPromptSubmit`
- Validates the prompt against Akto guardrails
- Can block

Block response — both the legacy top-level `decision`/`reason` fields and
the current `hookSpecificOutput`-nested fields are set, so this works
whichever schema version your Claude Code build honors:

```json
{
  "decision": "block", "reason": "Prompt blocked: <reason>",
  "hookSpecificOutput": {
    "hookEventName": "UserPromptSubmit", "continue": false,
    "additionalContext": "Prompt blocked: <reason>",
    "systemMessage": "Prompt blocked: <reason>"
  }
}
```

Allow: prints nothing, exits `0`.

#### After response (`akto-validate-response.sh`)

- Trigger: `Stop`
- Reads the transcript file, extracts the last user message and
  `last_assistant_message`, validates the pair against guardrails, then
  ingests it
- Can block, using the same dual legacy + `hookSpecificOutput` shape as the
  prompt hook (with `stopReason` also set, since `Stop` supports it):

  ```json
  {
    "decision": "block", "reason": "Response blocked: <reason>",
    "hookSpecificOutput": {
      "hookEventName": "Stop", "continue": false, "stopReason": "<reason>",
      "additionalContext": "Response blocked: <reason>",
      "systemMessage": "Response blocked: <reason>"
    }
  }
  ```

- Skips the guardrails check (ingests only) when `stop_hook_active` is
  `true`, to avoid a block-retry loop on the `Stop` hook itself

### MCP tool hooks

#### Before tool execution (`akto-validate-mcp-request.sh`)

- Trigger: `PreToolUse`
- Parses `tool_name` to detect MCP calls (`mcp__<server>__<tool>`) vs.
  built-in tools (`Bash`, `Write`, `Read`, etc.)
- MCP calls are mirrored as a JSON-RPC `tools/call` envelope so Akto
  classifies the traffic as MCP; built-in tools are mirrored as
  `{"body": <tool_input>, "toolName": "..."}` against a path derived from
  the tool name (`/tool/<normalized-name>` by default)
- Can deny:

  ```json
  { "hookSpecificOutput": { "hookEventName": "PreToolUse", "permissionDecision": "deny", "permissionDecisionReason": "Tool request blocked: <reason>" } }
  ```

- Can also **allow with a rewritten `tool_input`**, when guardrails returns
  `Modified: true` + a `ModifiedPayload`:

  ```json
  { "hookSpecificOutput": { "hookEventName": "PreToolUse", "permissionDecision": "allow", "permissionDecisionReason": "Tool request allowed (Akto guardrails)", "updatedInput": { "...": "..." } } }
  ```

- Blocked non-MCP (built-in tool) requests are **not** re-ingested by
  default — set `AKTO_INGEST_NON_MCP_TOOLS=true` to also mirror those

#### After tool execution (`akto-validate-mcp-response.sh`)

- Trigger: `PostToolUse`
- Validates the tool result against guardrails, then ingests it
- MCP calls are mirrored as JSON-RPC `tools/call` + `result`; built-in
  tools as `{"body": {"toolName": ..., "toolArgs": ...}}` /
  `{"body": {"result": ...}}`
- Can block:

  ```json
  { "decision": "block", "reason": "Tool result blocked: <reason>", "hookSpecificOutput": { "hookEventName": "PostToolUse", "additionalContext": "<reason>" } }
  ```

- Same `AKTO_INGEST_NON_MCP_TOOLS` gate applies to non-MCP tool results

### Warn vs. block behaviour

A guardrails policy can return one of three behaviours for a violation:

- **block** (default) — denied outright, every time.
- **alert** — allowed through; the violation is recorded server-side only.
- **warn** — denied once, with a message telling the user to resend to
  bypass (`"Warning!!, ... Send again to bypass. Reason for blocking: ..."`).
  The hook fingerprints the exact prompt/tool-call/response (a sorted-key
  SHA-256) and remembers it in a per-hook `akto_*_warn_pending.json` file;
  the *next* identical submission is let through and the fingerprint is
  cleared, so a genuinely new violation is blocked again rather than
  permanently allowed.

### Session correlation

Every mirrored event carries `x-akto-installer-*` headers (session id,
current message id, transcript path, cwd, etc.) so the backend can stitch a
session's prompt → tool calls → response into one trace. The current
message id is derived from the latest transcript entry's `uuid`, and the
whole per-session row is persisted to `akto_session_state.json` between
hook invocations.

## Configuration options

| Variable | Default | Description |
|----------|---------|-------------|
| `AKTO_DATA_INGESTION_URL` | (required) | Akto data ingestion service URL |
| `AKTO_API_TOKEN` | (empty) | Sent as the `Authorization` header to `AKTO_DATA_INGESTION_URL` |
| `AKTO_SYNC_MODE` | `true` | Synchronous guardrails mode for blocking hooks |
| `AKTO_TIMEOUT` | `5` | Timeout in seconds for the guardrails/ingestion HTTP call |
| `MODE` | `argus` | Operation mode: `argus` or `atlas` |
| `DEVICE_ID` | (auto-generated) | Device id used in `atlas`-mode hostnames and in every MCP mirror host |
| `CLAUDE_API_URL` | `https://api.anthropic.com` | Claude API URL used as the mirrored host (`argus` mode, non-MCP only) |
| `AKTO_CONNECTOR_VALUE` | `claudecli` | Short connector tag used in headers/tags/atlas hostnames |
| `CONTEXT_SOURCE` | `ENDPOINT` | Tag/field describing where traffic originated |
| `AKTO_INGEST_NON_MCP_TOOLS` | `false` | Also mirror blocked/allowed built-in (non-MCP) tool traffic |
| `MCP_INGEST_PATH` | `/mcp` | Mirrored path for MCP `tools/call` traffic |
| `NON_MCP_TOOL_PATH_PREFIX` | `/tool` | Path prefix for built-in tool traffic (`<prefix>/<normalized-tool-name>`) |
| `NON_MCP_INGEST_PATH` | (unset) | Force a fixed path for non-MCP traffic instead of deriving one from the tool name |
| `LOG_DIR` | `~/.claude/akto/logs` | Directory for log files and state files |
| `LOG_LEVEL` | `INFO` | Accepted for compatibility; every hook call is currently logged regardless of level |
| `LOG_PAYLOADS` | `false` | Log full prompt/response/tool-payload previews instead of truncated ones |

## Viewing logs

Default log directory: `~/.claude/akto/logs/`

- `validate-prompt.log` — prompt validation
- `validate-response.log` — conversation ingestion
- `validate-mcp-request.log` — MCP/tool request validation
- `validate-mcp-response.log` — MCP/tool response ingestion
- `hook-executions.log` — every other lifecycle event dispatched via `akto-hooks.sh`

Plus state files in the same directory: `akto_session_state.json` (session
correlation) and `akto_prompt_warn_pending.json` /
`akto_response_warn_pending.json` / `akto_pretool_warn_pending.json` /
`akto_posttool_warn_pending.json` (warn-then-resubmit-to-bypass tracking,
one per hook).

Tail all logs:

```bash
tail -f ~/.claude/akto/logs/*.log
```

## Troubleshooting

### Hooks not executing

1. **Check `~/.claude/settings.json`'s `hooks` key isn't `null`** — some
   Claude Code operations (e.g. a model switch) have been observed to reset
   it, silently disabling every hook with no error. `jq '.hooks' ~/.claude/settings.json`
   should print an object with your event names in it, not `null` or `{}`.
   If it's been reset, re-merge `settings.json` from this directory rather
   than assuming a script bug.
2. Verify hook config path: `~/.claude/settings.json`
3. Confirm `jq` and `curl` are on `PATH`: `jq --version && curl --version`
4. Verify environment vars are actually set inside the wrapper scripts (or
   your `.env`): `grep AKTO_DATA_INGESTION_URL ~/.claude/hooks/*.sh`
5. Ensure scripts are executable: `chmod +x ~/.claude/hooks/*.sh`
6. Check logs: `tail -f ~/.claude/akto/logs/*.log`
7. If `jq`/`curl` is missing, the hook logs `Akto hook disabled: missing
   required command(s): ...` and fails open (allows) instead of erroring —
   look for that line specifically.

### Service unavailable errors

If Akto is unavailable:

- With `AKTO_SYNC_MODE=true`: blocking hooks may deny requests once the
  call fails (an unreachable/non-2xx guardrails response is treated as a
  failure and fails **open**, so requests are actually still allowed — see
  the next point)
- Every hook fails open on any internal error (missing dependency, network
  error, non-2xx response, invalid JSON): the request is allowed and the
  error is logged, never silently swallowed

Inspect failures:

```bash
grep "API CALL FAILED" ~/.claude/akto/logs/*.log
```

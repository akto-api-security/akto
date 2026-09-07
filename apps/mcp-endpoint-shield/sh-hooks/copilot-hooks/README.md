# Akto Guardrails for GitHub Copilot CLI and VS Code Copilot Chat (bash)

Validates prompts and tool calls (including MCP tool calls) against Akto AI
Guardrails. One shared script set and one shared `hooks.json` cover both
GitHub Copilot CLI and VS Code Copilot Chat — both surfaces read hook
configuration from the same `~/.copilot/hooks/hooks.json`, and every script
here auto-detects which surface is calling it per-invocation (VS Code's
payloads carry a `hookEventName`/`hook_event_name` field; GitHub Copilot
CLI's don't), so there is nothing to configure per-surface.

Requires only `bash`, `curl`, and `jq` — all preinstalled on macOS (Sequoia
and later ship `jq` natively); on Linux, `apt install jq` / `dnf install jq`
/ `apk add jq` if missing. Every hook checks for these at startup and fails
open (allows the request, logs a clear error) if either is missing, rather
than breaking silently.

## Setup

### 1. Copy the hooks

```bash
mkdir -p ~/.copilot/hooks/akto
cp copilot-hooks/*.sh ~/.copilot/hooks/akto/
chmod +x ~/.copilot/hooks/akto/*.sh
```

Files copied:

- `akto-validate-prompt.sh`
- `akto-validate-pre-tool.sh`
- `akto-validate-post-tool.sh`
- `akto-hooks.sh`
- `akto-hook-wrapper.sh`
- `akto-validate-prompt-wrapper.sh`
- `akto-validate-pre-tool-wrapper.sh`
- `akto-validate-post-tool-wrapper.sh`
- `akto_common.sh` — shared helpers (logging, machine-id/username,
  session-state, HTTP POST, warn/resubmit state, MCP tool-name parsing,
  connector detection) that every other script sources; keep it
  in the same directory as the rest

### 2. Configure environment

Edit the `export` lines at the top of each `*-wrapper.sh`:

```bash
export AKTO_DATA_INGESTION_URL="ingestion-service-url"
export AKTO_API_TOKEN=""             # optional: sent as the Authorization header
export AKTO_SYNC_MODE="true"
export AKTO_TIMEOUT="5"
export MODE="atlas"                  # atlas (default here) or argus
export DEVICE_ID=""                  # optional

export LOG_DIR="~/.copilot/akto/logs" # shared across both wrapper sets so prompt/response turns correlate
export LOG_LEVEL="INFO"               # optional
export LOG_PAYLOADS="false"           # optional
```

Alternatively, point every wrapper at a single `.env` file instead of
duplicating values across four scripts — replace each wrapper's `export`
block with:

```bash
set -a
source "$(dirname "${BASH_SOURCE[0]}")/.env"
set +a
```

and keep `AKTO_DATA_INGESTION_URL`/`AKTO_API_TOKEN`/etc. in that one
`~/.copilot/hooks/akto/.env` file (`chmod 600` it, since it holds your
token). See `.env.example` for the full list of variables.

Don't set `AKTO_CONNECTOR` in the wrapper scripts themselves — leave it
unset so each script can auto-detect the calling surface from the payload
shape. It's only meant as an override in `.env` for the rare case where the
auto-detected label needs to be forced.

### 3. Register the hooks

Copy `hooks.json` from this directory to `~/.copilot/hooks/hooks.json`, or
merge its `hooks` block into your existing file (this file is shared
between GitHub Copilot CLI and VS Code Copilot Chat — if you already have
other Copilot hooks registered, merge rather than overwrite):

```json
{
  "version": 1,
  "hooks": {
    "userPromptSubmitted": [
      { "type": "command", "bash": "bash $HOME/.copilot/hooks/akto/akto-validate-prompt-wrapper.sh", "timeoutSec": 30 }
    ],
    "preToolUse": [
      { "type": "command", "bash": "bash $HOME/.copilot/hooks/akto/akto-validate-pre-tool-wrapper.sh", "timeoutSec": 30 }
    ],
    "postToolUse": [
      { "type": "command", "bash": "bash $HOME/.copilot/hooks/akto/akto-validate-post-tool-wrapper.sh", "timeoutSec": 30 }
    ]
  }
}
```

The full `hooks.json` in this directory wires all 14 hook events documented
in [GitHub's Copilot CLI hooks reference](https://docs.github.com/en/copilot/reference/hooks-reference)
— everything beyond the three validating hooks above (`sessionStart`,
`sessionEnd`, `agentStop`, `subagentStart`, `subagentStop`,
`errorOccurred`, `preCompact`, `notification`, `userPromptTransformed`,
`postToolUseFailure`, `permissionRequest`) is dispatched to
`akto-hooks.sh <hookName>` for fire-and-forget observability ingestion;
none of those can block. VS Code Copilot Chat skips 4 of these
(`sessionEnd`, `errorOccurred`, `notification`, `userPromptTransformed`) —
it simply never fires them, which is harmless since every event here is
registered against the same generic dispatcher.

### 4. Trust the hooks (GitHub Copilot CLI)

GitHub Copilot CLI requires each hook command to be explicitly trusted
before it will run. Run `copilot` and use the interactive `/hooks` command
to review and trust the four commands above (their SHA-256 hashes get
recorded in Copilot CLI's own config, not in anything from this repo). For
non-interactive use (`copilot -p` / scripted runs), pass
`--dangerously-bypass-hook-trust`. VS Code Copilot Chat prompts for trust
the first time each hook command runs.

### 5. Restart

Restart GitHub Copilot CLI and/or VS Code so the updated `hooks.json` is
picked up.

## How it works

### Connector detection

`detect_connector()` (in `akto_common.sh`) inspects the incoming payload: if
it carries `hookEventName` or `hook_event_name`, the call is treated as
`vscode`; otherwise it falls back to `${AKTO_CONNECTOR:-copilot}`. Both
resolved connectors report under the same `copilot` identity (ai-agent tag,
atlas hostname suffix) — the only things that actually differ per connector
are the mirrored `host` header (`x-vscode-hook` vs `x-copilot-hook`), the
default API URL, and the exit code a deny uses (see below).

### Prompt hook (`akto-validate-prompt.sh`)

- Trigger: `userPromptSubmitted`
- Validates the prompt against Akto guardrails
- Can block:

  ```json
  { "continue": false, "stopReason": "Prompt blocked: <reason>" }
  ```

- Allow: prints nothing, exits `0`
- Blocking exit code depends on the detected connector: `2` for `vscode`
  (VS Code Copilot Chat honors a non-zero exit as a fail-closed deny), `0`
  for everything else (GitHub Copilot CLI has no documented fail-closed
  exit-code contract for `userPromptSubmitted` — the `stopReason` JSON is
  the only signal it gets)

### Tool hooks

#### Before tool execution (`akto-validate-pre-tool.sh`)

- Trigger: `preToolUse`
- Parses `toolName`/`tool_name` to detect MCP calls: the legacy
  `mcp_<server>_<tool>` underscore form, or the CLI's `<server>-<tool>`
  hyphen form (native tools like `bash` or `report_intent` have no hyphen
  and are treated as non-MCP)
- MCP calls are mirrored as a JSON-RPC `tools/call` envelope so Akto
  classifies the traffic as MCP; non-MCP tools are mirrored as
  `{"body": {"toolName": ..., "toolArgs": ...}}` against
  `/copilot/tool/<toolName>`
- Can deny:

  ```json
  {
    "permissionDecision": "deny",
    "permissionDecisionReason": "Blocked by Akto Guardrails: <reason>",
    "hookSpecificOutput": { "permissionDecision": "deny", "permissionDecisionReason": "<reason>" }
  }
  ```

- Allow: prints nothing, exits `0` — there is no rewritten-tool-input path
  here (unlike some other connectors' `PreToolUse`)
- Blocking exit code: same per-connector rule as the prompt hook

#### After tool execution (`akto-validate-post-tool.sh`)

- Trigger: `postToolUse`
- Validates the tool result against guardrails, then ingests it
- MCP calls are mirrored as JSON-RPC `tools/call` + `result`; non-MCP tools
  as `{"body": {"toolName": ..., "toolArgs": ...}}` /
  `{"body": {"result": ...}}` (GitHub Copilot CLI's response body also
  includes `resultType`; VS Code's doesn't)
- Can block:

  ```json
  { "decision": "block", "reason": "<alert message>", "output": "<alert message>" }
  ```

  Always exits `0` — a `postToolUse` hook can only warn the model via this
  message, it can't undo an already-completed tool call

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
current message id, cwd, etc.) so the backend can stitch a session's
prompt → tool calls → response into one trace. Neither surface exposes a
stable per-turn message id on the hook payload itself, so the prompt hook
synthesizes one as a per-session `<session_id>:<turn_number>` counter and
the tool hooks read it back — the whole per-session row is persisted to
`akto_session_state.json` between hook invocations, shared across both
surfaces since they use the same `LOG_DIR`.

### Observability events (`akto-hooks.sh`)

Every event besides the three validating hooks above is dispatched here and
just mirrors the raw hook payload for ingestion — no validation, never
blocks. `agentStop` is the one exception: it reads the turn's transcript
file (retrying briefly if the write hasn't landed yet), extracts the last
user prompt and the assistant's response, and ingests that pair instead of
the raw payload; if no conversational content is found it falls back to
ingesting the payload metadata only.

## Configuration options

| Variable | Default | Description |
|----------|---------|-------------|
| `AKTO_DATA_INGESTION_URL` | (required) | Akto data ingestion service URL |
| `AKTO_API_TOKEN` | (empty) | Sent as the `Authorization` header to `AKTO_DATA_INGESTION_URL` |
| `AKTO_SYNC_MODE` | `true` | Synchronous guardrails mode; when `false`, the validating hooks skip guardrails and ingestion entirely |
| `AKTO_TIMEOUT` | `5` | Timeout in seconds for the guardrails/ingestion HTTP call |
| `MODE` | `atlas` | Operation mode: `argus` or `atlas` |
| `DEVICE_ID` | (auto-generated) | Device id used in `atlas`-mode hostnames and MCP mirror hosts |
| `AKTO_CONNECTOR` | (auto-detected) | Overrides the non-`vscode` fallback connector label (default `copilot`); never used when the payload identifies itself as `vscode` |
| `GITHUB_COPILOT_API_URL` | `https://api.github.com` | Mirrored host for non-MCP GitHub Copilot CLI traffic (`argus` mode) |
| `VSCODE_API_URL` | `https://vscode.dev` | Mirrored host for non-MCP VS Code traffic (`argus` mode) |
| `CONTEXT_SOURCE` | `ENDPOINT` | Tag/field describing where traffic originated |
| `MCP_INGEST_PATH` | `/mcp` | Mirrored path for MCP `tools/call` traffic |
| `LOG_DIR` | `~/.copilot/akto/logs` | Directory for log files and state files — shared between both wrapper sets |
| `LOG_LEVEL` | `INFO` | Accepted for compatibility; every hook call is currently logged regardless of level |
| `LOG_PAYLOADS` | `false` | Log full prompt/response/tool-payload previews instead of truncated ones |

## Viewing logs

Default log directory: `~/.copilot/akto/logs/`

- `validate-prompt.log` — prompt validation
- `validate-pre-tool.log` — tool request validation
- `validate-post-tool.log` — tool result validation/ingestion
- `hook-executions.log` — every other lifecycle event dispatched via `akto-hooks.sh`

Plus state files in the same directory: `akto_session_state.json` (session
correlation), `akto_prompt_warn_pending.json` /
`akto_pretool_warn_pending.json` / `akto_posttool_warn_pending.json`
(warn-then-resubmit-to-bypass tracking, one per hook).

Tail all logs:

```bash
tail -f ~/.copilot/akto/logs/*.log
```

## Troubleshooting

### Hooks not executing

1. Confirm `~/.copilot/hooks/hooks.json` actually contains your entries:
   `jq '.hooks | keys' ~/.copilot/hooks/hooks.json`
2. Confirm `jq` and `curl` are on `PATH`: `jq --version && curl --version`
3. Verify environment vars are actually set inside the wrapper scripts (or
   your `.env`): `grep AKTO_DATA_INGESTION_URL ~/.copilot/hooks/akto/*.sh`
4. Ensure scripts are executable: `chmod +x ~/.copilot/hooks/akto/*.sh`
5. GitHub Copilot CLI: make sure the hook commands are trusted (`/hooks`
   inside `copilot`, or `--dangerously-bypass-hook-trust` for scripted runs)
6. Check logs: `tail -f ~/.copilot/akto/logs/*.log`
7. If `jq`/`curl` is missing, the hook logs `Akto hook disabled: missing
   required command(s): ...` and fails open (allows) instead of erroring —
   look for that line specifically.

### Service unavailable errors

If Akto is unavailable:

- Every hook fails open on any internal error (missing dependency, network
  error, non-2xx response, invalid JSON): the request is allowed and the
  error is logged, never silently swallowed

Inspect failures:

```bash
grep "API CALL FAILED" ~/.copilot/akto/logs/*.log
```

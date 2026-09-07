# Akto Guardrails for Claude CLI (bash)

Validates prompts and MCP tool calls against Akto AI Guardrails in Claude CLI.

Requires `bash`, `curl`, and `jq` (all preinstalled on macOS; on Linux,
`apt install jq` / `yum install jq` if missing).

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
- `akto_common.sh` — shared helpers every other script sources

### 2. Configure environment

Edit the `export` lines at the top of each `*-wrapper.sh` (or set these in
your shell profile before Claude CLI launches):

```bash
export AKTO_DATA_INGESTION_URL="ingestion-service-url"
export AKTO_SYNC_MODE="true"
export MODE="argus"          # argus (default) or atlas
export DEVICE_ID=""          # optional, atlas mode only

export LOG_DIR="~/.claude/akto/logs"   # optional
export LOG_LEVEL="INFO"               # optional
export LOG_PAYLOADS="false"           # optional
```

### 3. Add hooks to Claude CLI

Copy `settings.json` from this directory into `~/.claude/settings.json` (or
merge its `hooks` block into your existing settings).

### 4. Restart Claude CLI

```bash
claude
```

## How it works

- `akto-validate-prompt.sh` (`UserPromptSubmit`) — validates prompts against
  guardrails, can block (`{"decision":"block","reason":"..."}`).
- `akto-validate-response.sh` (`Stop`) — reads the transcript, ingests the
  prompt/response pair, can block via the same `decision: block` shape.
- `akto-validate-mcp-request.sh` (`PreToolUse`) — validates MCP and built-in
  tool calls, can block or rewrite `tool_input` via `updatedInput` when
  guardrails return a `ModifiedPayload`.
- `akto-validate-mcp-response.sh` (`PostToolUse`) — validates/ingests tool
  results.
- `akto-hooks.sh <hookName>` — fire-and-forget observability dispatch for
  every other lifecycle hook in `settings.json` (`SessionStart`, `Stop`,
  `SubagentStop`, etc.).

All hooks fail open: if `AKTO_DATA_INGESTION_URL` is unset, `jq`/`curl` are
missing, or the guardrails call errors, the request is allowed and the error
is logged to `$LOG_DIR/*.log`.

# Akto Guardrails for Codex CLI (bash)

Validates prompts and tool calls against Akto AI Guardrails in OpenAI Codex CLI.

> **Note:** Codex CLI currently only supports the `Bash` tool for
> `PreToolUse`/`PostToolUse` hooks.

Requires `bash`, `curl`, and `jq` (all preinstalled on macOS; on Linux,
`apt install jq` / `yum install jq` if missing).

## Prerequisites

Codex CLI hooks are experimental. Enable them in `~/.codex/config.toml`:

```toml
[features]
codex_hooks = true
```

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
- `akto_common.sh` — shared helpers every other script sources

### 2. Configure environment

Edit the `export` lines at the top of each `*-wrapper.sh` (or set these in
your shell profile before Codex CLI launches):

```bash
export AKTO_DATA_INGESTION_URL="ingestion-service-url"
export AKTO_SYNC_MODE="true"
export MODE="argus"          # argus (default) or atlas
export DEVICE_ID=""          # optional, atlas mode only

export LOG_DIR="~/.codex/akto/logs"   # optional
export LOG_LEVEL="INFO"               # optional
export LOG_PAYLOADS="false"           # optional

export AKTO_INGEST_NON_MCP_TOOLS="false"   # built-in Bash tool mirror/ingest, off by default
```

The Codex API host/path are auto-detected from the same env vars Codex CLI
itself uses: `OPENAI_BASE_URL` (if set) → `OPENAI_API_KEY` set →
`api.openai.com` → otherwise `chatgpt.com`'s backend path.

### 3. Add hooks to Codex CLI

Copy `hooks.json` from this directory into `~/.codex/hooks.json` (or merge
its `hooks` block into your existing config).

### 4. Restart Codex CLI

## How it works

- `akto-validate-prompt.sh` (`UserPromptSubmit`) — validates prompts against
  guardrails, can block (`{"decision":"block","reason":"..."}`).
- `akto-validate-response.sh` (`Stop`) — reads the transcript, ingests the
  prompt/response pair. A `warn`-behaviour block uses `decision: block`; a
  strict deny uses `{"continue":false,"stopReason":...,"systemMessage":...}`
  to end the turn (per the Codex hooks contract).
- `akto-validate-pre-tool.sh` (`PreToolUse`) — validates Bash/`apply_patch`
  and MCP tool calls (wrapped as JSON-RPC `tools/call` so Akto classifies
  them as MCP traffic), can deny via `hookSpecificOutput`.
- `akto-validate-post-tool.sh` (`PostToolUse`) — validates/ingests tool
  results.
- `akto-hooks.sh <hookName>` — fire-and-forget observability dispatch
  (`SessionStart`).

All hooks fail open: if `AKTO_DATA_INGESTION_URL` is unset, `jq`/`curl` are
missing, or the guardrails call errors, the request is allowed and the error
is logged to `$LOG_DIR/*.log`.

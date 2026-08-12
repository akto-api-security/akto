# Akto Guardrails for Amp

Validate prompts, tool calls, and MCP tool calls against Akto AI Guardrails in
[Amp](https://ampcode.com).

> **Why a plugin and not a `hooks.json`:** Amp has no shell-command hook
> mechanism. Its only interception point is a plugin — a TypeScript module loaded
> from `.amp/plugins/` and executed by Bun. `akto-guardrails-plugin.ts` is that
> entry point; all validation logic stays in the same Python scripts the other
> Akto connectors use.

Verified against Amp `0.0.1786450425`.

## Prerequisites

- Amp CLI (`amp --version`)
- Python 3 (`python3 --version`)

## Setup

### 1. Install the plugin

Amp auto-discovers plugins — there is no config entry to add. Copy the plugin and
its validators into either location:

```bash
# System-wide (all projects)
mkdir -p ~/.config/amp/plugins
cp akto-guardrails-plugin.ts ~/.config/amp/plugins/
cp akto-*.py akto_amp_common.py akto_heartbeat.py akto_machine_id.py ~/.config/amp/plugins/
cp ../shared/akto_ingestion_utility.py ~/.config/amp/plugins/
```

For a single repository, use `<repo>/.amp/plugins/` instead.

The Python files must sit **next to** `akto-guardrails-plugin.ts` — the plugin
resolves them relative to its own path.

### 2. Configure

Amp starts the plugin as a long-lived process and passes on only its own
environment, which a GUI-launched Amp does not inherit from a shell profile. The
config file is therefore the reliable option; environment variables of the same
name still take precedence.

```bash
mkdir -p ~/.config/amp/akto
cat > ~/.config/amp/akto/config << 'EOF'
AKTO_DATA_INGESTION_URL=ingestion-service-url   # required
AKTO_API_TOKEN=your-akto-api-token
AKTO_SYNC_MODE=true      # true = enforce, false = observe only (still ingests)
MODE=argus               # argus (default) or atlas
DEVICE_ID=               # optional: custom device ID in atlas mode
EOF
chmod 600 ~/.config/amp/akto/config
```

Override the path with `AKTO_CONFIG_FILE`. The equivalent shell config also works:

```bash
export AKTO_DATA_INGESTION_URL="ingestion-service-url"
export AKTO_SYNC_MODE="true"
```

Without `AKTO_DATA_INGESTION_URL` the plugin logs a notice and stays inactive.

### 3. Reload Amp

Open the command palette (`Ctrl+O`) and run `plugins: reload`, or restart Amp.
Confirm it loaded:

```bash
amp plugins list
```

## How It Works

| Amp event | Script | Blocking? |
|-----------|--------|-----------|
| `session.start` | `akto-hooks.py SessionStart` | No — audit trail only |
| `agent.start` | `akto-validate-prompt.py` | Yes — cancels the turn |
| `tool.call` | `akto-validate-pre-tool.py` | Yes — rejects the tool call |
| `tool.result` | `akto-validate-post-tool.py` | No — audit trail only |
| `agent.end` | `akto-validate-response.py` | No — audit trail only |

The plugin writes one JSON object to a validator's stdin and reads the decision
off the last stdout line:

```json
{"decision": "block", "reason": "..."}
{"decision": "allow", "updatedInput": {...}}
```

No output means allow.

### Prompt validation (`agent.start`)

Amp's `agent.start` result can only *append* context to a turn, so a real block
is `thread.cancel()`, which Amp documents as preventing the turn from starting.
The user sees the reason via a notification and the prompt never reaches the model.

### Tool validation (`tool.call`)

A block becomes `{ action: 'reject-and-continue' }`: the tool never executes and
the model receives the reason, so it can choose a different route. When Akto
returns a rewritten payload (`Modified` / `ModifiedPayload`), the plugin returns
`{ action: 'modify' }` and the tool runs with the sanitized arguments.

### Warn and alert behaviours

Matching the other connectors:

- `block` — denied outright
- `warn` — denied the first time; submitting the identical prompt or tool call
  again bypasses it (fingerprints persist in `LOG_DIR`)
- `alert` — allowed; Akto records the violation server-side

### MCP support

Amp names MCP tools `mcp__<server>__<tool>`, so MCP and built-in tools are told
apart from the tool name alone:

| Tool type | Example | Mirrored path | Payload |
|-----------|---------|---------------|---------|
| MCP | `mcp__calculator__add` | `/mcp` | JSON-RPC 2.0 `tools/call` |
| Built-in | `shell_command` | `/tool/shell_command` | `{"body": ..., "toolName": ...}` |

MCP traffic is mirrored with host `<device-id>.amp.<server>` and an
`x-mcp-server` header so Akto attributes it to the right server.

Configure MCP servers in Amp's settings (see `settings.json.example`); the plugin
guards whatever servers are configured without any extra wiring.

## Fail-Open Design

Guardrails never wedge the agent. The action is allowed when the validator is
missing, errors, times out, returns unparseable output, or when Akto is
unreachable. Blocking only happens on an explicit deny from Akto.

## Configuration Options

| Variable | Default | Description |
|----------|---------|-------------|
| `AKTO_DATA_INGESTION_URL` | (required) | Akto data ingestion service URL |
| `AKTO_SYNC_MODE` | `true` | `true` enforces guardrails; `false` still ingests every event but never blocks |
| `AKTO_TIMEOUT` | `5` | Timeout in seconds for API calls and validator runs |
| `AKTO_API_TOKEN` | (none) | Sent as the `Authorization` header |
| `MODE` | `argus` | Operation mode: `argus` or `atlas` |
| `DEVICE_ID` | (auto-generated) | Custom device ID for atlas mode |
| `AMP_API_URL` | `https://ampcode.com` | Host recorded for non-MCP traffic |
| `AKTO_PYTHON` | `python3` | Python interpreter used to run validators |
| `AKTO_INGEST_NON_MCP_TOOLS` | `false` | Also ingest blocked non-MCP tool requests |
| `MCP_INGEST_PATH` | `/mcp` | Mirrored path for MCP traffic |
| `NON_MCP_TOOL_PATH_PREFIX` | `/tool` | Mirrored path prefix for built-in tools |
| `LOG_DIR` | `~/.config/amp/akto/logs` | Directory for log files |
| `LOG_LEVEL` | `INFO` | DEBUG, INFO, WARNING, ERROR |
| `LOG_PAYLOADS` | `false` | Log request/response payload previews |

## Viewing Logs

Default log directory: `~/.config/amp/akto/logs/`

- `akto-guardrails.log` — plugin activity (event dispatch, decisions)
- `validate-prompt.log` — prompt validation
- `validate-pre-tool.log` — tool request validation
- `validate-post-tool.log` — tool response ingestion
- `hook-executions.log` — session-start observability
- `validate-response.log` — turn ingestion

```bash
tail -f ~/.config/amp/akto/logs/*.log
```

## Differences from the CLI hook connectors

| Aspect | Claude / Codex CLI | Amp |
|--------|--------------------|-----|
| Mechanism | `hooks.json` running shell commands | TypeScript plugin executed by Bun |
| Install path | `~/.claude/hooks/`, `~/.codex/hooks/` | `~/.config/amp/plugins/` or `.amp/plugins/` |
| Registration | Config file entry | Auto-discovered |
| Prompt block | `{"decision":"block"}` on stdout | `thread.cancel()` |
| Tool block | `permissionDecision: "deny"` | `{ action: 'reject-and-continue' }` |
| Tool arg rewrite | `updatedInput` | `{ action: 'modify' }` |
| MCP tool naming | `mcp__<server>__<tool>` | same |

## Troubleshooting

### Plugin not loading

1. Confirm Amp sees it: `amp plugins list`
2. Confirm the file is at `~/.config/amp/plugins/akto-guardrails-plugin.ts`
3. Reload: command palette (`Ctrl+O`) → `plugins: reload`
4. Check `~/.config/amp/akto/logs/akto-guardrails.log` for a `PLUGIN_INIT` line

### Nothing is being validated

1. Verify the env var is visible to Amp: `echo $AKTO_DATA_INGESTION_URL`
2. A `PLUGIN_INIT` line with `"ingestionConfigured":false` means the variable was
   unset when Amp started — export it and restart Amp
3. Ensure Python 3 is installed: `python3 --version`
4. Look for `SCRIPT_NOT_FOUND` in `akto-guardrails.log` — the `.py` files must sit
   beside the plugin

### Service unavailable errors

Akto being unreachable fails open (everything is allowed). Inspect failures:

```bash
grep "API CALL FAILED" ~/.config/amp/akto/logs/*.log
```

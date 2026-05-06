# VSCode Copilot Hooks — Akto MCP Endpoint Shield

This directory contains the VSCode-specific hook configuration for Akto guardrails. The Python hook scripts are shared with `../github-cli-hooks/` — only the wrappers and hooks registration file live here.

## File placement

| File (this directory) | System destination |
|---|---|
| `hooks.json` | `~/.copilot/hooks/hooks.json` |
| `akto-validate-prompt-wrapper.sh` | `~/.vscode/copilot/hooks/akto/` |
| `akto-validate-pre-tool-wrapper.sh` | `~/.vscode/copilot/hooks/akto/` |
| `akto-validate-post-tool-wrapper.sh` | `~/.vscode/copilot/hooks/akto/` |

The Python scripts below must also be copied from `../github-cli-hooks/` to `~/.vscode/copilot/hooks/akto/`:

| File (from `../github-cli-hooks/`) | System destination |
|---|---|
| `akto-validate-prompt.py` | `~/.vscode/copilot/hooks/akto/` |
| `akto-validate-pre-tool.py` | `~/.vscode/copilot/hooks/akto/` |
| `akto-validate-post-tool.py` | `~/.vscode/copilot/hooks/akto/` |
| `akto_machine_id.py` | `~/.vscode/copilot/hooks/akto/` |
| `akto_heartbeat.py` | `~/.vscode/copilot/hooks/akto/` |

## Setup

### 1. Create the hooks directory
```bash
mkdir -p ~/.vscode/copilot/hooks/akto/logs
```

### 2. Copy wrapper scripts and Python files
```bash
# Wrappers (from this directory)
cp akto-validate-prompt-wrapper.sh ~/.vscode/copilot/hooks/akto/
cp akto-validate-pre-tool-wrapper.sh ~/.vscode/copilot/hooks/akto/
cp akto-validate-post-tool-wrapper.sh ~/.vscode/copilot/hooks/akto/
chmod +x ~/.vscode/copilot/hooks/akto/*.sh

# Python scripts (from ../github-cli-hooks/)
cp ../github-cli-hooks/akto-validate-prompt.py ~/.vscode/copilot/hooks/akto/
cp ../github-cli-hooks/akto-validate-pre-tool.py ~/.vscode/copilot/hooks/akto/
cp ../github-cli-hooks/akto-validate-post-tool.py ~/.vscode/copilot/hooks/akto/
cp ../github-cli-hooks/akto_machine_id.py ~/.vscode/copilot/hooks/akto/
cp ../github-cli-hooks/akto_heartbeat.py ~/.vscode/copilot/hooks/akto/
```

### 3. Fill in wrapper placeholders
Edit each `*-wrapper.sh` and replace:
- `{{AKTO_DATA_INGESTION_URL}}` — your Akto guardrails ingestion URL
- `{{AKTO_API_TOKEN}}` — your Akto API token

### 4. Register hooks with VSCode Copilot
```bash
cp hooks.json ~/.copilot/hooks/hooks.json
```
> If `~/.copilot/hooks/hooks.json` already exists, merge the `hooks` entries rather than overwriting the file — VSCode Copilot writes metadata fields (`firstLaunchAt`, `banner`) to this file.

## Environment variables

| Variable | Default | Description |
|---|---|---|
| `AKTO_DATA_INGESTION_URL` | _(required)_ | Guardrails service base URL |
| `AKTO_TOKEN` | _(required)_ | API token for authentication |
| `AKTO_SYNC_MODE` | `true` | `true` = validate synchronously and block; `false` = ingest only |
| `AKTO_CONNECTOR` | `vscode` | Connector identifier sent with each request |
| `MODE` | `atlas` | Runtime mode (`atlas` or `argus`) |
| `LOG_DIR` | `~/.vscode/copilot/hooks/akto/logs` | Directory for hook log files |
| `LOG_LEVEL` | `INFO` | Log verbosity (`DEBUG`, `INFO`, `WARNING`, `ERROR`) |
| `LOG_PAYLOADS` | `false` | Set to `true` to log full request/response payloads |
| `ENABLE_MCP_HOOKS_VSCODE` | _(unset)_ | Set to `false` to disable all hooks without removing them |

## Disabling hooks
To temporarily disable all hooks without removing them, add the following to `~/.akto-mcp-endpoint-shield/config/config.env`:
```bash
ENABLE_MCP_HOOKS_VSCODE=false
```

## Logs
Hook execution logs are written to `~/.vscode/copilot/hooks/akto/logs/`:
- `validate-prompt.log`
- `validate-pre-tool.log`
- `validate-post-tool.log`

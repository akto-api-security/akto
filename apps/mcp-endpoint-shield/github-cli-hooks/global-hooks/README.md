# GitHub Copilot CLI Hooks — Global Setup

This directory contains wrapper scripts and `hooks.json` for **global** Akto guardrails hooks. These hooks apply to all GitHub Copilot CLI sessions for the current user, regardless of which project is active.

The Python hook scripts are shared with the parent `../` directory — only the wrappers and hooks registration file live here.

## How it works

- `hooks.json` is placed in the GitHub Copilot CLI global hooks directory and references wrapper scripts via absolute `$HOME`-relative paths.
- Wrapper scripts and Python files are placed in `$HOME/github/hooks/`.
- Hooks fire for every GitHub Copilot CLI session for the user.

## File placement

| File (this directory) | System destination |
|---|---|
| `config.json` | `~/.copilot/config.json` |
| `akto-validate-prompt-wrapper.sh` | `~/github/hooks/` |
| `akto-validate-pre-tool-wrapper.sh` | `~/github/hooks/` |
| `akto-validate-post-tool-wrapper.sh` | `~/github/hooks/` |
| `akto-validate-prompt-wrapper.ps1` | `~/github/hooks/` |
| `akto-validate-pre-tool-wrapper.ps1` | `~/github/hooks/` |
| `akto-validate-post-tool-wrapper.ps1` | `~/github/hooks/` |

The Python scripts below must also be copied from `../` to `~/github/hooks/`:

| File (from `../`) | System destination |
|---|---|
| `akto-validate-prompt.py` | `~/github/hooks/` |
| `akto-validate-pre-tool.py` | `~/github/hooks/` |
| `akto-validate-post-tool.py` | `~/github/hooks/` |
| `akto_machine_id.py` | `~/github/hooks/` |
| `akto_heartbeat.py` | `~/github/hooks/` |

## Setup

### 1. Create the global hooks directory

```bash
mkdir -p ~/github/hooks/logs
```

### 2. Copy wrapper scripts and Python files

```bash
# From the root of the akto/apps/mcp-endpoint-shield/github-cli-hooks/ directory:

# Wrappers (from this directory)
cp global-hooks/akto-validate-prompt-wrapper.sh ~/github/hooks/
cp global-hooks/akto-validate-pre-tool-wrapper.sh ~/github/hooks/
cp global-hooks/akto-validate-post-tool-wrapper.sh ~/github/hooks/
cp global-hooks/akto-validate-prompt-wrapper.ps1 ~/github/hooks/
cp global-hooks/akto-validate-pre-tool-wrapper.ps1 ~/github/hooks/
cp global-hooks/akto-validate-post-tool-wrapper.ps1 ~/github/hooks/
chmod +x ~/github/hooks/*.sh

# Python scripts (from ../)
cp akto-validate-prompt.py ~/github/hooks/
cp akto-validate-pre-tool.py ~/github/hooks/
cp akto-validate-post-tool.py ~/github/hooks/
cp akto_machine_id.py ~/github/hooks/
cp akto_heartbeat.py ~/github/hooks/
```

### 3. Fill in wrapper placeholders

Edit each `*-wrapper.sh` and `*-wrapper.ps1` in `~/github/hooks/` and replace:
- `{{AKTO_DATA_INGESTION_URL}}` — your Akto guardrails ingestion URL
- `{{AKTO_API_TOKEN}}` — your Akto API token

### 4. Register hooks with GitHub Copilot CLI

```bash
mkdir -p ~/.copilot
cp global-hooks/config.json ~/.copilot/config.json
```

> If `~/.copilot/config.json` already exists, merge the `hooks` entries rather than overwriting.

## Environment variables

| Variable | Default | Description |
|---|---|---|
| `AKTO_DATA_INGESTION_URL` | _(required)_ | Guardrails service base URL |
| `AKTO_API_TOKEN` | _(required)_ | API token for authentication |
| `AKTO_SYNC_MODE` | `true` | `true` = validate synchronously and block; `false` = ingest only |
| `MODE` | `atlas` | Runtime mode (`atlas` or `argus`) |
| `CONTEXT_SOURCE` | `ENDPOINT` | Source context tag sent with each request |
| `AKTO_TIMEOUT` | `5` | Request timeout in seconds |
| `LOG_DIR` | `~/github/hooks/logs` | Directory for hook log files |
| `LOG_LEVEL` | `INFO` | Log verbosity (`DEBUG`, `INFO`, `WARNING`, `ERROR`) |
| `LOG_PAYLOADS` | `false` | Set to `true` to log full request/response payloads |

## Logs

Hook execution logs are written to `~/github/hooks/logs/`:
- `validate-prompt.log`
- `validate-pre-tool.log`
- `validate-post-tool.log`

# Akto Guardrails for Pi.dev

Validate prompts and MCP tool calls against Akto AI Guardrails in [pi.dev](https://pi.dev).

Pi does **not** run Claude Code-style `hooks` from `settings.json` natively. Akto ships a local Pi extension (`akto-guardrails.ts`) that Pi auto-loads from `~/.pi/agent/extensions/` — **no `pi install` required**.

## Setup

Use the Akto Endpoint Shield installer (`install_pi_hooks.sh`), which:

1. Copies Python hooks + wrappers to `~/.pi/hooks/akto/`
2. Installs `akto-guardrails.ts` to `~/.pi/agent/extensions/`
3. Removes legacy unused `settings.json` hook entries from older installers

After install, restart Pi or run `/reload`.

### Manual layout

| Path | Purpose |
|------|---------|
| `~/.pi/agent/extensions/akto-guardrails.ts` | Pi extension (bridges events → Python hooks) |
| `~/.pi/hooks/akto/*.py` | Guardrail validators |
| `~/.pi/hooks/akto/*-wrapper.sh` | Env + connector wrappers |
| `~/.pi/akto/logs/` | Hook logs (`validate-prompt.log`, etc.) |

### Configure environment

```bash
export AKTO_DATA_INGESTION_URL="ingestion-service-url"
export AKTO_API_TOKEN="your-token"
export AKTO_SYNC_MODE="true"
export MODE="atlas"
export DEVICE_ID="your-device-label"

export LOG_DIR="~/.pi/akto/logs"
export AKTO_CONNECTOR="pi"
export AKTO_CONNECTOR_VALUE="pi"
```

## Hook events

| Event | Script | Blocking |
|-------|--------|----------|
| UserPromptSubmit | `akto-validate-prompt.py` | Yes |
| Stop | `akto-validate-response.py` | Yes |
| PreToolUse | `akto-validate-mcp-request.py` | Yes |
| PostToolUse | `akto-validate-mcp-response.py` | Yes |

Blocking uses Claude Code JSON format (`decision: block`, `permissionDecision: deny`).

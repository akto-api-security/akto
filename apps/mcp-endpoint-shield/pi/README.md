# Akto Guardrails for Pi.dev

Validate prompts and MCP tool calls against Akto AI Guardrails in [pi.dev](https://pi.dev) via the [@hsingjui/pi-hooks](https://github.com/hsingjui/pi-hooks) extension.

Pi uses Claude Code-compatible command hooks in `~/.pi/agent/settings.json`. Install the hook runner first:

```bash
pi install npm:@hsingjui/pi-hooks
```

## Setup

### 1. Copy hook scripts

```bash
mkdir -p ~/.pi/hooks/akto
```

Copy the following files to `~/.pi/hooks/akto/`:

- `akto-validate-prompt.py`
- `akto-validate-response.py`
- `akto-validate-mcp-request.py`
- `akto-validate-mcp-response.py`
- `akto_machine_id.py`
- `akto_ingestion_utility.py` (from `../shared/`)

Also copy and configure the wrapper shell scripts, or use the Akto Endpoint Shield installer.

### 2. Merge hooks into settings

Merge the `hooks` block from `settings.json` in this directory into `~/.pi/agent/settings.json`.

### 3. Configure environment

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

# Akto Guardrails for Claude CLI — Argus Mode

Argus-only Claude CLI hooks. All ingested traffic appears under **Agentic AI** in the Akto dashboard. Use this folder when Claude CLI runs on a server or shared environment where per-device tracking is not needed.

For the per-device atlas variant, see `../claude-cli-hooks/`.

## What Argus Mode Does Differently

| Field | Atlas | Argus (this folder) |
|---|---|---|
| `host` request header | `{DEVICE_ID}.ai-agent.claudecli` | `AKTO_HOST` env var |
| Payload `ip` | system username | local LAN IP |
| Payload `akto_vxlan_id` | machine-id hash | `0` (constant) |
| Payload `contextSource` | `ENDPOINT` | `AGENTIC` |
| Tag `source` | `ENDPOINT` (only in atlas) | `AGENTIC` |

## Setup

### 1. Download files into Claude's hooks dir

```bash
mkdir -p ~/.claude/hooks
HOOKS_BASE="https://raw.githubusercontent.com/akto-api-security/akto/master/apps/mcp-endpoint-shield/claude-cli-hooks-argus"

for f in akto-validate-prompt.py akto-validate-prompt-wrapper.sh \
         akto-validate-response.py akto-validate-response-wrapper.sh \
         akto-validate-mcp-request.py akto-validate-mcp-request-wrapper.sh \
         akto-validate-mcp-response.py akto-validate-mcp-response-wrapper.sh \
         akto_helpers.py; do
  curl -o ~/.claude/hooks/"$f" "${HOOKS_BASE}/${f}"
done

chmod +x ~/.claude/hooks/*.sh
```

### 2. Replace placeholders in the wrapper scripts

```bash
AKTO_URL="https://your-akto-instance.com"
AKTO_TOKEN_VALUE="your-akto-token"
AKTO_HOST_VALUE="api.anthropic.com"   # or your custom host

sed -i.bak "s|{{AKTO_DATA_INGESTION_URL}}|${AKTO_URL}|g" ~/.claude/hooks/*-wrapper.sh
sed -i.bak "s|{{AKTO_TOKEN}}|${AKTO_TOKEN_VALUE}|g"      ~/.claude/hooks/*-wrapper.sh
sed -i.bak "s|{{AKTO_HOST}}|${AKTO_HOST_VALUE}|g"        ~/.claude/hooks/*-wrapper.sh
```

### 3. Register the hooks with Claude CLI

```bash
curl -o ~/.claude/settings.json \
  "https://raw.githubusercontent.com/akto-api-security/akto/master/apps/mcp-endpoint-shield/claude-cli-hooks-argus/settings.json"
```

### 4. Restart Claude CLI

```bash
claude
```

## Configuration Reference

| Variable | Required | Default | Description |
|---|---|---|---|
| `AKTO_DATA_INGESTION_URL` | yes | — | Akto ingestion base URL |
| `AKTO_TOKEN` | yes | — | Authorization header value |
| `AKTO_HOST` | no | `https://api.anthropic.com` | host header in mirrored requests |
| `CONTEXT_SOURCE` | no | `AGENTIC` | payload `contextSource` + `source` tag |
| `AKTO_SYNC_MODE` | no | `true` | `true` blocks on guardrail violation; `false` observe-only |
| `AKTO_TIMEOUT` | no | `5` | request timeout (seconds) |
| `AKTO_CONNECTOR` | no | `claude_code_cli` | dashboard connector identifier |
| `LOG_DIR` | no | `~/.claude/akto/logs` | log directory |
| `LOG_LEVEL` | no | `INFO` | DEBUG / INFO / WARNING / ERROR |
| `LOG_PAYLOADS` | no | `false` | log full request/response payloads |

## Logs

```
~/.claude/akto/logs/
├── validate-prompt.log
├── validate-response.log
├── validate-mcp-request.log
└── validate-mcp-response.log
```

```bash
tail -f ~/.claude/akto/logs/*.log
```

## Verify Installation

```bash
claude "what is 2+2?"
tail -20 ~/.claude/akto/logs/validate-prompt.log
```

Look for:
- `AKTO_HOST: https://...` and `DEVICE_IP: 192.168.x.x` at module load
- `Prompt ALLOWED by guardrails`

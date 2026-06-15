# Akto Cursor Hooks — Native Shell (bash + PowerShell)

Native-shell port of the Akto Cursor guardrails/observability hooks. **No python, no jq,
no third-party libraries** — pure `bash` (3.2+, the macOS default) with `curl`, or native
PowerShell 7+ (with a Windows PowerShell 5.1 fallback).

These mirror the Python hooks in `cursor-hooks/` 1:1 (stdin fields, proxy URL params,
ingestion payload shape, header names, fingerprint form, warn/alert/block flow, and the
exact decision-JSON schema Cursor expects). Connector is fixed to `cursor`.

## Layout

```
cursor-hooks/
├── bash/
│   ├── akto_common.sh                  # shared lib (config, JSON reader, HTTP, guardrails, warn-state)
│   ├── akto-validate-chat-prompt.sh    # beforeSubmitPrompt  → {"continue":bool[,"user_message"]}
│   ├── akto-validate-chat-response.sh  # afterAgentResponse   → {} (observe-only)
│   ├── akto-validate-mcp-request.sh    # beforeMCPExecution   → {"permission":"allow|deny"[,"user_message","agent_message"]}
│   ├── akto-validate-mcp-response.sh   # afterMCPExecution    → {} (observe-only)
│   └── akto-hooks.sh                    # generic observability ingest → {}
├── powershell/
│   ├── AktoCommon.psm1
│   ├── akto-validate-chat-prompt.ps1
│   ├── akto-validate-chat-response.ps1
│   ├── akto-validate-mcp-request.ps1
│   ├── akto-validate-mcp-response.ps1
│   └── akto-hooks.ps1
├── install-shell-hooks.sh              # installs bash hooks + wrappers + ~/.cursor/hooks.json
└── README.md
```

## Install

```bash
./install-shell-hooks.sh --ingestion-url https://<your-akto-host> [--device-id ID] [--api-token TOKEN]
```

This:
1. Copies the bash hooks + `akto_common.sh` into `~/.cursor/hooks/akto/`.
2. Generates **runtime-detecting wrappers**: each wrapper prefers `python3` (if the
   `.py` hook is present and `AKTO_HOOK_RUNTIME` is not `shell`), otherwise runs the
   shell hook. Force shell with `AKTO_HOOK_RUNTIME=shell`.
3. Writes `~/.cursor/hooks.json` (Cursor's registration format, `version: 1`) wiring
   all Cursor events — the four validating hooks plus 16 observability events. The
   `beforeMCPExecution` entry sets `"failClosed": true`, matching the Python `hooks.json`.

An existing `hooks.json` is backed up before being overwritten.

To use the PowerShell hooks instead, point the `hooks.json` commands at
`pwsh -File <hook>.ps1` (Windows / cross-platform deployments).

## Configuration (environment variables)

| Var | Default | Notes |
|-----|---------|-------|
| `AKTO_DATA_INGESTION_URL` | *(empty)* | Required; empty ⇒ fail-open (allow, no ingest). |
| `MODE` | `argus` | `atlas` switches host to `<device>.ai-agent.cursor`. |
| `AKTO_SYNC_MODE` | `true` | `true` ⇒ guardrails enforcement; `false` ⇒ observe-only ingest. |
| `AKTO_CONNECTOR` | `cursor` | Fixed for these hooks. |
| `AKTO_CONNECTOR_VALUE` | `cursor` | Used in MCP host + `mcp-client` tag + atlas suffix. |
| `AKTO_API_TOKEN` | *(empty)* | Sent as `Authorization` header if set. |
| `AKTO_TIMEOUT` | `5` | curl/Invoke-RestMethod timeout (seconds). |
| `DEVICE_ID` | machine id | Mirrors `akto_machine_id.py` resolution. |
| `CONTEXT_SOURCE` | `ENDPOINT` | |
| `MCP_INGEST_PATH` | `/mcp` | MCP traffic path (Akto classifies as MCP). |
| `LOG_DIR` | `~/.cursor/akto/{chat,mcp}-logs` | Chat hooks default to `chat-logs`, MCP hooks to `mcp-logs`. |
| `LOG_LEVEL` | `INFO` | |
| `LOG_PAYLOADS` | `false` | Logs request/response bodies when `true`. |

## Per-hook behaviour & Cursor decision schema

Cursor's hook contract differs from Claude CLI's, so the output JSON differs:

- **`beforeSubmitPrompt`** (`akto-validate-chat-prompt`): reads `prompt` + `attachments`
  from stdin. Validates with `guardrails=AKTO_SYNC_MODE&ingest_data=true`. Emits
  `{"continue": true}` to allow or `{"continue": false, "user_message": "..."}` to block.
  Fingerprint canonical form: `{"a": <attachments>, "p": <prompt>}` (sorted keys).
- **`afterAgentResponse`** (`akto-validate-chat-response`): reads `text`. **Observe-only**
  (`response_guardrails=true&ingest_data=true`) — cannot block; always prints `{}`.
- **`beforeMCPExecution`** (`akto-validate-mcp-request`): reads `tool_name` + `tool_input`
  (a JSON-encoded string, parsed before wrapping) + `url`/`command`/`server`. Resolves the
  MCP server alias from `~/.cursor/mcp.json` and the project `.cursor/mcp.json`, then wraps
  the call in a JSON-RPC 2.0 `tools/call` envelope on `/mcp`. Validates with
  `guardrails=true`. Emits `{"permission":"allow"}` or
  `{"permission":"deny","user_message":"...","agent_message":"..."}`.
  Fingerprint: `{"i": <tool_input>, "t": <tool_name>}` (sorted keys).
- **`afterMCPExecution`** (`akto-validate-mcp-response`): reads `tool_name`, `tool_input`,
  `result_json` (both JSON strings). **Observe-only** (`response_guardrails=true`); JSON-RPC
  result envelope on `/mcp`; always prints `{}`.
- **Observability** (`akto-hooks`): fire-and-forget ingest to `/v1/hooks/<event>` with
  `client_hook=<event>`; always prints `{}`.

### Warn / alert / block flow

Mirrors the Python hooks. `block` ⇒ deny. `alert` ⇒ allow (server-side alert only).
`warn` ⇒ block the first time, record the request fingerprint in
`<log_dir>/akto_*_warn_pending.json`; an identical resubmit clears the fingerprint and is
allowed (bypass-on-resubmit).

## Notes

- **Fail-open**: any guardrails/HTTP error, or unset `AKTO_DATA_INGESTION_URL`, allows the
  action (validating hooks) or skips ingestion (observe-only hooks).
- **bash 3.2**: no associative arrays; arrays guarded under `set -u`. TLS is unverified
  (`curl -k`) to match the Python `ssl._create_unverified_context()` behaviour.
- **PowerShell**: `[ordered]@{}` preserves key order; approved-verb function names; logging
  goes to file + stderr only (stdout stays a single clean JSON object).

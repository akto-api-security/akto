# Akto guardrails for Snowflake Cortex Code CLI

Send Cortex Code CLI prompts and tool use to Akto Atlas (or Argus) using the same `/api/http-proxy` ingestion path as Claude Code CLI, GitHub Copilot CLI, and Cursor hooks.

Upstream reference: [Cortex Code CLI extensibility (hooks)](https://docs.snowflake.com/en/user-guide/cortex-code/extensibility) and [security](https://docs.snowflake.com/en/user-guide/cortex-code/security).

## Prerequisites

- Snowflake Cortex Code CLI installed and working.
- Python 3 available as `python3`.
- Akto data ingestion base URL (`AKTO_DATA_INGESTION_URL`).

## Install (user machine)

1. Copy this entire directory to a stable path, for example:

   ```bash
   mkdir -p ~/.snowflake/cortex/akto-hooks
   cp -R snowflake-cortex-cli-hooks/* ~/.snowflake/cortex/akto-hooks/
   chmod +x ~/.snowflake/cortex/akto-hooks/*.sh
   ```

2. Create `~/.snowflake/cortex/akto-hooks/.env` from [.env.example](.env.example) and set at least `AKTO_DATA_INGESTION_URL` and `MODE` (`atlas` recommended for employee endpoints).

3. Merge hooks into Snowflake Cortex configuration:

   - **Global hooks file:** `~/.snowflake/cortex/hooks.json`
   - **Project file (alternative):** `.cortex/settings.json` or `.cortex/settings.local.json` in the repo root (see Snowflake docs for precedence).

   Start from [hooks.json.example](hooks.json.example): replace every `INSTALL_DIR` with your install path (for example `/Users/you/.snowflake/cortex/akto-hooks`). If you already have a `hooks` object, merge the event keys (`UserPromptSubmit`, `PreToolUse`, `PostToolUse`) so you do not remove existing hook entries.

4. Set restrictive permissions on secrets and config (see Snowflake security guidance):

   ```bash
   chmod 600 ~/.snowflake/cortex/akto-hooks/.env
   chmod 700 ~/.snowflake/cortex/akto-hooks
   ```

## Environment variables

| Variable | Description |
|----------|-------------|
| `AKTO_DATA_INGESTION_URL` | Required. Base URL of Akto ingestion (no trailing slash). |
| `MODE` | `atlas` (default for employees) or `argus`. |
| `DEVICE_ID` | Optional Atlas device override; defaults to generated machine id. |
| `AKTO_SYNC_MODE` | `true` to enforce guardrails where blocking is supported. |
| `AKTO_CONNECTOR` | Defaults to `cortex_code_cli`. |
| `CONTEXT_SOURCE` | Defaults to `ENDPOINT`. |
| `LOG_DIR` | Defaults to `~/.snowflake/cortex/akto/logs`. If that path cannot be created, logs fall back to the system temp directory under `akto-cortex-cli-hooks-logs`. |

## Behaviour

| Hook | Akto | Cortex blocking |
|------|------|------------------|
| `UserPromptSubmit` | Guardrails + optional `systemMessage` on violation; ingestion of violation | Snowflake documents this event as non-blocking; exit code is always `0`. |
| `PreToolUse` | Guardrails; blocked tool calls return JSON `decision: block` and exit `2` | Matches Snowflake exit code `2` to block. |
| `PostToolUse` | Ingestion (`ingest_data=true`) | Does not block. |

Synthetic host for Atlas: `https://{DEVICE_ID}.ai-agent.cortex` so traffic groups with other Atlas agents. Tags include `ai-agent: cortexcli`.

## Local smoke test

With `.env` loaded:

```bash
cat fixtures/sample-pretool.json | ./akto-validate-pre-tool-wrapper.sh
```

Expect exit `0` when guardrails allow (or when `AKTO_DATA_INGESTION_URL` is unset, hooks fail-open where documented).

## Files

| File | Role |
|------|------|
| `cortex_common.py` | Shared HTTP proxy URL builder, payload shape, logging. |
| `akto_machine_id.py` | Device id for Atlas routing. |
| `akto-validate-prompt.py` | `UserPromptSubmit`. |
| `akto-validate-pre-tool.py` | `PreToolUse`. |
| `akto-validate-post-tool.py` | `PostToolUse`. |
| `*-wrapper.sh` | Sources optional `.env`, runs Python. |

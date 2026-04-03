# Akto Guardrails for Codex CLI

Validate prompts and tool calls against Akto AI Guardrails in OpenAI Codex CLI.

> **Note:** Codex CLI currently only supports the `Bash` tool for `PreToolUse` and `PostToolUse` hooks.

## Prerequisites

Codex CLI hooks are experimental. Enable them in `~/.codex/config.toml`:

```toml
[features]
codex_hooks = true
```

## Setup

### 1. Download the plugin

```bash
mkdir -p ~/.codex/hooks
cd ~/.codex/hooks
```

Copy the following files to this directory:

- `akto-validate-prompt.py`
- `akto-validate-response.py`
- `akto-validate-pre-tool.py`
- `akto-validate-post-tool.py`
- `akto-validate-prompt-wrapper.sh`
- `akto-validate-response-wrapper.sh`
- `akto-validate-pre-tool-wrapper.sh`
- `akto-validate-post-tool-wrapper.sh`
- `akto_machine_id.py`

### 2. Configure environment

Add the following environment variables to your shell configuration file (for example `~/.zshrc`):

```bash
export AKTO_DATA_INGESTION_URL="ingestion-service-url"
export AKTO_SYNC_MODE="true"
export MODE="argus" # argus (default) or atlas
export DEVICE_ID="" # Optional: custom device ID in atlas mode

# Optional logging configuration
export LOG_DIR="~/.codex/akto/logs"
export LOG_LEVEL="INFO"
export LOG_PAYLOADS="false"
```

Then reload your shell config:

```bash
source ~/.zshrc
```

The Codex API host and path are **auto-detected** from the same env vars Codex CLI uses:

| Scenario | Host | Path |
|----------|------|------|
| `OPENAI_BASE_URL` set | value of `OPENAI_BASE_URL` | `/v1/responses` |
| `OPENAI_API_KEY` set | `api.openai.com` | `/v1/responses` |
| ChatGPT browser login | `chatgpt.com` | `/backend-api/codex/responses` |

### 3. Add hooks to Codex CLI

Copy `hooks.json` to `~/.codex/hooks.json`:

```json
{
  "hooks": {
    "UserPromptSubmit": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "bash ~/.codex/hooks/akto-validate-prompt-wrapper.sh",
            "timeout": 10
          }
        ]
      }
    ],
    "Stop": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "bash ~/.codex/hooks/akto-validate-response-wrapper.sh",
            "timeout": 10
          }
        ]
      }
    ],
    "PreToolUse": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "bash ~/.codex/hooks/akto-validate-pre-tool-wrapper.sh",
            "timeout": 10
          }
        ]
      }
    ],
    "PostToolUse": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "bash ~/.codex/hooks/akto-validate-post-tool-wrapper.sh",
            "timeout": 10
          }
        ]
      }
    ]
  }
}
```

You can also place the file at `<repo>/.codex/hooks.json` for repository-level hooks.

### 4. Restart Codex CLI

```bash
codex
```

## How It Works

### Prompt Hooks

#### Before Prompt Submit (`akto-validate-prompt.py`)

- Trigger: `UserPromptSubmit`
- Validates user prompts against Akto guardrails
- Can block prompts

Block response format:

```json
{
  "decision": "block",
  "reason": "Blocked by Akto Guardrails"
}
```

#### After Response (`akto-validate-response.py`)

- Trigger: `Stop`
- Captures prompt/response data from transcript and ingests it to Akto
- Observational only (does not trigger continuation)

### Tool Hooks

> **Note:** Codex CLI currently only supports the `Bash` tool for tool hooks.

#### Before Tool Execution (`akto-validate-pre-tool.py`)

- Trigger: `PreToolUse`
- Validates tool input against Akto guardrails before execution
- Can deny tool execution

Input contract:

- Reads `tool_name`, `tool_input` (with `command` field for Bash) from hook input

Deny response contract (Codex `hookSpecificOutput` format):

```json
{
  "hookSpecificOutput": {
    "hookEventName": "PreToolUse",
    "permissionDecision": "deny",
    "permissionDecisionReason": "Blocked by Akto Guardrails: <reason>"
  }
}
```

Allow contract:

- Prints no output and exits `0`

#### After Tool Execution (`akto-validate-post-tool.py`)

- Trigger: `PostToolUse`
- Reads tool input/output and ingests to Akto for observability
- Encodes `requestPayload` as `{"body": {"toolName": ..., "toolArgs": {...}}}`
- Encodes `responsePayload` as `{"body": {"result": {...}}}`
- Observational only (never blocks)

Input contract:

- Reads `tool_name`, `tool_input`, `tool_response` from hook input

Response contract:

- Prints no output and exits `0`

## Codex Hook Input Format

All hooks receive a common JSON payload on stdin:

```json
{
  "session_id": "string",
  "transcript_path": "string | null",
  "cwd": "string",
  "hook_event_name": "string",
  "model": "string",
  "turn_id": "string"
}
```

Plus event-specific fields:

| Event | Additional Fields |
|-------|------------------|
| `UserPromptSubmit` | `prompt` |
| `Stop` | `last_assistant_message`, `stop_hook_active` |
| `PreToolUse` | `tool_name`, `tool_use_id`, `tool_input` |
| `PostToolUse` | `tool_name`, `tool_use_id`, `tool_input`, `tool_response` |

## Configuration Options

| Variable | Default | Description |
|----------|---------|-------------|
| `AKTO_DATA_INGESTION_URL` | (required) | Akto data ingestion service URL |
| `AKTO_SYNC_MODE` | `true` | Synchronous guardrails mode for blocking hooks |
| `AKTO_TIMEOUT` | `5` | Timeout in seconds for API calls |
| `MODE` | `argus` | Operation mode: `argus` or `atlas` |
| `DEVICE_ID` | (auto-generated) | Custom device ID for atlas mode |
| `LOG_DIR` | `~/.codex/akto/logs` | Directory for log files |
| `LOG_LEVEL` | `INFO` | Logging verbosity: DEBUG, INFO, WARNING, ERROR |
| `LOG_PAYLOADS` | `false` | Log request/response payload previews |

## Viewing Logs

Default log directory: `~/.codex/akto/logs/`

- `validate-prompt.log` - prompt validation
- `validate-response.log` - conversation ingestion
- `validate-pre-tool.log` - tool request validation
- `validate-post-tool.log` - tool response ingestion

Tail all logs:

```bash
tail -f ~/.codex/akto/logs/*.log
```

## Differences from Claude CLI Hooks

| Aspect | Claude CLI | Codex CLI |
|--------|-----------|-----------|
| Config location | `~/.claude/settings.json` | `~/.codex/hooks.json` |
| Feature flag | Not required | `codex_hooks = true` in `config.toml` |
| Hook install path | `~/.claude/hooks/` | `~/.codex/hooks/` |
| Matcher support | No | Yes (regex on `PreToolUse`/`PostToolUse`) |
| Tool types | MCP tools + built-in | Currently only `Bash` |
| PreToolUse deny format | `{"decision": "block"}` | `hookSpecificOutput.permissionDecision: "deny"` |
| Additional hook events | None | `SessionStart` |
| Common input fields | Varies per event | All events share `session_id`, `cwd`, `model` |
| API host/path | Configured via `CLAUDE_API_URL` | Auto-detected from `OPENAI_BASE_URL` / `OPENAI_API_KEY` |

## Troubleshooting

### Hooks not executing

1. Verify hooks feature is enabled in `~/.codex/config.toml`
2. Verify hook config path: `~/.codex/hooks.json`
3. Ensure Python 3 is installed: `python3 --version`
4. Verify environment vars: `echo $AKTO_DATA_INGESTION_URL`
5. Ensure scripts are executable: `chmod +x ~/.codex/hooks/*.py ~/.codex/hooks/*.sh`
6. Check logs: `tail -f ~/.codex/akto/logs/*.log`

### Service unavailable errors

If Akto is unavailable:

- With `AKTO_SYNC_MODE=true`: blocking hooks may block requests
- With `AKTO_SYNC_MODE=false`: hooks fail open and allow execution

Inspect failures:

```bash
grep "API CALL FAILED" ~/.codex/akto/logs/*.log
```

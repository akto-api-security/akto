# Akto Guardrails for Claude CLI

Validate prompts and MCP tool calls against Akto AI Guardrails in Claude CLI.

## Setup

### 1. Download the plugin

```bash
mkdir -p ~/.claude/hooks
cd ~/.claude/hooks
```

Copy the following files to this directory:

- `akto-validate-prompt.py`
- `akto-validate-response.py`
- `akto-validate-mcp-request.py`
- `akto-validate-mcp-response.py`
- `akto-validate-prompt-wrapper.sh`
- `akto-validate-response-wrapper.sh`
- `akto-validate-mcp-request-wrapper.sh`
- `akto-validate-mcp-response-wrapper.sh`
- `akto_machine_id.py`

### 2. Configure environment

Add the following environment variables to your shell configuration file (for example `~/.zshrc`):

```bash
export AKTO_DATA_INGESTION_URL="ingestion-service-url"
export AKTO_SYNC_MODE="true"
export MODE="argus" # argus (default) or atlas
export DEVICE_ID="" # Optional: custom device ID in atlas mode

# Optional logging configuration
export LOG_DIR="~/.claude/akto/logs"
export LOG_LEVEL="INFO"
export LOG_PAYLOADS="false"
```

Then reload your shell config:

```bash
source ~/.zshrc
```

### 3. Add hooks to Claude CLI

Edit `~/.claude/settings.json`:

```json
{
  "hooks": {
    "UserPromptSubmit": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "bash ~/.claude/hooks/akto-validate-prompt-wrapper.sh",
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
            "command": "bash ~/.claude/hooks/akto-validate-response-wrapper.sh",
            "timeout": 10
          }
        ]
      }
    ],
    "PreToolUse": [
      {
        "matcher": "mcp__.*",
        "hooks": [
          {
            "type": "command",
            "command": "bash ~/.claude/hooks/akto-validate-mcp-request-wrapper.sh",
            "timeout": 10
          }
        ]
      }
    ],
    "PostToolUse": [
      {
        "matcher": "mcp__.*",
        "hooks": [
          {
            "type": "command",
            "command": "bash ~/.claude/hooks/akto-validate-mcp-response-wrapper.sh",
            "timeout": 10
          }
        ]
      }
    ]
  }
}
```

### 4. Restart Claude CLI

```bash
claude
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
- Observational only (cannot block)

### MCP Tool Hooks

#### Before MCP Tool Execution (`akto-validate-mcp-request.py`)

- Trigger: `PreToolUse` with matcher `mcp__.*`
- Validates MCP tool input against Akto guardrails
- Can block tool execution

Input contract:

- Reads `hook_specific_input.tool_name` and `hook_specific_input.tool_input`
- Also accepts camelCase fallback: `hookSpecificInput.toolName`, `hookSpecificInput.toolInput`

Block response contract:

```json
{
  "decision": "block",
  "reason": "Blocked by Akto Guardrails: <reason>"
}
```

Allow contract:

- Prints no output and exits `0`

#### After MCP Tool Execution (`akto-validate-mcp-response.py`)

- Trigger: `PostToolUse` with matcher `mcp__.*`
- Reads MCP tool input/output and ingests to Akto
- Observational only (never blocks)

Input contract:

- Reads `hook_specific_input.tool_name`, `hook_specific_input.tool_input`, `hook_specific_input.tool_response`
- Also accepts camelCase fallback: `hookSpecificInput.toolName`, `hookSpecificInput.toolInput`, `hookSpecificInput.toolResponse`

Response contract:

- Prints no output and exits `0`

## Configuration Options

| Variable | Default | Description |
|----------|---------|-------------|
| `AKTO_DATA_INGESTION_URL` | (required) | Akto data ingestion service URL |
| `AKTO_SYNC_MODE` | `true` | Synchronous guardrails mode for blocking hooks |
| `AKTO_TIMEOUT` | `5` | Timeout in seconds for API calls |
| `MODE` | `argus` | Operation mode: `argus` or `atlas` |
| `DEVICE_ID` | (auto-generated) | Custom device ID for atlas mode |
| `CLAUDE_API_URL` | `https://api.anthropic.com` | Claude API URL (argus mode only) |
| `LOG_DIR` | `~/.claude/akto/logs` | Directory for log files |
| `LOG_LEVEL` | `INFO` | Logging verbosity: DEBUG, INFO, WARNING, ERROR |
| `LOG_PAYLOADS` | `false` | Log request/response payload previews |

## Viewing Logs

Default log directory: `~/.claude/akto/logs/`

- `validate-prompt.log` - prompt validation
- `validate-response.log` - conversation ingestion
- `validate-mcp-request.log` - MCP request validation
- `validate-mcp-response.log` - MCP response ingestion

Tail all logs:

```bash
tail -f ~/.claude/akto/logs/*.log
```

## Troubleshooting

### Hooks not executing

1. Verify hook config path: `~/.claude/settings.json`
2. Ensure Python 3 is installed: `python3 --version`
3. Verify environment vars: `echo $AKTO_DATA_INGESTION_URL`
4. Ensure scripts are executable: `chmod +x ~/.claude/hooks/*.py ~/.claude/hooks/*.sh`
5. Check logs: `tail -f ~/.claude/akto/logs/*.log`

### Service unavailable errors

If Akto is unavailable:

- With `AKTO_SYNC_MODE=true`: blocking hooks may block requests
- With `AKTO_SYNC_MODE=false`: hooks fail open and allow execution

Inspect failures:

```bash
grep "API CALL FAILED" ~/.claude/akto/logs/*.log
```

# Akto Guardrails for Cursor Agent

Comprehensive validation and monitoring for Cursor agent interactions, including both chat conversations and MCP tool executions.

## Setup

### 1. Download the plugin

```bash
mkdir -p ~/.cursor/hooks/akto
cd ~/.cursor/hooks/akto
```

Copy the following files to this directory:

**For Chat Monitoring:**
- `akto-validate-chat-prompt.py` - Validates user prompts before submission
- `akto-validate-chat-response.py` - Ingests agent responses for analysis

**For MCP Tool Monitoring:**
- `akto-validate-mcp-request.py` - Validates MCP tool requests before execution
- `akto-validate-mcp-response.py` - Ingests MCP tool responses for analysis

**Shared:**
- `akto-machine-id.py` - Device ID generation utility

### 2. Configure environment

Add the following environment variables to your shell configuration file (e.g. `~/.bashrc`, `~/.zshrc`, or `~/.profile`):

```bash
# Add these to ~/.zshrc
export AKTO_DATA_INGESTION_URL="ingestion-service-url"
export AKTO_SYNC_MODE="true" # Set to false if you want to allow prompts if guardrails blocks them but still send them to Cursor
export MODE="argus" # Options: "argus" (default) or "atlas"
export DEVICE_ID="" # Optional: Custom device ID for atlas mode (auto-generated if not provided)

# Optional logging configuration
export LOG_DIR="~/.cursor/akto/mcp-logs" # Default: ~/.cursor/akto/mcp-logs (MCP tools), ~/.cursor/akto/chat-logs (chat)
export LOG_LEVEL="INFO" # Options: DEBUG, INFO, WARNING, ERROR (default: INFO)
export LOG_PAYLOADS="false" # Set to "true" to log request/response payloads (default: false)
```

#### Mode Configuration

- **argus** (default): Standard mode using configured `API_URL` or defaults to `https://api.anthropic.com`
- **atlas**: Uses device-specific routing with format `https://{deviceId}.ai-agent.cursor` and includes additional metadata tags:
  - `ai-agent=cursor`
  - `source=ENDPOINT`

**Device ID for Atlas Mode:**
- If `DEVICE_ID` environment variable is set, it will be used directly
- If `DEVICE_ID` is not set, the device ID is automatically generated from your machine's MAC address and cached
- The auto-generated device ID is lowercase with no dashes or colons

Then reload your shell configuration (or open a new terminal) before using Cursor:

```bash
source ~/.zshrc
```

### 3. Add hook configuration to Cursor

Edit or create `~/.cursor/hooks.json`:

```json
{
  "version": 1,
  "hooks": {
    "beforeSubmitPrompt": [
      {
        "command": "python3 ~/.cursor/hooks/akto/akto-validate-chat-prompt.py",
        "type": "command",
        "timeout": 10
      }
    ],
    "afterAgentResponse": [
      {
        "command": "python3 ~/.cursor/hooks/akto/akto-validate-chat-response.py",
        "type": "command",
        "timeout": 10
      }
    ],
    "beforeMCPExecution": [
      {
        "command": "python3 ~/.cursor/hooks/akto/akto-validate-mcp-request.py",
        "type": "command",
        "timeout": 10
      }
    ],
    "afterMCPExecution": [
      {
        "command": "python3 ~/.cursor/hooks/akto/akto-validate-mcp-response.py",
        "type": "command",
        "timeout": 10
      }
    ]
  }
}
```

**Hook Types:**
- `beforeSubmitPrompt` - Intercepts ALL user chat prompts before submission
- `afterAgentResponse` - Observes ALL agent chat responses after generation
- `beforeMCPExecution` - Intercepts MCP tool calls only
- `afterMCPExecution` - Observes MCP tool results only

### 4. Restart Cursor

Close and reopen Cursor for the hooks to take effect.

## How It Works

### Chat Hooks

#### Before Submit (akto-validate-chat-prompt.py)
- **Trigger:** `beforeSubmitPrompt` - Before user prompt is sent
- **Input:** `{"prompt": "text", "attachments": [...]}`
- **Actions:**
  - Validates user prompt against Akto guardrails
  - Blocks prompts that violate security policies
  - Logs validation decisions
- **Output:** `{"continue": true/false, "user_message": "reason"}`
- **Can Block:** ✅ Yes

#### After Response (akto-validate-chat-response.py)
- **Trigger:** `afterAgentResponse` - After agent generates response
- **Input:** `{"text": "agent response"}`
- **Actions:**
  - Captures agent responses for monitoring
  - Sends to Akto for ingestion and analysis
  - Logs response metrics
- **Output:** `{}` (observational only)
- **Can Block:** ❌ No (observational only)

### MCP Tool Hooks

#### Before Execution (akto-validate-mcp-request.py)
- **Trigger:** `beforeMCPExecution` - Before MCP tool execution
- **Input:** `{"server": "...", "tool_name": "...", "arguments": {...}}`
- **Actions:**
  - Validates tool inputs against Akto guardrails
  - Blocks requests that violate security policies
  - Logs blocked requests for monitoring
- **Output:**
```json
{
  "permission": "allow|deny|ask",
  "user_message": "Message shown to user (if denied)",
  "agent_message": "Message shown to agent (if denied)"
}
```
- **Can Block:** ✅ Yes

#### After Execution (akto-validate-mcp-response.py)
- **Trigger:** `afterMCPExecution` - After MCP tool execution
- **Input:** `{"server": "...", "tool_name": "...", "result": {...}}`
- **Actions:**
  - Captures MCP tool execution responses
  - Sends request-response pairs to Akto for ingestion
  - Logs alerts for policy violations
- **Output:** `{}` (observational only)
- **Can Block:** ❌ No (Cursor limitation)

### Coverage Comparison

| Hook Type | Scope | Can Block | Use Case |
|-----------|-------|-----------|----------|
| Chat Hooks | All user prompts & responses | Prompts only | Monitor all conversations |
| MCP Hooks | Tool executions only | Requests only | Monitor external API calls |

**With both hook types enabled, you get:**
- ✅ Complete conversation history
- ✅ All tool execution tracking
- ✅ Comprehensive security validation
- ✅ Full audit trail

## Configuration Options

| Variable | Default | Description |
|----------|---------|-------------|
| `AKTO_DATA_INGESTION_URL` | (required) | Akto data ingestion service URL |
| `AKTO_SYNC_MODE` | `true` | Block requests on guardrail violations |
| `AKTO_TIMEOUT` | `5` | Timeout in seconds for API calls |
| `MODE` | `argus` | Operation mode: `argus` or `atlas` |
| `DEVICE_ID` | (auto-generated) | Custom device ID for atlas mode |
| `API_URL` | `https://api.anthropic.com` | API endpoint URL (argus mode only) |
| `LOG_DIR` | `~/.cursor/akto/mcp-logs` or `~/.cursor/akto/chat-logs` | Directory for log files (auto-set per hook type) |
| `LOG_LEVEL` | `INFO` | Logging verbosity: DEBUG, INFO, WARNING, ERROR |
| `LOG_PAYLOADS` | `false` | Log request/response payloads (privacy-sensitive) |

## Troubleshooting

### Hooks not executing

1. Verify the hooks.json file path: `~/.cursor/hooks.json`
2. Check that Python 3 is available: `python3 --version`
3. Ensure environment variables are set: `echo $AKTO_DATA_INGESTION_URL`
4. Check file permissions: `chmod +x ~/.cursor/hooks/akto/*.py`

### Service unavailable errors

If the Akto service is unavailable:
- With `AKTO_SYNC_MODE=true`: Requests are blocked for safety
- With `AKTO_SYNC_MODE=false`: Requests are allowed (fail-open)

### Viewing logs

Hook execution logs are written to persistent files:

**Log File Locations:**

*Chat Hooks* (default: `~/.cursor/akto/chat-logs/`):
- `akto-validate-chat-prompt.log` - Chat prompt validation logs
- `akto-validate-chat-response.log` - Chat response ingestion logs

*MCP Tool Hooks* (default: `~/.cursor/akto/mcp-logs/`):
- `akto-validate-request.log` - MCP request validation logs
- `akto-validate-response.log` - MCP response ingestion logs

**View logs in real-time:**
```bash
# Watch chat hooks
tail -f ~/.cursor/akto/chat-logs/akto-validate-chat-prompt.log
tail -f ~/.cursor/akto/chat-logs/akto-validate-chat-response.log

# Watch MCP hooks
tail -f ~/.cursor/akto/mcp-logs/akto-validate-request.log
tail -f ~/.cursor/akto/mcp-logs/akto-validate-response.log

# View all logs together
tail -f ~/.cursor/akto/chat-logs/*.log ~/.cursor/akto/mcp-logs/*.log
```

**Log Format:**
```
2025-02-04 10:30:45,123 - INFO - === Hook execution started - Mode: atlas, Sync: True ===
2025-02-04 10:30:45,124 - INFO - Processing request for MCP server: github
2025-02-04 10:30:45,125 - INFO - Validating request for MCP server: github
2025-02-04 10:30:45,126 - INFO - API CALL: POST https://data-ingestion.akto.io/api/http-proxy?guardrails=true&akto_connector=cursor_mcp
2025-02-04 10:30:45,456 - INFO - API RESPONSE: Status 200, Duration: 330ms, Size: 245 bytes
2025-02-04 10:30:45,457 - INFO - Request ALLOWED for github
2025-02-04 10:30:45,458 - INFO - Request allowed
```

**What Gets Logged:**

1. **Hook Execution Context**
   - Mode (atlas/argus) and sync configuration
   - MCP server name
   - Hook start/end timestamps

2. **API Calls** (detailed logging)
   - Full URL with query parameters (`guardrails=true`, `akto_connector=cursor_mcp`, `ingest_data=true`)
   - HTTP method (POST)
   - Request/response timing (latency in ms)
   - Response status codes
   - Response sizes

3. **Guardrails Decisions** (akto-validate-mcp-request.py)
   - ALLOWED/DENIED with reasons
   - MCP tool input previews

4. **Data Ingestion** (akto-validate-mcp-response.py)
   - Ingestion attempts and results
   - Tool input/result previews

5. **Errors**
   - Full error messages with context
   - API call failures with timing

**Privacy Note:** By default, full request/response payloads are NOT logged for privacy. Set `LOG_PAYLOADS=true` to enable full payload logging for debugging (use with caution in production).

## Legacy Bash Hooks

The previous bash-based implementation has been moved to the `legacy/` directory. The new Python implementation provides:
- Better integration with Akto's http-proxy API
- Support for atlas/argus modes
- Consistent behavior with Claude CLI hooks
- More reliable JSON handling

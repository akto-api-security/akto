# Akto Guardrails for Cursor MCP

Validate MCP tool execution requests against Akto AI Guardrails before they're executed in Cursor.

## Setup

### 1. Download the plugin

```bash
mkdir -p ~/.cursor/hooks/akto
cd ~/.cursor/hooks/akto
```

Copy the following files to this directory:
- `validate-mcp-request.py`
- `validate-mcp-response.py`
- `machine_id.py`

### 2. Configure environment

Add the following environment variables to your shell configuration file (e.g. `~/.bashrc`, `~/.zshrc`, or `~/.profile`):

```bash
# Add these to ~/.zshrc
export AKTO_DATA_INGESTION_URL="ingestion-service-url"
export AKTO_SYNC_MODE="true" # Set to false if you want to allow prompts if guardrails blocks them but still send them to Cursor
export MODE="argus" # Options: "argus" (default) or "atlas"
export DEVICE_ID="" # Optional: Custom device ID for atlas mode (auto-generated if not provided)

# Optional logging configuration
export LOG_DIR="~/.cursor/mcp-logs" # Default: ~/.cursor/mcp-logs
export LOG_LEVEL="INFO" # Options: DEBUG, INFO, WARNING, ERROR (default: INFO)
export LOG_PAYLOADS="false" # Set to "true" to log request/response payloads (default: false)
```

#### Mode Configuration

- **argus** (default): Standard mode using configured `API_URL` or defaults to `https://api.anthropic.com`
- **atlas**: Uses device-specific routing with format `https://{deviceId}.cursor.ai-agent` and includes additional metadata tags:
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
    "beforeMCPExecution": [
      {
        "command": "python3 ~/.cursor/hooks/akto/validate-mcp-request.py"
      }
    ],
    "afterMCPExecution": [
      {
        "command": "python3 ~/.cursor/hooks/akto/validate-mcp-response.py"
      }
    ]
  }
}
```

### 4. Restart Cursor

Close and reopen Cursor for the hooks to take effect.

## How It Works

### Before Execution (validate-mcp-request.py)

- Intercepts MCP tool execution requests
- Validates tool inputs against Akto guardrails
- Blocks requests that violate security policies
- Logs blocked requests for monitoring

**Response Format:**
```json
{
  "permission": "allow|deny|ask",
  "user_message": "Message shown to user (if denied)",
  "agent_message": "Message shown to agent (if denied)"
}
```

### After Execution (validate-mcp-response.py)

- Captures MCP tool execution responses
- Sends request-response pairs to Akto for ingestion and analysis
- Cannot block responses (Cursor limitation)
- Logs alerts for policy violations

**Note:** The after-execution hook only logs and ingests data. It cannot prevent the response from being delivered to the agent.

## Configuration Options

| Variable | Default | Description |
|----------|---------|-------------|
| `AKTO_DATA_INGESTION_URL` | (required) | Akto data ingestion service URL |
| `AKTO_SYNC_MODE` | `true` | Block requests on guardrail violations |
| `AKTO_TIMEOUT` | `5` | Timeout in seconds for API calls |
| `MODE` | `argus` | Operation mode: `argus` or `atlas` |
| `DEVICE_ID` | (auto-generated) | Custom device ID for atlas mode |
| `API_URL` | `https://api.anthropic.com` | API endpoint URL (argus mode only) |
| `LOG_DIR` | `~/.cursor/mcp-logs` | Directory for log files |
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

**Log File Locations** (default: `~/.cursor/mcp-logs/`):
- `validate-request.log` - Before hook (request validation) logs
- `validate-response.log` - After hook (response ingestion) logs

**View logs in real-time:**
```bash
# Watch request validation logs
tail -f ~/.cursor/mcp-logs/validate-request.log

# Watch response ingestion logs
tail -f ~/.cursor/mcp-logs/validate-response.log

# View both logs together
tail -f ~/.cursor/mcp-logs/*.log
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

3. **Guardrails Decisions** (validate-mcp-request.py)
   - ALLOWED/DENIED with reasons
   - MCP tool input previews

4. **Data Ingestion** (validate-mcp-response.py)
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

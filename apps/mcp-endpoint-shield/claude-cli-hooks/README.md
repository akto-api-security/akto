# Akto Guardrails for Claude CLI

Validate your prompts against Akto AI Guardrails before they're sent to Claude.

## Setup

### 1. Download the plugin

```bash
mkdir -p ~/.claude/hooks
cd ~/.claude/hooks
```

Copy the following files to this directory:
- `akto-validate-prompt.py`
- `akto-validate-response.py`
- `akto-machine-id.py`

### 2. Configure environment

Add the following environment variables to your shell configuration file (e.g. `~/.bashrc`, `~/.zshrc`, or `~/.profile`):

```bash
# Add these to ~/.zshrc
export AKTO_DATA_INGESTION_URL="ingestion-service-url"
export AKTO_SYNC_MODE="true" # Set to false if you want to allow prompts if guardrails blocks them but still send them to Claude
export MODE="argus" # Options: "argus" (default) or "atlas"
export DEVICE_ID="" # Optional: Custom device ID for atlas mode (auto-generated if not provided)

# Optional logging configuration
export LOG_DIR="~/.claude/akto/logs" # Default: ~/.claude/akto/logs
export LOG_LEVEL="INFO" # Options: DEBUG, INFO, WARNING, ERROR (default: INFO)
export LOG_PAYLOADS="false" # Set to "true" to log request/response payloads (default: false)
```

#### Mode Configuration

- **argus** (default): Standard mode using configured `CLAUDE_API_URL` or defaults to `https://api.anthropic.com`
- **atlas**: Uses device-specific routing with format `https://{deviceId}.ai-agent.claudecli` and includes additional metadata tags:
  - `ai-agent=claudecli`
  - `source=ENDPOINT`

**Device ID for Atlas Mode:**
- If `DEVICE_ID` environment variable is set, it will be used directly
- If `DEVICE_ID` is not set, the device ID is automatically generated from your machine's MAC address and cached
- The auto-generated device ID is lowercase with no dashes or colons

Then reload your shell configuration (or open a new terminal) before running Claude Code:

```bash
source ~/.zshrc
```

### 3. Add hook to Claude CLI

Edit `~/.claude/settings.json`:

```json
{
    "hooks": {
        "UserPromptSubmit": [
            {
                "hooks": [
                    {
                        "type": "command",
                        "command": "python3 ~/.claude/hooks/akto-validate-prompt.py",
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
                        "command": "python3 ~/.claude/hooks/akto-validate-response.py",
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

### Before Prompt Submit (akto-validate-prompt.py)

- Intercepts prompts before they're sent to Claude
- Validates prompts against Akto guardrails
- Blocks prompts that violate security policies
- Logs blocked prompts for monitoring

**Response Format:**
```json
{
  "decision": "block",
  "reason": "Blocked by Akto Guardrails"
}
```

### After Response (akto-validate-response.py)

- Captures completed prompt-response pairs
- Sends conversation data to Akto for ingestion and analysis
- Logs ingestion attempts and results

**Note:** The after-response hook only logs and ingests data. It cannot block responses.

## Configuration Options

| Variable | Default | Description |
|----------|---------|-------------|
| `AKTO_DATA_INGESTION_URL` | (required) | Akto data ingestion service URL |
| `AKTO_SYNC_MODE` | `true` | Block prompts on guardrail violations |
| `AKTO_TIMEOUT` | `5` | Timeout in seconds for API calls |
| `MODE` | `argus` | Operation mode: `argus` or `atlas` |
| `DEVICE_ID` | (auto-generated) | Custom device ID for atlas mode |
| `CLAUDE_API_URL` | `https://api.anthropic.com` | Claude API URL (argus mode only) |
| `LOG_DIR` | `~/.claude/akto/logs` | Directory for log files |
| `LOG_LEVEL` | `INFO` | Logging verbosity: DEBUG, INFO, WARNING, ERROR |
| `LOG_PAYLOADS` | `false` | Log request/response payloads (privacy-sensitive) |

## Viewing Logs

Hook execution logs are written to persistent files:

**Log File Locations** (default: `~/.claude/akto/logs/`):
- `akto-validate-prompt.log` - Before hook (prompt validation) logs
- `akto-validate-response.log` - After hook (response ingestion) logs

**View logs in real-time:**
```bash
# Watch prompt validation logs
tail -f ~/.claude/akto/logs/akto-validate-prompt.log

# Watch response ingestion logs
tail -f ~/.claude/akto/logs/akto-validate-response.log

# View both logs together
tail -f ~/.claude/akto/logs/*.log
```

**Log Format:**
```
2025-02-04 15:30:45,123 - INFO - === Hook execution started - Mode: atlas, Sync: True ===
2025-02-04 15:30:45,124 - INFO - Processing prompt (length: 245 chars)
2025-02-04 15:30:45,125 - INFO - Validating prompt against guardrails
2025-02-04 15:30:45,126 - INFO - Prompt preview: Help me create a...
2025-02-04 15:30:45,127 - INFO - API CALL: POST https://data-ingestion.akto.io/api/http-proxy?guardrails=true&akto_connector=claude_code_cli
2025-02-04 15:30:45,458 - INFO - API RESPONSE: Status 200, Duration: 331ms, Size: 256 bytes
2025-02-04 15:30:45,459 - INFO - Prompt ALLOWED by guardrails
2025-02-04 15:30:45,460 - INFO - Prompt allowed
```

**What Gets Logged:**

1. **Hook Execution Context**
   - Mode (atlas/argus) and sync configuration
   - Hook start/end timestamps

2. **API Calls** (detailed logging)
   - Full URL with query parameters
   - HTTP method
   - Request/response timing (latency in ms)
   - Response status codes
   - Response sizes

3. **Guardrails Decisions**
   - ALLOWED/DENIED with reasons
   - Prompt previews (first 100 characters by default)

4. **Data Ingestion**
   - Ingestion attempts and results
   - Success/failure status

5. **Errors**
   - Full error messages with context
   - API call failures with timing

**Privacy Note:** By default, full request/response payloads are NOT logged for privacy. Set `LOG_PAYLOADS=true` to enable full payload logging for debugging (use with caution in production).

## Troubleshooting

### Hooks not executing

1. Verify the settings.json file path: `~/.claude/settings.json`
2. Check that Python 3 is available: `python3 --version`
3. Ensure environment variables are set: `echo $AKTO_DATA_INGESTION_URL`
4. Check file permissions: `chmod +x ~/.claude/hooks/*.py`
5. Check logs for errors: `tail -f ~/.claude/akto/logs/*.log`

### Service unavailable errors

If the Akto service is unavailable:
- With `AKTO_SYNC_MODE=true`: Prompts are blocked for safety
- With `AKTO_SYNC_MODE=false`: Prompts are allowed (fail-open)

Check logs to see API call failures:
```bash
grep "API CALL FAILED" ~/.claude/akto/logs/*.log
```

### Debug logging

To see detailed request/response payloads and debug information:

```bash
export LOG_LEVEL="DEBUG"
export LOG_PAYLOADS="true"
```

Then restart Claude CLI and check the logs.

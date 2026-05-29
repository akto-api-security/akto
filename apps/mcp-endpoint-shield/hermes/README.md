# Akto Guardrails Plugin for Hermes Agent

Integrate Akto's AI security guardrails into Hermes Agent to validate prompts, tool calls, and AI responses in real-time.

## 📋 Overview

This plugin adds comprehensive security layers to Hermes Agent:
1. **Prompt Validation** - Scan user inputs before sending to LLM
2. **Tool Validation** - Verify tool calls against security policies  
3. **Response Logging** - Audit tool responses and AI outputs
4. **Comprehensive Audit Trail** - All activity logged to Akto dashboard

### Key Features

✅ **Real-time Validation** - Validate prompts and tools before execution  
✅ **Prompt Blocking** - Block risky prompts with clean user messages  
✅ **Tool Blocking** - Prevent dangerous tool executions  
✅ **Audit Trail** - Log all prompts, responses, and tool calls  
✅ **Fail-Open Design** - Allows execution if guardrails service is unreachable  
✅ **Multiple Hook Points** - `pre_llm_call`, `post_llm_call`, `pre_tool_call`, `post_tool_call`  
✅ **Sync & Async Modes** - Blocking mode for strict enforcement, logging-only for monitoring  
✅ **Comprehensive Logging** - File and console logging with configurable levels  

---

## 🚀 Quick Start

### 1. Install Plugin

Copy plugin files to Hermes plugins directory:

```bash
# Create plugins directory
mkdir -p ~/.hermes/plugins

# Copy plugin files
cp -r /path/to/hermes /~/.hermes/plugins/akto-guardrails
```

### 2. Configure Akto URL

Set the guardrails API endpoint (required):

```bash
# For bash
echo 'export AKTO_DATA_INGESTION_URL="https://your-akto-instance.com/guardrails"' >> ~/.bashrc
source ~/.bashrc

# For zsh
echo 'export AKTO_DATA_INGESTION_URL="https://your-akto-instance.com/guardrails"' >> ~/.zshrc
source ~/.zshrc
```

**Verify the URL is set:**
```bash
echo $AKTO_DATA_INGESTION_URL
# Should output: https://your-akto-instance.com/guardrails
```

### 3. Configure Hermes to Load Plugin

Edit `~/.hermes/config.json` and add plugin:

```json
{
  "plugins": {
    "akto-guardrails": {
      "enabled": true,
      "config": {
        "AKTO_DATA_INGESTION_URL": "https://your-akto-instance.com/guardrails",
        "AKTO_SYNC_MODE": "true",
        "LOG_LEVEL": "INFO"
      }
    }
  }
}
```

### 4. Start Hermes

```bash
hermes
```

That's it! The plugin is now active and will:
- Validate all user prompts before sending to LLM
- Block risky prompts with user-friendly messages
- Validate all tool calls before execution
- Log all activity to Akto dashboard for audit

---

## 📦 Files Included

### Core Plugin
- **`__init__.py`** - Main plugin with hook registrations
  - `pre_llm_call` - Validate prompts before AI
  - `post_llm_call` - Log responses for audit
  - `pre_tool_call` - Validate tools before execution
  - `post_tool_call` - Log tool results

### Support Modules
- **`akto_client.py`** - API client for Akto communication
- **`validators.py`** - Prompt and tool validation logic
- **`config.py`** - Configuration management
- **`logging_util.py`** - Logging setup and utilities
- **`akto_machine_id.py`** - Device identification

### Configuration & Documentation
- **`.env.example`** - Environment variables template
- **`README.md`** - This file
- **`TESTING.md`** - Testing guide
- **`INTEGRATION_GUIDE.md`** - Client integration steps

---

## 🔧 Configuration

### Environment Variables

Configure plugin behavior via environment variables:

```bash
# REQUIRED
export AKTO_DATA_INGESTION_URL="https://your-akto-instance.com/guardrails"

# OPTIONAL - Behavior
export AKTO_SYNC_MODE="true"           # "true"=block risky prompts, "false"=log only
export AKTO_TIMEOUT="5"                # Request timeout (seconds)

# OPTIONAL - Logging
export LOG_LEVEL="INFO"                # DEBUG, INFO, WARNING, ERROR
export LOG_PAYLOADS="false"            # Set to "true" for verbose payload logging
export LOG_DIR="~/.config/hermes/akto/logs"

# OPTIONAL - Advanced
export MODE="argus"                    # "argus" (default) or "atlas"
export CONTEXT_SOURCE="ENDPOINT"       # Request classification
export AKTO_CONNECTOR="hermes"        # Connector identifier
```

### Via Hermes Config File

Edit `~/.hermes/config.json`:

```json
{
  "plugins": {
    "akto-guardrails": {
      "enabled": true,
      "config": {
        "AKTO_DATA_INGESTION_URL": "https://your-akto-instance.com/guardrails",
        "AKTO_SYNC_MODE": "true",
        "LOG_LEVEL": "INFO",
        "LOG_PAYLOADS": false,
        "AKTO_TIMEOUT": 5,
        "MODE": "argus"
      }
    }
  }
}
```

---

## 🔍 How It Works

### Hook Points

The plugin intercepts Hermes Agent at 4 critical points:

```
User Input
    ↓
┌─────────────────────────────────────┐
│ pre_llm_call Hook                   │ ← Validate prompt, BLOCK if needed
│ (Runs BEFORE sending to LLM)     │
└────────────┬────────────────────────┘
             ↓ (if allowed)
        LLM API
             ↓
┌─────────────────────────────────────┐
│ post_llm_call Hook                  │ ← Log response for audit
│ (Runs AFTER LLM responds)        │
└────────────┬────────────────────────┘
             ↓
       Tool Execution
             ↓
┌─────────────────────────────────────┐
│ pre_tool_call Hook                  │ ← Validate tool, BLOCK if needed
│ (Runs BEFORE tool execution)        │
└────────────┬────────────────────────┘
             ↓ (if allowed)
       ┌─────────────┐
       │ Tool Output │
       └──────┬──────┘
              ↓
┌──────────────────────────────────────┐
│ post_tool_call Hook                 │ ← Log result for audit
│ (Runs AFTER tool completes)         │
└──────────────────────────────────────┘
```

### Hook Signatures

**pre_llm_call** - Validate user prompt
```python
async def pre_llm_call(
    user_message: str,           # User's input
    session_id: str,             # Session ID
    model: str,                  # Model name
    platform: str,               # Platform (cli, api, etc.)
    **kwargs
) -> Optional[Dict[str, Any]]:
    # Return None to allow, or {"action": "block", "message": "reason"} to block
```

**post_llm_call** - Log AI response (observational, cannot block)
```python
async def post_llm_call(
    session_id: str,             # Session ID
    user_message: str,           # Original user message
    assistant_response: str,     # AI's response
    conversation_history: list,  # Full conversation
    model: str,                  # Model name
    platform: str                # Platform
) -> None:
    # Log the response for audit trail
```

**pre_tool_call** - Validate tool execution
```python
async def pre_tool_call(
    tool_name: str,              # Name of tool
    args: dict,                  # Tool arguments
    session_id: str,             # Session ID
    task_id: str,                # Task ID
    **kwargs
) -> Optional[Dict[str, Any]]:
    # Return None to allow, or {"action": "block", "message": "reason"} to block
```

**post_tool_call** - Log tool result (observational, cannot block)
```python
async def post_tool_call(
    tool_name: str,              # Tool name
    args: dict,                  # Tool arguments
    result: str,                 # Tool output
    session_id: str,             # Session ID
    task_id: str,                # Task ID
    duration_ms: int             # Execution time
) -> None:
    # Log the tool result for audit trail
```

---

## 📊 Logging

### Log Directory

Logs are written to:
```
~/.config/hermes/akto/logs/
└── hermes-guardrails.log    # All plugin activity
```

### Log Format

Each log entry includes:
- **Timestamp**: ISO 8601 format
- **Logger**: Module name
- **Level**: DEBUG, INFO, WARNING, ERROR
- **Message**: Log entry with context

Example:
```
2026-05-04 10:23:45,123 - hermes_guardrails - INFO - [pre_llm_call] Prompt allowed, continuing to AI
2026-05-04 10:23:46,456 - hermes_guardrails - INFO - API RESPONSE: Status 200, Duration: 145ms, Size: 256 bytes
2026-05-04 10:23:47,789 - hermes_guardrails - WARNING - [BLOCKING PROMPT] Prompt blocked by guardrails: Policy violation
```

### View Logs

```bash
# Watch real-time
tail -f ~/.config/hermes/akto/logs/hermes-guardrails.log

# Search for blocks
grep "BLOCKING" ~/.config/hermes/akto/logs/hermes-guardrails.log

# Enable debug logging
export LOG_LEVEL="DEBUG"
export LOG_PAYLOADS="true"
hermes
```

---

## 🧪 Testing

See [TESTING.md](TESTING.md) for comprehensive testing procedures.

Quick test:
```bash
# Start Hermes with guardrails
export AKTO_DATA_INGESTION_URL="http://your-akto-server"
export LOG_LEVEL="DEBUG"
hermes

# In Hermes prompt:
# > What is your name?

# Check logs
tail -f ~/.config/hermes/akto/logs/hermes-guardrails.log
```

---

## 🔐 Data Sent to Akto

### Prompt Validation

```json
{
  "path": "/v1/messages",
  "method": "POST",
  "requestPayload": "{\"body\": \"user prompt text\"}",
  "responsePayload": "{}",
  "requestHeaders": "{\"host\": \"hermes.agent\", \"x-hermes-hook\": \"pre_llm_call\"}",
  "tag": "{\"gen-ai\": \"Gen AI\", \"ai-agent\": \"hermes\", \"source\": \"ENDPOINT\"}"
}
```

### Tool Validation

```json
{
  "path": "/v1/tools/execute",
  "method": "POST",
  "requestPayload": "{\"tool\": \"tool_name\", \"args\": {...}}",
  "responsePayload": "{}",
  "requestHeaders": "{\"host\": \"hermes.agent\", \"x-hermes-hook\": \"pre_tool_call\"}",
  "tag": "{\"gen-ai\": \"Gen AI\", \"tool-use\": \"Tool Execution\", \"tool-name\": \"tool_name\"}"
}
```

---

## ❓ Troubleshooting

### Plugin Not Loading

**Check if plugin files exist:**
```bash
ls -la ~/.hermes/plugins/akto-guardrails/
# Should show __init__.py, akto_client.py, etc.
```

**Check Hermes config is correct:**
```bash
cat ~/.hermes/config.json | grep -A5 akto-guardrails
```

**Check Python version:**
```bash
python3 --version  # Need 3.6+
```

### No Logs Appearing

**Verify Akto URL is set:**
```bash
echo $AKTO_DATA_INGESTION_URL
```

**If empty, set it and restart Hermes:**
```bash
export AKTO_DATA_INGESTION_URL="https://your-akto-instance.com/guardrails"
hermes
```

**Check log directory exists:**
```bash
mkdir -p ~/.config/hermes/akto/logs
ls -la ~/.config/hermes/akto/logs/
```

### Akto Server Unreachable

**Check connectivity:**
```bash
curl -I https://your-akto-instance.com/guardrails
```

**Check firewall/network:**
```bash
ping $(echo $AKTO_DATA_INGESTION_URL | sed 's|https://||; s|/.*||')
```

**Plugin should still work (fail-open):**
- Prompts allowed if validation fails
- Tools allowed if validation fails
- Logging attempted but not blocking execution

### Prompts Not Being Blocked

**Check AKTO_SYNC_MODE:**
```bash
echo $AKTO_SYNC_MODE
# Should be "true" for blocking mode
```

**Check log level for details:**
```bash
export LOG_LEVEL="DEBUG"
hermes
# Look for [BLOCKING PROMPT] or [pre_llm_call] messages
```

**Verify Akto guardrails rules are configured:**
- Check Akto dashboard for active policies
- Ensure policies apply to "hermes" connector
- Test with a simple blocking rule first

---

## 🤝 Integration Checklist

- [ ] Copy plugin files to `~/.hermes/plugins/akto-guardrails/`
- [ ] Set `AKTO_DATA_INGESTION_URL` environment variable
- [ ] Configure plugin in `~/.hermes/config.json`
- [ ] Create log directory: `mkdir -p ~/.config/hermes/akto/logs`
- [ ] Start Hermes: `hermes`
- [ ] Verify logs appear: `tail -f ~/.config/hermes/akto/logs/hermes-guardrails.log`
- [ ] Test with a simple prompt
- [ ] Check Akto dashboard for events
- [ ] Configure guardrails rules in Akto
- [ ] Test blocking behavior

---

## 📞 Support

For issues or questions:
1. Check logs: `tail -f ~/.config/hermes/akto/logs/hermes-guardrails.log`
2. Enable debug: `export LOG_LEVEL=DEBUG LOG_PAYLOADS=true && hermes`
3. Verify Akto is reachable: `curl https://your-akto-instance.com/health`
4. Check Hermes config: `cat ~/.hermes/config.json`

---

## 📄 License

Part of the Akto API Security suite.

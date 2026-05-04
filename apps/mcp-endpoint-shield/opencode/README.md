# Akto Guardrails Plugin for OpenCode

Integrate Akto's AI security guardrails into OpenCode to validate prompts, tools, and tool responses in real-time.

## 📋 Overview

This plugin adds three security layers to OpenCode:
1. **Prompt Validation** - Scan user inputs before sending to AI
2. **Tool Validation** - Verify tool calls against security policies
3. **Response Logging** - Audit tool responses and execution details

### Key Features

✅ **Built-in & MCP Tool Support** - Works with both OpenCode built-ins (read, glob, etc.) and MCP servers  
✅ **JSON-RPC Compliance** - Full JSON-RPC 2.0 support for MCP protocol  
✅ **Fail-Open Design** - Allows execution if guardrails service is unreachable  
✅ **Comprehensive Audit Trail** - Logs all activity to `~/.config/opencode/akto/logs/`  
✅ **Async Validation** - Non-blocking, doesn't slow down tool execution  
✅ **MCP Server Ready** - Detect and route MCP tools automatically  

---

## 🚀 Quick Start

### 1. Install Plugin

Copy the plugin to OpenCode's plugin directory:

```bash
cp akto-guardrails-plugin.js ~/.opencode/plugins/
cp akto-*.py ~/.opencode/plugins/
cp akto_machine_id.py ~/.opencode/plugins/
```

### 2. Configure Akto URL

Set the guardrails API endpoint (required):

```bash
export AKTO_DATA_INGESTION_URL="https://your-akto-instance/guardrails"
```

### 3. Start OpenCode

```bash
opencode
```

That's it! The plugin is now active and will:
- Log all prompts, tool calls, and responses
- Send data to Akto for analysis
- Allow/block based on your Akto policies

---

## 📦 Files Included

### Core Plugin
- **akto-guardrails-plugin.js** - Main plugin (hooks into OpenCode)

### Validation Handlers
- **akto-validate-prompt.py** - Prompt validation (before sending to AI)
- **akto-validate-tool-request.py** - Tool validation (before execution)
- **akto-validate-tool-response.py** - Response logging (after execution)

### MCP Support
- **akto-mcp-request.py** - Handles MCP tool requests (JSON-RPC format)
- **akto-mcp-response.py** - Handles MCP tool responses (JSON-RPC format)

### Utilities
- **akto_machine_id.py** - Device identification
- **opencode.json** - MCP server configuration template
- **settings.json** - Plugin metadata (name, version, hooks info for OpenCode)

### Test Files (Optional)
- **test-mcp-server.js** - Example MCP server (for testing only)

---

## 🔧 Configuration

### Environment Variables

```bash
# REQUIRED
export AKTO_DATA_INGESTION_URL="https://your-akto-instance/guardrails"

# OPTIONAL
export AKTO_TIMEOUT=5                    # Request timeout (seconds)
export LOG_LEVEL="INFO"                  # DEBUG, INFO, WARNING, ERROR
export LOG_PAYLOADS="false"              # Log full request/response bodies
export LOG_DIR="~/.config/opencode/akto/logs"  # Log directory
export MODE="argus"                      # argus (default) or atlas
export CONTEXT_SOURCE="ENDPOINT"         # Request classification source
```

### MCP Server Configuration

If using MCP servers, add to `~/.config/opencode/opencode.json`:

```json
{
  "mcp": {
    "your-mcp-server": {
      "type": "local",
      "command": ["node", "/path/to/server.js"],
      "enabled": true
    }
  }
}
```

Example (calculator MCP server):
```json
{
  "mcp": {
    "calculator": {
      "type": "local",
      "command": ["node", "/path/to/test-mcp-server.js"],
      "enabled": true
    }
  }
}
```

---

## 🔍 How It Works

### Architecture Overview

```
┌──────────────────────────────────┐
│      OpenCode User Input          │
└──────────────┬───────────────────┘
               │
       ┌───────┴────────┐
       │                │
       ↓                ↓
   ┌─────────┐    ┌──────────┐
   │ Prompt  │    │  Tool    │
   │         │    │          │
   └────┬────┘    └────┬─────┘
        │              │
        ↓              ↓
   akto-validate-   akto-validate-
   prompt.py        tool-request.py
        │              │
        └──────┬───────┘
               ↓
        ┌─────────────┐
        │ Akto Server │
        │  Guardrails │
        │     API     │
        └─────────────┘
```

### Tool Type Detection

The plugin automatically detects tool type and routes accordingly:

| Tool Type | Format | Handler | Path |
|-----------|--------|---------|------|
| **Non-MCP** | `read`, `glob` | Direct HTTP | `/v1/tools/execute` |
| **MCP** | `server_tool`, `calculator_add` | JSON-RPC via Python | `/mcp` |

**Detection Logic:**
- If tool name contains underscore (e.g., `calculator_add`) → MCP
- Otherwise (e.g., `read`, `glob`) → Non-MCP

---

## 📝 Usage Examples

### Example 1: Built-in Tool (Non-MCP)

```
User: "read dummy_server.py"
     ↓
Plugin detects: tool="read" (non-MCP)
     ↓
Sends HTTP to Akto: {"path": "/v1/tools/execute", ...}
     ↓
Akto returns: ALLOWED
     ↓
File is read ✅
```

**Logs:**
```
[TOOL_EXECUTE_BEFORE] {"tool":"read",...}
[NON_MCP_TOOL_DETECTED] {"tool":"read"}
[CURL_REQUEST] {...path":"/v1/tools/execute"...}
[CURL_RESPONSE] {"statusCode":200,...}
```

### Example 2: MCP Tool

```
User: "Use calculator_add to add 5 and 3"
     ↓
Plugin detects: tool="calculator_add" (MCP), server="calculator"
     ↓
Spawns: python3 akto-mcp-request.py
     ↓
Python builds JSON-RPC: {"jsonrpc":"2.0","method":"tools/call",...}
     ↓
Sends to Akto: {"path": "/mcp", "requestPayload": "{jsonrpc...}", ...}
     ↓
Akto returns: ALLOWED
     ↓
Tool executes, returns 8 ✅
```

**Logs (plugin):**
```
[TOOL_EXECUTE_BEFORE] {"tool":"calculator_add",...}
[MCP_TOOL_DETECTED] {"server":"calculator","mcpTool":"add"}
```

**Logs (Python handler):**
```
INFO - Processing MCP tool: calculator_add (server=calculator, tool=add)
INFO - API CALL: POST http://.../api/http-proxy?akto_connector=opencode
DEBUG - Request payload: {"path": "/mcp", "requestPayload": "{\"jsonrpc\": \"2.0\", \"method\": \"tools/call\"...
INFO - API RESPONSE: Status 200
INFO - MCP request ALLOWED
```

---

## 📊 Logging

### Log Locations

```
~/.config/opencode/akto/logs/
├── akto-guardrails.log          # Plugin activity (prompts, tool detection)
├── akto-mcp-request.log         # MCP request validation
├── akto-mcp-response.log        # MCP response logging
├── akto-validate-prompt.log     # Prompt validation details
├── akto-validate-tool-request.log   # Tool request validation
└── akto-validate-tool-response.log  # Tool response ingestion
```

### Log Levels

```bash
# Show only errors
export LOG_LEVEL="ERROR"

# Show all details (for debugging)
export LOG_LEVEL="DEBUG"
export LOG_PAYLOADS="true"  # Include full request/response bodies
```

### Viewing Logs

```bash
# Watch real-time
tail -f ~/.config/opencode/akto/logs/akto-guardrails.log

# Check MCP requests
cat ~/.config/opencode/akto/logs/akto-mcp-request.log

# Check MCP responses
cat ~/.config/opencode/akto/logs/akto-mcp-response.log
```

---

## 🧪 Testing

### Test Non-MCP Tools

```bash
# Set environment
export AKTO_DATA_INGESTION_URL="http://your-akto-server"

# Start OpenCode
opencode

# In OpenCode prompt:
read dummy_server.py

# Check logs
tail -f ~/.config/opencode/akto/logs/akto-guardrails.log

# Look for:
# ✅ [TOOL_EXECUTE_BEFORE] with tool="read"
# ✅ [NON_MCP_TOOL_DETECTED]
# ✅ [CURL_REQUEST] with path="/v1/tools/execute"
```

### Test MCP Tools

1. **Configure MCP server** in `~/.config/opencode/opencode.json`:
```json
{
  "mcp": {
    "calculator": {
      "type": "local",
      "command": ["node", "/path/to/test-mcp-server.js"],
      "enabled": true
    }
  }
}
```

2. **Start OpenCode:**
```bash
export AKTO_DATA_INGESTION_URL="http://your-akto-server"
export LOG_LEVEL="DEBUG"
opencode
```

3. **In OpenCode prompt:**
```
Use calculator_add to add 5 and 3
```

4. **Check logs:**
```bash
# Plugin logs
tail -f ~/.config/opencode/akto/logs/akto-guardrails.log
# Should show: [MCP_TOOL_DETECTED] {"server":"calculator",...}

# Python handler logs
cat ~/.config/opencode/akto/logs/akto-mcp-request.log
# Should show JSON-RPC payload with method="tools/call"
```

---

## 🔐 Data Sent to Akto

### Non-MCP Tools

```json
{
  "path": "/v1/tools/execute",
  "method": "POST",
  "requestPayload": "{\"tool\": \"read\", \"args\": {...}}",
  "requestHeaders": "{\"host\": \"https://opencode.ai/\", \"x-opencode-hook\": \"PreToolUse\"}",
  "tag": "{\"gen-ai\": \"Gen AI\", \"tool-use\": \"Tool Execution\", \"source\": \"ENDPOINT\"}"
}
```

### MCP Tools

```json
{
  "path": "/mcp",
  "method": "POST",
  "requestPayload": "{\"jsonrpc\": \"2.0\", \"method\": \"tools/call\", \"params\": {\"name\": \"add\", \"arguments\": {...}}}",
  "requestHeaders": "{\"host\": \"<device-id>.opencode.calculator\", \"x-opencode-hook\": \"PreToolUse\", \"x-mcp-server\": \"calculator\"}",
  "tag": "{\"mcp-server\": \"MCP Server\", \"mcp-client\": \"opencode\", \"source\": \"ENDPOINT\"}"
}
```

---

## ❓ Troubleshooting

### Plugin Not Loading

**Check logs:**
```bash
ls -la ~/.opencode/plugins/akto-*.py
ls -la ~/.opencode/plugins/akto-guardrails-plugin.js
```

**Ensure files are readable:**
```bash
chmod +x ~/.opencode/plugins/akto-*.py
```

### No Logs Appearing

**Verify Akto URL is set:**
```bash
echo $AKTO_DATA_INGESTION_URL
```

**If empty, set it:**
```bash
export AKTO_DATA_INGESTION_URL="http://your-akto-server"
```

**Restart OpenCode:**
```bash
killall opencode
opencode
```

### Python Scripts Not Running

**Check Python is available:**
```bash
python3 --version
```

**Check log directory exists:**
```bash
mkdir -p ~/.config/opencode/akto/logs
```

### MCP Tools Not Detected

**Verify opencode.json exists and is valid:**
```bash
cat ~/.config/opencode/opencode.json
```

**Check it's valid JSON:**
```bash
python3 -m json.tool ~/.config/opencode/opencode.json
```

**Restart OpenCode to reload config:**
```bash
killall opencode
sleep 2
opencode
```

---

## 📚 Architecture Details

### Hook Flow

1. **Prompt Hook** (`experimental.chat.messages.transform`)
   - Fires BEFORE prompt is sent to AI
   - Validates against Akto rules
   - Can BLOCK the prompt

2. **Tool Request Hook** (`tool.execute.before`)
   - Fires BEFORE tool is executed
   - Validates against Akto rules
   - Can BLOCK tool execution

3. **Tool Response Hook** (`tool.execute.after`)
   - Fires AFTER tool completes
   - Logs response for audit trail
   - Cannot block (execution already happened)

### MCP Protocol Integration

MCP (Model Context Protocol) tools communicate via JSON-RPC 2.0:

```
Client → tools/list (discover available tools)
Server ← [{"name": "add", "description": "..."}, ...]

Client → tools/call (execute tool)
         {"jsonrpc": "2.0", "method": "tools/call", "params": {"name": "add", "arguments": {"a": 5, "b": 3}}}
Server ← {"jsonrpc": "2.0", "id": 1, "result": {"output": "8"}}
```

The plugin detects this format and sends it to Akto's `/mcp` endpoint for analysis.

---

## 🤝 Integration Checklist

- [ ] Copy all `.py` files to `~/.opencode/plugins/`
- [ ] Copy `akto-guardrails-plugin.js` to `~/.opencode/plugins/`
- [ ] Set `AKTO_DATA_INGESTION_URL` environment variable
- [ ] (Optional) Configure MCP servers in `~/.config/opencode/opencode.json`
- [ ] Restart OpenCode: `killall opencode && opencode`
- [ ] Verify logs appear: `tail -f ~/.config/opencode/akto/logs/akto-guardrails.log`
- [ ] Test non-MCP tool: Type `read dummy_server.py`
- [ ] (Optional) Test MCP tool if configured

---

## 📞 Support

For issues or questions:
1. Check logs in `~/.config/opencode/akto/logs/`
2. Enable debug logging: `export LOG_LEVEL="DEBUG" LOG_PAYLOADS="true"`
3. Review the troubleshooting section above
4. Check Akto server is reachable: `curl http://your-akto-server/health`

---

## 📄 License

Part of the Akto API Security suite.

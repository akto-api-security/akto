# OpenCode Akto Integration Guide for Clients

Complete step-by-step guide to integrate Akto guardrails into your OpenCode installation.

## 📋 Prerequisites

- OpenCode installed on your system
- Python 3.6+ available
- Akto instance running with guardrails API endpoint
- Network access to your Akto server

---

## 🔧 Installation Steps

### Step 1: Obtain the Plugin Files

Get the OpenCode plugin package from the Akto repository:

```bash
# Clone or download the repository
git clone https://github.com/akto-api-security/akto.git
cd akto/apps/mcp-endpoint-shield/opencode
```

### Step 2: Copy Plugin to OpenCode

Copy all plugin files to OpenCode's plugins directory:

```bash
# Create plugins directory if it doesn't exist
mkdir -p ~/.opencode/plugins

# Copy all production files
cp akto-guardrails-plugin.js ~/.opencode/plugins/
cp akto-validate-prompt.py ~/.opencode/plugins/
cp akto-validate-tool-request.py ~/.opencode/plugins/
cp akto-validate-tool-response.py ~/.opencode/plugins/
cp akto-mcp-request.py ~/.opencode/plugins/
cp akto-mcp-response.py ~/.opencode/plugins/
cp akto_machine_id.py ~/.opencode/plugins/
cp settings.json ~/.opencode/plugins/

# Make Python scripts executable
chmod +x ~/.opencode/plugins/akto-*.py
```

**About settings.json:**
This file contains plugin metadata (name, version, hooks) that OpenCode uses for plugin discovery. You don't need to configure it—just copy it along with other files. OpenCode will read it automatically.

**Verify installation:**
```bash
ls -la ~/.opencode/plugins/akto-*
# Should show 8 files + settings.json
```

### Step 3: Configure Akto Server URL

Set your Akto instance URL (REQUIRED):

```bash
# For bash
echo 'export AKTO_DATA_INGESTION_URL="https://your-akto-instance.com/guardrails"' >> ~/.bashrc
source ~/.bashrc

# For zsh
echo 'export AKTO_DATA_INGESTION_URL="https://your-akto-instance.com/guardrails"' >> ~/.zshrc
source ~/.zshrc
```

**Test the URL is set:**
```bash
echo $AKTO_DATA_INGESTION_URL
# Should output your URL
```

### Step 4: (Optional) Configure MCP Servers

If you use MCP servers, add them to `~/.config/opencode/opencode.json`:

```bash
# Create OpenCode config directory if needed
mkdir -p ~/.config/opencode

# Create/edit opencode.json
cat > ~/.config/opencode/opencode.json << 'EOF'
{
  "$schema": "https://opencode.ai/config.json",
  "mcp": {
    "your-mcp-server-name": {
      "type": "local",
      "command": ["node", "/path/to/your/mcp/server.js"],
      "enabled": true
    }
  }
}
EOF
```

**Example with multiple servers:**
```json
{
  "$schema": "https://opencode.ai/config.json",
  "mcp": {
    "git": {
      "type": "local",
      "command": ["npx", "-y", "@modelcontextprotocol/server-git"],
      "enabled": true
    },
    "memory": {
      "type": "local",
      "command": ["npx", "-y", "@modelcontextprotocol/server-memory"],
      "enabled": true
    }
  }
}
```

### Step 5: Start OpenCode

```bash
# Kill any existing instance
killall opencode 2>/dev/null || true
sleep 2

# Start fresh
opencode
```

**Verify plugin is loaded:**
```bash
tail -f ~/.config/opencode/akto/logs/akto-guardrails.log
# Should see: [PLUGIN_INIT] {"message":"Akto guardrails plugin initialized"}
```

---

## ✅ Verification Checklist

After installation, verify everything works:

### 1. Plugin Files Copied ✓

```bash
ls -la ~/.opencode/plugins/akto-*
# Should show all plugin files including settings.json
```

### 2. Plugin Loaded ✓

```bash
cat ~/.config/opencode/akto/logs/akto-guardrails.log | head -5
# Should show: [PLUGIN_INIT]
```

### 3. Akto URL Configured ✓

```bash
grep AKTO_DATA_INGESTION_URL ~/.bashrc ~/.zshrc 2>/dev/null
# Should show your URL
```

### 4. Log Directory Created ✓

```bash
ls -la ~/.config/opencode/akto/logs/
# Should exist with akto-*.log files
```

### 5. Test with Built-in Tool ✓

```bash
# In OpenCode, type:
read SOME_FILE.txt

# Then check logs:
tail ~/.config/opencode/akto/logs/akto-guardrails.log
# Should show: [TOOL_EXECUTE_BEFORE] {"tool":"read",...}
```

### 6. (Optional) Test with MCP Tool ✓

If you configured MCP servers:

```bash
# In OpenCode, ask the LLM to use an MCP tool:
"Use the git tool to show the latest commit"

# Check logs:
tail ~/.config/opencode/akto/logs/akto-guardrails.log
# Should show: [MCP_TOOL_DETECTED] {...}

# Check MCP request logs:
tail ~/.config/opencode/akto/logs/akto-mcp-request.log
# Should show: "API RESPONSE: Status 200"
```

---

## 🚀 Usage

Once installed, the plugin works automatically:

### Built-in Tools (OpenCode)
```
User: "read my_file.txt"
↓
Plugin: Validates against Akto policies
↓
Akto Decision: ALLOW or BLOCK
↓
Tool: Executes or is blocked
```

### MCP Tools
```
User: "Use git to get the latest commit"
↓
Plugin: Detects MCP tool (git_*), spawns Python handler
↓
Python Handler: Converts to JSON-RPC, sends to Akto
↓
Akto Decision: ALLOW or BLOCK
↓
Tool: Executes or is blocked
```

### Prompts
```
User types prompt: "Hello, analyze this"
↓
Plugin: Validates prompt against Akto policies
↓
Akto Decision: ALLOW or BLOCK
↓
Prompt: Sent to AI or rejected
```

---

## 📊 Configuration Reference

### Environment Variables

```bash
# REQUIRED
export AKTO_DATA_INGESTION_URL="https://your-akto-server/guardrails"

# OPTIONAL - Logging
export LOG_LEVEL="INFO"                    # DEBUG, INFO, WARNING, ERROR
export LOG_PAYLOADS="false"                # Set to "true" for verbose payload logging
export LOG_DIR="~/.config/opencode/akto/logs"

# OPTIONAL - Advanced
export AKTO_TIMEOUT="5"                    # Request timeout in seconds
export MODE="argus"                        # "argus" (default) or "atlas"
export CONTEXT_SOURCE="ENDPOINT"           # Request classification
```

### OpenCode Config (opencode.json)

```json
{
  "$schema": "https://opencode.ai/config.json",
  "mcp": {
    "server-name": {
      "type": "local",                     // "local" for spawned process
      "command": ["executable", "arg1"],   // Command to start server
      "enabled": true                      // Enable/disable server
    }
  }
}
```

---

## 🔍 Monitoring & Logs

### View Real-time Activity

```bash
# Watch all plugin activity
tail -f ~/.config/opencode/akto/logs/akto-guardrails.log

# Watch prompt validation
tail -f ~/.config/opencode/akto/logs/akto-validate-prompt.log

# Watch tool validation
tail -f ~/.config/opencode/akto/logs/akto-validate-tool-request.log

# Watch MCP requests
tail -f ~/.config/opencode/akto/logs/akto-mcp-request.log

# Watch MCP responses
tail -f ~/.config/opencode/akto/logs/akto-mcp-response.log
```

### Log Locations

| Component | Log File |
|-----------|----------|
| Plugin | `~/.config/opencode/akto/logs/akto-guardrails.log` |
| Prompt Validation | `~/.config/opencode/akto/logs/akto-validate-prompt.log` |
| Tool Request | `~/.config/opencode/akto/logs/akto-validate-tool-request.log` |
| Tool Response | `~/.config/opencode/akto/logs/akto-validate-tool-response.log` |
| MCP Request | `~/.config/opencode/akto/logs/akto-mcp-request.log` |
| MCP Response | `~/.config/opencode/akto/logs/akto-mcp-response.log` |

### Enable Debug Logging

```bash
export LOG_LEVEL="DEBUG"
export LOG_PAYLOADS="true"

# Restart OpenCode
killall opencode
opencode
```

---

## ❓ Troubleshooting

### Plugin Not Loading

**Symptom:** No logs appear in `~/.config/opencode/akto/logs/`

**Solution:**
```bash
# Check plugin files exist
ls -la ~/.opencode/plugins/akto-*

# Check Python is available
python3 --version

# Create log directory
mkdir -p ~/.config/opencode/akto/logs

# Restart OpenCode
killall opencode
sleep 2
opencode
```

### Akto Server Unreachable

**Symptom:** Logs show "API CALL FAILED"

**Solution:**
```bash
# Verify URL is correct
echo $AKTO_DATA_INGESTION_URL

# Test connectivity
curl -I $AKTO_DATA_INGESTION_URL

# Check firewall/network
ping $(echo $AKTO_DATA_INGESTION_URL | sed 's|https://||; s|/.*||')
```

### MCP Tools Not Detected

**Symptom:** MCP tool doesn't appear as `server_tool` format

**Solution:**
```bash
# Check opencode.json is valid
python3 -m json.tool ~/.config/opencode/opencode.json

# Check MCP server is running manually
node /path/to/your/mcp/server.js

# Restart OpenCode to reload config
killall opencode
sleep 2
opencode
```

### Python Script Errors

**Symptom:** Errors in `akto-mcp-request.log`

**Solution:**
```bash
# Check Python version
python3 --version  # Should be 3.6+

# Test Python directly
python3 ~/.opencode/plugins/akto-mcp-request.py << EOF
{"tool_name": "test", "tool_input": {}}
EOF

# Enable verbose logging
export LOG_LEVEL="DEBUG"
export LOG_PAYLOADS="true"
```

---

## 📞 Support

For issues or questions:

1. **Check logs** - Always look at log files first:
   ```bash
   tail -50 ~/.config/opencode/akto/logs/akto-*.log
   ```

2. **Enable debug logging:**
   ```bash
   export LOG_LEVEL="DEBUG"
   export LOG_PAYLOADS="true"
   killall opencode
   opencode
   ```

3. **Test connectivity to Akto:**
   ```bash
   curl -v $AKTO_DATA_INGESTION_URL/health
   ```

4. **Review README.md** for detailed architecture and examples

---

## 📚 Next Steps

1. ✅ Installation complete
2. 📊 Monitor logs and verify working
3. 🔐 Configure Akto policies to match your security needs
4. 🎯 Set up alerts in Akto for policy violations
5. 📈 Review audit logs regularly

---



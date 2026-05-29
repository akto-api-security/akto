# Hermes Agent - Akto Guardrails Integration Guide

Step-by-step guide for clients to integrate Akto guardrails into Hermes Agent.

---

## 📋 Prerequisites

Before starting, ensure you have:

- ✅ Hermes Agent installed and functional
- ✅ Python 3.6 or higher
- ✅ Akto instance deployed and accessible
- ✅ Network access from your machine to Akto server
- ✅ Akto Data Ingestion URL (provided by your Akto admin)

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


## 🚀 Integration Steps

### Step 1: Download Plugin Files

Download or clone the plugin from GitHub:

**Option A: Clone from GitHub**
```bash
git clone https://github.com/akto-api-security/akto.git
cd akto/apps/mcp-endpoint-shield/hermes
```

**Option B: Download Individual Files**
```bash
# Create directory
mkdir -p ~/.hermes-akto-plugin
cd ~/.hermes-akto-plugin

# Download files from GitHub
curl -O https://raw.githubusercontent.com/akto-api-security/akto/master/apps/mcp-endpoint-shield/hermes/__init__.py
curl -O https://raw.githubusercontent.com/akto-api-security/akto/master/apps/mcp-endpoint-shield/hermes/akto_client.py
curl -O https://raw.githubusercontent.com/akto-api-security/akto/master/apps/mcp-endpoint-shield/hermes/validators.py
curl -O https://raw.githubusercontent.com/akto-api-security/akto/master/apps/mcp-endpoint-shield/hermes/config.py
curl -O https://raw.githubusercontent.com/akto-api-security/akto/master/apps/mcp-endpoint-shield/hermes/logging_util.py
curl -O https://raw.githubusercontent.com/akto-api-security/akto/master/apps/mcp-endpoint-shield/hermes/akto_machine_id.py
```

---

### Step 2: Install Plugin

Copy plugin files to Hermes plugins directory:

```bash
# Create plugins directory if it doesn't exist
mkdir -p ~/.hermes/plugins

# Copy plugin folder
cp -r /path/to/hermes ~/.hermes/plugins/akto-guardrails

# Verify installation
ls -la ~/.hermes/plugins/akto-guardrails/
```

**Expected output:**
```
-rw-r--r-- __init__.py
-rw-r--r-- akto_client.py
-rw-r--r-- validators.py
-rw-r--r-- config.py
-rw-r--r-- logging_util.py
-rw-r--r-- akto_machine_id.py
```

---

### Step 3: Configure Akto URL (REQUIRED)

Set the Akto Data Ingestion URL as an environment variable:

**For bash:**
```bash
echo 'export AKTO_DATA_INGESTION_URL="https://your-akto-instance.com/guardrails"' >> ~/.bashrc
source ~/.bashrc
```

**For zsh:**
```bash
echo 'export AKTO_DATA_INGESTION_URL="https://your-akto-instance.com/guardrails"' >> ~/.zshrc
source ~/.zshrc
```

**Verify the URL is set:**
```bash
echo $AKTO_DATA_INGESTION_URL
# Should output: https://your-akto-instance.com/guardrails
```

---

### Step 4: Configure Hermes to Load Plugin

Edit or create `~/.hermes/config.json`:

```json
{
  "plugins": {
    "akto-guardrails": {
      "enabled": true,
      "config": {
        "AKTO_DATA_INGESTION_URL": "http://akto-guardrails.akto.io-instance",
        "AKTO_SYNC_MODE": "true",
        "LOG_LEVEL": "INFO"
      }
    }
  }
}
```

**What each setting means:**
- `enabled: true` - Plugin is active
- `AKTO_DATA_INGESTION_URL` - Your Akto server URL (required)
- `AKTO_SYNC_MODE: "true"` - Block risky prompts/tools
- `LOG_LEVEL: "INFO"` - Log important events only

---

### Step 5: Create Log Directory

```bash
mkdir -p ~/.config/hermes/akto/logs
chmod 755 ~/.config/hermes/akto/logs
```

Logs will be written to: `~/.config/hermes/akto/logs/hermes-guardrails.log`

---

### Step 6: Start Hermes

```bash
# Start Hermes with guardrails plugin loaded
hermes
```

**You should see plugin initialization in the logs:**
```bash
tail -f ~/.config/hermes/akto/logs/hermes-guardrails.log
```

Look for:
```
INFO - === Hermes Akto Guardrails Plugin Initializing ===
INFO - Registered hook: pre_llm_call
INFO - Registered hook: post_llm_call
INFO - Registered hook: pre_tool_call
INFO - Registered hook: post_tool_call
INFO - === Hermes Akto Guardrails Plugin Ready ===
```

---

## ✅ Verification

### Test 1: Basic Prompt Validation

1. In Hermes prompt, enter:
   ```
   > What is the capital of France?
   ```

2. Check logs for validation:
   ```bash
   grep "pre_llm_call" ~/.config/hermes/akto/logs/hermes-guardrails.log
   ```

3. Should see:
   ```
   INFO - [pre_llm_call] Processing prompt from session=...
   INFO - [pre_llm_call] Prompt allowed, continuing to AI
   ```

✅ **Result:** Prompt validated and allowed

### Test 2: Response Logging

After AI responds, check logs:
```bash
grep "post_llm_call" ~/.config/hermes/akto/logs/hermes-guardrails.log
```

Should see:
```
INFO - [post_llm_call] Logging response from session=...
INFO - [post_llm_call] Response logged successfully
```

✅ **Result:** Response logged successfully

### Test 3: Akto Dashboard

1. Log in to Akto dashboard
2. Navigate to "Events" or "Traffic" section
3. Filter by connector: "hermes"
4. Should see HTTP requests logged
5. Check for `/v1/messages` (prompts) and `/v1/tools/execute` (tools)

✅ **Result:** Events visible in Akto dashboard

---

## 🔧 Configuration Options

### Optional Environment Variables

```bash
# Behavior
export AKTO_SYNC_MODE="false"           # "false" = logging only (don't block)
export AKTO_TIMEOUT="5"                 # Timeout in seconds

# Logging
export LOG_LEVEL="DEBUG"                # DEBUG, INFO, WARNING, ERROR
export LOG_PAYLOADS="true"              # Log full request/response bodies
export LOG_DIR="~/.config/hermes/akto/logs"

# Advanced
export MODE="argus"                     # "argus" (default) or "atlas"
export CONTEXT_SOURCE="ENDPOINT"        # Request classification
export AKTO_CONNECTOR="hermes"          # Connector name
```

**Or configure in `~/.hermes/config.json`:**
```json
{
  "plugins": {
    "akto-guardrails": {
      "enabled": true,
      "config": {
        "AKTO_DATA_INGESTION_URL": "https://your-akto-instance.com/guardrails",
        "AKTO_SYNC_MODE": "true",
        "AKTO_TIMEOUT": "5",
        "LOG_LEVEL": "INFO",
        "LOG_PAYLOADS": false,
        "MODE": "argus"
      }
    }
  }
}
```

---

## 🛡️ Understanding Plugin Behavior

### Blocking Mode (AKTO_SYNC_MODE="true")

When enabled, the plugin validates prompts and tools BEFORE execution:

```
User Input
    ↓
Plugin validates against Akto rules
    ↓
IF violated:
  → Block and show reason
    ↓
  User sees: "🚫 Your prompt was blocked by Akto guardrails: [reason]"
    
IF allowed:
  → Continue to AI
```

### Logging Mode (AKTO_SYNC_MODE="false")

When disabled, the plugin logs activity but doesn't block:

```
User Input
    ↓
Execute immediately
    ↓
Plugin logs to Akto (non-blocking)
    ↓
User sees: AI response
```

### Fail-Open Behavior

If Akto server is unreachable:
- Prompts are **allowed** (never block when service fails)
- Tools are **allowed** (never block when service fails)
- Execution continues normally
- Errors are logged but not shown to user

---

## 📊 What Gets Logged

The plugin logs all of these to Akto:

| Event | When | Data |
|-------|------|------|
| **Prompt Submission** | User sends message | Prompt text, session ID, model |
| **AI Response** | Claude generates answer | Response text, conversation history |
| **Tool Call** | Before tool execution | Tool name, arguments, task ID |
| **Tool Result** | After tool completes | Tool name, output, execution time |

All events include:
- Session identifier
- Timestamp
- Device/machine identifier
- Platform information
- Security tags

---

## 🔍 Monitoring & Logs

### View Real-Time Activity

```bash
tail -f ~/.config/hermes/akto/logs/hermes-guardrails.log
```

### Search for Specific Events

```bash
# Find all prompt validations
grep "pre_llm_call" ~/.config/hermes/akto/logs/hermes-guardrails.log

# Find all blocks
grep "BLOCKING" ~/.config/hermes/akto/logs/hermes-guardrails.log

# Find tool calls
grep "pre_tool_call\|post_tool_call" ~/.config/hermes/akto/logs/hermes-guardrails.log

# Find errors
grep "ERROR\|error\|failed\|Failed" ~/.config/hermes/akto/logs/hermes-guardrails.log
```

### Enable Debug Logging

```bash
export LOG_LEVEL="DEBUG"
export LOG_PAYLOADS="true"
hermes
```

This will log:
- Full request/response payloads
- Detailed hook execution flow
- Timing information
- Configuration values

---

## ❓ Troubleshooting

### Plugin Not Loading

**Problem:** No logs appear in `~/.config/hermes/akto/logs/`

**Solution:**
1. Verify plugin files exist:
   ```bash
   ls -la ~/.hermes/plugins/akto-guardrails/
   ```

2. Check Hermes config:
   ```bash
   cat ~/.hermes/config.json | grep -A5 akto-guardrails
   ```

3. Verify Python version:
   ```bash
   python3 --version  # Need 3.6+
   ```

4. Check Hermes startup for errors:
   ```bash
   hermes 2>&1 | grep -i "error\|akto"
   ```

### Akto Server Unreachable

**Problem:** Logs show "API CALL FAILED"

**Solution:**
1. Verify URL is correct:
   ```bash
   echo $AKTO_DATA_INGESTION_URL
   ```

2. Test connectivity:
   ```bash
   curl -I https://your-akto-instance.com/guardrails
   ```

3. Check firewall:
   ```bash
   ping $(echo $AKTO_DATA_INGESTION_URL | sed 's|https://||; s|/.*||')
   ```

4. Verify Akto is running:
   ```bash
   curl https://your-akto-instance.com/health
   ```

### Prompts Not Being Blocked

**Problem:** Risky prompts are still allowed

**Solution:**
1. Check if blocking is enabled:
   ```bash
   echo $AKTO_SYNC_MODE
   # Should be "true"
   ```

2. Verify guardrails rules exist in Akto:
   - Log in to Akto dashboard
   - Check "Guardrails" or "Policies" section
   - Ensure rules are configured and active
   - Ensure rules apply to "hermes" connector

3. Check logs for validation:
   ```bash
   grep "pre_llm_call\|BLOCKING" ~/.config/hermes/akto/logs/hermes-guardrails.log
   ```

### No Events in Akto Dashboard

**Problem:** Plugin runs but events don't appear in dashboard

**Solution:**
1. Verify URL is set and correct:
   ```bash
   echo $AKTO_DATA_INGESTION_URL
   ```

2. Check logs for API errors:
   ```bash
   grep "API CALL\|API RESPONSE" ~/.config/hermes/akto/logs/hermes-guardrails.log
   ```

3. Enable debug logging:
   ```bash
   export LOG_LEVEL="DEBUG"
   export LOG_PAYLOADS="true"
   hermes
   ```

4. Test API connectivity directly:
   ```bash
   curl -X POST "$AKTO_DATA_INGESTION_URL/api/http-proxy?akto_connector=hermes" \
     -H "Content-Type: application/json" \
     -d '{"test": "payload"}'
   ```

---

## 📋 Integration Checklist

Before considering the integration complete, verify:

- [ ] Plugin files copied to `~/.hermes/plugins/akto-guardrails/`
- [ ] `AKTO_DATA_INGESTION_URL` environment variable set
- [ ] `~/.hermes/config.json` includes plugin configuration
- [ ] Log directory created: `~/.config/hermes/akto/logs`
- [ ] Hermes starts without errors: `hermes`
- [ ] Plugin initialization logged
- [ ] Normal prompts allowed and logged
- [ ] Events appear in Akto dashboard
- [ ] Akto guardrails rules configured
- [ ] Test blocking with a simple rule
- [ ] All logs appear as expected

---

## 🎯 Next Steps

### 1. Configure Akto Guardrails Rules

In your Akto dashboard:
1. Create policies for your organization
2. Define what should be blocked (e.g., prompts containing "password", "secret", etc.)
3. Configure enforcement mode (block vs. warn)
4. Set alert notifications

### 2. Monitor Dashboard

Regularly check Akto dashboard for:
- Blocked prompts (if any)
- Tool usage patterns
- Security events
- API usage metrics

### 3. Fine-Tune Rules

Adjust guardrails rules based on:
- False positives (blocking legitimate requests)
- Security findings
- Business requirements
- Compliance needs

### 4. Enable Team Access

Share Akto dashboard with team members:
1. Add users to Akto
2. Configure role-based access
3. Set up alerts and notifications

---

## 📞 Support & Questions

If you encounter issues:

1. **Check logs:**
   ```bash
   tail -f ~/.config/hermes/akto/logs/hermes-guardrails.log
   ```

2. **Enable debug:**
   ```bash
   export LOG_LEVEL=DEBUG LOG_PAYLOADS=true && hermes
   ```

3. **Verify configuration:**
   ```bash
   echo $AKTO_DATA_INGESTION_URL
   cat ~/.hermes/config.json
   ```

4. **Test Akto connectivity:**
   ```bash
   curl -I https://your-akto-instance.com/guardrails
   ```

5. **Contact Akto support:**
   - Email: support@akto.io
   - Docs: https://akto.io/docs
   - Community: https://www.akto.io/community

---

## 📖 Additional Resources

- **README.md** - Detailed plugin documentation
- **Akto Documentation** - https://akto.io/docs
- **GitHub Repository** - https://github.com/akto-api-security/akto

---

Good luck! Your Hermes Agent is now protected by Akto guardrails. 🛡️

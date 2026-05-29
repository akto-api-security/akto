# Hermes Akto Guardrails - Quick Start (5 minutes)

Fast setup guide for the Hermes Agent guardrails plugin.

---

## ⚡ Installation (3 steps)

### 1️⃣ Copy Plugin
```bash
cp -r /path/to/hermes ~/.hermes/plugins/akto-guardrails
```

### 2️⃣ Set Akto URL
```bash
export AKTO_DATA_INGESTION_URL="https://your-akto-instance.com/guardrails"
```

### 3️⃣ Configure Hermes
Edit `~/.hermes/config.json`:
```json
{
  "plugins": {
    "akto-guardrails": {
      "enabled": true,
      "config": {
        "AKTO_DATA_INGESTION_URL": "https://your-akto-instance.com/guardrails"
      }
    }
  }
}
```

---

## ▶️ Start Hermes
```bash
hermes
```

✅ Done! Plugin is active.

---

## 🧪 Quick Test

```bash
# Watch logs
tail -f ~/.config/hermes/akto/logs/hermes-guardrails.log &

# In Hermes prompt
> What is Python?

# Check logs show validation
# Look for: "[pre_llm_call] Prompt allowed"
```

---

## 📋 What Gets Protected

| Action | Protected By | Blocking |
|--------|---|---|
| User prompts | `pre_llm_call` | ✅ Yes (if rule violated) |
| AI responses | `post_llm_call` | ❌ No (logging only) |
| Tool calls | `pre_tool_call` | ✅ Yes (if rule violated) |
| Tool results | `post_tool_call` | ❌ No (logging only) |

---

## 🔧 Configuration

### Essential
```bash
AKTO_DATA_INGESTION_URL="https://..."  # Required!
```

### Optional
```bash
AKTO_SYNC_MODE="true"        # Block risky prompts
AKTO_TIMEOUT="5"             # Timeout in seconds
LOG_LEVEL="INFO"             # DEBUG/INFO/WARNING/ERROR
```

---

## 🔍 Monitor Activity

```bash
# Real-time logs
tail -f ~/.config/hermes/akto/logs/hermes-guardrails.log

# Find blocks
grep "BLOCKING" ~/.config/hermes/akto/logs/hermes-guardrails.log

# Enable debug
export LOG_LEVEL="DEBUG" && hermes
```

---

## ✅ Verify Installation

```bash
# Plugin files exist
ls ~/.hermes/plugins/akto-guardrails/__init__.py

# URL is set
echo $AKTO_DATA_INGESTION_URL

# Logs are created
ls -la ~/.config/hermes/akto/logs/

# Can start Hermes
hermes --help
```

---

## 🛠️ Troubleshooting

| Problem | Solution |
|---------|----------|
| No logs | Check `AKTO_DATA_INGESTION_URL` is set |
| Plugin not loading | Verify `~/.hermes/config.json` syntax |
| Akto unreachable | Check firewall/VPN, plugin still works (fail-open) |
| Prompts not blocked | Enable AKTO_SYNC_MODE="true", create blocking rules |

---

## 📚 Full Docs

- **Complete Setup** → [INTEGRATION_GUIDE.md](INTEGRATION_GUIDE.md)
- **Testing** → [TESTING.md](TESTING.md)
- **Details** → [README.md](README.md)
- **Architecture** → [IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md)

---

## 🚀 What's Next

1. ✅ Plugin installed
2. Create blocking rules in Akto dashboard
3. Monitor logs and events
4. Adjust rules based on activity
5. Enable for team

---

That's it! Your Hermes Agent is now protected by Akto guardrails. 🛡️

# Akto Guardrails for Claude CLI

Validate your prompts against Akto AI Guardrails before they're sent to Claude.

## Setup

### 1. Download the plugin

```bash
mkdir -p ~/.claude/hooks
cd ~/.claude/hooks
```

### 2. Configure environment

Add the following environment variables to your shell configuration file (e.g. `~/.bashrc`, `~/.zshrc`, or `~/.profile`):

```bash
# Add these to ~/.zshrc
export AKTO_DATA_INGESTION_URL="ingestion-service-url"
export AKTO_SYNC_MODE="true" # Set to false if you want to allow prompts if guardrails blocks them but still send them to Claude
```

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
                        "command": "python3 ~/.claude/hooks/validate-prompt.py",
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
                        "command": "python3 ~/.claude/hooks/validate-response.py",
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

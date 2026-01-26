# Akto Guardrails for Claude CLI

Validate your prompts against Akto AI Guardrails before they're sent to Claude.

## Setup

### 1. Download the plugin

```bash
mkdir -p ~/.claude/hooks
cd ~/.claude/hooks
# Download validate-prompt.py and .env.example here
```

### 2. Configure environment

```bash
cp .env.example .env
```

Edit `.env` with your values:
```bash
AKTO_GUARDRAILS_URL=http://localhost:80
DATABASE_ABSTRACTOR_SERVICE_TOKEN=your-token-here
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
        ]
    }
}
```

### 4. Restart Claude CLI

```bash
claude
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `AKTO_GUARDRAILS_URL` | `http://localhost:80` | Guardrails service URL |
| `DATABASE_ABSTRACTOR_SERVICE_TOKEN` | - | Auth token for hosted guardrails |
| `AKTO_GUARDRAILS_TIMEOUT` | `5` | Request timeout (seconds) |
| `AKTO_GUARDRAILS_FAIL_OPEN` | `true` | Allow prompts if guardrails unavailable |

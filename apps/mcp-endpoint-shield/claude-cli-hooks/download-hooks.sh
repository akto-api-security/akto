#!/usr/bin/env bash
# Download Akto Claude CLI hooks from the Akto repo (see README.md for env + settings.json).
set -euo pipefail

HOOKS_BASE="https://raw.githubusercontent.com/akto-api-security/akto/refs/heads/master/apps/mcp-endpoint-shield/claude-cli-hooks"

mkdir -p ~/.claude/hooks

# User prompt + assistant response (UserPromptSubmit, Stop)
curl -fsSL -o ~/.claude/hooks/akto-validate-prompt-wrapper.sh \
  "${HOOKS_BASE}/akto-validate-prompt-wrapper.sh"
curl -fsSL -o ~/.claude/hooks/akto-validate-prompt.py \
  "${HOOKS_BASE}/akto-validate-prompt.py"
curl -fsSL -o ~/.claude/hooks/akto-validate-response-wrapper.sh \
  "${HOOKS_BASE}/akto-validate-response-wrapper.sh"
curl -fsSL -o ~/.claude/hooks/akto-validate-response.py \
  "${HOOKS_BASE}/akto-validate-response.py"

# MCP tool use (PreToolUse, PostToolUse)
curl -fsSL -o ~/.claude/hooks/akto-validate-mcp-request-wrapper.sh \
  "${HOOKS_BASE}/akto-validate-mcp-request-wrapper.sh"
curl -fsSL -o ~/.claude/hooks/akto-validate-mcp-request.py \
  "${HOOKS_BASE}/akto-validate-mcp-request.py"
curl -fsSL -o ~/.claude/hooks/akto-validate-mcp-response-wrapper.sh \
  "${HOOKS_BASE}/akto-validate-mcp-response-wrapper.sh"
curl -fsSL -o ~/.claude/hooks/akto-validate-mcp-response.py \
  "${HOOKS_BASE}/akto-validate-mcp-response.py"

# Shared utility (machine / device id)
curl -fsSL -o ~/.claude/hooks/akto_machine_id.py \
  "${HOOKS_BASE}/akto_machine_id.py"

# Hooks wiring for Claude Code (merge "hooks" into ~/.claude/settings.json — see README)
curl -fsSL -o ~/.claude/hooks/settings.json \
  "${HOOKS_BASE}/settings.json"

mkdir -p ~/.claude
if [ ! -f ~/.claude/settings.json ]; then
  cp ~/.claude/hooks/settings.json ~/.claude/settings.json
  echo "Created ~/.claude/settings.json with Akto hooks (no existing Claude settings file)."
else
  echo "Note: ~/.claude/settings.json already exists. Merge the \"hooks\" key from ~/.claude/hooks/settings.json if you have not already." >&2
fi

chmod +x ~/.claude/hooks/*.sh ~/.claude/hooks/*.py

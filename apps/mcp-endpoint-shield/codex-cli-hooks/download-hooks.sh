#!/usr/bin/env bash
# Download Akto Codex CLI hooks from the Akto repo (see README.md for env + hooks.json).
set -euo pipefail

# Default until merged to mainline; override with AKTO_CODEX_HOOKS_REF=master (or any branch/ref).
HOOKS_REF="${AKTO_CODEX_HOOKS_REF:-hotfix/codex_hooks}"
HOOKS_BASE="https://raw.githubusercontent.com/akto-api-security/akto/refs/heads/${HOOKS_REF}/apps/mcp-endpoint-shield/codex-cli-hooks"

mkdir -p ~/.codex/hooks

# User prompt + assistant response (UserPromptSubmit, Stop)
curl -fsSL -o ~/.codex/hooks/akto-validate-prompt-wrapper.sh \
  "${HOOKS_BASE}/akto-validate-prompt-wrapper.sh"
curl -fsSL -o ~/.codex/hooks/akto-validate-prompt.py \
  "${HOOKS_BASE}/akto-validate-prompt.py"
curl -fsSL -o ~/.codex/hooks/akto-validate-response-wrapper.sh \
  "${HOOKS_BASE}/akto-validate-response-wrapper.sh"
curl -fsSL -o ~/.codex/hooks/akto-validate-response.py \
  "${HOOKS_BASE}/akto-validate-response.py"

# Bash tool use (PreToolUse, PostToolUse)
curl -fsSL -o ~/.codex/hooks/akto-validate-pre-tool-wrapper.sh \
  "${HOOKS_BASE}/akto-validate-pre-tool-wrapper.sh"
curl -fsSL -o ~/.codex/hooks/akto-validate-pre-tool.py \
  "${HOOKS_BASE}/akto-validate-pre-tool.py"
curl -fsSL -o ~/.codex/hooks/akto-validate-post-tool-wrapper.sh \
  "${HOOKS_BASE}/akto-validate-post-tool-wrapper.sh"
curl -fsSL -o ~/.codex/hooks/akto-validate-post-tool.py \
  "${HOOKS_BASE}/akto-validate-post-tool.py"

# Shared utility (machine / device id)
curl -fsSL -o ~/.codex/hooks/akto_machine_id.py \
  "${HOOKS_BASE}/akto_machine_id.py"

# Example env (optional reference)
curl -fsSL -o ~/.codex/hooks/.env.example \
  "${HOOKS_BASE}/.env.example"

# Hooks wiring for Codex (merge "hooks" into ~/.codex/hooks.json — see README)
curl -fsSL -o ~/.codex/hooks/hooks.json \
  "${HOOKS_BASE}/hooks.json"

mkdir -p ~/.codex
if [ ! -f ~/.codex/hooks.json ]; then
  cp ~/.codex/hooks/hooks.json ~/.codex/hooks.json
  echo "Created ~/.codex/hooks.json with Akto hooks (no existing Codex hooks file)."
else
  echo "Note: ~/.codex/hooks.json already exists. Merge the \"hooks\" key from ~/.codex/hooks/hooks.json if you have not already." >&2
fi

chmod +x ~/.codex/hooks/*.sh ~/.codex/hooks/*.py

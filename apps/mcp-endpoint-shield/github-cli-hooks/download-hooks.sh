#!/usr/bin/env bash
# Download Akto GitHub Copilot hook scripts into ~/.github/hooks (override with GITHUB_HOOKS_DEST).
# Copilot loads hook commands from each repository's .github/hooks.json — that file is generated
# (or merged) to point at the shared scripts under $HOME/.github/hooks.
#
# Enforcement vs monitoring (per GitHub docs):
# - preToolUse: stdout permissionDecision / permissionDecisionReason can deny tool execution.
# - userPromptSubmitted, postToolUse: hook output is ignored for blocking; use for validation + ingest + stderr.
#
# https://docs.github.com/en/copilot/reference/hooks-configuration
set -euo pipefail

# Walk up from $start_dir until we find a .git file or directory (used only for default hooks.json placement).
find_git_root() {
  local dir="${1:-$PWD}"
  while [[ "$dir" != "/" ]]; do
    if [[ -e "$dir/.git" ]]; then
      printf '%s\n' "$dir"
      return 0
    fi
    dir="$(dirname "$dir")"
  done
  return 1
}

# Default until merged to mainline; override with AKTO_GITHUB_HOOKS_REF=master (or any branch/ref).
HOOKS_REF="${AKTO_GITHUB_HOOKS_REF:-hotfix/copilot_hooks_behaviour}"
HOOKS_BASE="https://raw.githubusercontent.com/akto-api-security/akto/refs/heads/${HOOKS_REF}/apps/mcp-endpoint-shield/github-cli-hooks"

# Hook scripts default to ~/.github/hooks (override with GITHUB_HOOKS_DEST).
DEST="${GITHUB_HOOKS_DEST:-$HOME/.github/hooks}"
mkdir -p "$DEST"
echo "Installing Akto Copilot hook scripts to: $DEST" >&2

curl -fsSL -o "$DEST/akto-validate-prompt-wrapper.sh" \
  "${HOOKS_BASE}/akto-validate-prompt-wrapper.sh"
curl -fsSL -o "$DEST/akto-validate-prompt-wrapper.ps1" \
  "${HOOKS_BASE}/akto-validate-prompt-wrapper.ps1"
curl -fsSL -o "$DEST/akto-validate-prompt.py" \
  "${HOOKS_BASE}/akto-validate-prompt.py"

curl -fsSL -o "$DEST/akto-validate-pre-tool-wrapper.sh" \
  "${HOOKS_BASE}/akto-validate-pre-tool-wrapper.sh"
curl -fsSL -o "$DEST/akto-validate-pre-tool-wrapper.ps1" \
  "${HOOKS_BASE}/akto-validate-pre-tool-wrapper.ps1"
curl -fsSL -o "$DEST/akto-validate-pre-tool.py" \
  "${HOOKS_BASE}/akto-validate-pre-tool.py"

curl -fsSL -o "$DEST/akto-validate-post-tool-wrapper.sh" \
  "${HOOKS_BASE}/akto-validate-post-tool-wrapper.sh"
curl -fsSL -o "$DEST/akto-validate-post-tool-wrapper.ps1" \
  "${HOOKS_BASE}/akto-validate-post-tool-wrapper.ps1"
curl -fsSL -o "$DEST/akto-validate-post-tool.py" \
  "${HOOKS_BASE}/akto-validate-post-tool.py"

curl -fsSL -o "$DEST/akto_machine_id.py" \
  "${HOOKS_BASE}/akto_machine_id.py"
curl -fsSL -o "$DEST/akto_heartbeat.py" \
  "${HOOKS_BASE}/akto_heartbeat.py"

curl -fsSL -o "$DEST/.env.example" \
  "${HOOKS_BASE}/.env.example"

chmod +x "${DEST}/"*.sh "${DEST}/"*.py 2>/dev/null || true

# Generate hooks.json with absolute paths to DEST (default ~/.github/hooks). Copilot reads this from the repo root.
export DEST
python3 - <<'PY' >"${DEST}/hooks.json.published"
import json
import os
from pathlib import Path

dest = Path(os.environ["DEST"]).resolve()

def bash_cmd(script: str) -> str:
    return f'bash "{dest / script}"'

def ps_cmd(script: str) -> str:
    p = dest / script
    return f'powershell -ExecutionPolicy Bypass -File "{p}"'

doc = (
    "Validate prompts against Akto Guardrails (monitoring only - cannot block per GitHub limitation)"
)
hooks = {
    "version": 1,
    "hooks": {
        "userPromptSubmitted": [
            {
                "type": "command",
                "bash": bash_cmd("akto-validate-prompt-wrapper.sh"),
                "powershell": ps_cmd("akto-validate-prompt-wrapper.ps1"),
                "comment": doc,
                "timeoutSec": 30,
            }
        ],
        "preToolUse": [
            {
                "type": "command",
                "bash": bash_cmd("akto-validate-pre-tool-wrapper.sh"),
                "powershell": ps_cmd("akto-validate-pre-tool-wrapper.ps1"),
                "comment": "Validate and block tool execution based on Akto Guardrails policies",
                "timeoutSec": 30,
            }
        ],
        "postToolUse": [
            {
                "type": "command",
                "bash": bash_cmd("akto-validate-post-tool-wrapper.sh"),
                "powershell": ps_cmd("akto-validate-post-tool-wrapper.ps1"),
                "comment": "Response guardrails + ingest (stdout ignored by Copilot postToolUse — see GitHub hooks docs)",
                "timeoutSec": 30,
            }
        ],
    },
}
print(json.dumps(hooks, indent=2))
PY
mv "${DEST}/hooks.json.published" "${DEST}/hooks.json"

if [[ -n "${GITHUB_HOOKS_JSON:-}" ]]; then
  HOOKS_JSON_OUT="$GITHUB_HOOKS_JSON"
elif REPO_ROOT="$(find_git_root "$PWD" 2>/dev/null)" && [[ -n "$REPO_ROOT" ]]; then
  HOOKS_JSON_OUT="$REPO_ROOT/.github/hooks.json"
else
  HOOKS_JSON_OUT="$PWD/.github/hooks.json"
fi
mkdir -p "$(dirname "$HOOKS_JSON_OUT")"
if [ ! -f "$HOOKS_JSON_OUT" ]; then
  cp "$DEST/hooks.json" "$HOOKS_JSON_OUT"
  echo "Created $HOOKS_JSON_OUT (points at hook scripts under $DEST)." >&2
else
  echo "Note: $HOOKS_JSON_OUT already exists. Merge the \"hooks\" key from $DEST/hooks.json if you have not already." >&2
fi

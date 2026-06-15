#!/usr/bin/env bash
# install-shell-hooks.sh - Install Akto MCP Endpoint Shield hooks for OpenCode
# Pure bash - no python, no jq, no third-party dependencies
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HOOKS_INSTALL_DIR="${HOME}/.opencode/plugins/akto"

# ── helpers ──────────────────────────────────────────────────────────────────
info()  { printf '\033[0;32m[INFO]\033[0m  %s\n' "$*"; }
warn()  { printf '\033[0;33m[WARN]\033[0m  %s\n' "$*"; }
error() { printf '\033[0;31m[ERROR]\033[0m %s\n' "$*" >&2; }

detect_runtime() {
  # Honour override
  if [ -n "${AKTO_HOOK_RUNTIME:-}" ]; then
    echo "$AKTO_HOOK_RUNTIME"; return
  fi
  if command -v python3 >/dev/null 2>&1; then echo "python3"; else echo "bash"; fi
}

# ── parse args ────────────────────────────────────────────────────────────────
INSTALL_DIR=""
while [[ $# -gt 0 ]]; do
  case $1 in
    --dir) INSTALL_DIR="$2"; shift 2 ;;
    --help|-h)
      echo "Usage: $0 [--dir <path>]"
      echo ""
      echo "Options:"
      echo "  --dir <path>   Override install directory (default: ~/.opencode/plugins/akto)"
      exit 0 ;;
    *) error "Unknown argument: $1"; exit 1 ;;
  esac
done
[ -n "$INSTALL_DIR" ] && HOOKS_INSTALL_DIR="$INSTALL_DIR"

# ── install ───────────────────────────────────────────────────────────────────
info "Installing Akto hooks for OpenCode → $HOOKS_INSTALL_DIR"
mkdir -p "$HOOKS_INSTALL_DIR"

# Copy bash hooks
mkdir -p "$HOOKS_INSTALL_DIR/bash"
for f in akto_common.sh akto-validate-prompt.sh akto-validate-tool-request.sh akto-validate-tool-response.sh; do
  src="$SCRIPT_DIR/bash/$f"
  if [ -f "$src" ]; then
    cp "$src" "$HOOKS_INSTALL_DIR/bash/$f"
    chmod +x "$HOOKS_INSTALL_DIR/bash/$f"
    info "  Copied bash/$f"
  else
    warn "  Missing: bash/$f"
  fi
done

# Copy PowerShell hooks (optional - used when pwsh is available on Windows/Mac/Linux)
mkdir -p "$HOOKS_INSTALL_DIR/powershell"
for f in AktoCommon.psm1 akto-validate-prompt.ps1 akto-validate-tool-request.ps1 akto-validate-tool-response.ps1; do
  src="$SCRIPT_DIR/powershell/$f"
  if [ -f "$src" ]; then
    cp "$src" "$HOOKS_INSTALL_DIR/powershell/$f"
    info "  Copied powershell/$f"
  else
    warn "  Missing: powershell/$f"
  fi
done

# ── write runtime-detecting wrapper scripts ───────────────────────────────────
write_bash_wrapper() {
  local wrapper_path="$1"
  local python_hook_rel="$2"  # relative path inside mcp-endpoint-shield package
  local bash_script_name="$3"
  local ps_script_name="$4"

  cat > "$wrapper_path" << WRAPPER
#!/usr/bin/env bash
# Runtime-detecting wrapper - prefers python3 > pwsh > bash
AKTO_INSTALL_DIR="\$(cd "\$(dirname "\${BASH_SOURCE[0]}")" && pwd)"
RUNTIME="\${AKTO_HOOK_RUNTIME:-}"

if [ -z "\$RUNTIME" ]; then
  if command -v python3 >/dev/null 2>&1; then RUNTIME="python3"
  elif command -v pwsh >/dev/null 2>&1; then RUNTIME="pwsh"
  else RUNTIME="bash"; fi
fi

case "\$RUNTIME" in
  python3)
    PYTHON_HOOKS_DIR="\${AKTO_PYTHON_HOOKS_DIR:-}"
    if [ -n "\$PYTHON_HOOKS_DIR" ] && [ -f "\$PYTHON_HOOKS_DIR/$python_hook_rel" ]; then
      exec python3 "\$PYTHON_HOOKS_DIR/$python_hook_rel" "\$@"
    fi
    # Fallthrough to bash if python hooks not found
    exec bash "\$AKTO_INSTALL_DIR/bash/$bash_script_name" "\$@"
    ;;
  pwsh|powershell)
    exec pwsh -NonInteractive -NoProfile -File "\$AKTO_INSTALL_DIR/powershell/$ps_script_name" "\$@"
    ;;
  bash|*)
    exec bash "\$AKTO_INSTALL_DIR/bash/$bash_script_name" "\$@"
    ;;
esac
WRAPPER
  chmod +x "$wrapper_path"
}

info "Writing runtime-detecting wrapper scripts..."
write_bash_wrapper \
  "$HOOKS_INSTALL_DIR/akto-validate-prompt" \
  "akto-validate-prompt.py" \
  "akto-validate-prompt.sh" \
  "akto-validate-prompt.ps1"

write_bash_wrapper \
  "$HOOKS_INSTALL_DIR/akto-validate-tool-request" \
  "akto-validate-tool-request.py" \
  "akto-validate-tool-request.sh" \
  "akto-validate-tool-request.ps1"

write_bash_wrapper \
  "$HOOKS_INSTALL_DIR/akto-validate-tool-response" \
  "akto-validate-tool-response.py" \
  "akto-validate-tool-response.sh" \
  "akto-validate-tool-response.ps1"

# ── write opencode plugin config ──────────────────────────────────────────────
OPENCODE_CONFIG_DIR="${HOME}/.opencode"
mkdir -p "$OPENCODE_CONFIG_DIR"

PLUGIN_CONFIG="$OPENCODE_CONFIG_DIR/plugins/akto/plugin.json"
mkdir -p "$(dirname "$PLUGIN_CONFIG")"

cat > "$PLUGIN_CONFIG" << JSON
{
  "name": "akto-endpoint-shield",
  "version": "1.0.0",
  "hooks": {
    "prompt.submit.before": {
      "command": "$HOOKS_INSTALL_DIR/akto-validate-prompt",
      "timeout": 10000
    },
    "tool.execute.before": {
      "command": "$HOOKS_INSTALL_DIR/akto-validate-tool-request",
      "timeout": 10000
    },
    "tool.execute.after": {
      "command": "$HOOKS_INSTALL_DIR/akto-validate-tool-response",
      "timeout": 10000
    }
  }
}
JSON
info "Wrote plugin config → $PLUGIN_CONFIG"

# ── check opencode config for plugin registration ─────────────────────────────
OPENCODE_SETTINGS="$OPENCODE_CONFIG_DIR/config.json"
if [ -f "$OPENCODE_SETTINGS" ]; then
  if grep -q "akto-endpoint-shield" "$OPENCODE_SETTINGS" 2>/dev/null; then
    info "Plugin already registered in $OPENCODE_SETTINGS"
  else
    warn "OpenCode config found at $OPENCODE_SETTINGS"
    warn "Please add the plugin manually or via 'opencode plugins add $HOOKS_INSTALL_DIR'"
  fi
else
  info "No existing opencode config found."
  info "Register plugin with: opencode plugins add $HOOKS_INSTALL_DIR"
fi

info ""
info "Installation complete!"
info ""
info "Required environment variables:"
info "  AKTO_DATA_INGESTION_URL   Akto gateway URL (required)"
info "  DEVICE_ID                 Device identifier (auto-detected if unset)"
info "  MODE                      Deployment mode: atlas (default) or direct"
info "  AKTO_SYNC_MODE            Sync validation: true (default) or false"
info "  AKTO_HOOK_RUNTIME         Force runtime: python3 | pwsh | bash"
info ""
info "Optional:"
info "  AKTO_API_TOKEN            API token for authentication"
info "  AKTO_CONNECTOR            Connector tag (default: opencode)"
info "  CONTEXT_SOURCE            Context source tag (default: ENDPOINT)"
info "  LOG_LEVEL                 DEBUG | INFO | WARNING | ERROR (default: INFO)"
info "  LOG_PAYLOADS              Log request payloads: true | false (default: false)"

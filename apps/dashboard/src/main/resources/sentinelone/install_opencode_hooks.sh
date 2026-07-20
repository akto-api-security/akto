#!/bin/bash

# ========================================================================================
# Akto Endpoint Shield - OpenCode Plugin Installer
# ========================================================================================
# Automatically installs the Akto guardrails plugin for OpenCode if detected.
# Downloads the latest plugin + Python handlers from GitHub and drops them into
# OpenCode's global plugin directory (~/.config/opencode/plugin).
#
# Unlike the CLI hook integrations (Claude/Gemini/Cursor/Codex) that register command
# hooks in a settings.json, OpenCode loads a JavaScript plugin *in-process* and reads
# its configuration from environment variables. Since OpenCode is launched interactively
# (no wrapper to export env vars), this installer bakes the config into a prelude that is
# prepended to the plugin. The prelude also re-reads ~/.akto-endpoint-shield/config/config.env
# on every OpenCode start, so URL / token / enable-flag changes take effect live.
#
# Controlled by flags in config.env (Jamf/enterprise only):
#   ENABLE_PROMPT_HOOKS_OPENCODE=false  AND  ENABLE_MCP_HOOKS_OPENCODE=false
#     -> plugin is uninstalled / disabled. Default (flag absent) = enabled.
#
# Mirrors: mcp-endpoint-shield/misc/macos/install_opencode_hooks.sh (master branch)
# ========================================================================================

# Ensure common binary paths are available (RTR/remote execution uses minimal PATH)
export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$PATH"

set -e

# Configuration
GITHUB_RAW_BASE="https://raw.githubusercontent.com/akto-api-security/akto/master/apps/mcp-endpoint-shield/opencode"
GITHUB_SHARED_BASE="https://raw.githubusercontent.com/akto-api-security/akto/master/apps/mcp-endpoint-shield/shared"

TARGET_USER_HOME="${TARGET_USER_HOME:-}"
AKTO_DATA_INGESTION_URL="${AKTO_DATA_INGESTION_URL:-}"
AKTO_API_TOKEN="${AKTO_API_TOKEN:-}"
for a in "$@"; do
    case "$a" in
        TARGET_USER_HOME=*) TARGET_USER_HOME="${a#TARGET_USER_HOME=}" ;;
        AKTO_DATA_INGESTION_URL=*) AKTO_DATA_INGESTION_URL="${a#AKTO_DATA_INGESTION_URL=}" ;;
        AKTO_API_TOKEN=*) AKTO_API_TOKEN="${a#AKTO_API_TOKEN=}" ;;
    esac
done

[ -n "$AKTO_DATA_INGESTION_URL" ] && export AKTO_DATA_INGESTION_URL
[ -n "$AKTO_API_TOKEN" ] && export AKTO_API_TOKEN

# Log function
log() {
    echo "[OpenCode Hooks] $1"
}

log_error() {
    echo "[OpenCode Hooks] ERROR: $1" >&2
}

install_for_user() {
    local user_home="$1"

    export HOME="$user_home"
    TARGET_USER_HOME="$user_home"
    OPENCODE_CONFIG_DIR="$user_home/.config/opencode"
    OPENCODE_PLUGIN_DIR="$OPENCODE_CONFIG_DIR/plugin"
    PLUGIN_FILE="$OPENCODE_PLUGIN_DIR/akto-guardrails-plugin.js"
    CONFIG_FILE="$user_home/.akto-endpoint-shield/config/config.env"

    if main; then
        return 0
    else
        log_error "Installation failed for $user_home"
        return 1
    fi
}

# Check if OpenCode is installed
check_opencode_installed() {
    if command -v opencode >/dev/null 2>&1 \
        || [ -d "$OPENCODE_CONFIG_DIR" ] \
        || [ -d "$TARGET_USER_HOME/.opencode" ]; then
        return 0
    fi
    return 1
}

# Returns 0 if the OpenCode plugin should be installed.
# Bail (return 1) only when BOTH flags are explicitly "false" — mirrors the Cursor
# installer gate. Default (flag absent) = enabled.
is_opencode_enabled() {
    local prompt_flag="" mcp_flag=""

    prompt_flag="$ENABLE_PROMPT_HOOKS_OPENCODE"
    mcp_flag="$ENABLE_MCP_HOOKS_OPENCODE"

    if [ -f "$CONFIG_FILE" ]; then
        [ -z "$prompt_flag" ] && prompt_flag=$(grep "^ENABLE_PROMPT_HOOKS_OPENCODE=" "$CONFIG_FILE" 2>/dev/null | cut -d= -f2-)
        [ -z "$mcp_flag" ]    && mcp_flag=$(grep "^ENABLE_MCP_HOOKS_OPENCODE=" "$CONFIG_FILE" 2>/dev/null | cut -d= -f2-)
    fi

    if [ "$prompt_flag" = "false" ] && [ "$mcp_flag" = "false" ]; then
        return 1
    fi
    return 0
}

# Get data ingestion URL. Canonical env var is AKTO_API_BASE_URL (matches Go binary).
# AKTO_DATA_INGESTION_URL is accepted as a backward-compatible alias.
get_ingestion_url() {
    if [ -n "$AKTO_API_BASE_URL" ];        then echo "$AKTO_API_BASE_URL";        return 0; fi
    if [ -n "$AKTO_DATA_INGESTION_URL" ];  then echo "$AKTO_DATA_INGESTION_URL";  return 0; fi

    if [ -f "$CONFIG_FILE" ]; then
        local url
        url=$(grep "^AKTO_API_BASE_URL=" "$CONFIG_FILE" 2>/dev/null | cut -d= -f2-)
        [ -n "$url" ] && { echo "$url"; return 0; }
        url=$(grep "^AKTO_DATA_INGESTION_URL=" "$CONFIG_FILE" 2>/dev/null | cut -d= -f2-)
        [ -n "$url" ] && { echo "$url"; return 0; }
    fi
    return 1
}

get_api_token() {
    if [ -n "$AKTO_API_TOKEN" ]; then echo "$AKTO_API_TOKEN"; return 0; fi
    if [ -f "$CONFIG_FILE" ]; then
        local token
        token=$(grep "^AKTO_API_TOKEN=" "$CONFIG_FILE" 2>/dev/null | cut -d= -f2-)
        [ -n "$token" ] && { echo "$token"; return 0; }
    fi
    return 1
}

# Generate device label matching Go's GetDeviceLabel() format:
# "{hostname}-{first8ofMachineID}" (e.g. "macbook-pro-a1b2c3d4")
# This must stay in sync with utils/device.go GetDeviceLabel().
generate_device_label() {
    local device_name
    device_name=$(scutil --get ComputerName 2>/dev/null | tr ' ' '-')
    [ -z "$device_name" ] && device_name=$(hostname 2>/dev/null | sed 's/\.local$//' | tr ' ' '-')

    local machine_id=""
    if command -v ioreg >/dev/null 2>&1; then
        local uuid
        uuid=$(ioreg -rd1 -c IOPlatformExpertDevice 2>/dev/null | grep IOPlatformUUID | awk -F'"' '{print $4}')
        if [ -n "$uuid" ]; then
            machine_id=$(echo "$uuid" | tr -d '-' | tr '[:upper:]' '[:lower:]')
        fi
    fi

    if [ -z "$machine_id" ] && command -v ifconfig >/dev/null 2>&1; then
        local mac
        mac=$(ifconfig en0 2>/dev/null | grep ether | awk '{print $2}' | tr -d ':')
        [ -n "$mac" ] && machine_id=$(echo "$mac" | tr '[:upper:]' '[:lower:]')
    fi

    local short_id="${machine_id:0:8}"

    if [ -n "$device_name" ] && [ -n "$short_id" ]; then
        echo "${device_name}-${short_id}"
    elif [ -n "$device_name" ]; then
        echo "$device_name"
    elif [ -n "$machine_id" ]; then
        echo "$machine_id"
    else
        echo "unknown-device"
    fi
}

# Download file from GitHub
download_file() {
    local url="$1"
    local dest="$2"

    if command -v curl >/dev/null 2>&1; then
        curl -fsSL -H "Cache-Control: no-cache" -H "Pragma: no-cache" "$url" -o "$dest" 2>/dev/null
        return $?
    elif command -v wget >/dev/null 2>&1; then
        wget -q --no-cache "$url" -O "$dest" 2>/dev/null
        return $?
    else
        log_error "Neither curl nor wget available"
        return 1
    fi
}

# Build the injected config prelude (placeholders substituted below) and prepend it
# to the upstream plugin. The prelude:
#   - sets HOME from USERPROFILE if unset (Windows parity; harmless on macOS)
#   - loads ~/.akto-endpoint-shield/config/config.env on every OpenCode start so the
#     URL / token / device id / enable-flag can be updated without re-running installer
#   - falls back to the values baked in at install time
#   - empties the ingestion URL (which fail-opens => disables the plugin) when both
#     ENABLE_PROMPT_HOOKS_OPENCODE and ENABLE_MCP_HOOKS_OPENCODE are "false"
write_plugin_with_prelude() {
    local upstream="$1"      # downloaded plugin.js
    local ingestion_url="$2"
    local api_token="$3"
    local device_id="$4"
    local dest="$5"

    local prelude
    prelude=$(mktemp)
    cat > "$prelude" <<'PRELUDE'
// === Akto Endpoint Shield — injected configuration (generated by installer) ===
// Do not edit by hand; regenerated on every install / config poll.
;(() => {
  const _os = require('os')
  const _fs = require('fs')
  const _path = require('path')
  if (!process.env.HOME && process.env.USERPROFILE) process.env.HOME = process.env.USERPROFILE

  // Values baked in at install time.
  const _defaults = {
    AKTO_DATA_INGESTION_URL: "__AKTO_URL__",
    AKTO_API_TOKEN: "__AKTO_TOKEN__",
    AKTO_SYNC_MODE: "true",
    AKTO_TIMEOUT: "5",
    AKTO_CONNECTOR: "opencode",
    MODE: "atlas",
    DEVICE_ID: "__DEVICE_ID__",
    CONTEXT_SOURCE: "ENDPOINT",
  }

  // Live override: parse config.env (bash-style KEY=VALUE) if present.
  const _cfg = {}
  try {
    const _p = _path.join(_os.homedir(), ".akto-endpoint-shield", "config", "config.env")
    if (_fs.existsSync(_p)) {
      for (const _line of _fs.readFileSync(_p, "utf8").split(/\r?\n/)) {
        const _m = _line.match(/^\s*([A-Z_][A-Z0-9_]*)=(.*)$/)
        if (_m) _cfg[_m[1]] = _m[2].replace(/^["']|["']$/g, "")
      }
    }
  } catch (_e) { /* fail open — use baked defaults */ }

  // AKTO_API_BASE_URL is the canonical URL name used by the Go binary.
  const _url = _cfg.AKTO_API_BASE_URL || _cfg.AKTO_DATA_INGESTION_URL || _defaults.AKTO_DATA_INGESTION_URL
  const _apply = (k, v) => { if (v !== undefined && v !== "" && !process.env[k]) process.env[k] = v }
  _apply("AKTO_DATA_INGESTION_URL", _url)
  _apply("AKTO_API_TOKEN", _cfg.AKTO_API_TOKEN || _defaults.AKTO_API_TOKEN)
  for (const _k of ["AKTO_SYNC_MODE", "AKTO_TIMEOUT", "AKTO_CONNECTOR", "MODE", "DEVICE_ID", "CONTEXT_SOURCE"]) {
    _apply(_k, _cfg[_k] || _defaults[_k])
  }

  // Live disable: emptying the URL makes the plugin fail-open (no data sent, no blocking).
  if (_cfg.ENABLE_PROMPT_HOOKS_OPENCODE === "false" && _cfg.ENABLE_MCP_HOOKS_OPENCODE === "false") {
    process.env.AKTO_DATA_INGESTION_URL = ""
  }
})()
// === end injected configuration ===

PRELUDE

    # Substitute baked values (| delimiter — URLs contain slashes).
    sed -e "s|__AKTO_URL__|$ingestion_url|g" \
        -e "s|__AKTO_TOKEN__|$api_token|g" \
        -e "s|__DEVICE_ID__|$device_id|g" \
        "$prelude" > "$dest"
    cat "$upstream" >> "$dest"
    rm -f "$prelude"
}

# Main installation function
main() {
    log "Starting OpenCode plugin installation..."
    log "Target user home: $TARGET_USER_HOME"

    if ! check_opencode_installed; then
        log "OpenCode not detected - skipping plugin installation"
        return 0
    fi

    log "✓ OpenCode detected"

    if ! is_opencode_enabled; then
        log "Both ENABLE_PROMPT_HOOKS_OPENCODE and ENABLE_MCP_HOOKS_OPENCODE are false — skipping plugin installation"
        return 0
    fi

    INGESTION_URL=$(get_ingestion_url)
    if [ -z "$INGESTION_URL" ]; then
        log_error "AKTO_API_BASE_URL is not set — cannot install plugin. Set it in config.env or pass it as an environment variable."
        return 1
    fi

    API_TOKEN=$(get_api_token)
    if [ -z "$API_TOKEN" ]; then
        log_error "AKTO_API_TOKEN is not set — cannot install plugin. Set it in config.env or pass it as an environment variable."
        return 1
    fi

    DEVICE_ID=$(generate_device_label)
    [ -z "$DEVICE_ID" ] && DEVICE_ID="unknown-device"
    log "Device label: $DEVICE_ID"

    mkdir -p "$OPENCODE_PLUGIN_DIR"
    log "✓ Plugin directory: $OPENCODE_PLUGIN_DIR"

    # -----------------------------------------------------------------------
    # Download Python handlers + shared utilities. They live alongside the
    # plugin because the plugin resolves them via path.dirname(__filename).
    # -----------------------------------------------------------------------
    log "Downloading plugin files from GitHub..."

    if ! download_file "$GITHUB_SHARED_BASE/akto_ingestion_utility.py" "$OPENCODE_PLUGIN_DIR/akto_ingestion_utility.py"; then
        log_error "Failed to download akto_ingestion_utility.py"
        return 1
    fi
    chmod +x "$OPENCODE_PLUGIN_DIR/akto_ingestion_utility.py"
    log "✓ Downloaded akto_ingestion_utility.py"

    OPENCODE_PY_FILES=(
        "akto_machine_id.py"
        "akto-validate-prompt.py"
        "akto-validate-tool-request.py"
        "akto-validate-tool-response.py"
        "akto-mcp-request.py"
        "akto-mcp-response.py"
    )
    for f in "${OPENCODE_PY_FILES[@]}"; do
        if ! download_file "$GITHUB_RAW_BASE/$f" "$OPENCODE_PLUGIN_DIR/$f"; then
            log_error "Failed to download $f"
            return 1
        fi
        chmod +x "$OPENCODE_PLUGIN_DIR/$f"
        log "✓ Downloaded $f"
    done

    # -----------------------------------------------------------------------
    # Download the plugin, then write it with the injected config prelude.
    # -----------------------------------------------------------------------
    local upstream_plugin
    upstream_plugin=$(mktemp)
    if ! download_file "$GITHUB_RAW_BASE/akto-guardrails-plugin.js" "$upstream_plugin"; then
        log_error "Failed to download akto-guardrails-plugin.js"
        rm -f "$upstream_plugin"
        return 1
    fi

    write_plugin_with_prelude "$upstream_plugin" "$INGESTION_URL" "$API_TOKEN" "$DEVICE_ID" "$PLUGIN_FILE"
    rm -f "$upstream_plugin"
    log "✓ Installed akto-guardrails-plugin.js"

    # Ensure the log directory the plugin writes to exists.
    mkdir -p "$OPENCODE_CONFIG_DIR/akto/logs"

    # RTR runs as root — the plugin dir and akto/logs dir we just created are root-owned by
    # default, which locks the real user (and OpenCode itself, running unprivileged) out of
    # its own plugin/log files. Hand ownership back on exactly what we touched — not the
    # whole opencode config dir, so unrelated OpenCode state is never disturbed. macOS
    # stat -f, falling back to GNU stat -c.
    REAL_USER="$(stat -f '%Su' "$TARGET_USER_HOME" 2>/dev/null || stat -c '%U' "$TARGET_USER_HOME" 2>/dev/null || echo "")"
    if [ -n "$REAL_USER" ] && [ "$REAL_USER" != "root" ]; then
        chown -R "$REAL_USER" "$OPENCODE_PLUGIN_DIR" "$OPENCODE_CONFIG_DIR/akto" 2>/dev/null || true
        log "✓ Set ownership to $REAL_USER"
    fi

    log ""
    log "=========================================="
    log "✅ OpenCode plugin installed successfully!"
    log "=========================================="
    log ""
    log "Plugin location: $PLUGIN_FILE"
    log "Logs:            $OPENCODE_CONFIG_DIR/akto/logs"
    log "Restart OpenCode to load the plugin."
    log ""
    return 0
}

if [ -n "$TARGET_USER_HOME" ]; then
    if [ ! -d "$TARGET_USER_HOME" ]; then
        log_error "TARGET_USER_HOME is not a valid directory: $TARGET_USER_HOME"
        exit 1
    fi
    install_for_user "$TARGET_USER_HOME"
    exit $?
else
    EXIT_CODE=0

    for u in /Users/*; do
        u="${u%/}"
        base="$(basename "$u")"
        [ "$base" = "Shared" ] && continue
        [ "$base" = "Guest" ] && continue
        case "$base" in .*) continue ;; esac
        [ -d "$u" ] || continue

        log "=== Processing user: $u ==="
        install_for_user "$u" || EXIT_CODE=1
    done
    exit $EXIT_CODE
fi

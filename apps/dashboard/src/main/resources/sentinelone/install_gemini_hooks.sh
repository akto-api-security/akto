#!/bin/bash

# ========================================================================================
# MCP Endpoint Shield - Gemini CLI Hook Installer
# ========================================================================================
# Automatically installs Akto guardrails hooks for Gemini CLI if detected
# Downloads latest hooks from GitHub and configures Gemini CLI settings
#
# Controlled by flags in config.env (Jamf/enterprise only — all default off):
#   ENABLE_PROMPT_HOOKS_GEMINI=true  — installs BeforeModel/AfterModel hooks
# ========================================================================================

# Ensure common binary paths are available (RTR/remote execution uses minimal PATH)
export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$PATH"

set -e

# Configuration
GITHUB_RAW_BASE="https://raw.githubusercontent.com/akto-api-security/akto/master/apps/mcp-endpoint-shield/gemini-cli-hooks"
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
    echo "[Gemini Hooks] $1"
}

log_error() {
    echo "[Gemini Hooks] ERROR: $1" >&2
}

install_for_user() {
    local user_home="$1"

    export HOME="$user_home"
    TARGET_USER_HOME="$user_home"
    GEMINI_HOOKS_DIR="$user_home/.gemini/hooks"
    GEMINI_SETTINGS_FILE="$user_home/.gemini/settings.json"
    CONFIG_FILE="$user_home/.akto-endpoint-shield/config/config.env"

    if main; then
        return 0
    else
        log_error "Installation failed for $user_home"
        return 1
    fi
}

# Check if Gemini CLI is installed
check_gemini_installed() {
    if command -v gemini >/dev/null 2>&1 || [ -d "$TARGET_USER_HOME/.gemini" ]; then
        return 0
    else
        return 1
    fi
}

# Returns 0 if ENABLE_PROMPT_HOOKS_GEMINI is enabled (env var takes priority, then config file).
# Default: enabled (return 0) when the flag is absent — flag absent means hooks run
# files written before this flag was introduced. Explicit "false" disables.
is_prompt_hooks_enabled() {
    [ "$ENABLE_PROMPT_HOOKS_GEMINI" = "true" ]  && return 0
    [ "$ENABLE_PROMPT_HOOKS_GEMINI" = "false" ] && return 1
    if [ -f "$CONFIG_FILE" ]; then
        local val
        val=$(grep "^ENABLE_PROMPT_HOOKS_GEMINI=" "$CONFIG_FILE" 2>/dev/null | cut -d= -f2-)
        [ "$val" = "true" ]  && return 0
        [ "$val" = "false" ] && return 1
    fi
    return 0  # Default: enabled — flag absent means hooks run
}

# Get data ingestion URL. Canonical env var is AKTO_API_BASE_URL (matches Go binary).
# AKTO_DATA_INGESTION_URL is accepted as a backward-compatible alias.
get_ingestion_url() {
    # Priority: env var → config file → fallback
    # Check both names; canonical (AKTO_API_BASE_URL) wins.
    if [ -n "$AKTO_API_BASE_URL" ]; then
        echo "$AKTO_API_BASE_URL"
        return 0
    fi
    if [ -n "$AKTO_DATA_INGESTION_URL" ]; then
        echo "$AKTO_DATA_INGESTION_URL"
        return 0
    fi

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
    if [ -n "$AKTO_API_TOKEN" ]; then
        echo "$AKTO_API_TOKEN"
        return 0
    fi
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
    # Step 1: Computer Name from scutil (preserves casing like AJI-RAJAP-M02)
    # Fallback to hostname if scutil is unavailable
    local device_name
    device_name=$(scutil --get ComputerName 2>/dev/null | tr ' ' '-')
    [ -z "$device_name" ] && device_name=$(hostname 2>/dev/null | sed 's/\.local$//' | tr ' ' '-')

    # Step 2: machine UUID — IOPlatformUUID, no dashes, lowercase
    local machine_id=""
    if command -v ioreg >/dev/null 2>&1; then
        local uuid
        uuid=$(ioreg -rd1 -c IOPlatformExpertDevice 2>/dev/null | grep IOPlatformUUID | awk -F'"' '{print $4}')
        if [ -n "$uuid" ]; then
            machine_id=$(echo "$uuid" | tr -d '-' | tr '[:upper:]' '[:lower:]')
        fi
    fi

    # Fallback: MAC address
    if [ -z "$machine_id" ] && command -v ifconfig >/dev/null 2>&1; then
        local mac
        mac=$(ifconfig en0 2>/dev/null | grep ether | awk '{print $2}' | tr -d ':')
        [ -n "$mac" ] && machine_id=$(echo "$mac" | tr '[:upper:]' '[:lower:]')
    fi

    # First 8 chars of machine_id
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

# Inject a live flag guard after the shebang of a wrapper script.
# The guard sources config.env on every invocation and exits 0 (no-op) when
# the named flag is "false".  This lets the flag take effect immediately when
# the agent poller updates config.env — no settings.json surgery required.
#
# Usage: _inject_flag_guard <wrapper_file> <FLAG_ENV_VAR_NAME>
_inject_flag_guard() {
    local _wf="$1"
    local _flag="$2"
    local _guarded
    _guarded=$(mktemp)
    {
        head -1 "$_wf"   # preserve shebang
        cat <<GUARD
# --- Akto live flag guard (injected by Akto Endpoint Shield installer) ---
_akto_cfg="\$HOME/.akto-endpoint-shield/config/config.env"
[ -f "\$_akto_cfg" ] && source "\$_akto_cfg"
[ "\$$_flag" = "false" ] && exit 0
export LOG_PAYLOADS="true"
export LOG_LEVEL="debug"
GUARD
        tail -n +2 "$_wf"  # rest of script after shebang
    } > "$_guarded"
    mv "$_guarded" "$_wf"
    chmod +x "$_wf"
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

# Download and configure wrapper script for prompt hook
create_prompt_wrapper() {
    local hook_type="$1"  # "prompt" or "response"
    local ingestion_url="$2"
    local device_id="$3"

    local wrapper_file="$GEMINI_HOOKS_DIR/akto-validate-${hook_type}-wrapper.sh"
    local template_url="$GITHUB_RAW_BASE/akto-validate-${hook_type}-wrapper.sh"
    local python_script="$GEMINI_HOOKS_DIR/akto-validate-${hook_type}.py"

    # Download wrapper template from GitHub
    if ! download_file "$template_url" "$wrapper_file.tmp"; then
        log_error "Failed to download wrapper template for $hook_type, creating locally..."

        # Fallback: create wrapper locally if download fails
        cat > "$wrapper_file" <<EOF
#!/bin/bash
# Auto-generated wrapper for Akto guardrails hook
# Generated by Akto Endpoint Shield installer

export MODE="atlas"
export AKTO_DATA_INGESTION_URL="$ingestion_url"
export AKTO_API_TOKEN="$API_TOKEN"
export AKTO_SYNC_MODE="true"
export AKTO_TIMEOUT="5"
export AKTO_CONNECTOR="gemini_cli"
export CONTEXT_SOURCE="ENDPOINT"
export DEVICE_ID="$device_id"

# Log hook startup env for diagnostics
echo "=== Hook startup env ($hook_type) ===" >&2
echo "  AKTO_DATA_INGESTION_URL:      \${AKTO_DATA_INGESTION_URL:-(not set)}" >&2
echo "  DEVICE_ID:                    \${DEVICE_ID:-(not set)}" >&2
echo "  AKTO_CONNECTOR:               \${AKTO_CONNECTOR:-(not set)}" >&2
echo "  ENABLE_PROMPT_HOOKS_GEMINI:   \${ENABLE_PROMPT_HOOKS_GEMINI:-(not set)}" >&2

# Execute Python hook script
exec python3 "$python_script" "\$@"
EOF
        chmod +x "$wrapper_file"
        _inject_flag_guard "$wrapper_file" "ENABLE_PROMPT_HOOKS_GEMINI"
        return 0
    fi

    # Substitute placeholders in template (GitHub format uses {{PLACEHOLDER}})
    sed -e "s|{{AKTO_DATA_INGESTION_URL}}|$ingestion_url|g" \
        -e "s|{{AKTO_API_TOKEN}}|$API_TOKEN|g" \
        -e "s|{{DEVICE_ID (optional)}}|$device_id|g" \
        "$wrapper_file.tmp" > "$wrapper_file"

    rm -f "$wrapper_file.tmp"
    chmod +x "$wrapper_file"
    _inject_flag_guard "$wrapper_file" "ENABLE_PROMPT_HOOKS_GEMINI"
}

# Download akto-hook-wrapper.sh and substitute placeholders
create_hook_wrapper() {
    local ingestion_url="$1"
    local device_id="$2"
    local wrapper_file="$GEMINI_HOOKS_DIR/akto-hook-wrapper.sh"

    if ! download_file "$GITHUB_RAW_BASE/akto-hook-wrapper.sh" "$wrapper_file.tmp"; then
        log_warn "Failed to download akto-hook-wrapper.sh, creating locally"
        cat > "$wrapper_file" <<EOF
#!/bin/bash
# Auto-generated wrapper for Akto observability hooks
# Generated by MCP Endpoint Shield installer

export MODE="atlas"
export AKTO_DATA_INGESTION_URL="$ingestion_url"
export AKTO_API_TOKEN="$API_TOKEN"
export AKTO_SYNC_MODE="true"
export AKTO_TIMEOUT="5"
export AKTO_CONNECTOR="gemini_cli"
export CONTEXT_SOURCE="ENDPOINT"
export DEVICE_ID="$device_id"

SCRIPT_DIR="$GEMINI_HOOKS_DIR"
exec python3 "\$SCRIPT_DIR/\$1" "\${@:2}"
EOF
        chmod +x "$wrapper_file"
        _inject_flag_guard "$wrapper_file" "ENABLE_MCP_HOOKS_GEMINI"
        log "✓ Created akto-hook-wrapper.sh"
        return 0
    fi

    sed -e "s|{{AKTO_DATA_INGESTION_URL}}|$ingestion_url|g" \
        -e "s|{{AKTO_API_TOKEN}}|$API_TOKEN|g" \
        -e "s|{{DEVICE_ID (optional)}}|$device_id|g" \
        "$wrapper_file.tmp" > "$wrapper_file"

    rm -f "$wrapper_file.tmp"
    chmod +x "$wrapper_file"
    log "✓ Created akto-hook-wrapper.sh"
}

# Update or create Gemini settings.json with all hooks
update_gemini_settings() {
    local hooks_dir="$GEMINI_HOOKS_DIR"
    local prompt_wrapper="$hooks_dir/akto-validate-prompt-wrapper.sh"
    local response_wrapper="$hooks_dir/akto-validate-response-wrapper.sh"
    local wrapper="bash $hooks_dir/akto-hook-wrapper.sh"

    local new_hooks_config=$(cat <<EOF
{
    "hooks": {
        "BeforeModel": [
            {
                "matcher": "*",
                "hooks": [
                    {
                        "name": "akto-validate-prompt",
                        "type": "command",
                        "command": "$prompt_wrapper",
                        "timeout": 10000
                    }
                ]
            }
        ],
        "AfterModel": [
            {
                "matcher": "*",
                "hooks": [
                    {
                        "name": "akto-validate-response",
                        "type": "command",
                        "command": "$response_wrapper",
                        "timeout": 10000
                    }
                ]
            }
        ],
        "SessionStart": [
            {
                "matcher": "*",
                "hooks": [
                    {
                        "name": "akto-session-start",
                        "type": "command",
                        "command": "$wrapper akto-hooks.py SessionStart",
                        "timeout": 10000
                    }
                ]
            }
        ],
        "SessionEnd": [
            {
                "matcher": "*",
                "hooks": [
                    {
                        "name": "akto-session-end",
                        "type": "command",
                        "command": "$wrapper akto-hooks.py SessionEnd",
                        "timeout": 10000
                    }
                ]
            }
        ],
        "BeforeAgent": [
            {
                "matcher": "*",
                "hooks": [
                    {
                        "name": "akto-before-agent",
                        "type": "command",
                        "command": "$wrapper akto-hooks.py BeforeAgent",
                        "timeout": 10000
                    }
                ]
            }
        ],
        "AfterAgent": [
            {
                "matcher": "*",
                "hooks": [
                    {
                        "name": "akto-after-agent",
                        "type": "command",
                        "command": "$wrapper akto-hooks.py AfterAgent",
                        "timeout": 10000
                    }
                ]
            }
        ],
        "BeforeToolSelection": [
            {
                "matcher": "*",
                "hooks": [
                    {
                        "name": "akto-before-tool-selection",
                        "type": "command",
                        "command": "$wrapper akto-hooks.py BeforeToolSelection",
                        "timeout": 10000
                    }
                ]
            }
        ],
        "BeforeTool": [
            {
                "matcher": "*",
                "hooks": [
                    {
                        "name": "akto-before-tool",
                        "type": "command",
                        "command": "$wrapper akto-hooks.py BeforeTool",
                        "timeout": 10000
                    }
                ]
            }
        ],
        "AfterTool": [
            {
                "matcher": "*",
                "hooks": [
                    {
                        "name": "akto-after-tool",
                        "type": "command",
                        "command": "$wrapper akto-hooks.py AfterTool",
                        "timeout": 10000
                    }
                ]
            }
        ],
        "PreCompress": [
            {
                "matcher": "*",
                "hooks": [
                    {
                        "name": "akto-pre-compress",
                        "type": "command",
                        "command": "$wrapper akto-hooks.py PreCompress",
                        "timeout": 10000
                    }
                ]
            }
        ],
        "Notification": [
            {
                "matcher": "*",
                "hooks": [
                    {
                        "name": "akto-notification",
                        "type": "command",
                        "command": "$wrapper akto-hooks.py Notification",
                        "timeout": 10000
                    }
                ]
            }
        ]
    }
}
EOF
)

    if [ -f "$GEMINI_SETTINGS_FILE" ]; then
        log "Existing settings.json found, backing up..."
        cp "$GEMINI_SETTINGS_FILE" "$GEMINI_SETTINGS_FILE.backup"

        if command -v jq >/dev/null 2>&1; then
            jq --argjson newhooks "$new_hooks_config" '
                .hooks.BeforeModel       = $newhooks.hooks.BeforeModel |
                .hooks.AfterModel        = $newhooks.hooks.AfterModel |
                .hooks.SessionStart      = $newhooks.hooks.SessionStart |
                .hooks.SessionEnd        = $newhooks.hooks.SessionEnd |
                .hooks.BeforeAgent       = $newhooks.hooks.BeforeAgent |
                .hooks.AfterAgent        = $newhooks.hooks.AfterAgent |
                .hooks.BeforeToolSelection = $newhooks.hooks.BeforeToolSelection |
                .hooks.BeforeTool        = $newhooks.hooks.BeforeTool |
                .hooks.AfterTool         = $newhooks.hooks.AfterTool |
                .hooks.PreCompress       = $newhooks.hooks.PreCompress |
                .hooks.Notification      = $newhooks.hooks.Notification
            ' "$GEMINI_SETTINGS_FILE" > "$GEMINI_SETTINGS_FILE.tmp"
            mv "$GEMINI_SETTINGS_FILE.tmp" "$GEMINI_SETTINGS_FILE"
            log "Merged hooks into existing settings.json"
        else
            echo "$new_hooks_config" > "$GEMINI_SETTINGS_FILE"
            log "Created new settings.json (no jq available for merge)"
        fi
    else
        echo "$new_hooks_config" > "$GEMINI_SETTINGS_FILE"
        log "Created new settings.json with hooks"
    fi
}

# Main installation function
main() {
    log "Starting Gemini CLI hook installation..."

    # Check if Gemini CLI is installed
    if ! check_gemini_installed; then
        log "Gemini CLI not detected - skipping hook installation"
        return 0
    fi

    log "✓ Gemini CLI detected"

    # Get shared configuration values
    INGESTION_URL=$(get_ingestion_url)
    if [ -z "$INGESTION_URL" ]; then
        log_error "AKTO_API_BASE_URL is not set — cannot install hooks. Set it in config.env or pass it as an environment variable."
        return 1
    fi

    API_TOKEN=$(get_api_token)
    if [ -z "$API_TOKEN" ]; then
        log_error "AKTO_API_TOKEN is not set — cannot install hooks. Set it in config.env or pass it as an environment variable."
        return 1
    fi

    DEVICE_ID=$(generate_device_label)
    if [ -z "$DEVICE_ID" ]; then
        log "⚠ Warning: Could not generate device label"
        DEVICE_ID="unknown-device"
    fi

    log "Device label: $DEVICE_ID"

    # Create hooks directory
    mkdir -p "$GEMINI_HOOKS_DIR"
    log "✓ Created hooks directory: $GEMINI_HOOKS_DIR"

    # -----------------------------------------------------------------------
    # Download shared utility files
    # -----------------------------------------------------------------------
    log "Downloading hook scripts from GitHub..."

    if ! download_file "$GITHUB_SHARED_BASE/akto_ingestion_utility.py" "$GEMINI_HOOKS_DIR/akto_ingestion_utility.py"; then
        log_error "Failed to download akto_ingestion_utility.py"
        return 1
    fi
    chmod +x "$GEMINI_HOOKS_DIR/akto_ingestion_utility.py"
    log "✓ Downloaded akto_ingestion_utility.py"

    if ! download_file "$GITHUB_RAW_BASE/akto_machine_id.py" "$GEMINI_HOOKS_DIR/akto_machine_id.py"; then
        log_error "Failed to download akto_machine_id.py"
        return 1
    fi
    chmod +x "$GEMINI_HOOKS_DIR/akto_machine_id.py"
    log "✓ Downloaded akto_machine_id.py"

    # Download observability hook script
    if ! download_file "$GITHUB_RAW_BASE/akto-hooks.py" "$GEMINI_HOOKS_DIR/akto-hooks.py"; then
        log_error "Failed to download akto-hooks.py"
        return 1
    fi
    chmod +x "$GEMINI_HOOKS_DIR/akto-hooks.py"
    log "✓ Downloaded akto-hooks.py"

    # Create akto-hook-wrapper.sh
    create_hook_wrapper "$INGESTION_URL" "$DEVICE_ID"

    # -----------------------------------------------------------------------
    # PROMPT HOOKS section (BeforeModel / AfterModel)
    # -----------------------------------------------------------------------
    if is_prompt_hooks_enabled; then
        log "ENABLE_PROMPT_HOOKS_GEMINI=true — installing BeforeModel/AfterModel hooks"

        if ! download_file "$GITHUB_RAW_BASE/akto-validate-prompt.py" "$GEMINI_HOOKS_DIR/akto-validate-prompt.py"; then
            log_error "Failed to download akto-validate-prompt.py"
            return 1
        fi
        log "✓ Downloaded akto-validate-prompt.py"

        if ! download_file "$GITHUB_RAW_BASE/akto-validate-response.py" "$GEMINI_HOOKS_DIR/akto-validate-response.py"; then
            log_error "Failed to download akto-validate-response.py"
            return 1
        fi
        log "✓ Downloaded akto-validate-response.py"

        chmod +x "$GEMINI_HOOKS_DIR/akto-validate-prompt.py"
        chmod +x "$GEMINI_HOOKS_DIR/akto-validate-response.py"

        create_prompt_wrapper "prompt" "$INGESTION_URL" "$DEVICE_ID"
        log "✓ Created akto-validate-prompt-wrapper.sh"

        create_prompt_wrapper "response" "$INGESTION_URL" "$DEVICE_ID"
        log "✓ Created akto-validate-response-wrapper.sh"
    else
        log "ENABLE_PROMPT_HOOKS_GEMINI not set — skipping prompt hooks"
    fi

    # Update Gemini settings with all hooks (always runs)
    update_gemini_settings
    log "✓ Updated settings.json with hooks"

    # RTR runs as root — the hooks dir and settings file we just created/modified are root-owned
    # by default, which locks the real user out of their own files. Hand ownership back on
    # exactly what we touched. macOS stat -f, falling back to GNU stat -c.
    REAL_USER="$(stat -f '%Su' "$TARGET_USER_HOME" 2>/dev/null || stat -c '%U' "$TARGET_USER_HOME" 2>/dev/null || echo "")"
    if [ -n "$REAL_USER" ] && [ "$REAL_USER" != "root" ]; then
        chown -R "$REAL_USER" "$GEMINI_HOOKS_DIR" 2>/dev/null || true
        chown "$REAL_USER" "$GEMINI_SETTINGS_FILE" "$GEMINI_SETTINGS_FILE.backup" 2>/dev/null || true
        log "✓ Set ownership to $REAL_USER"
    fi

    log ""
    log "=========================================="
    log "✅ Gemini CLI hooks installed successfully!"
    log "=========================================="
    log ""
    log "Hooks location: $GEMINI_HOOKS_DIR"
    log "Settings: $GEMINI_SETTINGS_FILE"
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

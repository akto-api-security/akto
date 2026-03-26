#!/bin/bash

# ========================================================================================
# MCP Endpoint Shield - Claude CLI Hook Installer
# ========================================================================================
# Automatically installs Akto guardrails hooks for Claude CLI if detected
# Downloads latest hooks from GitHub and configures Claude CLI settings
# ========================================================================================

set -e

GITHUB_RAW_BASE="https://raw.githubusercontent.com/akto-api-security/akto/agent-hooks/apps/mcp-endpoint-shield/claude-cli-hooks"

TARGET_USER_HOME="${TARGET_USER_HOME:-}"
AKTO_GUARDRAILS_URL="${AKTO_GUARDRAILS_URL:-}"
for a in "$@"; do
    case "$a" in
        TARGET_USER_HOME=*) TARGET_USER_HOME="${a#TARGET_USER_HOME=}" ;;
        AKTO_GUARDRAILS_URL=*) AKTO_GUARDRAILS_URL="${a#AKTO_GUARDRAILS_URL=}" ;;
    esac
done

[ -n "$AKTO_GUARDRAILS_URL" ] && export AKTO_GUARDRAILS_URL

log() {
    echo "[Claude CLI Hooks] $1"
}

log_error() {
    echo "[Claude CLI Hooks] ERROR: $1" >&2
}

install_for_user() {
    local user_home="$1"

    export HOME="$user_home"
    TARGET_USER_HOME="$user_home"
    CLAUDE_HOOKS_DIR="$user_home/.claude/hooks"
    CLAUDE_SETTINGS_FILE="$user_home/.claude/settings.json"
    CONFIG_FILE="$user_home/.akto-mcp-endpoint-shield/config/config.env"

    if main; then
        return 0
    else
        log_error "Installation failed for $user_home"
        return 1
    fi
}

check_claude_installed() {
    if [ -d "$TARGET_USER_HOME/.claude" ] || command -v claude >/dev/null 2>&1; then
        return 0
    else
        return 1
    fi
}

get_ingestion_url() {
    if [ -n "${AKTO_GUARDRAILS_URL:-}" ]; then
        echo "$AKTO_GUARDRAILS_URL"
        return 0
    fi

    if [ -f "$CONFIG_FILE" ]; then
        local url
        url=$(grep "^AKTO_GUARDRAILS_URL=" "$CONFIG_FILE" 2>/dev/null | cut -d= -f2-)
        if [ -n "$url" ]; then
            echo "$url"
            return 0
        fi
    fi

    echo "https://guardrails.akto.io"
}

generate_device_id() {
    if command -v ioreg >/dev/null 2>&1; then
        UUID=$(ioreg -rd1 -c IOPlatformExpertDevice 2>/dev/null | grep IOPlatformUUID | awk -F'"' '{print $4}')
        if [ -n "$UUID" ]; then
            echo "$UUID" | tr -d '-' | tr '[:upper:]' '[:lower:]'
            return 0
        fi
    fi

    if command -v ifconfig >/dev/null 2>&1; then
        MAC=$(ifconfig en0 2>/dev/null | grep ether | awk '{print $2}' | tr -d ':')
        if [ -n "$MAC" ]; then
            echo "$MAC" | tr '[:upper:]' '[:lower:]'
            return 0
        fi
    fi

    if [ -f /etc/machine-id ]; then
        cat /etc/machine-id
        return 0
    elif [ -f /var/lib/dbus/machine-id ]; then
        cat /var/lib/dbus/machine-id
        return 0
    fi

    echo "unknown-device-$(date +%s)"
}

download_file() {
    local url="$1"
    local dest="$2"

    log "Downloading $(basename "$dest")..."
    
    if command -v curl >/dev/null 2>&1; then
        curl -fsSL "$url" -o "$dest"
    elif command -v wget >/dev/null 2>&1; then
        wget -q -O "$dest" "$url"
    else
        log_error "Neither curl nor wget found. Cannot download hooks."
        return 1
    fi

    chmod +x "$dest" 2>/dev/null || true
}

create_wrapper() {
    local hook_type="$1"
    local ingestion_url="$2"
    local device_id="$3"

    local wrapper_file="$CLAUDE_HOOKS_DIR/akto-validate-${hook_type}-wrapper.sh"
    local template_url="$GITHUB_RAW_BASE/akto-validate-${hook_type}-wrapper.sh"
    local python_script="$CLAUDE_HOOKS_DIR/akto-validate-${hook_type}.py"

    if ! download_file "$template_url" "$wrapper_file.tmp"; then
        log_error "Failed to download wrapper template for $hook_type, creating locally..."
        log "Creating wrapper with URL: $ingestion_url"

        cat > "$wrapper_file" <<EOF
#!/bin/bash
# Auto-generated wrapper for Akto guardrails hook
# Data Ingestion URL: $ingestion_url

export MODE="atlas"
export AKTO_DATA_INGESTION_URL="$ingestion_url"
export AKTO_SYNC_MODE="true"
export AKTO_TIMEOUT="5"
export AKTO_CONNECTOR="claude_code_cli"
export CONTEXT_SOURCE="ENDPOINT"
export DEVICE_ID="$device_id"

# Log configuration for debugging
echo "[Claude Hook] Data Ingestion URL: \$AKTO_DATA_INGESTION_URL" >&2
echo "[Claude Hook] Device ID: \$DEVICE_ID" >&2

exec python3 "$python_script" "\$@"
EOF
        chmod +x "$wrapper_file"
        return 0
    fi

    sed -e "s|{{AKTO_DATA_INGESTION_URL}}|$ingestion_url|g" \
        -e "s|{{DEVICE_ID (optional)}}|$device_id|g" \
        "$wrapper_file.tmp" > "$wrapper_file"
        "$wrapper_file.tmp" > "$wrapper_file"

    rm -f "$wrapper_file.tmp"
    chmod +x "$wrapper_file"
    
    log "Wrapper configured with URL: $ingestion_url"
}

update_claude_settings() {
    local prompt_wrapper="$CLAUDE_HOOKS_DIR/akto-validate-prompt-wrapper.sh"
    local response_wrapper="$CLAUDE_HOOKS_DIR/akto-validate-response-wrapper.sh"

    local new_hooks_config
    new_hooks_config=$(cat <<EOF
{
    "hooks": {
        "UserPromptSubmit": [
            {
                "hooks": [
                    {
                        "type": "command",
                        "command": "$prompt_wrapper",
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
                        "command": "$response_wrapper",
                        "timeout": 10
                    }
                ]
            }
        ]
    }
}
EOF
)

    if [ -f "$CLAUDE_SETTINGS_FILE" ]; then
        log "Existing settings.json found, backing up..."
        cp "$CLAUDE_SETTINGS_FILE" "$CLAUDE_SETTINGS_FILE.backup"

        if command -v jq >/dev/null 2>&1; then
            jq --argjson newhooks "$new_hooks_config" '.hooks = $newhooks.hooks' "$CLAUDE_SETTINGS_FILE" > "$CLAUDE_SETTINGS_FILE.tmp"
            mv "$CLAUDE_SETTINGS_FILE.tmp" "$CLAUDE_SETTINGS_FILE"
            log "Merged hooks into existing settings.json"
        else
            echo "$new_hooks_config" > "$CLAUDE_SETTINGS_FILE"
            log "Created new settings.json (no jq available for merge)"
        fi
    else
        echo "$new_hooks_config" > "$CLAUDE_SETTINGS_FILE"
        log "Created new settings.json with hooks"
    fi
}

main() {
    log "Starting Claude CLI hook installation..."
    log "Target user home: $TARGET_USER_HOME"

    if ! check_claude_installed; then
        log "Claude CLI not detected - skipping hook installation"
        return 0
    fi

    log "✓ Claude CLI detected"

    INGESTION_URL=$(get_ingestion_url)
    if [ -z "$INGESTION_URL" ]; then
        log "⚠ Warning: AKTO_GUARDRAILS_URL not configured"
        INGESTION_URL="https://guardrails.akto.io"
    fi

    DEVICE_ID=$(generate_device_id)
    if [ -z "$DEVICE_ID" ]; then
        log "⚠ Warning: Could not generate device ID"
        DEVICE_ID="unknown-device"
    fi

    log "Device ID: $DEVICE_ID"
    log "Guardrails URL: $INGESTION_URL"

    mkdir -p "$CLAUDE_HOOKS_DIR"
    log "✓ Created hooks directory: $CLAUDE_HOOKS_DIR"

    REAL_USER="$(stat -f '%Su' "$TARGET_USER_HOME" 2>/dev/null || stat -c '%U' "$TARGET_USER_HOME" 2>/dev/null || echo "")"
    if [ -n "$REAL_USER" ] && [ "$REAL_USER" != "root" ]; then
        chown -R "$REAL_USER" "$TARGET_USER_HOME/.claude" 2>/dev/null || true
        log "✓ Set ownership to $REAL_USER"
    fi

    log "Downloading hook scripts from GitHub..."

    if ! download_file "$GITHUB_RAW_BASE/akto-validate-prompt.py" "$CLAUDE_HOOKS_DIR/akto-validate-prompt.py"; then
        log_error "Failed to download akto-validate-prompt.py"
        return 1
    fi
    log "✓ Downloaded akto-validate-prompt.py"

    if ! download_file "$GITHUB_RAW_BASE/akto-validate-response.py" "$CLAUDE_HOOKS_DIR/akto-validate-response.py"; then
        log_error "Failed to download akto-validate-response.py"
        return 1
    fi
    log "✓ Downloaded akto-validate-response.py"

    if ! download_file "$GITHUB_RAW_BASE/akto_machine_id.py" "$CLAUDE_HOOKS_DIR/akto_machine_id.py"; then
        log_error "Failed to download akto_machine_id.py"
        return 1
    fi
    log "✓ Downloaded akto_machine_id.py"

    chmod +x "$CLAUDE_HOOKS_DIR/akto-validate-prompt.py"
    chmod +x "$CLAUDE_HOOKS_DIR/akto-validate-response.py"
    chmod +x "$CLAUDE_HOOKS_DIR/akto_machine_id.py"

    log "Creating wrapper scripts with environment variables..."
    create_wrapper "prompt" "$INGESTION_URL" "$DEVICE_ID"
    log "✓ Created akto-validate-prompt-wrapper.sh"

    create_wrapper "response" "$INGESTION_URL" "$DEVICE_ID"
    log "✓ Created akto-validate-response-wrapper.sh"

    log "Updating Claude CLI settings..."
    update_claude_settings
    log "✓ Updated settings.json"

    if [ -n "$REAL_USER" ] && [ "$REAL_USER" != "root" ]; then
        chown -R "$REAL_USER" "$TARGET_USER_HOME/.claude" 2>/dev/null || true
    fi

    log ""
    log "=========================================="
    log "✅ Claude CLI hooks installed successfully!"
    log "=========================================="
    log ""
    log "Hooks location: $CLAUDE_HOOKS_DIR"
    log "Settings: $CLAUDE_SETTINGS_FILE"
    log "Guardrails URL: $INGESTION_URL"

    return 0
}

if [ -n "$TARGET_USER_HOME" ]; then
    install_for_user "$TARGET_USER_HOME"
else
    log "Scanning for user home directories..."
    
    if [ -d "/Users" ]; then
        for user_dir in /Users/*; do
            [ -d "$user_dir" ] || continue
            [ "$(basename "$user_dir")" = "Shared" ] && continue
            
            install_for_user "$user_dir" || true
        done
    elif [ -d "/home" ]; then
        for user_dir in /home/*; do
            [ -d "$user_dir" ] || continue
            
            install_for_user "$user_dir" || true
        done
    fi
    
    log "Scan complete"
fi

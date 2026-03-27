#!/bin/bash

# ========================================================================================
# MCP Endpoint Shield - Cursor IDE Hook Installer
# ========================================================================================
# Automatically installs Akto guardrails hooks for Cursor IDE if detected
# Downloads latest hooks from GitHub and configures Cursor hooks.json
# ========================================================================================

set -e

GITHUB_RAW_BASE="https://raw.githubusercontent.com/akto-api-security/akto/agent-hooks/apps/mcp-endpoint-shield/cursor-hooks"

TARGET_USER_HOME="${TARGET_USER_HOME:-}"
AKTO_DATA_INGESTION_URL="${AKTO_DATA_INGESTION_URL:-}"
for a in "$@"; do
    case "$a" in
        TARGET_USER_HOME=*) TARGET_USER_HOME="${a#TARGET_USER_HOME=}" ;;
        AKTO_DATA_INGESTION_URL=*) AKTO_DATA_INGESTION_URL="${a#AKTO_DATA_INGESTION_URL=}" ;;
    esac
done

[ -n "$AKTO_DATA_INGESTION_URL" ] && export AKTO_DATA_INGESTION_URL

log() {
    echo "[Cursor Hooks] $1"
}

log_error() {
    echo "[Cursor Hooks] ERROR: $1" >&2
}

install_for_user() {
    local user_home="$1"

    export HOME="$user_home"
    TARGET_USER_HOME="$user_home"
    CURSOR_HOOKS_DIR="$user_home/.cursor/hooks/akto"
    CURSOR_HOOKS_FILE="$user_home/.cursor/hooks.json"
    CONFIG_FILE="$user_home/.akto-mcp-endpoint-shield/config/config.env"
    CURSOR_CONNECTOR="cursor"

    if main; then
        return 0
    else
        log_error "Installation failed for $user_home"
        return 1
    fi
}

check_cursor_installed() {
    if [ -d "$TARGET_USER_HOME/.cursor" ] || [ -d "/Applications/Cursor.app" ]; then
        return 0
    else
        return 1
    fi
}

get_ingestion_url() {
    if [ -n "${AKTO_DATA_INGESTION_URL:-}" ]; then
        echo "$AKTO_DATA_INGESTION_URL"
        return 0
    fi

    if [ -f "$CONFIG_FILE" ]; then
        local url
        url=$(grep "^AKTO_DATA_INGESTION_URL=" "$CONFIG_FILE" 2>/dev/null | cut -d= -f2-)
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

    echo ""
}

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

create_wrapper() {
    local hook_type="$1"
    local ingestion_url="$2"
    local device_id="$3"

    local wrapper_file="$CURSOR_HOOKS_DIR/akto-validate-chat-${hook_type}-wrapper.sh"
    local template_url="$GITHUB_RAW_BASE/akto-validate-chat-${hook_type}-wrapper.sh"
    local python_script="$CURSOR_HOOKS_DIR/akto-validate-chat-${hook_type}.py"

    if ! download_file "$template_url" "$wrapper_file.tmp"; then
        log_error "Failed to download wrapper template for $hook_type, creating locally..."
        log "Creating wrapper with URL: $ingestion_url"

        cat > "$wrapper_file" <<EOF
#!/bin/bash
# Auto-generated wrapper for Akto guardrails hook
# Guardrails URL: $ingestion_url

export MODE="atlas"
export AKTO_DATA_INGESTION_URL="$ingestion_url"
export AKTO_SYNC_MODE="true"
export AKTO_TIMEOUT="5"
export AKTO_CONNECTOR="$CURSOR_CONNECTOR"
export CONTEXT_SOURCE="ENDPOINT"
export DEVICE_ID="$device_id"

# Log configuration for debugging
echo "[Cursor Hook] Data Ingestion URL: \$AKTO_DATA_INGESTION_URL" >&2
echo "[Cursor Hook] Device ID: \$DEVICE_ID" >&2

exec python3 "$python_script" "\$@"
EOF
        chmod +x "$wrapper_file"
        return 0
    fi

    # Replace placeholder
    sed -e "s|{{AKTO_DATA_INGESTION_URL}}|$ingestion_url|g" \
        -e "s|{{DEVICE_ID (optional)}}|$device_id|g" \
        "$wrapper_file.tmp" > "$wrapper_file"

    rm -f "$wrapper_file.tmp"
    chmod +x "$wrapper_file"
    
    log "Wrapper configured with URL: $ingestion_url"
}

update_cursor_hooks() {
    local chat_prompt_wrapper="$TARGET_USER_HOME/.cursor/hooks/akto/akto-validate-chat-prompt-wrapper.sh"
    local chat_response_wrapper="$TARGET_USER_HOME/.cursor/hooks/akto/akto-validate-chat-response-wrapper.sh"

    local new_hooks_config
    new_hooks_config=$(cat <<EOF
{
    "version": 1,
    "hooks": {
        "beforeSubmitPrompt": [
            {
                "command": "$chat_prompt_wrapper",
                "type": "command",
                "timeout": 10
            }
        ],
        "afterAgentResponse": [
            {
                "command": "$chat_response_wrapper",
                "type": "command",
                "timeout": 10
            }
        ]
    }
}
EOF
)

    if [ -f "$CURSOR_HOOKS_FILE" ]; then
        log "Existing hooks.json found, backing up..."
        cp "$CURSOR_HOOKS_FILE" "$CURSOR_HOOKS_FILE.backup"

        if command -v jq >/dev/null 2>&1; then
            local existing_config
            existing_config=$(cat "$CURSOR_HOOKS_FILE")

            local merged_config
            merged_config=$(echo "$existing_config" | jq --argjson newhooks "$new_hooks_config" '
                .version = $newhooks.version |
                .hooks.beforeSubmitPrompt = ((.hooks.beforeSubmitPrompt // []) + $newhooks.hooks.beforeSubmitPrompt) |
                .hooks.afterAgentResponse = ((.hooks.afterAgentResponse // []) + $newhooks.hooks.afterAgentResponse)
            ')

            echo "$merged_config" > "$CURSOR_HOOKS_FILE"
            log "Merged hooks into existing hooks.json"
        else
            echo "$new_hooks_config" > "$CURSOR_HOOKS_FILE"
            log "Created new hooks.json (no jq available for merge)"
        fi
    else
        echo "$new_hooks_config" > "$CURSOR_HOOKS_FILE"
        log "Created new hooks.json"
    fi
}

main() {
    log "Starting Cursor IDE hook installation..."
    log "Target user home: $TARGET_USER_HOME"

    if ! check_cursor_installed; then
        log "Cursor IDE not detected - skipping hook installation"
        return 0
    fi

    log "Cursor IDE detected"

    INGESTION_URL=$(get_ingestion_url)
    if [ -z "$INGESTION_URL" ]; then
        log "Warning: AKTO_DATA_INGESTION_URL not configured"
        INGESTION_URL="https://your-ingestion-url.akto.io"
    fi

    DEVICE_ID=$(generate_device_id)
    if [ -z "$DEVICE_ID" ]; then
        log "Warning: Could not generate device ID"
        DEVICE_ID="unknown-device"
    fi

    log "Device ID: $DEVICE_ID"

    mkdir -p "$CURSOR_HOOKS_DIR"
    log "Created hooks directory: $CURSOR_HOOKS_DIR"

    REAL_USER="$(stat -f '%Su' "$TARGET_USER_HOME" 2>/dev/null || echo "")"
    if [ -n "$REAL_USER" ] && [ "$REAL_USER" != "root" ]; then
        chown -R "$REAL_USER" "$TARGET_USER_HOME/.cursor" 2>/dev/null || true
        log "Set ownership to $REAL_USER"
    fi

    log "Downloading hook scripts from GitHub..."

    if ! download_file "$GITHUB_RAW_BASE/akto-validate-chat-prompt.py" "$CURSOR_HOOKS_DIR/akto-validate-chat-prompt.py"; then
        log_error "Failed to download akto-validate-chat-prompt.py"
        return 1
    fi
    log "Downloaded akto-validate-chat-prompt.py"

    if ! download_file "$GITHUB_RAW_BASE/akto-validate-chat-response.py" "$CURSOR_HOOKS_DIR/akto-validate-chat-response.py"; then
        log_error "Failed to download akto-validate-chat-response.py"
        return 1
    fi
    log "Downloaded akto-validate-chat-response.py"

    if ! download_file "$GITHUB_RAW_BASE/akto_machine_id.py" "$CURSOR_HOOKS_DIR/akto_machine_id.py"; then
        log_error "Failed to download akto_machine_id.py"
        return 1
    fi
    log "Downloaded akto_machine_id.py"

    chmod +x "$CURSOR_HOOKS_DIR/akto-validate-chat-prompt.py"
    chmod +x "$CURSOR_HOOKS_DIR/akto-validate-chat-response.py"
    chmod +x "$CURSOR_HOOKS_DIR/akto_machine_id.py"

    log "Creating wrapper scripts with environment variables..."

    create_wrapper "prompt" "$INGESTION_URL" "$DEVICE_ID"
    log "Created akto-validate-chat-prompt-wrapper.sh"

    create_wrapper "response" "$INGESTION_URL" "$DEVICE_ID"
    log "Created akto-validate-chat-response-wrapper.sh"

    log "Updating Cursor IDE hooks.json..."
    update_cursor_hooks
    log "Updated hooks.json"

    if [ -n "$REAL_USER" ] && [ "$REAL_USER" != "root" ]; then
        chown -R "$REAL_USER" "$TARGET_USER_HOME/.cursor" 2>/dev/null || true
    fi

    log ""
    log "=========================================="
    log "Cursor IDE hooks installed successfully!"
    log "=========================================="
    log ""
    log "Hooks location: $CURSOR_HOOKS_DIR"
    log "Configuration: $CURSOR_HOOKS_FILE"

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
    for u in /Users/*/; do
        u="${u%/}"
        base="$(basename "$u")"
        [ "$base" = "Shared" ] && continue
        [ "$base" = "Guest" ] && continue
        [[ "$base" == .* ]] && continue
        [ -d "$u" ] || continue

        log "=== Processing user: $u ==="
        install_for_user "$u" || EXIT_CODE=1
    done
    exit $EXIT_CODE
fi

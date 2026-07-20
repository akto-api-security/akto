#!/bin/bash

# ========================================================================================
# MCP Endpoint Shield - Cursor IDE Hook Installer
# ========================================================================================
# Automatically installs Akto guardrails hooks for Cursor IDE if detected
# Downloads latest hooks from GitHub and configures Cursor hooks.json
# Compatible with macOS and Linux
# Mirrors: mcp-endpoint-shield/misc/macos/install_cursor_hooks.sh (master branch, full hook set)
# ========================================================================================

# Ensure common binary paths are available (RTR/remote execution uses minimal PATH)
export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$PATH"

set -e

GITHUB_RAW_BASE="https://raw.githubusercontent.com/akto-api-security/akto/master/apps/mcp-endpoint-shield/cursor-hooks"
GITHUB_SHARED_BASE="https://raw.githubusercontent.com/akto-api-security/akto/master/apps/mcp-endpoint-shield/shared"

# Detect OS
OS_TYPE="$(uname -s)"
IS_MACOS=false
IS_LINUX=false

case "$OS_TYPE" in
    Darwin) IS_MACOS=true ;;
    Linux) IS_LINUX=true ;;
esac

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
    CURSOR_CONNECTOR="cursor"

    if main; then
        return 0
    else
        log_error "Installation failed for $user_home"
        return 1
    fi
}

check_cursor_installed() {
    # Check Cursor config directory (works on both macOS and Linux)
    if [ -d "$TARGET_USER_HOME/.cursor" ]; then
        return 0
    fi

    # macOS app bundle check
    if [ "$IS_MACOS" = true ] && [ -d "/Applications/Cursor.app" ]; then
        return 0
    fi

    # Linux package installations
    if [ "$IS_LINUX" = true ]; then
        # ~/.config/Cursor/ is created on first run — most reliable Linux indicator
        if [ -d "$TARGET_USER_HOME/.config/Cursor" ]; then
            return 0
        fi
        # .deb package paths (note: capital C in some packages)
        if [ -d "/opt/cursor" ] || [ -d "/opt/Cursor" ] || \
           [ -d "/usr/share/cursor" ] || [ -d "/usr/share/Cursor" ] || \
           [ -f "/usr/bin/cursor" ] || \
           [ -f "$TARGET_USER_HOME/.local/bin/cursor" ] || \
           [ -d "$TARGET_USER_HOME/.local/share/cursor" ]; then
            return 0
        fi
        # Snap installations
        if [ -d "/snap/cursor" ]; then
            return 0
        fi
        # command -v may fail under sudo (different PATH), try it last
        if command -v cursor >/dev/null 2>&1; then
            return 0
        fi
    fi

    return 1
}

get_ingestion_url() {
    if [ -n "${AKTO_DATA_INGESTION_URL:-}" ]; then
        echo "$AKTO_DATA_INGESTION_URL"
        return 0
    fi
    echo "https://guardrails.akto.io"
}

get_api_token() {
    echo "${AKTO_API_TOKEN:-}"
}

generate_device_id() {
    # macOS: Try ioreg first
    if [ "$IS_MACOS" = true ] && command -v ioreg >/dev/null 2>&1; then
        UUID=$(ioreg -rd1 -c IOPlatformExpertDevice 2>/dev/null | grep IOPlatformUUID | awk -F'"' '{print $4}')
        if [ -n "$UUID" ]; then
            echo "$UUID" | tr -d '-' | tr '[:upper:]' '[:lower:]'
            return 0
        fi
    fi

    # Linux: Try machine-id
    if [ "$IS_LINUX" = true ] && [ -f "/etc/machine-id" ]; then
        cat "/etc/machine-id" | tr '[:upper:]' '[:lower:]'
        return 0
    fi

    # Fallback: Get MAC address (macOS: en0, Linux: eth0 or ens0)
    if command -v ifconfig >/dev/null 2>&1; then
        if [ "$IS_MACOS" = true ]; then
            MAC=$(ifconfig en0 2>/dev/null | grep ether | awk '{print $2}' | tr -d ':')
        else
            MAC=$(ifconfig eth0 2>/dev/null | awk '/ether/{print $2}' | tr -d ':')
            [ -z "$MAC" ] && MAC=$(ifconfig ens0 2>/dev/null | awk '/ether/{print $2}' | tr -d ':')
        fi
        if [ -n "$MAC" ]; then
            echo "$MAC" | tr '[:upper:]' '[:lower:]'
            return 0
        fi
    fi

    # Last resort: ip command (Linux only) — no grep -oP, use awk instead
    if [ "$IS_LINUX" = true ] && command -v ip >/dev/null 2>&1; then
        MAC=$(ip link show | awk '/link\/ether/{print $2}' | head -1 | tr -d ':')
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
        curl -fsSL -H "Cache-Control: no-cache" -H "Pragma: no-cache" "$url" -o "$dest"
        return $?
    elif command -v wget >/dev/null 2>&1; then
        wget -q --no-cache "$url" -O "$dest"
        return $?
    else
        log_error "Neither curl nor wget available"
        return 1
    fi
}

# Download and configure wrapper script for prompt chat hook
create_prompt_wrapper() {
    local hook_type="$1"  # "prompt" or "response"
    local ingestion_url="$2"
    local device_id="$3"

    local wrapper_file="$CURSOR_HOOKS_DIR/akto-validate-chat-${hook_type}-wrapper.sh"
    local template_url="$GITHUB_RAW_BASE/akto-validate-chat-${hook_type}-wrapper.sh"
    local python_script="$CURSOR_HOOKS_DIR/akto-validate-chat-${hook_type}.py"

    if ! download_file "$template_url" "$wrapper_file.tmp"; then
        log_error "Failed to download wrapper template for $hook_type, creating locally..."

        cat > "$wrapper_file" <<EOF
#!/bin/bash
# Auto-generated wrapper for Akto guardrails hook

export MODE="atlas"
export AKTO_DATA_INGESTION_URL="$ingestion_url"
export AKTO_API_TOKEN="$API_TOKEN"
export AKTO_SYNC_MODE="true"
export AKTO_TIMEOUT="5"
export AKTO_CONNECTOR="$CURSOR_CONNECTOR"
export CONTEXT_SOURCE="ENDPOINT"
export DEVICE_ID="$device_id"

echo "[Cursor Hook] Data Ingestion URL: \$AKTO_DATA_INGESTION_URL" >&2
echo "[Cursor Hook] Device ID: \$DEVICE_ID" >&2

exec python3 "$python_script" "\$@"
EOF
        chmod +x "$wrapper_file"
        return 0
    fi

    sed -e "s|{{AKTO_DATA_INGESTION_URL}}|$ingestion_url|g" \
        -e "s|{{AKTO_API_TOKEN}}|$API_TOKEN|g" \
        -e "s|{{DEVICE_ID (optional)}}|$device_id|g" \
        "$wrapper_file.tmp" > "$wrapper_file"

    rm -f "$wrapper_file.tmp"
    chmod +x "$wrapper_file"

    log "Wrapper configured with URL: $ingestion_url"
}

# Download and configure wrapper script for MCP hook
create_mcp_wrapper() {
    local hook_type="$1"  # "mcp-request" or "mcp-response"
    local ingestion_url="$2"
    local device_id="$3"

    local wrapper_file="$CURSOR_HOOKS_DIR/akto-validate-${hook_type}-wrapper.sh"
    local template_url="$GITHUB_RAW_BASE/akto-validate-${hook_type}-wrapper.sh"

    if ! download_file "$template_url" "$wrapper_file.tmp"; then
        log_error "Failed to download MCP wrapper template for $hook_type"
        return 1
    fi

    sed -e "s|{{AKTO_DATA_INGESTION_URL}}|$ingestion_url|g" \
        -e "s|{{AKTO_API_TOKEN}}|$API_TOKEN|g" \
        -e "s|{{DEVICE_ID (optional)}}|$device_id|g" \
        "$wrapper_file.tmp" > "$wrapper_file"

    rm -f "$wrapper_file.tmp"
    chmod +x "$wrapper_file"
}

# Download akto-hook-wrapper.sh and substitute placeholders (drives the general observability hooks)
create_hook_wrapper() {
    local ingestion_url="$1"
    local device_id="$2"
    local wrapper_file="$CURSOR_HOOKS_DIR/akto-hook-wrapper.sh"

    if ! download_file "$GITHUB_RAW_BASE/akto-hook-wrapper.sh" "$wrapper_file.tmp"; then
        log_error "Failed to download akto-hook-wrapper.sh, creating locally"
        cat > "$wrapper_file" <<EOF
#!/bin/bash
# Auto-generated wrapper for Akto observability hooks

export MODE="atlas"
export AKTO_DATA_INGESTION_URL="$ingestion_url"
export AKTO_API_TOKEN="$API_TOKEN"
export AKTO_SYNC_MODE="true"
export AKTO_TIMEOUT="5"
export AKTO_CONNECTOR="$CURSOR_CONNECTOR"
export CONTEXT_SOURCE="ENDPOINT"
export DEVICE_ID="$device_id"

SCRIPT_DIR="$CURSOR_HOOKS_DIR"
exec python3 "\$SCRIPT_DIR/\$1" "\${@:2}"
EOF
        chmod +x "$wrapper_file"
        log "Created akto-hook-wrapper.sh"
        return 0
    fi

    sed -e "s|{{AKTO_DATA_INGESTION_URL}}|$ingestion_url|g" \
        -e "s|{{AKTO_API_TOKEN}}|$API_TOKEN|g" \
        -e "s|{{DEVICE_ID (optional)}}|$device_id|g" \
        "$wrapper_file.tmp" > "$wrapper_file"

    rm -f "$wrapper_file.tmp"
    chmod +x "$wrapper_file"
    log "Created akto-hook-wrapper.sh"
}

install_mcp_hooks() {
    local ingestion_url="$1"
    local device_id="$2"

    log "Installing MCP hooks (beforeMCPExecution/afterMCPExecution)..."

    for script in akto-validate-mcp-request.py akto-validate-mcp-response.py; do
        if ! download_file "$GITHUB_RAW_BASE/$script" "$CURSOR_HOOKS_DIR/$script"; then
            log_error "Failed to download $script"
            return 1
        fi
        chmod +x "$CURSOR_HOOKS_DIR/$script"
        log "Downloaded $script"
    done

    create_mcp_wrapper "mcp-request" "$ingestion_url" "$device_id"
    log "Created akto-validate-mcp-request-wrapper.sh"

    create_mcp_wrapper "mcp-response" "$ingestion_url" "$device_id"
    log "Created akto-validate-mcp-response-wrapper.sh"
}

# Update or create Cursor hooks.json with the full hook set
update_cursor_hooks() {
    local hooks_dir="$CURSOR_HOOKS_DIR"
    local wrapper="bash $hooks_dir/akto-hook-wrapper.sh"

    local new_hooks_config
    new_hooks_config=$(cat <<EOF
{
  "version": 1,
  "hooks": {
    "beforeSubmitPrompt": [
      { "command": "bash $hooks_dir/akto-validate-chat-prompt-wrapper.sh", "type": "command", "timeout": 10 }
    ],
    "afterAgentResponse": [
      { "command": "bash $hooks_dir/akto-validate-chat-response-wrapper.sh", "type": "command", "timeout": 10 }
    ],
    "beforeMCPExecution": [
      { "command": "bash $hooks_dir/akto-validate-mcp-request-wrapper.sh", "type": "command", "timeout": 10 }
    ],
    "afterMCPExecution": [
      { "command": "bash $hooks_dir/akto-validate-mcp-response-wrapper.sh", "type": "command", "timeout": 10 }
    ],
    "sessionStart": [
      { "command": "$wrapper akto-hooks.py sessionStart", "type": "command", "timeout": 10 }
    ],
    "sessionEnd": [
      { "command": "$wrapper akto-hooks.py sessionEnd", "type": "command", "timeout": 10 }
    ],
    "preToolUse": [
      { "command": "$wrapper akto-hooks.py preToolUse", "type": "command", "timeout": 10 }
    ],
    "postToolUse": [
      { "command": "$wrapper akto-hooks.py postToolUse", "type": "command", "timeout": 10 }
    ],
    "postToolUseFailure": [
      { "command": "$wrapper akto-hooks.py postToolUseFailure", "type": "command", "timeout": 10 }
    ],
    "subagentStart": [
      { "command": "$wrapper akto-hooks.py subagentStart", "type": "command", "timeout": 10 }
    ],
    "subagentStop": [
      { "command": "$wrapper akto-hooks.py subagentStop", "type": "command", "timeout": 10 }
    ],
    "beforeShellExecution": [
      { "command": "$wrapper akto-hooks.py beforeShellExecution", "type": "command", "timeout": 10 }
    ],
    "afterShellExecution": [
      { "command": "$wrapper akto-hooks.py afterShellExecution", "type": "command", "timeout": 10 }
    ],
    "beforeReadFile": [
      { "command": "$wrapper akto-hooks.py beforeReadFile", "type": "command", "timeout": 10 }
    ],
    "afterFileEdit": [
      { "command": "$wrapper akto-hooks.py afterFileEdit", "type": "command", "timeout": 10 }
    ],
    "afterAgentThought": [
      { "command": "$wrapper akto-hooks.py afterAgentThought", "type": "command", "timeout": 10 }
    ],
    "stop": [
      { "command": "$wrapper akto-hooks.py stop", "type": "command", "timeout": 10 }
    ],
    "preCompact": [
      { "command": "$wrapper akto-hooks.py preCompact", "type": "command", "timeout": 10 }
    ],
    "beforeTabFileRead": [
      { "command": "$wrapper akto-hooks.py beforeTabFileRead", "type": "command", "timeout": 10 }
    ],
    "afterTabFileEdit": [
      { "command": "$wrapper akto-hooks.py afterTabFileEdit", "type": "command", "timeout": 10 }
    ]
  }
}
EOF
)

    if [ -f "$CURSOR_HOOKS_FILE" ]; then
        log "Existing hooks.json found, backing up..."
        cp "$CURSOR_HOOKS_FILE" "$CURSOR_HOOKS_FILE.backup"

        if command -v jq >/dev/null 2>&1; then
            jq --argjson newhooks "$new_hooks_config" '.version = $newhooks.version | .hooks = $newhooks.hooks' "$CURSOR_HOOKS_FILE" > "$CURSOR_HOOKS_FILE.tmp"
            mv "$CURSOR_HOOKS_FILE.tmp" "$CURSOR_HOOKS_FILE"
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
        log "Warning: ingestion URL not configured"
        INGESTION_URL="https://your-ingestion-url.akto.io"
    fi

    API_TOKEN=$(get_api_token)

    DEVICE_ID=$(generate_device_id)
    if [ -z "$DEVICE_ID" ]; then
        log "Warning: Could not generate device ID"
        DEVICE_ID="unknown-device"
    fi

    log "Device ID: $DEVICE_ID"

    mkdir -p "$CURSOR_HOOKS_DIR"
    log "Created hooks directory: $CURSOR_HOOKS_DIR"

    # Get real user (works on both macOS and Linux)
    REAL_USER=""
    if [ "$IS_MACOS" = true ]; then
        REAL_USER="$(stat -f '%Su' "$TARGET_USER_HOME" 2>/dev/null || echo "")"
    else
        REAL_USER="$(stat -c '%U' "$TARGET_USER_HOME" 2>/dev/null || echo "")"
    fi

    if [ -n "$REAL_USER" ] && [ "$REAL_USER" != "root" ]; then
        chown -R "$REAL_USER" "$TARGET_USER_HOME/.cursor" 2>/dev/null || true
        log "Set ownership to $REAL_USER"
    fi

    log "Downloading hook scripts from GitHub..."

    if ! download_file "$GITHUB_SHARED_BASE/akto_ingestion_utility.py" "$CURSOR_HOOKS_DIR/akto_ingestion_utility.py"; then
        log_error "Failed to download akto_ingestion_utility.py"
        return 1
    fi
    chmod +x "$CURSOR_HOOKS_DIR/akto_ingestion_utility.py"
    log "Downloaded akto_ingestion_utility.py"

    if ! download_file "$GITHUB_RAW_BASE/akto_machine_id.py" "$CURSOR_HOOKS_DIR/akto_machine_id.py"; then
        log_error "Failed to download akto_machine_id.py"
        return 1
    fi
    chmod +x "$CURSOR_HOOKS_DIR/akto_machine_id.py"
    log "Downloaded akto_machine_id.py"

    HOOK_PY_FILES=(
        "akto-validate-chat-prompt.py"
        "akto-validate-chat-response.py"
        "akto-validate-mcp-request.py"
        "akto-validate-mcp-response.py"
        "akto-hooks.py"
    )

    for f in "${HOOK_PY_FILES[@]}"; do
        if ! download_file "$GITHUB_RAW_BASE/$f" "$CURSOR_HOOKS_DIR/$f"; then
            log_error "Failed to download $f"
            return 1
        fi
        chmod +x "$CURSOR_HOOKS_DIR/$f"
        log "Downloaded $f"
    done

    create_hook_wrapper "$INGESTION_URL" "$DEVICE_ID"

    log "Installing prompt hooks (beforeSubmitPrompt/afterAgentResponse)..."
    create_prompt_wrapper "prompt" "$INGESTION_URL" "$DEVICE_ID"
    log "Created akto-validate-chat-prompt-wrapper.sh"
    create_prompt_wrapper "response" "$INGESTION_URL" "$DEVICE_ID"
    log "Created akto-validate-chat-response-wrapper.sh"

    install_mcp_hooks "$INGESTION_URL" "$DEVICE_ID"
    log "MCP hooks installed"

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

    # Glob directly in the for-clause (not through an unquoted variable) so this expands
    # correctly under both bash and zsh — CrowdStrike RTR executes -CloudFile scripts under
    # zsh regardless of the #!/bin/bash shebang, and zsh does not glob unquoted variable
    # expansion by default (no GLOB_SUBST), so `for u in $USER_DIRS` would silently iterate
    # once over the literal pattern string instead of real directories.
    if [ "$IS_MACOS" = true ]; then
        GLOB_BASE="/Users"
    else
        GLOB_BASE="/home"
    fi

    for u in "$GLOB_BASE"/*; do
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

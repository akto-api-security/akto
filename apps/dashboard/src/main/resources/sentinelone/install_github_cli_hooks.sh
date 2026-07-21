#!/bin/bash

# ========================================================================================
# Akto Endpoint Shield - GitHub Copilot CLI Hook Installer
# ========================================================================================
# Automatically installs Akto guardrails hooks for GitHub Copilot CLI if detected.
# Detection: gh copilot extension installed, OR ~/.copilot/ directory exists.
# Config file: ~/.copilot/config.json
# Hook scripts: ~/.github/hooks/
#
# Controlled by flags in config.env (Jamf/enterprise only — all default off):
#   ENABLE_PROMPT_HOOKS_GITHUB_CLI=true  — installs userPromptSubmitted hook
#   ENABLE_MCP_HOOKS_GITHUB_CLI=true     — installs preToolUse/postToolUse MCP hooks
# ========================================================================================

# Ensure common binary paths are available (RTR/remote execution uses minimal PATH)
export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$PATH"

set -e

# Configuration
GITHUB_RAW_BASE="https://raw.githubusercontent.com/akto-api-security/akto/master/apps/mcp-endpoint-shield/github-cli-hooks"
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
    echo "[GitHub CLI Hooks] $1"
}

log_error() {
    echo "[GitHub CLI Hooks] ERROR: $1" >&2
}

install_for_user() {
    local user_home="$1"

    export HOME="$user_home"
    TARGET_USER_HOME="$user_home"
    GH_HOOKS_DIR="$user_home/.github/hooks"
    GH_HOOKS_FILE="$user_home/.copilot/config.json"
    CONFIG_FILE="$user_home/.akto-endpoint-shield/config/config.env"

    if main; then
        return 0
    else
        log_error "Installation failed for $user_home"
        return 1
    fi
}

# Check if GitHub Copilot CLI (gh copilot extension) is installed or configured.
# Returns 0 when any of the following is true:
#   1. gh binary is present AND gh copilot extension is listed
#   2. The copilot extension directory exists under common gh extension paths
#   3. ~/.copilot/ already exists (user previously configured Copilot CLI)
check_copilot_cli_installed() {
    # Fast path: directory created by a prior install — no gh needed
    if [ -d "$HOME/.copilot" ]; then
        return 0
    fi

    if command -v gh >/dev/null 2>&1; then
        # Ask gh which extensions are installed (most reliable)
        if gh extension list 2>/dev/null | grep -qi "copilot"; then
            return 0
        fi
        # Also scan the extension dir directly — avoids a gh subprocess on every call
        local ext_dir="$HOME/.local/share/gh/extensions"
        if [ -d "$ext_dir" ] && ls "$ext_dir" 2>/dev/null | grep -qi "copilot"; then
            return 0
        fi
    fi

    return 1
}

# Returns 0 if ENABLE_PROMPT_HOOKS_GITHUB_CLI is enabled (env var takes priority, then config file).
# Default: enabled (return 0) when the flag is absent — flag absent means hooks run
# files written before this flag was introduced. Explicit "false" disables.
is_prompt_hooks_enabled() {
    [ "$ENABLE_PROMPT_HOOKS_GITHUB_CLI" = "true" ]  && return 0
    [ "$ENABLE_PROMPT_HOOKS_GITHUB_CLI" = "false" ] && return 1
    if [ -f "$CONFIG_FILE" ]; then
        local val
        val=$(grep "^ENABLE_PROMPT_HOOKS_GITHUB_CLI=" "$CONFIG_FILE" 2>/dev/null | cut -d= -f2-)
        [ "$val" = "true" ]  && return 0
        [ "$val" = "false" ] && return 1
    fi
    return 0  # Default: enabled — flag absent means hooks run
}

# Returns 0 if ENABLE_MCP_HOOKS_GITHUB_CLI is enabled (env var takes priority, then config file).
# Default: enabled (return 0) when the flag is absent — flag absent means hooks run.
is_mcp_hooks_enabled() {
    [ "$ENABLE_MCP_HOOKS_GITHUB_CLI" = "true" ]  && return 0
    [ "$ENABLE_MCP_HOOKS_GITHUB_CLI" = "false" ] && return 1
    if [ -f "$CONFIG_FILE" ]; then
        local val
        val=$(grep "^ENABLE_MCP_HOOKS_GITHUB_CLI=" "$CONFIG_FILE" 2>/dev/null | cut -d= -f2-)
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
# the agent poller updates config.env — no hooks.json surgery required.
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

# Create akto-hook-wrapper.sh (always generated locally for correct absolute paths)
create_hook_wrapper() {
    local ingestion_url="$1"
    local device_id="$2"
    local wrapper_file="$GH_HOOKS_DIR/akto-hook-wrapper.sh"

    cat > "$wrapper_file" <<EOF
#!/bin/bash
# Auto-generated wrapper for Akto observability hooks
# Generated by MCP Endpoint Shield installer

export MODE="atlas"
export AKTO_DATA_INGESTION_URL="$ingestion_url"
export AKTO_API_TOKEN="$API_TOKEN"
export AKTO_SYNC_MODE="true"
export AKTO_TIMEOUT="5"
export AKTO_CONNECTOR="github_cli"
export CONTEXT_SOURCE="ENDPOINT"
export DEVICE_ID="$device_id"

SCRIPT_DIR="\$HOME/.github/hooks"
exec python3 "\$SCRIPT_DIR/\$1" "\${@:2}"
EOF
    chmod +x "$wrapper_file"
    _inject_flag_guard "$wrapper_file" "ENABLE_PROMPT_HOOKS_GITHUB_CLI"
    log "✓ Created akto-hook-wrapper.sh"
}

# Create wrapper script for prompt hook (always generated locally for correct absolute paths)
create_prompt_wrapper() {
    local ingestion_url="$1"
    local device_id="$2"

    local wrapper_file="$GH_HOOKS_DIR/akto-validate-prompt-wrapper.sh"
    local python_script="$HOME/.github/hooks/akto-validate-prompt.py"

    cat > "$wrapper_file" <<EOF
#!/bin/bash
# Auto-generated wrapper for Akto guardrails hook
# Generated by Akto Endpoint Shield installer

export MODE="atlas"
export AKTO_DATA_INGESTION_URL="$ingestion_url"
export AKTO_API_TOKEN="$API_TOKEN"
export AKTO_SYNC_MODE="true"
export AKTO_TIMEOUT="5"
export AKTO_CONNECTOR="github_cli"
export CONTEXT_SOURCE="ENDPOINT"
export DEVICE_ID="$device_id"

# Log hook startup env for diagnostics
echo "=== Hook startup env (prompt) ===" >&2
echo "  AKTO_DATA_INGESTION_URL:         \${AKTO_DATA_INGESTION_URL:-(not set)}" >&2
echo "  DEVICE_ID:                       \${DEVICE_ID:-(not set)}" >&2
echo "  AKTO_CONNECTOR:                  \${AKTO_CONNECTOR:-(not set)}" >&2
echo "  ENABLE_MCP_HOOKS_GITHUB_CLI:    \${ENABLE_MCP_HOOKS_GITHUB_CLI:-(not set)}" >&2
echo "  ENABLE_PROMPT_HOOKS_GITHUB_CLI: \${ENABLE_PROMPT_HOOKS_GITHUB_CLI:-(not set)}" >&2

# Execute Python hook script
exec python3 "$python_script" "\$@"
EOF
    chmod +x "$wrapper_file"
    _inject_flag_guard "$wrapper_file" "ENABLE_PROMPT_HOOKS_GITHUB_CLI"
}

# Create wrapper script for MCP hook (always generated locally for correct absolute paths)
create_mcp_wrapper() {
    local hook_type="$1"  # "pre-tool" or "post-tool"
    local ingestion_url="$2"
    local device_id="$3"

    local wrapper_file="$GH_HOOKS_DIR/akto-validate-${hook_type}-wrapper.sh"
    local python_script="$HOME/.github/hooks/akto-validate-${hook_type}.py"

    cat > "$wrapper_file" <<EOF
#!/bin/bash
# Auto-generated wrapper for Akto guardrails MCP hook
# Generated by Akto Endpoint Shield installer

export MODE="atlas"
export AKTO_DATA_INGESTION_URL="$ingestion_url"
export AKTO_API_TOKEN="$API_TOKEN"
export AKTO_SYNC_MODE="true"
export AKTO_TIMEOUT="5"
export AKTO_CONNECTOR="github_cli"
export CONTEXT_SOURCE="ENDPOINT"
export DEVICE_ID="$device_id"

# Execute Python hook script
exec python3 "$python_script" "\$@"
EOF
    chmod +x "$wrapper_file"
    _inject_flag_guard "$wrapper_file" "ENABLE_MCP_HOOKS_GITHUB_CLI"
}

# Update or create GitHub CLI hooks.json with prompt hook (userPromptSubmitted)
# Note: GitHub CLI hooks.json format differs from Claude/Cursor:
#   - "version": 1 at root level
#   - Event keys are camelCase: userPromptSubmitted, preToolUse, postToolUse
#   - Entries are flat objects with "bash" field (not nested "hooks" array)
update_gh_hooks() {
    local prompt_wrapper="$GH_HOOKS_DIR/akto-validate-prompt-wrapper.sh"

    local hook_wrapper="$GH_HOOKS_DIR/akto-hook-wrapper.sh"

    local new_hooks_config=$(cat <<EOF
{
    "version": 1,
    "hooks": {
        "userPromptSubmitted": [
            {
                "type": "command",
                "bash": "bash $prompt_wrapper",
                "comment": "Validate prompts against Akto Guardrails",
                "timeoutSec": 30
            }
        ],
        "SessionStart": [
            {
                "type": "command",
                "bash": "bash $hook_wrapper akto-hooks.py SessionStart",
                "comment": "Ingest session start event to Akto for observability",
                "timeoutSec": 10
            }
        ],
        "Stop": [
            {
                "type": "command",
                "bash": "bash $hook_wrapper akto-hooks.py Stop",
                "comment": "Ingest session stop event to Akto for observability",
                "timeoutSec": 10
            }
        ],
        "PreCompact": [
            {
                "type": "command",
                "bash": "bash $hook_wrapper akto-hooks.py PreCompact",
                "comment": "Ingest pre-compact event to Akto for observability",
                "timeoutSec": 10
            }
        ],
        "SubagentStart": [
            {
                "type": "command",
                "bash": "bash $hook_wrapper akto-hooks.py SubagentStart",
                "comment": "Ingest subagent start event to Akto for observability",
                "timeoutSec": 10
            }
        ],
        "SubagentStop": [
            {
                "type": "command",
                "bash": "bash $hook_wrapper akto-hooks.py SubagentStop",
                "comment": "Ingest subagent stop event to Akto for observability",
                "timeoutSec": 10
            }
        ]
    }
}
EOF
)

    if [ -f "$GH_HOOKS_FILE" ]; then
        log "Existing hooks.json found, backing up..."
        cp "$GH_HOOKS_FILE" "$GH_HOOKS_FILE.backup"

        if command -v jq >/dev/null 2>&1; then
            jq --argjson new "$new_hooks_config" '
                .hooks.userPromptSubmitted = $new.hooks.userPromptSubmitted |
                .hooks.SessionStart        = $new.hooks.SessionStart |
                .hooks.Stop                = $new.hooks.Stop |
                .hooks.PreCompact          = $new.hooks.PreCompact |
                .hooks.SubagentStart       = $new.hooks.SubagentStart |
                .hooks.SubagentStop        = $new.hooks.SubagentStop |
                .version = 1
            ' "$GH_HOOKS_FILE" > "$GH_HOOKS_FILE.tmp"
            mv "$GH_HOOKS_FILE.tmp" "$GH_HOOKS_FILE"
            log "Merged prompt hook into existing hooks.json"
        else
            echo "$new_hooks_config" > "$GH_HOOKS_FILE"
            log "Created new hooks.json (no jq available for merge)"
        fi
    else
        echo "$new_hooks_config" > "$GH_HOOKS_FILE"
        log "Created new hooks.json with hooks"
    fi
}

# Download MCP hook scripts and merge preToolUse/postToolUse into hooks.json
install_mcp_hooks() {
    local ingestion_url="$1"
    local device_id="$2"

    log "Installing MCP hooks (preToolUse/postToolUse)..."

    # Download MCP Python scripts
    for script in akto-validate-pre-tool.py akto-validate-post-tool.py; do
        if ! download_file "$GITHUB_RAW_BASE/$script" "$GH_HOOKS_DIR/$script"; then
            log_error "Failed to download $script"
            return 1
        fi
        chmod +x "$GH_HOOKS_DIR/$script"
        log "✓ Downloaded $script"
    done

    # Download and configure MCP wrapper scripts
    create_mcp_wrapper "pre-tool" "$ingestion_url" "$device_id"
    log "✓ Created akto-validate-pre-tool-wrapper.sh"

    create_mcp_wrapper "post-tool" "$ingestion_url" "$device_id"
    log "✓ Created akto-validate-post-tool-wrapper.sh"

    local pre_tool_wrapper="$GH_HOOKS_DIR/akto-validate-pre-tool-wrapper.sh"
    local post_tool_wrapper="$GH_HOOKS_DIR/akto-validate-post-tool-wrapper.sh"

    # Merge preToolUse/postToolUse into GitHub CLI hooks.json
    if [ -f "$GH_HOOKS_FILE" ]; then
        if command -v jq >/dev/null 2>&1; then
            jq \
                --arg pre "bash $pre_tool_wrapper" \
                --arg post "bash $post_tool_wrapper" '
                .hooks.preToolUse = [{"type": "command", "bash": $pre, "comment": "Validate and block tool execution based on Akto Guardrails policies", "timeoutSec": 30}] |
                .hooks.postToolUse = [{"type": "command", "bash": $post, "comment": "Ingest tool execution results to Akto for monitoring and analytics", "timeoutSec": 30}] |
                .version = 1
            ' "$GH_HOOKS_FILE" > "$GH_HOOKS_FILE.tmp"
            mv "$GH_HOOKS_FILE.tmp" "$GH_HOOKS_FILE"
            log "✓ Merged MCP hooks (preToolUse/postToolUse) into hooks.json"
        else
            # No jq — write a minimal config with just the MCP hooks
            cat > "$GH_HOOKS_FILE" <<EOF
{
    "version": 1,
    "hooks": {
        "preToolUse": [{"type": "command", "bash": "bash $pre_tool_wrapper", "comment": "Validate and block tool execution based on Akto Guardrails policies", "timeoutSec": 30}],
        "postToolUse": [{"type": "command", "bash": "bash $post_tool_wrapper", "comment": "Ingest tool execution results to Akto for monitoring and analytics", "timeoutSec": 30}]
    }
}
EOF
            log "✓ Created hooks.json with MCP hooks (no jq available for merge)"
        fi
    else
        # No existing hooks.json — create minimal file with MCP hooks only
        cat > "$GH_HOOKS_FILE" <<EOF
{
    "version": 1,
    "hooks": {
        "preToolUse": [{"type": "command", "bash": "bash $pre_tool_wrapper", "comment": "Validate and block tool execution based on Akto Guardrails policies", "timeoutSec": 30}],
        "postToolUse": [{"type": "command", "bash": "bash $post_tool_wrapper", "comment": "Ingest tool execution results to Akto for monitoring and analytics", "timeoutSec": 30}]
    }
}
EOF
        log "✓ Created hooks.json with MCP hooks"
    fi
}

# Main installation function
main() {
    log "Starting GitHub CLI hook installation..."

    # Check if GitHub Copilot CLI is installed or previously configured
    if ! check_copilot_cli_installed; then
        log "GitHub Copilot CLI not detected (gh copilot extension not found, ~/.copilot/ absent) - skipping hook installation"
        return 0
    fi

    log "✓ GitHub Copilot CLI detected"

    # Check if any hooks are enabled — bail early only when both are explicitly disabled
    if ! is_prompt_hooks_enabled && ! is_mcp_hooks_enabled; then
        log "Both ENABLE_PROMPT_HOOKS_GITHUB_CLI and ENABLE_MCP_HOOKS_GITHUB_CLI are set to false — skipping hook installation"
        return 0
    fi

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

    # Create hooks and config directories
    mkdir -p "$GH_HOOKS_DIR"
    mkdir -p "$(dirname "$GH_HOOKS_FILE")"
    log "✓ Created hooks directory: $GH_HOOKS_DIR"

    # -----------------------------------------------------------------------
    # Download shared utility files
    # -----------------------------------------------------------------------
    log "Downloading hook scripts from GitHub..."

    if ! download_file "$GITHUB_SHARED_BASE/akto_ingestion_utility.py" "$GH_HOOKS_DIR/akto_ingestion_utility.py"; then
        log_error "Failed to download akto_ingestion_utility.py"
        return 1
    fi
    chmod +x "$GH_HOOKS_DIR/akto_ingestion_utility.py"
    log "✓ Downloaded akto_ingestion_utility.py"

    if ! download_file "$GITHUB_RAW_BASE/akto_machine_id.py" "$GH_HOOKS_DIR/akto_machine_id.py"; then
        log_error "Failed to download akto_machine_id.py"
        return 1
    fi
    chmod +x "$GH_HOOKS_DIR/akto_machine_id.py"
    log "✓ Downloaded akto_machine_id.py"

    # Download observability hook script and heartbeat
    for f in akto-hooks.py akto_heartbeat.py; do
        if ! download_file "$GITHUB_RAW_BASE/$f" "$GH_HOOKS_DIR/$f"; then
            log_error "Failed to download $f"
            return 1
        fi
        chmod +x "$GH_HOOKS_DIR/$f"
        log "✓ Downloaded $f"
    done

    # Create akto-hook-wrapper.sh
    create_hook_wrapper "$INGESTION_URL" "$DEVICE_ID"

    # -----------------------------------------------------------------------
    # PROMPT HOOKS section (userPromptSubmitted)
    # -----------------------------------------------------------------------
    if is_prompt_hooks_enabled; then
        log "Installing prompt hook (userPromptSubmitted)..."

        if ! download_file "$GITHUB_RAW_BASE/akto-validate-prompt.py" "$GH_HOOKS_DIR/akto-validate-prompt.py"; then
            log_error "Failed to download akto-validate-prompt.py"
            return 1
        fi
        chmod +x "$GH_HOOKS_DIR/akto-validate-prompt.py"
        log "✓ Downloaded akto-validate-prompt.py"

        create_prompt_wrapper "$INGESTION_URL" "$DEVICE_ID"
        log "✓ Created akto-validate-prompt-wrapper.sh"
    else
        log "ENABLE_PROMPT_HOOKS_GITHUB_CLI not set — skipping prompt hook"
    fi

    # Update GitHub CLI hooks.json with all hooks (always runs)
    update_gh_hooks
    log "✓ Updated hooks.json with hooks"

    # -----------------------------------------------------------------------
    # MCP HOOKS section (preToolUse / postToolUse)
    # -----------------------------------------------------------------------
    if is_mcp_hooks_enabled; then
        install_mcp_hooks "$INGESTION_URL" "$DEVICE_ID"
        log "✓ MCP hooks installed"
    else
        log "ENABLE_MCP_HOOKS_GITHUB_CLI not set — skipping MCP hooks"
    fi

    # RTR runs as root — the hooks dir and hooks.json we just created/modified are root-owned
    # by default, which locks the real user out of their own files. Hand ownership back on
    # exactly what we touched. macOS stat -f, falling back to GNU stat -c.
    REAL_USER="$(stat -f '%Su' "$TARGET_USER_HOME" 2>/dev/null || stat -c '%U' "$TARGET_USER_HOME" 2>/dev/null || echo "")"
    if [ -n "$REAL_USER" ] && [ "$REAL_USER" != "root" ]; then
        chown -R "$REAL_USER" "$GH_HOOKS_DIR" 2>/dev/null || true
        chown "$REAL_USER" "$GH_HOOKS_FILE" "$GH_HOOKS_FILE.backup" 2>/dev/null || true
        log "✓ Set ownership to $REAL_USER"
    fi

    log ""
    log "=========================================="
    log "✅ GitHub CLI hooks installed successfully!"
    log "=========================================="
    log ""
    log "Hooks location: $GH_HOOKS_DIR"
    log "Configuration: $GH_HOOKS_FILE"
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

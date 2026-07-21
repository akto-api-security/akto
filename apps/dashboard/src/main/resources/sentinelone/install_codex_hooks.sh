#!/bin/bash

# ========================================================================================
# Akto Endpoint Shield - Codex CLI Hook Installer
# ========================================================================================
# Automatically installs Akto guardrails hooks for Codex CLI if detected
# Downloads latest hooks from GitHub and configures Codex (hooks.json + codex_hooks in config.toml; see OpenAI Codex hooks doc).
#
# Controlled by flags in config.env (Jamf/enterprise only — all default off):
#   ENABLE_PROMPT_HOOKS_CODEX=true  — installs UserPromptSubmit/Stop hooks
#   ENABLE_MCP_HOOKS_CODEX=true     — installs PreToolUse/PostToolUse MCP hooks
# ========================================================================================

# Ensure common binary paths are available (RTR/remote execution uses minimal PATH)
export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$PATH"

set -e

# Configuration
GITHUB_RAW_BASE="https://raw.githubusercontent.com/akto-api-security/akto/master/apps/mcp-endpoint-shield/codex-cli-hooks"
GITHUB_SHARED_BASE="https://raw.githubusercontent.com/akto-api-security/akto/master/apps/mcp-endpoint-shield/shared"

TARGET_USER_HOME="${TARGET_USER_HOME:-}"
AKTO_DATA_INGESTION_URL="${AKTO_DATA_INGESTION_URL:-}"
AKTO_API_BASE_URL="${AKTO_API_BASE_URL:-}"
AKTO_API_TOKEN="${AKTO_API_TOKEN:-}"
for a in "$@"; do
    case "$a" in
        TARGET_USER_HOME=*) TARGET_USER_HOME="${a#TARGET_USER_HOME=}" ;;
        AKTO_API_BASE_URL=*) AKTO_API_BASE_URL="${a#AKTO_API_BASE_URL=}" ;;
        AKTO_DATA_INGESTION_URL=*) AKTO_DATA_INGESTION_URL="${a#AKTO_DATA_INGESTION_URL=}" ;;
        AKTO_API_TOKEN=*) AKTO_API_TOKEN="${a#AKTO_API_TOKEN=}" ;;
    esac
done

[ -n "$AKTO_API_BASE_URL" ] && export AKTO_API_BASE_URL
[ -n "$AKTO_DATA_INGESTION_URL" ] && export AKTO_DATA_INGESTION_URL
[ -n "$AKTO_API_TOKEN" ] && export AKTO_API_TOKEN

# Log function
log() {
    echo "[Codex Hooks] $1"
}

log_error() {
    echo "[Codex Hooks] ERROR: $1" >&2
}

install_for_user() {
    local user_home="$1"

    export HOME="$user_home"
    TARGET_USER_HOME="$user_home"
    CODEX_HOOKS_DIR="$user_home/.codex/hooks"
    # Codex loads hook registration from ~/.codex/hooks.json (not settings.json).
    CODEX_SETTINGS_FILE="$user_home/.codex/hooks.json"
    # Enterprise requirements file pushed by MDM; its presence signals an enterprise deployment.
    REQUIREMENTS_TOML="$user_home/.codex/requirements.toml"
    CONFIG_FILE="$user_home/.akto-endpoint-shield/config/config.env"

    if main; then
        return 0
    else
        log_error "Installation failed for $user_home"
        return 1
    fi
}

# Check if Codex CLI is installed
check_codex_installed() {
    if command -v codex >/dev/null 2>&1 || [ -d "$TARGET_USER_HOME/.codex" ]; then
        return 0
    else
        return 1
    fi
}

# Returns 0 if ENABLE_PROMPT_HOOKS_CODEX is enabled (env var takes priority, then config file).
# Default: enabled (return 0) when the flag is absent — flag absent means hooks run
# files written before this flag was introduced. Explicit "false" disables.
is_prompt_hooks_enabled() {
    [ "$ENABLE_PROMPT_HOOKS_CODEX" = "true" ]  && return 0
    [ "$ENABLE_PROMPT_HOOKS_CODEX" = "false" ] && return 1
    if [ -f "$CONFIG_FILE" ]; then
        local val
        val=$(grep "^ENABLE_PROMPT_HOOKS_CODEX=" "$CONFIG_FILE" 2>/dev/null | cut -d= -f2-)
        [ "$val" = "true" ]  && return 0
        [ "$val" = "false" ] && return 1
    fi
    return 0  # Default: enabled — flag absent means hooks run
}

# Returns 0 if ENABLE_MCP_HOOKS_CODEX is enabled (env var takes priority, then config file).
# Default: enabled (return 0) when the flag is absent — flag absent means hooks run.
is_mcp_hooks_enabled() {
    [ "$ENABLE_MCP_HOOKS_CODEX" = "true" ]  && return 0
    [ "$ENABLE_MCP_HOOKS_CODEX" = "false" ] && return 1
    if [ -f "$CONFIG_FILE" ]; then
        local val
        val=$(grep "^ENABLE_MCP_HOOKS_CODEX=" "$CONFIG_FILE" 2>/dev/null | cut -d= -f2-)
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

# OpenAI Codex hooks require [features] codex_hooks = true in ~/.codex/config.toml.
ensure_codex_hooks_feature_flag() {
    local cf="$HOME/.codex/config.toml"
    mkdir -p "$HOME/.codex"
    # Already set correctly — nothing to do.
    [ -f "$cf" ] && grep -qE '^[[:space:]]*codex_hooks[[:space:]]*=[[:space:]]*true' "$cf" 2>/dev/null && return 0
    # File doesn't exist — create it fresh.
    if [ ! -f "$cf" ]; then
        printf '[features]\ncodex_hooks = true\n' >"$cf"
        return 0
    fi
    # codex_hooks key exists but is not true — update it in place.
    if grep -qE '^[[:space:]]*codex_hooks[[:space:]]*=' "$cf" 2>/dev/null; then
        sed -i '' -E 's/^[[:space:]]*codex_hooks[[:space:]]*=.*/codex_hooks = true/' "$cf" 2>/dev/null || true
        return 0
    fi
    # [features] section exists but has no codex_hooks line — insert on the line immediately
    # after [features] to avoid creating a duplicate header which breaks TOML parsing.
    # awk is used instead of BSD sed because sed's /pattern/a\ is unreliable on macOS.
    if grep -qE '^\[features\]' "$cf" 2>/dev/null; then
        awk '/^\[features\]/{print; print "codex_hooks = true"; next}1' "$cf" > "$cf.tmp" \
            && mv "$cf.tmp" "$cf"
        return 0
    fi
    # No [features] section at all — safe to append a new one.
    printf '\n[features]\ncodex_hooks = true\n' >>"$cf"
}

# Returns 0 when running in enterprise mode.
# Signals checked in priority order:
#   1. ENTERPRISE_MODE=true env var (set by Jamf/MDM script)
#   2. requirements.toml exists (MDM pushed it before installer ran)
#   3. allow_managed_hooks_only is set in any local config
is_enterprise_mode() {
    [ "$ENTERPRISE_MODE" = "true" ] && return 0
    [ -f "$REQUIREMENTS_TOML" ] && return 0
    grep -qE '^[[:space:]]*allow_managed_hooks_only[[:space:]]*=[[:space:]]*true' \
        "$HOME/.codex/config.toml" 2>/dev/null && return 0
    return 1
}

# Ensures requirements.toml has the two keys Codex needs for managed hooks:
#   [features] hooks = true   — forces hooks on even if user disables them locally
#   [hooks] managed_dir       — tells Codex where managed scripts live
ensure_requirements_toml_flags() {
    local rf="$REQUIREMENTS_TOML"
    mkdir -p "$HOME/.codex"
    touch "$rf"

    if ! grep -qE '^[[:space:]]*hooks[[:space:]]*=[[:space:]]*true' "$rf" 2>/dev/null; then
        if grep -qE '^\[features\]' "$rf" 2>/dev/null; then
            awk '/^\[features\]/{print; print "hooks = true"; next}1' "$rf" > "$rf.tmp" \
                && mv "$rf.tmp" "$rf"
        else
            printf '\n[features]\nhooks = true\n' >> "$rf"
        fi
        log "✓ Set [features] hooks = true in requirements.toml"
    fi

    if ! grep -qE '^[[:space:]]*managed_dir[[:space:]]*=' "$rf" 2>/dev/null; then
        if grep -qE '^\[hooks\]' "$rf" 2>/dev/null; then
            awk -v dir="$CODEX_HOOKS_DIR" \
                '/^\[hooks\]/{print; print "managed_dir = \"" dir "\""; next}1' \
                "$rf" > "$rf.tmp" && mv "$rf.tmp" "$rf"
        else
            printf '\n[hooks]\nmanaged_dir = "%s"\n' "$CODEX_HOOKS_DIR" >> "$rf"
        fi
        log "✓ Set [hooks] managed_dir in requirements.toml"
    fi
}

# Appends Akto hook entries to requirements.toml as managed hooks.
# Managed hooks are trusted by Codex policy — no manual /hooks approval ever needed.
# Idempotent: removes any previous Akto block before writing a fresh one.
write_enterprise_hooks() {
    local rf="$REQUIREMENTS_TOML"

    cp "$rf" "$rf.backup" 2>/dev/null || true

    # Strip previous Akto block so re-runs don't accumulate duplicate entries.
    if grep -q '# BEGIN AKTO MANAGED HOOKS' "$rf" 2>/dev/null; then
        awk '/# BEGIN AKTO MANAGED HOOKS/{skip=1}
             skip && /# END AKTO MANAGED HOOKS/{skip=0; next}
             !skip' "$rf" > "$rf.tmp" && mv "$rf.tmp" "$rf"
    fi

    local session_cmd="bash $CODEX_HOOKS_DIR/akto-hook-wrapper.sh akto-hooks.py SessionStart"
    local prompt_wrapper="$CODEX_HOOKS_DIR/akto-validate-prompt-wrapper.sh"
    local response_wrapper="$CODEX_HOOKS_DIR/akto-validate-response-wrapper.sh"
    local pre_tool_wrapper="$CODEX_HOOKS_DIR/akto-validate-pre-tool-wrapper.sh"
    local post_tool_wrapper="$CODEX_HOOKS_DIR/akto-validate-post-tool-wrapper.sh"

    {
        printf '\n# BEGIN AKTO MANAGED HOOKS\n'

        printf '[[hooks.SessionStart]]\n'
        printf '[[hooks.SessionStart.hooks]]\n'
        printf 'type = "command"\n'
        printf 'command = "%s"\n' "$session_cmd"
        printf 'timeout = 10\n'

        if is_prompt_hooks_enabled; then
            printf '\n[[hooks.UserPromptSubmit]]\n'
            printf '[[hooks.UserPromptSubmit.hooks]]\n'
            printf 'type = "command"\n'
            printf 'command = "%s"\n' "$prompt_wrapper"
            printf 'timeout = 10\n'

            printf '\n[[hooks.Stop]]\n'
            printf '[[hooks.Stop.hooks]]\n'
            printf 'type = "command"\n'
            printf 'command = "%s"\n' "$response_wrapper"
            printf 'timeout = 10\n'
        fi

        if is_mcp_hooks_enabled; then
            printf '\n[[hooks.PreToolUse]]\n'
            printf 'matcher = "^Bash$"\n'
            printf '[[hooks.PreToolUse.hooks]]\n'
            printf 'type = "command"\n'
            printf 'command = "%s"\n' "$pre_tool_wrapper"
            printf 'timeout = 10\n'

            printf '\n[[hooks.PostToolUse]]\n'
            printf 'matcher = "^Bash$"\n'
            printf '[[hooks.PostToolUse.hooks]]\n'
            printf 'type = "command"\n'
            printf 'command = "%s"\n' "$post_tool_wrapper"
            printf 'timeout = 10\n'
        fi

        printf '# END AKTO MANAGED HOOKS\n'
    } >> "$rf"

    log "✓ Managed hooks written to requirements.toml (no manual trust required)"
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
# the agent poller updates config.env — no hook config file surgery required.
#
# Usage: _inject_flag_guard <wrapper_file> <FLAG_ENV_VAR_NAME> <device_id>
_inject_flag_guard() {
    local _wf="$1"
    local _flag="$2"
    local _device_id="${3:-}"
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
export DEVICE_ID="$_device_id"
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

    local wrapper_file="$CODEX_HOOKS_DIR/akto-validate-${hook_type}-wrapper.sh"
    local template_url="$GITHUB_RAW_BASE/akto-validate-${hook_type}-wrapper.sh"
    local python_script="$CODEX_HOOKS_DIR/akto-validate-${hook_type}.py"

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
export AKTO_CONNECTOR="codex_cli"
export CONTEXT_SOURCE="ENDPOINT"
export DEVICE_ID="$device_id"

# Log hook startup env for diagnostics
echo "=== Hook startup env ($hook_type) ===" >&2
echo "  AKTO_DATA_INGESTION_URL:    \${AKTO_DATA_INGESTION_URL:-(not set)}" >&2
echo "  DEVICE_ID:                  \${DEVICE_ID:-(not set)}" >&2
echo "  AKTO_CONNECTOR:             \${AKTO_CONNECTOR:-(not set)}" >&2
echo "  ENABLE_MCP_HOOKS_CODEX:    \${ENABLE_MCP_HOOKS_CODEX:-(not set)}" >&2
echo "  ENABLE_PROMPT_HOOKS_CODEX: \${ENABLE_PROMPT_HOOKS_CODEX:-(not set)}" >&2

# Execute Python hook script
exec python3 "$python_script" "\$@"
EOF
        chmod +x "$wrapper_file"
        _inject_flag_guard "$wrapper_file" "ENABLE_PROMPT_HOOKS_CODEX" "$device_id"
        return 0
    fi

    # Substitute placeholders in template (GitHub format uses {{PLACEHOLDER}})
    sed -e "s|{{AKTO_DATA_INGESTION_URL}}|$ingestion_url|g" \
        -e "s|{{AKTO_API_TOKEN}}|$API_TOKEN|g" \
        -e "s|{{DEVICE_ID (optional)}}|$device_id|g" \
        "$wrapper_file.tmp" > "$wrapper_file"

    rm -f "$wrapper_file.tmp"
    chmod +x "$wrapper_file"
    _inject_flag_guard "$wrapper_file" "ENABLE_PROMPT_HOOKS_CODEX" "$device_id"
}

# Download and configure wrapper script for MCP hook
create_mcp_wrapper() {
    local hook_type="$1"  # "pre-tool" or "post-tool"
    local ingestion_url="$2"
    local device_id="$3"

    local wrapper_file="$CODEX_HOOKS_DIR/akto-validate-${hook_type}-wrapper.sh"
    local template_url="$GITHUB_RAW_BASE/akto-validate-${hook_type}-wrapper.sh"

    # Download wrapper template from GitHub
    if ! download_file "$template_url" "$wrapper_file.tmp"; then
        log_error "Failed to download MCP wrapper template for $hook_type, creating locally..."

        local python_script="$CODEX_HOOKS_DIR/akto-validate-${hook_type}.py"

        # Fallback: create wrapper locally if download fails
        cat > "$wrapper_file" <<EOF
#!/bin/bash
# Auto-generated wrapper for Akto guardrails MCP hook
# Generated by Akto Endpoint Shield installer

export MODE="atlas"
export AKTO_DATA_INGESTION_URL="$ingestion_url"
export AKTO_API_TOKEN="$API_TOKEN"
export AKTO_SYNC_MODE="true"
export AKTO_TIMEOUT="5"
export AKTO_CONNECTOR="codex_cli"
export CONTEXT_SOURCE="ENDPOINT"
export DEVICE_ID="$device_id"

# Execute Python hook script
exec python3 "$python_script" "\$@"
EOF
        chmod +x "$wrapper_file"
        _inject_flag_guard "$wrapper_file" "ENABLE_MCP_HOOKS_CODEX" "$device_id"
        return 0
    fi

    # Substitute placeholders in template
    sed -e "s|{{AKTO_DATA_INGESTION_URL}}|$ingestion_url|g" \
        -e "s|{{AKTO_API_TOKEN}}|$API_TOKEN|g" \
        -e "s|{{DEVICE_ID (optional)}}|$device_id|g" \
        "$wrapper_file.tmp" > "$wrapper_file"

    rm -f "$wrapper_file.tmp"
    chmod +x "$wrapper_file"
    _inject_flag_guard "$wrapper_file" "ENABLE_MCP_HOOKS_CODEX" "$device_id"
}

# Download akto-hook-wrapper.sh and substitute placeholders
create_hook_wrapper() {
    local ingestion_url="$1"
    local device_id="$2"
    local wrapper_file="$CODEX_HOOKS_DIR/akto-hook-wrapper.sh"

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
export AKTO_CONNECTOR="codex_cli"
export CONTEXT_SOURCE="ENDPOINT"
export DEVICE_ID="$device_id"

SCRIPT_DIR="$CODEX_HOOKS_DIR"
exec python3 "\$SCRIPT_DIR/\$1" "\${@:2}"
EOF
        chmod +x "$wrapper_file"
        _inject_flag_guard "$wrapper_file" "ENABLE_MCP_HOOKS_CODEX" "$device_id"
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

# Update or create Codex hooks.json with prompt hooks (UserPromptSubmit / Stop)
update_codex_settings() {
    local prompt_wrapper="$CODEX_HOOKS_DIR/akto-validate-prompt-wrapper.sh"
    local response_wrapper="$CODEX_HOOKS_DIR/akto-validate-response-wrapper.sh"

    local hooks_dir="$CODEX_HOOKS_DIR"
    local wrapper="bash $hooks_dir/akto-hook-wrapper.sh"

    local new_hooks_config=$(cat <<EOF
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
        ],
        "SessionStart": [
            {
                "hooks": [
                    {
                        "type": "command",
                        "command": "$wrapper akto-hooks.py SessionStart",
                        "timeout": 10
                    }
                ]
            }
        ]
    }
}
EOF
)

    if [ -f "$CODEX_SETTINGS_FILE" ]; then
        log "Existing hooks.json found, backing up..."
        cp "$CODEX_SETTINGS_FILE" "$CODEX_SETTINGS_FILE.backup"

        if command -v jq >/dev/null 2>&1; then
            jq --argjson newhooks "$new_hooks_config" '
                .hooks.UserPromptSubmit = $newhooks.hooks.UserPromptSubmit |
                .hooks.Stop             = $newhooks.hooks.Stop |
                .hooks.SessionStart     = $newhooks.hooks.SessionStart
            ' "$CODEX_SETTINGS_FILE" > "$CODEX_SETTINGS_FILE.tmp"
            mv "$CODEX_SETTINGS_FILE.tmp" "$CODEX_SETTINGS_FILE"
            log "Merged prompt hooks into existing hooks.json"
        else
            echo "$new_hooks_config" > "$CODEX_SETTINGS_FILE"
            log "Created new hooks.json (no jq available for merge)"
        fi
    else
        echo "$new_hooks_config" > "$CODEX_SETTINGS_FILE"
        log "Created new hooks.json with prompt hooks"
    fi
}

# Downloads MCP Python scripts and creates wrapper .sh files only — no hooks.json write.
# Used directly by the enterprise path; called internally by install_mcp_hooks for non-enterprise.
install_mcp_scripts_only() {
    local ingestion_url="$1"
    local device_id="$2"

    for script in akto-validate-pre-tool.py akto-validate-post-tool.py; do
        if ! download_file "$GITHUB_RAW_BASE/$script" "$CODEX_HOOKS_DIR/$script"; then
            log_error "Failed to download $script"
            return 1
        fi
        chmod +x "$CODEX_HOOKS_DIR/$script"
        log "✓ Downloaded $script"
    done

    create_mcp_wrapper "pre-tool" "$ingestion_url" "$device_id"
    log "✓ Created akto-validate-pre-tool-wrapper.sh"

    create_mcp_wrapper "post-tool" "$ingestion_url" "$device_id"
    log "✓ Created akto-validate-post-tool-wrapper.sh"
}

# Download MCP hook scripts and merge PreToolUse/PostToolUse into hooks.json (non-enterprise).
install_mcp_hooks() {
    local ingestion_url="$1"
    local device_id="$2"

    log "Installing MCP hooks (PreToolUse/PostToolUse)..."

    install_mcp_scripts_only "$ingestion_url" "$device_id" || return 1

    local pre_tool_wrapper="$CODEX_HOOKS_DIR/akto-validate-pre-tool-wrapper.sh"
    local post_tool_wrapper="$CODEX_HOOKS_DIR/akto-validate-post-tool-wrapper.sh"

    if [ -f "$CODEX_SETTINGS_FILE" ]; then
        if command -v jq >/dev/null 2>&1; then
            jq \
                --arg pre "$pre_tool_wrapper" \
                --arg post "$post_tool_wrapper" '
                .hooks.PreToolUse = [{"matcher":"Bash","hooks":[{"type":"command","command":$pre,"timeout":10}]}] |
                .hooks.PostToolUse = [{"matcher":"Bash","hooks":[{"type":"command","command":$post,"timeout":10}]}]
            ' "$CODEX_SETTINGS_FILE" > "$CODEX_SETTINGS_FILE.tmp"
            mv "$CODEX_SETTINGS_FILE.tmp" "$CODEX_SETTINGS_FILE"
            log "✓ Merged MCP hooks (PreToolUse/PostToolUse) into hooks.json"
        else
            cat > "$CODEX_SETTINGS_FILE" <<EOF
{
    "hooks": {
        "PreToolUse": [{"matcher":"Bash","hooks":[{"type":"command","command":"$pre_tool_wrapper","timeout":10}]}],
        "PostToolUse": [{"matcher":"Bash","hooks":[{"type":"command","command":"$post_tool_wrapper","timeout":10}]}]
    }
}
EOF
            log "✓ Created hooks.json with MCP hooks (no jq available for merge)"
        fi
    else
        cat > "$CODEX_SETTINGS_FILE" <<EOF
{
    "hooks": {
        "PreToolUse": [{"matcher":"Bash","hooks":[{"type":"command","command":"$pre_tool_wrapper","timeout":10}]}],
        "PostToolUse": [{"matcher":"Bash","hooks":[{"type":"command","command":"$post_tool_wrapper","timeout":10}]}]
    }
}
EOF
        log "✓ Created hooks.json with MCP hooks"
    fi
}

# Main installation function
main() {
    log "Starting Codex CLI hook installation..."
    log "Target user home: $TARGET_USER_HOME"

    # Check if Codex CLI is installed
    if ! check_codex_installed; then
        log "Codex CLI not detected - skipping hook installation"
        return 0
    fi

    log "✓ Codex CLI detected"

    # Check if any hooks are enabled — bail early only when both are explicitly disabled
    if ! is_prompt_hooks_enabled && ! is_mcp_hooks_enabled; then
        log "Both ENABLE_PROMPT_HOOKS_CODEX and ENABLE_MCP_HOOKS_CODEX are set to false — skipping hook installation"
        return 0
    fi

    ensure_codex_hooks_feature_flag

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
    mkdir -p "$CODEX_HOOKS_DIR"
    log "✓ Created hooks directory: $CODEX_HOOKS_DIR"

    # -----------------------------------------------------------------------
    # Download shared utility files
    # -----------------------------------------------------------------------
    log "Downloading hook scripts from GitHub..."

    if ! download_file "$GITHUB_SHARED_BASE/akto_ingestion_utility.py" "$CODEX_HOOKS_DIR/akto_ingestion_utility.py"; then
        log_error "Failed to download akto_ingestion_utility.py"
        return 1
    fi
    chmod +x "$CODEX_HOOKS_DIR/akto_ingestion_utility.py"
    log "✓ Downloaded akto_ingestion_utility.py"

    if ! download_file "$GITHUB_RAW_BASE/akto_machine_id.py" "$CODEX_HOOKS_DIR/akto_machine_id.py"; then
        log_error "Failed to download akto_machine_id.py"
        return 1
    fi
    chmod +x "$CODEX_HOOKS_DIR/akto_machine_id.py"
    log "✓ Downloaded akto_machine_id.py"

    # Download observability hook script
    if ! download_file "$GITHUB_RAW_BASE/akto-hooks.py" "$CODEX_HOOKS_DIR/akto-hooks.py"; then
        log_error "Failed to download akto-hooks.py"
        return 1
    fi
    chmod +x "$CODEX_HOOKS_DIR/akto-hooks.py"
    log "✓ Downloaded akto-hooks.py"

    # Create akto-hook-wrapper.sh
    create_hook_wrapper "$INGESTION_URL" "$DEVICE_ID"

    # -----------------------------------------------------------------------
    # PROMPT HOOKS section (UserPromptSubmit / Stop)
    # -----------------------------------------------------------------------
    if is_prompt_hooks_enabled; then
        log "Installing prompt hooks (UserPromptSubmit/Stop)..."

        if ! download_file "$GITHUB_RAW_BASE/akto-validate-prompt.py" "$CODEX_HOOKS_DIR/akto-validate-prompt.py"; then
            log_error "Failed to download akto-validate-prompt.py"
            return 1
        fi
        log "✓ Downloaded akto-validate-prompt.py"

        if ! download_file "$GITHUB_RAW_BASE/akto-validate-response.py" "$CODEX_HOOKS_DIR/akto-validate-response.py"; then
            log_error "Failed to download akto-validate-response.py"
            return 1
        fi
        log "✓ Downloaded akto-validate-response.py"

        chmod +x "$CODEX_HOOKS_DIR/akto-validate-prompt.py"
        chmod +x "$CODEX_HOOKS_DIR/akto-validate-response.py"

        create_prompt_wrapper "prompt" "$INGESTION_URL" "$DEVICE_ID"
        log "✓ Created akto-validate-prompt-wrapper.sh"

        create_prompt_wrapper "response" "$INGESTION_URL" "$DEVICE_ID"
        log "✓ Created akto-validate-response-wrapper.sh"

        if ! is_enterprise_mode; then
            update_codex_settings
            log "✓ Updated hooks.json with prompt hooks"
        fi
    else
        log "ENABLE_PROMPT_HOOKS_CODEX not set — skipping prompt hooks"
    fi

    # -----------------------------------------------------------------------
    # MCP HOOKS section (PreToolUse / PostToolUse)
    # -----------------------------------------------------------------------
    if is_mcp_hooks_enabled; then
        if is_enterprise_mode; then
            # Enterprise: download scripts only — config goes into requirements.toml below
            install_mcp_scripts_only "$INGESTION_URL" "$DEVICE_ID"
            log "✓ MCP hook scripts downloaded"
        else
            install_mcp_hooks "$INGESTION_URL" "$DEVICE_ID"
            log "✓ MCP hooks installed"
        fi
    else
        log "ENABLE_MCP_HOOKS_CODEX not set — skipping MCP hooks"
    fi

    # -----------------------------------------------------------------------
    # CONFIG WRITE — enterprise writes requirements.toml (managed, auto-trusted)
    #                non-enterprise writes hooks.json (requires /hooks approval)
    # -----------------------------------------------------------------------
    if is_enterprise_mode; then
        log "Enterprise mode detected — writing managed hooks to requirements.toml"
        ensure_requirements_toml_flags
        write_enterprise_hooks
    else
        # Non-enterprise: update hooks.json with all registered hooks
        update_codex_settings
        log "✓ Updated hooks.json with hooks"
    fi

    # Ensure every .sh file in the hooks directory is executable.
    # Individual create_* functions already call chmod +x, but this catch-all
    # covers any file that was copied without execute bits (e.g. curl to FAT volume,
    # partial install, or manual copy).
    chmod +x "$CODEX_HOOKS_DIR"/*.sh 2>/dev/null || true

    # RTR runs as root — every path we just created/modified (hooks dir, hooks.json,
    # config.toml's codex_hooks flag) is root-owned by default, which locks the real
    # user out of their own files. Hand ownership back on exactly what we touched —
    # not the whole ~/.codex — so pre-existing codex-owned state (auth.json, sessions,
    # etc.) is never disturbed. macOS stat -f, falling back to GNU stat -c.
    REAL_USER="$(stat -f '%Su' "$TARGET_USER_HOME" 2>/dev/null || stat -c '%U' "$TARGET_USER_HOME" 2>/dev/null || echo "")"
    if [ -n "$REAL_USER" ] && [ "$REAL_USER" != "root" ]; then
        chown -R "$REAL_USER" "$CODEX_HOOKS_DIR" 2>/dev/null || true
        chown "$REAL_USER" "$CODEX_SETTINGS_FILE" "$CODEX_SETTINGS_FILE.backup" "$TARGET_USER_HOME/.codex/config.toml" 2>/dev/null || true
        log "✓ Set ownership to $REAL_USER"
    fi

    log ""
    log "=========================================="
    log "✅ Codex CLI hooks installed successfully!"
    log "=========================================="
    log ""
    log "Hooks location: $CODEX_HOOKS_DIR"
    log "Hooks config: $CODEX_SETTINGS_FILE (~/.codex/config.toml for codex_hooks flag)"
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

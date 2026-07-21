#!/bin/bash

# ========================================================================================
# Akto Endpoint Shield - Kiro CLI Hook Installer
# ========================================================================================
# Installs Akto guardrail hooks for kiro-cli (Amazon Q Developer CLI lineage) if detected.
# Downloads the latest hooks from GitHub and registers them in the kiro-cli agent config.
#
# Controlled by flags in config.env (Jamf/enterprise only - all default off):
#   ENABLE_PROMPT_HOOKS_KIRO_CLI=true  - installs the userPromptSubmit hook (fail-closed)
#   ENABLE_MCP_HOOKS_KIRO_CLI=true     - installs the preToolUse (fail-closed) + postToolUse hooks
#
# Unlike the Kiro IDE (whose Agent Hooks are automation-only), kiro-cli exposes true
# lifecycle hooks whose command can reject the action via a non-zero exit code.
# ========================================================================================

# Ensure common binary paths are available (RTR/remote execution uses minimal PATH)
export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$PATH"

set -e

GITHUB_RAW_BASE="https://raw.githubusercontent.com/akto-api-security/akto/master/apps/mcp-endpoint-shield/kiro-cli-hooks"
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

log()       { echo "[Kiro CLI Hooks] $1"; }
log_error() { echo "[Kiro CLI Hooks] ERROR: $1" >&2; }

install_for_user() {
    local user_home="$1"

    export HOME="$user_home"
    TARGET_USER_HOME="$user_home"
    KIRO_HOOKS_DIR="$user_home/.kiro/hooks/akto"
    KIRO_AGENTS_DIR="$user_home/.kiro/agents"
    CONFIG_FILE="$user_home/.akto-endpoint-shield/config/config.env"

    if main; then
        return 0
    else
        log_error "Installation failed for $user_home"
        return 1
    fi
}

# kiro-cli is detected by its binary or its on-disk footprint (settings/sessions).
check_kiro_cli_installed() {
    command -v kiro-cli >/dev/null 2>&1 && return 0
    command -v kiro >/dev/null 2>&1 && return 0
    [ -x "$HOME/.local/bin/kiro-cli" ] && return 0
    [ -f "$HOME/.kiro/settings/cli.json" ] && return 0
    [ -d "$HOME/.kiro/sessions/cli" ] && return 0
    return 1
}

# Flag helpers (env var wins, then config.env). Default off (opt-in), matching the Go binary.
_flag_enabled() {
    local name="$1" val
    val="$(eval echo "\$$name")"
    [ "$val" = "true" ]  && return 0
    [ "$val" = "false" ] && return 1
    if [ -f "$CONFIG_FILE" ]; then
        val=$(grep "^$name=" "$CONFIG_FILE" 2>/dev/null | cut -d= -f2-)
        [ "$val" = "true" ]  && return 0
        [ "$val" = "false" ] && return 1
    fi
    # Default: enabled when the flag is absent — matches every other installer in this
    # family (claude/cursor/gemini/codex/etc.) and the dashboard, which never sends these
    # flags at all, so "absent" must mean "install it" rather than "skip it".
    return 0
}
is_prompt_hooks_enabled() { _flag_enabled "ENABLE_PROMPT_HOOKS_KIRO_CLI"; }
is_mcp_hooks_enabled()    { _flag_enabled "ENABLE_MCP_HOOKS_KIRO_CLI"; }

get_ingestion_url() {
    [ -n "$AKTO_API_BASE_URL" ]       && { echo "$AKTO_API_BASE_URL"; return 0; }
    [ -n "$AKTO_DATA_INGESTION_URL" ] && { echo "$AKTO_DATA_INGESTION_URL"; return 0; }
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
    [ -n "$AKTO_API_TOKEN" ] && { echo "$AKTO_API_TOKEN"; return 0; }
    if [ -f "$CONFIG_FILE" ]; then
        grep "^AKTO_API_TOKEN=" "$CONFIG_FILE" 2>/dev/null | cut -d= -f2-
    fi
}

# Device label matching Go's GetDeviceLabel(): "{ComputerName}-{first8ofMachineID}".
generate_device_label() {
    local device_name
    device_name=$(scutil --get ComputerName 2>/dev/null | tr ' ' '-')
    [ -z "$device_name" ] && device_name=$(hostname 2>/dev/null | sed 's/\.local$//' | tr ' ' '-')

    local machine_id=""
    if command -v ioreg >/dev/null 2>&1; then
        local uuid
        uuid=$(ioreg -rd1 -c IOPlatformExpertDevice 2>/dev/null | grep IOPlatformUUID | awk -F'"' '{print $4}')
        [ -n "$uuid" ] && machine_id=$(echo "$uuid" | tr -d '-' | tr '[:upper:]' '[:lower:]')
    fi
    local short_id="${machine_id:0:8}"
    if [ -n "$device_name" ] && [ -n "$short_id" ]; then echo "${device_name}-${short_id}";
    elif [ -n "$device_name" ]; then echo "$device_name";
    else echo "unknown-device"; fi
}

# Live flag guard: sources config.env each run and no-ops (exit 0 = allow) when the flag is off.
_inject_flag_guard() {
    local _wf="$1" _flag="$2" _guarded
    _guarded=$(mktemp)
    {
        head -1 "$_wf"
        cat <<GUARD
# --- Akto live flag guard (injected by Akto Endpoint Shield installer) ---
_akto_cfg="\$HOME/.akto-endpoint-shield/config/config.env"
[ -f "\$_akto_cfg" ] && source "\$_akto_cfg"
[ "\$$_flag" = "false" ] && exit 0
GUARD
        tail -n +2 "$_wf"
    } > "$_guarded"
    mv "$_guarded" "$_wf"
    chmod +x "$_wf"
}

download_file() {
    local url="$1" dest="$2"
    if command -v curl >/dev/null 2>&1; then
        curl -fsSL -H "Cache-Control: no-cache" "$url" -o "$dest" 2>/dev/null; return $?
    elif command -v wget >/dev/null 2>&1; then
        wget -q --no-cache "$url" -O "$dest" 2>/dev/null; return $?
    fi
    log_error "Neither curl nor wget available"; return 1
}

# Download a wrapper template and substitute placeholders + inject the flag guard.
create_wrapper() {
    local name="$1" ingestion_url="$2" token="$3" device_id="$4" flag="$5"
    local wrapper_file="$KIRO_HOOKS_DIR/akto-validate-${name}-wrapper.sh"
    if download_file "$GITHUB_RAW_BASE/akto-validate-${name}-wrapper.sh" "$wrapper_file.tmp"; then
        sed -e "s|{{AKTO_DATA_INGESTION_URL}}|$ingestion_url|g" \
            -e "s|{{AKTO_API_TOKEN}}|$token|g" \
            -e "s|{{DEVICE_ID}}|$device_id|g" \
            "$wrapper_file.tmp" > "$wrapper_file"
        rm -f "$wrapper_file.tmp"
        chmod +x "$wrapper_file"
        _inject_flag_guard "$wrapper_file" "$flag"
        log "Created akto-validate-${name}-wrapper.sh"
    else
        log_error "Failed to download akto-validate-${name}-wrapper.sh"
    fi
}

# Merge the Akto hooks block into every kiro-cli agent config found in the global
# agent dir. Requires jq; without it we log and skip (non-fatal).
update_kiro_agent_hooks() {
    local prompt_wf="$KIRO_HOOKS_DIR/akto-validate-prompt-wrapper.sh"
    local pretool_wf="$KIRO_HOOKS_DIR/akto-validate-pre-tool-wrapper.sh"
    local posttool_wf="$KIRO_HOOKS_DIR/akto-validate-post-tool-wrapper.sh"

    # Only reference wrappers that actually exist on disk — if a hook type was disabled
    # (or its download failed) create_wrapper never wrote the file, and referencing a
    # missing wrapper here would leave kiro-cli invoking a command that doesn't exist.
    local hooks_json="{"
    local first=true
    if [ -f "$prompt_wf" ]; then
        [ "$first" = true ] && first=false || hooks_json="$hooks_json,"
        hooks_json="$hooks_json\"userPromptSubmit\":[{\"command\":\"bash $prompt_wf\"}]"
    fi
    if [ -f "$pretool_wf" ]; then
        [ "$first" = true ] && first=false || hooks_json="$hooks_json,"
        hooks_json="$hooks_json\"preToolUse\":[{\"matcher\":\"*\",\"command\":\"bash $pretool_wf\"}]"
    fi
    if [ -f "$posttool_wf" ]; then
        [ "$first" = true ] && first=false || hooks_json="$hooks_json,"
        hooks_json="$hooks_json\"postToolUse\":[{\"matcher\":\"*\",\"command\":\"bash $posttool_wf\"}]"
    fi
    hooks_json="$hooks_json}"

    if [ "$first" = true ]; then
        log "No Akto wrapper scripts were created — skipping agent config merge."
        return 0
    fi

    if ! command -v jq >/dev/null 2>&1; then
        log "jq not available - cannot merge hooks into agent config automatically."
        log "Add the hooks block from agent-hooks.example.json to your kiro-cli agent, or run 'kiro-cli agent edit'."
        return 0
    fi

    mkdir -p "$KIRO_AGENTS_DIR"
    # find + plain-pipe loop instead of a glob array — "shopt -s nullglob" is bash-only (RTR
    # executes these scripts under zsh regardless of the #!/bin/bash shebang, where shopt
    # doesn't exist at all), and a plain pipe (not `< <(...)` process substitution) matches the
    # defensive pattern already used in scan_installed_apps.sh's find_files() for RTR shells.
    local found_marker="/tmp/.akto_kiro_agents_found_$$"
    rm -f "$found_marker"
    find "$KIRO_AGENTS_DIR" -maxdepth 1 -name '*.json' -type f 2>/dev/null | while IFS= read -r f; do
        : > "$found_marker"
        cp "$f" "$f.backup"
        jq --argjson h "$hooks_json" '.hooks = ((.hooks // {}) + $h)' "$f" > "$f.tmp" && mv "$f.tmp" "$f"
        log "Merged Akto hooks into $(basename "$f")"
    done

    if [ ! -f "$found_marker" ]; then
        echo "{\"name\":\"akto-guardrails\",\"hooks\":$hooks_json}" > "$KIRO_AGENTS_DIR/akto-guardrails.json"
        log "Created default agent akto-guardrails.json (launch: kiro-cli chat --agent akto-guardrails)"
    fi
    rm -f "$found_marker"
}

main() {
    log "Starting Kiro CLI hook installation..."
    log "Target user home: $TARGET_USER_HOME"
    if ! check_kiro_cli_installed; then
        log "kiro-cli not detected - skipping hook installation"
        return 0
    fi
    log "kiro-cli detected"

    INGESTION_URL=$(get_ingestion_url)
    if [ -z "$INGESTION_URL" ]; then
        log_error "AKTO_API_BASE_URL is not set - cannot install hooks."
        return 1
    fi
    API_TOKEN=$(get_api_token)
    DEVICE_ID=$(generate_device_label)
    log "Device label: $DEVICE_ID"

    mkdir -p "$KIRO_HOOKS_DIR"

    download_file "$GITHUB_SHARED_BASE/akto_ingestion_utility.py" "$KIRO_HOOKS_DIR/akto_ingestion_utility.py" \
        && chmod +x "$KIRO_HOOKS_DIR/akto_ingestion_utility.py" && log "downloaded akto_ingestion_utility.py" \
        || { log_error "Failed to download akto_ingestion_utility.py"; return 1; }
    download_file "$GITHUB_RAW_BASE/akto_machine_id.py" "$KIRO_HOOKS_DIR/akto_machine_id.py" \
        && chmod +x "$KIRO_HOOKS_DIR/akto_machine_id.py" && log "downloaded akto_machine_id.py" \
        || { log_error "Failed to download akto_machine_id.py"; return 1; }
    download_file "$GITHUB_RAW_BASE/akto-hooks.py" "$KIRO_HOOKS_DIR/akto-hooks.py" \
        && chmod +x "$KIRO_HOOKS_DIR/akto-hooks.py" && log "downloaded akto-hooks.py" \
        || { log_error "Failed to download akto-hooks.py"; return 1; }

    # Prompt guardrail (userPromptSubmit)
    if is_prompt_hooks_enabled; then
        log "ENABLE_PROMPT_HOOKS_KIRO_CLI=true - installing userPromptSubmit hook"
        create_wrapper "prompt" "$INGESTION_URL" "$API_TOKEN" "$DEVICE_ID" "ENABLE_PROMPT_HOOKS_KIRO_CLI"
    else
        log "ENABLE_PROMPT_HOOKS_KIRO_CLI not set - skipping prompt hook"
    fi

    # Tool guardrails (preToolUse fail-closed + postToolUse ingestion)
    if is_mcp_hooks_enabled; then
        log "ENABLE_MCP_HOOKS_KIRO_CLI=true - installing preToolUse/postToolUse hooks"
        create_wrapper "pre-tool"  "$INGESTION_URL" "$API_TOKEN" "$DEVICE_ID" "ENABLE_MCP_HOOKS_KIRO_CLI"
        create_wrapper "post-tool" "$INGESTION_URL" "$API_TOKEN" "$DEVICE_ID" "ENABLE_MCP_HOOKS_KIRO_CLI"
    else
        log "ENABLE_MCP_HOOKS_KIRO_CLI not set - skipping tool hooks"
    fi

    update_kiro_agent_hooks

    # RTR runs as root — KIRO_HOOKS_DIR (~/.kiro/hooks/akto) is TWO new levels deep if
    # ~/.kiro/hooks didn't already exist (hooks/, then hooks/akto/), and every level mkdir -p
    # creates is root-owned. Chowning only the leaf dir leaves its root-owned parent(s) blocking
    # access to everything beneath them, so walk up from each leaf to the topmost ancestor that
    # didn't already belong to the real user, then chown from there down (same pattern as
    # install_vscode_copilot_hooks.sh's chown_up_to_owned). macOS stat -f, falling back to GNU
    # stat -c.
    REAL_USER="$(stat -f '%Su' "$TARGET_USER_HOME" 2>/dev/null || stat -c '%U' "$TARGET_USER_HOME" 2>/dev/null || echo "")"
    if [ -n "$REAL_USER" ] && [ "$REAL_USER" != "root" ]; then
        for leaf in "$KIRO_HOOKS_DIR" "$KIRO_AGENTS_DIR"; do
            chown_root="$leaf"
            while [ "$(dirname "$chown_root")" != "$TARGET_USER_HOME" ] && [ "$(dirname "$chown_root")" != "/" ]; do
                parent="$(dirname "$chown_root")"
                owner="$(stat -f '%Su' "$parent" 2>/dev/null || stat -c '%U' "$parent" 2>/dev/null || echo "")"
                [ "$owner" = "$REAL_USER" ] && break
                chown_root="$parent"
            done
            chown -R "$REAL_USER" "$chown_root" 2>/dev/null || true
        done
        log "Set ownership to $REAL_USER"
    fi

    log "Kiro CLI hooks installed. Hooks: $KIRO_HOOKS_DIR"
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

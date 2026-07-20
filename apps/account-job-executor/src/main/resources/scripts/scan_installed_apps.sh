#!/bin/bash
# Installed AI-Agent Application Discovery Script (macOS/Linux)
# Mirrors: mcp-endpoint-shield/mcp/agent_detector.go (detectMacOSAgents / detectLinuxAgents)
#
# Replaces the CrowdStrike Falcon Discover "software inventory" API path when the
# Discover module/Assets scope isn't available on the API client — this scans the
# filesystem directly for known AI-agent app bundles, CLI binaries, and config-dir
# footprints, the same signals the native Go endpoint-shield agent uses.
#
# Requirements: POSIX shell (bash/zsh)
# Optional: mdfind (macOS Spotlight, used as a fast first pass for .app bundles)

# CrowdStrike RTR executes scripts in a restricted environment where common coreutils
# (date, stat, awk, sed, tr, basename, dirname, tail — confirmed via RTR stderr capture)
# are "command not found" regardless of $PATH — this isn't a PATH problem, the binaries
# just aren't present in that sandbox. Avoid all of them; use only shell builtins.
export PATH="/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:/opt/homebrew/bin:/opt/homebrew/sbin:$PATH"

log_debug() {
    echo "[akto-scan] $*" >&2
}

log_debug "=== Installed Apps Discovery Script Started ==="
log_debug "Hostname: $(hostname)"
log_debug "OS: $(uname -s)"

cleanup() {
    if [ "$JSON_STARTED" = true ] && [ "$JSON_CLOSED" = false ]; then
        echo ""
        echo "  ]"
        echo "}"
    fi
    log_debug "=== Installed Apps Discovery Script Ended ==="
}
trap cleanup EXIT

JSON_STARTED=false
JSON_CLOSED=false
FIRST=true

echo "{"
echo "  \"hostname\": \"$(hostname)\","
echo "  \"os\": \"$(uname -s)\","
echo "  \"apps_found\": ["
JSON_STARTED=true

add_app() {
    local agent="$1"
    local path="$2"
    local method="$3"

    if [ "$FIRST" = true ]; then
        FIRST=false
    else
        echo ","
    fi
    log_debug "Found app: agent=$agent path=$path method=$method"
    printf '    {"agent":"%s","path":"%s","detection_method":"%s"}' "$agent" "$path" "$method"
}

# macOS: fast Spotlight lookup for a .app bundle; falls back to hardcoded paths by caller.
find_app_with_mdfind() {
    local app_name="$1"
    if ! command -v mdfind >/dev/null 2>&1; then
        return 1
    fi
    local result=""
    # First line only, without `head` (not reliably available under RTR, and `< <(...)`
    # process substitution is bash-only — RTR appears to ignore the #!/bin/bash shebang and
    # run scripts under a plainer POSIX sh, so avoid both).
    while IFS= read -r line; do
        if [ -z "$result" ]; then
            result="$line"
        fi
    done <<EOF
$(mdfind "kMDItemContentType == 'com.apple.application-bundle' && kMDItemFSName == '${app_name}.app'" 2>/dev/null)
EOF
    if [ -n "$result" ]; then
        echo "$result"
        return 0
    fi
    return 1
}

check_app_bundle() {
    local agent="$1"
    local app_name="$2"
    shift 2
    local fallback_paths=("$@")

    local found_path
    found_path=$(find_app_with_mdfind "$app_name")
    if [ -n "$found_path" ]; then
        add_app "$agent" "$found_path" "mdfind"
        return 0
    fi

    for p in "${fallback_paths[@]}"; do
        if [ -e "$p" ]; then
            add_app "$agent" "$p" "path"
            return 0
        fi
    done
    return 1
}

check_binary_paths() {
    local agent="$1"
    shift
    local paths=("$@")
    for p in "${paths[@]}"; do
        if [ -f "$p" ] || [ -x "$p" ]; then
            add_app "$agent" "$p" "path"
            return 0
        fi
    done
    return 1
}

check_path_lookup() {
    local agent="$1"
    local bin="$2"
    local resolved
    resolved=$(command -v "$bin" 2>/dev/null)
    if [ -n "$resolved" ]; then
        add_app "$agent" "$resolved" "PATH"
        return 0
    fi
    return 1
}

check_dir_exists() {
    local agent="$1"
    local dir="$2"
    if [ -d "$dir" ]; then
        add_app "$agent" "$dir" "config-dir"
        return 0
    fi
    return 1
}

# Explicit if/elif chain (not ||/&& short-circuiting) so a binary/PATH hit never
# also falls through to the footprint check — that would emit a duplicate record.
check_kiro_cli() {
    local user_home="$1"
    shift
    local binary_paths=("$@")

    if check_binary_paths "kirocli" "${binary_paths[@]}"; then
        return 0
    elif check_path_lookup "kirocli" "kiro-cli"; then
        return 0
    elif check_path_lookup "kirocli" "kiro"; then
        return 0
    elif [ -f "$user_home/.kiro/settings/cli.json" ] || [ -d "$user_home/.kiro/sessions/cli" ]; then
        add_app "kirocli" "$user_home/.kiro" "footprint"
        return 0
    fi
    return 1
}

# OpenClaw (formerly Clawdbot) — binary is "openclaw", installed via one of:
#   - curl install.sh -> ~/.local/bin/openclaw
#   - npm install -g openclaw -> npm global bin dir (covered by PATH lookup)
#   - install-cli.sh prefix installer, default prefix ~/.openclaw -> ~/.openclaw/bin/openclaw
#   - Homebrew cask (macOS menu-bar app) or tap formula (CLI)
# Config footprint (~/.openclaw/openclaw.json) is the same path the guardrails installer
# already assumes (install_openclaw_guardrails_sentinelone.sh) — used here as a last-resort
# signal for installs where the binary isn't on this user's PATH.
check_openclaw() {
    local user_home="$1"
    shift
    local binary_paths=("$@")

    if check_binary_paths "openclaw" "${binary_paths[@]}"; then
        return 0
    elif check_path_lookup "openclaw" "openclaw"; then
        return 0
    elif [ -f "$user_home/.openclaw/openclaw.json" ]; then
        add_app "openclaw" "$user_home/.openclaw" "footprint"
        return 0
    fi
    return 1
}

OS_TYPE="$(uname -s)"

# Get all user home directories (same enumeration as scan_mcp_configs.sh / scan_skills.sh)
USER_HOMES=""
if [ "$OS_TYPE" = "Darwin" ]; then
    USER_HOMES=$(ls -d /Users/* 2>/dev/null | grep -v "/Users/Shared" | grep -v "/Users/Guest")
elif [ "$OS_TYPE" = "Linux" ]; then
    USER_HOMES=$(ls -d /home/* /root 2>/dev/null)
fi

log_debug "Found user homes: $USER_HOMES"

for user_home in $USER_HOMES; do
    log_debug "=== Processing user: $user_home ==="

    if [ "$OS_TYPE" = "Darwin" ]; then
        # ── App bundles (mdfind first, hardcoded fallback) ──────────────────
        check_app_bundle "cursor" "Cursor" \
            "/Applications/Cursor.app" "$user_home/Applications/Cursor.app"
        check_app_bundle "vscode" "Visual Studio Code" \
            "/Applications/Visual Studio Code.app" "$user_home/Applications/Visual Studio Code.app"
        check_app_bundle "windsurf" "Windsurf" \
            "/Applications/Windsurf.app" "$user_home/Applications/Windsurf.app"
        check_app_bundle "claude-desktop" "Claude" \
            "/Applications/Claude.app" "$user_home/Applications/Claude.app"
        check_app_bundle "antigravity" "Antigravity" \
            "/Applications/Antigravity.app" "$user_home/Applications/Antigravity.app"
        check_app_bundle "codex" "Codex" \
            "/Applications/Codex.app" "$user_home/Applications/Codex.app"
        check_app_bundle "kiroide" "Kiro" \
            "/Applications/Kiro.app" "$user_home/Applications/Kiro.app"
        # macOS menu-bar companion app (Electron) — distinct from the openclaw CLI below,
        # same as claude-desktop vs claude-cli-user.
        check_app_bundle "openclaw-desktop" "OpenClaw" \
            "/Applications/OpenClaw.app" "$user_home/Applications/OpenClaw.app"

        # ── Claude CLI ───────────────────────────────────────────────────────
        check_binary_paths "claude-cli-user" \
            "/opt/homebrew/bin/claude" "/usr/local/bin/claude" "$user_home/.local/bin/claude" \
            || check_path_lookup "claude-cli-user" "claude"

        # ── GitHub Copilot config dir ────────────────────────────────────────
        check_dir_exists "copilot" "$user_home/.copilot"

        # ── Codex CLI ─────────────────────────────────────────────────────────
        check_binary_paths "codex" \
            "/opt/homebrew/bin/codex" "/usr/local/bin/codex" "$user_home/.local/bin/codex" \
            || check_path_lookup "codex" "codex"

        # ── Ollama ────────────────────────────────────────────────────────────
        check_binary_paths "ollama" \
            "/opt/homebrew/bin/ollama" "/usr/local/bin/ollama" "$user_home/.local/bin/ollama" \
            || check_path_lookup "ollama" "ollama"

        # ── Kiro CLI (binary, PATH, or on-disk footprint — mutually exclusive) ──
        check_kiro_cli "$user_home" \
            "/opt/homebrew/bin/kiro-cli" "/usr/local/bin/kiro-cli" "$user_home/.local/bin/kiro-cli" \
            "/opt/homebrew/bin/kiro" "/usr/local/bin/kiro" "$user_home/.local/bin/kiro"

        # ── OpenClaw CLI (binary, PATH, or config footprint — mutually exclusive) ──
        check_openclaw "$user_home" \
            "$user_home/.local/bin/openclaw" "$user_home/.openclaw/bin/openclaw" \
            "/opt/homebrew/bin/openclaw" "/usr/local/bin/openclaw"

    elif [ "$OS_TYPE" = "Linux" ]; then
        # ── Editor/desktop directory signals (matches detectLinuxAgents) ─────
        check_dir_exists "cursor" "$user_home/.local/share/cursor"
        check_dir_exists "vscode" "$user_home/.vscode"
        check_dir_exists "windsurf" "$user_home/.windsurf"
        check_dir_exists "antigravity" "$user_home/.antigravity"
        check_dir_exists "codex" "$user_home/.local/share/codex"
        check_dir_exists "kiroide" "$user_home/.local/share/kiro"

        # ── Claude CLI ───────────────────────────────────────────────────────
        check_binary_paths "claude-cli-user" \
            "/usr/local/bin/claude" "$user_home/.local/bin/claude" \
            || check_path_lookup "claude-cli-user" "claude"

        check_dir_exists "copilot" "$user_home/.copilot"

        check_binary_paths "codex" \
            "/usr/local/bin/codex" "$user_home/.local/bin/codex" \
            || check_path_lookup "codex" "codex"

        check_binary_paths "ollama" \
            "/usr/local/bin/ollama" "$user_home/.local/bin/ollama" \
            || check_path_lookup "ollama" "ollama"

        check_kiro_cli "$user_home" \
            "/usr/local/bin/kiro-cli" "$user_home/.local/bin/kiro-cli" \
            "/usr/local/bin/kiro" "$user_home/.local/bin/kiro"

        check_openclaw "$user_home" \
            "$user_home/.local/bin/openclaw" "$user_home/.openclaw/bin/openclaw" \
            "/usr/local/bin/openclaw"
    fi
done

echo ""
echo "  ]"
echo "}"
JSON_CLOSED=true

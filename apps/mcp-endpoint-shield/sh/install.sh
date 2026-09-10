#!/bin/bash
# install.sh — install the Akto shell hooks for every detected agent.
#
#   bash install.sh [AKTO_DATA_INGESTION_URL=...] [AKTO_API_TOKEN=...] [DEVICE_ID=...]
#                   [--dry-run] [--only <connector>] [--uninstall]
#
# Copies the handler to ~/.akto/hooks, writes ~/.akto/hooks.env, and merges the
# hook block into each detected agent's config.
#
# Unlike the Python installers this needs no jq: the merge is done by
# lib/setkey.awk, which rewrites one top-level key and copies every other key
# through as its original bytes. The jq-less path in the old installers replaced
# the whole settings file and lost the user's other settings.
#
# Idempotent. Every config it edits is backed up next to the original first.

set -u
SRC_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEST="${AKTO_HOOKS_DIR:-$HOME/.akto/hooks}"
ENV_FILE="${AKTO_CONFIG_FILE:-$HOME/.akto/hooks.env}"
SETKEY="$SRC_DIR/lib/setkey.awk"
JSONAWK="$SRC_DIR/lib/json.awk"

DRY_RUN=false
UNINSTALL=false
ONLY=""
AKTO_DATA_INGESTION_URL="${AKTO_DATA_INGESTION_URL:-}"
AKTO_API_TOKEN="${AKTO_API_TOKEN:-}"
DEVICE_ID="${DEVICE_ID:-}"
MODE="${MODE:-atlas}"

for a in "$@"; do
    case "$a" in
        --dry-run)   DRY_RUN=true ;;
        --uninstall) UNINSTALL=true ;;
        --only)      ;;                      # value consumed below
        --only=*)    ONLY="${a#--only=}" ;;
        AKTO_DATA_INGESTION_URL=*) AKTO_DATA_INGESTION_URL="${a#*=}" ;;
        AKTO_API_TOKEN=*)          AKTO_API_TOKEN="${a#*=}" ;;
        DEVICE_ID=*)               DEVICE_ID="${a#*=}" ;;
        MODE=*)                    MODE="${a#*=}" ;;
        -h|--help)
            sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'
            exit 0 ;;
    esac
done

log()  { printf '[akto-hooks] %s\n' "$1"; }
warn() { printf '[akto-hooks] %s\n' "$1" >&2; }

run() { # honour --dry-run for anything that touches disk
    if [ "$DRY_RUN" = true ]; then printf '[dry-run] %s\n' "$*"; else "$@"; fi
}

# ── Preflight ─────────────────────────────────────────────────────────────────

missing=""
for tool in awk curl; do
    command -v "$tool" >/dev/null 2>&1 || missing="$missing $tool"
done
if [ -n "$missing" ]; then
    warn "missing required base tools:$missing — cannot install"
    exit 1
fi
if ! command -v shasum >/dev/null 2>&1 && ! command -v sha256sum >/dev/null 2>&1 &&
   ! command -v openssl >/dev/null 2>&1; then
    warn "no shasum/sha256sum/openssl found; the warn-resubmit flow will use a weaker key"
fi

# ── Agent registry ────────────────────────────────────────────────────────────
# connector | probe path | config file | JSON key to set | config template
AGENTS="
claude_code_cli|$HOME/.claude|$HOME/.claude/settings.json|hooks|claude-settings.json
cursor|$HOME/.cursor|$HOME/.cursor/hooks.json|hooks|cursor-hooks.json
codex_cli|$HOME/.codex|$HOME/.codex/hooks.json|hooks|codex-hooks.json
gemini_cli|$HOME/.gemini|$HOME/.gemini/settings.json|hooks|gemini-settings.json
vscode|$HOME/.vscode/copilot|$HOME/.vscode/copilot/hooks.json|hooks|vscode-copilot-hooks.json
kiro_cli|$HOME/.kiro|$HOME/.kiro/hooks.json|hooks|kiro-agent-hooks.json
"

# ── Install ───────────────────────────────────────────────────────────────────

install_files() {
    log "installing handler to $DEST"
    run mkdir -p "$DEST/lib" "$DEST/ps"
    run cp "$SRC_DIR/akto-hook.sh" "$DEST/akto-hook.sh"
    run cp "$SRC_DIR/lib/json.awk" "$SRC_DIR/lib/setkey.awk" \
           "$SRC_DIR/lib/akto_core.sh" "$SRC_DIR/lib/akto_adapters.sh" "$DEST/lib/"
    run cp "$SRC_DIR/ps/akto-hook.ps1" "$DEST/ps/akto-hook.ps1"
    run chmod +x "$DEST/akto-hook.sh"
}

write_env() {
    [ -n "$AKTO_DATA_INGESTION_URL" ] || {
        warn "AKTO_DATA_INGESTION_URL not given — hooks will fail open until $ENV_FILE sets it"
    }
    log "writing $ENV_FILE"
    if [ "$DRY_RUN" = true ]; then printf '[dry-run] write %s\n' "$ENV_FILE"; return 0; fi
    mkdir -p "$(dirname "$ENV_FILE")"
    umask 077
    {
        echo "# Akto hook configuration. A real environment variable overrides any line here."
        echo "AKTO_DATA_INGESTION_URL=$AKTO_DATA_INGESTION_URL"
        [ -n "$AKTO_API_TOKEN" ] && echo "AKTO_API_TOKEN=$AKTO_API_TOKEN"
        [ -n "$DEVICE_ID" ] && echo "DEVICE_ID=$DEVICE_ID"
        echo "MODE=$MODE"
        echo "AKTO_SYNC_MODE=true"
        echo "AKTO_TIMEOUT=5"
        echo "LOG_LEVEL=INFO"
        echo "LOG_PAYLOADS=false"
    } >"$ENV_FILE"
    chmod 600 "$ENV_FILE" 2>/dev/null
}

# Render a config template, pointing every command at the installed handler.
render_template() {
    sed -e "s|~/.akto/hooks/akto-hook.sh|$DEST/akto-hook.sh|g" \
        -e "s|%USERPROFILE%\\\\.akto\\\\hooks\\\\akto-hook.ps1|$DEST/ps/akto-hook.ps1|g" \
        "$SRC_DIR/config/$1"
}

merge_config() { # merge_config <config file> <key> <template>
    local cfg="$1" key="$2" tmpl="$3"
    local valfile merged
    valfile="$(mktemp)"
    merged="$(mktemp)"
    # The template is a whole config document; the value we set is its `key`.
    render_template "$tmpl" | awk -v mode=get -v path="$key" -f "$JSONAWK" >"$valfile" 2>/dev/null
    if [ ! -s "$valfile" ]; then
        warn "  could not read '$key' out of template $tmpl — skipping"
        rm -f "$valfile" "$merged"
        return 1
    fi

    if [ "$DRY_RUN" = true ]; then
        printf '[dry-run] merge %s into %s\n' "$key" "$cfg"
        rm -f "$valfile" "$merged"
        return 0
    fi

    mkdir -p "$(dirname "$cfg")"
    if [ -s "$cfg" ]; then
        cp "$cfg" "$cfg.akto-backup.$(date +%Y%m%d%H%M%S)"
        if ! awk -v key="$key" -v valfile="$valfile" -f "$SETKEY" <"$cfg" >"$merged" 2>/dev/null ||
           [ ! -s "$merged" ]; then
            warn "  $cfg is not parseable JSON — left untouched"
            rm -f "$valfile" "$merged"
            return 1
        fi
    else
        printf '{"%s":%s}\n' "$key" "$(cat "$valfile")" >"$merged"
    fi
    mv "$merged" "$cfg"
    rm -f "$valfile"
    return 0
}

uninstall_config() { # uninstall_config <config file> <key>
    local cfg="$1" key="$2" valfile merged
    [ -s "$cfg" ] || return 0
    valfile="$(mktemp)"; merged="$(mktemp)"
    echo '{}' >"$valfile"
    if [ "$DRY_RUN" = true ]; then
        printf '[dry-run] clear %s in %s\n' "$key" "$cfg"
    elif awk -v key="$key" -v valfile="$valfile" -f "$SETKEY" <"$cfg" >"$merged" 2>/dev/null && [ -s "$merged" ]; then
        cp "$cfg" "$cfg.akto-backup.$(date +%Y%m%d%H%M%S)"
        mv "$merged" "$cfg"
        log "  cleared hooks in $cfg"
    fi
    rm -f "$valfile" "$merged"
}

# ── Main ──────────────────────────────────────────────────────────────────────

if [ "$UNINSTALL" = true ]; then
    log "uninstalling"
    printf '%s\n' "$AGENTS" | while IFS='|' read -r connector probe cfg key tmpl; do
        [ -n "$connector" ] || continue
        [ -n "$ONLY" ] && [ "$ONLY" != "$connector" ] && continue
        uninstall_config "$cfg" "$key"
    done
    [ "$DRY_RUN" = true ] || rm -rf "$DEST"
    log "done (config backups kept; $ENV_FILE left in place)"
    exit 0
fi

install_files
write_env

found=0
printf '%s\n' "$AGENTS" | while IFS='|' read -r connector probe cfg key tmpl; do
    [ -n "$connector" ] || continue
    [ -n "$ONLY" ] && [ "$ONLY" != "$connector" ] && continue
    if [ ! -d "$probe" ]; then
        continue
    fi
    log "detected $connector"
    if merge_config "$cfg" "$key" "$tmpl"; then
        log "  wired $cfg"
    fi
    found=$((found + 1))
done

log "GitHub Copilot CLI reads hooks.json from the working directory; copy"
log "  config/github-copilot-hooks.json into <repo>/.github/hooks.json to enable it."
log "done. Verify with:  echo '{\"prompt\":\"hi\"}' | $DEST/akto-hook.sh claude_code_cli UserPromptSubmit"

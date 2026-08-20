#!/bin/bash
# akto_core.sh — shared runtime for the Akto shell hooks.
#
# Dependencies: bash, awk, curl, and one of shasum/sha256sum/openssl. All are base
# system tools on macOS and Linux; nothing is installed. Windows is served by the
# PowerShell twin in sh/ps/, not by this file.
#
# User-supplied content (prompts, tool arguments, assistant replies) is never
# decoded. It is read out of the hook's stdin as raw JSON text and spliced into the
# outgoing payload still encoded — see lib/json.awk for why that matters.

AKTO_LIB_DIR="${AKTO_LIB_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)}"
AKTO_JSON_AWK="$AKTO_LIB_DIR/json.awk"

# ── Config file ───────────────────────────────────────────────────────────────
# One KEY=VALUE file replaces the per-hook wrapper scripts, which existed only to
# export this same set of variables before exec'ing a handler. The agent's own
# config.env is read first (so ENABLE_* kill switches and a seeded DEVICE_ID
# apply), then the hook config. A real environment variable always wins, so a
# wrapper or an installer can still override any single value.

akto_load_config() {
    local f key value
    for f in "${AKTO_AGENT_CONFIG:-$HOME/.akto-endpoint-shield/config/config.env}" \
             "${AKTO_CONFIG_FILE:-$HOME/.akto/hooks.env}"; do
        [ -f "$f" ] || continue
        while IFS= read -r line || [ -n "$line" ]; do
            case "$line" in ''|'#'*) continue ;; esac
            key="${line%%=*}"
            value="${line#*=}"
            case "$key" in *[!A-Za-z0-9_]*|'') continue ;; esac
            value="${value%\"}"; value="${value#\"}"
            value="${value%\'}"; value="${value#\'}"
            # Real environment wins; the file only supplies defaults.
            [ -n "$(eval "printf '%s' \"\${$key:-}\"")" ] && continue
            export "$key=$value"
        done <"$f"
    done
}
akto_load_config

# ── Config ────────────────────────────────────────────────────────────────────

MODE="$(printf '%s' "${MODE:-argus}" | tr '[:upper:]' '[:lower:]')"
AKTO_DATA_INGESTION_URL="${AKTO_DATA_INGESTION_URL%/}"
AKTO_API_TOKEN="${AKTO_API_TOKEN:-}"
AKTO_TIMEOUT="${AKTO_TIMEOUT:-5}"
AKTO_SYNC_MODE="$(printf '%s' "${AKTO_SYNC_MODE:-true}" | tr '[:upper:]' '[:lower:]')"
AKTO_CONNECTOR="${AKTO_CONNECTOR:-}"
CONTEXT_SOURCE="${CONTEXT_SOURCE:-ENDPOINT}"
AKTO_INGEST_NON_MCP_TOOLS="$(printf '%s' "${AKTO_INGEST_NON_MCP_TOOLS:-false}" | tr '[:upper:]' '[:lower:]')"
MCP_INGEST_PATH="${MCP_INGEST_PATH:-/mcp}"
NON_MCP_TOOL_PATH_PREFIX="${NON_MCP_TOOL_PATH_PREFIX:-/tool}"
SSL_VERIFY="$(printf '%s' "${SSL_VERIFY:-true}" | tr '[:upper:]' '[:lower:]')"
SSL_CERT_PATH="${SSL_CERT_PATH:-}"
LOG_LEVEL="$(printf '%s' "${LOG_LEVEL:-INFO}" | tr '[:lower:]' '[:upper:]')"
LOG_PAYLOADS="$(printf '%s' "${LOG_PAYLOADS:-false}" | tr '[:upper:]' '[:lower:]')"

# Connector -> short tag used in the ai-agent tag, hook header and atlas host.
akto_connector_tag() {
    case "$1" in
        claude_code_cli) echo "claudecli" ;;
        cursor)          echo "cursor" ;;
        vscode)          echo "vscode" ;;
        gemini_cli)      echo "geminicli" ;;
        github)          echo "github" ;;
        codex_cli)       echo "codexcli" ;;
        kiro_cli)        echo "kirocli" ;;
        amp)             echo "amp" ;;
        opencode)        echo "opencode" ;;
        *)               echo "$1" ;;
    esac
}

akto_default_log_dir() {
    case "$1" in
        claude_code_cli) echo "$HOME/.claude/akto/logs" ;;
        cursor)          echo "$HOME/.cursor/akto/chat-logs" ;;
        gemini_cli)      echo "$HOME/.gemini/akto/chat-logs" ;;
        codex_cli)       echo "$HOME/.codex/akto/logs" ;;
        github)          echo "$HOME/akto/.github/akto/vscode/logs" ;;
        vscode)          echo "$HOME/.vscode/copilot/hooks/akto/logs" ;;
        opencode)        echo "$HOME/.config/opencode/akto/logs" ;;
        kiro_cli)        echo "$HOME/.kiro/akto/logs" ;;
        amp)             echo "$HOME/.config/amp/akto/logs" ;;
        *)               echo "$HOME/akto/$1-hooks/logs" ;;
    esac
}

TAG_NAME="$(akto_connector_tag "$AKTO_CONNECTOR")"
AKTO_CONNECTOR_VALUE="${AKTO_CONNECTOR_VALUE:-$TAG_NAME}"
HOOK_HEADER="x-${TAG_NAME}-hook"
LOG_DIR="${LOG_DIR:-$(akto_default_log_dir "$AKTO_CONNECTOR")}"
SESSION_STATE_PATH="$LOG_DIR/akto_session_state.json"

# ── Logging ───────────────────────────────────────────────────────────────────
# stdout is the hook's decision channel, so every log line goes to the file (and
# errors additionally to stderr, matching the Python hooks).

akto_log_init() {
    mkdir -p "$LOG_DIR" 2>/dev/null
    AKTO_LOG_FILE="$LOG_DIR/${1:-hook-executions}.log"
}

_log() { # _log <level> <message>
    [ -n "$AKTO_LOG_FILE" ] || return 0
    printf '%s - %s - %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$1" "$2" >>"$AKTO_LOG_FILE" 2>/dev/null
}
log_debug() { [ "$LOG_LEVEL" = "DEBUG" ] && _log DEBUG "$1"; return 0; }
log_info()  { _log INFO "$1"; }
log_warn()  { _log WARNING "$1"; }
log_error() { _log ERROR "$1"; printf 'Akto hook: %s\n' "$1" >&2; }

# ── JSON helpers ──────────────────────────────────────────────────────────────
# AKTO_INPUT holds the hook's stdin verbatim.

jget() { # jget <path> -> raw JSON value, empty + status 1 when absent
    printf '%s' "$AKTO_INPUT" | awk -v mode=get -v path="$1" -f "$AKTO_JSON_AWK" 2>/dev/null
}

jget_from() { # jget_from <json> <path>
    printf '%s' "$1" | awk -v mode=get -v path="$2" -f "$AKTO_JSON_AWK" 2>/dev/null
}

jstr() { # jstr <path> -> inner body of a JSON string, escapes intact ("" when absent)
    local v
    v="$(jget "$1")" || return 1
    case "$v" in
        '"'*) printf '%s' "$v" | awk -v mode=unquote -f "$AKTO_JSON_AWK" ;;
        *) printf '%s' "$v" ;;
    esac
}

jstr_from() { # jstr_from <json> <path>
    local v
    v="$(jget_from "$1" "$2")" || return 1
    case "$v" in
        '"'*) printf '%s' "$v" | awk -v mode=unquote -f "$AKTO_JSON_AWK" ;;
        *) printf '%s' "$v" ;;
    esac
}

jesc() { # jesc <raw text> -> JSON string body (no surrounding quotes)
    printf '%s' "$1" | awk -v mode=escape -f "$AKTO_JSON_AWK"
}

jembed() { # jembed <json text> -> that text escaped for embedding as a JSON string value
    printf '%s' "$1" | awk -v mode=escape -f "$AKTO_JSON_AWK"
}

sha256_of() {
    if command -v shasum >/dev/null 2>&1; then
        printf '%s' "$1" | shasum -a 256 | awk '{print $1}'
    elif command -v sha256sum >/dev/null 2>&1; then
        printf '%s' "$1" | sha256sum | awk '{print $1}'
    elif command -v openssl >/dev/null 2>&1; then
        printf '%s' "$1" | openssl dgst -sha256 | awk '{print $NF}'
    else
        # No digest tool: fall back to the literal input. The warn-resubmit map
        # still works (it only needs a stable key), it is just not compacted.
        printf '%s' "$1" | tr -c 'a-zA-Z0-9' '-'
    fi
}

# ── Device identity ───────────────────────────────────────────────────────────
# Mirrors utils.GetDeviceLabel() in the Go agent: the device name lowercased with
# every non-alphanumeric char replaced by '-', then '-' + first 8 chars of the
# machine id. DEVICE_ID from the wrapper wins when present.

akto_device_label() {
    if [ -n "$DEVICE_ID" ]; then printf '%s' "$DEVICE_ID"; return 0; fi
    local name id
    name="$(scutil --get ComputerName 2>/dev/null)"
    [ -z "$name" ] && name="$(hostname 2>/dev/null | sed 's/\.local$//')"
    name="$(printf '%s' "$name" | tr '[:upper:]' '[:lower:]' | sed 's/[^a-z0-9]/-/g')"

    id="$(ioreg -rd1 -c IOPlatformExpertDevice 2>/dev/null | grep IOPlatformUUID | awk -F'"' '{print $4}' | tr -d '-' | tr '[:upper:]' '[:lower:]')"
    if [ -z "$id" ] && [ -r /etc/machine-id ]; then id="$(tr -d '\n' </etc/machine-id | tr '[:upper:]' '[:lower:]')"; fi
    if [ -z "$id" ] && [ -r /var/lib/dbus/machine-id ]; then id="$(tr -d '\n' </var/lib/dbus/machine-id | tr '[:upper:]' '[:lower:]')"; fi
    if [ -z "$id" ] && command -v ifconfig >/dev/null 2>&1; then
        id="$(ifconfig en0 2>/dev/null | awk '/ether/{print $2}' | tr -d ':' | tr '[:upper:]' '[:lower:]')"
        [ -z "$id" ] && id="$(ifconfig eth0 2>/dev/null | awk '/ether/{print $2}' | tr -d ':' | tr '[:upper:]' '[:lower:]')"
    fi

    local short="${id:0:8}"
    if [ -n "$name" ] && [ -n "$short" ]; then printf '%s-%s' "$name" "$short"
    elif [ -n "$name" ]; then printf '%s' "$name"
    elif [ -n "$id" ]; then printf '%s' "$id"
    else printf 'unknown-device'
    fi
}

akto_username() {
    if [ -n "$SUDO_USER" ] && [ "$SUDO_USER" != "root" ]; then printf '%s' "$SUDO_USER"; return 0; fi
    local u
    u="$(id -un 2>/dev/null)"
    [ -z "$u" ] && u="${USER:-${USERNAME:-unknown}}"
    printf '%s' "$u"
}

# ── HTTP ──────────────────────────────────────────────────────────────────────

akto_proxy_url() { # akto_proxy_url <guardrails 0|1> <ingest 0|1> [client_hook]
    local q="akto_connector=$AKTO_CONNECTOR"
    [ "$1" = "1" ] && q="guardrails=true&$q"
    [ "$2" = "1" ] && q="$q&ingest_data=true"
    [ -n "$3" ] && q="$q&client_hook=$3"
    printf '%s/api/http-proxy?%s' "$AKTO_DATA_INGESTION_URL" "$q"
}

# Sets AKTO_RESP to the response body. Returns non-zero when the call fails, so
# every caller can fail open.
http_post_json() { # http_post_json <url> <body>
    local url="$1" body="$2" args
    args=(-sS -X POST -H 'Content-Type: application/json'
          --max-time "$AKTO_TIMEOUT" --data-binary @-)
    [ -n "$AKTO_API_TOKEN" ] && args+=(-H "Authorization: $AKTO_API_TOKEN")
    if [ -n "$SSL_CERT_PATH" ]; then args+=(--cacert "$SSL_CERT_PATH")
    elif [ "$SSL_VERIFY" = "false" ]; then args+=(-k)
    else
        # Parity with the Python hooks, which used an unverified SSL context so
        # that on-prem deployments behind a corporate MITM proxy keep working.
        args+=(-k)
    fi

    log_info "API CALL: POST $url"
    [ "$LOG_PAYLOADS" = "true" ] && log_debug "Request payload: $body"

    local start end
    start="$(date +%s)"
    AKTO_RESP="$(printf '%s' "$body" | curl "${args[@]}" "$url" 2>>"${AKTO_LOG_FILE:-/dev/null}")"
    local rc=$?
    end="$(date +%s)"
    if [ $rc -ne 0 ]; then
        log_error "API CALL FAILED after $((end - start))s (curl rc=$rc)"
        AKTO_RESP=""
        return 1
    fi
    log_info "API RESPONSE: ${#AKTO_RESP} bytes in $((end - start))s"
    [ "$LOG_PAYLOADS" = "true" ] && log_debug "Response body: $AKTO_RESP"
    return 0
}

# ── Payload ───────────────────────────────────────────────────────────────────
# Builds the mirrored-HTTP record the ingestion API expects. The header/payload
# fields are JSON documents carried as JSON strings, so each is built as text and
# then escaped once for embedding — the same double-encoding json.dumps() produced.

akto_build_payload() {
    # $1 path, $2 request-headers JSON, $3 response-headers JSON,
    # $4 request-payload JSON, $5 response-payload JSON, $6 tags JSON,
    # $7 status code, $8 vxlan id
    local path="$1" reqh="$2" resph="$3" reqp="$4" respp="$5" tags="$6" code="$7" vxlan="$8"
    cat <<EOF
{"path":"$(jesc "$path")","requestHeaders":"$(jembed "$reqh")","responseHeaders":"$(jembed "$resph")","method":"POST","requestPayload":"$(jembed "$reqp")","responsePayload":"$(jembed "$respp")","ip":"$(jesc "$(akto_username)")","destIp":"127.0.0.1","time":"$(date +%s)000","statusCode":"$code","type":"HTTP/1.1","status":"$code","akto_account_id":"1000000","akto_vxlan_id":"$(jesc "$vxlan")","is_pending":"false","source":"MIRRORING","direction":null,"process_id":null,"socket_id":null,"daemonset_id":null,"enabled_graph":null,"tag":"$(jembed "$tags")","metadata":"$(jembed "$tags")","contextSource":"$(jesc "$CONTEXT_SOURCE")"}
EOF
}

akto_tags() { # akto_tags <is_mcp 0|1>
    if [ "$1" = "1" ]; then
        printf '{"mcp-server":"MCP Server","mcp-client":"%s"' "$(jesc "$AKTO_CONNECTOR_VALUE")"
    else
        printf '{"gen-ai":"Gen AI","ai-agent":"%s"' "$(jesc "$AKTO_CONNECTOR_VALUE")"
    fi
    [ "$MODE" = "atlas" ] && printf ',"source":"%s"' "$(jesc "$CONTEXT_SOURCE")"
    printf '}'
}

akto_atlas_host() {
    if [ "$MODE" = "atlas" ] && [ -n "$AKTO_DEVICE_LABEL" ]; then
        printf '%s.ai-agent.%s' "$AKTO_DEVICE_LABEL" "$AKTO_CONNECTOR_VALUE"
    else
        printf '%s' "${AKTO_API_URL:-127.0.0.1}"
    fi
}

# ── Guardrails ────────────────────────────────────────────────────────────────
# Sets GR_ALLOWED / GR_REASON (still JSON-escaped) / GR_BEHAVIOUR / GR_MODIFIED /
# GR_MODIFIED_PAYLOAD. Always fails open.

akto_guardrails_eval() { # akto_guardrails_eval <payload> [client_hook]
    GR_ALLOWED=true; GR_REASON=""; GR_BEHAVIOUR=""; GR_MODIFIED=false; GR_MODIFIED_PAYLOAD=""
    if [ -z "$AKTO_DATA_INGESTION_URL" ]; then
        log_warn "AKTO_DATA_INGESTION_URL not set, allowing (fail-open)"
        return 0
    fi
    http_post_json "$(akto_proxy_url 1 1 "$2")" "$1" || return 0

    local gr
    gr="$(jget_from "$AKTO_RESP" "data.guardrailsResult")" || return 0
    [ -n "$gr" ] || return 0

    local v
    v="$(jget_from "$gr" "Allowed")" && [ -n "$v" ] && GR_ALLOWED="$v"
    GR_REASON="$(jstr_from "$gr" "Reason")"
    GR_BEHAVIOUR="$(jstr_from "$gr" "behaviour")"
    [ -z "$GR_BEHAVIOUR" ] && GR_BEHAVIOUR="$(jstr_from "$gr" "Behaviour")"
    v="$(jget_from "$gr" "Modified")" && [ -n "$v" ] && GR_MODIFIED="$v"
    GR_MODIFIED_PAYLOAD="$(jstr_from "$gr" "ModifiedPayload")"
    return 0
}

akto_ingest() { # akto_ingest <payload> [client_hook]
    [ -n "$AKTO_DATA_INGESTION_URL" ] || return 0
    http_post_json "$(akto_proxy_url 0 1 "$2")" "$1" >/dev/null 2>&1 || true
    return 0
}

_behaviour_is() { [ "$(printf '%s' "$GR_BEHAVIOUR" | tr '[:upper:]' '[:lower:]')" = "$1" ]; }

# ── Warn / alert resubmit flow ────────────────────────────────────────────────
# "alert" reports server-side but never blocks. "warn" blocks once, then lets the
# identical request through on the next attempt — the fingerprint of the blocked
# request is parked in a state file and consumed on the retry.

akto_warn_state_path() { printf '%s/akto_%s_warn_pending.json' "$LOG_DIR" "$1"; }

_warn_pending_has() { grep -qF "\"$2\"" "$1" 2>/dev/null; }

_warn_pending_add() {
    local f="$1" fp="$2" tmp="$1.tmp"
    mkdir -p "$(dirname "$f")" 2>/dev/null
    { printf '{"warn_pending":['
      if [ -f "$f" ]; then
          grep -oE '"[0-9a-f-]{16,}"' "$f" 2>/dev/null | tr '\n' ',' | sed 's/,$//'
          grep -qE '"[0-9a-f-]{16,}"' "$f" 2>/dev/null && printf ','
      fi
      printf '"%s"]}\n' "$fp"
    } >"$tmp" 2>/dev/null && mv "$tmp" "$f" 2>/dev/null
}

_warn_pending_remove() {
    local f="$1" fp="$2" tmp="$1.tmp"
    [ -f "$f" ] || return 0
    sed "s/\"$fp\",\{0,1\}//; s/,\]/]/" "$f" >"$tmp" 2>/dev/null && mv "$tmp" "$f" 2>/dev/null
}

# Returns 0 to allow, 1 to block. Call only when guardrails said "not allowed".
akto_apply_warn_flow() { # akto_apply_warn_flow <state-name> <fingerprint>
    if _behaviour_is alert; then
        log_info "Alert behaviour: allowing despite violation (server-side alert only)"
        return 0
    fi
    _behaviour_is warn || return 1

    local f
    f="$(akto_warn_state_path "$1")"
    if _warn_pending_has "$f" "$2"; then
        _warn_pending_remove "$f" "$2"
        log_info "Warn flow: allowing resubmit"
        return 0
    fi
    _warn_pending_add "$f" "$2"
    return 1
}

# ── Session state ─────────────────────────────────────────────────────────────
# Carries the message-turn id from the prompt hook to the events that follow it,
# so downstream tracing can stitch prompt -> tools -> response together.

akto_session_load() { # akto_session_load <key> <field>
    [ -f "$SESSION_STATE_PATH" ] || return 1
    jget_from "$(cat "$SESSION_STATE_PATH" 2>/dev/null)" "$1.$2"
}

akto_session_save() { # akto_session_save <key> <field> <raw-value>
    local tmp="$SESSION_STATE_PATH.tmp" existing=""
    mkdir -p "$LOG_DIR" 2>/dev/null
    [ -f "$SESSION_STATE_PATH" ] && existing="$(cat "$SESSION_STATE_PATH" 2>/dev/null)"
    # Single-field rows keep this a whole-file rewrite; the hooks only ever store
    # the current message id, so there is nothing to merge.
    printf '{"%s":{"%s":"%s"}}\n' "$(jesc "$1")" "$(jesc "$2")" "$(jesc "$3")" >"$tmp" 2>/dev/null &&
        mv "$tmp" "$SESSION_STATE_PATH" 2>/dev/null
    return 0
}

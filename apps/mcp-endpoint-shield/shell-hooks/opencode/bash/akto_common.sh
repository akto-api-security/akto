#!/usr/bin/env bash
# akto_common.sh - shared helpers for Akto opencode hooks (bash port).
# Pure-bash + curl only. No python, no jq, no third-party libraries.
set -u

AKTO_LOG_LEVEL="$(printf '%s' "${LOG_LEVEL:-INFO}" | tr '[:lower:]' '[:upper:]')"
AKTO_LOG_PAYLOADS="$(printf '%s' "${LOG_PAYLOADS:-false}" | tr '[:upper:]' '[:lower:]')"
AKTO_MODE="$(printf '%s' "${MODE:-atlas}" | tr '[:upper:]' '[:lower:]')"
AKTO_DATA_INGESTION_URL="${AKTO_DATA_INGESTION_URL:-}"
AKTO_DATA_INGESTION_URL="${AKTO_DATA_INGESTION_URL%/}"
AKTO_TIMEOUT="${AKTO_TIMEOUT:-5}"
AKTO_SYNC_MODE="$(printf '%s' "${AKTO_SYNC_MODE:-true}" | tr '[:upper:]' '[:lower:]')"
AKTO_API_TOKEN="${AKTO_API_TOKEN:-}"
AKTO_CONNECTOR="${AKTO_CONNECTOR:-opencode}"
CONTEXT_SOURCE="${CONTEXT_SOURCE:-ENDPOINT}"

AKTO_LOG_DIR="${LOG_DIR:-$HOME/.config/opencode/akto/logs}"
case "$AKTO_LOG_DIR" in "~"/*) AKTO_LOG_DIR="$HOME/${AKTO_LOG_DIR#"~/"}";; esac
AKTO_LOG_FILE=""

# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------
_akto_level_num() {
  case "$1" in DEBUG) echo 10;; INFO) echo 20;; WARNING) echo 30;; ERROR) echo 40;; *) echo 20;; esac
}
_AKTO_LEVEL_NUM="$(_akto_level_num "$AKTO_LOG_LEVEL")"

log_info()  { _akto_log INFO  "$@"; }
log_warn()  { _akto_log WARNING "$@"; }
log_error() { _akto_log ERROR "$@"; }
_akto_log() {
  local lvl="$1"; shift
  local want; want="$(_akto_level_num "$lvl")"
  [ "$want" -lt "$_AKTO_LEVEL_NUM" ] && return 0
  local ts; ts="$(date '+%Y-%m-%d %H:%M:%S')"
  [ -n "${AKTO_LOG_FILE:-}" ] && printf '%s - %s - %s\n' "$ts" "$lvl" "$*" >>"$AKTO_LOG_FILE" 2>/dev/null || true
  [ "$lvl" = "ERROR" ] && printf '%s\n' "$*" >&2
  return 0
}

# ---------------------------------------------------------------------------
# JSON
# ---------------------------------------------------------------------------
json_escape() {
  local s="$1"
  s="${s//\\/\\\\}"; s="${s//\"/\\\"}"; s="${s//$'\r'/\\r}"; s="${s//$'\n'/\\n}"; s="${s//$'\t'/\\t}"
  printf '%s' "$s"
}
json_str() { printf '"%s"' "$(json_escape "$1")"; }

_AKTO_JSON=""; _AKTO_JSON_LEN=0
_json_skip_ws() {
  local i="$1" c
  while [ "$i" -lt "$_AKTO_JSON_LEN" ]; do c="${_AKTO_JSON:$i:1}"; case "$c" in ' '|$'\t'|$'\n'|$'\r') i=$((i+1));; *) break;; esac; done
  echo "$i"
}
_json_scan_string() {
  local i=$(( $1 + 1 )) c
  while [ "$i" -lt "$_AKTO_JSON_LEN" ]; do c="${_AKTO_JSON:$i:1}"; if [ "$c" = "\\" ]; then i=$((i+2)); continue; fi; [ "$c" = '"' ] && { echo $((i+1)); return 0; }; i=$((i+1)); done; echo "$i"
}
_json_scan_value() {
  local i="$1" c first depth; first="${_AKTO_JSON:$i:1}"
  case "$first" in
    '"') _json_scan_string "$i"; return 0;;
    '{'|'[') depth=0
      while [ "$i" -lt "$_AKTO_JSON_LEN" ]; do c="${_AKTO_JSON:$i:1}"
        case "$c" in '"') i="$(_json_scan_string "$i")"; continue;; '{'|'[') depth=$((depth+1));; '}'|']') depth=$((depth-1)); [ "$depth" -eq 0 ] && { echo $((i+1)); return 0; };; esac; i=$((i+1)); done; echo "$i"; return 0;;
    *) while [ "$i" -lt "$_AKTO_JSON_LEN" ]; do c="${_AKTO_JSON:$i:1}"; case "$c" in ','|'}'|']'|' '|$'\t'|$'\n'|$'\r') break;; esac; i=$((i+1)); done; echo "$i"; return 0;;
  esac
}
json_raw() {
  _AKTO_JSON="$1"; _AKTO_JSON_LEN=${#_AKTO_JSON}
  local key="$2" i c kstart kend kname vstart vend
  i="$(_json_skip_ws 0)"; [ "${_AKTO_JSON:$i:1}" = '{' ] || return 1; i=$((i+1))
  while [ "$i" -lt "$_AKTO_JSON_LEN" ]; do
    i="$(_json_skip_ws "$i")"; c="${_AKTO_JSON:$i:1}"; [ "$c" = '}' ] && return 1; [ "$c" = ',' ] && { i=$((i+1)); continue; }
    [ "$c" = '"' ] || return 1; kstart="$i"; kend="$(_json_scan_string "$i")"; kname="${_AKTO_JSON:$((kstart+1)):$((kend-kstart-2))}"
    i="$(_json_skip_ws "$kend")"; [ "${_AKTO_JSON:$i:1}" = ':' ] || return 1; i="$(_json_skip_ws $((i+1)))"
    vstart="$i"; vend="$(_json_scan_value "$i")"
    if [ "$kname" = "$key" ]; then printf '%s' "${_AKTO_JSON:$vstart:$((vend-vstart))}"; return 0; fi
    i="$vend"
  done; return 1
}
_json_unescape() {
  local s="$1"; s="${s//\\\"/\"}"; s="${s//\\n/$'\n'}"; s="${s//\\r/$'\r'}"; s="${s//\\t/$'\t'}"; s="${s//\\\//\/}"; s="${s//\\\\/\\}"; printf '%s' "$s"
}
json_string() {
  local raw; raw="$(json_raw "$1" "$2")" || return 1
  case "$raw" in '"'*'"') raw="${raw#\"}"; raw="${raw%\"}"; _json_unescape "$raw";; *) printf '%s' "$raw";; esac
}

# ---------------------------------------------------------------------------
# sha256
# ---------------------------------------------------------------------------
sha256_hex() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum | awk '{print $1}'; else shasum -a 256 | awk '{print $1}'; fi
}

# ---------------------------------------------------------------------------
# Machine id / username
# ---------------------------------------------------------------------------
_akto_uname="$(uname -s 2>/dev/null || echo unknown)"
_AKTO_MACHINE_ID=""
_AKTO_USERNAME=""

get_machine_id() {
  [ -n "$_AKTO_MACHINE_ID" ] && { printf '%s' "$_AKTO_MACHINE_ID"; return 0; }
  local raw=""
  [ "$_akto_uname" = "Darwin" ] && raw="$(scutil --get ComputerName 2>/dev/null)"
  [ -z "$raw" ] && raw="$(hostname 2>/dev/null)" && raw="${raw%.local}"
  raw="$(printf '%s' "$raw" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9]/-/g')"
  _AKTO_MACHINE_ID="$raw"; printf '%s' "$raw"
}
get_username() {
  [ -n "$_AKTO_USERNAME" ] && { printf '%s' "$_AKTO_USERNAME"; return 0; }
  local u=""
  [ -n "${SUDO_USER:-}" ] && [ "$SUDO_USER" != "root" ] && u="$SUDO_USER"
  [ -z "$u" ] && u="$(id -un 2>/dev/null)"
  [ -z "$u" ] && u="unknown"
  _AKTO_USERNAME="$u"; printf '%s' "$u"
}

# ---------------------------------------------------------------------------
# OpenCode API URL resolution
# ---------------------------------------------------------------------------
resolve_opencode_api_url() {
  if [ "$AKTO_MODE" = "atlas" ]; then
    local did; did="${DEVICE_ID:-$(get_machine_id)}"
    [ -n "$did" ] && { printf 'https://%s.opencode.local' "$did"; return 0; }
    printf '%s' "${OPENCODE_API_URL:-https://api.opencode.ai}"
  else
    printf '%s' "${OPENCODE_API_URL:-https://api.opencode.ai}"
  fi
}

# ---------------------------------------------------------------------------
# HTTP
# ---------------------------------------------------------------------------
# build_http_proxy_url GUARDRAILS(0/1) INGEST_DATA(0/1)
build_http_proxy_url() {
  local gr="$1" ingest="$2" params=""
  [ "$gr" = "1" ] && params="guardrails=true"
  [ -n "$params" ] && params="$params&"
  params="${params}akto_connector=${AKTO_CONNECTOR}"
  [ "$ingest" = "1" ] && params="${params}&ingest_data=true"
  printf '%s/api/http-proxy?%s' "$AKTO_DATA_INGESTION_URL" "$params"
}

post_payload_json() {
  local url="$1" body="$2" resp rc
  log_info "API CALL: POST $url"
  [ "$AKTO_LOG_PAYLOADS" = "true" ] && log_info "Payload: $body"
  if [ -n "$AKTO_API_TOKEN" ]; then
    resp="$(printf '%s' "$body" | curl -sS -k --max-time "$AKTO_TIMEOUT" -X POST "$url" \
      -H "Content-Type: application/json" -H "Authorization: $AKTO_API_TOKEN" --data-binary @- 2>>"${AKTO_LOG_FILE:-/dev/null}")"
    rc=$?
  else
    resp="$(printf '%s' "$body" | curl -sS -k --max-time "$AKTO_TIMEOUT" -X POST "$url" \
      -H "Content-Type: application/json" --data-binary @- 2>>"${AKTO_LOG_FILE:-/dev/null}")"
    rc=$?
  fi
  [ "$rc" -ne 0 ] && { log_error "API CALL FAILED (curl rc=$rc)"; return "$rc"; }
  printf '%s' "$resp"; return 0
}

# ---------------------------------------------------------------------------
# Guardrails parsing + warn-resubmit flow
# ---------------------------------------------------------------------------
GR_ALLOWED="true"; GR_REASON=""; GR_BEHAVIOUR=""
parse_guardrails_result() {
  local resp="$1"; GR_ALLOWED="true"; GR_REASON=""; GR_BEHAVIOUR=""
  local data gr
  data="$(json_raw "$resp" "data")" || return 0
  gr="$(json_raw "$data" "guardrailsResult")" || return 0
  local a; a="$(json_string "$gr" "Allowed" 2>/dev/null)"; [ -n "$a" ] && GR_ALLOWED="$a"
  GR_REASON="$(json_string "$gr" "Reason" 2>/dev/null)"
  GR_BEHAVIOUR="$(json_string "$gr" "behaviour" 2>/dev/null)"
  [ -z "$GR_BEHAVIOUR" ] && GR_BEHAVIOUR="$(json_string "$gr" "Behaviour" 2>/dev/null)"
}

is_warn_behaviour()  { [ "$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]' | sed 's/^ *//;s/ *$//')" = "warn" ]; }
is_alert_behaviour() { [ "$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]' | sed 's/^ *//;s/ *$//')" = "alert" ]; }

ALLOWED="true"; OUT_REASON=""
apply_warn_resubmit_flow() {
  local gr_allowed="$1" reason="$2" behaviour="$3" fp="$4" wf="$5"
  ALLOWED="true"; OUT_REASON=""
  [ "$gr_allowed" = "true" ] && return 0
  if is_alert_behaviour "$behaviour"; then log_info "Alert behaviour: allowing"; return 0; fi
  if ! is_warn_behaviour "$behaviour"; then ALLOWED="false"; OUT_REASON="$reason"; return 0; fi
  if grep -q "\"$fp\"" "$wf" 2>/dev/null; then
    local tmp="$wf.tmp"
    grep -v "\"$fp\"" "$wf" > "$tmp" 2>/dev/null && mv "$tmp" "$wf" 2>/dev/null || rm -f "$tmp" 2>/dev/null
    log_info "Warn flow: resubmit allowed, removed fingerprint"
    return 0
  fi
  local fps; fps="$(grep -oE '"[0-9a-f]{64}"' "$wf" 2>/dev/null | tr -d '"')"
  local tmp="$wf.tmp"
  { printf '{"warn_pending":['
    local first=1
    echo "$fps" | while IFS= read -r f; do [ -z "$f" ] && continue; [ "$first" -eq 1 ] && first=0 || printf ','; printf '"%s"' "$f"; done
    [ "$first" -eq 1 ] && first=0 || printf ','
    printf '"%s"' "$fp"
    printf ']}\n'
  } > "$tmp" 2>/dev/null && mv "$tmp" "$wf" 2>/dev/null || rm -f "$tmp" 2>/dev/null
  ALLOWED="false"; OUT_REASON="$reason"
}

mkdir -p "$AKTO_LOG_DIR" 2>/dev/null || true

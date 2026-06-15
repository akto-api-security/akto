#!/usr/bin/env bash
# akto_common.sh - shared helpers for Akto claude-cli hooks (bash port).
#
# Pure-bash + curl only. No python, no jq, no third-party libraries.
# Targets bash 3.2 (the version Apple ships with macOS) for max portability.
#
# Provides:
#   - env/config loading (mirrors the Python module-level config)
#   - logging to a per-hook file
#   - machine id / username resolution (matches akto_machine_id.py)
#   - a small pure-bash JSON reader (top-level keys) + string escaper
#   - HTTP POST via curl (unverified TLS, optional bearer token)
#   - guardrails behaviour helpers + warn-pending state file
#   - sha256 fingerprint

# ---------------------------------------------------------------------------
# Config (env with defaults) - mirrors the Python hooks 1:1
# ---------------------------------------------------------------------------
AKTO_LOG_DIR="${LOG_DIR:-$HOME/.claude/akto/logs}"
# expand a leading ~ if present
case "$AKTO_LOG_DIR" in "~"/*) AKTO_LOG_DIR="$HOME/${AKTO_LOG_DIR#"~/"}";; esac
AKTO_LOG_LEVEL="$(printf '%s' "${LOG_LEVEL:-INFO}" | tr '[:lower:]' '[:upper:]')"
AKTO_LOG_PAYLOADS="$(printf '%s' "${LOG_PAYLOADS:-false}" | tr '[:upper:]' '[:lower:]')"

AKTO_MODE="$(printf '%s' "${MODE:-argus}" | tr '[:upper:]' '[:lower:]')"
AKTO_DATA_INGESTION_URL="${AKTO_DATA_INGESTION_URL:-}"
AKTO_DATA_INGESTION_URL="${AKTO_DATA_INGESTION_URL%/}"   # strip trailing slash
AKTO_TIMEOUT="${AKTO_TIMEOUT:-5}"
AKTO_SYNC_MODE="$(printf '%s' "${AKTO_SYNC_MODE:-true}" | tr '[:upper:]' '[:lower:]')"
AKTO_CONNECTOR="${AKTO_CONNECTOR:-claude_code_cli}"
AKTO_CONNECTOR_VALUE="${AKTO_CONNECTOR_VALUE:-claudecli}"
AKTO_API_TOKEN="${AKTO_API_TOKEN:-}"
CONTEXT_SOURCE="${CONTEXT_SOURCE:-ENDPOINT}"
AKTO_INGEST_NON_MCP_TOOLS="$(printf '%s' "${AKTO_INGEST_NON_MCP_TOOLS:-false}" | tr '[:upper:]' '[:lower:]')"
MCP_INGEST_PATH="${MCP_INGEST_PATH:-/mcp}"
NON_MCP_TOOL_PATH_PREFIX="${NON_MCP_TOOL_PATH_PREFIX:-/tool}"

mkdir -p "$AKTO_LOG_DIR" 2>/dev/null || true

# ---------------------------------------------------------------------------
# Logging - INFO+ to <hook>.log, ERROR to stderr (mirrors Python handlers)
# AKTO_LOG_FILE must be set by the calling hook before sourcing actions.
# ---------------------------------------------------------------------------
_akto_level_num() {
  case "$1" in
    DEBUG) echo 10;; INFO) echo 20;; WARNING) echo 30;;
    ERROR) echo 40;; *) echo 20;;
  esac
}
_AKTO_LEVEL_NUM="$(_akto_level_num "$AKTO_LOG_LEVEL")"

log() {  # log LEVEL message...
  local lvl="$1"; shift
  local msg="$*"
  local want; want="$(_akto_level_num "$lvl")"
  [ "$want" -lt "$_AKTO_LEVEL_NUM" ] && return 0
  local ts; ts="$(date '+%Y-%m-%d %H:%M:%S')"
  if [ -n "${AKTO_LOG_FILE:-}" ]; then
    printf '%s - %s - %s\n' "$ts" "$lvl" "$msg" >>"$AKTO_LOG_FILE" 2>/dev/null || true
  fi
  [ "$lvl" = "ERROR" ] && printf '%s\n' "$msg" >&2
  return 0
}
log_info()  { log INFO  "$@"; }
log_warn()  { log WARNING "$@"; }
log_error() { log ERROR "$@"; }

# ---------------------------------------------------------------------------
# JSON string escaping (for building payloads)
# Handles the cases that occur in hook data: backslash, quote, CR/LF/TAB.
# ---------------------------------------------------------------------------
json_escape() {
  local s="$1"
  s="${s//\\/\\\\}"          # backslash FIRST
  s="${s//\"/\\\"}"          # double quote
  s="${s//$'\r'/\\r}"
  s="${s//$'\n'/\\n}"
  s="${s//$'\t'/\\t}"
  printf '%s' "$s"
}

# json_str VALUE -> a quoted JSON string literal for VALUE
json_str() { printf '"%s"' "$(json_escape "$1")"; }

# ---------------------------------------------------------------------------
# Minimal pure-bash JSON reader for TOP-LEVEL keys of an object.
# Respects strings/escapes and nested {}/[]. Good for hook stdin where the
# fields we need (prompt, tool_name, tool_input, ...) are top-level.
#
#   json_raw  JSON KEY  -> raw value substring verbatim (string keeps quotes,
#                          object/array kept intact for round-tripping)
#   json_string JSON KEY -> unescaped string value (empty if absent/not string)
#   json_bool JSON KEY   -> "true"/"false"/""
# Returns non-zero if key not found.
# ---------------------------------------------------------------------------
_AKTO_JSON=""        # set by json_raw, holds input
_AKTO_JSON_LEN=0

_json_skip_ws() {    # arg: start index -> echoes next non-ws index
  local i="$1" c
  while [ "$i" -lt "$_AKTO_JSON_LEN" ]; do
    c="${_AKTO_JSON:$i:1}"
    case "$c" in ' '|$'\t'|$'\n'|$'\r') i=$((i+1));; *) break;; esac
  done
  echo "$i"
}

_json_scan_string() {  # arg: index of opening quote -> echoes index AFTER closing quote
  local i=$(( $1 + 1 )) c
  while [ "$i" -lt "$_AKTO_JSON_LEN" ]; do
    c="${_AKTO_JSON:$i:1}"
    if [ "$c" = "\\" ]; then i=$((i+2)); continue; fi
    [ "$c" = '"' ] && { echo $((i+1)); return 0; }
    i=$((i+1))
  done
  echo "$i"
}

_json_scan_value() {  # arg: index of value start -> echoes index AFTER value end
  local i="$1" c first depth
  first="${_AKTO_JSON:$i:1}"
  case "$first" in
    '"') _json_scan_string "$i"; return 0;;
    '{'|'[')
      depth=0
      while [ "$i" -lt "$_AKTO_JSON_LEN" ]; do
        c="${_AKTO_JSON:$i:1}"
        case "$c" in
          '"') i="$(_json_scan_string "$i")"; continue;;
          '{'|'[') depth=$((depth+1));;
          '}'|']') depth=$((depth-1)); [ "$depth" -eq 0 ] && { echo $((i+1)); return 0; };;
        esac
        i=$((i+1))
      done
      echo "$i"; return 0;;
    *)  # scalar: number/true/false/null - read until , } ] or ws
      while [ "$i" -lt "$_AKTO_JSON_LEN" ]; do
        c="${_AKTO_JSON:$i:1}"
        case "$c" in ','|'}'|']'|' '|$'\t'|$'\n'|$'\r') break;; esac
        i=$((i+1))
      done
      echo "$i"; return 0;;
  esac
}

# json_raw JSON KEY -> raw value substring (verbatim)
json_raw() {
  _AKTO_JSON="$1"; _AKTO_JSON_LEN=${#_AKTO_JSON}
  local key="$2" i c kstart kend kname vstart vend
  i="$(_json_skip_ws 0)"
  [ "${_AKTO_JSON:$i:1}" = '{' ] || return 1
  i=$((i+1))
  while [ "$i" -lt "$_AKTO_JSON_LEN" ]; do
    i="$(_json_skip_ws "$i")"
    c="${_AKTO_JSON:$i:1}"
    [ "$c" = '}' ] && return 1
    [ "$c" = ',' ] && { i=$((i+1)); continue; }
    [ "$c" = '"' ] || return 1
    kstart="$i"; kend="$(_json_scan_string "$i")"
    kname="${_AKTO_JSON:$((kstart+1)):$((kend-kstart-2))}"
    i="$(_json_skip_ws "$kend")"
    [ "${_AKTO_JSON:$i:1}" = ':' ] || return 1
    i="$(_json_skip_ws $((i+1)))"
    vstart="$i"; vend="$(_json_scan_value "$i")"
    if [ "$kname" = "$key" ]; then
      printf '%s' "${_AKTO_JSON:$vstart:$((vend-vstart))}"
      return 0
    fi
    i="$vend"
  done
  return 1
}

# _json_unescape "<escaped>" -> raw text (handles common escapes)
_json_unescape() {
  local s="$1"
  s="${s//\\\"/\"}"
  s="${s//\\n/$'\n'}"
  s="${s//\\r/$'\r'}"
  s="${s//\\t/$'\t'}"
  s="${s//\\\//\/}"
  s="${s//\\\\/\\}"
  printf '%s' "$s"
}

# json_string JSON KEY -> unescaped string value
json_string() {
  local raw; raw="$(json_raw "$1" "$2")" || return 1
  case "$raw" in
    '"'*'"') raw="${raw#\"}"; raw="${raw%\"}"; _json_unescape "$raw";;
    *) printf '%s' "$raw";;   # not a string; return as-is
  esac
}

# json_bool JSON KEY -> "true"/"false"/""
json_bool() {
  local raw; raw="$(json_raw "$1" "$2")" || { printf ''; return 1; }
  case "$raw" in true) printf 'true';; false) printf 'false';; *) printf '';; esac
}

# ---------------------------------------------------------------------------
# sha256 hex of stdin -> portable across mac (shasum) / linux (sha256sum)
# ---------------------------------------------------------------------------
sha256_hex() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum | awk '{print $1}'
  else
    shasum -a 256 | awk '{print $1}'
  fi
}

# ---------------------------------------------------------------------------
# Machine id / username (mirrors akto_machine_id.py)
# ---------------------------------------------------------------------------
_akto_uname="$(uname -s 2>/dev/null || echo unknown)"

_generate_machine_id() {
  case "$_akto_uname" in
    Darwin)
      local out uuid
      out="$(ioreg -rd1 -c IOPlatformExpertDevice 2>/dev/null | grep IOPlatformUUID | head -1)"
      if [ -n "$out" ]; then
        uuid="$(printf '%s' "$out" | sed -E 's/.*"IOPlatformUUID" = "([^"]+)".*/\1/')"
        printf '%s' "$uuid" | tr -d '-' | tr '[:upper:]' '[:lower:]'; return 0
      fi;;
    Linux)
      if [ -r /etc/machine-id ]; then
        tr -d '-' < /etc/machine-id | tr '[:upper:]' '[:lower:]' | tr -d '\n'; return 0
      fi
      if [ -r /var/lib/dbus/machine-id ]; then
        tr -d '-' < /var/lib/dbus/machine-id | tr '[:upper:]' '[:lower:]' | tr -d '\n'; return 0
      fi;;
  esac
  # Fallback: a MAC-ish id from ifconfig/ip if available
  local mac
  mac="$(ifconfig 2>/dev/null | grep -Eo '([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}' | head -1)"
  [ -z "$mac" ] && mac="$(ip link 2>/dev/null | grep -Eo '([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}' | head -1)"
  printf '%s' "$mac" | tr -d ':-' | tr '[:upper:]' '[:lower:]'
}

get_machine_id() {
  [ -n "${_AKTO_MACHINE_ID:-}" ] && { printf '%s' "$_AKTO_MACHINE_ID"; return 0; }
  local raw=""
  if [ "$_akto_uname" = "Darwin" ]; then
    raw="$(scutil --get ComputerName 2>/dev/null)"
  fi
  if [ -z "$raw" ]; then
    raw="$(hostname 2>/dev/null)"
    raw="${raw%.local}"
  fi
  [ -z "$raw" ] && raw="$(_generate_machine_id)"
  # ToLower + [^a-zA-Z0-9] -> '-'
  raw="$(printf '%s' "$raw" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9]/-/g')"
  _AKTO_MACHINE_ID="$raw"
  printf '%s' "$raw"
}

get_username() {
  [ -n "${_AKTO_USERNAME:-}" ] && { printf '%s' "$_AKTO_USERNAME"; return 0; }
  local u=""
  if [ -n "${SUDO_USER:-}" ] && [ "$SUDO_USER" != "root" ]; then
    u="$SUDO_USER"
  elif [ "$(id -un 2>/dev/null)" = "root" ] && [ "$_akto_uname" = "Darwin" ]; then
    u="$(stat -f %Su /dev/console 2>/dev/null)"
    [ "$u" = "root" ] && u=""
  fi
  [ -z "$u" ] && u="$(id -un 2>/dev/null)"
  [ -z "$u" ] && u="unknown"
  _AKTO_USERNAME="$u"
  printf '%s' "$u"
}

# ---------------------------------------------------------------------------
# HTTP proxy URL + POST via curl (unverified TLS, optional bearer token)
# ---------------------------------------------------------------------------
build_http_proxy_url() {  # flags: guardrails response_guardrails ingest_data (1/0)
  local g="$1" rg="$2" ing="$3" params=""
  [ "$g" = "1" ]   && params="${params}guardrails=true&"
  [ "$rg" = "1" ]  && params="${params}response_guardrails=true&"
  params="${params}akto_connector=${AKTO_CONNECTOR}"
  [ "$ing" = "1" ] && params="${params}&ingest_data=true"
  printf '%s/api/http-proxy?%s' "$AKTO_DATA_INGESTION_URL" "$params"
}

# post_payload_json URL JSON_BODY -> echoes response body (raw). returns curl exit code.
post_payload_json() {
  local url="$1" body="$2"
  log_info "API CALL: POST $url"
  [ "$AKTO_LOG_PAYLOADS" = "true" ] && log INFO "Request payload: $body"
  local resp rc
  if [ -n "$AKTO_API_TOKEN" ]; then
    resp="$(printf '%s' "$body" | curl -sS -k --max-time "$AKTO_TIMEOUT" \
              -X POST "$url" \
              -H "Content-Type: application/json" \
              -H "Authorization: $AKTO_API_TOKEN" \
              --data-binary @- 2>>"${AKTO_LOG_FILE:-/dev/null}")"
    rc=$?
  else
    resp="$(printf '%s' "$body" | curl -sS -k --max-time "$AKTO_TIMEOUT" \
              -X POST "$url" \
              -H "Content-Type: application/json" \
              --data-binary @- 2>>"${AKTO_LOG_FILE:-/dev/null}")"
    rc=$?
  fi
  if [ "$rc" -ne 0 ]; then
    log_error "API CALL FAILED (curl rc=$rc)"
    return "$rc"
  fi
  log_info "API RESPONSE: ${#resp} bytes"
  [ "$AKTO_LOG_PAYLOADS" = "true" ] && log INFO "Response body: $resp"
  printf '%s' "$resp"
  return 0
}

# ---------------------------------------------------------------------------
# guardrails result parsing - given the raw response, extract nested fields.
# The result shape is {"data":{"guardrailsResult":{...}}}. We read the
# guardrailsResult object then pull simple fields from it.
# Sets globals: GR_ALLOWED GR_REASON GR_BEHAVIOUR GR_MODIFIED GR_MODIFIED_PAYLOAD
# ---------------------------------------------------------------------------
parse_guardrails_result() {
  local resp="$1"
  GR_ALLOWED="true"; GR_REASON=""; GR_BEHAVIOUR=""; GR_MODIFIED="false"; GR_MODIFIED_PAYLOAD=""
  local data gr
  data="$(json_raw "$resp" "data")" || { return 0; }
  gr="$(json_raw "$data" "guardrailsResult")" || { return 0; }
  local a; a="$(json_bool "$gr" "Allowed")"; [ -n "$a" ] && GR_ALLOWED="$a"
  GR_REASON="$(json_string "$gr" "Reason" 2>/dev/null)"
  GR_BEHAVIOUR="$(json_string "$gr" "behaviour" 2>/dev/null)"
  [ -z "$GR_BEHAVIOUR" ] && GR_BEHAVIOUR="$(json_string "$gr" "Behaviour" 2>/dev/null)"
  local m; m="$(json_bool "$gr" "Modified")"; [ -n "$m" ] && GR_MODIFIED="$m"
  GR_MODIFIED_PAYLOAD="$(json_raw "$gr" "ModifiedPayload" 2>/dev/null)"
}

# behaviour helpers
behaviour_lc() { printf '%s' "$1" | tr '[:upper:]' '[:lower:]' | sed 's/^ *//;s/ *$//'; }
is_warn_behaviour()  { [ "$(behaviour_lc "$1")" = "warn" ]; }
is_alert_behaviour() { [ "$(behaviour_lc "$1")" = "alert" ]; }

# ---------------------------------------------------------------------------
# warn-pending state file (newline-delimited fingerprints, JSON wrapper)
#   warn_state_path FILE  -> set WARN_STATE_PATH
#   warn_pending_has FP   -> return 0 if present
#   warn_pending_add FP / warn_pending_del FP
# Stored as {"warn_pending":["fp1","fp2"]}; we read/write simply.
# ---------------------------------------------------------------------------
warn_pending_has() {  # FILE FP
  local f="$1" fp="$2"
  [ -f "$f" ] || return 1
  grep -q "\"$fp\"" "$f" 2>/dev/null
}
_warn_pending_list() {  # FILE -> echoes one fp per line
  local f="$1"
  [ -f "$f" ] || return 0
  # extract the array contents and split on quotes
  tr -d '\n' < "$f" | sed -E 's/.*"warn_pending"[[:space:]]*:[[:space:]]*\[//; s/\].*//' \
    | grep -Eo '"[0-9a-f]{64}"' | tr -d '"'
}
_warn_pending_write() { # FILE  (reads fps from stdin, one per line)
  local f="$1" tmp="$1.tmp" first=1
  { printf '{"warn_pending":['
    while IFS= read -r fp; do
      [ -z "$fp" ] && continue
      [ "$first" -eq 1 ] && first=0 || printf ','
      printf '"%s"' "$fp"
    done
    printf ']}\n'
  } >"$tmp" 2>/dev/null && mv "$tmp" "$f" 2>/dev/null || rm -f "$tmp" 2>/dev/null
}
warn_pending_add() {  # FILE FP
  local f="$1" fp="$2"
  { _warn_pending_list "$f"; printf '%s\n' "$fp"; } | sort -u | _warn_pending_write "$f"
}
warn_pending_del() {  # FILE FP
  local f="$1" fp="$2"
  _warn_pending_list "$f" | grep -v "^$fp$" | _warn_pending_write "$f"
}

# apply_warn_resubmit_flow: sets ALLOWED ("true"/"false") and OUT_REASON
# args: gr_allowed(true/false) reason behaviour fingerprint warn_state_file
apply_warn_resubmit_flow() {
  local gr_allowed="$1" reason="$2" behaviour="$3" fp="$4" wf="$5"
  ALLOWED="true"; OUT_REASON=""
  if [ "$gr_allowed" = "true" ]; then return 0; fi
  if is_alert_behaviour "$behaviour"; then
    log_info "Alert behaviour: allowing despite violation (server-side alert only)"
    return 0
  fi
  if ! is_warn_behaviour "$behaviour"; then
    ALLOWED="false"; OUT_REASON="$reason"; return 0
  fi
  if warn_pending_has "$wf" "$fp"; then
    warn_pending_del "$wf" "$fp"
    log_info "Warn flow: allowing resubmit; removed fingerprint from map"
    return 0
  fi
  warn_pending_add "$wf" "$fp"
  ALLOWED="false"; OUT_REASON="$reason"
}

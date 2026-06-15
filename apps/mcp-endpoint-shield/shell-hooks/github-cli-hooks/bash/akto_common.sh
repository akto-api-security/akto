#!/usr/bin/env bash
# akto_common.sh - shared helpers for Akto github-cli / copilot hooks (bash port).
#
# Pure-bash + curl only. No python, no jq, no third-party libraries.
# Targets bash 3.2 (the version Apple ships with macOS) for max portability.
#
# Faithful port of the github-cli Python hooks (akto-validate-*.py +
# akto_machine_id.py + akto_heartbeat.py). The connector is auto-detected from
# the hook payload (vscode vs copilot_cli), mirroring detect_connector().
#
# Provides:
#   - env/config loading (mirrors the Python module-level config)
#   - connector auto-detection + per-connector config (cfg_*)
#   - logging to a per-hook file
#   - machine id / username resolution (matches akto_machine_id.py)
#   - a small pure-bash JSON reader (top-level keys) + string escaper
#   - HTTP POST via curl (unverified TLS, optional bearer token)
#   - guardrails behaviour helpers + warn-pending state file
#   - sha256 fingerprint
#   - heartbeat publisher (matches akto_heartbeat.py)

# ---------------------------------------------------------------------------
# Config (env with defaults) - mirrors the Python hooks 1:1
# ---------------------------------------------------------------------------
AKTO_LOG_LEVEL="$(printf '%s' "${LOG_LEVEL:-INFO}" | tr '[:lower:]' '[:upper:]')"
AKTO_LOG_PAYLOADS="$(printf '%s' "${LOG_PAYLOADS:-false}" | tr '[:upper:]' '[:lower:]')"

AKTO_MODE="$(printf '%s' "${MODE:-argus}" | tr '[:upper:]' '[:lower:]')"
AKTO_DATA_INGESTION_URL="${AKTO_DATA_INGESTION_URL:-}"
AKTO_DATA_INGESTION_URL="${AKTO_DATA_INGESTION_URL%/}"   # strip trailing slash
AKTO_TIMEOUT="${AKTO_TIMEOUT:-5}"
AKTO_SYNC_MODE="$(printf '%s' "${AKTO_SYNC_MODE:-true}" | tr '[:upper:]' '[:lower:]')"
AKTO_API_TOKEN="${AKTO_API_TOKEN:-}"
CONTEXT_SOURCE="${CONTEXT_SOURCE:-ENDPOINT}"
MCP_INGEST_PATH="${MCP_INGEST_PATH:-/mcp}"

# Heartbeat (mirrors akto_heartbeat.py)
HEARTBEAT_MODULE_TYPE="MCP_ENDPOINT_SHIELD"
HEARTBEAT_VERSION="1.0.0"
HEARTBEAT_INTERVAL_S=30
DB_ABSTRACTOR_URL="${DATABASE_ABSTRACTOR_SERVICE_URL:-https://cyborg.akto.io}"
DB_ABSTRACTOR_URL="${DB_ABSTRACTOR_URL%/}"
HEARTBEAT_TIMEOUT=3

# AKTO_LOG_DIR / AKTO_LOG_FILE are set later by the calling hook (it depends on
# the detected connector). We keep a getter that respects LOG_DIR if present.

# ---------------------------------------------------------------------------
# Connector auto-detection (mirrors detect_connector + get_connector_config)
#   detect_connector INPUT_JSON -> echoes "vscode" or "copilot_cli"
#   load_connector_config CONNECTOR -> sets CFG_* globals
# ---------------------------------------------------------------------------
detect_connector() {  # INPUT_JSON
  # hookEventName is present in all VSCode payloads
  if json_raw "$1" hookEventName >/dev/null 2>&1; then
    printf 'vscode'
  else
    printf '%s' "${AKTO_CONNECTOR:-copilot_cli}"
  fi
}

# CFG_* globals filled by load_connector_config
CFG_CONNECTOR=""; CFG_IS_VSCODE=0; CFG_API_URL=""; CFG_AI_AGENT_TAG=""
CFG_HOOK_HEADER=""; CFG_ATLAS_DOMAIN=""; CFG_LOG_DIR_DEFAULT=""; CFG_BLOCKED_EXIT_CODE=0

load_connector_config() {  # CONNECTOR
  local c="$1"
  if [ "$c" = "vscode" ]; then
    CFG_CONNECTOR="vscode"
    CFG_IS_VSCODE=1
    CFG_API_URL="${VSCODE_API_URL:-https://vscode.dev}"
    CFG_AI_AGENT_TAG="vscode"
    CFG_HOOK_HEADER="x-vscode-hook"
    CFG_ATLAS_DOMAIN="ai-agent.vscode"
    CFG_LOG_DIR_DEFAULT="$HOME/.github/akto/vscode/logs"
    CFG_BLOCKED_EXIT_CODE=2
  else
    CFG_CONNECTOR="$c"
    CFG_IS_VSCODE=0
    CFG_API_URL="${GITHUB_COPILOT_API_URL:-https://api.github.com}"
    CFG_AI_AGENT_TAG="copilotcli"
    CFG_HOOK_HEADER="x-copilot-hook"
    CFG_ATLAS_DOMAIN="ai-agent.copilot"
    CFG_LOG_DIR_DEFAULT="$HOME/.github/akto/copilot/logs"
    CFG_BLOCKED_EXIT_CODE=0  # github-cli cannot block prompts
  fi
  # atlas mode rewrites api_url to device-scoped host
  if [ "$AKTO_MODE" = "atlas" ]; then
    local did; did="${DEVICE_ID:-$(get_machine_id)}"
    [ -n "$did" ] && CFG_API_URL="https://${did}.${CFG_ATLAS_DOMAIN}"
  fi
}

# resolve_log_dir -> echoes the expanded log dir (LOG_DIR env beats default)
resolve_log_dir() {
  local d="${LOG_DIR:-$CFG_LOG_DIR_DEFAULT}"
  case "$d" in "~"/*) d="$HOME/${d#"~/"}";; esac
  printf '%s' "$d"
}

# ---------------------------------------------------------------------------
# Logging - INFO+ to <hook>.log, ERROR to stderr (mirrors Python handlers)
# AKTO_LOG_FILE must be set by the calling hook before logging.
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
# ---------------------------------------------------------------------------
_AKTO_JSON=""
_AKTO_JSON_LEN=0

_json_skip_ws() {
  local i="$1" c
  while [ "$i" -lt "$_AKTO_JSON_LEN" ]; do
    c="${_AKTO_JSON:$i:1}"
    case "$c" in ' '|$'\t'|$'\n'|$'\r') i=$((i+1));; *) break;; esac
  done
  echo "$i"
}
_json_scan_string() {
  local i=$(( $1 + 1 )) c
  while [ "$i" -lt "$_AKTO_JSON_LEN" ]; do
    c="${_AKTO_JSON:$i:1}"
    if [ "$c" = "\\" ]; then i=$((i+2)); continue; fi
    [ "$c" = '"' ] && { echo $((i+1)); return 0; }
    i=$((i+1))
  done
  echo "$i"
}
_json_scan_value() {
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
    *)
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
    *) printf '%s' "$raw";;
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
# Param order matches the Python build_http_proxy_url shapes:
#   prompt/pre-tool: akto_connector first, then guardrails, then ingest_data
#   post-tool:       response_guardrails first, then akto_connector, then ingest
# We expose flags and reproduce the exact param ordering per hook via callers.
# ---------------------------------------------------------------------------
# build_proxy_url MODE_KIND : MODE_KIND in {prompt_guard, ingest, resp_guard, resp_ingest}
# These match exactly the Python query-string orderings.
build_proxy_url() {  # KIND
  local kind="$1"
  case "$kind" in
    prompt_guard)  # guardrails=True ingest_data=False  -> akto_connector, guardrails
      printf '%s/api/http-proxy?akto_connector=%s&guardrails=true' "$AKTO_DATA_INGESTION_URL" "$CFG_CONNECTOR";;
    ingest)        # guardrails=False ingest_data=True   -> akto_connector, ingest_data
      printf '%s/api/http-proxy?akto_connector=%s&ingest_data=true' "$AKTO_DATA_INGESTION_URL" "$CFG_CONNECTOR";;
    resp_guard)    # response_guardrails=True ingest=False -> response_guardrails, akto_connector
      printf '%s/api/http-proxy?response_guardrails=true&akto_connector=%s' "$AKTO_DATA_INGESTION_URL" "$CFG_CONNECTOR";;
    resp_ingest)   # response_guardrails=False ingest=True -> akto_connector, ingest_data
      printf '%s/api/http-proxy?akto_connector=%s&ingest_data=true' "$AKTO_DATA_INGESTION_URL" "$CFG_CONNECTOR";;
    resp_guard_ingest) # response_guardrails=True ingest=True
      printf '%s/api/http-proxy?response_guardrails=true&akto_connector=%s&ingest_data=true' "$AKTO_DATA_INGESTION_URL" "$CFG_CONNECTOR";;
  esac
}

# post_payload_json URL JSON_BODY -> echoes response body (raw). returns curl exit code.
post_payload_json() {
  local url="$1" body="$2"
  log_info "API CALL: POST $url"
  [ "$AKTO_LOG_PAYLOADS" = "true" ] && log INFO "Payload: $body"
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
  log_info "Response: ${#resp} bytes"
  [ "$AKTO_LOG_PAYLOADS" = "true" ] && log INFO "Response body: $resp"
  printf '%s' "$resp"
  return 0
}

# ---------------------------------------------------------------------------
# guardrails result parsing
# Sets globals: GR_ALLOWED GR_REASON GR_BEHAVIOUR
# ---------------------------------------------------------------------------
parse_guardrails_result() {
  local resp="$1"
  GR_ALLOWED="true"; GR_REASON=""; GR_BEHAVIOUR=""
  local data gr
  data="$(json_raw "$resp" "data")" || { return 0; }
  gr="$(json_raw "$data" "guardrailsResult")" || { return 0; }
  local a; a="$(json_bool "$gr" "Allowed")"; [ -n "$a" ] && GR_ALLOWED="$a"
  GR_REASON="$(json_string "$gr" "Reason" 2>/dev/null)"
  GR_BEHAVIOUR="$(json_string "$gr" "behaviour" 2>/dev/null)"
  [ -z "$GR_BEHAVIOUR" ] && GR_BEHAVIOUR="$(json_string "$gr" "Behaviour" 2>/dev/null)"
}

behaviour_lc() { printf '%s' "$1" | tr '[:upper:]' '[:lower:]' | sed 's/^ *//;s/ *$//'; }
is_warn_behaviour()  { [ "$(behaviour_lc "$1")" = "warn" ]; }
is_alert_behaviour() { [ "$(behaviour_lc "$1")" = "alert" ]; }

# ---------------------------------------------------------------------------
# warn-pending state file (JSON {"warn_pending":[...]})
# ---------------------------------------------------------------------------
warn_pending_has() {  # FILE FP
  local f="$1" fp="$2"
  [ -f "$f" ] || return 1
  grep -q "\"$fp\"" "$f" 2>/dev/null
}
_warn_pending_list() {  # FILE -> echoes one fp per line
  local f="$1"
  [ -f "$f" ] || return 0
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

# ---------------------------------------------------------------------------
# Heartbeat publisher (mirrors akto_heartbeat.py send_heartbeat)
# Rate-limited to once per HEARTBEAT_INTERVAL_S via a file timestamp.
# Errors are swallowed - heartbeat must never affect hook behaviour.
# Usage: send_heartbeat LOG_DIR
# ---------------------------------------------------------------------------
_hb_should_send() {  # LOG_DIR
  local f="$1/last_heartbeat" last now
  [ -f "$f" ] || return 0
  last="$(cat "$f" 2>/dev/null)"
  case "$last" in ''|*[!0-9.]*) return 0;; esac
  now="$(date +%s)"
  # integer compare on seconds (strip fractional part of stored value)
  local last_i="${last%%.*}"
  [ -z "$last_i" ] && return 0
  [ "$((now - last_i))" -ge "$HEARTBEAT_INTERVAL_S" ]
}
_hb_record_send() {  # LOG_DIR
  printf '%s' "$(date +%s)" > "$1/last_heartbeat" 2>/dev/null || true
}
_hb_agent_id() {  # LOG_DIR  -> echoes persistent ns-timestamp agent id
  local f="$1/agent_id" id
  id="$(cat "$f" 2>/dev/null)"
  if [ -n "$id" ]; then printf '%s' "$id"; return 0; fi
  # nanosecond-ish: prefer %N if supported, else seconds*1e9
  local ns; ns="$(date +%s%N 2>/dev/null)"
  case "$ns" in *N|'') ns="$(( $(date +%s) * 1000000000 ))";; esac
  printf '%s' "$ns" > "$f" 2>/dev/null || true
  printf '%s' "$ns"
}
send_heartbeat() {  # LOG_DIR
  local ld="$1"
  mkdir -p "$ld" 2>/dev/null || true
  _hb_should_send "$ld" || { log_info "Heartbeat skipped (within rate-limit window)"; return 0; }
  local device_id username agent_id now body url
  device_id="${DEVICE_ID:-$(get_machine_id)}"
  username="$(get_username)"
  agent_id="$(_hb_agent_id "$ld")"
  now="$(date +%s)"
  body="$(printf '{"moduleInfo":{"id":%s,"name":%s,"moduleType":%s,"currentVersion":%s,"startedTs":%s,"lastHeartbeatReceived":%s,"additionalData":{"username":%s,"mcpServers":{}}}}' \
    "$(json_str "$agent_id")" "$(json_str "$device_id")" "$(json_str "$HEARTBEAT_MODULE_TYPE")" \
    "$(json_str "$HEARTBEAT_VERSION")" "$now" "$now" "$(json_str "$username")")"
  url="${DB_ABSTRACTOR_URL}/api/updateModuleInfoForHeartbeat"
  if [ -n "$AKTO_API_TOKEN" ]; then
    printf '%s' "$body" | curl -sS -k --max-time "$HEARTBEAT_TIMEOUT" -X POST "$url" \
      -H "Content-Type: application/json" -H "Authorization: $AKTO_API_TOKEN" \
      --data-binary @- >/dev/null 2>>"${AKTO_LOG_FILE:-/dev/null}" || { log_info "Heartbeat skipped (request failed)"; return 0; }
  else
    printf '%s' "$body" | curl -sS -k --max-time "$HEARTBEAT_TIMEOUT" -X POST "$url" \
      -H "Content-Type: application/json" \
      --data-binary @- >/dev/null 2>>"${AKTO_LOG_FILE:-/dev/null}" || { log_info "Heartbeat skipped (request failed)"; return 0; }
  fi
  _hb_record_send "$ld"
  log_info "Heartbeat sent: agentId=$agent_id, deviceId=$device_id, username=$username"
}

# ---------------------------------------------------------------------------
# MCP tool-name detection (mirrors parse_github_tool):
#   - underscore form: mcp_<server>_<tool>  (split on first underscore after prefix)
#   - hyphen form:     <server>-<tool>      (split on LAST hyphen)
# Sets IS_MCP(0/1) MCP_SERVER MCP_TOOL
# ---------------------------------------------------------------------------
parse_github_tool() {  # TOOLNAME
  local tn="$1"; IS_MCP=0; MCP_SERVER=""; MCP_TOOL=""
  case "$tn" in
    mcp_*)
      local rest="${tn#mcp_}"
      case "$rest" in
        *_*)
          local s="${rest%%_*}" t="${rest#*_}"
          if [ -n "$s" ] && [ -n "$t" ]; then MCP_SERVER="$s"; MCP_TOOL="$t"; IS_MCP=1; return 0; fi;;
      esac;;
  esac
  case "$tn" in
    *-*)
      local s="${tn%-*}" t="${tn##*-}"
      if [ -n "$s" ] && [ -n "$t" ]; then MCP_SERVER="$s"; MCP_TOOL="$t"; IS_MCP=1; return 0; fi;;
  esac
  return 0
}

# jsonrpc arguments object: tool_input if object, {"input":...} otherwise, {} if empty
jsonrpc_arguments() {  # TOOL_INPUT_RAW
  local ti="$1"
  case "$ti" in
    '{'*) printf '%s' "$ti";;
    ''|null) printf '{}';;
    *) printf '{"input":%s}' "$ti";;
  esac
}
build_tools_call_jsonrpc() {  # MCP_TOOL TOOL_INPUT_RAW
  printf '{"jsonrpc":"2.0","method":"tools/call","params":{"name":%s,"arguments":%s},"id":1}' \
    "$(json_str "$1")" "$(jsonrpc_arguments "$2")"
}
build_tools_call_result_jsonrpc() {  # TOOL_RESPONSE_RAW
  local tr="$1" result_body
  case "$tr" in '{'*) result_body="$tr";; ''|null) result_body='{"output":null}';; *) result_body="{\"output\":$tr}";; esac
  printf '{"jsonrpc":"2.0","id":1,"result":%s}' "$result_body"
}

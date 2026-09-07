#!/bin/bash
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/akto_common.sh"

LOGFILE="validate-prompt.log"

if ! akto_check_deps "$LOGFILE"; then
  exit 0
fi

MODE="$(_akto_lower "${MODE:-atlas}")"
AKTO_DATA_INGESTION_URL="${AKTO_DATA_INGESTION_URL:-}"
AKTO_DATA_INGESTION_URL="${AKTO_DATA_INGESTION_URL%/}"
AKTO_TIMEOUT="${AKTO_TIMEOUT:-5}"
AKTO_SYNC_MODE="$([[ "$(_akto_lower "${AKTO_SYNC_MODE:-true}")" == "true" ]] && echo true || echo false)"
AKTO_API_TOKEN="${AKTO_API_TOKEN:-}"
CONTEXT_SOURCE="${CONTEXT_SOURCE:-ENDPOINT}"
LOG_PAYLOADS="$(_akto_lower "${LOG_PAYLOADS:-false}")"

input_data=$(cat)
if ! jq -e . >/dev/null 2>&1 <<<"$input_data"; then
  log_error "$LOGFILE" "Invalid JSON input"
  exit 0
fi
input_data=$(_akto_alias_camel_keys <<<"$input_data")

connector="$(detect_connector "$input_data")"
get_connector_config "$connector"

LOG_DIR="${LOG_DIR:-$CFG_LOG_DIR_DEFAULT}"
LOG_DIR="${LOG_DIR/#\~/$HOME}"
export LOG_DIR
mkdir -p "$LOG_DIR" 2>/dev/null
SESSION_STATE_PATH="${LOG_DIR}/akto_session_state.json"

warn_state_path="$LOG_DIR/akto_prompt_warn_pending.json"

session_info=$(resolve_session_info "$input_data" "true")
session_id=$(jq -r '.session_id // empty' <<<"$input_data")
message_id=$(jq -r '.current_message_id // empty' <<<"$session_info")
cfg_session_headers=$(session_headers "$session_id" "$message_id")

log_info "$LOGFILE" "=== User Prompt Submitted Hook - Connector: $connector, Mode: $MODE, Sync: $AKTO_SYNC_MODE ==="
[[ "$LOG_PAYLOADS" == "true" ]] && log_info "$LOGFILE" "Input: $(jq -c . <<<"$input_data")"
log_info "$LOGFILE" "MODE: $MODE, API_URL: $CFG_API_URL"

prompt=$(jq -r '.prompt // ""' <<<"$input_data")
cwd=$(jq -r '.cwd // ""' <<<"$input_data")
now_ms=$(( $(date +%s) * 1000 ))
timestamp=$(jq -r --argjson now "$now_ms" '
  .timestamp as $t
  | if ($t|type)=="string" then
      (try (($t | sub("Z$";"+00:00") | fromdateiso8601) * 1000 | floor) catch $now)
    elif ($t|type)=="number" then ($t|floor)
    else $now end
' <<<"$input_data" 2>/dev/null)
[[ -z "$timestamp" || "$timestamp" == "null" ]] && timestamp="$now_ms"

log_info "$LOGFILE" "Prompt length: ${#prompt} chars, CWD: $cwd"

trimmed="$(printf '%s' "$prompt" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
if [[ -z "$trimmed" ]]; then
  log_info "$LOGFILE" "Empty prompt, skipping validation"
  exit 0
fi

build_akto_request() {
  local prompt="$1" timestamp="$2"
  local device_id host tags
  device_id="${DEVICE_ID:-$(get_machine_id)}"
  host="${CFG_API_URL#https://}"; host="${host#http://}"
  tags=$(jq -n -c --arg tag "$CFG_AI_AGENT_TAG" --arg src "$CONTEXT_SOURCE" --arg mode "$MODE" '
    {"gen-ai":"Gen AI"} + (if $mode=="atlas" then {"ai-agent":$tag,"source":$src} else {} end)
  ')
  jq -n -c \
    --arg host "$host" --arg hh "$CFG_HOOK_HEADER" \
    --argjson sh "$cfg_session_headers" \
    --arg prompt "$prompt" --arg ts "$timestamp" \
    --arg ip "$(get_username)" --arg akvx "$device_id" \
    --argjson tag "$tags" --arg ctxsrc "$CONTEXT_SOURCE" '
    {
      path: "/copilot/chat",
      requestHeaders: (({host:$host} + {($hh):"UserPromptSubmitted"} + {"content-type":"application/json"} + $sh) | tojson),
      responseHeaders: ({($hh):"UserPromptSubmitted"} | tojson),
      method: "POST",
      requestPayload: ({body: $prompt} | tojson),
      responsePayload: ({} | tojson),
      ip: $ip, destIp: "127.0.0.1", time: $ts,
      statusCode: "200", type: "HTTP/1.1", status: "200",
      akto_account_id: "1000000", akto_vxlan_id: $akvx, is_pending: "false",
      source: "MIRRORING", direction: null, process_id: null, socket_id: null,
      daemonset_id: null, enabled_graph: null,
      tag: ($tag | tojson), metadata: ($tag | tojson), contextSource: "ENDPOINT"
    }
  '
}

build_http_proxy_url() {
  local guardrails="$1" ingest_data="$2"
  local params=("akto_connector=${CFG_CONNECTOR}")
  [[ "$guardrails" == "true" ]] && params+=("guardrails=true")
  [[ "$ingest_data" == "true" ]] && params+=("ingest_data=true")
  local IFS='&'
  printf '%s/api/http-proxy?%s' "$AKTO_DATA_INGESTION_URL" "${params[*]}"
}

prompt_fingerprint() {
  jq -n -c --arg p "$1" '{p:$p}' | jq -S -c . | _akto_sha256
}

ingest_request() {
  local prompt="$1" timestamp="$2" reason="$3" blocked="$4"
  [[ -z "$AKTO_DATA_INGESTION_URL" ]] && return 0
  log_info "$LOGFILE" "Ingesting $([[ "$blocked" == "true" ]] && echo blocked || echo allowed) request"
  local body
  body=$(build_akto_request "$prompt" "$timestamp")
  if [[ "$blocked" == "true" ]]; then
    body=$(jq -c --arg reason "$reason" '
      .responsePayload = ({body: ({"x-blocked-by":"Akto Proxy", reason:$reason} | tojson)} | tojson)
      | .statusCode = "403" | .status = "403"
    ' <<<"$body")
  fi
  akto_post_json "$(build_http_proxy_url false true)" "$body" "$LOGFILE" >/dev/null
  log_info "$LOGFILE" "$([[ "$blocked" == "true" ]] && echo Blocked || echo Allowed) request ingested successfully"
}

if [[ "$AKTO_SYNC_MODE" == "true" && -n "$AKTO_DATA_INGESTION_URL" ]]; then
  log_info "$LOGFILE" "Validating prompt against guardrails"

  gr_allowed=true; gr_reason=""; behaviour=""
  request_body=$(build_akto_request "$prompt" "$timestamp")
  if result=$(akto_post_json "$(build_http_proxy_url true false)" "$request_body" "$LOGFILE" 2>/dev/null); then
    gr_allowed=$(jq -r '(.data.guardrailsResult.Allowed | if . == null then true else . end)' <<<"$result" 2>/dev/null)
    [[ "$gr_allowed" == "null" || -z "$gr_allowed" ]] && gr_allowed=true
    gr_reason=$(jq -r '.data.guardrailsResult.Reason // ""' <<<"$result" 2>/dev/null)
    behaviour=$(jq -r '(.data.guardrailsResult.behaviour // .data.guardrailsResult.Behaviour // "")' <<<"$result" 2>/dev/null)
    if [[ "$gr_allowed" == "true" ]]; then
      log_info "$LOGFILE" "Prompt ALLOWED by guardrails"
    else
      log_warn "$LOGFILE" "Prompt DENIED by guardrails: $gr_reason"
    fi
  fi

  fingerprint=$(prompt_fingerprint "$prompt")
  allowed=$(apply_warn_resubmit_flow "$gr_allowed" "$behaviour" "$fingerprint" "$warn_state_path" "$LOGFILE")

  if [[ "$allowed" != "true" ]]; then
    b_lower=$(_akto_lower_trim "$behaviour")
    if [[ "$b_lower" == "warn" ]]; then
      block_reason="Warning!!, prompt blocked, please review it. Send again to bypass. Reason for blocking: $gr_reason"
    else
      block_reason="Prompt blocked: $gr_reason"
    fi

    log_warn "$LOGFILE" "BLOCKING prompt - Reason: $block_reason"
    ingest_request "$prompt" "$timestamp" "$gr_reason" "true"
    printf '\xe2\x9a\xa0\xef\xb8\x8f  Akto Guardrails flagged prompt: %s\n' "${gr_reason:-Policy violation}" >&2
    jq -n -c --arg r "$block_reason" '{continue:false, stopReason:$r}'
    exit "$CFG_BLOCKED_EXIT_CODE"
  fi

  ingest_request "$prompt" "$timestamp" "" "false"
fi

log_info "$LOGFILE" "Hook completed"
exit 0

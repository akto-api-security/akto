#!/bin/bash
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

LOG_DIR="${LOG_DIR:-$HOME/.codex/akto/logs}"
LOG_DIR="${LOG_DIR/#\~/$HOME}"
export LOG_DIR
mkdir -p "$LOG_DIR" 2>/dev/null

source "$SCRIPT_DIR/akto_common.sh"

get_username >/dev/null 2>&1

LOGFILE="validate-prompt.log"
akto_check_deps "$LOGFILE" || exit 0
LOG_PAYLOADS="$(_akto_lower "${LOG_PAYLOADS:-false}")"

MODE="$(_akto_lower "${MODE:-atlas}")"
AKTO_DATA_INGESTION_URL="${AKTO_DATA_INGESTION_URL:-}"
AKTO_DATA_INGESTION_URL="${AKTO_DATA_INGESTION_URL%/}"
AKTO_TIMEOUT="${AKTO_TIMEOUT:-5}"
AKTO_SYNC_MODE="$(_akto_lower "${AKTO_SYNC_MODE:-true}")"
AKTO_CONNECTOR="${AKTO_CONNECTOR:-codex_cli}"
AKTO_API_TOKEN="${AKTO_API_TOKEN:-}"
CONTEXT_SOURCE="${CONTEXT_SOURCE:-ENDPOINT}"
WARN_STATE_PATH="$LOG_DIR/akto_prompt_warn_pending.json"

_detect_codex_api() {
  if [[ -n "${OPENAI_BASE_URL:-}" ]]; then
    DETECTED_HOST="${OPENAI_BASE_URL%/}"; CODEX_API_PATH="/v1/responses"; return
  fi
  if [[ -n "${OPENAI_API_KEY:-}" ]]; then
    DETECTED_HOST="https://api.openai.com"; CODEX_API_PATH="/v1/responses"; return
  fi
  DETECTED_HOST="https://chatgpt.com"; CODEX_API_PATH="/backend-api/codex/responses"
}
_detect_codex_api

if [[ "$MODE" == "atlas" ]]; then
  DEVICE_ID="${DEVICE_ID:-$(get_machine_id)}"
  if [[ -n "$DEVICE_ID" ]]; then
    CODEX_API_HOST="https://${DEVICE_ID}.ai-agent.codexcli"
  else
    CODEX_API_HOST="$DETECTED_HOST"
  fi
  log_info "$LOGFILE" "MODE: $MODE, Device ID: $DEVICE_ID, CODEX_API_HOST: $CODEX_API_HOST, CODEX_API_PATH: $CODEX_API_PATH"
else
  CODEX_API_HOST="$DETECTED_HOST"
  log_info "$LOGFILE" "MODE: $MODE, CODEX_API_HOST: $CODEX_API_HOST, CODEX_API_PATH: $CODEX_API_PATH"
fi

build_http_proxy_url() {
  local guardrails="$1" ingest_data="$2"
  local params=()
  [[ "$guardrails" == "true" ]] && params+=("guardrails=true")
  params+=("akto_connector=${AKTO_CONNECTOR}")
  [[ "$ingest_data" == "true" ]] && params+=("ingest_data=true")
  local IFS='&'
  printf '%s/api/http-proxy?%s' "$AKTO_DATA_INGESTION_URL" "${params[*]}"
}

build_validation_request() {
  local query="$1" session_info_json="${2:-null}"
  local host installer_hdrs req_headers now_ms

  host="${CODEX_API_HOST#https://}"; host="${host#http://}"
  installer_hdrs='{}'
  [[ "$session_info_json" != "null" ]] && installer_hdrs=$(_akto_installer_headers "$session_info_json" "null")
  now_ms=$(( $(date +%s) * 1000 ))

  jq -n -c \
    --arg mode "$MODE" --arg src "$CONTEXT_SOURCE" \
    --arg host "$host" --argjson inst "$installer_hdrs" --arg path "$CODEX_API_PATH" \
    --arg q "$query" --arg ip "$(get_username)" --arg time "$now_ms" --arg akvx "$DEVICE_ID" '
    ($q | gsub("^\\s+|\\s+$";"")) as $qtrim
    | ({"gen-ai":"Gen AI"} + (if $mode=="atlas" then {"ai-agent":"codexcli","source":$src} else {} end)) as $tags
    | ({"host":$host,"x-codex-hook":"UserPromptSubmit","content-type":"application/json"} + $inst) as $reqh
    | {
        path: $path,
        requestHeaders: ($reqh|tojson),
        responseHeaders: ({"x-codex-hook":"UserPromptSubmit"}|tojson),
        method: "POST",
        requestPayload: ({body:$qtrim}|tojson),
        responsePayload: ({}|tojson),
        ip: $ip, destIp: "127.0.0.1", time: $time,
        statusCode: "200", type: "HTTP/1.1", status: "200",
        akto_account_id: "1000000", akto_vxlan_id: $akvx, is_pending: "false",
        source: "MIRRORING", direction: null, process_id: null, socket_id: null,
        daemonset_id: null, enabled_graph: null,
        tag: ($tags|tojson), metadata: ($tags|tojson), contextSource: $src
      }
  '
}

call_guardrails() {
  local query="$1" session_info_json="${2:-null}"
  if [[ -z "${query//[$'\t\r\n ']/}" ]]; then
    jq -n -c '{allowed:true,reason:"",behaviour:""}'
    return
  fi
  if [[ -z "$AKTO_DATA_INGESTION_URL" ]]; then
    log_warn "$LOGFILE" "AKTO_DATA_INGESTION_URL not set, allowing prompt (fail-open)"
    jq -n -c '{allowed:true,reason:"",behaviour:""}'
    return
  fi

  log_info "$LOGFILE" "Validating prompt against guardrails"
  if [[ "$LOG_PAYLOADS" == "true" ]]; then
    log_info "$LOGFILE" "Prompt: ${query:0:200}..."
  else
    log_info "$LOGFILE" "Prompt preview: ${query:0:100}..."
  fi

  local req_body url result rc
  req_body=$(build_validation_request "$query" "$session_info_json")
  url=$(build_http_proxy_url "true" "true")
  result=$(akto_post_json "$url" "$req_body" "$LOGFILE")
  rc=$?
  if [[ $rc -ne 0 || -z "$result" ]]; then
    log_error "$LOGFILE" "Guardrails validation error"
    jq -n -c '{allowed:true,reason:"",behaviour:""}'
    return
  fi

  local parsed
  parsed=$(jq -c '
    (.data.guardrailsResult // {}) as $gr
    | { allowed: (if ($gr.Allowed == null) then true else $gr.Allowed end), reason: ($gr.Reason // ""), behaviour: ($gr.behaviour // $gr.Behaviour // "") }
  ' <<<"$result" 2>/dev/null)
  if [[ -z "$parsed" ]]; then
    jq -n -c '{allowed:true,reason:"",behaviour:""}'
    return
  fi

  if [[ "$(jq -r '.allowed' <<<"$parsed")" == "true" ]]; then
    log_info "$LOGFILE" "Prompt ALLOWED by guardrails"
  else
    log_warn "$LOGFILE" "Prompt DENIED by guardrails: $(jq -r '.reason' <<<"$parsed")"
  fi
  printf '%s' "$parsed"
}

prompt_fingerprint() {
  local prompt="$1"
  jq -n -c -S --arg p "$prompt" '{p:$p,a:[]}' | _akto_sha256
}

ingest_blocked_request() {
  local prompt="$1" reason="$2" session_info_json="${3:-null}"
  [[ -z "$AKTO_DATA_INGESTION_URL" || "$AKTO_SYNC_MODE" != "true" ]] && return
  log_info "$LOGFILE" "Ingesting blocked request data"

  local req_body url
  req_body=$(build_validation_request "$prompt" "$session_info_json")
  req_body=$(jq -c --arg reason "${reason:-Policy violation}" '
    .responseHeaders = ({"x-codex-hook":"UserPromptSubmit","x-blocked-by":"Akto Proxy","content-type":"application/json"} | tojson)
    | .responsePayload = ({body: ({"x-blocked-by":"Akto Proxy","reason":$reason} | tojson)} | tojson)
    | .statusCode = "403" | .status = "403"
  ' <<<"$req_body")
  url=$(build_http_proxy_url "false" "true")
  if akto_post_json "$url" "$req_body" "$LOGFILE" >/dev/null; then
    log_info "$LOGFILE" "Blocked request ingestion successful"
  else
    log_error "$LOGFILE" "Ingestion error"
  fi
}

main() {
  log_info "$LOGFILE" "=== Hook execution started - Mode: $MODE, Sync: $AKTO_SYNC_MODE ==="

  local input_data
  input_data=$(cat)
  if ! jq -e . >/dev/null 2>&1 <<<"$input_data"; then
    log_error "$LOGFILE" "Invalid JSON input"
    exit 0
  fi

  local prompt session_id
  prompt=$(jq -r '.prompt // ""' <<<"$input_data")
  session_id=$(jq -r '.session_id // ""' <<<"$input_data")
  local session_info
  session_info=$(resolve_session_info "$input_data" "true")

  log_info "$LOGFILE" "Session: $session_id, Hook: $(jq -r '.hook_event_name // "UserPromptSubmit"' <<<"$input_data")"

  if [[ -z "${prompt//[$'\t\r\n ']/}" ]]; then
    log_info "$LOGFILE" "Empty prompt, allowing"
    exit 0
  fi
  log_info "$LOGFILE" "Processing prompt (length: ${#prompt} chars)"

  if [[ "$AKTO_SYNC_MODE" == "true" ]]; then
    local gr_json gr_allowed gr_reason behaviour fingerprint allowed
    gr_json=$(call_guardrails "$prompt" "$session_info")
    gr_allowed=$(jq -r '.allowed' <<<"$gr_json")
    gr_reason=$(jq -r '.reason' <<<"$gr_json")
    behaviour=$(jq -r '.behaviour' <<<"$gr_json")
    fingerprint=$(prompt_fingerprint "$prompt")
    allowed=$(apply_warn_resubmit_flow "$gr_allowed" "$behaviour" "$fingerprint" "$WARN_STATE_PATH" "$LOGFILE")

    if [[ "$allowed" != "true" ]]; then
      local block_reason
      if [[ "$(_akto_lower_trim "$behaviour")" == "warn" ]]; then
        block_reason="Warning!!, prompt blocked, please review it. Send again to bypass. Reason for blocking: ${gr_reason}"
      else
        block_reason="Prompt blocked: ${gr_reason}"
      fi
      jq -n -c --arg r "$block_reason" '{decision:"block", reason:$r}'
      log_warn "$LOGFILE" "BLOCKING prompt - Reason: $gr_reason"
      ingest_blocked_request "$prompt" "${gr_reason:-Policy violation}" "$session_info"
      exit 0
    fi
  fi

  log_info "$LOGFILE" "Prompt allowed"
  exit 0
}

main

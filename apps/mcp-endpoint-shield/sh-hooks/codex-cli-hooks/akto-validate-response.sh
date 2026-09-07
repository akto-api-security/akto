#!/bin/bash
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

LOG_DIR="${LOG_DIR:-$HOME/.codex/akto/logs}"
LOG_DIR="${LOG_DIR/#\~/$HOME}"
export LOG_DIR
mkdir -p "$LOG_DIR" 2>/dev/null

source "$SCRIPT_DIR/akto_common.sh"

LOGFILE="validate-response.log"
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
WARN_STATE_PATH="$LOG_DIR/akto_response_warn_pending.json"

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
  local guardrails="$1" response_guardrails="$2" ingest_data="$3"
  local params=()
  [[ "$guardrails" == "true" ]] && params+=("guardrails=true")
  [[ "$response_guardrails" == "true" ]] && params+=("response_guardrails=true")
  params+=("akto_connector=${AKTO_CONNECTOR}")
  [[ "$ingest_data" == "true" ]] && params+=("ingest_data=true")
  local IFS='&'
  printf '%s/api/http-proxy?%s' "$AKTO_DATA_INGESTION_URL" "${params[*]}"
}

build_ingestion_payload() {
  local user_prompt="$1" response_text="$2" session_info_json="${3:-null}"
  local host installer_hdrs now_ms

  host="${CODEX_API_HOST#https://}"; host="${host#http://}"
  installer_hdrs='{}'
  [[ "$session_info_json" != "null" ]] && installer_hdrs=$(_akto_installer_headers "$session_info_json" "null")
  now_ms=$(( $(date +%s) * 1000 ))

  jq -n -c \
    --arg mode "$MODE" --arg src "$CONTEXT_SOURCE" --arg path "$CODEX_API_PATH" \
    --arg host "$host" --argjson inst "$installer_hdrs" \
    --arg p "$user_prompt" --arg r "$response_text" \
    --arg ip "$(get_username)" --arg time "$now_ms" '
    ({"gen-ai":"Gen AI"} + (if $mode=="atlas" then {"ai-agent":"codexcli","source":$src} else {} end)) as $tags
    | ({"host":$host,"x-codex-hook":"Stop","content-type":"application/json"} + $inst) as $reqh
    | {
        path: $path,
        requestHeaders: ($reqh|tojson),
        responseHeaders: ({"x-codex-hook":"Stop","content-type":"application/json"}|tojson),
        method: "POST",
        requestPayload: ({body:$p}|tojson),
        responsePayload: ({body:$r}|tojson),
        ip: $ip, destIp: "127.0.0.1", time: $time,
        statusCode: "200", type: "HTTP/1.1", status: "200",
        akto_account_id: "1000000", akto_vxlan_id: 0, is_pending: "false",
        source: "MIRRORING", direction: null, process_id: null, socket_id: null,
        daemonset_id: null, enabled_graph: null,
        tag: ($tags|tojson), metadata: ($tags|tojson), contextSource: $src
      }
  '
}

call_guardrails() {
  local user_prompt="$1" response_text="$2" session_info_json="${3:-null}"
  if [[ -z "${response_text//[$'\t\r\n ']/}" ]]; then
    jq -n -c '{allowed:true,reason:"",behaviour:""}'
    return
  fi
  if [[ -z "$AKTO_DATA_INGESTION_URL" ]]; then
    log_warn "$LOGFILE" "AKTO_DATA_INGESTION_URL not set, allowing response (fail-open)"
    jq -n -c '{allowed:true,reason:"",behaviour:""}'
    return
  fi

  log_info "$LOGFILE" "Validating assistant response against guardrails"
  if [[ "$LOG_PAYLOADS" == "true" ]]; then
    log_info "$LOGFILE" "Response: ${response_text:0:200}..."
  else
    log_info "$LOGFILE" "Response preview: ${response_text:0:100}..."
  fi

  local req_body url result rc
  req_body=$(build_ingestion_payload "$user_prompt" "$response_text" "$session_info_json")
  url=$(build_http_proxy_url "false" "true" "false")
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
    log_info "$LOGFILE" "Response ALLOWED by guardrails"
  else
    log_warn "$LOGFILE" "Response DENIED by guardrails: $(jq -r '.reason' <<<"$parsed")"
  fi
  printf '%s' "$parsed"
}

response_fingerprint() {
  local user_prompt="$1" response_text="$2"
  jq -n -c -S --arg p "$user_prompt" --arg r "$response_text" '{p:$p,r:$r}' | _akto_sha256
}

ingest_blocked_response() {
  local user_prompt="$1" response_text="$2" reason="$3" session_info_json="${4:-null}"
  [[ -z "$AKTO_DATA_INGESTION_URL" || "$AKTO_SYNC_MODE" != "true" ]] && return
  log_info "$LOGFILE" "Ingesting blocked request data"

  local req_body url
  req_body=$(build_ingestion_payload "$user_prompt" "$response_text" "$session_info_json")
  req_body=$(jq -c --arg reason "${reason:-Policy violation}" '
    .responseHeaders = ({"x-codex-hook":"Stop","x-blocked-by":"Akto Proxy","content-type":"application/json"} | tojson)
    | .responsePayload = ({body: ({"x-blocked-by":"Akto Proxy","reason":$reason} | tojson)} | tojson)
    | .statusCode = "403" | .status = "403"
  ' <<<"$req_body")
  url=$(build_http_proxy_url "false" "false" "true")
  if akto_post_json "$url" "$req_body" "$LOGFILE" >/dev/null; then
    log_info "$LOGFILE" "Blocked request ingestion successful"
  else
    log_error "$LOGFILE" "Ingestion error"
  fi
}

send_ingestion_data() {
  local user_prompt="$1" response_text="$2" session_info_json="${3:-null}"
  [[ -z "$AKTO_DATA_INGESTION_URL" ]] && { log_info "$LOGFILE" "AKTO_DATA_INGESTION_URL not set, skipping ingestion"; return; }
  [[ -z "${user_prompt//[$'\t\r\n ']/}" || -z "${response_text//[$'\t\r\n ']/}" ]] && return

  log_info "$LOGFILE" "Ingesting conversation data"
  if [[ "$LOG_PAYLOADS" == "true" ]]; then
    log_info "$LOGFILE" "Prompt: ${user_prompt:0:200}..."
    log_info "$LOGFILE" "Response: ${response_text:0:200}..."
  else
    log_info "$LOGFILE" "Prompt preview: ${user_prompt:0:100}..."
    log_info "$LOGFILE" "Response preview: ${response_text:0:100}..."
  fi

  local req_body url resp_guardrails
  req_body=$(build_ingestion_payload "$user_prompt" "$response_text" "$session_info_json")
  resp_guardrails="true"
  [[ "$AKTO_SYNC_MODE" == "true" ]] && resp_guardrails="false"
  url=$(build_http_proxy_url "false" "$resp_guardrails" "true")
  if akto_post_json "$url" "$req_body" "$LOGFILE" >/dev/null; then
    log_info "$LOGFILE" "Conversation ingestion successful"
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

  local session_info
  session_info=$(resolve_session_info "$input_data" "false")

  local transcript_path
  transcript_path=$(jq -r '.transcript_path // ""' <<<"$input_data")
  if [[ -z "$transcript_path" ]]; then
    log_info "$LOGFILE" "No transcript path provided"
    exit 0
  fi
  transcript_path="${transcript_path/#\~/$HOME}"
  log_info "$LOGFILE" "Reading transcript from: $transcript_path"

  local response_text stop_hook_active user_prompt
  response_text=$(jq -r '.last_assistant_message // "" | gsub("^\\s+|\\s+$";"")' <<<"$input_data")
  stop_hook_active=$(jq -r 'if (.stop_hook_active // false) then "true" else "false" end' <<<"$input_data")
  user_prompt=$(get_last_user_prompt "$transcript_path")

  if [[ -z "$user_prompt" || -z "$response_text" ]]; then
    log_info "$LOGFILE" "No complete interaction found in transcript"
    exit 0
  fi
  log_info "$LOGFILE" "Extracted interaction - Prompt: ${#user_prompt} chars, Response: ${#response_text} chars"

  if [[ "$stop_hook_active" == "true" ]]; then
    log_info "$LOGFILE" "stop_hook_active=true: skipping guardrails block to avoid Stop hook loops"
  fi

  if [[ "$AKTO_SYNC_MODE" == "true" && "$stop_hook_active" != "true" ]]; then
    local gr_json gr_allowed gr_reason behaviour fingerprint allowed
    gr_json=$(call_guardrails "$user_prompt" "$response_text" "$session_info")
    gr_allowed=$(jq -r '.allowed' <<<"$gr_json")
    gr_reason=$(jq -r '.reason' <<<"$gr_json")
    behaviour=$(jq -r '.behaviour' <<<"$gr_json")
    fingerprint=$(response_fingerprint "$user_prompt" "$response_text")
    allowed=$(apply_warn_resubmit_flow "$gr_allowed" "$behaviour" "$fingerprint" "$WARN_STATE_PATH" "$LOGFILE")

    if [[ "$allowed" != "true" ]]; then
      local is_warn block_reason
      is_warn="false"
      [[ "$(_akto_lower_trim "$behaviour")" == "warn" ]] && is_warn="true"
      if [[ "$is_warn" == "true" ]]; then
        block_reason="Warning!!, response blocked, please review it. Send again to bypass. Reason for blocking: ${gr_reason}"
      else
        block_reason="Response blocked: ${gr_reason}"
      fi

      if [[ "$is_warn" == "true" ]]; then
        jq -n -c --arg r "$block_reason" '{decision:"block", reason:$r}'
      else
        jq -n -c --arg sr "${gr_reason:-Policy violation}" --arg sm "$block_reason" '
          {continue:false, stopReason:$sr, systemMessage:$sm,
           hookSpecificOutput:{hookEventName:"Stop", additionalContext:$sr}}
        '
      fi
      log_warn "$LOGFILE" "BLOCKING Stop - Reason: $gr_reason"
      ingest_blocked_response "$user_prompt" "$response_text" "$gr_reason" "$session_info"
      exit 0
    fi
  fi

  send_ingestion_data "$user_prompt" "$response_text" "$session_info"

  log_info "$LOGFILE" "Hook execution completed"
  exit 0
}

main

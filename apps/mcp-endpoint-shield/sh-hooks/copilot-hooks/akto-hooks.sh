#!/bin/bash
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ -z "${LOG_DIR:-}" ]]; then
  LOG_DIR="$HOME/.copilot/akto/logs"
fi
LOG_DIR="${LOG_DIR/#\~/$HOME}"
export LOG_DIR
mkdir -p "$LOG_DIR" 2>/dev/null

source "$SCRIPT_DIR/akto_common.sh"

get_username >/dev/null 2>&1

MODE="$(_akto_lower "${MODE:-atlas}")"
AKTO_DATA_INGESTION_URL="${AKTO_DATA_INGESTION_URL:-}"
AKTO_API_TOKEN="${AKTO_API_TOKEN:-}"
AKTO_TIMEOUT="${AKTO_TIMEOUT:-5}"
AKTO_CONNECTOR="${AKTO_CONNECTOR:-}"
CONTEXT_SOURCE="${CONTEXT_SOURCE:-ENDPOINT}"
LOG_PAYLOADS="$(_akto_lower "${LOG_PAYLOADS:-false}")"

TAG_NAME="copilot"
_HOOK_HEADER="x-${TAG_NAME}-hook"

DEVICE_ID="${DEVICE_ID:-$(get_machine_id)}"
if [[ "$MODE" == "atlas" && -n "$DEVICE_ID" ]]; then
  AI_AGENT_API_URL="https://${DEVICE_ID}.ai-agent.${TAG_NAME}"
else
  AI_AGENT_API_URL="${AKTO_API_URL:-}"
fi

_akto_hooks_build_http_proxy_url() {
  local guardrails="$1" ingest_data="$2" client_hook="${3:-}"
  local params=()
  [[ "$guardrails" == "true" ]] && params+=("guardrails=true")
  params+=("akto_connector=${AKTO_CONNECTOR}")
  [[ "$ingest_data" == "true" ]] && params+=("ingest_data=true")
  [[ -n "$client_hook" ]] && params+=("client_hook=${client_hook}")
  local IFS='&'
  printf '%s/api/http-proxy?%s' "$AKTO_DATA_INGESTION_URL" "${params[*]}"
}

_akto_hooks_build_ingestion_payload() {
  local hook_name="$1" request_payload_json="$2" response_payload_json="$3" session_info_json="${4:-null}" input_json="${5:-null}" status_code="${6:-200}"
  local device_id host req_headers resp_headers base_tags ip_user now_ms installer_hdrs

  device_id="${DEVICE_ID:-$(get_machine_id)}"
  host="${AI_AGENT_API_URL#https://}"; host="${host#http://}"
  ip_user="$(get_username)"
  now_ms=$(( $(date +%s) * 1000 ))

  base_tags=$(jq -n -c --arg hook "$hook_name" --arg tag "$TAG_NAME" --arg src "$CONTEXT_SOURCE" --arg mode "$MODE" '
    {"gen-ai":"Gen AI","hook":$hook} + (if $mode=="atlas" then {"ai-agent":$tag,"source":$src} else {} end)
  ')

  installer_hdrs='{}'
  [[ "$session_info_json" != "null" ]] && installer_hdrs=$(_akto_installer_headers "$session_info_json" "$input_json")

  req_headers=$(jq -n -c --arg host "$host" --arg hh "$_HOOK_HEADER" --arg hook "$hook_name" --argjson inst "$installer_hdrs" '
    {"host":$host, ($hh):$hook, "content-type":"application/json"} + $inst
  ')
  resp_headers=$(jq -n -c --arg hh "$_HOOK_HEADER" --arg hook "$hook_name" '
    {($hh):$hook, "content-type":"application/json"}
  ')

  jq -n -c \
    --arg hook "$hook_name" \
    --argjson reqh "$req_headers" --argjson resph "$resp_headers" \
    --argjson reqp "$request_payload_json" --argjson resp "$response_payload_json" \
    --arg ip "$ip_user" --arg time "$now_ms" --arg status "$status_code" \
    --arg akvx "$device_id" --argjson tag "$base_tags" --arg ctxsrc "$CONTEXT_SOURCE" '
    {
      path: ("/v1/hooks/" + $hook),
      requestHeaders: ($reqh | tojson),
      responseHeaders: ($resph | tojson),
      method: "POST",
      requestPayload: ({body: $reqp} | tojson),
      responsePayload: ({body: $resp} | tojson),
      ip: $ip, destIp: "127.0.0.1", time: $time,
      statusCode: $status, type: "HTTP/1.1", status: $status,
      akto_account_id: "1000000", akto_vxlan_id: $akvx, is_pending: "false",
      source: "MIRRORING", direction: null, process_id: null, socket_id: null,
      daemonset_id: null, enabled_graph: null,
      tag: ($tag | tojson), metadata: ($tag | tojson), contextSource: $ctxsrc
    }
  '
}

_akto_hooks_send_ingestion() {
  local hook_name="$1" request_payload_json="$2" response_payload_json="$3" session_info_json="$4" input_json="$5" logfile="$6"
  if [[ -z "$AKTO_DATA_INGESTION_URL" ]]; then
    log_info "$logfile" "AKTO_DATA_INGESTION_URL not set, skipping ingestion"
    return 0
  fi
  log_info "$logfile" "Guardrails enabled? -> false"
  local payload url
  payload=$(_akto_hooks_build_ingestion_payload "$hook_name" "$request_payload_json" "$response_payload_json" "$session_info_json" "$input_json" "200")
  url=$(_akto_hooks_build_http_proxy_url "false" "true" "$hook_name")
  if akto_post_json "$url" "$payload" "$logfile" >/dev/null; then
    log_info "$logfile" "Ingestion successful for hook: $hook_name"
  else
    log_error "$logfile" "Ingestion error"
  fi
}

run_observability_hook() {
  local hook_name="$1" logfile="hook-executions.log"
  log_info "$logfile" "=== $hook_name hook started ==="

  local input_data
  input_data=$(cat)
  if ! jq -e . >/dev/null 2>&1 <<<"$input_data"; then
    input_data='{}'
  fi
  input_data=$(_akto_alias_camel_keys <<<"$input_data") || input_data='{}'
  AKTO_CONNECTOR="$(detect_connector "$input_data")"

  log_info "$logfile" "$hook_name input: $(jq -c . <<<"$input_data" 2>/dev/null)"

  local session_info
  session_info=$(resolve_session_info "$input_data" "false")

  _akto_hooks_send_ingestion "$hook_name" "$input_data" '{}' "$session_info" "$input_data" "$logfile"

  log_info "$logfile" "=== $hook_name hook completed ==="
}

run_agent_stop_hook() {
  local logfile="hook-executions.log"
  log_info "$logfile" "=== agentStop hook started ==="

  local input_data
  input_data=$(cat)
  if ! jq -e . >/dev/null 2>&1 <<<"$input_data"; then
    input_data='{}'
  fi
  input_data=$(_akto_alias_camel_keys <<<"$input_data") || input_data='{}'
  AKTO_CONNECTOR="$(detect_connector "$input_data")"

  log_info "$logfile" "agentStop input: $(jq -c . <<<"$input_data" 2>/dev/null)"

  local session_info
  session_info=$(resolve_session_info "$input_data" "false")

  local transcript_path
  transcript_path=$(jq -r '.transcript_path // empty' <<<"$input_data")
  transcript_path="${transcript_path/#\~/$HOME}"

  local turn user_prompt response_text
  turn=$(get_last_turn "$transcript_path")
  user_prompt="${turn%%$'\t'*}"
  response_text="${turn#*$'\t'}"

  local attempt=0
  while [[ $attempt -lt 5 ]]; do
    [[ -n "$response_text" || -z "$user_prompt" ]] && break
    sleep 0.2
    turn=$(get_last_turn "$transcript_path")
    user_prompt="${turn%%$'\t'*}"
    response_text="${turn#*$'\t'}"
    attempt=$((attempt + 1))
  done

  if [[ -n "$user_prompt" || -n "$response_text" ]]; then
    log_info "$logfile" "Extracted turn — prompt: ${#user_prompt} chars, response: ${#response_text} chars"
    _akto_hooks_send_ingestion "agentStop" "$(jq -n -c --arg p "$user_prompt" '$p')" "$(jq -n -c --arg r "$response_text" '$r')" "$session_info" "$input_data" "$logfile"
  else
    log_info "$logfile" "No conversational content found in transcript — ingesting metadata only"
    _akto_hooks_send_ingestion "agentStop" "$input_data" '{}' "$session_info" "$input_data" "$logfile"
  fi

  log_info "$logfile" "=== agentStop hook completed ==="
}

if [[ $# -lt 1 ]]; then
  echo "Usage: akto-hooks.sh <hookName>" >&2
  exit 1
fi

if ! akto_check_deps "hook-executions.log"; then
  echo "{}"
  exit 0
fi

if [[ "$1" == "agentStop" ]]; then
  run_agent_stop_hook
else
  run_observability_hook "$1"
fi
echo "{}"
exit 0

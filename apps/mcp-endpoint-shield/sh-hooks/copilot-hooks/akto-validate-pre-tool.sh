#!/bin/bash
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/akto_common.sh"

get_username >/dev/null 2>&1

LOGFILE="validate-pre-tool.log"

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
MCP_INGEST_PATH="${MCP_INGEST_PATH:-/mcp}"

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

warn_state_path="$LOG_DIR/akto_pretool_warn_pending.json"

session_info=$(resolve_session_info "$input_data" "false")
session_id=$(jq -r '.session_id // empty' <<<"$input_data")
message_id=$(jq -r '.current_message_id // empty' <<<"$session_info")
cfg_session_headers=$(session_headers "$session_id" "$message_id")

log_info "$LOGFILE" "=== Pre-Tool Use Hook - Connector: $connector, Mode: $MODE, Sync: $AKTO_SYNC_MODE ==="
[[ "$LOG_PAYLOADS" == "true" ]] && log_info "$LOGFILE" "Raw input (truncated): $(jq -c . <<<"$input_data")"
log_info "$LOGFILE" "MODE: $MODE, API_URL: $CFG_API_URL"

tool_name=$(jq -r '(.toolName // .tool_name // "unknown")' <<<"$input_data")
raw_args=$(jq -c '(if .toolArgs != null then .toolArgs else (.tool_input // {}) end)' <<<"$input_data")
tool_args=$(jq -r 'if type=="string" then . else tojson end' <<<"$raw_args")

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

IFS=$'\t' read -r is_mcp mcp_server mcp_tool < <(parse_github_tool "$tool_name")

log_info "$LOGFILE" "Parsed: tool_name=$tool_name, tool_args_len=${#tool_args}, cwd=$cwd, timestamp=$timestamp"
if [[ "$tool_name" == "unknown" ]]; then
  log_warn "$LOGFILE" "tool_name fell back to 'unknown'. connector=$connector."
fi
if [[ "$is_mcp" == "true" ]]; then
  log_info "$LOGFILE" "Tool: $tool_name (MCP server=$mcp_server, mcpTool=$mcp_tool), CWD: $cwd"
else
  log_info "$LOGFILE" "Tool: $tool_name, CWD: $cwd"
fi

if [[ "$AKTO_SYNC_MODE" != "true" || -z "$AKTO_DATA_INGESTION_URL" ]]; then
  log_info "$LOGFILE" "Guardrails disabled (sync mode off or no URL)"
  exit 0
fi

build_akto_request() {
  local status_code="${1:-200}"
  local device_id host tags path request_payload response_payload
  device_id="${DEVICE_ID:-$(get_machine_id)}"

  if [[ "$is_mcp" == "true" ]]; then
    tags=$(jq -n -c --arg tag "$CFG_AI_AGENT_TAG" --arg src "$CONTEXT_SOURCE" --arg mode "$MODE" '
      {"mcp-server":"MCP Server","mcp-client":$tag} + (if $mode=="atlas" then {source:$src} else {} end)
    ')
    host=$(mcp_mirror_host "$device_id" "$CFG_AI_AGENT_TAG" "$mcp_server")
    path="$MCP_INGEST_PATH"
    local parsed_input
    parsed_input=$(printf '%s' "$tool_args" | jq -Rc 'fromjson? // {raw: .}')
    request_payload=$(build_tools_call_jsonrpc "$mcp_tool" "$parsed_input")
    response_payload='{}'
  else
    tags=$(jq -n -c --arg tag "$CFG_AI_AGENT_TAG" --arg src "$CONTEXT_SOURCE" --arg mode "$MODE" '
      {"gen-ai":"Gen AI","tool-use":"Tool Execution"} + (if $mode=="atlas" then {"ai-agent":$tag} else {} end) + (if $mode=="atlas" then {source:$src} else {} end)
    ')
    host="${CFG_API_URL#https://}"; host="${host#http://}"
    path="/copilot/tool/${tool_name}"
    request_payload=$(jq -n -c --arg tn "$tool_name" --arg ta "$tool_args" '{body: ({toolName:$tn, toolArgs:$ta} | tojson)}')
    response_payload='{}'
  fi

  local req_headers resp_headers
  req_headers=$(jq -n -c --arg host "$host" --arg hh "$CFG_HOOK_HEADER" --arg mcps "$mcp_server" --argjson ismcp "$is_mcp" --argjson sh "$cfg_session_headers" '
    {host:$host, ($hh):"PreToolUse", "content-type":"application/json"}
    + (if $ismcp and ($mcps|length)>0 then {"x-mcp-server":$mcps} else {} end)
    + $sh
  ')
  resp_headers=$(jq -n -c --arg hh "$CFG_HOOK_HEADER" '{($hh):"PreToolUse"}')

  jq -n -c \
    --arg path "$path" --argjson reqh "$req_headers" --argjson resph "$resp_headers" \
    --argjson reqp "$request_payload" --argjson resp "$response_payload" \
    --arg ip "$(get_username)" --arg ts "$timestamp" --arg status "$status_code" \
    --arg akvx "$device_id" --argjson tag "$tags" '
    {
      path: $path,
      requestHeaders: ($reqh | tojson),
      responseHeaders: ($resph | tojson),
      method: "POST",
      requestPayload: ($reqp | tojson),
      responsePayload: ($resp | tojson),
      ip: $ip, destIp: "127.0.0.1", time: $ts,
      statusCode: $status, type: "HTTP/1.1", status: $status,
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

pretool_fingerprint() {
  jq -n -c --arg t "$1" --arg a "$2" '{t:$t,a:$a}' | jq -S -c . | _akto_sha256
}

if [[ "$is_mcp" == "true" ]]; then
  log_info "$LOGFILE" "Validating MCP tools/call for $mcp_tool (server=$mcp_server, githubTool=$tool_name)"
else
  log_info "$LOGFILE" "Validating tool use: $tool_name"
fi

gr_allowed=true; gr_reason=""; behaviour=""
request_body=$(build_akto_request "200")
if result=$(akto_post_json "$(build_http_proxy_url true true)" "$request_body" "$LOGFILE" 2>/dev/null); then
  gr_allowed=$(jq -r '(.data.guardrailsResult.Allowed | if . == null then true else . end)' <<<"$result" 2>/dev/null)
  [[ "$gr_allowed" == "null" || -z "$gr_allowed" ]] && gr_allowed=true
  gr_reason=$(jq -r '.data.guardrailsResult.Reason // ""' <<<"$result" 2>/dev/null)
  behaviour=$(jq -r '(.data.guardrailsResult.behaviour // .data.guardrailsResult.Behaviour // "")' <<<"$result" 2>/dev/null)
  if [[ "$gr_allowed" == "true" ]]; then
    log_info "$LOGFILE" "Tool use ALLOWED by guardrails"
  else
    log_warn "$LOGFILE" "Tool use DENIED by guardrails: $gr_reason"
  fi
fi

fingerprint=$(pretool_fingerprint "$tool_name" "$tool_args")
allowed=$(apply_warn_resubmit_flow "$gr_allowed" "$behaviour" "$fingerprint" "$warn_state_path" "$LOGFILE")

if [[ "$allowed" != "true" ]]; then
  b_lower=$(_akto_lower_trim "$behaviour")
  if [[ "$b_lower" == "warn" ]]; then
    denial_reason="Warning!! Tool use blocked, please review it. Send again to bypass. Reason for blocking: $gr_reason"
  else
    denial_reason="Blocked by Akto Guardrails: ${gr_reason:-Policy violation}"
  fi

  log_warn "$LOGFILE" "BLOCKING tool use: $tool_name, Reason: $denial_reason"
  jq -n -c --arg r "$denial_reason" '
    {permissionDecision:"deny", permissionDecisionReason:$r,
     hookSpecificOutput:{permissionDecision:"deny", permissionDecisionReason:$r}}
  '

  if [[ -n "$AKTO_DATA_INGESTION_URL" ]]; then
    blocked_body=$(build_akto_request "403")
    blocked_body=$(jq -c --arg reason "${gr_reason:-Policy violation}" '
      .responsePayload = ({body: ({"x-blocked-by":"Akto Proxy", reason:$reason} | tojson)} | tojson)
    ' <<<"$blocked_body")
    akto_post_json "$(build_http_proxy_url false true)" "$blocked_body" "$LOGFILE" >/dev/null
    log_info "$LOGFILE" "Blocked tool use ingested successfully"
  fi
  exit "$CFG_BLOCKED_EXIT_CODE"
fi

log_info "$LOGFILE" "Tool use PASSED guardrails for $tool_name"
exit 0

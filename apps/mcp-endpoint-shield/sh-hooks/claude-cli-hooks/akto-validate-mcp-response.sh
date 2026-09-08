#!/bin/bash
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

LOG_DIR="${LOG_DIR:-$HOME/.claude/akto/logs}"
LOG_DIR="${LOG_DIR/#\~/$HOME}"
export LOG_DIR
mkdir -p "$LOG_DIR" 2>/dev/null

source "$SCRIPT_DIR/akto_common.sh"

get_username >/dev/null 2>&1

LOGFILE="validate-mcp-response.log"
akto_check_deps "$LOGFILE" || exit 0
LOG_PAYLOADS="$(_akto_lower "${LOG_PAYLOADS:-false}")"

MODE="$(_akto_lower "${MODE:-atlas}")"
AKTO_DATA_INGESTION_URL="${AKTO_DATA_INGESTION_URL:-}"
AKTO_DATA_INGESTION_URL="${AKTO_DATA_INGESTION_URL%/}"
AKTO_TIMEOUT="${AKTO_TIMEOUT:-5}"
AKTO_SYNC_MODE="$(_akto_lower "${AKTO_SYNC_MODE:-true}")"
AKTO_INGEST_NON_MCP_TOOLS="$(_akto_lower "${AKTO_INGEST_NON_MCP_TOOLS:-false}")"
AKTO_CONNECTOR="${AKTO_CONNECTOR:-claude_code_cli}"
AKTO_CONNECTOR_VALUE="${AKTO_CONNECTOR_VALUE:-claudecli}"
AKTO_API_TOKEN="${AKTO_API_TOKEN:-}"
CONTEXT_SOURCE="${CONTEXT_SOURCE:-ENDPOINT}"
WARN_STATE_PATH="$LOG_DIR/akto_posttool_warn_pending.json"
MCP_INGEST_PATH="${MCP_INGEST_PATH:-/mcp}"
NON_MCP_TOOL_PATH_PREFIX="${NON_MCP_TOOL_PATH_PREFIX:-/tool}"

DEVICE_ID="${DEVICE_ID:-$(get_machine_id)}"
if [[ "$MODE" == "atlas" ]]; then
  if [[ -n "$DEVICE_ID" ]]; then
    CLAUDE_API_URL="https://${DEVICE_ID}.ai-agent.${AKTO_CONNECTOR_VALUE}"
  else
    CLAUDE_API_URL="https://api.anthropic.com"
  fi
  log_info "$LOGFILE" "MODE: $MODE, Device ID: $DEVICE_ID, CLAUDE_API_URL: $CLAUDE_API_URL"
else
  CLAUDE_API_URL="${CLAUDE_API_URL:-https://api.anthropic.com}"
  log_info "$LOGFILE" "MODE: $MODE, CLAUDE_API_URL: $CLAUDE_API_URL"
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

parse_claude_tool() {
  local tool_name="$1"
  jq -n -c --arg t "$tool_name" '
    if ($t | startswith("mcp__")) then
      ($t | split("__")) as $parts
      | if ($parts|length) < 3 then {is_mcp:false, server:"", tool:""}
        else
          ($parts[1]) as $server | ($parts[2:] | join("__")) as $tool
          | if ($server=="" or $tool=="") then {is_mcp:false, server:"", tool:""}
            else {is_mcp:true, server:$server, tool:$tool} end
        end
    else {is_mcp:false, server:"", tool:""} end
  '
}

normalize_tool_name_for_url_path() {
  local s="${1:-unknown}"
  s=$(printf '%s' "$s" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')
  [[ -z "$s" ]] && s="unknown"
  s=$(printf '%s' "$s" | sed -E 's/[^a-zA-Z0-9._~-]+/-/g; s/-+/-/g; s/^-+//; s/-+$//')
  [[ -z "$s" ]] && s="unknown"
  printf '%s' "$s"
}

non_mcp_ingest_path() {
  local tool_name="$1"
  local fixed="${NON_MCP_INGEST_PATH:-}"
  if [[ -n "$fixed" ]]; then
    [[ "$fixed" == /* ]] || fixed="/$fixed"
    printf '%s' "$fixed"
    return
  fi
  local prefix="${NON_MCP_TOOL_PATH_PREFIX:-/tool}"
  [[ "$prefix" == /* ]] || prefix="/$prefix"
  prefix="${prefix%/}"
  [[ -z "$prefix" ]] && prefix="/tool"
  printf '%s/%s' "$prefix" "$(normalize_tool_name_for_url_path "$tool_name")"
}

build_tools_call_jsonrpc() {
  local mcp_tool_name="$1" tool_input_json="$2" request_id="${3:-1}"
  jq -n -c --arg name "$mcp_tool_name" --argjson input "$tool_input_json" --argjson id "$request_id" '
    ($input | if type=="object" then . elif . == null then {} else {input:.} end) as $args
    | {jsonrpc:"2.0", method:"tools/call", params:{name:$name, arguments:$args}, id:$id}
  '
}

build_tools_call_result_jsonrpc() {
  local tool_response_json="$1" request_id="${2:-1}"
  jq -n -c --argjson resp "$tool_response_json" --argjson id "$request_id" '
    ($resp | if type=="object" then . else {output:.} end) as $result
    | {jsonrpc:"2.0", id:$id, result:$result}
  '
}

mcp_mirror_host() { printf '%s.%s.%s' "$DEVICE_ID" "$AKTO_CONNECTOR_VALUE" "$1"; }

build_hook_tags() {
  local is_mcp="$1"
  jq -n -c --arg mode "$MODE" --arg src "$CONTEXT_SOURCE" --arg av "$AKTO_CONNECTOR_VALUE" --argjson is_mcp "$is_mcp" '
    (if $is_mcp then {"mcp-server":"MCP Server","mcp-client":$av} else {"gen-ai":"Gen AI","ai-agent":$av} end)
    + (if $mode=="atlas" then {"source":$src} else {} end)
  '
}

build_ingestion_payload() {
  local tool_name="$1" tool_input_json="$2" tool_response_json="$3" is_mcp="$4" mcp_server="$5" mcp_tool="$6" session_info_json="${7:-null}"
  local tags host req_hdr installer_hdrs request_payload response_payload path now_ms

  tags=$(build_hook_tags "$is_mcp")
  if [[ "$is_mcp" == "true" ]]; then
    host=$(mcp_mirror_host "$mcp_server")
  else
    host="${CLAUDE_API_URL#https://}"; host="${host#http://}"
  fi

  installer_hdrs='{}'
  [[ "$session_info_json" != "null" ]] && installer_hdrs=$(_akto_installer_headers "$session_info_json" "null")

  req_hdr=$(jq -n -c --arg host "$host" --arg mcps "$mcp_server" --argjson is_mcp "$is_mcp" --argjson inst "$installer_hdrs" '
    {"host":$host, "x-claude-hook":"PostToolUse", "content-type":"application/json"}
    + (if $is_mcp and $mcps != "" then {"x-mcp-server":$mcps} else {} end)
    + $inst
  ')

  if [[ "$is_mcp" == "true" ]]; then
    request_payload=$(build_tools_call_jsonrpc "$mcp_tool" "$tool_input_json")
    response_payload=$(build_tools_call_result_jsonrpc "$tool_response_json")
    path="$MCP_INGEST_PATH"
  else
    request_payload=$(jq -n -c --arg tn "$tool_name" --argjson ta "$tool_input_json" '{body:{toolName:$tn, toolArgs:$ta}}')
    response_payload=$(jq -n -c --argjson r "$tool_response_json" '{body:{result:$r}}')
    path=$(non_mcp_ingest_path "$tool_name")
  fi
  now_ms=$(( $(date +%s) * 1000 ))

  jq -n -c \
    --arg path "$path" --argjson reqh "$req_hdr" --argjson reqp "$request_payload" --argjson resp "$response_payload" \
    --arg ip "$(get_username)" --arg time "$now_ms" --argjson tag "$tags" --arg src "$CONTEXT_SOURCE" --arg akvx "$DEVICE_ID" '
    {
      path: $path,
      requestHeaders: ($reqh|tojson),
      responseHeaders: ({"x-claude-hook":"PostToolUse","content-type":"application/json"}|tojson),
      method: "POST",
      requestPayload: ($reqp|tojson),
      responsePayload: ($resp|tojson),
      ip: $ip, destIp: "127.0.0.1", time: $time,
      statusCode: "200", type: "HTTP/1.1", status: "200",
      akto_account_id: "1000000", akto_vxlan_id: $akvx, is_pending: "false",
      source: "MIRRORING", direction: null, process_id: null, socket_id: null,
      daemonset_id: null, enabled_graph: null,
      tag: ($tag|tojson), metadata: ($tag|tojson), contextSource: $src
    }
  '
}

_akto_falsy() {
  [[ "$(jq -r '(. == {} or . == null or . == [] or . == "" or . == false or . == 0)' <<<"$1")" == "true" ]]
}

call_guardrails() {
  local tool_name="$1" tool_input_json="$2" tool_response_json="$3" is_mcp="$4" mcp_server="$5" mcp_tool="$6" session_info_json="${7:-null}"

  if _akto_falsy "$tool_input_json" || _akto_falsy "$tool_response_json"; then
    jq -n -c '{allowed:true,reason:"",behaviour:""}'
    return
  fi
  if [[ -z "$AKTO_DATA_INGESTION_URL" ]]; then
    log_warn "$LOGFILE" "AKTO_DATA_INGESTION_URL not set, allowing request (fail-open)"
    jq -n -c '{allowed:true,reason:"",behaviour:""}'
    return
  fi

  if [[ "$is_mcp" == "true" ]]; then
    log_info "$LOGFILE" "Validating MCP tools/call result for $mcp_tool (server=$mcp_server, claudeTool=$tool_name)"
  else
    log_info "$LOGFILE" "Validating built-in / non-MCP tool result: $tool_name"
  fi

  local req_body url result rc
  req_body=$(build_ingestion_payload "$tool_name" "$tool_input_json" "$tool_response_json" "$is_mcp" "$mcp_server" "$mcp_tool" "$session_info_json")
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
    log_info "$LOGFILE" "Request ALLOWED for $tool_name"
  else
    log_warn "$LOGFILE" "Request DENIED for $tool_name: $(jq -r '.reason' <<<"$parsed")"
  fi
  printf '%s' "$parsed"
}

posttool_fingerprint() {
  local tool_input_json="$1"
  jq -n -c -S --argjson i "$tool_input_json" '{i:$i}' | _akto_sha256
}

ingest_blocked_request() {
  local tool_name="$1" tool_input_json="$2" tool_response_json="$3" reason="$4" is_mcp="$5" mcp_server="$6" mcp_tool="$7" session_info_json="${8:-null}"
  [[ -z "$AKTO_DATA_INGESTION_URL" || "$AKTO_SYNC_MODE" != "true" ]] && return
  if [[ "$is_mcp" != "true" && "$AKTO_INGEST_NON_MCP_TOOLS" != "true" ]]; then
    log_info "$LOGFILE" "Skipping non-MCP blocked-request ingestion (set AKTO_INGEST_NON_MCP_TOOLS=true to re-enable)"
    return
  fi

  log_info "$LOGFILE" "Ingesting blocked request data"
  local req_body url
  req_body=$(build_ingestion_payload "$tool_name" "$tool_input_json" "$tool_response_json" "$is_mcp" "$mcp_server" "$mcp_tool" "$session_info_json")
  req_body=$(jq -c --arg reason "${reason:-Policy violation}" '
    .responseHeaders = ({"x-claude-hook":"PostToolUse","x-blocked-by":"Akto Proxy","content-type":"application/json"} | tojson)
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
  local tool_name="$1" tool_input_json="$2" tool_response_json="$3" is_mcp="$4" mcp_server="$5" mcp_tool="$6" session_info_json="${7:-null}"
  [[ -z "$AKTO_DATA_INGESTION_URL" ]] && { log_info "$LOGFILE" "AKTO_DATA_INGESTION_URL not set, skipping ingestion"; return; }
  if _akto_falsy "$tool_input_json"; then log_info "$LOGFILE" "Skipping ingestion due to empty tool input"; return; fi
  if _akto_falsy "$tool_response_json"; then log_info "$LOGFILE" "Skipping ingestion due to empty tool response"; return; fi

  if [[ "$is_mcp" == "true" ]]; then
    log_info "$LOGFILE" "Ingesting MCP tools/call result for $mcp_tool (server=$mcp_server, claudeTool=$tool_name)"
  else
    log_info "$LOGFILE" "Ingesting non-MCP tool result (gen-ai only): $tool_name"
  fi

  local req_body url resp_guardrails
  req_body=$(build_ingestion_payload "$tool_name" "$tool_input_json" "$tool_response_json" "$is_mcp" "$mcp_server" "$mcp_tool" "$session_info_json")
  resp_guardrails="true"
  [[ "$AKTO_SYNC_MODE" == "true" ]] && resp_guardrails="false"
  url=$(build_http_proxy_url "false" "$resp_guardrails" "true")
  if akto_post_json "$url" "$req_body" "$LOGFILE" >/dev/null; then
    log_info "$LOGFILE" "Tool response ingestion successful"
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

  local tool_name tool_input_json tool_response_json mcp_json is_mcp mcp_server mcp_tool
  tool_name=$(jq -r '.tool_name // ""' <<<"$input_data")
  mcp_json=$(parse_claude_tool "$tool_name")
  is_mcp=$(jq -r '.is_mcp' <<<"$mcp_json")
  mcp_server=$(jq -r '.server' <<<"$mcp_json")
  mcp_tool=$(jq -r '.tool' <<<"$mcp_json")
  tool_input_json=$(jq -c '.tool_input // {}' <<<"$input_data")
  tool_response_json=$(jq -c '.tool_response // {}' <<<"$input_data")

  if [[ "$is_mcp" == "true" ]]; then
    log_info "$LOGFILE" "Processing MCP tool response: $tool_name (server=$mcp_server, mcpTool=$mcp_tool)"
  else
    log_info "$LOGFILE" "Processing non-MCP tool response (gen-ai only): $tool_name"
  fi

  if [[ "$AKTO_SYNC_MODE" == "true" ]]; then
    local gr_json gr_allowed gr_reason behaviour fingerprint allowed
    gr_json=$(call_guardrails "$tool_name" "$tool_input_json" "$tool_response_json" "$is_mcp" "$mcp_server" "$mcp_tool" "$session_info")
    gr_allowed=$(jq -r '.allowed' <<<"$gr_json")
    gr_reason=$(jq -r '.reason' <<<"$gr_json")
    behaviour=$(jq -r '.behaviour' <<<"$gr_json")
    fingerprint=$(posttool_fingerprint "$tool_input_json")
    allowed=$(apply_warn_resubmit_flow "$gr_allowed" "$behaviour" "$fingerprint" "$WARN_STATE_PATH" "$LOGFILE")

    if [[ "$allowed" != "true" ]]; then
      local block_reason
      if [[ "$(_akto_lower_trim "$behaviour")" == "warn" ]]; then
        block_reason="Warning!!, tool result blocked, please review it. Send again to bypass. Reason for blocking: ${gr_reason}"
      else
        block_reason="Tool result blocked: ${gr_reason}"
      fi
      jq -n -c --arg r "$block_reason" --arg ctx "${gr_reason:-Policy violation}" '
        {decision:"block", reason:$r, hookSpecificOutput:{hookEventName:"PostToolUse", additionalContext:$ctx}}
      '
      log_warn "$LOGFILE" "BLOCKING tool result - Tool: $tool_name, Reason: $gr_reason"
      ingest_blocked_request "$tool_name" "$tool_input_json" "$tool_response_json" "$gr_reason" "$is_mcp" "$mcp_server" "$mcp_tool" "$session_info"
      exit 0
    fi
  fi

  send_ingestion_data "$tool_name" "$tool_input_json" "$tool_response_json" "$is_mcp" "$mcp_server" "$mcp_tool" "$session_info"
  exit 0
}

main

#!/usr/bin/env bash
# akto-validate-tool-response.sh - bash port of akto-validate-tool-response.py (tool.execute.after)
set -u
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/akto_common.sh"

AKTO_LOG_FILE="$AKTO_LOG_DIR/validate-tool-response.log"
DEVICE_ID="${DEVICE_ID:-$(get_machine_id)}"
OPENCODE_API_URL="$(resolve_opencode_api_url)"

build_ingestion_payload() {  # TOOL_NAME TOOL_INPUT_RAW TOOL_RESPONSE_RAW
  local tool_name="$1" tool_input="$2" tool_response="$3"
  local host="${OPENCODE_API_URL#https://}"; host="${host#http://}"
  local tags='{"gen-ai":"Gen AI","tool-use":"Tool Execution","tool_server_name":"opencode"}'
  [ "$AKTO_MODE" = "atlas" ] && tags="{\"gen-ai\":\"Gen AI\",\"tool-use\":\"Tool Execution\",\"tool_server_name\":\"opencode\",\"ai-agent\":\"opencode\",\"source\":$(json_str "$CONTEXT_SOURCE")}"

  local req_hdr="{\"host\":$(json_str "$host"),\"x-claude-hook\":\"PostToolUse\",\"content-type\":\"application/json\"}"
  local resp_hdr='{"x-claude-hook":"PostToolUse","content-type":"application/json"}'
  local req_payload; req_payload="{\"body\":{\"toolName\":$(json_str "$tool_name"),\"toolArgs\":${tool_input:-null}}}"
  local resp_payload; resp_payload="{\"body\":{\"result\":${tool_response:-null}}}"

  printf '{"path":"/v1/messages","requestHeaders":%s,"responseHeaders":%s,"method":"POST","requestPayload":%s,"responsePayload":%s,"ip":%s,"destIp":"127.0.0.1","time":"%s","statusCode":"200","type":"HTTP/1.1","status":"200","akto_account_id":"1000000","akto_vxlan_id":%s,"is_pending":"false","source":"MIRRORING","direction":null,"process_id":null,"socket_id":null,"daemonset_id":null,"enabled_graph":null,"tag":%s,"metadata":%s,"contextSource":%s}' \
    "$(json_str "$req_hdr")" "$(json_str "$resp_hdr")" "$(json_str "$req_payload")" "$(json_str "$resp_payload")" \
    "$(json_str "$(get_username)")" "$(date +%s)000" "$(json_str "$DEVICE_ID")" "$(json_str "$tags")" "$(json_str "$tags")" "$(json_str "$CONTEXT_SOURCE")"
}

main() {
  log_info "=== PostToolUse hook started - Mode: $AKTO_MODE, Sync: $AKTO_SYNC_MODE ==="
  local input; input="$(cat)"
  [ -z "$input" ] && { log_error "Empty input"; exit 0; }

  local tool_name; tool_name="$(json_string "$input" tool_name 2>/dev/null)"; [ -z "$tool_name" ] && tool_name=""
  local tool_input; tool_input="$(json_raw "$input" tool_input 2>/dev/null)"; [ -z "$tool_input" ] && tool_input="{}"
  local tool_response; tool_response="$(json_raw "$input" tool_response 2>/dev/null)"; [ -z "$tool_response" ] && tool_response="{}"
  log_info "Processing tool response: $tool_name"

  if [ -z "$AKTO_DATA_INGESTION_URL" ]; then
    log_info "AKTO_DATA_INGESTION_URL not set, skipping ingestion"; exit 0
  fi
  if [ "$tool_input" = "{}" ] || [ -z "$tool_input" ]; then
    log_info "Skipping ingestion due to empty tool input"; exit 0
  fi
  if [ "$tool_response" = "{}" ] || [ -z "$tool_response" ]; then
    log_info "Skipping ingestion due to empty tool response"; exit 0
  fi

  # guardrails=NOT AKTO_SYNC_MODE, ingest=true (mirrors Python: guardrails=not AKTO_SYNC_MODE, ingest_data=True)
  local gr_flag=0; [ "$AKTO_SYNC_MODE" != "true" ] && gr_flag=1
  local body; body="$(build_ingestion_payload "$tool_name" "$tool_input" "$tool_response")"
  post_payload_json "$(build_http_proxy_url "$gr_flag" 1)" "$body" >/dev/null 2>&1 || true
  log_info "Tool response ingestion successful"
  exit 0
}
main

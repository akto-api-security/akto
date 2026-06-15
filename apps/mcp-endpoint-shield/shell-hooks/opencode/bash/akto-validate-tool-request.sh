#!/usr/bin/env bash
# akto-validate-tool-request.sh - bash port of akto-validate-tool-request.py (tool.execute.before)
set -u
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/akto_common.sh"

AKTO_LOG_FILE="$AKTO_LOG_DIR/validate-tool-request.log"
DEVICE_ID="${DEVICE_ID:-$(get_machine_id)}"
OPENCODE_API_URL="$(resolve_opencode_api_url)"

build_validation_request() {  # TOOL_NAME TOOL_INPUT_JSON
  local tool_name="$1" tool_input="$2"
  local host="${OPENCODE_API_URL#https://}"; host="${host#http://}"
  local tags='{"gen-ai":"Gen AI","tool_server_name":"opencode"}'
  [ "$AKTO_MODE" = "atlas" ] && tags="{\"gen-ai\":\"Gen AI\",\"tool_server_name\":\"opencode\",\"ai-agent\":\"opencode\",\"source\":$(json_str "$CONTEXT_SOURCE")}"

  local req_hdr="{\"host\":$(json_str "$host"),\"x-opencode-hook\":\"PreToolUse\",\"content-type\":\"application/json\"}"
  local resp_hdr='{"x-opencode-hook":"PreToolUse"}'
  local req_payload; req_payload="{\"body\":${tool_input:-null},\"toolName\":$(json_str "$tool_name")}"

  printf '{"path":"/v1/messages","requestHeaders":%s,"responseHeaders":%s,"method":"POST","requestPayload":%s,"responsePayload":%s,"ip":%s,"destIp":"127.0.0.1","time":"%s","statusCode":"200","type":"HTTP/1.1","status":"200","akto_account_id":"1000000","akto_vxlan_id":%s,"is_pending":"false","source":"MIRRORING","direction":null,"process_id":null,"socket_id":null,"daemonset_id":null,"enabled_graph":null,"tag":%s,"metadata":%s,"contextSource":%s}' \
    "$(json_str "$req_hdr")" "$(json_str "$resp_hdr")" "$(json_str "$req_payload")" "$(json_str '{}')" \
    "$(json_str "$(get_username)")" "$(date +%s)000" "$(json_str "$DEVICE_ID")" "$(json_str "$tags")" "$(json_str "$tags")" "$(json_str "$CONTEXT_SOURCE")"
}

main() {
  log_info "=== PreToolUse hook started - Mode: $AKTO_MODE, Sync: $AKTO_SYNC_MODE ==="
  local input; input="$(cat)"
  [ -z "$input" ] && { log_error "Empty input"; exit 0; }

  local tool_name; tool_name="$(json_string "$input" tool_name 2>/dev/null)"; [ -z "$tool_name" ] && tool_name=""
  local tool_input; tool_input="$(json_raw "$input" tool_input 2>/dev/null)"; [ -z "$tool_input" ] && tool_input="{}"
  log_info "Processing tool request: $tool_name"

  if [ "$AKTO_SYNC_MODE" = "true" ] && [ -n "$AKTO_DATA_INGESTION_URL" ]; then
    # allow if empty input
    if [ "$tool_input" = "{}" ] || [ -z "$tool_input" ]; then
      log_info "Empty tool input, allowing"; exit 0
    fi

    local body resp
    body="$(build_validation_request "$tool_name" "$tool_input")"
    resp="$(post_payload_json "$(build_http_proxy_url 1 0)" "$body")" || { log_error "guardrails failed; fail-open"; exit 0; }
    parse_guardrails_result "$resp"

    if [ "$GR_ALLOWED" != "true" ]; then
      local block_reason; block_reason="${GR_REASON:-Policy violation}"
      log_warn "BLOCKING tool request - Tool: $tool_name, Reason: $block_reason"
      # ingest blocked
      local blocked_body; blocked_body="$(build_validation_request "$tool_name" "$tool_input")"
      post_payload_json "$(build_http_proxy_url 0 1)" "$blocked_body" >/dev/null 2>&1 || true
      printf '{"decision":"block","reason":%s}' "$(json_str "Blocked by Akto Guardrails: $block_reason")"
      exit 0
    fi
    log_info "Tool request allowed for $tool_name"
  fi
  exit 0
}
main

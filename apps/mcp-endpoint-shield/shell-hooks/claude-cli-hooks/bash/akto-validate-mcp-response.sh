#!/usr/bin/env bash
# akto-validate-mcp-response.sh - bash port of akto-validate-mcp-response.py (PostToolUse)
set -u
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/akto_common.sh"

AKTO_LOG_FILE="$AKTO_LOG_DIR/validate-mcp-response.log"
WARN_STATE_PATH="$AKTO_LOG_DIR/akto_posttool_warn_pending.json"
DEVICE_ID="${DEVICE_ID:-$(get_machine_id)}"

if [ "$AKTO_MODE" = "atlas" ]; then
  if [ -n "$DEVICE_ID" ]; then CLAUDE_API_URL="https://${DEVICE_ID}.ai-agent.${AKTO_CONNECTOR_VALUE}"; else CLAUDE_API_URL="https://api.anthropic.com"; fi
else
  CLAUDE_API_URL="${CLAUDE_API_URL:-https://api.anthropic.com}"
fi

parse_claude_tool() {
  local tn="$1"; IS_MCP=0; MCP_SERVER=""; MCP_TOOL=""
  case "$tn" in mcp__*) ;; *) return 0;; esac
  local rest="${tn#mcp__}"
  case "$rest" in *__*) ;; *) return 0;; esac
  MCP_SERVER="${rest%%__*}"; MCP_TOOL="${rest#*__}"
  { [ -z "$MCP_SERVER" ] || [ -z "$MCP_TOOL" ]; } && { MCP_SERVER=""; MCP_TOOL=""; return 0; }
  IS_MCP=1
}
normalize_tool_name() {
  local s; s="$(printf '%s' "${1:-unknown}" | sed -E 's/[^a-zA-Z0-9._~-]+/-/g; s/-+/-/g; s/^-//; s/-$//')"
  [ -z "$s" ] && s="unknown"; printf '%s' "$s"
}
non_mcp_ingest_path() {
  local fixed="${NON_MCP_INGEST_PATH:-}"
  if [ -n "$fixed" ]; then case "$fixed" in /*) printf '%s' "$fixed";; *) printf '/%s' "$fixed";; esac; return 0; fi
  local prefix="$NON_MCP_TOOL_PATH_PREFIX"; case "$prefix" in /*) ;; *) prefix="/$prefix";; esac
  prefix="${prefix%/}"; [ -z "$prefix" ] && prefix="/tool"
  printf '%s/%s' "$prefix" "$(normalize_tool_name "$1")"
}
jsonrpc_arguments() {
  local ti="$1"; case "$ti" in '{'*) printf '%s' "$ti";; ''|null) printf '{}';; *) printf '{"input":%s}' "$ti";; esac
}
build_tools_call_jsonrpc() {
  printf '{"jsonrpc":"2.0","method":"tools/call","params":{"name":%s,"arguments":%s},"id":1}' "$(json_str "$1")" "$(jsonrpc_arguments "$2")"
}
build_tools_call_result_jsonrpc() {  # TOOL_RESPONSE_RAW
  local tr="$1" result_body
  case "$tr" in '{'*) result_body="$tr";; ''|null) result_body='{"output":null}';; *) result_body="{\"output\":$tr}";; esac
  printf '{"jsonrpc":"2.0","id":1,"result":%s}' "$result_body"
}
build_session_headers() {
  local frag=""
  [ -n "${SI_session_id:-}" ]      && frag="$frag,\"x-akto-installer-session_id\":$(json_str "$SI_session_id")"
  [ -n "${SI_transcript_path:-}" ] && frag="$frag,\"x-akto-installer-transcript_path\":$(json_str "$SI_transcript_path")"
  [ -n "${SI_cwd:-}" ]             && frag="$frag,\"x-akto-installer-cwd\":$(json_str "$SI_cwd")"
  [ -n "${SI_permission_mode:-}" ] && frag="$frag,\"x-akto-installer-permission_mode\":$(json_str "$SI_permission_mode")"
  [ -n "${SI_hook_event_name:-}" ] && frag="$frag,\"x-akto-installer-hook_event_name\":$(json_str "$SI_hook_event_name")"
  [ -n "${SI_tool_use_id:-}" ]     && frag="$frag,\"x-akto-installer-tool_use_id\":$(json_str "$SI_tool_use_id")"
  printf '%s' "$frag"
}

build_ingestion_payload() {  # TOOL_NAME TOOL_INPUT_RAW TOOL_RESPONSE_RAW
  local tool_name="$1" tool_input="$2" tool_response="$3"
  local tags host req_hdr request_payload response_payload path
  if [ "$IS_MCP" = "1" ]; then
    tags="{\"mcp-server\":\"MCP Server\",\"mcp-client\":$(json_str "$AKTO_CONNECTOR_VALUE")"
    host="${DEVICE_ID}.${AKTO_CONNECTOR_VALUE}.${MCP_SERVER}"
    request_payload="$(build_tools_call_jsonrpc "$MCP_TOOL" "$tool_input")"
    response_payload="$(build_tools_call_result_jsonrpc "$tool_response")"
    path="$MCP_INGEST_PATH"
  else
    tags="{\"gen-ai\":\"Gen AI\",\"ai-agent\":$(json_str "$AKTO_CONNECTOR_VALUE")"
    host="${CLAUDE_API_URL#https://}"; host="${host#http://}"
    request_payload="{\"body\":{\"toolName\":$(json_str "$tool_name"),\"toolArgs\":${tool_input:-null}}}"
    response_payload="{\"body\":{\"result\":${tool_response:-null}}}"
    path="$(non_mcp_ingest_path "$tool_name")"
  fi
  [ "$AKTO_MODE" = "atlas" ] && tags="$tags,\"source\":$(json_str "$CONTEXT_SOURCE")"
  tags="$tags}"
  req_hdr="{\"host\":$(json_str "$host"),\"x-claude-hook\":\"PostToolUse\",\"content-type\":\"application/json\""
  [ "$IS_MCP" = "1" ] && [ -n "$MCP_SERVER" ] && req_hdr="$req_hdr,\"x-mcp-server\":$(json_str "$MCP_SERVER")"
  req_hdr="$req_hdr$(build_session_headers)}"
  local request_headers; request_headers="$(json_str "$req_hdr")"
  local response_headers; response_headers="$(json_str '{"x-claude-hook":"PostToolUse","content-type":"application/json"}')"
  local tag_str; tag_str="$(json_str "$tags")"
  printf '{"path":%s,"requestHeaders":%s,"responseHeaders":%s,"method":"POST","requestPayload":%s,"responsePayload":%s,"ip":%s,"destIp":"127.0.0.1","time":"%s","statusCode":"200","type":"HTTP/1.1","status":"200","akto_account_id":"1000000","akto_vxlan_id":0,"is_pending":"false","source":"MIRRORING","direction":null,"process_id":null,"socket_id":null,"daemonset_id":null,"enabled_graph":null,"tag":%s,"metadata":%s,"contextSource":%s}' \
    "$(json_str "$path")" "$request_headers" "$response_headers" "$(json_str "$request_payload")" "$(json_str "$response_payload")" \
    "$(json_str "$(get_username)")" "$(date +%s)000" "$tag_str" "$tag_str" "$(json_str "$CONTEXT_SOURCE")"
}

should_ingest_nonmcp() { [ "$IS_MCP" = "1" ] || [ "$AKTO_INGEST_NON_MCP_TOOLS" = "true" ]; }

main() {
  log_info "=== Hook execution started - Mode: $AKTO_MODE, Sync: $AKTO_SYNC_MODE ==="
  local input; input="$(cat)"
  SI_session_id="$(json_string "$input" session_id 2>/dev/null)"
  SI_transcript_path="$(json_string "$input" transcript_path 2>/dev/null)"
  SI_cwd="$(json_string "$input" cwd 2>/dev/null)"
  SI_permission_mode="$(json_string "$input" permission_mode 2>/dev/null)"
  SI_hook_event_name="$(json_string "$input" hook_event_name 2>/dev/null)"
  SI_tool_use_id="$(json_string "$input" tool_use_id 2>/dev/null)"

  local tool_name tool_input tool_response
  tool_name="$(json_string "$input" tool_name 2>/dev/null)"
  tool_input="$(json_raw "$input" tool_input 2>/dev/null)"; [ -z "$tool_input" ] && tool_input="{}"
  tool_response="$(json_raw "$input" tool_response 2>/dev/null)"; [ -z "$tool_response" ] && tool_response="{}"
  parse_claude_tool "$tool_name"

  if [ "$AKTO_SYNC_MODE" = "true" ]; then
    if [ -z "$AKTO_DATA_INGESTION_URL" ]; then log_warn "AKTO_DATA_INGESTION_URL not set, allowing (fail-open)"; exit 0; fi
    # guardrails only meaningful with input+response present
    if [ -n "$tool_input" ] && [ -n "$tool_response" ]; then
      local body resp
      body="$(build_ingestion_payload "$tool_name" "$tool_input" "$tool_response")"
      resp="$(post_payload_json "$(build_http_proxy_url 0 1 0)" "$body")" || { log_error "guardrails failed; fail-open"; exit 0; }
      parse_guardrails_result "$resp"
      local fp; fp="$(printf '{"i":%s}' "$tool_input" | sha256_hex)"
      apply_warn_resubmit_flow "$GR_ALLOWED" "$GR_REASON" "$GR_BEHAVIOUR" "$fp" "$WARN_STATE_PATH"
      if [ "$ALLOWED" = "false" ]; then
        local block_reason
        if is_warn_behaviour "$GR_BEHAVIOUR"; then
          block_reason="Warning!!, tool result blocked, please review it. Send again to bypass. Reason for blocking: $GR_REASON"
        else
          block_reason="Tool result blocked: $GR_REASON"
        fi
        log_warn "BLOCKING tool result - Tool: $tool_name, Reason: $GR_REASON"
        printf '{"decision":"block","reason":%s,"hookSpecificOutput":{"hookEventName":"PostToolUse","additionalContext":%s}}\n' \
          "$(json_str "$block_reason")" "$(json_str "${GR_REASON:-Policy violation}")"
        if should_ingest_nonmcp; then post_payload_json "$(build_http_proxy_url 0 0 1)" "$body" >/dev/null 2>&1 || true; fi
        exit 0
      fi
    fi
  fi
  # normal ingestion (only mcp or AKTO_INGEST_NON_MCP_TOOLS)
  if [ -n "$AKTO_DATA_INGESTION_URL" ] && [ -n "$tool_input" ] && [ -n "$tool_response" ] && should_ingest_nonmcp; then
    local rg=0; [ "$AKTO_SYNC_MODE" != "true" ] && rg=1
    local body; body="$(build_ingestion_payload "$tool_name" "$tool_input" "$tool_response")"
    post_payload_json "$(build_http_proxy_url 0 "$rg" 1)" "$body" >/dev/null 2>&1 || true
  fi
  exit 0
}
main
